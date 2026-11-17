package br.com.estoque.orders.application;

/** Sinaliza uma corrida de inserção; a consulta de replay exige outra transação. */
public class DuplicateExternalId extends RuntimeException {
    public DuplicateExternalId(Throwable cause) { super(cause); }
}
