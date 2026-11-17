# Importação inicial com RabbitMQ

A entrada é o ZIP original com `orgaos.csv`, `itens.csv`, `pedidos.csv` e `pedido_itens.csv` na raiz. O arquivo local de referência está em `massa-dados/massa-dados-candidato.zip`. Não é necessário descompactá-lo.

## Executar

Com Docker em execução, `docker compose up --build` inicia PostgreSQL, RabbitMQ e a API. O Compose habilita `IMPORTS_ENABLED=true` e utiliza um volume persistente para os arquivos recebidos. A carga inicial exige tabelas operacionais vazias. Não apague um volume existente para importar novamente: use um ambiente separado caso já existam dados de uso.

No PowerShell, envie o ZIP:

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
| `STAGING` | Os lotes estão em processamento ou aguardam validação global. |
| `NEEDS_REVIEW` | Há inconsistências nos dados; a publicação foi bloqueada. |
| `COMPLETED` | Os dados foram validados e publicados atomicamente. |
| `FAILED` | Falha técnica ou de formato requer intervenção e eventual retomada. |

O endpoint `retry` retoma somente a carga ativa em `FAILED`, preservando lotes já concluídos. Para corrigir dados em `NEEDS_REVIEW`, preserve o ZIP original, prepare uma versão corrigida com justificativas e envie o novo ZIP. Reenviar o mesmo conteúdo retorna a mesma carga, identificada pelo hash.

Enquanto a importação estiver habilitada e não houver uma carga concluída, as APIs operacionais respondem `503`. As APIs de importação continuam disponíveis. Fora do Compose, `IMPORTS_ENABLED` é falso por padrão para permitir desenvolvimento dos módulos sem RabbitMQ.

## Processamento e concorrência

O leitor mantém até 500 registros por lote. O conteúdo e a identificação determinística do lote são registrados no PostgreSQL antes da publicação. RabbitMQ recebe somente o ID do lote; as mensagens e filas são duráveis. O publicador exige confirmação do broker e republica lotes pendentes após expirar sua reserva.

Cada consumidor bloqueia o lote, verifica se ele já terminou, grava a preparação via JPA em batches de 50 registros e marca a conclusão na mesma transação. O `ACK` ocorre depois do commit. Se houver reentrega após o commit, o marcador impede processamento duplicado. Falhas técnicas do consumidor são registradas, a entrega segue para `imports.failed` e o publicador agenda nova tentativa; após oito falhas, a carga fica em `FAILED`.

As tabelas `import_rows` preservam os campos originais e os campos normalizados separadamente. Após todos os lotes, a validação global usa operações SQL em conjunto para verificar referências, duplicidades, saldo final e saldo ao longo do histórico. Essa etapa não depende da ordem de chegada das mensagens.

## Política de consistência

Esta implementação adota publicação integral: **qualquer inconsistência bloqueia a carga inteira nas tabelas operacionais**. Isso evita publicar saldos incoerentes após excluir pedidos inválidos. Os dados permanecem na preparação para análise; não são silenciosamente descartados, somados nem corrigidos.

- Cabeçalhos equivalentes após normalização são consolidados e contabilizados. Cabeçalhos divergentes com a mesma chave impedem a carga.
- Pares pedido/item repetidos impedem a carga, mesmo se a quantidade for igual: não é possível inferir se são duplicações ou parcelas complementares.
- Referências inexistentes, quantidades inválidas, pedidos sem itens e cancelamentos incompletos impedem a carga.
- Unidades são normalizadas com remoção dos espaços das extremidades e conversão para maiúsculas. Os campos originais permanecem disponíveis.
- Datas sem fuso são interpretadas em `America/Sao_Paulo`. Horários ambíguos ou inexistentes por transição de fuso são rejeitados.
- O saldo final deve corresponder ao saldo inicial menos os pedidos confirmados. O histórico de reservas e devoluções também não pode produzir saldo negativo. Na ausência de uma ordem conhecida para eventos simultâneos, reservas precedem devoluções; essa premissa conservadora pode exigir revisão de casos do legado.
- A situação atual de um item inativo não invalida, sozinha, seus pedidos históricos.

A publicação insere o histórico e seus saldos em uma única transação. Não chama a reserva online para cada pedido antigo, evitando descontar o histórico novamente. As requisições posteriores usam o módulo `orders`, inclusive para reconhecer reenvios e cancelar pedidos importados.

## Testes

```powershell
mvn "-Dtest=LegacyRowTest,ImportQueueIntegrationTest" test
```

A suíte exige Docker e cria PostgreSQL e RabbitMQ isolados. A massa completa é um teste optativo, separado dos testes pequenos:

```powershell
mvn "-Dtest=ImportQueueIntegrationTest#massaCompletaPassaPelaFilaEPreservaInconsistencias" "-Dmass.file=massa-dados/massa-dados-candidato.zip" test
```

Esse teste verifica os 3.382.295 registros da massa fornecida, gera `target/import-mass-report.json` com tempos e ocorrências e espera `NEEDS_REVIEW`, pois o arquivo contém inconsistências conhecidas. Os containers de teste são descartados ao final; o ZIP local e o relatório permanecem. O teste não altera o banco do Docker Compose.

RabbitMQ permite desacoplar preparação e consumo, controlar concorrência e recuperar falhas. A existência da fila não prova ganho de velocidade: os tempos medidos dependem do banco, disco, tamanho de lote e número de consumidores.

## Resultado da massa original

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
