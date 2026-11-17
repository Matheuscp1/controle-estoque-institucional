package br.com.estoque.imports.application;

public interface BatchProcessor {
    /** Retorna somente depois do commit. Reentregas de um lote concluído não produzem efeitos. */
    void process(String batchId);
}
