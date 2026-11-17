INSERT INTO import_issues(job_id,source,line_number,record_key,reason)
SELECT job_id,source,line_number,record_key,error FROM import_rows WHERE job_id=:job AND error IS NOT NULL;

INSERT INTO import_issues(job_id,source,reason)
SELECT :job,s.name,'EMPTY_SOURCE: arquivo sem registros'
FROM (VALUES ('orgaos.csv'),('itens.csv'),('pedidos.csv'),('pedido_itens.csv')) AS s(name)
WHERE NOT EXISTS (SELECT 1 FROM import_rows r WHERE r.job_id=:job AND r.source=s.name);

INSERT INTO import_issues(job_id,source,line_number,record_key,reason)
SELECT :job,source,MIN(line_number),record_key,'CONFLICTING_DUPLICATE: mesma chave com conteúdo divergente'
FROM import_rows WHERE job_id=:job AND error IS NULL AND source<>'pedido_itens.csv'
GROUP BY source,record_key HAVING COUNT(DISTINCT payload)>1;

INSERT INTO import_issues(job_id,source,line_number,record_key,reason)
SELECT :job,source,MIN(line_number),record_key,'DUPLICATE_ORDER_ITEM: item repetido no pedido'
FROM import_rows WHERE job_id=:job AND source='pedido_itens.csv' AND error IS NULL
GROUP BY source,record_key,reference_key HAVING COUNT(*)>1;

CREATE TEMP TABLE load_orgs ON COMMIT DROP AS
SELECT record_key AS code,CAST(MIN(payload) AS jsonb) AS data FROM import_rows
WHERE job_id=:job AND source='orgaos.csv' AND error IS NULL GROUP BY record_key;
CREATE UNIQUE INDEX ON load_orgs(code);

CREATE TEMP TABLE load_items ON COMMIT DROP AS
SELECT code,data,CAST(data->>'saldo_inicial' AS NUMERIC(19,3)) AS initial_balance,
CAST(data->>'saldo_atual' AS NUMERIC(19,3)) AS balance
FROM (SELECT record_key AS code,CAST(MIN(payload) AS jsonb) AS data FROM import_rows
WHERE job_id=:job AND source='itens.csv' AND error IS NULL GROUP BY record_key) s;
CREATE UNIQUE INDEX ON load_items(code);

CREATE TEMP TABLE load_orders ON COMMIT DROP AS
SELECT code,data,data->>'codigo_orgao' AS org_code,data->>'situacao' AS status,
CAST(data->>'data_hora' AS TIMESTAMP WITH TIME ZONE) AS created_at,
CAST(NULLIF(data->>'data_cancelamento','') AS TIMESTAMP WITH TIME ZONE) AS cancelled_at
FROM (SELECT record_key AS code,CAST(MIN(payload) AS jsonb) AS data FROM import_rows
WHERE job_id=:job AND source='pedidos.csv' AND error IS NULL GROUP BY record_key) s;
CREATE UNIQUE INDEX ON load_orders(code);

CREATE TEMP TABLE load_lines ON COMMIT DROP AS
SELECT line_number,record_key AS order_code,reference_key AS item_code,
CAST(CAST(payload AS jsonb)->>'quantidade' AS NUMERIC(19,3)) AS quantity
FROM import_rows WHERE job_id=:job AND source='pedido_itens.csv' AND error IS NULL;
CREATE INDEX ON load_lines(order_code,item_code);
CREATE INDEX ON load_lines(item_code);
ANALYZE load_orgs;
ANALYZE load_items;
ANALYZE load_orders;
ANALYZE load_lines;

INSERT INTO import_issues(job_id,source,record_key,reason)
SELECT :job,'pedidos.csv',p.code,'UNKNOWN_ORGANIZATION: órgão inexistente ou inválido'
FROM load_orders p WHERE NOT EXISTS(SELECT 1 FROM load_orgs o WHERE o.code=p.org_code);

INSERT INTO import_issues(job_id,source,line_number,record_key,reason)
SELECT :job,'pedido_itens.csv',l.line_number,l.order_code,'UNKNOWN_ORDER: pedido inexistente ou inválido'
FROM load_lines l WHERE NOT EXISTS(SELECT 1 FROM load_orders p WHERE p.code=l.order_code);

INSERT INTO import_issues(job_id,source,line_number,record_key,reason)
SELECT :job,'pedido_itens.csv',l.line_number,l.order_code,'UNKNOWN_ITEM: item inexistente ou inválido'
FROM load_lines l WHERE NOT EXISTS(SELECT 1 FROM load_items i WHERE i.code=l.item_code);

INSERT INTO import_issues(job_id,source,record_key,reason)
SELECT :job,'pedidos.csv',p.code,'EMPTY_ORDER: pedido sem linhas válidas'
FROM load_orders p WHERE NOT EXISTS(SELECT 1 FROM load_lines l WHERE l.order_code=p.code);

INSERT INTO import_issues(job_id,source,record_key,reason)
SELECT :job,'itens.csv',i.code,'BALANCE_MISMATCH: saldo atual diverge do saldo inicial menos pedidos confirmados'
FROM load_items i LEFT JOIN
(SELECT l.item_code,SUM(l.quantity) AS consumed FROM load_lines l JOIN load_orders p ON p.code=l.order_code
 WHERE p.status='CONFIRMADO' GROUP BY l.item_code) c ON c.item_code=i.code
WHERE i.initial_balance-COALESCE(c.consumed,0)<>i.balance;

INSERT INTO import_issues(job_id,source,record_key,reason)
SELECT DISTINCT :job,'itens.csv',t.item_code,'NEGATIVE_HISTORICAL_BALANCE: saldo negativo na reconstrução cronológica'
FROM (
 SELECT e.item_code,i.initial_balance+SUM(e.delta) OVER
 (PARTITION BY e.item_code ORDER BY e.event_time,e.kind,e.order_code,e.line_number ROWS UNBOUNDED PRECEDING) AS balance
 FROM (
  SELECT l.item_code,p.created_at AS event_time,0 AS kind,p.code AS order_code,l.line_number,-l.quantity AS delta
  FROM load_lines l JOIN load_orders p ON p.code=l.order_code
  UNION ALL
  SELECT l.item_code,p.cancelled_at,1,p.code,l.line_number,l.quantity
  FROM load_lines l JOIN load_orders p ON p.code=l.order_code WHERE p.status='CANCELADO'
 ) e JOIN load_items i ON i.code=e.item_code
) t WHERE t.balance<0;

UPDATE import_issues i SET line_number=(
 SELECT MIN(r.line_number) FROM import_rows r
 WHERE r.job_id=i.job_id AND r.source=i.source AND r.record_key=i.record_key
)
WHERE i.job_id=:job AND i.line_number IS NULL AND i.record_key IS NOT NULL;
