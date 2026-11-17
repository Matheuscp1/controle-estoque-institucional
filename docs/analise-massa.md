# Análise da massa histórica

Auditoria realizada sobre `massa-dados-candidato.zip` fornecido pelo usuário. Nenhum registro foi importado, corrigido ou descartado do arquivo original. A leitura foi feita diretamente do ZIP, sem extrair os CSVs.

O resultado detalhado está em [auditoria-massa.json](auditoria-massa.json). A auditoria foi pontual; sua ferramenta auxiliar não integra o projeto. O importador definitivo será implementado no backend Java.

## Volume observado

| Arquivo | Registros, sem cabeçalho |
| --- | ---: |
| `orgaos.csv` | 250 |
| `itens.csv` | 50.000 |
| `pedidos.csv` | 1.002.000 |
| `pedido_itens.csv` | 2.330.045 |

Existem 1.000.000 de identificadores de pedido distintos: 930.254 confirmados e 69.746 cancelados. Dos itens, 48.036 estão ativos e 1.964 inativos.

## Achados

| Verificação | Quantidade |
| --- | ---: |
| Ocorrências extras de cabeçalhos de pedido idênticos | 2.000 |
| Pedidos distintos com órgão inexistente | 300 |
| Pedidos distintos sem linhas | 100 |
| Linhas com item inexistente | 250 |
| Linhas com pedido inexistente | 150 |
| Linhas com quantidade inválida ou não positiva | 200 |
| Ocorrências extras do par pedido/item | 4.513 |
| Pedidos cancelados sem data válida e/ou motivo preenchido | 13.547 |
| Pedidos com cancelamento anterior à criação | 1 |
| Pedidos com mais de 100 linhas brutas | 3 |
| Itens com divergência na reconciliação bruta | 2.824 |

As categorias podem se sobrepor; não se deve somá-las para calcular o total de rejeições. O maior pedido tem 110 linhas brutas, antes do tratamento de pares repetidos. Não foram encontrados cabeçalhos divergentes para o mesmo identificador na auditoria.

Há 76.029 linhas ligadas a itens atualmente inativos. Isso, isoladamente, não invalida o histórico: a inativação atual não comprova que o item estava inativo na data do pedido.

As unidades variam em caixa e espaços, como `UN`, `un`, `Un` e ` UN`. Todos os 1.000.000 de pedidos distintos têm data/hora sem fuso explícito. Os comprimentos textuais observados cabem nas colunas da modelagem atual.

## Consequências para a implementação

1. **Deduplicação de cabeçalhos:** cabeçalhos idênticos podem representar uma única ocorrência, registrando as duplicatas no relatório. Conteúdos divergentes devem ser separados para análise, nunca substituídos silenciosamente.
2. **Pares repetidos:** a chave composta de `pedido_itens` exige saneamento. A auditoria detecta repetição do par, mas não determina se são linhas repetidas por engano ou quantidades complementares. Não somar nem descartar automaticamente sem definir e documentar essa política.
3. **Atomicidade:** pedidos sem linhas ou com referências/quantidades inválidas precisam ser tratados por inteiro. Importar apenas as linhas válidas mudaria o pedido histórico.
4. **Cancelamentos incompletos:** a modelagem atual exige data e motivo. Antes da carga, separar ausência de motivo de ausência de data. Um motivo legado explicitamente sinalizado pode ser uma política de saneamento; datas desconhecidas não devem ser inventadas. Nenhuma dessas correções foi aplicada nesta auditoria.
5. **Unidades:** propor normalização com remoção de espaços nas extremidades e conversão para maiúsculas, registrando a transformação e aplicando a mesma regra aos novos cadastros e filtros.
6. **Datas:** propor `America/Sao_Paulo` como fuso de origem, explicitamente como premissa, usando as regras históricas do fuso. Isso não foi informado no CSV. `data_cadastro` traz somente data; a conversão para timestamp também precisa de convenção documentada.
7. **Limite de linhas:** o limite de 100 linhas da API local é uma decisão nossa, não uma exigência do enunciado. O importador não deve rejeitar pedidos históricos válidos por esse limite arbitrário. Conferir quantas linhas distintas restam nos três pedidos maiores.
8. **Reconciliação:** a auditoria compara `saldo_inicial - quantidades positivas dos pedidos confirmados` com `saldo_atual`, usando o primeiro cabeçalho por identificador e mantendo pares repetidos na soma. As 2.824 divergências são diagnósticas, não correções calculadas. A reconciliação definitiva deve ocorrer após o saneamento, com política explícita para o saldo de abertura e pedidos rejeitados.

O próximo passo da carga é implementar tabelas de preparação e relatórios de aceitação/rejeição, preservando os valores originais para rastreabilidade. Não executar o histórico pela API sobre o saldo atual, pois isso causaria novo débito.

## Estado da carga

Esta análise documenta a auditoria da massa. A implementação do importador, a aplicação das políticas de tratamento e a carga no banco continuam pendentes.
