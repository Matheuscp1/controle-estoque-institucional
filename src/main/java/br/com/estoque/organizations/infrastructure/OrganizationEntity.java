package br.com.estoque.organizations.infrastructure;

import br.com.estoque.organizations.domain.Organization;
import javax.persistence.*;
import org.springframework.data.domain.Persistable;

@Entity @Table(name="orgaos")
public class OrganizationEntity implements Persistable<String> {
    @Id @Column(length=64,nullable=false,updatable=false) private String codigo;
    @Column(length=30,nullable=false) private String sigla;
    @Column(length=255,nullable=false) private String nome;
    @org.hibernate.annotations.Type(type="br.com.estoque.organizations.infrastructure.FixedCharStringType")
    @Column(length=2,nullable=false,columnDefinition="char(2)") private String uf;
    @Transient private boolean newEntity=true;
    protected OrganizationEntity() {}

    static OrganizationEntity from(Organization organization) {
        var entity=new OrganizationEntity();entity.codigo=organization.codigo();entity.sigla=organization.sigla();
        entity.nome=organization.nome();entity.uf=organization.uf();return entity;
    }
    Organization toDomain() {return new Organization(codigo,sigla,nome,uf);}
    @Override public String getId() {return codigo;}
    @Override public boolean isNew() {return newEntity;}
    @PostLoad @PostPersist private void persisted() {newEntity=false;}
}
