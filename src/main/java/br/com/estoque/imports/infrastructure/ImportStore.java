package br.com.estoque.imports.infrastructure;

import br.com.estoque.imports.domain.BatchPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;
import javax.persistence.*;
import org.springframework.transaction.annotation.Transactional;

public class ImportStore {
    private final EntityManager em;
    private final ObjectMapper json;
    public ImportStore(EntityManager em,ObjectMapper json) { this.em=em;this.json=json; }

    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    @Transactional
    public String register(String hash) {
        var control = em.find(ImportControlEntity.class,1,LockModeType.PESSIMISTIC_WRITE);
        var existing = em.find(ImportJobEntity.class,hash);
        if (existing!=null) return existing.id;
        if (control.activeJob!=null) {
            var active=em.find(ImportJobEntity.class,control.activeJob);
            if (Set.of("PREPARING","STAGING").contains(active.status)) throw new IllegalStateException("Já existe uma carga em execução.");
        }
        for (String table : List.of("orgaos","itens","pedidos")) {
            if (((Number)em.createNativeQuery("SELECT COUNT(*) FROM " + table).getSingleResult()).longValue()!=0)
                throw new IllegalStateException("A carga inicial exige tabelas operacionais vazias.");
        }
        em.persist(new ImportJobEntity(hash));
        control.activeJob=hash; control.ready=false;
        return hash;
    }

    @Transactional(readOnly=true)
    public ImportJobEntity.View status(String id) {
        var job=em.find(ImportJobEntity.class,id);
        if (job==null) throw new NoSuchElementException("Carga não encontrada.");
        long done=em.createQuery("SELECT COUNT(b) FROM ImportBatchEntity b WHERE b.jobId=:id AND b.done=true",Long.class).setParameter("id",id).getSingleResult();
        long issues=((Number)em.createNativeQuery("SELECT COUNT(*) FROM import_issues WHERE job_id=:id").setParameter("id",id).getSingleResult()).longValue();
        return new ImportJobEntity.View(id,job.status,job.totalBatches,done,issues,job.duplicateHeaders,job.error);
    }

    @Transactional(readOnly=true)
    public boolean ready() { return em.find(ImportControlEntity.class,1).ready; }

    @Transactional
    public String claimPreparation() {
        var list=em.createQuery("SELECT j FROM ImportJobEntity j WHERE j.status='PREPARING' AND (j.leaseUntil IS NULL OR j.leaseUntil<:now) ORDER BY j.createdAt",ImportJobEntity.class)
                .setParameter("now",OffsetDateTime.now()).setMaxResults(1).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        if(list.isEmpty()) return null;
        var job=list.get(0); job.leaseUntil=OffsetDateTime.now().plusMinutes(5); return job.id;
    }

    @Transactional
    public void addBatch(String jobId,String source,int index,BatchPayload payload) {
        var job=em.find(ImportJobEntity.class,jobId,LockModeType.PESSIMISTIC_WRITE);
        if (!job.status.equals("PREPARING")) return;
        String id=hash(jobId+":"+source+":"+index);
        if(em.find(ImportBatchEntity.class,id)==null) {
            var batch=new ImportBatchEntity();batch.id=id;batch.jobId=jobId;batch.source=source;batch.batchNumber=index;
            try { batch.payload=json.writeValueAsString(payload); } catch(Exception e) { throw new IllegalArgumentException(e); }
            batch.nextPublish=OffsetDateTime.now();em.persist(batch);
        }
        job.leaseUntil=OffsetDateTime.now().plusMinutes(5);
    }

    @Transactional
    public void preparationDone(String id,int total) {
        var job=em.find(ImportJobEntity.class,id,LockModeType.PESSIMISTIC_WRITE);
        if(job.status.equals("PREPARING")) {job.totalBatches=total;job.status="STAGING";job.leaseUntil=null;}
    }

    @Transactional
    public void preparationFailed(String id,String error) {
        var job=em.find(ImportJobEntity.class,id,LockModeType.PESSIMISTIC_WRITE);
        if(job.status.equals("PREPARING")) { job.status="FAILED";job.error=truncate(error); }
    }

    @Transactional
    public List<String> claimDispatch() {
        var batches=em.createQuery("SELECT b FROM ImportBatchEntity b, ImportJobEntity j WHERE b.jobId=j.id AND j.status IN ('PREPARING','STAGING') AND b.done=false AND b.attempts<8 AND b.nextPublish<=:now ORDER BY b.nextPublish,b.id",ImportBatchEntity.class)
                .setParameter("now",OffsetDateTime.now()).setMaxResults(16).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        for(var batch:batches) batch.nextPublish=OffsetDateTime.now().plusSeconds(60);
        return batches.stream().map(b->b.id).toList();
    }

    @Transactional
    public void failedAttempt(String id,String error) {
        var batch=em.find(ImportBatchEntity.class,id,LockModeType.PESSIMISTIC_WRITE);
        if(batch==null || batch.done) return;
        batch.attempts++;batch.error=truncate(error);batch.nextPublish=OffsetDateTime.now().plusSeconds(Math.min(300,5L*batch.attempts));
        if(batch.attempts>=8) {
            var job=em.find(ImportJobEntity.class,batch.jobId);
            job.status="FAILED";job.error="Lote esgotou tentativas: "+id;
        }
    }

    @Transactional
    public void retry(String id) {
        var control=em.find(ImportControlEntity.class,1,LockModeType.PESSIMISTIC_WRITE);
        var job=em.find(ImportJobEntity.class,id,LockModeType.PESSIMISTIC_WRITE);
        if(job==null) throw new NoSuchElementException("Carga não encontrada.");
        if(!id.equals(control.activeJob)||!job.status.equals("FAILED")) throw new IllegalStateException("Somente a carga ativa com falha técnica pode ser retomada.");
        em.createQuery("UPDATE ImportBatchEntity b SET b.attempts=0,b.error=null,b.nextPublish=:now WHERE b.jobId=:id AND b.done=false")
                .setParameter("now",OffsetDateTime.now()).setParameter("id",id).executeUpdate();
        job.status=job.totalBatches==0?"PREPARING":"STAGING";job.leaseUntil=null;job.error=null;
    }

    @Transactional(readOnly=true)
    public List<String> awaitingValidation() {
        return em.createQuery("SELECT j.id FROM ImportJobEntity j WHERE j.status='STAGING' AND NOT EXISTS (SELECT b.id FROM ImportBatchEntity b WHERE b.jobId=j.id AND b.done=false)",String.class).getResultList();
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> issues(String jobId,long after,int limit) {
        if(limit<1||limit>100||after<0) throw new IllegalArgumentException("Paginação inválida.");
        status(jobId);
        @SuppressWarnings("unchecked") List<Object[]> rows=em.createNativeQuery("SELECT i.id,i.source,i.line_number,i.record_key,i.reason,r.raw_payload FROM import_issues i LEFT JOIN import_rows r ON r.job_id=i.job_id AND r.source=i.source AND r.line_number=i.line_number WHERE i.job_id=:id AND i.id>:after ORDER BY i.id")
                .setParameter("id",jobId).setParameter("after",after).setMaxResults(limit).getResultList();
        return rows.stream().map(row->{ Map<String,Object> result=new LinkedHashMap<>();
            String[] keys={"id","arquivo","linha","chave","motivo","original"};
            for(int i=0;i<keys.length;i++) result.put(keys[i],row[i]);return result; }).toList();
    }
    static String truncate(String text) { return text==null?"Falha sem detalhe":text.substring(0,Math.min(1400,text.length())); }
}
