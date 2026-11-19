package br.com.estoque.imports.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public class IncrementalPublicationScheduler {
    private static final Logger LOG=LoggerFactory.getLogger(IncrementalPublicationScheduler.class);
    private final ImportStore store;
    private final IncrementalImportPublisher publisher;
    public IncrementalPublicationScheduler(ImportStore store,IncrementalImportPublisher publisher) {this.store=store;this.publisher=publisher;}
    @Scheduled(fixedDelayString="${imports.incremental-delay-ms:250}",initialDelayString="${imports.initial-delay-ms:0}")
    public void publish() {
        for(String id:store.awaitingPublication()) {
            try {
                publisher.publishCatalogs(id);
                publisher.prepareManifest(id);
                var batches=publisher.pendingBatches(id);
                for(int offset=0;offset<batches.size();offset+=IncrementalImportPublisher.PUBLICATION_BATCH_LIMIT)
                    publisher.publishBatches(batches.subList(offset,Math.min(batches.size(),offset+IncrementalImportPublisher.PUBLICATION_BATCH_LIMIT)));
            }catch(Exception e){LOG.error("Inserção incremental da carga {} falhou e será repetida",id,e);}
        }
    }
}
