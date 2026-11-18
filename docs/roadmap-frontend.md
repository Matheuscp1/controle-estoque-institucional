# Roadmap do front-end

O front do desafio usa Vue 2 e Vuetify 2. As telas consomem a API Spring Boot pelo caminho relativo `/api`.

| Etapa | Commit | Critério de conclusão |
| --- | --- | --- |
| 1. Base | `chore(frontend): iniciar Vue 2 e Vuetify` | O projeto compila e apresenta a navegação e o cliente HTTP. |
| 2. Consulta de itens | `feat(frontend): consultar itens com filtros e cursor` | Os filtros e a paginação são processados pelo servidor. A tela mostra carregamento, ausência de resultados e erros. |
| 3. Edição de itens | `feat(frontend): editar itens com controle de versão` | A edição envia a versão do item e exige uma revisão dos dados atuais quando recebe 409. |
| 4. Consulta de pedidos | `feat(frontend): consultar pedidos por órgão` | É possível selecionar um órgão, filtrar os pedidos e consultar suas linhas com paginação. |
| 5. Cancelamento | `feat(frontend): cancelar pedidos com confirmação` | A ação exige motivo, impede envios simultâneos no mesmo formulário e atualiza a situação pela API. |
| 6. Entrega | `build(frontend): integrar a interface ao Docker Compose` | O Compose serve a interface e encaminha a API ao backend. O README explica a execução. |

## Contratos de referência

- [Itens](api-itens.md) usa `depois` para paginação e `versao` na edição.
- [Órgãos](api-organizations.md) fornece os dados para seleção.
- [Pedidos](api-orders.md) usa `antes` para paginação, `inicio` inclusivo e `fim` exclusivo. A confirmação de cancelamento envia `motivo`.
- [Carga histórica](importacao.md) descreve o bloqueio das APIs operacionais durante a carga inicial.
