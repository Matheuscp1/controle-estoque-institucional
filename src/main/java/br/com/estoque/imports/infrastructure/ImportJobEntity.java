package br.com.estoque.imports.infrastructure;

import java.time.OffsetDateTime;
import javax.persistence.*;

@Entity @Table(name="import_jobs")
public class ImportJobEntity {
    @Id @Column(length=64) String id;
    @Column(nullable=false,length=30) String status;
    @Column(name="created_at",nullable=false,columnDefinition="timestamp with time zone") OffsetDateTime createdAt;
    @Column(name="lease_until",columnDefinition="timestamp with time zone") OffsetDateTime leaseUntil;
    @Column(name="total_batches",nullable=false) int totalBatches;
    @Column(name="duplicate_headers",nullable=false) long duplicateHeaders;
    @Column(columnDefinition="text") String error;
    protected ImportJobEntity() {}
    ImportJobEntity(String id) { this.id=id; this.status="PREPARING"; this.createdAt=OffsetDateTime.now(); }
    public record View(String id,String status,int totalBatches,long completedBatches,long issues,long duplicateHeaders,String error) {}
}
