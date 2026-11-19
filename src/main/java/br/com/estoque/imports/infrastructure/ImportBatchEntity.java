package br.com.estoque.imports.infrastructure;

import java.time.OffsetDateTime;
import javax.persistence.*;

@Entity @Table(name="import_batches") @org.hibernate.annotations.DynamicUpdate
public class ImportBatchEntity {
    @Id @Column(length=64) String id;
    @Column(name="job_id",nullable=false,length=64) String jobId;
    @Column(nullable=false,length=30) String source;
    @Column(name="batch_number",nullable=false) int batchNumber;
    @Column(nullable=false,columnDefinition="text") String payload;
    @Column(name="order_counts",columnDefinition="text",updatable=false) String orderCounts;
    @Column(nullable=false) boolean done;
    @Column(name="publication_done",nullable=false) boolean publicationDone;
    @Column(nullable=false) int attempts;
    @Column(name="next_publish",nullable=false,columnDefinition="timestamp with time zone") OffsetDateTime nextPublish;
    @Column(columnDefinition="text") String error;
    protected ImportBatchEntity() {}
}
