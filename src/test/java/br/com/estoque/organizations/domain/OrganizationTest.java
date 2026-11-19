package br.com.estoque.organizations.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OrganizationTest {
    @Test void normalizaUfEPreservaCodigoDoLegado() {
        var org=new Organization("ORG-0001","SEC","Secretaria"," sp ");
        assertThat(org.uf()).isEqualTo("SP");
        assertThat(org.codigo()).isEqualTo("ORG-0001");
    }
    @Test void rejeitaUfQueNaoExiste() {
        assertThatThrownBy(()->new Organization("O","SEC","Secretaria","XX"))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejeitaCamposAusentesOuMaioresQueAsColunas() {
        assertThatThrownBy(()->new Organization(" ","SEC","Secretaria","SP"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new Organization("O",null,"Secretaria","SP"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new Organization("O","SEC","x".repeat(256),"SP"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
