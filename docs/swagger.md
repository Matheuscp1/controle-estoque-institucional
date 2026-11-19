# Documentação interativa da API

Com o backend em execução, abra:

- Swagger UI: http://localhost:8080/swagger-ui.html
- Especificação OpenAPI JSON: http://localhost:8080/v3/api-docs
- Especificação OpenAPI YAML: http://localhost:8080/v3/api-docs.yaml

A documentação permanece acessível antes da carga inicial. As operações estão agrupadas em **Itens**, **Órgãos**, **Pedidos** e **Importação**, com exemplos de requisição, parâmetros, schemas de resposta e erros. O contrato é gerado a partir dos controllers, com descrições e exemplos nos adaptadores HTTP e em `OpenApiConfiguration`; não altera as regras de negócio.

## Testar pelo Swagger UI

Expanda uma operação, selecione **Try it out**, preencha os parâmetros ou JSON e clique em **Execute**. Os códigos, IDs, saldos e versões dos exemplos devem ser ajustados aos dados da sua base. As chamadas executam operações reais no backend configurado.

Para carregar o histórico:

1. Abra `POST /api/imports` e selecione o ZIP no campo **file**, de até 128 MiB, contendo os quatro CSVs esperados.
2. Execute e copie o `id` retornado em `202`. Esse retorno confirma o recebimento, não o fim do processamento.
3. Consulte `GET /api/imports/{id}` para acompanhar lotes, registros publicados e status.
4. Se terminar em `COMPLETED_WITH_ISSUES`, consulte `/issues`, `/quarantine` e `/rows`. Corrija as linhas com a revisão atual e uma justificativa, então solicite `/reprocess`.

`retry` retoma uma falha técnica; `reprocess` agenda a validação das correções. Veja as regras completas em [importação](importacao.md) e [revisão manual](revisao-importacao.md).

Nas correções, o exemplo de `fields` corresponde a `itens.csv`. Para outro `source`, envie todas as colunas do respectivo CSV, como valores de texto. As consultas de revisão retornam `original`, `fields`, `previous` e `corrected` como **JSON serializado em texto**, quando presentes; o corpo enviado para corrigir usa `fields` como objeto.

## Contratos importantes

- A criação de pedidos retorna **200**, tanto no primeiro cadastro quanto no reenvio idempotente. Repita `idExterno` com o mesmo conteúdo para recuperar o pedido; conteúdo diferente retorna **409**.
- Pedidos são atendidos ou cancelados integralmente. Cancelar novamente retorna **409**, sem nova devolução.
- Editar ou inativar um item exige a `versao` consultada. Conflitos retornam **409**; recarregue antes de tentar novamente.
- Listagens de itens e pedidos retornam `{ "registros": [...], "total": 123 }`. O total corresponde aos filtros, sem considerar cursor nem tamanho da página. Órgãos e listas de revisão continuam retornando arrays. A paginação permite de 1 a 100 registros, padrão 50, e cada rota identifica o cursor (`depois`, `antes` ou `after`).
- Durante a carga inicial, alterações operacionais retornam **503**, enquanto consultas continuam disponíveis. Registros em quarentena podem retornar **409** mesmo após o término da carga.
- Os headers documentados são `X-Request-ID`, `X-Import-Ready` e `X-Cache`; sua presença depende da operação e das funcionalidades habilitadas. Confira [cache e logs](cache-logs.md).

## Configuração

`API_DOCS_ENABLED=false` desativa a especificação e o Swagger UI após reiniciar a aplicação. Para usar essa variável no Compose, ela é repassada ao serviço backend. A documentação das rotas de importação aparece somente quando `IMPORTS_ENABLED=true`.

Foi usado `springdoc-openapi-ui:1.8.0`, da linha compatível com Spring Boot 2.7. O projeto permanece em Boot 2.7; uma atualização do framework deve rever a versão do springdoc conforme a [matriz oficial de compatibilidade](https://springdoc.org/v1/).
