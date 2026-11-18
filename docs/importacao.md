# Importação inicial com RabbitMQ

A entrada é o ZIP original com `orgaos.csv`, `itens.csv`, `pedidos.csv` e `pedido_itens.csv` na raiz. O arquivo local de referência está em `massa-dados/massa-dados-candidato.zip`. Não é necessário descompactá-lo.

## Executar

Com Docker em execução, `docker compose up --build` inicia PostgreSQL, RabbitMQ e a API. O Compose habilita `IMPORTS_ENABLED=true` e utiliza um volume persistente para os arquivos recebidos. A carga inicial exige tabelas operacionais vazias. Não apague um volume existente para importar novamente: use um ambiente separado caso já existam dados de uso.

Se a API estiver sendo executada pela IDE, use `IMPORTS_ENABLED=true` e reinicie a aplicação. A configuração atual habilita a importação por padrão; `IMPORTS_ENABLED=false` desliga o módulo e remove a rota `/api/imports`, que passa a retornar `404`.

Para executar a API localmente pelo Maven, use:

```powershell
docker compose up -d db rabbitmq
$env:IMPORTS_ENABLED = "true"
mvn spring-boot:run
```

O PostgreSQL local usa a porta `5432`, e a conexão AMQP do RabbitMQ usa `5672`. A porta `15672` serve apenas ao painel administrativo. As variáveis definidas em um terminal valem para os processos iniciados nele; não alteram uma aplicação já iniciada pela IDE. Mantenha apenas uma API usando a porta `8080`.

Com a API iniciada e a importação habilitada, envie o ZIP em outro terminal:

```powershell
curl.exe -X POST http://localhost:8080/api/imports -F "file=@massa-dados/massa-dados-candidato.zip"
```

A resposta `202` contém `id`, o SHA-256 do arquivo. Consulte:

```text
GET /api/imports/{id}
GET /api/imports/{id}/issues?after=0&limit=100
POST /api/imports/{id}/retry
```

O relatório usa cursor: envie como `after` o último `id` retornado. Cada ocorrência informa arquivo, linha lógica quando identificável (cabeçalho contado como linha 1), chave, motivo e conteúdo original. Erros do arquivo inteiro, como ausência de registros, não têm uma linha específica.

| Estado | Significado |
| --- | --- |
| `PREPARING` | O ZIP está sendo lido em fluxo e os lotes estão sendo registrados. |
| `STAGING` | A leitura terminou; os consumidores e as inserções incrementais podem continuar. |
| `NEEDS_REVIEW` | Estado da versão anterior, convertido após revalidação para o novo fluxo. |
| `COMPLETED_WITH_ISSUES` | Processamento concluído; ocorrências disponíveis para tratamento e somente os dados afetados ficam protegidos. |
| `REVIEW_PENDING` | Correções aguardam revalidação assíncrona; os dados já liberados continuam utilizáveis. |
| `COMPLETED` | A reconciliação terminou e o acesso operacional foi liberado. |
| `FAILED` | Falha técnica ou de formato requer intervenção e eventual retomada. |

O endpoint `retry` retoma a carga ativa em `FAILED`, preservando lotes concluídos. Também permite retomar uma carga antiga em `NEEDS_REVIEW` que ainda não tenha o manifesto incremental da migração V4: suas linhas preparadas são reaproveitadas, sem reler o ZIP. Isso não corrige inconsistências de origem.

Para corrigir dados, use os [endpoints de revisão manual](revisao-importacao.md). Cargas antigas em `NEEDS_REVIEW` com todos os lotes e inserts terminados são revalidadas pelo agendador após iniciar a versão atual. A substituição por outro ZIP permanece apenas para cargas antigas ou falhas ainda não liberadas; uma carga liberada, inclusive com pendências, nunca é substituída automaticamente. Reenviar exatamente o mesmo arquivo retorna a mesma carga.

