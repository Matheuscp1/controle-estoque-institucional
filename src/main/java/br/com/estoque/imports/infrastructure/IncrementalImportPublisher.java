package br.com.estoque.imports.infrastructure;

import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.persistence.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;

/** Insere dados provisórios, mantendo o acesso operacional bloqueado até a reconciliação final. */
public class IncrementalImportPublisher {
    private final EntityManager em;
    public IncrementalImportPublisher(EntityManager em) {this.em=em;}

    @Transactional
    public void publishCatalogs(String id) {
        var control=em.find(ImportControlEntity.class,1,LockModeType.PESSIMISTIC_WRITE);
        var job=em.find(ImportJobEntity.class,id);
        if(!active(control,job))return;
        restoreSourceMarkers(job);
        for(String source:List.of("orgaos.csv","itens.csv")) {
            if(!sourceDone(id,source) || sourcePublished(id,source))continue;
            String script=source.equals("orgaos.csv")?"publish-organizations.sql":"publish-items.sql";
            int inserted=query(script,id).executeUpdate();
            String table=source.equals("orgaos.csv")?"orgaos":"itens";
            long foreign=((Number)em.createNativeQuery("SELECT COUNT(*) FROM "+table+" WHERE import_job_id IS NULL OR import_job_id<>:job")
                    .setParameter("job",id).getSingleResult()).longValue();
            if(foreign>0)throw new IllegalStateException("Há dados fora da carga provisória: "+table);
            String counter=source.equals("orgaos.csv")?"published_orgs":"published_items";
            em.createNativeQuery("UPDATE import_jobs SET "+counter+"="+counter+"+:count WHERE id=:job")
                    .setParameter("count",inserted).setParameter("job",id).executeUpdate();
            em.createNativeQuery("UPDATE import_sources SET published=true WHERE job_id=:job AND source=:source")
                    .setParameter("job",id).setParameter("source",source).executeUpdate();
        }
    }

    @Transactional
    public void prepareManifest(String id) {
        var control=em.find(ImportControlEntity.class,1,LockModeType.PESSIMISTIC_READ);
        var job=em.find(ImportJobEntity.class,id);
        if(!active(control,job)||!job.status.equals("STAGING")||job.manifestReady)return;
        em.createNativeQuery("SELECT id FROM import_jobs WHERE id=:id FOR NO KEY UPDATE").setParameter("id",id).getSingleResult();
        em.refresh(job);
        if(!active(control,job)||!job.status.equals("STAGING")||job.manifestReady)return;
        // Todos os lotes já estão persistidos, mesmo que os consumidores ainda estejam trabalhando.
        // O manifesto permite saber se um pedido recebeu TODAS as linhas, sem depender da ordem da fila.
        long pending=em.createQuery("SELECT COUNT(b) FROM ImportBatchEntity b WHERE b.jobId=:id AND b.source='pedido_itens.csv' AND b.done=false",Long.class)
                .setParameter("id",id).getSingleResult();
        if(pending==0) {
            em.createNativeQuery("""
                INSERT INTO import_order_manifest(job_id,order_code,expected_lines)
                SELECT :job,record_key,COUNT(*) FROM import_rows
                WHERE job_id=:job AND source='pedido_itens.csv' AND record_key IS NOT NULL
                GROUP BY record_key ON CONFLICT(job_id,order_code) DO NOTHING
                """).setParameter("job",id).executeUpdate();
        } else em.createNativeQuery("""
            INSERT INTO import_order_manifest(job_id,order_code,expected_lines)
            SELECT :job,code,SUM(quantity) FROM (
                SELECT r.key AS code,CAST(r.value AS BIGINT) AS quantity
                FROM import_batches b CROSS JOIN LATERAL jsonb_each_text(CAST(b.order_counts AS jsonb)) r
                WHERE b.job_id=:job AND b.source='pedido_itens.csv' AND b.order_counts IS NOT NULL
                UNION ALL
                SELECT substring(r.value->'fields'->>'id_externo_pedido',1,128),1
                FROM import_batches b CROSS JOIN LATERAL jsonb_array_elements(CAST(b.payload AS jsonb)->'rows') r(value)
                WHERE b.job_id=:job AND b.source='pedido_itens.csv' AND b.order_counts IS NULL
                  AND r.value->'fields'->>'id_externo_pedido' IS NOT NULL
            ) counts GROUP BY code
            ON CONFLICT(job_id,order_code) DO NOTHING
            """).setParameter("job",id).executeUpdate();
        job.manifestReady=true;
    }

