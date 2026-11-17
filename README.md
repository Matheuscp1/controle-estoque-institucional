# controle-estoque-institucional

Estrutura e modelagem inicial do backend em Java 17 e Spring Boot 2.7.18.

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

A aplicação usa a porta `8080`. O Flyway aplica as migrações automaticamente; esta etapa ainda não possui endpoints de negócio.
O banco usa volume persistente. A senha padrão é apenas para desenvolvimento; configure `DB_PASSWORD` em `.env` para alterá-la.

Para compilar com Java 17 e Maven:

```sh
mvn package
```

Para executar sem Docker, disponibilize PostgreSQL, configure `DB_URL`, `DB_USER` e `DB_PASSWORD` e execute `mvn spring-boot:run`.

## Banco e testes

As migrações criam `orgaos`, `itens`, `pedidos` e `pedido_itens`, com restrições e índices. Veja [modelagem, premissas e uso de IA](docs/modelagem.md).

```sh
mvn test
```

Os testes de integridade executam em H2 no modo PostgreSQL. A massa histórica foi analisada. A validação com PostgreSQL real e a implementação da carga continuam pendentes.

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

Implementar itens, pedidos, controle de concorrência, carga histórica e testes conforme o enunciado.
