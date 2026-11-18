# API de pedidos

O módulo `orders` atende o pedido inteiro em uma transação: cabeçalho, linhas e saldos são confirmados juntos. Qualquer falha desfaz todas essas alterações.

## Criar e reenviar

`POST /api/pedidos`:

```json
{
  "idExterno": "integracao-2026-0001",
  "codigoOrgao": "ORG-0001",
  "solicitante": "Maria",
  "origem": "INTEGRACAO",
  "itens": [
    { "codigoItem": "ITEM-0001", "quantidade": 3 },
    { "codigoItem": "ITEM-0002", "quantidade": 1.5 }
  ]
}
```

Retorna `200` com o pedido criado ou já existente. A resposta contém `id`, `idExterno`, `codigoOrgao`, `solicitante`, `origem`, `dataHora`, `situacao`, `dataCancelamento`, `motivoCancelamento` e `itens`. A assinatura interna não é exposta.

Premissas:

- `idExterno` é obrigatório e único globalmente, inclusive entre órgãos e origens. O portal também precisa gerar uma chave estável para seus reenvios.
- Reenviar a mesma chave e o mesmo conteúdo retorna o mesmo pedido. Uma chave com outro conteúdo retorna `409`, sem sobrescrever o original.
- A comparação inclui órgão, solicitante, origem e pares item/quantidade. A ordem das linhas e zeros decimais finais não alteram a assinatura SHA-256. Prefixos com o tamanho dos campos evitam ambiguidades na concatenação.
- Códigos e textos são preservados como recebidos. Quantidades devem ser positivas e caber em `NUMERIC(19,3)`; o JSON aceita até três casas decimais. Um item só pode aparecer uma vez por pedido.
- Não há limite de 100 itens por pedido: o limite de 100 refere-se somente à paginação das consultas.
- Um pedido novo exige órgão e itens existentes, itens ativos e saldo suficiente para todas as linhas. Pedidos rejeitados não ficam registrados como atendidos parcialmente.
- Reenviar um pedido já cancelado retorna seu estado cancelado e não realiza outra reserva.

## Consultar

`GET /api/pedidos/{id}` retorna um pedido com suas linhas, ou `404`.

`GET /api/orgaos/{codigo}/pedidos?limite=50` lista os pedidos de um órgão por ID decrescente. Órgão inexistente retorna `404`; órgão sem pedidos retorna `[]`.

| Parâmetro | Regra |
| --- | --- |
| `inicio` | Data ISO-8601 com fuso, inclusiva. |
| `fim` | Data ISO-8601 com fuso, exclusiva. Deve ser posterior ao início. |
| `situacao` | `CONFIRMADO` ou `CANCELADO`. |
| `antes` | ID positivo do último pedido da página anterior. |
| `limite` | De 1 a 100, padrão 50. |

As datas são filtradas por criação do pedido. A paginação usa cursor, sem `OFFSET` nem contagem total. As linhas da página são carregadas em uma segunda consulta, evitando uma consulta por pedido e evitando paginação de coleções em memória. A listagem não representa um snapshot imutável entre páginas se houver operações simultâneas.

## Cancelar

`POST /api/pedidos/{id}/cancelamento`:

```json
{ "motivo": "Solicitação feita por engano" }
```

Retorna `200` com o pedido cancelado. O motivo é obrigatório, com até 500 caracteres. A data é gerada pelo servidor e não pode anteceder a criação. Os instantes gerados usam UTC e precisão de microssegundos, compatível com as colunas do PostgreSQL.

Essa exigência vale para novos cancelamentos pela API. Pedidos já cancelados no legado podem ser importados sem motivo; nesse caso as consultas retornam `motivoCancelamento: null`. A ausência do motivo não permite cancelar o pedido novamente.

As quantidades são devolvidas mesmo se o item tiver sido inativado após o pedido. Um segundo cancelamento retorna `409`. Se alguma devolução ultrapassar a capacidade de `NUMERIC(19,3)`, a operação inteira retorna conflito: nenhum saldo é alterado e o pedido continua confirmado.

## Concorrência e erros

O cabeçalho é inserido antes de bloquear os itens, para resolver a disputa da chave externa primeiro. Se outra transação já tiver inserido a chave, a transação perdedora é revertida; somente depois ocorre a consulta do pedido existente em uma nova transação. Esse detalhe é necessário porque uma violação de unicidade invalida a transação no PostgreSQL.

Reserva e devolução usam bloqueio pessimista dos itens em ordem crescente de código. O cancelamento também bloqueia o cabeçalho antes de conferir a situação. Os saldos são atualizados por entidades gerenciadas, com incremento de `@Version`: uma edição de item baseada em versão anterior recebe conflito.

| HTTP | Situação |
| --- | --- |
| `400` | JSON, campos, quantidades ou filtros inválidos; itens repetidos. |
| `404` | Pedido, órgão ou item inexistente. |
| `409` | Saldo insuficiente, item inativo, chave reutilizada com outro conteúdo, segundo cancelamento, devolução acima do limite ou conflito concorrente. |

A operação é síncrona e transacional no banco. RabbitMQ será utilizado na carga histórica; não é necessário para registrar cada pedido da API.
