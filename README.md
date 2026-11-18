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

## Funcionalidades

- Consulta de itens por descrição, unidade e faixa de saldo, com paginação por cursor.
- Edição de descrição, unidade, saldo e situação, com controle de versão. Um conflito exige recarregar e revisar os dados.
- Consulta de pedidos de um órgão por período e situação, com itens e quantidades.
- Cancelamento de pedido confirmado com motivo e atualização da lista.

As datas de filtro abrangem os dias selecionados no fuso do navegador. O cliente conserva a precisão decimal dos saldos permitidos pela API. A tela informa erros, listas vazias e o bloqueio HTTP 503 da carga inicial.

O histórico de implementação por etapa está no [roadmap](docs/roadmap-frontend.md).

## Uso de IA

O Codex cruzou o enunciado com os contratos da API, implementou as telas e verificou os fluxos no navegador. O bundle completo do Vuetify causou falha no build em Linux, então os componentes usados passaram a ser importados individualmente.
