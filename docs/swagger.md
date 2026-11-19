# Documentação interativa da API

Com o backend em execução, abra:

- Swagger UI: http://localhost:8080/swagger-ui.html
- Especificação OpenAPI JSON: http://localhost:8080/v3/api-docs
- Especificação OpenAPI YAML: http://localhost:8080/v3/api-docs.yaml

A documentação permanece acessível antes da carga inicial. As operações estão agrupadas em **Itens**, **Órgãos**, **Pedidos** e **Importação**, com exemplos de requisição, parâmetros, schemas de resposta e erros. O contrato é gerado a partir dos controllers, com descrições e exemplos nos adaptadores HTTP e em `OpenApiConfiguration`; não altera as regras de negócio.

## Testar pelo Swagger UI

Expanda uma operação, selecione **Try it out**, preencha os parâmetros ou JSON e clique em **Execute**. Os códigos, IDs, saldos e versões dos exemplos devem ser ajustados aos dados da sua base. As chamadas executam operações reais no backend configurado.

Para carregar o histórico:

1. No grupo **Importação**, expanda `POST /api/imports` e clique em **Try it out**.
2. Em **file**, clique em **Choose File / Escolher arquivo**. Selecione `massa-dados/massa-dados-candidato.zip`, sem descompactar. O ZIP deve ter até 128 MiB e conter `orgaos.csv`, `itens.csv`, `pedidos.csv` e `pedido_itens.csv` na raiz.
3. Clique em **Execute**. Confira **Server response: 202** e copie o `id` do **Response body**. Esse retorno confirma o recebimento, não o fim do processamento.
4. Expanda `GET /api/imports/active`, clique em **Try it out** e **Execute**. A rota retorna a carga atual sem informar ID, inclusive após conclusão ou falha; `204` indica que nenhuma carga foi registrada. Também pode usar `GET /api/imports/{id}`, preenchendo o ID copiado, sem aspas.
5. Repita **Execute** para atualizar o status; o Swagger não acompanha automaticamente. Observe lotes e registros publicados. Em `PREPARING`, o total ainda pode ser zero; após concluir os lotes, pode faltar a validação final.
6. Em `COMPLETED`, a carga terminou sem ocorrências ativas. Em `COMPLETED_WITH_ISSUES`, siga a revisão abaixo. Em `FAILED`, confira `error` e os logs antes de retomar.

### Revisar as ocorrências pelo Swagger

1. Em `GET /api/imports/{id}/issues`, preencha `id`, `after=0`, `limit=100` e `history=false`. Execute e escolha uma ocorrência para analisar. Anote arquivo, chave, linha e motivo. O cursor da página seguinte é o último ID de ocorrência.
2. Em `GET /api/imports/{id}/rows`, preencha o mesmo `id`, `source` com o nome do CSV e `key` com a chave identificada. Use `after=0` e `limit=100`. Confira os campos atuais e a revisão de cada linha. Para `pedido_itens.csv`, a chave identifica o pedido e permite conferir todas as suas linhas.
3. Em `PUT /api/imports/{id}/rows/{source}/{line}`, preencha o arquivo e a linha real retornada. Substitua o **Request body** por todos os campos do CSV, a `revision` atual e uma `justification`. O [README contém um exemplo completo](../README.md#consultar-e-corrigir-inconsistências). Clique em **Execute**; `200` retorna a nova revisão. `409` exige verificar o conflito antes de tentar novamente.
4. Se a análise exigir um registro ausente, use `POST /api/imports/{id}/rows/{source}`, com campos completos, revisão zero e justificativa. Para uma cópia comprovadamente idêntica, use `POST /api/imports/{id}/rows/{source}/{line}/exclude-duplicate`, enviando revisão atual e justificativa. Essas operações respeitam o escopo dos dados pendentes.
5. Após salvar as correções, execute `POST /api/imports/{id}/reprocess`, sem corpo. O retorno `202` agenda `REVIEW_PENDING`. Acompanhe por `/active` ou `/{id}` e aguarde a conclusão antes de corrigir outras linhas.
6. Consulte `/issues` novamente com `history=false`. Use `/quarantine` para conferir registros ainda protegidos e `/corrections` para consultar a auditoria. Uma correção salva só é refletida nas ocorrências após revalidação; não há botão para resolver uma ocorrência sem validar os dados.

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
