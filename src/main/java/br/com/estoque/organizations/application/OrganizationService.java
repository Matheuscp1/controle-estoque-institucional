package br.com.estoque.organizations.application;

import br.com.estoque.organizations.domain.*;
import java.util.List;

public class OrganizationService {
    private final OrganizationRepository repository;
    public OrganizationService(OrganizationRepository repository) {this.repository=repository;}
    public Organization cadastrar(String codigo,String sigla,String nome,String uf) {
        return repository.cadastrar(new Organization(codigo,sigla,nome,uf));
    }
    public Organization buscar(String codigo) {
        return repository.buscar(codigo).orElseThrow(()->new OrganizationErrors.NotFound(codigo));
    }
    public List<Organization> listar(String depois,int limite) {
        if(limite<1||limite>100) throw new IllegalArgumentException("Limite deve estar entre 1 e 100.");
        if(depois!=null&&depois.length()>64) throw new IllegalArgumentException("Cursor inválido.");
        return repository.listar(depois,limite);
    }
}
