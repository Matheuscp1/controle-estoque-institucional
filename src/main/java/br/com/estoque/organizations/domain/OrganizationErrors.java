package br.com.estoque.organizations.domain;

public final class OrganizationErrors {
    private OrganizationErrors() {}
    public static class NotFound extends RuntimeException {
        public NotFound(String code) {super("Órgão não encontrado: "+code);}
    }
    public static class DuplicateCode extends RuntimeException {
        public DuplicateCode(String code) {super("Código de órgão já cadastrado: "+code);}
    }
}
