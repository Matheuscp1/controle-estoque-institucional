# controle-estoque-institucional

Backend de itens, órgãos e pedidos transacionais em Java 17 e Spring Boot 2.7.18.

Projeto Maven: `br.com.estoque:controle-estoque-institucional`.
Pacote Java: `br.com.estoque`.
Repositório: https://github.com/Matheuscp1/controle-estoque-institucional

## Estrutura

Cache de consultas compartilhado com Redis e logs de requisições/importação: veja [configuração e limites](docs/cache-logs.md).

- `pom.xml`: dependências e build Maven.
- `src/main/java/br/com/estoque/Application.java`: entrada da aplicação.
- `src/main/resources/application.yml`: configuração do banco e da API.
- `Dockerfile` e `compose.yaml`: aplicação, PostgreSQL 15 e RabbitMQ.

## Executar

Com Docker em execução:

```sh
docker compose up --build
```

A aplicação usa a porta `8080`. O Flyway aplica as migrações automaticamente; estão disponíveis os endpoints de itens, órgãos e pedidos.
No Compose, a importação inicial está habilitada: consultas mostram os dados inseridos progressivamente, enquanto alterações retornam `503` até a primeira validação. Envie o ZIP pelo endpoint de importação seguindo [o guia de carga histórica](docs/importacao.md).
O banco usa volume persistente. A senha padrão é apenas para desenvolvimento; configure `DB_PASSWORD` em `.env` para alterá-la.

Para compilar com Java 17 e Maven:

```sh
mvn package
```

Para executar sem Docker, disponibilize PostgreSQL, configure `DB_URL`, `DB_USER` e `DB_PASSWORD` e execute `mvn spring-boot:run`.

## Arquitetura e API

Adotamos um **monólito modular com arquitetura de portas e adaptadores (hexagonal)**. Uma única aplicação é organizada por funcionalidades, como itens, órgãos e pedidos. Dentro de cada módulo, as regras dependem de interfaces, enquanto os detalhes de HTTP e persistência ficam nos adaptadores externos. Os módulos `items`, `organizations` e `orders` seguem essa organização. O módulo `imports` acrescenta validação do legado, processamento assíncrono, inserts incrementais, conclusão com pendências e revisão manual.

| Camada | Responsabilidade |
| --- | --- |
| `domain` | Objetos e regras de negócio, independentes de Spring e JPA. |
| `application` | Casos de uso e interfaces de repositório, que representam as portas de saída. |
| `api` | Adaptador de entrada HTTP: endpoints, requisições, respostas e tradução de erros. |
| `infrastructure` | Adaptadores de persistência, entidades JPA e configuração dos componentes. |

Consulte [a arquitetura e o ciclo TDD](docs/arquitetura.md) e [os contratos da API de itens](docs/api-itens.md).

O módulo `organizations` oferece cadastro e consulta paginada de órgãos, validação de UF e proteção contra códigos duplicados. Consulte [os contratos da API de órgãos](docs/api-organizations.md).

O módulo `orders` oferece criação e consulta de pedidos, listagem por órgão e cancelamento. `OrderEntity` e `OrderItemEntity` representam o cabeçalho e suas linhas em JPA, com chave composta para impedir itens repetidos. Consulte [os contratos, exemplos e premissas da API de pedidos](docs/api-orders.md).

Pedidos reservam todo o saldo em uma única transação. Os itens são bloqueados em ordem de código e suas versões avançam a cada movimentação. O identificador externo é único globalmente: reenvios equivalentes retornam o pedido existente; conteúdos diferentes recebem conflito. O cancelamento bloqueia o pedido e devolve as quantidades somente uma vez, inclusive para itens posteriormente inativados. Uma falha desfaz a operação inteira.

A persistência usa Spring Data JPA, com `@Entity` e `@Version` na infraestrutura. O domínio permanece independente do JPA. As migrações são gerenciadas pelo Flyway; o Hibernate valida o esquema existente.

O módulo de itens oferece cadastro, consulta, edição, inativação lógica, filtros e paginação. A unidade é normalizada para maiúsculas, e edições exigem a versão atual do item.

