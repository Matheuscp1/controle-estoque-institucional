WITH candidates AS (
 SELECT r.code FROM import_batches b
 CROSS JOIN LATERAL jsonb_object_keys(CAST(b.order_counts AS jsonb)) r(code)
 WHERE b.id=:batch AND b.job_id=:job AND b.order_counts IS NOT NULL
 UNION
 SELECT DISTINCT substring(r.value->'fields'->>'id_externo_pedido',1,128) AS code
 FROM import_batches b CROSS JOIN LATERAL jsonb_array_elements(CAST(b.payload AS jsonb)->'rows') r(value)
 WHERE b.id=:batch AND b.job_id=:job AND b.order_counts IS NULL
), headers AS (
 SELECT r.record_key AS code,CAST(MIN(r.payload) AS jsonb) AS data
 FROM import_rows r JOIN candidates c ON c.code=r.record_key
 WHERE r.job_id=:job AND r.source='pedidos.csv'
 GROUP BY r.record_key
 HAVING COUNT(*) FILTER(WHERE r.error IS NOT NULL)=0 AND COUNT(DISTINCT r.payload)=1
), line_data AS (
 SELECT r.record_key AS code,r.reference_key AS item_code,r.error,
 CAST(CAST(r.payload AS jsonb)->>'quantidade' AS NUMERIC(19,3)) AS quantity,
 i.codigo AS existing_item
 FROM import_rows r JOIN candidates c ON c.code=r.record_key
 LEFT JOIN itens i ON i.codigo=r.reference_key AND i.import_job_id=:job
 WHERE r.job_id=:job AND r.source='pedido_itens.csv'
), complete_lines AS MATERIALIZED (
 SELECT code,COUNT(*) AS received,
 string_agg(LENGTH(item_code)||':'||item_code||
 LENGTH(CAST(trim_scale(quantity) AS TEXT))||':'||CAST(trim_scale(quantity) AS TEXT),'' ORDER BY item_code COLLATE "C") AS signature_lines
 FROM line_data GROUP BY code
 HAVING COUNT(*)=COUNT(DISTINCT item_code)
 AND COUNT(*) FILTER(WHERE error IS NOT NULL OR existing_item IS NULL)=0
), inserted AS (
 INSERT INTO pedidos(id_externo,codigo_orgao,solicitante,origem,assinatura,data_hora,situacao,data_cancelamento,motivo_cancelamento,import_job_id)
 SELECT h.code,h.data->>'codigo_orgao',h.data->>'solicitante',h.data->>'origem',
 encode(sha256(convert_to(
 LENGTH(h.data->>'codigo_orgao')||':'||(h.data->>'codigo_orgao')||
 LENGTH(h.data->>'solicitante')||':'||(h.data->>'solicitante')||
 LENGTH(h.data->>'origem')||':'||(h.data->>'origem')||l.signature_lines,'UTF8')),'hex'),
 CAST(h.data->>'data_hora' AS TIMESTAMP WITH TIME ZONE),h.data->>'situacao',
 CAST(NULLIF(h.data->>'data_cancelamento','') AS TIMESTAMP WITH TIME ZONE),
 CASE WHEN h.data->>'situacao'='CANCELADO' THEN NULLIF(h.data->>'motivo_cancelamento','') ELSE NULL END,:job
 FROM headers h JOIN complete_lines l ON l.code=h.code
 JOIN import_order_manifest m ON m.job_id=:job AND m.order_code=h.code AND m.expected_lines=l.received
 JOIN orgaos o ON o.codigo=h.data->>'codigo_orgao' AND o.import_job_id=:job
 ORDER BY h.code COLLATE "C"
 ON CONFLICT(id_externo) DO NOTHING RETURNING id,id_externo
), inserted_lines AS (
 INSERT INTO pedido_itens(pedido_id,codigo_item,quantidade)
 SELECT p.id,l.item_code,l.quantity FROM inserted p JOIN line_data l ON l.code=p.id_externo
 ORDER BY p.id,l.item_code COLLATE "C"
 RETURNING pedido_id
)
SELECT (SELECT COUNT(*) FROM inserted) AS orders_count,(SELECT COUNT(*) FROM inserted_lines) AS lines_count
