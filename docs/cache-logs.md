# Cache Redis e logs

O Redis guarda respostas JSON das consultas HTTP de itens, órgãos e pedidos. Ele compartilha as respostas entre instâncias do backend. PostgreSQL continua sendo a fonte dos dados; as operações de saldo, versão e idempotência sempre acessam o banco. Redis não substitui RabbitMQ e não acelera os inserts da carga.

## Comportamento do cache

- TTL padrão de **2 segundos**, resposta máxima de **256 KiB**. O Redis do Compose tem limite de 128 MiB e descarta entradas menos usadas quando necessário, sem persistência em disco.
- Apenas GET com resposta 200 dos endpoints `/api/itens`, `/api/itens/{codigo}`, `/api/orgaos`, `/api/orgaos/{codigo}`, `/api/orgaos/{codigo}/pedidos` e `/api/pedidos/{id}`. Caminho, parâmetros de pesquisa/paginação e `Accept` fazem parte da chave, armazenada como hash.
- Durante a carga inicial (`X-Import-Ready: false`), as consultas vão ao banco para mostrar o avanço. Endpoints da importação, erros e requisições com autenticação ou cookies não entram no cache.
- Toda chamada HTTP de alteração em `/api/` invalida as consultas ao terminar. A publicação das correções da importação também invalida, **depois do commit**.
- A invalidação troca uma geração compartilhada no Redis. Scripts Lua verificam essa geração ao ler e gravar; uma leitura iniciada antes da invalidação não repovoa a geração atual. Entradas da geração anterior expiram pelo TTL, sem varrer todas as chaves.
- `X-Cache: HIT`, `MISS` ou `BYPASS` permite conferir o uso do cache nas consultas elegíveis. `X-Request-ID` é gerado para cada requisição, inclusive quando a resposta vem do cache.

Isso fornece consistência eventual nas consultas: uma requisição simultânea a uma gravação pode mostrar o estado anterior. Escritas por SQL externo ou chamadas internas fora do fluxo HTTP dependem do TTL; novos fluxos de escrita devem invalidar após commit. A transação PostgreSQL e a invalidação Redis não são uma transação única. Se a invalidação falhar, outra instância pode ver uma entrada anterior até ela expirar. Nenhuma dessas respostas é usada para autorizar pedidos ou movimentações.

Se o Redis falhar, a consulta segue no banco. O cliente usa timeout de conexão/comando de 200 ms; depois de uma falha, a instância deixa de tentar usar o cache por 5 segundos e emite um aviso sem dados da requisição. O TTL limita respostas antigas em outras instâncias. Uma queda do Redis não deve impedir uma alteração já confirmada no PostgreSQL.

## Configuração

O `docker compose up --build` inclui Redis. Para executar o backend pela IDE, pode iniciar apenas o serviço com `docker compose up -d redis`.

| Variável | Padrão | Uso |
| --- | --- | --- |
| `REDIS_HOST` | `localhost` (Compose: `redis`) | Servidor de cache |
| `REDIS_PORT` | `6379` | Porta |
| `REDIS_PASSWORD` | vazio | Senha de um Redis externo configurado com autenticação |
| `READ_CACHE_ENABLED` | `true` | Desativar cache com `false` |
| `READ_CACHE_TTL_MS` | `2000` | Validade em milissegundos |
| `LOG_DIRECTORY` | `./logs` | Diretório dos arquivos de log |

O Compose expõe Redis somente em `127.0.0.1` para desenvolvimento. Instâncias que compartilham o cache devem usar o mesmo banco operacional e o mesmo Redis dedicado a esse ambiente. A chave usa o prefixo `estoque:{http-v2}:`. Não compartilhe esse Redis entre bases de ambientes diferentes.

## Logs

Os logs vão ao console e a `logs/estoque.log`. A rotação ocorre diariamente ou a cada 10 MiB, com histórico de até 7 dias e teto de 200 MiB nos arquivos arquivados. A pasta é ignorada pelo Git. No container, acompanhe por `docker compose logs -f backend`; arquivos locais do container não sobrevivem à sua remoção sem um volume configurado.

Cada requisição registra método, modelo da rota, status, duração e resultado do cache, com identificador `requestId`. Rotas sem handler (por exemplo, bloqueio antes do controller) e respostas servidas pelo cache aparecem como `[unmapped-or-cached]`. O filtro não registra corpo, query string, cookies nem tokens. Respostas 4xx geram WARN e 5xx geram ERROR; exceções inesperadas também limpam o contexto da thread.

A importação registra início/fim da preparação do ZIP, progresso periódico a cada 30 segundos enquanto prepara/insere, falhas de lote e conclusão após commit com status e quantidade de ocorrências. Não existe log INFO por linha do CSV. A frequência pode ser ajustada por `imports.log-progress-ms`. Logs não substituem as ocorrências e o histórico de correções no banco.

A implementação usa [Spring Data Redis com scripts](https://docs.spring.io/spring-data/redis/reference/redis/scripting.html) para comparar a geração e acessar as entradas atomicamente.