### Vantagens e custos dessa organização

A principal vantagem é testar e alterar as regras sem depender do Spring ou do banco. Nos testes unitários, os casos de uso recebem repositórios simulados e um relógio fixo. Nos testes de integração, os adaptadores são verificados com a persistência real do ambiente de teste.

A separação também reduz o impacto de mudanças técnicas. Neste projeto, a persistência de itens foi trocada de JDBC para JPA mantendo a interface do repositório e os casos de uso. As responsabilidades ficam localizadas: alterações no contrato HTTP pertencem à API; alterações no mapeamento do banco pertencem à infraestrutura.

Manter uma única aplicação simplifica a execução pelo Docker Compose e permite transações locais entre pedidos e estoque. A arquitetura, por si só, não garante atomicidade nem protege contra concorrência: essas regras continuam exigindo transações, controle de versão, bloqueios quando necessários e testes.

O custo é ter mais classes e conversões entre objetos de domínio e entidades persistentes. Para este desafio, essa separação ajuda a isolar e testar as regras de saldo, pedidos e importação. Criamos interfaces nos limites de dependência, sem exigir uma interface para toda classe.

**SOLID** orienta a distribuição de responsabilidades e dependências entre as classes. **TDD** orienta o desenvolvimento por testes escritos antes da implementação. São práticas utilizadas no projeto, não nomes da arquitetura.

### Configuração dos módulos e da persistência

Cada módulo terá sua configuração na camada `infrastructure`, responsável por conectar os casos de uso e a persistência ao Spring. A configuração agrupa os componentes do módulo; não é necessário criar uma classe de configuração para cada entidade.

- `@Configuration` identifica a classe de configuração.
- `@EntityScan` informa os pacotes das entidades JPA.
- `@EnableJpaRepositories` informa o pacote dos repositórios Spring Data.
- `@Bean` cria os serviços de aplicação, fornecendo as implementações das interfaces de repositório e outras dependências necessárias, como um relógio para obter datas.

Essa composição mantém os serviços de aplicação como classes Java comuns, sem `@Service` ou dependências do Spring. Os serviços dependem de interfaces de repositório, e não do JPA. Nos testes unitários, podemos construí-los diretamente com repositórios simulados e, quando necessário, um relógio fixo, sem iniciar a aplicação.

Na estrutura atual, a aplicação principal poderia descobrir entidades e repositórios automaticamente por estarem abaixo de `br.com.estoque`. A busca explícita torna os limites dos módulos claros e permite carregar sua persistência nos testes isolados. Conforme novos módulos forem implementados, suas configurações deverão incluir os respectivos pacotes de entidades e repositórios.

## Banco e testes

As migrações criam `orgaos`, `itens`, `pedidos` e `pedido_itens`, com restrições e índices. Veja [modelagem, premissas e uso de IA](docs/modelagem.md).

```sh
mvn test
```

Os testes unitários de domínio e aplicação executam sem Spring. Os testes de API e de integridade usam H2 no modo PostgreSQL. A suíte de pedidos também executa com PostgreSQL 15 real via Testcontainers, incluindo reserva concorrente, reenvios simultâneos, rollback integral e cancelamento concorrente. Essa suíte exige Docker em execução e pode baixar a imagem na primeira execução.

Para executar apenas a suíte de pedidos no PostgreSQL:

```sh
mvn "-Dtest=OrdersPostgresTest" test
```

Para executar os testes sem Docker, excluindo explicitamente a suíte PostgreSQL:

```sh
mvn "-Dtest=*,!OrdersPostgresTest,!ImportQueueIntegrationTest" test
```

