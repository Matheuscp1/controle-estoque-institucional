package br.com.estoque.imports.infrastructure;

import java.time.OffsetDateTime;
import javax.persistence.*;

@Entity @Table(name="import_batches")
public class ImportBatchEntity {
    @Id @Column(length=64) String id;
    @Column(name="job_id",nullable=false,length=64) String jobId;
    @Column(nullable=false,length=30) String source;
    @Column(name="batch_number",nullable=false) int batchNumber;
    @Column(nullable=false,columnDefinition="text") String payload;
    @Column(nullable=false) boolean done;
    @Column(nullable=false) int attempts;
    @Column(name="next_publish",nullable=false,columnDefinition="timestamp with time zone") OffsetDateTime nextPublish;
    @Column(columnDefinition="text") String error;
    protected ImportBatchEntity() {}
}