Enquanto a importação estiver habilitada e não houver uma carga concluída, as APIs operacionais respondem `503`. As APIs de importação continuam disponíveis. Para desenvolvimento sem RabbitMQ, desabilite explicitamente o módulo com `IMPORTS_ENABLED=false`.

## Inserts durante a carga

1. Ao terminar cada CSV, o leitor registra sua quantidade de lotes. Órgãos e itens são inseridos assim que os consumidores terminam o respectivo arquivo e os registros locais são válidos. Não é necessário esperar os pedidos.
2. Após a leitura do ZIP, um manifesto calcula quantas linhas cada pedido deve receber, a partir dos lotes persistidos. Isso não exige que os consumidores tenham terminado. Como o CSV pode estar fora de ordem, a primeira linha recebida não indica um pedido completo.
3. Cada lote consumido de linhas identifica pedidos candidatos. Um pedido entra somente quando todas as linhas chegaram, o cabeçalho é válido e inequívoco, as referências existem e não há pares pedido/item repetidos.
4. Cabeçalho, linhas, contadores e marcador são gravados na mesma transação. Uma falha desfaz esse lote; outros lotes já gravados permanecem.
5. A conferência global reconcilia o saldo final e o histórico. A carga termina em `COMPLETED` ou `COMPLETED_WITH_ISSUES`. As pendências geram restrições específicas para impedir movimentações inconsistentes, permitindo operar os demais dados.

`GET /api/imports/{id}` inclui `preparedBatches`, `completedBatches`, `publishedOrganizations`, `publishedItems`, `publishedOrders` e `publishedOrderLines`. Os quatro últimos contadores representam inserts provisórios concluídos. Durante `PREPARING`, `totalBatches` ainda pode ser zero porque o leitor não conhece o total final.

## Processamento e concorrência

O leitor mantém até 500 registros por lote. O conteúdo e a identificação determinística do lote são registrados no PostgreSQL antes da publicação. RabbitMQ recebe somente o ID do lote; as mensagens e filas são duráveis. O publicador exige confirmação do broker e republica lotes pendentes após expirar sua reserva.

Cada consumidor bloqueia o lote, verifica se ele já terminou, grava a preparação via JPA em batches de 50 registros e marca a conclusão na mesma transação. O `ACK` ocorre depois do commit. Se houver reentrega após o commit, o marcador impede processamento duplicado. Falhas técnicas do consumidor são registradas, a entrega segue para `imports.failed` e o publicador agenda nova tentativa; após oito falhas, a carga fica em `FAILED`.

O despacho reserva até 128 lotes por ciclo, com intervalo padrão de 250 ms. A conexão PostgreSQL usa `reWriteBatchedInserts=true` para agrupar os inserts JDBC emitidos pelo Hibernate. Se configurar `DB_URL` manualmente, mantenha esse parâmetro na URL para obter a mesma otimização.

O lote guarda contagens compactas por pedido para evitar reler seu JSON completo ao construir o manifesto e selecionar candidatos. As atualizações JPA dos marcadores usam `@DynamicUpdate`, preservando o conteúdo sem regravá-lo. A reserva de despacho dura 300 segundos por padrão (`imports.dispatch-lease-seconds`), evitando republicações a cada minuto enquanto o lote aguarda consumo; falhas registradas pelo consumidor continuam usando o atraso progressivo de tentativa.

A inserção incremental usa SQL em conjunto pelo `EntityManager`. A chave única `pedidos.id_externo` decide entre transações concorrentes; `ON CONFLICT DO NOTHING RETURNING` retorna somente os pedidos realmente criados, e suas linhas são inseridas na mesma instrução. A ordem de inserção dos identificadores é determinística. Marcadores e contadores são transacionais, inclusive quando lotes diferentes contêm linhas do mesmo pedido.

O agrupamento `complete_lines` usa `MATERIALIZED` para calcular a completude e a assinatura dos pedidos uma vez por lote. Na investigação de uma carga lenta em 18/11/2026, o plano anterior repetia esse agrupamento 499 vezes no lote analisado. Uma comparação somente de leitura da seleção dos mesmos candidatos levou 11,74 s antes e 0,56 s com materialização. Essa medição isolada, com a importação ativa, não inclui os inserts nem permite extrapolar a duração total da carga.