Os testes pequenos verificam regras e concorrência funcional. Na implementação anterior, a execução optativa da massa completa processou os 3.382.295 registros em preparação e terminou em `NEEDS_REVIEW`, sem publicação operacional. A medição completa do fluxo incremental ainda está pendente: a execução local foi interrompida por falta de espaço em disco. Os resultados e limites estão no [guia de importação](docs/importacao.md#resultado-da-massa-original).

## Tratamento dos dados legados

**Estado atual:** o importador realiza inserts incrementais. As consultas mostram os dados já inseridos durante a carga, com `X-Import-Ready: false`; alterações aguardam a primeira validação. A carga conclui em `COMPLETED` ou `COMPLETED_WITH_ISSUES`, liberando o uso dos dados consistentes e protegendo os afetados até revisão. Há endpoints para consultar ocorrências, corrigir linhas com revisão e justificativa, adicionar registros ausentes, desconsiderar duplicatas idênticas e agendar reprocessamento. Consulte [a revisão manual](docs/revisao-importacao.md) e [a execução da carga](docs/importacao.md).

Foram lidos 250 órgãos, 50.000 itens, 1.002.000 cabeçalhos de pedidos e 2.330.045 linhas de pedidos. A auditoria identificou 2.000 cabeçalhos extras idênticos, referências inexistentes, quantidades inválidas, 100 pedidos sem linhas e cancelamentos incompletos. Os números e suas limitações estão na [análise da massa](docs/analise-massa.md).

A importação deverá respeitar as mesmas regras de negócio da operação online: saldo não negativo, pedido completo, identificador único e cancelamento sem devolução duplicada. A origem legada não autoriza inserir registros inválidos nas tabelas definitivas.

| Situação | Tratamento |
| --- | --- |
| Cabeçalhos equivalentes após normalização com o mesmo identificador | Consolidar em uma ocorrência e contabilizar as duplicatas na carga concluída. |
| Mesmo identificador com conteúdos diferentes | Separar o pedido para análise, sem sobrescrever nenhuma ocorrência. |
| Pedido sem linhas, com referência inexistente ou quantidade inválida | Rejeitar o pedido inteiro da carga definitiva, preservando os registros de origem para análise. |
| Par pedido/item repetido | Separar o pedido até definir se é duplicação ou quantidade complementar; não somar nem descartar linhas silenciosamente. |
| Unidade com variação de caixa ou espaços | Normalizar com remoção dos espaços nas extremidades e conversão para maiúsculas, registrando a transformação. |
| Cancelamento sem data válida ou anterior à criação | Separar para análise; não inventar a data. O motivo é opcional no legado e sua ausência, sozinha, não gera ocorrência. |
| Pedido histórico de item atualmente inativo | Preservar se válido: a inatividade atual não comprova que o item estava inativo na data do pedido. |
| Divergência de saldo | Registrar e reconciliar após o saneamento; não corrigir silenciosamente nem descontar o histórico novamente sobre `saldo_atual`. |

Os registros permanecem em `import_rows`, fora das tabelas operacionais, com arquivo de origem, linha lógica, identificador e conteúdo original. `import_issues` registra as inconsistências e pode ser consultada pela API com paginação. O ZIP original também é preservado.

**Os inserts são incrementais; pendências não impedem concluir o processamento.** Itens com saldo ou histórico pendente não podem ser movimentados nem editados, e pedidos afetados não podem ser cancelados ou ter seu identificador reutilizado. As correções preservam o original, exigem revisão e justificativa e só são publicadas após revalidação. Dados e movimentos já liberados não são sobrescritos pelo histórico. Reenviar exatamente o mesmo ZIP retorna a carga existente; o reprocessamento manual não precisa reler o arquivo.

A carga usa Java, leitura em fluxo, lotes de 500 registros e gravações JPA em batches de 50. As datas sem offset usam `America/Sao_Paulo`; horários ambíguos ou inexistentes são rejeitados. A validação global verifica o saldo final e reconstrói reservas e devoluções para detectar saldos históricos negativos. As 2.824 divergências da auditoria inicial são comparações brutas, anteriores ao saneamento, e não valores prontos para corrigir o banco. A publicação copia os saldos históricos sem executar novos débitos.


## Próximas etapas

Revisar as inconsistências da massa com o responsável pelos dados usando os endpoints de tratamento e validar as correções. Comparar configurações de lotes e consumidores em medições controladas antes de afirmar ganhos de desempenho com a fila.
