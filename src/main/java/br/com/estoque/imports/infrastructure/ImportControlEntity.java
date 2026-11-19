package br.com.estoque.imports.infrastructure;

import javax.persistence.*;

@Entity @Table(name="import_control")
public class ImportControlEntity {
    @Id Integer id;
    @Column(nullable=false) boolean ready;
    @Column(name="active_job",length=64) String activeJob;
    protected ImportControlEntity() {}
}
