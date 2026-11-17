INSERT INTO orgaos(codigo,sigla,nome,uf)
SELECT code,data->>'sigla',data->>'nome',data->>'uf' FROM load_orgs;

INSERT INTO itens(codigo,descricao,unidade,saldo_inicial,saldo,ativo,versao,data_cadastro)
SELECT code,data->>'descricao',data->>'unidade',initial_balance,balance,data->>'ativo'='S',0,
CAST(data->>'data_cadastro' AS TIMESTAMP WITH TIME ZONE) FROM load_items;

INSERT INTO pedidos(id_externo,codigo_orgao,solicitante,origem,assinatura,data_hora,situacao,data_cancelamento,motivo_cancelamento)
SELECT p.code,p.org_code,p.data->>'solicitante',p.data->>'origem',
encode(sha256(convert_to(
 LENGTH(p.org_code)||':'||p.org_code||
 LENGTH(p.data->>'solicitante')||':'||(p.data->>'solicitante')||
 LENGTH(p.data->>'origem')||':'||(p.data->>'origem')||s.lines,'UTF8')),'hex'),
p.created_at,p.status,p.cancelled_at,CASE WHEN p.status='CANCELADO' THEN p.data->>'motivo_cancelamento' ELSE NULL END
FROM load_orders p JOIN (
 SELECT order_code,string_agg(LENGTH(item_code)||':'||item_code||
 LENGTH(CAST(trim_scale(quantity) AS TEXT))||':'||CAST(trim_scale(quantity) AS TEXT),'' ORDER BY item_code COLLATE "C") AS lines
 FROM load_lines GROUP BY order_code
) s ON s.order_code=p.code;

INSERT INTO pedido_itens(pedido_id,codigo_item,quantidade)
SELECT p.id,l.item_code,l.quantity FROM load_lines l JOIN pedidos p ON p.id_externo=l.order_code;
