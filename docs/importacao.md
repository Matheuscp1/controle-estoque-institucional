# Importação inicial com RabbitMQ

A entrada é o ZIP original com `orgaos.csv`, `itens.csv`, `pedidos.csv` e `pedido_itens.csv` na raiz. O arquivo local de referência está em `massa-dados/massa-dados-candidato.zip`. Não é necessário descompactá-lo.

A carga começa com `POST /api/imports`, não ao iniciar a aplicação. O backend lê o arquivo em fluxo, distribui lotes pelo RabbitMQ e publica os registros progressivamente. A primeira validação libera as operações sobre dados consistentes; ocorrências restantes podem ser corrigidas depois. Este guia descreve o backend implementado, não pressupõe que o painel do frontend já consuma essas rotas.

## Executar

Na raiz do backend, com Docker e Compose disponíveis, `docker compose up --build` inicia PostgreSQL, RabbitMQ, Redis e a API. O Compose habilita `IMPORTS_ENABLED=true` e utiliza um volume persistente para os arquivos recebidos. A carga inicial exige tabelas operacionais vazias. Não apague um volume existente para importar novamente: use um ambiente separado caso já existam dados de uso.

Se a API estiver sendo executada pela IDE, use `IMPORTS_ENABLED=true` e reinicie a aplicação. A configuração atual habilita a importação por padrão; `IMPORTS_ENABLED=false` desliga o módulo e remove a rota `/api/imports`, que passa a retornar `404`.

Para executar a API localmente pelo Maven, use:

```powershell
docker compose up -d db rabbitmq redis
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
GET /api/imports/active
GET /api/imports/{id}/issues?after=0&limit=100
```

O relatório de ocorrências usa cursor: envie como `after` o último `id` de ocorrência retornado, não o ID da carga. Cada ocorrência informa arquivo, linha lógica quando identificável (cabeçalho contado como linha 1), chave, motivo e conteúdo original. Erros do arquivo inteiro, como ausência de registros, não têm uma linha específica.

### Enviar pelo Swagger

