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
        em.createNativeQuery("DELETE FROM import_issues WHERE job_id=:job").setParameter("job",id).executeUpdate();
        script("validate.sql",id);
        long issueCount=((Number)em.createNativeQuery("SELECT COUNT(*) FROM import_issues WHERE job_id=:job").setParameter("job",id).getSingleResult()).longValue();
        if(issueCount>0) {job.status="NEEDS_REVIEW";job.error="Carga preservada em preparação. Resolva as inconsistências antes de reenviar.";return;}
        for(String table:java.util.List.of("orgaos","itens","pedidos")) {
            if(((Number)em.createNativeQuery("SELECT COUNT(*) FROM "+table).getSingleResult()).longValue()>0)
                throw new IllegalStateException("Tabelas operacionais alteradas durante a carga.");
        }
        job.duplicateHeaders=((Number)em.createNativeQuery("SELECT COUNT(*)-(SELECT COUNT(*) FROM load_orgs)-(SELECT COUNT(*) FROM load_items)-(SELECT COUNT(*) FROM load_orders) FROM import_rows WHERE job_id=:job AND source<>'pedido_itens.csv'")
                .setParameter("job",id).getSingleResult()).longValue();
        script("promote.sql",id);
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
