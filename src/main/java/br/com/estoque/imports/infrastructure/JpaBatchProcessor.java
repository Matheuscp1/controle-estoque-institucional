package br.com.estoque.imports.infrastructure;

import br.com.estoque.imports.application.BatchProcessor;
import br.com.estoque.imports.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import javax.persistence.*;
import org.springframework.transaction.annotation.Transactional;

public class JpaBatchProcessor implements BatchProcessor {
    private final EntityManager em;
    private final ObjectMapper json;
    public JpaBatchProcessor(EntityManager em,ObjectMapper json) {this.em=em;this.json=json;}

    @Override @Transactional
    public void process(String id) {
        var batch=em.find(ImportBatchEntity.class,id,LockModeType.PESSIMISTIC_WRITE);
        if(batch==null) throw new IllegalArgumentException("Lote desconhecido.");
        if(batch.done) return;
        var job=em.find(ImportJobEntity.class,batch.jobId);
        if(!java.util.Set.of("PREPARING","STAGING").contains(job.status)) return;
        String jobId=batch.jobId,source=batch.source;
        BatchPayload payload;
        try {payload=json.readValue(batch.payload,BatchPayload.class);} catch(Exception e){throw new IllegalArgumentException("Lote ilegível.",e);}
        int index=0;
        for(var row:payload.rows()) {
            var entity=new ImportRowEntity();entity.id=new ImportRowEntity.Key(jobId,source,row.line());
            try {
                entity.rawPayload=json.writeValueAsString(row.fields());
                Map<String,String> parsed;
                try {parsed=LegacyRow.parse(source,row.fields()); entity.payload=json.writeValueAsString(parsed);}
                catch(IllegalArgumentException e) {parsed=row.fields();entity.error=ImportStore.truncate(e.getMessage());}
                entity.recordKey=key(parsed.get(source.equals("pedido_itens.csv")?"id_externo_pedido":source.equals("pedidos.csv")?"id_externo":"codigo"));
                entity.referenceKey=key(parsed.get(source.equals("pedido_itens.csv")?"codigo_item":"codigo_orgao"));
            } catch(com.fasterxml.jackson.core.JsonProcessingException e) {throw new IllegalArgumentException(e);}
            em.persist(entity);
            if(++index%50==0) {em.flush();em.clear();}
        }
        em.flush();
        em.find(ImportBatchEntity.class,id).done=true;
    }
    private String key(String value) { return value==null?null:value.substring(0,Math.min(value.length(),128)); }
}
