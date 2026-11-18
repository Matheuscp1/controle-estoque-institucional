INSERT INTO orgaos(codigo,sigla,nome,uf,import_job_id)
SELECT code,data->>'sigla',data->>'nome',data->>'uf',:job
FROM (
 SELECT record_key AS code,CAST(MIN(payload) AS jsonb) AS data
 FROM import_rows WHERE job_id=:job AND source='orgaos.csv'
 GROUP BY record_key HAVING COUNT(*) FILTER(WHERE error IS NOT NULL)=0 AND COUNT(DISTINCT payload)=1
) valid
ORDER BY code COLLATE "C"
ON CONFLICT(codigo) DO NOTHING
