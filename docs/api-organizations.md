# API de órgãos

O módulo `organizations` oferece cadastro e consulta dos órgãos que poderão realizar pedidos. Utiliza domínio independente do Spring, casos de uso, DTOs HTTP e persistência JPA. O cadastro usa a tabela `orgaos`, criada pela migração inicial.

| Método | Caminho | Resultado |
| --- | --- | --- |
| POST | `/api/orgaos` | Cadastro; HTTP 201 |
| GET | `/api/orgaos/{codigo}` | Consulta pelo código |
| GET | `/api/orgaos?limite=50&depois=ORG-0001` | Consulta paginada por código |

Exemplo de cadastro:

```json
{"codigo":"ORG-0001","sigla":"SEC","nome":"Secretaria de Educação","uf":"SP"}
```

Código, sigla e nome são obrigatórios, com limites de 64, 30 e 255 caracteres. A UF é normalizada para maiúsculas, sem espaços nas extremidades, e precisa corresponder a uma das 27 unidades federativas. Os códigos são preservados como texto, incluindo zeros à esquerda, e diferenciam maiúsculas de minúsculas.

As respostas usam `codigo`, `sigla`, `nome` e `uf`. A listagem retorna um array em ordem de código, com limite padrão 50 e máximo 100. A próxima página usa o último código recebido em `depois`; uma página vazia encerra a consulta. Não há contagem total por página.

Entradas inválidas retornam 400, código ausente retorna 404 e código duplicado retorna 409. Uma tentativa de cadastro com código existente não atualiza o registro anterior. Como o ID é atribuído pelo cliente e não existe coluna de versão nessa tabela, a entidade implementa `Persistable`: novos cadastros usam `persist`, evitando que `save` seja interpretado como atualização via `merge`.

A chave primária garante unicidade mesmo em cadastros simultâneos. Não há endpoints de edição ou exclusão nesta etapa; pedidos históricos continuarão referenciando o código do órgão. Autenticação e distinção de permissões do órgão central ainda não estão implementadas.

## Testes

```sh
mvn "-Dtest=OrganizationTest,OrganizationServiceTest,OrganizationApiTest" test
```

Os testes cobrem normalização, validações, paginação, ausência de registros, duplicidade e criação concorrente. Os testes unitários rodam sem Spring; os testes da API usam H2. A validação específica no PostgreSQL permanece necessária.
