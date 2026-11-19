package br.com.estoque.imports.domain;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class LegacyRow {
    public static final Map<String,List<String>> HEADERS = Map.of(
        "orgaos.csv", List.of("codigo","sigla","nome","uf"),
        "itens.csv", List.of("codigo","descricao","unidade","saldo_inicial","saldo_atual","ativo","data_cadastro"),
        "pedidos.csv", List.of("id_externo","codigo_orgao","solicitante","origem","data_hora","situacao","data_cancelamento","motivo_cancelamento"),
        "pedido_itens.csv", List.of("id_externo_pedido","codigo_item","quantidade"));
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Set<String> UFS = Set.of("AC","AL","AP","AM","BA","CE","DF","ES","GO","MA","MT","MS","MG","PA","PB","PR","PE","PI","RJ","RN","RS","RO","RR","SC","SP","SE","TO");
    private LegacyRow() {}

    public static Map<String,String> parse(String source, Map<String,String> raw) {
        try { return parseFields(source,raw); }
        catch (DateTimeException e) { throw new IllegalArgumentException("Data inválida: " + e.getMessage()); }
    }
    private static Map<String,String> parseFields(String source, Map<String,String> raw) {
        if (!HEADERS.containsKey(source) || !raw.keySet().equals(new HashSet<>(HEADERS.get(source))))
            throw new IllegalArgumentException("Colunas incompatíveis com o arquivo.");
        var row = new TreeMap<>(raw);
        switch (source) {
            case "orgaos.csv" -> {
                text(row,"codigo",64); text(row,"sigla",30); text(row,"nome",255);
                option(row,"uf",UFS);
            }
            case "itens.csv" -> {
                text(row,"codigo",64); text(row,"descricao",500);
                row.put("unidade", row.get("unidade").strip().toUpperCase(Locale.ROOT));
                text(row,"unidade",30); decimal(row,"saldo_inicial",false); decimal(row,"saldo_atual",false);
                option(row,"ativo",Set.of("S","N"));
                row.put("data_cadastro",LocalDate.parse(row.get("data_cadastro")).atStartOfDay(ZONE).toOffsetDateTime().toString());
            }
            case "pedidos.csv" -> {
                text(row,"id_externo",128); text(row,"codigo_orgao",64); text(row,"solicitante",255);
                option(row,"origem",Set.of("PORTAL","INTEGRACAO"));
                option(row,"situacao",Set.of("CONFIRMADO","CANCELADO"));
                var created = timestamp(row.get("data_hora")); row.put("data_hora",created.toString());
                if (row.get("situacao").equals("CANCELADO")) {
                    String reason=row.get("motivo_cancelamento");
                    if(reason!=null && reason.length()>500) throw new IllegalArgumentException("Campo inválido: motivo_cancelamento");
                    row.put("motivo_cancelamento",reason==null||reason.isBlank()?"":reason);
                    var cancelled = timestamp(row.get("data_cancelamento"));
                    if (cancelled.isBefore(created)) throw new IllegalArgumentException("Cancelamento anterior ao pedido.");
                    row.put("data_cancelamento",cancelled.toString());
                } else if (!row.get("data_cancelamento").isBlank() || !row.get("motivo_cancelamento").isBlank()) {
                    throw new IllegalArgumentException("Pedido confirmado com dados de cancelamento.");
                }
            }
            case "pedido_itens.csv" -> {
                text(row,"id_externo_pedido",128); text(row,"codigo_item",64); decimal(row,"quantidade",true);
            }
            default -> throw new IllegalArgumentException("Arquivo desconhecido.");
        }
        return row;
    }

    private static OffsetDateTime timestamp(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Data obrigatória ausente.");
        var local = LocalDateTime.parse(value);
        var offsets = ZONE.getRules().getValidOffsets(local);
        if (offsets.size() != 1) throw new IllegalArgumentException("Horário local inexistente ou ambíguo.");
        return local.atOffset(offsets.get(0));
    }
    private static void text(Map<String,String> row,String field,int max) {
        String value = row.get(field);
        if (value == null || value.isBlank() || value.length() > max)
            throw new IllegalArgumentException("Campo inválido: " + field);
    }
    private static void option(Map<String,String> row,String field,Set<String> values) {
        if (!values.contains(row.get(field))) throw new IllegalArgumentException("Valor inválido: " + field);
    }
    private static void decimal(Map<String,String> row,String field,boolean positive) {
        BigDecimal value;
        try { value = new BigDecimal(row.get(field)); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Número inválido: " + field); }
        if (value.signum() < 0 || (positive && value.signum()==0) || value.stripTrailingZeros().scale()>3
            || value.compareTo(new BigDecimal("10000000000000000"))>=0)
            throw new IllegalArgumentException("Quantidade fora do intervalo: " + field);
        row.put(field,value.stripTrailingZeros().toPlainString());
    }
}
