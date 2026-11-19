# Arquitetura do backend

## Decisão

Monólito modular: uma aplicação Spring Boot, um PostgreSQL e módulos organizados por funcionalidade. Itens, órgãos e pedidos usam portas e adaptadores. O módulo `imports` acrescenta RabbitMQ, preparação de dados, inserts incrementais e validação global antes da liberação para uso.

Essa estrutura permite manter transações locais entre pedidos e estoque, necessárias para o atendimento integral, e subir o backend com um único serviço. O desafio não exige deploy ou escala independentes por funcionalidade, portanto microsserviços acrescentariam comunicação e coordenação de transações sem uma necessidade demonstrada.

Dentro de cada módulo usamos camadas com inversão de dependência, seguindo a ideia de portas e adaptadores. Não há interfaces para todas as classes: elas são usadas nos limites que precisam de uma implementação externa, como a persistência.

```text
br.com.estoque
└── items
    ├── domain          Item e suas regras; erros sem códigos HTTP
    ├── application     ItemService, filtros e interface ItemRepository
    ├── api             Controller, requests, responses e tradução de erros
    └── infrastructure  ItemEntity, Spring Data JPA e configuração dos componentes
```

```mermaid
flowchart LR
    API[API HTTP] --> APP[Casos de uso]
    APP --> DOM[Domínio]
    APP --> PORTA[Interface ItemRepository]
    JPA[Adaptador JPA] -. implementa .-> PORTA
    JPA --> PG[(PostgreSQL)]
```

O domínio não importa Spring, JPA ou classes da API. A aplicação recebe o repositório e o relógio por construtor. A infraestrutura conecta as implementações. A API usa DTOs próprios, sem expor entidades persistentes ou mapas genéricos.

`ItemEntity` mapeia a tabela `itens` com `@Entity`, `@Table`, `@Id` e `@Version`. A entidade fica na infraestrutura, separada do `Item` imutável do domínio. Isso preserva as regras sem depender do ciclo de vida do Hibernate. `SpringDataItemRepository` estende `JpaRepository`; `JpaItemRepository` implementa a interface da aplicação e converte entre entidade e domínio. Os filtros usam Criteria API, com limite e cursor. As listagens de itens e pedidos incluem uma consulta COUNT com os mesmos filtros da busca, sem o cursor, para retornar o total junto com os registros da página. Apenas a página é carregada como entidades; a contagem tem custo adicional proporcional à busca no banco.

O Flyway continua sendo a fonte da estrutura do banco. `ddl-auto: validate` faz o Hibernate validar o mapeamento, sem criar ou alterar tabelas. `open-in-view: false` fecha a persistência antes da renderização HTTP. Com Spring Boot 2.7, as anotações são de `javax.persistence`.

## SOLID aplicado

| Princípio | Aplicação concreta |
| --- | --- |
| Responsabilidade única | Controller trata HTTP; serviço coordena; domínio valida e transforma o item; adaptador JPA cuida da persistência. |
| Aberto/fechado | Outro adaptador de persistência pode implementar `ItemRepository` sem alterar os casos de uso. |
| Substituição | Implementações do repositório precisam preservar o contrato, inclusive a atualização atômica condicionada à versão. Um adaptador que ignora a versão não é substituto válido. |
| Segregação de interfaces | O repositório contém apenas operações de itens; não mistura pedidos, órgãos e carga histórica. |
| Inversão de dependência | `ItemService` depende de `ItemRepository`, não de `JpaRepository` ou `EntityManager`. O Spring é usado na composição e nos adaptadores. |

## Concorrência e transações

No cadastro, a versão da entidade nova é nula: o Spring Data usa `persist`, e o Hibernate inicia a versão em zero. Isso impede que um código já existente seja tratado como edição por `merge`; a chave primária rejeita inclusive cadastros simultâneos.

Edição e inativação verificam a versão esperada e alteram uma entidade gerenciada dentro da transação. O Hibernate usa `@Version` para gerar o update condicionado à versão e incrementá-la. Se outra operação gravar nesse intervalo, a falha otimista é convertida em conflito. O teste da versão no banco é indispensável; verificar apenas o objeto em memória não impediria a corrida.

Quando os campos não mudam, `OPTIMISTIC_FORCE_INCREMENT` mantém a regra de avançar a versão em toda gravação. As exceções são tratadas fora de `TransactionTemplate`, após rollback, incluindo falhas no commit. Saldo inicial e data de cadastro têm `updatable=false` e são preservados na edição. Reservas e devoluções de pedidos também incrementam a versão por meio do JPA.

Pedidos têm uma fronteira transacional abrangendo cabeçalho, linhas e saldo dos itens. `OrderService` depende das portas `OrderRepository`, `StockMovement`, `OrganizationLookup` e `UnitOfWork`. A implementação Spring da unidade de trabalho usa `TransactionTemplate`, com uma transação própria em `READ_COMMITTED` por operação e timeout de 30 segundos. Os casos de uso não devem ser usados como passos de uma transação externa que espere revertê-los depois: cada chamada confirma ou desfaz seu próprio trabalho.

`OrderEntity` mapeia o cabeçalho e `OrderItemEntity` usa `@EmbeddedId`/`@MapsId` para a chave pedido/item. Os relacionamentos são lazy e não propagam exclusão ao catálogo ou ao órgão. DTOs e objetos de domínio são materializados dentro da transação; entidades JPA não chegam à API.

