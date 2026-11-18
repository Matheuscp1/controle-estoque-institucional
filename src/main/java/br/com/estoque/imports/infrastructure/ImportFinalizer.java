package br.com.estoque.imports.infrastructure;

import java.nio.charset.StandardCharsets;
import javax.persistence.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;

public class ImportFinalizer {
    private final EntityManager em;
    public ImportFinalizer(EntityManager em) {this.em=em;}

    @Transactional
    public void finish(String id) {
        var control=em.find(ImportControlEntity.class,1,LockModeType.PESSIMISTIC_WRITE);
        var job=em.find(ImportJobEntity.class,id,LockModeType.PESSIMISTIC_WRITE);
        if(job==null||!job.status.equals("STAGING")||!id.equals(control.activeJob)) return;
        long pending=em.createQuery("SELECT COUNT(b) FROM ImportBatchEntity b WHERE b.jobId=:id AND b.done=false",Long.class).setParameter("id",id).getSingleResult();
        long batches=em.createQuery("SELECT COUNT(b) FROM ImportBatchEntity b WHERE b.jobId=:id",Long.class).setParameter("id",id).getSingleResult();
        if(pending>0||batches!=job.totalBatches) return;
        long unpublished=em.createQuery("SELECT COUNT(b) FROM ImportBatchEntity b WHERE b.jobId=:id AND b.source='pedido_itens.csv' AND b.publicationDone=false",Long.class).setParameter("id",id).getSingleResult();
        long catalogs=((Number)em.createNativeQuery("SELECT COUNT(*) FROM import_sources WHERE job_id=:id AND source IN ('orgaos.csv','itens.csv') AND published=true").setParameter("id",id).getSingleResult()).longValue();
        if(!job.manifestReady||unpublished>0||catalogs!=2)return;
        em.createNativeQuery("DELETE FROM import_issues WHERE job_id=:job").setParameter("job",id).executeUpdate();
        script("validate.sql",id);
        long issueCount=((Number)em.createNativeQuery("SELECT COUNT(*) FROM import_issues WHERE job_id=:job").setParameter("job",id).getSingleResult()).longValue();
        if(issueCount>0) {job.status="NEEDS_REVIEW";job.error="Dados provisórios inseridos. Uso bloqueado até resolver as inconsistências.";return;}
        var tables=java.util.Map.of("orgaos","load_orgs","itens","load_items","pedidos","load_orders","pedido_itens","load_lines");
        for(String table:java.util.List.of("orgaos","itens","pedidos")) {
            long foreign=((Number)em.createNativeQuery("SELECT COUNT(*) FROM "+table+" WHERE import_job_id IS NULL OR import_job_id<>:job")
                    .setParameter("job",id).getSingleResult()).longValue();
            if(foreign>0)throw new IllegalStateException("Há dados fora da carga provisória: "+table);
        }
        for(var table:tables.entrySet()) {
            long actual=((Number)em.createNativeQuery("SELECT COUNT(*) FROM "+table.getKey()).getSingleResult()).longValue();
            long expected=((Number)em.createNativeQuery("SELECT COUNT(*) FROM "+table.getValue()).getSingleResult()).longValue();
            if(actual!=expected)throw new IllegalStateException("Contagem divergente após inserção incremental: "+table.getKey());
        }
        job.duplicateHeaders=((Number)em.createNativeQuery("SELECT COUNT(*)-(SELECT COUNT(*) FROM load_orgs)-(SELECT COUNT(*) FROM load_items)-(SELECT COUNT(*) FROM load_orders) FROM import_rows WHERE job_id=:job AND source<>'pedido_itens.csv'")
                .setParameter("job",id).getSingleResult()).longValue();
        job.status="COMPLETED";job.error=null;control.ready=true;
    }

    private void script(String name,String id) {
        String sql;
        try(var input=new ClassPathResource("imports/"+name).getInputStream()) {sql=new String(input.readAllBytes(),StandardCharsets.UTF_8);}
        catch(java.io.IOException e){throw new IllegalStateException(e);}
        for(String statement:sql.split(";")) {
            if(statement.isBlank())continue;
            var query=em.createNativeQuery(statement);
            if(statement.contains(":job"))query.setParameter("job",id);
            query.executeUpdate();
        }
    }
}
