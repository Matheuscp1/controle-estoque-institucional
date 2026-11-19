CREATE INDEX idx_pedidos_orgao_id ON pedidos(codigo_orgao, id DESC);
CREATE INDEX idx_pedidos_orgao_situacao_id ON pedidos(codigo_orgao, situacao, id DESC);
