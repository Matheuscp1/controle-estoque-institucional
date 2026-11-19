CREATE TEMP TABLE review_quarantine (entity_type VARCHAR(20),record_key VARCHAR(128),PRIMARY KEY(entity_type,record_key)) ON COMMIT DROP;

INSERT INTO review_quarantine
SELECT DISTINCT CASE source WHEN 'itens.csv' THEN 'ITEM' WHEN 'orgaos.csv' THEN 'ORGANIZATION' ELSE 'ORDER' END,record_key
FROM import_issues WHERE job_id=:job AND active=true AND record_key IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO review_quarantine
SELECT 'ORDER',r.record_key FROM import_rows r WHERE r.job_id=:job AND r.excluded=false AND r.source='pedidos.csv' AND r.record_key IS NOT NULL
AND NOT EXISTS(SELECT 1 FROM pedidos p WHERE p.id_externo=r.record_key AND p.import_job_id=:job)
ON CONFLICT DO NOTHING;

INSERT INTO review_quarantine
SELECT 'ORDER',r.record_key FROM import_rows r JOIN review_quarantine q ON q.entity_type='ORGANIZATION' AND q.record_key=r.reference_key
WHERE r.job_id=:job AND r.excluded=false AND r.source='pedidos.csv' AND r.record_key IS NOT NULL ON CONFLICT DO NOTHING;

INSERT INTO review_quarantine
SELECT 'ITEM',r.reference_key FROM import_rows r JOIN review_quarantine q ON q.entity_type='ORDER' AND q.record_key=r.record_key
WHERE r.job_id=:job AND r.excluded=false AND r.source='pedido_itens.csv' AND r.reference_key IS NOT NULL ON CONFLICT DO NOTHING;

INSERT INTO review_quarantine
SELECT 'ORGANIZATION',r.reference_key FROM import_rows r
WHERE r.job_id=:job AND r.excluded=false AND r.source='pedidos.csv' AND r.reference_key IS NOT NULL
AND EXISTS (SELECT 1 FROM import_issues i WHERE i.job_id=:job AND i.active=true AND i.record_key=r.record_key AND i.reason LIKE 'UNKNOWN_ORGANIZATION:%')
ON CONFLICT DO NOTHING;

INSERT INTO review_quarantine
SELECT 'ITEM',r.record_key FROM import_rows r WHERE r.job_id=:job AND r.excluded=false AND r.source='itens.csv' AND r.record_key IS NOT NULL
AND EXISTS (SELECT 1 FROM import_issues i WHERE i.job_id=:job AND i.active=true AND i.record_key IS NULL)
ON CONFLICT DO NOTHING;

INSERT INTO review_quarantine
SELECT 'ORDER',r.record_key FROM import_rows r WHERE r.job_id=:job AND r.excluded=false AND r.source='pedidos.csv' AND r.record_key IS NOT NULL
AND EXISTS (SELECT 1 FROM import_issues i WHERE i.job_id=:job AND i.active=true AND i.record_key IS NULL)
ON CONFLICT DO NOTHING;

INSERT INTO import_quarantine(job_id,entity_type,record_key)
SELECT :job,entity_type,record_key FROM review_quarantine ON CONFLICT DO NOTHING;
