package br.com.estoque.items.domain;

public final class ErroItem {
    private ErroItem() {}

    public static class StockConflict extends RuntimeException {
        public StockConflict(String message) { super(message); }
    }

    public static class NaoEncontrado extends RuntimeException {
        public NaoEncontrado(String codigo) { super("Item não encontrado: " + codigo); }
    }

    public static class Conflito extends RuntimeException {
        public Conflito() { super("Item alterado por outra operação. Recarregue antes de salvar."); }
    }

    public static class CodigoDuplicado extends RuntimeException {
        public CodigoDuplicado(String codigo) { super("Código de item já cadastrado: " + codigo); }
    }
}
