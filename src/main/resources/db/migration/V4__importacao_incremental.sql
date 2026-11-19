ALTER TABLE import_jobs ADD COLUMN manifest_ready BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE import_jobs ADD COLUMN published_orgs BIGINT NOT NULL DEFAULT 0;
ALTER TABLE import_jobs ADD COLUMN published_items BIGINT NOT NULL DEFAULT 0;
ALTER TABLE import_jobs ADD COLUMN published_orders BIGINT NOT NULL DEFAULT 0;
ALTER TABLE import_jobs ADD COLUMN published_lines BIGINT NOT NULL DEFAULT 0;
ALTER TABLE import_batches ADD COLUMN publication_done BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE import_batches ADD COLUMN order_counts TEXT;
CREATE INDEX idx_import_batches_publication ON import_batches(job_id,source,done,publication_done);
CREATE TABLE import_sources (
 job_id VARCHAR(64) NOT NULL REFERENCES import_jobs(id), source VARCHAR(30) NOT NULL,
 expected_batches INTEGER NOT NULL CHECK(expected_batches>=0),
 published BOOLEAN NOT NULL DEFAULT FALSE,
 PRIMARY KEY(job_id,source)
);
CREATE TABLE import_order_manifest (
 job_id VARCHAR(64) NOT NULL REFERENCES import_jobs(id), order_code VARCHAR(128) NOT NULL,
 expected_lines BIGINT NOT NULL CHECK(expected_lines>0), PRIMARY KEY(job_id,order_code)
);
ALTER TABLE orgaos ADD COLUMN import_job_id VARCHAR(64) REFERENCES import_jobs(id);
ALTER TABLE itens ADD COLUMN import_job_id VARCHAR(64) REFERENCES import_jobs(id);
ALTER TABLE pedidos ADD COLUMN import_job_id VARCHAR(64) REFERENCES import_jobs(id);
CREATE INDEX idx_orgaos_import_job ON orgaos(import_job_id);
CREATE INDEX idx_itens_import_job ON itens(import_job_id);
CREATE INDEX idx_pedidos_import_job ON pedidos(import_job_id);
