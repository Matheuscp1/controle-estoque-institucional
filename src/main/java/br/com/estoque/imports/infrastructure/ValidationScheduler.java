package br.com.estoque.imports.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public class ValidationScheduler {
    private static final Logger LOG=LoggerFactory.getLogger(ValidationScheduler.class);
    private final ImportStore store;
    private final ImportFinalizer finalizer;
    public ValidationScheduler(ImportStore store,ImportFinalizer finalizer) {this.store=store;this.finalizer=finalizer;}
    @Scheduled(fixedDelayString="${imports.validate-delay-ms:5000}",initialDelayString="${imports.initial-delay-ms:0}")
    public void validate() {
        for(String id:store.awaitingValidation()) {
            try {finalizer.finish(id);} catch(Exception e){store.validationFailed(id,e.getMessage());LOG.error("Validação da carga {} falhou; consulte o estado e o erro registrados",id,e);}
        }
    }
}
