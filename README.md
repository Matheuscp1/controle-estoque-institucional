# Estoque Institucional — front-end

Interface em Vue 2 e Vuetify 2 para o desafio de controle de saldo de itens e pedidos de órgãos públicos.

## Executar

O back-end precisa estar disponível em `http://localhost:8080`. O código da API está na branch `back-end` deste repositório.

Com Node.js 22:

```sh
cd frontend
npm ci
npm run dev
```

O Vite mostra a URL local. Para usar outra API, configure `API_TARGET` antes de iniciar o servidor.

Com Docker:

```sh
docker compose up --build
```

A interface fica em `http://localhost:8081`. O Nginx encaminha `/api` ao back-end na porta `8080` do host. Para outro endereço acessível ao contêiner, configure `API_UPSTREAM` no serviço `frontend` do Compose.

## Testes e TDD

No diretório `frontend`, execute `npm test` para os testes unitários e de componentes, ou `npm run test:watch` durante o desenvolvimento. O ciclo de TDD é: escrever um teste para o comportamento esperado, confirmar que falha, implementar a alteração e executar novamente os testes e `npm run typecheck`.

Os testes cobrem valores decimais, contrato HTTP, paginação com cancelamento de requisições, edição de item e cancelamento de pedido. As chamadas à API são simuladas; esses testes não dependem do back-end em execução.

## Organização do código

`frontend/src/views/items` e `frontend/src/views/orders` contêm as telas e seus componentes específicos. `frontend/src/components` reúne componentes usados por mais de uma tela; `services` concentra o acesso à API; `utils` guarda funções sem dependência da interface. Os testes ficam ao lado dos arquivos testados.

## Funcionalidades

- Consulta de itens por descrição, unidade e faixa de saldo, com paginação por cursor e total correspondente aos filtros.
- Edição de descrição, unidade, saldo e situação, com controle de versão. Um conflito exige recarregar e revisar os dados.
- Consulta de pedidos de um órgão por período e situação, com itens, quantidades e total de pedidos correspondente aos filtros.
- Cancelamento de pedido confirmado com motivo e atualização da lista.

As datas de filtro abrangem os dias selecionados no fuso do navegador. O cliente conserva a precisão decimal dos saldos permitidos pela API. A tela informa erros, listas vazias e o bloqueio HTTP 503 da carga inicial.

O histórico de implementação por etapa está no [roadmap](docs/roadmap-frontend.md).

## Uso de IA

O Codex cruzou o enunciado com os contratos da API, para melhor desempenho com teste
