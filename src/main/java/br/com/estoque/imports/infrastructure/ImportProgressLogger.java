package br.com.estoque.imports.infrastructure;

import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;

public class ImportProgressLogger {
    private static final Logger LOG=LoggerFactory.getLogger(ImportProgressLogger.class);
    private final ImportStore store;
    public ImportProgressLogger(ImportStore store) {this.store=store;}
    @Scheduled(fixedDelayString="${imports.log-progress-ms:30000}",initialDelayString="${imports.initial-delay-ms:0}")
    public void progress() {
        for(String id:store.awaitingPublication()) {
            var job=store.status(id);
            LOG.info("import progress job={} status={} preparedBatches={} completedBatches={} publishedItems={} publishedOrders={} publishedLines={}",
                    id,job.status(),job.preparedBatches(),job.completedBatches(),job.publishedItems(),job.publishedOrders(),job.publishedOrderLines());
        }
    }
}
