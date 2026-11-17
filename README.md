# controle-estoque-institucional

Backend com modelagem, cadastro de itens e órgãos em Java 17 e Spring Boot 2.7.18.

Projeto Maven: `br.com.estoque:controle-estoque-institucional`.
Pacote Java: `br.com.estoque`.
Repositório: https://github.com/Matheuscp1/controle-estoque-institucional

## Estrutura

- `pom.xml`: dependências e build Maven.
- `src/main/java/br/com/estoque/Application.java`: entrada da aplicação.
- `src/main/resources/application.yml`: configuração do banco e da API.
- `Dockerfile` e `compose.yaml`: aplicação e PostgreSQL 15.

## Executar

Com Docker em execução:

```sh
docker compose up --build
```

A aplicação usa a porta `8080`. O Flyway aplica as migrações automaticamente; esta etapa oferece os endpoints de itens e órgãos.
O banco usa volume persistente. A senha padrão é apenas para desenvolvimento; configure `DB_PASSWORD` em `.env` para alterá-la.

Para compilar com Java 17 e Maven:

```sh
mvn package
```

Para executar sem Docker, disponibilize PostgreSQL, configure `DB_URL`, `DB_USER` e `DB_PASSWORD` e execute `mvn spring-boot:run`.

## Arquitetura e API

Adotamos um **monólito modular com arquitetura de portas e adaptadores (hexagonal)**. Uma única aplicação é organizada por funcionalidades, como itens, órgãos e pedidos. Dentro de cada módulo, as regras dependem de interfaces, enquanto os detalhes de HTTP e persistência ficam nos adaptadores externos. Os módulos de itens e órgãos já seguem essa organização; os demais serão implementados no mesmo padrão.

| Camada | Responsabilidade |
| --- | --- |
| `domain` | Objetos e regras de negócio, independentes de Spring e JPA. |
| `application` | Casos de uso e interfaces de repositório, que representam as portas de saída. |
| `api` | Adaptador de entrada HTTP: endpoints, requisições, respostas e tradução de erros. |
| `infrastructure` | Adaptadores de persistência, entidades JPA e configuração dos componentes. |

Consulte [a arquitetura e o ciclo TDD](docs/arquitetura.md) e [os contratos da API de itens](docs/api-itens.md).

O módulo `organizations` oferece cadastro e consulta paginada de órgãos, validação de UF e proteção contra códigos duplicados. Consulte [os contratos da API de órgãos](docs/api-organizations.md).

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

Os testes unitários de itens executam sem Spring. Os testes de API e de integridade executam em H2 no modo PostgreSQL. A massa histórica foi analisada. A validação com PostgreSQL real e a implementação da carga continuam pendentes.

## Tratamento dos dados legados

**Estado atual:** o ZIP foi analisado por completo. Nenhum dado foi importado, corrigido ou descartado do arquivo original. O importador ainda não foi implementado; as políticas abaixo descrevem o tratamento planejado, não uma carga já executada.

Foram lidos 250 órgãos, 50.000 itens, 1.002.000 cabeçalhos de pedidos e 2.330.045 linhas de pedidos. A auditoria identificou 2.000 cabeçalhos extras idênticos, referências inexistentes, quantidades inválidas, 100 pedidos sem linhas e cancelamentos incompletos. Os números e suas limitações estão na [análise da massa](docs/analise-massa.md).

A importação deverá respeitar as mesmas regras de negócio da operação online: saldo não negativo, pedido completo, identificador único e cancelamento sem devolução duplicada. A origem legada não autoriza inserir registros inválidos nas tabelas definitivas.

| Situação | Tratamento planejado |
| --- | --- |
| Cabeçalhos idênticos com o mesmo identificador | Manter uma ocorrência e registrar as duplicatas no relatório. |
| Mesmo identificador com conteúdos diferentes | Separar o pedido para análise, sem sobrescrever nenhuma ocorrência. |
| Pedido sem linhas, com referência inexistente ou quantidade inválida | Rejeitar o pedido inteiro da carga definitiva, preservando os registros de origem para análise. |
| Par pedido/item repetido | Separar o pedido até definir se é duplicação ou quantidade complementar; não somar nem descartar linhas silenciosamente. |
| Unidade com variação de caixa ou espaços | Normalizar com remoção dos espaços nas extremidades e conversão para maiúsculas, registrando a transformação. |
| Cancelamento incompleto ou anterior à criação | Separar para análise; não inventar data ou motivo para contornar as restrições. Exigir motivo é uma premissa nossa, não uma regra textual do enunciado. |
| Pedido histórico de item atualmente inativo | Preservar se válido: a inatividade atual não comprova que o item estava inativo na data do pedido. |
| Divergência de saldo | Registrar e reconciliar após o saneamento; não corrigir silenciosamente nem descontar o histórico novamente sobre `saldo_atual`. |

Os registros não aproveitáveis deverão ficar em uma área de rejeições, fora das tabelas operacionais, com arquivo de origem, número da linha, identificador do pedido ou item, conteúdo original e motivo. Quando uma linha invalidar um pedido, o relatório deverá relacionar todas as linhas desse pedido. O ZIP original deve ser preservado. Até aqui, o que existe é o relatório da auditoria; essa área de rejeições ainda será implementada.

A carga será feita em Java, com leitura em fluxo, preparação dos dados e gravação em lotes. A definição do fuso das datas sem offset e a política final de reconciliação de saldo ainda precisam ser consolidadas. As 2.824 divergências encontradas são comparações brutas, anteriores ao saneamento, e não valores prontos para corrigir o banco.


## Próximas etapas

Concluir o módulo de pedidos antes de integrar a importação: entidades de pedido e suas linhas, atendimento integral, idempotência, cancelamento único e consultas. Depois finalizar a carga histórica com RabbitMQ, reutilizando as regras de validação sem debitar novamente o histórico. O código preliminar da fila permanece em desenvolvimento. Validar as operações concorrentes no PostgreSQL e medir desempenho com a massa histórica.
