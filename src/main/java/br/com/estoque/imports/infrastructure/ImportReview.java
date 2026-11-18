package br.com.estoque.imports.infrastructure;

import br.com.estoque.imports.domain.LegacyRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import javax.persistence.*;
import org.springframework.transaction.annotation.Transactional;

public class ImportReview {
    private final EntityManager em;
    private final ObjectMapper json;
    public ImportReview(EntityManager em,ObjectMapper json) {this.em=em;this.json=json;}
    public record Correction(Map<String,String> fields,long revision,String justification) {}

    @Transactional
    public Map<String,Object> correct(String id,String source,long line,Correction correction) {
        lockEditable(id);check(source,correction);
        if(line<2)throw new IllegalArgumentException("Linha inválida.");
        var rows=em.createNativeQuery("SELECT record_key,revision,COALESCE(corrected_payload,raw_payload) FROM import_rows WHERE job_id=:id AND source=:source AND line_number=:line AND excluded=false FOR UPDATE")
                .setParameter("id",id).setParameter("source",source).setParameter("line",line).getResultList();
        if(rows.isEmpty())throw new NoSuchElementException("Linha não encontrada.");
        Object[] row=(Object[])rows.get(0);
        if(((Number)row[1]).longValue()!=correction.revision())throw new IllegalStateException("A linha foi alterada. Recarregue a revisão.");
        var parsed=LegacyRow.parse(source,correction.fields());
        String key=key(source,parsed);
        if(!Objects.equals(row[0],key))throw new IllegalArgumentException("A correção não pode trocar o identificador do registro.");
        requireScope(id,source,key,parsed);
        long revision=correction.revision()+1;
        em.createNativeQuery("UPDATE import_rows SET corrected_payload=:raw,payload=:payload,error=NULL,reference_key=:reference,revision=:revision WHERE job_id=:id AND source=:source AND line_number=:line")
                .setParameter("raw",encode(correction.fields())).setParameter("payload",encode(parsed))
                .setParameter("reference",reference(source,parsed)).setParameter("revision",revision)
                .setParameter("id",id).setParameter("source",source).setParameter("line",line).executeUpdate();
        audit(id,source,line,revision,(String)row[2],correction);
        return Map.of("line",line,"revision",revision);
    }

    @Transactional
    public Map<String,Object> add(String id,String source,Correction correction) {
        lockEditable(id);check(source,correction);
        if(correction.revision()!=0)throw new IllegalArgumentException("Registro novo exige revisão zero.");
        var parsed=LegacyRow.parse(source,correction.fields());String key=key(source,parsed);
        String type=source.equals("itens.csv")?"ITEM":source.equals("orgaos.csv")?"ORGANIZATION":"ORDER";
        long empty=((Number)em.createNativeQuery("SELECT COUNT(*) FROM import_issues WHERE job_id=:id AND source=:source AND active=true AND reason LIKE 'EMPTY_SOURCE:%'")
                .setParameter("id",id).setParameter("source",source).getSingleResult()).longValue();
        if(empty>0&&!scoped(id,type,key)) {
            String table=type.equals("ITEM")?"itens":type.equals("ORGANIZATION")?"orgaos":"pedidos";
            String column=type.equals("ORDER")?"id_externo":"codigo";
            long existing=((Number)em.createNativeQuery("SELECT COUNT(*) FROM "+table+" WHERE "+column+"=:key").setParameter("key",key).getSingleResult()).longValue();
            if(existing>0)throw new IllegalStateException("Identificador já pertence a registro liberado.");
            em.createNativeQuery("INSERT INTO import_quarantine(job_id,entity_type,record_key) VALUES (:id,:type,:key) ON CONFLICT DO NOTHING")
                    .setParameter("id",id).setParameter("type",type).setParameter("key",key).executeUpdate();
        }
        requireScope(id,source,key,parsed);
        if(!source.equals("pedido_itens.csv") && count("SELECT COUNT(*) FROM import_rows WHERE job_id=:id AND source=:source AND record_key=:key",id,source,key)>0)
            throw new IllegalStateException("O registro já existe. Corrija a linha existente.");
        long line=((Number)em.createNativeQuery("SELECT COALESCE(MAX(line_number),1)+1 FROM import_rows WHERE job_id=:id AND source=:source")
                .setParameter("id",id).setParameter("source",source).getSingleResult()).longValue();
        em.createNativeQuery("INSERT INTO import_rows(job_id,source,line_number,record_key,reference_key,raw_payload,corrected_payload,payload,revision) VALUES (:id,:source,:line,:key,:reference,'{}',:raw,:payload,1)")
                .setParameter("id",id).setParameter("source",source).setParameter("line",line).setParameter("key",key)
                .setParameter("reference",reference(source,parsed)).setParameter("raw",encode(correction.fields())).setParameter("payload",encode(parsed)).executeUpdate();
        audit(id,source,line,1,null,correction);
        return Map.of("line",line,"revision",1);
    }