`import_rows` preserva campos originais e normalizados. `import_sources` registra o término de cada arquivo e `import_order_manifest` registra a quantidade esperada de linhas por pedido. A validação global verifica referências, duplicidades, saldo final e saldo histórico sem depender da ordem das mensagens.

## Política de consistência

Esta implementação adota **inserts incrementais e conclusão com pendências**. Inconsistências deixam a carga em `COMPLETED_WITH_ISSUES`. Os itens afetados não podem ser editados, reservados ou receber devoluções; pedidos pendentes não podem ser cancelados nem ter seu identificador reutilizado. As demais operações continuam disponíveis. Consultas podem mostrar registros provisórios; consulte `/quarantine` para identificar as restrições. Registros originais e ocorrências permanecem para análise.

- Cabeçalhos equivalentes após normalização são consolidados. Cabeçalhos divergentes com a mesma chave ficam pendentes.
- Pares pedido/item repetidos deixam o pedido inteiro pendente. Uma cópia comprovadamente idêntica pode ser desconsiderada pelo endpoint específico, com justificativa e preservação do original.
- Referências inexistentes, quantidades inválidas, pedidos sem itens e cancelamentos sem data válida ficam pendentes. O motivo do cancelamento é opcional no legado; vazio ou somente espaços é normalizado para ausência, sem gerar ocorrência. Quando informado, seu limite continua sendo 500 caracteres.
- Unidades são normalizadas com remoção dos espaços das extremidades e conversão para maiúsculas. Os campos originais permanecem disponíveis.
- Datas sem fuso são interpretadas em `America/Sao_Paulo`. Horários ambíguos ou inexistentes por transição de fuso são rejeitados.
- O saldo final deve corresponder ao saldo inicial menos os pedidos confirmados. O histórico de reservas e devoluções também não pode produzir saldo negativo. Na ausência de uma ordem conhecida para eventos simultâneos, reservas precedem devoluções; essa premissa conservadora pode exigir revisão de casos do legado.
- A situação atual de um item inativo não invalida, sozinha, seus pedidos históricos.

Cada pedido completo é inserido atomicamente, com suas linhas. A carga copia os saldos históricos sem chamar a reserva online para cada pedido antigo, evitando descontar o histórico novamente. Após a liberação final, as requisições usam o módulo `orders`, inclusive para reconhecer reenvios e cancelar pedidos importados.

### Tratamento das ocorrências

| Ocorrência | Tratamento |
| --- | --- |
| `UNKNOWN_ORDER` | Preservar a linha rejeitada e revisar o cabeçalho ausente ou inválido. Não criar um pedido fictício para aproveitar a linha. |
| `UNKNOWN_ITEM` | Revisar o cadastro ou a referência do item. O pedido inteiro fica impedido de publicação; retirar somente a linha inválida produziria um pedido parcial. |
| `BALANCE_MISMATCH` | Revisar saldo inicial, saldo atual e histórico com o responsável pelos dados. Não escolher automaticamente um saldo nem excluir pedidos para fechar a conta. |
| `EMPTY_ORDER` | Rejeitar o pedido sem linhas válidas e preservar sua origem. A regra 2 exige um ou mais itens; excluir o pedido da carga aproveitada é diferente de remover essa validação. |

A revisão registra correções justificadas e revalida referências e saldos. O original e as ocorrências anteriores permanecem registrados. A conclusão com pendências não transforma um registro inconsistente em válido.

`EMPTY_ORDER` não se refere ao motivo de cancelamento. Pedidos legados cancelados sem motivo podem ser inseridos quando os demais dados forem válidos. A data de cancelamento continua obrigatória para reconstruir o histórico de saldo. Na API de novos cancelamentos, o motivo continua obrigatório.

