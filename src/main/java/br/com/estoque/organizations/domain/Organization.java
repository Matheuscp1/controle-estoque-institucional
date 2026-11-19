package br.com.estoque.organizations.domain;

import java.util.Locale;
import java.util.Set;

public record Organization(String codigo,String sigla,String nome,String uf) {
    private static final Set<String> STATES=Set.of("AC","AL","AP","AM","BA","CE","DF","ES","GO","MA","MT","MS","MG","PA","PB","PR","PE","PI","RJ","RN","RS","RO","RR","SC","SP","SE","TO");
    public Organization {
        validate(codigo,"Código",64);
        validate(sigla,"Sigla",30);
        validate(nome,"Nome",255);
        if(uf==null) throw new IllegalArgumentException("UF obrigatória.");
        uf=uf.strip().toUpperCase(Locale.ROOT);
        if(!STATES.contains(uf)) throw new IllegalArgumentException("UF inválida.");
    }
    private static void validate(String value,String field,int max) {
        if(value==null||value.isBlank()||value.length()>max)
            throw new IllegalArgumentException(field+" é obrigatório e deve ter até "+max+" caracteres.");
    }
}
