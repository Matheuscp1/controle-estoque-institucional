# Revisão manual da importação

Uma carga termina em `COMPLETED_WITH_ISSUES` quando o processamento acabou, mas há dados a corrigir. O responsável pode revisar esses dados enquanto os registros consistentes são usados. As migrações V6 e V7 adicionam revisões, histórico de correções, restrições operacionais e os novos estados.

## Consultar e corrigir

1. Consulte `GET /api/imports/{id}/issues?after=0&limit=100`. As ocorrências atuais incluem arquivo, linha, chave, motivo, original, campos atuais e `revision`. Para consultar também ocorrências de validações anteriores, use `history=true`. `active=false` significa que a ocorrência pertence a uma validação anterior; não prova, isoladamente, que seu problema foi resolvido. A validação atual é a referência.
2. Consulte todas as linhas de uma chave, inclusive duplicatas, em `GET /api/imports/{id}/rows?source=itens.csv&key=A&after=0&limit=100`. `original` e `fields` são textos JSON; no envio da correção, `fields` deve ser um objeto JSON.
3. Envie os campos CSV completos, sem alterar o identificador, junto com a revisão lida e uma justificativa. Datas mantêm o formato do CSV, sem offset.

```http
PUT /api/imports/{id}/rows/itens.csv/2
Content-Type: application/json

{
  "revision": 0,
  "justification": "Saldo conferido com o histórico de pedidos",
  "fields": {
    "codigo": "A", "descricao": "Papel", "unidade": "UN",
    "saldo_inicial": "10", "saldo_atual": "7", "ativo": "S",
    "data_cadastro": "2020-01-01"
  }
}
```

A resposta `200` contém `line` e a nova `revision`. Revisão desatualizada retorna `409`, sem sobrescrever a correção de outra pessoa. O ZIP e `raw_payload` nunca são modificados. Os campos corrigidos, conteúdo anterior, justificativa e horário ficam em `import_corrections`. Consulte esse histórico por `GET /api/imports/{id}/corrections?after=0&limit=100`.

## Registros ausentes e duplicatas

`POST /api/imports/{id}/rows/{source}` recebe o mesmo corpo, com `revision: 0`, e adiciona uma linha para um identificador pendente. Retorna `201` com linha e revisão. Permite recuperar cadastros ou cabeçalhos ausentes e adicionar linhas a pedidos pendentes. Não serve como cadastro operacional comum. Registros adicionados manualmente têm original `{}` e conteúdo integral no histórico de correções.

Uma duplicata normalizada idêntica pode ser desconsiderada com:

```http
POST /api/imports/{id}/rows/pedido_itens.csv/99/exclude-duplicate
Content-Type: application/json

{"revision":0,"justification":"Duplicação confirmada na origem"}
```

A resposta é `204`. A linha permanece armazenada com `excluded=true`; outra cópia válida precisa continuar existindo. O endpoint recusa apagar a última linha ou simplesmente retirar um item diferente para aproveitar parte de um pedido. Duplicatas divergentes exigem conferência e correção dos dados antes de qualquer exclusão.

## Reprocessar

Depois das correções, envie `POST /api/imports/{id}/reprocess`. Retorna `202` e agenda `REVIEW_PENDING`; acompanhe `GET /api/imports/{id}`. Repetir o pedido enquanto ele estiver agendado não agenda outra execução. A etapa refaz a validação global, sincroniza os registros elegíveis e atualiza as restrições em uma transação. Pode demorar conforme o tamanho do histórico, mas não reenvia a massa para a fila.

O resultado será `COMPLETED` se não houver mais ocorrências ou `COMPLETED_WITH_ISSUES` com as pendências restantes. As linhas de um pedido são publicadas juntas; pedidos existentes preservam seu ID. Saldos e pedidos já liberados não são restaurados a partir do CSV, preservando as operações online. Uma falha técnica desfaz a publicação dessa revisão, registra o detalhe em `error` e retorna a `COMPLETED_WITH_ISSUES`, permitindo ajustar os dados ou solicitar `/reprocess` novamente. Não fica repetindo uma revisão defeituosa indefinidamente.

## Proteções e limites

- Consulte `GET /api/imports/{id}/quarantine?type=ITEM&after=&limit=100`; os tipos são `ITEM`, `ORDER` e `ORGANIZATION`. O cursor é a última `key` retornada. Consultas operacionais continuam disponíveis, inclusive para conferir registros provisórios.
- Itens com ocorrências de saldo ou ligados a pedidos inválidos ficam protegidos contra edição, reserva e devolução. Um pedido com qualquer item protegido não pode ser cancelado parcialmente. Pedidos pendentes reservam seu identificador externo. Órgãos pendentes não podem receber novos pedidos.
- A correção pode alterar dados ainda em revisão. Trocar identificadores ou envolver itens já liberados é recusado com `409`/`400`: isso exigiria uma reconciliação específica com as movimentações online, que este fluxo não implementa. Não se deve contornar essa proteção alterando o banco diretamente.
- Somente duplicatas idênticas podem ser desconsideradas por esse endpoint. Não há descarte genérico de histórico nem botão que marque uma ocorrência como resolvida sem revalidar os dados.
- Edições e solicitações de reprocessamento são serializadas pelo controle da carga; cada linha exige sua revisão. Durante `REVIEW_PENDING`, novas correções são recusadas até concluir a validação.
- Ao iniciar esta versão, cargas antigas em `NEEDS_REVIEW` com inserção incremental concluída entram na revalidação automaticamente. Não é necessário reenviar o ZIP. Cargas anteriores ao manifesto incremental continuam usando `/retry` primeiro.
- O projeto ainda não possui autenticação/perfis de gestor; a justificativa é registrada, mas não há identidade autenticada atribuída ao autor da correção.