As ocorrências podem ser corrigidas pelos endpoints de linhas e aproveitadas após `POST /api/imports/{id}/reprocess`. O reprocessamento usa a preparação persistida, sem reler o ZIP e sem repetir os lotes RabbitMQ. Alterações nos registros já liberados são recusadas para preservar a operação online. Consulte os contratos e limites no [guia de revisão](revisao-importacao.md).

## Testes

```powershell
mvn "-Dtest=LegacyRowTest,ImportQueueIntegrationTest" test
```

A suíte exige Docker e cria PostgreSQL e RabbitMQ isolados. A massa completa é um teste optativo, separado dos testes pequenos:

```powershell
mvn "-Dtest=ImportQueueIntegrationTest#massaCompletaPassaPelaFilaEPreservaInconsistencias" "-Dmass.file=massa-dados/massa-dados-candidato.zip" test
```

Esse teste verifica os 3.382.295 registros da massa fornecida, gera `target/import-incremental-report.json` com tempos, primeiros inserts e ocorrências e espera `COMPLETED_WITH_ISSUES`, pois o arquivo contém inconsistências conhecidas. Leitura, fila e inserções ocorrem simultaneamente. Os containers de teste são descartados ao final; o ZIP local e o relatório permanecem. O teste não altera o banco do Docker Compose.

RabbitMQ permite desacoplar preparação e consumo, controlar concorrência e recuperar falhas. A existência da fila não prova ganho de velocidade: os tempos medidos dependem do banco, disco, tamanho de lote e número de consumidores.

### Validação do fluxo incremental

Os testes pequenos cobrem inserts do catálogo antes do fim da carga, pedidos com linhas distribuídas entre lotes, reentregas concorrentes, rollback das linhas e dos marcadores, retomada de cargas antigas e substituição restrita a registros provisórios.

A execução da massa completa em 18/11/2026 foi interrompida quando o disco C: ficou com aproximadamente 58 MB livres. Antes da interrupção, o progresso registrou 50.000 itens inseridos enquanto a preparação e o consumo continuavam. Essa observação parcial não comprova o processamento completo nem permite comparar a duração total com a implementação anterior. O relatório de desempenho incremental ainda precisa ser produzido em uma execução completa com espaço disponível; os números da seção seguinte pertencem à versão anterior.

## Resultado da massa original

Os números abaixo são a referência da implementação anterior, que aguardava toda a preparação e a validação antes dos inserts. Não representam uma medição do fluxo incremental.

Execução local de 17/11/2026, com PostgreSQL 15.20, RabbitMQ 4, dois consumidores e Java 22.0.2 compilando para Java 17. Os resultados detalhados e o hash do ZIP estão em [resultado-importacao.json](resultado-importacao.json).

| Medida | Resultado |
| --- | --- |
| Registros preservados na preparação | 3.382.295 |
| Lotes concluídos | 6.766 |
| Recebimento e preparação | 177,43 s |
| Publicação pela fila e consumo dos lotes | 334,88 s |
| Validação global | 174,51 s |
| Total dessas etapas | Aproximadamente 11 min 27 s |
| Pico de memória residente do processo Java de teste | Aproximadamente 476 MiB |
| Ocorrências de inconsistência | 53.856 |
| Resultado | `NEEDS_REVIEW`, nenhuma publicação operacional |

As ocorrências incluem 4.513 pares pedido/item repetidos, 2.824 divergências de saldo final, 1.221 itens com saldo negativo na reconstrução histórica e referências a pedidos inválidos ou inexistentes. Um mesmo pedido pode gerar várias ocorrências: **53.856 não é a quantidade de pedidos rejeitados**. Os totais também diferem da auditoria bruta porque a validação de campos torna inválidos cabeçalhos que são referenciados por outras linhas.

O teste separou preparação, consumo e validação para medir cada etapa. A aplicação permite preparar e consumir simultaneamente. A memória medida inclui JVM e infraestrutura de teste, mas exclui PostgreSQL e RabbitMQ. É uma execução local, sem comparação controlada com carga síncrona e sem extrapolação para produção.
