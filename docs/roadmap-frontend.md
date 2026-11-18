# Roadmap do front-end

O desafio pede Vue 2 e Vuetify 2. As telas usam a API Spring Boot em `/api`.

| Etapa | Funcionalidade |
| --- | --- |
| 1 | Base Vue, Vuetify, navegação e cliente HTTP. |
| 2 | Consulta de itens com filtros e paginação por `depois`. |
| 3 | Edição de itens com `versao` e tratamento de conflito HTTP 409. |
| 4 | Consulta de pedidos por órgão, período e situação, com paginação por `antes`. |
| 5 | Cancelamento de pedido com motivo e confirmação. |
| 6 | Imagem Docker, Compose do front-end e documentação. |

O front consulta `/api/itens`, `/api/orgaos` e `/api/orgaos/{codigo}/pedidos` e envia a edição e o cancelamento para os endpoints correspondentes da API. O back-end responde HTTP 503 enquanto a carga inicial está bloqueada.
