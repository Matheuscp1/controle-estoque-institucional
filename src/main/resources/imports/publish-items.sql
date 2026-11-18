INSERT INTO itens(codigo,descricao,unidade,saldo_inicial,saldo,ativo,versao,data_cadastro,import_job_id)
SELECT code,data->>'descricao',data->>'unidade',CAST(data->>'saldo_inicial' AS NUMERIC(19,3)),
CAST(data->>'saldo_atual' AS NUMERIC(19,3)),data->>'ativo'='S',0,
CAST(data->>'data_cadastro' AS TIMESTAMP WITH TIME ZONE),:job
FROM (
 SELECT record_key AS code,CAST(MIN(payload) AS jsonb) AS data
 FROM import_rows WHERE job_id=:job AND source='itens.csv'
 GROUP BY record_key HAVING COUNT(*) FILTER(WHERE error IS NOT NULL)=0 AND COUNT(DISTINCT payload)=1
) valid
ORDER BY code COLLATE "C"
ON CONFLICT(codigo) DO NOTHING
