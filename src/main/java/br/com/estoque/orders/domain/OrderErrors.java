package br.com.estoque.orders.domain;

public final class OrderErrors {
    private OrderErrors() {}

    public static class Conflict extends RuntimeException {
        public Conflict(String message) { super(message); }
    }

    public static class NotFound extends RuntimeException {
        public NotFound(String message) { super(message); }
    }
}