1. Abra [Swagger UI](http://localhost:8080/swagger-ui.html), localize **Importação** e expanda `POST /api/imports`.
2. Clique em **Try it out** e, no campo **file**, em **Choose File / Escolher arquivo**. Selecione o ZIP original, de até 128 MiB.
3. Clique em **Execute** e confira o código `202` em **Server response**. Copie o `id` do **Response body**; o recebimento não significa que a carga terminou.
4. Abra `GET /api/imports/active` e execute, sem parâmetros. Também pode consultar `GET /api/imports/{id}`, preenchendo o ID copiado, sem aspas.
5. Repita **Execute** para atualizar o progresso. O Swagger não faz atualização automática. Aguarde `COMPLETED` ou `COMPLETED_WITH_ISSUES`; neste último caso, siga a seção de revisão abaixo.

O [guia do Swagger](swagger.md) e o [README](../README.md#consultar-e-corrigir-inconsistências) trazem o passo a passo de correção e um exemplo completo de JSON.

### Descoberta automática pelo frontend

Ao abrir o painel, consulte `GET /api/imports/active`. A rota usa a carga referenciada pelo controle da importação, sem listar o histórico nem exigir que o usuário informe o ID:

- `200`: retorna o mesmo objeto de `GET /api/imports/{id}`, incluindo `id`, `status` e os contadores de progresso. O frontend pode usar esse ID para consultar ocorrências e revisões.
- `204`: nenhuma carga registrada; não há corpo JSON. Exiba o estado vazio e a opção de enviar o ZIP.

“Active” significa a carga atual vinculada ao sistema, não necessariamente um processamento em execução. O resultado permanece disponível em `COMPLETED`, `COMPLETED_WITH_ISSUES` ou `FAILED`. Se uma carga provisória for substituída, a rota passa a retornar a substituta; a anterior continua acessível por seu ID. Durante `PREPARING`, `STAGING` ou `REVIEW_PENDING`, o painel pode repetir a consulta a cada 3–5 segundos. Atualize também após upload, retomada ou reprocessamento. A resposta usa `Cache-Control: no-store`.

Essa rota apenas consulta: não inicia nem retoma importações, não libera edição e não calcula tempo restante. As proteções de escrita continuam no backend. Com `imports.enabled=false`, as rotas de importação não existem (`404`), o que é diferente do `204` de uma instalação habilitada sem carga.

## Estados e liberação das operações

| Estado | Significado |
| --- | --- |
| `PREPARING` | O ZIP está sendo lido em fluxo e os lotes estão sendo registrados. |
| `STAGING` | A leitura terminou; consumo, manifesto, publicação ou validação inicial ainda podem estar em andamento. Não há um status separado para cada uma dessas etapas. |
| `NEEDS_REVIEW` | Estado da versão anterior, convertido após revalidação para o novo fluxo. |
| `COMPLETED_WITH_ISSUES` | Processamento concluído; ocorrências disponíveis para tratamento e somente os dados afetados ficam protegidos. |
| `REVIEW_PENDING` | Correções aguardam ou passam por revalidação assíncrona; dados já liberados continuam utilizáveis e novas correções aguardam o término. |
| `COMPLETED` | A reconciliação terminou e o acesso operacional foi liberado. |
| `FAILED` | Falha técnica ou de formato requer intervenção e eventual retomada. |

O endpoint `retry` retoma a carga ativa em `FAILED`, preservando lotes concluídos. Também permite retomar uma carga antiga em `NEEDS_REVIEW` que ainda não tenha o manifesto incremental da migração V4: suas linhas preparadas são reaproveitadas, sem reler o ZIP. Isso não corrige inconsistências de origem.

Para corrigir dados, use os [endpoints de revisão manual](revisao-importacao.md). Cargas antigas em `NEEDS_REVIEW` com todos os lotes e inserts terminados são revalidadas pelo agendador após iniciar a versão atual. A substituição por outro ZIP permanece apenas para cargas antigas ou falhas ainda não liberadas; uma carga liberada, inclusive com pendências, nunca é substituída automaticamente. Reenviar exatamente o mesmo arquivo retorna a mesma carga.

Durante a carga inicial, consultas `GET`/`HEAD` continuam disponíveis e retornam os registros já inseridos, com paginação. `OPTIONS` também é permitido. O header `X-Import-Ready: false` informa que os dados ainda são provisórios; o frontend pode exibir “Em validação” e acompanhar os contadores em `GET /api/imports/{id}`. Antes dos primeiros inserts, as listas podem estar vazias. Operações de escrita respondem `503` até concluir a primeira validação; os endpoints de importação permanecem disponíveis.

Depois da validação inicial, `X-Import-Ready: true` indica que o bloqueio global de escrita terminou. Isso não garante que todos os registros estejam liberados: em `COMPLETED_WITH_ISSUES` ou `REVIEW_PENDING`, as restrições individuais continuam válidas e retornam `409` quando aplicáveis. Consulte `/quarantine` para identificar os afetados. Em frontend de outra origem, a configuração CORS deve expor esse header. Para desenvolvimento sem RabbitMQ, desabilite explicitamente o módulo com `IMPORTS_ENABLED=false`.

## Inserts durante a carga

1. Ao terminar cada CSV, o leitor registra sua quantidade de lotes. Órgãos e itens são inseridos assim que os consumidores terminam o respectivo arquivo e os registros locais são válidos. Não é necessário esperar os pedidos.
2. Após a leitura do ZIP, um manifesto calcula quantas linhas cada pedido deve receber, a partir dos lotes persistidos. Isso não exige que os consumidores tenham terminado. Como o CSV pode estar fora de ordem, a primeira linha recebida não indica um pedido completo.
3. Cada lote consumido de linhas identifica pedidos candidatos. Um pedido entra somente quando todas as linhas chegaram, o cabeçalho é válido e inequívoco, as referências existem e não há pares pedido/item repetidos.
4. A publicação reúne até oito lotes consumidos por transação. Pedidos candidatos são deduplicados entre os lotes; identificadores já publicados são descartados antes de consultar novamente cabeçalhos e linhas. Cabeçalhos, linhas, contadores e marcadores são gravados juntos. Uma falha desfaz todo o grupo; grupos anteriores permanecem. Os locks dos lotes seguem uma ordem estável, e a restrição única do pedido continua protegendo contra publicações concorrentes.
5. A conferência global reconcilia o saldo final e o histórico. A carga termina em `COMPLETED` ou `COMPLETED_WITH_ISSUES`. As pendências geram restrições específicas para impedir movimentações inconsistentes, permitindo operar os demais dados.

`GET /api/imports/{id}` e `/active` incluem `preparedBatches`, `completedBatches`, `publishedOrganizations`, `publishedItems`, `publishedOrders` e `publishedOrderLines`. Durante a carga, os quatro últimos contadores mostram registros já publicados, ainda sujeitos à validação. Eles não representam a quantidade de registros liberados da quarentena. Durante `PREPARING`, `totalBatches` ainda pode ser zero porque o leitor não conhece o total final.

Quando `totalBatches > 0`, `completedBatches / totalBatches` indica o progresso do consumo dos lotes, não da carga inteira. Mesmo em 100%, publicação e validação podem continuar. O endpoint não retorna tempo restante estimado nem data de início; o frontend não deve apresentar os contadores como uma previsão de conclusão.

## Processamento e concorrência

A publicação agrupada reduz transações e verificações repetidas, sem aumentar o lote de leitura do ZIP nem remover validações. O manifesto continua exigindo todas as linhas de cada pedido. O ganho de tempo da alteração ainda precisa ser medido com a massa completa em ambiente separado; os testes de integração verificam rollback, reentrega e grupos concorrentes sobrepostos.

O [resultado da execução completa](#resultado-da-massa-original) registra a referência anterior à publicação agrupada. Ele distingue o tempo de recebimento do ZIP do processamento até a validação final.

O leitor mantém até 500 registros por lote. O conteúdo e a identificação determinística do lote são registrados no PostgreSQL antes da publicação. RabbitMQ recebe somente o ID do lote; as mensagens e filas são duráveis. O publicador exige confirmação do broker e republica lotes pendentes após expirar sua reserva.

### Volume e dimensionamento dos lotes

O ZIP de referência tem 99.025.999 bytes (aproximadamente 94,44 MiB). As contagens abaixo excluem os cabeçalhos dos CSVs. Cada arquivo encerra seu próprio último lote; a quantidade é `arredondarParaCima(registros / 500)` por arquivo.

| Arquivo | Registros de entrada | Lotes de até 500 registros |
| --- | ---: | ---: |
| `orgaos.csv` | 250 | 1 |
| `itens.csv` | 50.000 | 100 |
| `pedidos.csv` | 1.002.000 | 2.004 |
| `pedido_itens.csv` | 2.330.045 | 4.661 |
| **Total** | **3.382.295** | **6.766** |

São 6.766 lotes lógicos; reentregas podem gerar mais mensagens RabbitMQ, sem duplicar os registros. Registros de entrada, pedidos publicados e ocorrências são medidas diferentes: cabeçalhos duplicados são consolidados e registros inconsistentes ficam preservados para revisão. Uma ocorrência não equivale necessariamente a um pedido rejeitado.

| Etapa | Limite atual | Finalidade |
| --- | --- | --- |
| Leitura do CSV | 500 registros por lote | Limitar memória e permitir retomada por lote. |
| Despacho para RabbitMQ | Até 128 IDs por ciclo | Reservar os lotes que aguardam envio. |
| Consumo | 2 consumidores; prefetch 2 por consumidor | Limitar mensagens simultâneas aguardando confirmação. |
| Gravação JPA da preparação | Batches JDBC de 50 registros | Agrupar inserts dentro da transação de cada lote. |
| Publicação de pedidos | Até 8 lotes de linhas por transação | Compartilhar consultas e commit entre lotes. |

Se todos os 4.661 lotes de linhas estiverem disponíveis, a publicação pode reuni-los em 583 grupos. Durante o consumo incremental, grupos menores podem aumentar esse número. O grupo seleciona candidatos a partir de até 4.000 linhas de origem; isso **não limita o total de linhas inseridas a 4.000**, pois cada pedido candidato é consultado por inteiro. O pedido só entra quando todas as suas linhas estão disponíveis e válidas.

Para planejamento local, use **cerca de uma hora como referência observada**, sem garantia de prazo. O upload de 7,69 s mede apenas o recebimento; a duração de 1h02min19s inclui preparação, fila, publicação e validação. A versão com grupos de oito ainda precisa de nova medição completa. Não se deve dividir o tempo anterior por oito: manifesto, consumo e validação continuam tendo seus próprios custos. Hardware, disco, recursos do Docker e consultas concorrentes também influenciam o resultado.

### Persistência e reentregas

Cada consumidor bloqueia o lote, verifica se ele já terminou, grava a preparação via JPA em batches de 50 registros e marca a conclusão na mesma transação. O `ACK` ocorre depois do commit. Se houver reentrega após o commit, o marcador impede processamento duplicado. Falhas técnicas do consumidor são registradas, a entrega segue para `imports.failed` e o publicador agenda nova tentativa; após oito falhas, a carga fica em `FAILED`.

O despacho reserva até 128 lotes por ciclo, com intervalo padrão de 250 ms. A conexão PostgreSQL usa `reWriteBatchedInserts=true` para agrupar os inserts JDBC emitidos pelo Hibernate. Se configurar `DB_URL` manualmente, mantenha esse parâmetro na URL para obter a mesma otimização.

O lote guarda contagens compactas por pedido para evitar reler seu JSON completo ao construir o manifesto e selecionar candidatos. As atualizações JPA dos marcadores usam `@DynamicUpdate`, preservando o conteúdo sem regravá-lo. A reserva de despacho dura 300 segundos por padrão (`imports.dispatch-lease-seconds`), evitando republicações a cada minuto enquanto o lote aguarda consumo; falhas registradas pelo consumidor continuam usando o atraso progressivo de tentativa.

A inserção incremental usa SQL em conjunto pelo `EntityManager`. A chave única `pedidos.id_externo` decide entre transações concorrentes; `ON CONFLICT DO NOTHING RETURNING` retorna somente os pedidos realmente criados, e suas linhas são inseridas na mesma instrução. A ordem de inserção dos identificadores é determinística. Marcadores e contadores são transacionais, inclusive quando lotes diferentes contêm linhas do mesmo pedido.

O agrupamento `complete_lines` usa `MATERIALIZED` para calcular a completude e a assinatura dos pedidos uma vez por grupo de publicação. Na investigação de uma carga lenta em 18/11/2026, o plano anterior repetia esse agrupamento 499 vezes no lote analisado. Uma comparação somente de leitura da seleção dos mesmos candidatos levou 11,74 s antes e 0,56 s com materialização. Essa medição isolada, com a importação ativa, não inclui os inserts nem permite extrapolar a duração total da carga.

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

### Rotas para revisão

Após `COMPLETED_WITH_ISSUES`, use o ID descoberto por `/active` nas rotas abaixo. O fluxo normal é consultar a ocorrência, conferir as linhas na origem, salvar as correções e solicitar a revalidação.

| Operação | Rota e parâmetros |
| --- | --- |
| Listar ocorrências atuais | `GET /api/imports/{id}/issues?after=0&limit=100&history=false`. `history=true` inclui validações anteriores; uma ocorrência antiga inativa não prova que o problema foi resolvido. |
| Conferir linhas e revisões | `GET /api/imports/{id}/rows?source=itens.csv&key=A&after=0&limit=100`. Substitua arquivo e chave pelos dados da ocorrência. |
| Corrigir uma linha | `PUT /api/imports/{id}/rows/{source}/{line}`, com todos os campos CSV em `fields`, a `revision` atual e `justification`. Retorna `200` com a nova revisão. |
| Adicionar registro ausente | `POST /api/imports/{id}/rows/{source}`, com campos completos, revisão zero e justificativa. Retorna `201`; limitado aos dados em revisão. |
| Desconsiderar cópia idêntica | `POST /api/imports/{id}/rows/{source}/{line}/exclude-duplicate`, com revisão atual e justificativa. Retorna `204`, preservando outra cópia válida e a auditoria. |
| Revalidar as correções | `POST /api/imports/{id}/reprocess`, sem corpo. Retorna `202`; acompanhe `REVIEW_PENDING` por `GET /api/imports/{id}` ou `/active`. |
| Conferir registros protegidos | `GET /api/imports/{id}/quarantine?type=ITEM&limit=100`. Tipos: `ITEM`, `ORDER` e `ORGANIZATION`. |
| Consultar auditoria | `GET /api/imports/{id}/corrections?after=0&limit=100`. |

Para paginar, `issues` e `corrections` usam o último `id` retornado; `rows` usa a última `line`; `quarantine` usa a última `key`, com `after` vazio na primeira página. Nas consultas, campos como `original` e `fields` são JSON serializado em texto; na correção, envie `fields` como objeto JSON com valores textuais. Não altere o identificador do registro. Se houver conflito de revisão (`409`), releia os dados antes de corrigir novamente.

Salvar uma correção não remove a ocorrência imediatamente. Aguarde a revalidação e consulte `/issues` e `/quarantine` novamente. Não há descarte genérico de registros nem operação para marcar uma ocorrência como resolvida sem validar os dados. Falhas técnicas da carga usam `/retry`; inconsistências corrigidas usam `/reprocess`. São fluxos diferentes.

## Testes

```powershell
mvn "-Dtest=LegacyRowTest,ImportQueueIntegrationTest" test
```

A suíte exige Docker e cria PostgreSQL e RabbitMQ isolados. A massa completa é um teste optativo, separado dos testes pequenos:

O teste usa o mesmo agendador de publicação agrupada da aplicação. A espera pelo consumo e publicação tem limite padrão de 120 minutos, configurável com `-Dmass.timeout.minutes=180`, por exemplo. Esse limite de espera não é uma estimativa de duração e não inclui a validação final executada em seguida.

```powershell
mvn "-Dtest=ImportQueueIntegrationTest#massaCompletaPassaPelaFilaEPreservaInconsistencias" "-Dmass.file=massa-dados/massa-dados-candidato.zip" test
```

Esse teste verifica os 3.382.295 registros da massa fornecida, gera `target/import-incremental-report.json` com tempos, primeiros inserts e ocorrências e espera `COMPLETED_WITH_ISSUES`, pois o arquivo contém inconsistências conhecidas. Leitura, fila e inserções ocorrem simultaneamente. Os containers de teste são descartados ao final; o ZIP local e o relatório permanecem. O teste não altera o banco do Docker Compose.

RabbitMQ permite desacoplar preparação e consumo, controlar concorrência e recuperar falhas. A existência da fila não prova ganho de velocidade: os tempos medidos dependem do banco, disco, tamanho de lote e número de consumidores.

### Validação do fluxo incremental

Os testes pequenos cobrem inserts do catálogo antes do fim da carga, pedidos com linhas distribuídas entre lotes, reentregas concorrentes, publicação de grupos sobrepostos, rollback de pedidos/linhas/contadores/marcadores, retomada de cargas antigas e substituição restrita a registros provisórios. Também verificam a descoberta da carga atual por `/active`, incluindo ausência, conclusão e substituição. Os testes de Swagger verificam o contrato e suas referências.

A suíte pequena não executa a massa completa por padrão. A execução concluída abaixo valida uma versão anterior à publicação agrupada; os testes de concorrência e rollback da nova versão não substituem uma nova medição completa de desempenho.

## Resultado da massa original

A execução local mais recente concluída ocorreu em **19/11/2026**, com o fluxo incremental e antes da otimização que agrupa até oito lotes na publicação. Os horários abaixo usam `America/Sao_Paulo`.

| Medida | Resultado observado |
| --- | --- |
| Registros de entrada | 3.382.295 |
| Lotes concluídos | 6.766 |
| Resposta ao upload do ZIP | 7,69 s |
| Registro da carga | 11:16:15 |
| Conclusão da carga | 12:18:33 |
| Tempo do registro à conclusão, incluindo validação | Aproximadamente 1h02min19s |
| Órgãos publicados | 250 |
| Itens publicados | 50.000 |
| Pedidos publicados | 997.149 |
| Linhas de pedidos publicadas | 2.319.169 |
| Ocorrências ativas ao concluir | 9.843 |
| Resultado | `COMPLETED_WITH_ISSUES` |

As 9.843 ocorrências não equivalem a 9.843 pedidos rejeitados: um registro pode ter mais de uma inconsistência, e há ocorrências de itens e referências. Os contadores de publicação também não indicam que todos esses registros estão liberados para movimentação; consulte a quarentena.

Essa é uma referência local, não um prazo garantido. Ainda faltam a medição completa da publicação agrupada e um benchmark das consultas com a base carregada. Não foi comprovado ganho proporcional ao tamanho do grupo ou ao número de consumidores. A versão atual mantém as verificações de completude, duplicidade e concorrência.

### Referências históricas

O arquivo [resultado-importacao.json](resultado-importacao.json) registra a execução de **17/11/2026**, anterior ao fluxo incremental: aproximadamente 11min27s, 53.856 ocorrências e término em `NEEDS_REVIEW`, **sem publicação operacional**. Esse resultado não mede o mesmo trabalho da execução acima e não deve ser apresentado como o tempo atual da carga. O pico de memória registrado ali também pertence àquela execução, não à versão atual.

Em 18/11/2026, uma tentativa incremental foi interrompida por falta de espaço em disco. Ela não fornece um tempo total comparável. Esses registros são preservados como histórico de desenvolvimento; a referência concluída é a execução de 19/11/2026 descrita acima.