    @Transactional
    public void reprocess(String id) {
        var control=em.find(ImportControlEntity.class,1,LockModeType.PESSIMISTIC_WRITE);
        var job=em.find(ImportJobEntity.class,id,LockModeType.PESSIMISTIC_WRITE);
        if(job==null)throw new NoSuchElementException("Carga não encontrada.");
        if(!id.equals(control.activeJob))throw new IllegalStateException("Carga não é a ativa.");
        if(job.status.equals("REVIEW_PENDING"))return;
        if(!Set.of("COMPLETED_WITH_ISSUES","NEEDS_REVIEW").contains(job.status))throw new IllegalStateException("Carga não possui revisão pendente.");
        job.status="REVIEW_PENDING";job.error=null;
    }

    public record Exclusion(long revision,String justification) {}
    @Transactional
    public void excludeDuplicate(String id,String source,long line,Exclusion request) {
        lockEditable(id);
        if(request==null||request.revision()<0||request.justification()==null||request.justification().isBlank()||request.justification().length()>1000)
            throw new IllegalArgumentException("Revisão e justificativa obrigatórias.");
        var rows=em.createNativeQuery("SELECT record_key,revision,COALESCE(corrected_payload,raw_payload),payload FROM import_rows WHERE job_id=:id AND source=:source AND line_number=:line AND excluded=false FOR UPDATE")
                .setParameter("id",id).setParameter("source",source).setParameter("line",line).getResultList();
        if(rows.isEmpty())throw new NoSuchElementException("Linha não encontrada.");
        Object[] row=(Object[])rows.get(0);
        if(((Number)row[1]).longValue()!=request.revision())throw new IllegalStateException("Revisão desatualizada.");
        String type=source.equals("itens.csv")?"ITEM":source.equals("orgaos.csv")?"ORGANIZATION":"ORDER";
        if(!scoped(id,type,(String)row[0])||row[3]==null)throw new IllegalStateException("Registro não é uma duplicata válida em revisão.");
        long copies=((Number)em.createNativeQuery("SELECT COUNT(*) FROM import_rows WHERE job_id=:id AND source=:source AND line_number<>:line AND excluded=false AND payload=:payload AND error IS NULL")
                .setParameter("id",id).setParameter("source",source).setParameter("line",line).setParameter("payload",row[3]).getSingleResult()).longValue();
        if(copies==0)throw new IllegalStateException("Só é permitido excluir uma cópia idêntica, preservando o registro original válido.");
        em.createNativeQuery("UPDATE import_rows SET excluded=true,revision=revision+1 WHERE job_id=:id AND source=:source AND line_number=:line")
                .setParameter("id",id).setParameter("source",source).setParameter("line",line).executeUpdate();
        em.createNativeQuery("INSERT INTO import_corrections(job_id,source,line_number,revision,previous_payload,corrected_payload,justification,operation) VALUES (:id,:source,:line,:revision,:previous,:previous,:justification,'EXCLUDE_DUPLICATE')")
                .setParameter("id",id).setParameter("source",source).setParameter("line",line).setParameter("revision",request.revision()+1)
                .setParameter("previous",row[2]).setParameter("justification",request.justification()).executeUpdate();
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> rows(String id,String source,String key,long after,int limit) {
        page(after,limit);
        if(!LegacyRow.HEADERS.containsKey(source)||key==null||key.isBlank())throw new IllegalArgumentException("Arquivo e chave obrigatórios.");
        return maps(em.createNativeQuery("SELECT line_number,revision,raw_payload,COALESCE(corrected_payload,raw_payload),error,excluded FROM import_rows WHERE job_id=:id AND source=:source AND record_key=:key AND line_number>:after ORDER BY line_number")
                .setParameter("id",id).setParameter("source",source).setParameter("key",key).setParameter("after",after).setMaxResults(limit).getResultList(),
                "line","revision","original","fields","error","excluded");
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> history(String id,long after,int limit) {
        page(after,limit);
        return maps(em.createNativeQuery("SELECT id,source,line_number,revision,previous_payload,corrected_payload,justification,created_at,operation FROM import_corrections WHERE job_id=:id AND id>:after ORDER BY id")
                .setParameter("id",id).setParameter("after",after).setMaxResults(limit).getResultList(),
                "id","source","line","revision","previous","corrected","justification","createdAt","operation");
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> quarantine(String id,String type,String after,int limit) {
        page(0,limit);
        if(!Set.of("ITEM","ORDER","ORGANIZATION").contains(type))throw new IllegalArgumentException("Tipo inválido.");
        return maps(em.createNativeQuery("SELECT entity_type,record_key FROM import_quarantine WHERE job_id=:id AND entity_type=:type AND record_key>:after ORDER BY record_key")
                .setParameter("id",id).setParameter("type",type).setParameter("after",after).setMaxResults(limit).getResultList(),"type","key");
    }

    private void lockEditable(String id) {
        var control=em.find(ImportControlEntity.class,1,LockModeType.PESSIMISTIC_WRITE);
        var job=em.find(ImportJobEntity.class,id,LockModeType.PESSIMISTIC_WRITE);
        if(job==null)throw new NoSuchElementException("Carga não encontrada.");
        if(!id.equals(control.activeJob)||!job.status.equals("COMPLETED_WITH_ISSUES"))
            throw new IllegalStateException("Aguarde a conclusão com pendências antes de editar.");
    }
    private void requireScope(String id,String source,String key,Map<String,String> fields) {
        String type=source.equals("itens.csv")?"ITEM":source.equals("orgaos.csv")?"ORGANIZATION":"ORDER";
        if(!scoped(id,type,key))throw new IllegalStateException("Registro já liberado ou fora das pendências. Alteração histórica bloqueada.");
        if(source.equals("pedido_itens.csv")&&!scoped(id,"ITEM",fields.get("codigo_item")))
            throw new IllegalStateException("O item de destino não está em revisão. É necessária reconciliação antes de alterar seu histórico.");
        if(source.equals("pedidos.csv")) {
            long released=((Number)em.createNativeQuery("SELECT COUNT(*) FROM import_rows r WHERE r.job_id=:id AND r.source='pedido_itens.csv' AND r.record_key=:key AND r.reference_key IS NOT NULL AND NOT EXISTS (SELECT 1 FROM import_quarantine q WHERE q.job_id=:id AND q.entity_type='ITEM' AND q.record_key=r.reference_key)")
                    .setParameter("id",id).setParameter("key",key).getSingleResult()).longValue();
            if(released>0)throw new IllegalStateException("Pedido envolve item já liberado; alteração histórica bloqueada.");
        }
    }
    private boolean scoped(String id,String type,String key) {
        return ((Number)em.createNativeQuery("SELECT COUNT(*) FROM import_quarantine WHERE job_id=:id AND entity_type=:type AND record_key=:key")
                .setParameter("id",id).setParameter("type",type).setParameter("key",key).getSingleResult()).longValue()>0;
    }
    private long count(String sql,String id,String source,String key) {return ((Number)em.createNativeQuery(sql).setParameter("id",id).setParameter("source",source).setParameter("key",key).getSingleResult()).longValue();}
    private String key(String source,Map<String,String> fields) {return fields.get(source.equals("pedido_itens.csv")?"id_externo_pedido":source.equals("pedidos.csv")?"id_externo":"codigo");}
    private String reference(String source,Map<String,String> fields) {return fields.getOrDefault(source.equals("pedido_itens.csv")?"codigo_item":"codigo_orgao",null);}
    private void check(String source,Correction c) {
        if(c==null||c.fields()==null||c.justification()==null||c.justification().isBlank()||c.justification().length()>1000||c.revision()<0)
            throw new IllegalArgumentException("Campos, revisão e justificativa (até 1000 caracteres) são obrigatórios.");
        if(!LegacyRow.HEADERS.containsKey(source))throw new IllegalArgumentException("Arquivo inválido.");
        if(c.fields().values().stream().anyMatch(Objects::isNull))throw new IllegalArgumentException("Use texto vazio para campos ausentes.");
    }
    private void audit(String id,String source,long line,long revision,String previous,Correction c) {
        em.createNativeQuery("INSERT INTO import_corrections(job_id,source,line_number,revision,previous_payload,corrected_payload,justification) VALUES (:id,:source,:line,:revision,:previous,:corrected,:justification)")
                .setParameter("id",id).setParameter("source",source).setParameter("line",line).setParameter("revision",revision)
                .setParameter("previous",previous).setParameter("corrected",encode(c.fields())).setParameter("justification",c.justification()).executeUpdate();
    }
    private String encode(Object value) {try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("Conteúdo inválido.",e);}}
    private void page(long after,int limit) {if(after<0||limit<1||limit>100)throw new IllegalArgumentException("Paginação inválida.");}
    private List<Map<String,Object>> maps(List<?> rows,String... keys) {
        return rows.stream().map(value->{Object[] row=(Object[])value;Map<String,Object> map=new LinkedHashMap<>();for(int i=0;i<keys.length;i++)map.put(keys[i],row[i]);return map;}).toList();
    }
}
