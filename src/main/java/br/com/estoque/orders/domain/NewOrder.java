package br.com.estoque.orders.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public record NewOrder(String externalId, String organizationCode, String requester,
                       OrderSource source, List<OrderLine> items) {
    public NewOrder {
        required(externalId, "Identificador externo", 128);
        required(organizationCode, "Código do órgão", 64);
        required(requester, "Solicitante", 255);
        if (source == null) throw new IllegalArgumentException("Origem obrigatória.");
        if (items == null || items.isEmpty() || items.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Pedido deve conter um ou mais itens válidos.");
        items = items.stream().sorted(Comparator.comparing(OrderLine::itemCode)).toList();
        if (items.stream().map(OrderLine::itemCode).distinct().count() != items.size())
            throw new IllegalArgumentException("Não repita o mesmo item no pedido.");
    }

    public String signature() {
        var content = new StringBuilder();
        append(content, organizationCode);
        append(content, requester);
        append(content, source.name());
        for (var line : items) {
            append(content, line.itemCode());
            append(content, line.quantity().stripTrailingZeros().toPlainString());
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static void append(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value);
    }

    static void required(String value, String field, int max) {
        if (value == null || value.isBlank() || value.length() > max)
            throw new IllegalArgumentException(field + " é obrigatório e deve ter até " + max + " caracteres.");
    }
}
