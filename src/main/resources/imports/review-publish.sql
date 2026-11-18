INSERT INTO orgaos(codigo,sigla,nome,uf,import_job_id)
SELECT code,data->>'sigla',data->>'nome',data->>'uf',:job FROM load_orgs o
WHERE NOT EXISTS(SELECT 1 FROM import_issues x WHERE x.job_id=:job AND x.active=true AND x.source='orgaos.csv' AND x.record_key=o.code)
ON CONFLICT(codigo) DO UPDATE SET sigla=EXCLUDED.sigla,nome=EXCLUDED.nome,uf=EXCLUDED.uf
WHERE orgaos.import_job_id=:job AND EXISTS(SELECT 1 FROM import_quarantine q WHERE q.job_id=:job AND q.entity_type='ORGANIZATION' AND q.record_key=orgaos.codigo);

INSERT INTO itens(codigo,descricao,unidade,saldo_inicial,saldo,ativo,versao,data_cadastro,import_job_id)
SELECT code,data->>'descricao',data->>'unidade',initial_balance,balance,data->>'ativo'='S',0,
CAST(data->>'data_cadastro' AS TIMESTAMP WITH TIME ZONE),:job FROM load_items i
WHERE NOT EXISTS(SELECT 1 FROM import_issues x WHERE x.job_id=:job AND x.active=true AND x.source='itens.csv' AND x.record_key=i.code
 AND x.reason NOT LIKE 'BALANCE_MISMATCH:%' AND x.reason NOT LIKE 'NEGATIVE_HISTORICAL_BALANCE:%')
ON CONFLICT(codigo) DO UPDATE SET descricao=EXCLUDED.descricao,unidade=EXCLUDED.unidade,saldo_inicial=EXCLUDED.saldo_inicial,
 saldo=EXCLUDED.saldo,ativo=EXCLUDED.ativo,data_cadastro=EXCLUDED.data_cadastro,versao=itens.versao+1
WHERE itens.import_job_id=:job AND EXISTS(SELECT 1 FROM import_quarantine q WHERE q.job_id=:job AND q.entity_type='ITEM' AND q.record_key=itens.codigo);

CREATE TEMP TABLE review_targets ON COMMIT DROP AS
SELECT p.code,p.data,p.org_code,p.created_at,p.status,p.cancelled_at,
 encode(sha256(convert_to(LENGTH(p.org_code)||':'||p.org_code||
 LENGTH(p.data->>'solicitante')||':'||(p.data->>'solicitante')||
 LENGTH(p.data->>'origem')||':'||(p.data->>'origem')||l.signature_lines,'UTF8')),'hex') AS signature
FROM load_orders p
JOIN (SELECT order_code,string_agg(LENGTH(item_code)||':'||item_code||
 LENGTH(CAST(trim_scale(quantity) AS TEXT))||':'||CAST(trim_scale(quantity) AS TEXT),'' ORDER BY item_code COLLATE "C") AS signature_lines
 FROM load_lines GROUP BY order_code) l ON l.order_code=p.code
JOIN orgaos o ON o.codigo=p.org_code AND o.import_job_id=:job
WHERE NOT EXISTS(SELECT 1 FROM import_issues x WHERE x.job_id=:job AND x.active=true AND x.source IN ('pedidos.csv','pedido_itens.csv') AND x.record_key=p.code)
AND NOT EXISTS(SELECT 1 FROM load_lines x WHERE x.order_code=p.code AND NOT EXISTS(SELECT 1 FROM itens i WHERE i.codigo=x.item_code AND i.import_job_id=:job))
AND (NOT EXISTS(SELECT 1 FROM pedidos existing WHERE existing.id_externo=p.code)
 OR EXISTS(SELECT 1 FROM import_quarantine q WHERE q.job_id=:job AND q.entity_type='ORDER' AND q.record_key=p.code));
CREATE UNIQUE INDEX ON review_targets(code);

DELETE FROM pedido_itens WHERE pedido_id IN (
 SELECT p.id FROM pedidos p JOIN review_targets t ON t.code=p.id_externo WHERE p.import_job_id=:job
);

INSERT INTO pedidos(id_externo,codigo_orgao,solicitante,origem,assinatura,data_hora,situacao,data_cancelamento,motivo_cancelamento,import_job_id)
SELECT code,org_code,data->>'solicitante',data->>'origem',signature,created_at,status,cancelled_at,
 CASE WHEN status='CANCELADO' THEN NULLIF(data->>'motivo_cancelamento','') ELSE NULL END,:job
FROM review_targets ORDER BY code COLLATE "C"
ON CONFLICT(id_externo) DO UPDATE SET codigo_orgao=EXCLUDED.codigo_orgao,solicitante=EXCLUDED.solicitante,origem=EXCLUDED.origem,
 assinatura=EXCLUDED.assinatura,data_hora=EXCLUDED.data_hora,situacao=EXCLUDED.situacao,data_cancelamento=EXCLUDED.data_cancelamento,motivo_cancelamento=EXCLUDED.motivo_cancelamento
WHERE pedidos.import_job_id=:job;

INSERT INTO pedido_itens(pedido_id,codigo_item,quantidade)
SELECT p.id,l.item_code,l.quantity FROM load_lines l JOIN review_targets t ON t.code=l.order_code
JOIN pedidos p ON p.id_externo=t.code AND p.import_job_id=:job ORDER BY p.id,l.item_code COLLATE "C";

UPDATE import_jobs SET
 published_orgs=(SELECT COUNT(*) FROM orgaos WHERE import_job_id=:job),
 published_items=(SELECT COUNT(*) FROM itens WHERE import_job_id=:job),
 published_orders=(SELECT COUNT(*) FROM pedidos WHERE import_job_id=:job),
 published_lines=(SELECT COUNT(*) FROM pedido_itens l JOIN pedidos p ON p.id=l.pedido_id WHERE p.import_job_id=:job)
WHERE id=:job;