O cabeçalho é inserido primeiro para disputar a unicidade de `id_externo`. Somente o vencedor reserva estoque. Uma inserção duplicada causa rollback antes da consulta de replay em outra transação. Reserva e devolução usam `PESSIMISTIC_WRITE` em ordem de código dos itens; cancelamento bloqueia primeiro o cabeçalho. O adapter de estoque chama as regras do domínio `Item`, preserva o saldo inicial e deixa o Hibernate incrementar `@Version`. As dependências entre persistências de módulos ficam na infraestrutura.

## TDD

O módulo de itens foi iniciado pelos testes:

1. Foram escritos os testes de domínio e aplicação e executados antes das classes correspondentes. O primeiro resultado foi falha de compilação por tipos ausentes.
2. Foram implementadas as regras, a interface de persistência e os casos de uso. Os oito testes passaram.
3. Foram escritos oito testes da API, incluindo persistência e concorrência. A execução inicial falhou pela ausência dos adaptadores.
4. Foram implementados controller, tradução de erros e JDBC. A suíte passou; a configuração de teste foi isolada para não ser descoberta pela aplicação.
5. Na migração para JPA, foram adicionados testes antes das classes de persistência: conflito entre entidades gerenciadas em sessões independentes e cadastro simultâneo com o mesmo código. Também foi coberta a gravação sem alteração de campos. O adaptador JDBC foi substituído sem alterar a interface dos casos de uso.

Esse ciclo é aplicado às novas funcionalidades. O protótipo anterior não foi desenvolvido integralmente em TDD; adicionar testes depois não muda esse histórico. Nas próximas etapas, cada nova regra ou correção deve começar com um teste que falhe pelo comportamento esperado.

Os testes unitários rodam sem Spring; os testes de API usam Spring, MockMvc, Flyway e H2. A configuração da API de itens importa somente seu módulo, evitando depender de implementações futuras. Para testes focados:

```sh
mvn "-Dtest=ItemTest,ItemServiceTest,ItensApiTest" test
```

Os testes de pedidos começaram pelo domínio e pelos contratos de aplicação/API, com falhas por tipos ainda ausentes. A mesma suíte de contrato roda em H2 e PostgreSQL 15 via Testcontainers. Ela cobre rollback de um pedido com vários itens, cancelamento único, reenvios concorrentes, saldo disputado, conflito de edição após movimentação, filtros e contrato HTTP. A conexão de teste H2 usa fuso UTC, alinhado ao binding JDBC do Hibernate; os instantes retornados pela criação e consulta também são comparados. H2 sozinho não comprova a concorrência específica do PostgreSQL. A massa real foi processada em teste isolado com PostgreSQL e RabbitMQ; os resultados e limites da medição estão no guia de importação.

## Importação histórica

Órgãos disponibiliza cadastro e consulta, com JPA e testes de duplicidade concorrente. Pedidos disponibiliza entidades de cabeçalho e linhas, reserva integral, idempotência, cancelamento único e consultas paginadas. A carga histórica usa essa base e preserva os pedidos anteriores ao início da operação online.

A fila usa lotes persistidos antes da publicação, confirmações do broker, consumidores com confirmação após commit e um marcador transacional de conclusão por lote. A porta `BatchProcessor` separa a entrega RabbitMQ da persistência JPA. `LegacyRow` valida campos sem Spring. Os coordenadores de preparação e publicação ficam na infraestrutura, assim como a validação global por SQL em conjunto, adequada às relações entre milhões de registros.

O catálogo é inserido após o término do respectivo arquivo. Para pedidos, um manifesto registra o número esperado de linhas antes de autorizar o insert. A inserção usa SQL em conjunto via `EntityManager`: cabeçalho e linhas entram na mesma transação, e a chave única de identificador externo impede duplicação entre lotes concorrentes. O marcador do lote e os contadores são atualizados somente se a transação concluir.

`import_job_id` identifica os dados provisórios. Um lock compartilhado no controle permite inserts concorrentes, enquanto a substituição de uma carga e a liberação final exigem lock exclusivo. O leitor usa `FOR NO KEY UPDATE` na linha da carga: esse lock protege suas alterações sem bloquear o `KEY SHARE` usado pelas referências dos consumidores. O teste de concorrência mantém essa referência aberta e verifica que a preparação consegue avançar.

Os inserts preservam saldos e situações históricos, sem executar novos débitos. A primeira validação conclui a carga com ou sem ocorrências. `import_quarantine` identifica itens, pedidos e órgãos protegidos; os adaptadores de persistência conferem essa restrição antes de alterações ou movimentos, inclusive fora do filtro HTTP. A API permite as operações consistentes. Consultas continuam disponíveis para conferência.

`ImportReview` serializa correções pelo controle da carga, exige revisão da linha e preserva o conteúdo original. O reprocessamento assíncrono valida a preparação corrigida, atualiza apenas registros elegíveis ainda protegidos e insere os ausentes, preservando IDs de pedidos. Uma transação engloba publicação, novas ocorrências e liberação das restrições. Dados já liberados não são restaurados a partir do legado. Testes cobrem rollback, preservação de movimentos online e reprocessamento de referências recuperadas. Consulte [os contratos e limites da revisão](revisao-importacao.md).
