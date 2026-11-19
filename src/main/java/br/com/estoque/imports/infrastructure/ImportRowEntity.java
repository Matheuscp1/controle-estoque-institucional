package br.com.estoque.imports.infrastructure;

import java.io.Serializable;
import java.util.Objects;
import javax.persistence.*;

@Entity @Table(name="import_rows")
public class ImportRowEntity {
    @Embeddable public static class Key implements Serializable {
        @Column(name="job_id",length=64) String jobId;
        @Column(length=30) String source;
        @Column(name="line_number") long line;
        public Key() {}
        Key(String jobId,String source,long line) {this.jobId=jobId;this.source=source;this.line=line;}
        @Override public boolean equals(Object o) { return o instanceof Key k && line==k.line && Objects.equals(jobId,k.jobId) && Objects.equals(source,k.source); }
        @Override public int hashCode() { return Objects.hash(jobId,source,line); }
    }
    @EmbeddedId Key id;
    @Column(name="record_key",length=128) String recordKey;
    @Column(name="reference_key",length=128) String referenceKey;
    @Column(name="raw_payload",nullable=false,columnDefinition="text") String rawPayload;
    @Column(columnDefinition="text") String payload;
    @Column(columnDefinition="text") String error;
    protected ImportRowEntity() {}
}
