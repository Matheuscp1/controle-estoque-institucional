package br.com.estoque.imports.infrastructure;

import javax.persistence.EntityManager;

/** Adaptador compartilhado pelos módulos de persistência; não altera suas regras de domínio. */
public final class ImportQuarantine {
    private ImportQuarantine() {}
    public static boolean contains(EntityManager em,String type,String key) {
        return ((Number)em.createNativeQuery("SELECT COUNT(*) FROM import_quarantine q JOIN import_control c ON c.active_job=q.job_id WHERE q.entity_type=:type AND q.record_key=:key")
                .setParameter("type",type).setParameter("key",key).getSingleResult()).longValue()>0;
    }
}