    @Transactional(readOnly=true)
    public List<String> pendingBatches(String id) {
        var job=em.find(ImportJobEntity.class,id);
        if(job==null||!job.manifestReady||!sourceDone(id,"pedidos.csv")
                ||!sourcePublished(id,"orgaos.csv")||!sourcePublished(id,"itens.csv"))return List.of();
        return em.createQuery("SELECT b.id FROM ImportBatchEntity b WHERE b.jobId=:id AND b.source='pedido_itens.csv' AND b.done=true AND b.publicationDone=false ORDER BY b.batchNumber",String.class)
                .setParameter("id",id).setMaxResults(64).getResultList();
    }

    @Transactional
    public void publishBatch(String batchId) {
        // Leitores compartilham o lock; a substituição de uma carga e a liberação final exigem lock exclusivo.
        var control=em.find(ImportControlEntity.class,1,LockModeType.PESSIMISTIC_READ);
        var batch=em.find(ImportBatchEntity.class,batchId,LockModeType.PESSIMISTIC_WRITE);
        if(batch==null||!batch.done||batch.publicationDone||!batch.source.equals("pedido_itens.csv"))return;
        var job=em.find(ImportJobEntity.class,batch.jobId);
        if(!active(control,job)||!job.manifestReady||!sourceDone(job.id,"pedidos.csv")
                ||!sourcePublished(job.id,"orgaos.csv")||!sourcePublished(job.id,"itens.csv"))return;
        Object[] counts=(Object[])query("publish-orders.sql",job.id).setParameter("batch",batchId).getSingleResult();
        em.createNativeQuery("UPDATE import_jobs SET published_orders=published_orders+:orders,published_lines=published_lines+:lines WHERE id=:job")
                .setParameter("orders",((Number)counts[0]).longValue()).setParameter("lines",((Number)counts[1]).longValue())
                .setParameter("job",job.id).executeUpdate();
        batch.publicationDone=true;
    }

    private boolean sourceDone(String id,String source) {
        return ((Number)em.createNativeQuery("""
            SELECT COUNT(*) FROM import_sources s WHERE s.job_id=:job AND s.source=:source
            AND s.expected_batches=(SELECT COUNT(*) FROM import_batches b WHERE b.job_id=s.job_id AND b.source=s.source AND b.done=true)
            """).setParameter("job",id).setParameter("source",source).getSingleResult()).longValue()==1;
    }
    private boolean sourcePublished(String id,String source) {
        return ((Number)em.createNativeQuery("SELECT COUNT(*) FROM import_sources WHERE job_id=:job AND source=:source AND published=true")
                .setParameter("job",id).setParameter("source",source).getSingleResult()).longValue()==1;
    }
    private boolean active(ImportControlEntity control,ImportJobEntity job) {
        return job!=null&&!control.ready&&job.id.equals(control.activeJob)&&Set.of("PREPARING","STAGING").contains(job.status);
    }
    private void restoreSourceMarkers(ImportJobEntity job) {
        // Compatibilidade com cargas preparadas antes da migração V4.
        if(!job.status.equals("STAGING"))return;
        em.createNativeQuery("""
            INSERT INTO import_sources(job_id,source,expected_batches)
            SELECT :job,s.source,COUNT(b.id)
            FROM (VALUES ('orgaos.csv'),('itens.csv'),('pedidos.csv'),('pedido_itens.csv')) s(source)
            LEFT JOIN import_batches b ON b.job_id=:job AND b.source=s.source
            GROUP BY s.source ON CONFLICT(job_id,source) DO NOTHING
            """).setParameter("job",job.id).executeUpdate();
    }
    private Query query(String script,String id) {
        try(var input=new ClassPathResource("imports/"+script).getInputStream()) {
            return em.createNativeQuery(new String(input.readAllBytes(),StandardCharsets.UTF_8)).setParameter("job",id);
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
}
