# API de itens

Base: `/api/itens`. Os corpos de entrada e saída usam camelCase. Códigos diferenciam maiúsculas de minúsculas e não são editáveis.

| Método | Caminho | Resultado |
| --- | --- | --- |
| POST | `/api/itens` | Cadastro; HTTP 201 |
| GET | `/api/itens/{codigo}` | Consulta por código |
| GET | `/api/itens` | Lista filtrada e paginada |
| PUT | `/api/itens/{codigo}` | Edição com versão obrigatória |
| DELETE | `/api/itens/{codigo}?versao=0` | Inativação lógica com versão obrigatória |

## Cadastro

```json
{"codigo":"PAPEL","descricao":"Papel A4","unidade":" un ","saldo":100}
```

A unidade é normalizada para `UN`. O item começa ativo, com versão zero e saldo inicial igual ao disponível. Saldos aceitam até 16 algarismos inteiros e três casas decimais, sem valores negativos. Código duplicado retorna 409.

## Edição

```json
{"descricao":"Papel A4 branco","unidade":"UN","saldo":95,"ativo":true,"versao":0}
```

Use a versão recebida na última consulta. A operação incrementa a versão, controlada por `@Version` no JPA, e preserva código, saldo inicial e data de cadastro. Uma versão desatualizada retorna 409; o cliente deve recarregar os dados antes de tentar novamente. A edição permite corrigir saldo e reativar o item. Auditoria dos ajustes manuais ainda não está implementada.

Inativar preserva o registro e seu saldo; os itens inativos continuam aparecendo nas consultas. A resposta contém o item com `ativo: false` e a nova versão.

## Consultas

Filtros opcionais: `descricao` (trecho sem diferenciar caixa), `unidade` (igualdade após normalização), `saldoMin` e `saldoMax` (inclusivos). A faixa precisa ser não negativa, com mínimo menor ou igual ao máximo. Os caracteres `%` e `_` da descrição são tratados como texto literal.

Paginação: `limite` entre 1 e 100, padrão 50, ordenação por código. Para a próxima página, envie o último código como `depois`. A resposta é um array; uma página vazia encerra a consulta. Não há cálculo de total a cada página.

```text
GET /api/itens?descricao=papel&unidade=UN&saldoMin=10&saldoMax=200&limite=50
GET /api/itens?descricao=papel&unidade=UN&saldoMin=10&saldoMax=200&limite=50&depois=PAPEL
```

Campos de resposta: `codigo`, `descricao`, `unidade`, `saldoInicial`, `saldo`, `ativo`, `versao` e `dataCadastro` (ISO-8601 com offset).

## Erros

400 para entradas inválidas; 404 para item não encontrado; 409 para código duplicado ou conflito de versão. O corpo contém `mensagem` e a lista `erros`, preenchida com detalhes de validação quando aplicável. Não são expostas instruções SQL.

O módulo ainda não possui autenticação/autorização. O Compose limita a porta ao localhost para desenvolvimento.
