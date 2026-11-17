package br.com.estoque;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Testa as migrações sem depender dos controllers ou serviços da aplicação. */
@JdbcTest(properties = "spring.datasource.url=jdbc:h2:mem:modelagem;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SchemaIntegrationTest {
    @Autowired JdbcTemplate db;

    @BeforeEach void preparar() {
        db.update("INSERT INTO orgaos(codigo,sigla,nome,uf) VALUES ('ORG','SEC','Secretaria','SP')");
        db.update("INSERT INTO itens(codigo,descricao,unidade,saldo_inicial,saldo) VALUES ('A','Papel','UN',10,10)");
        db.update("INSERT INTO pedidos(id,id_externo,codigo_orgao,solicitante,origem,assinatura,data_hora) "
                + "VALUES (100,'pedido-1','ORG','Maria','PORTAL','assinatura','2026-01-01 00:00:00+00')");
    }

    @Test void saldoNaoPodeSerNegativoMesmoPorSqlDireto() {
        assertThatThrownBy(() -> db.update("UPDATE itens SET saldo=-1 WHERE codigo='A'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(db.queryForObject("SELECT saldo FROM itens WHERE codigo='A'", BigDecimal.class))
                .isEqualByComparingTo("10");
    }

    @Test void codigoDoItemEIdentificadorExternoSaoUnicos() {
        assertThatThrownBy(() -> db.update("INSERT INTO itens(codigo,descricao,unidade,saldo_inicial,saldo) VALUES ('A','Outro','UN',0,0)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("INSERT INTO pedidos(id_externo,codigo_orgao,solicitante,origem,assinatura) "
                + "VALUES ('pedido-1','ORG','João','INTEGRACAO','outra')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void naoPermiteReferenciasInexistentesOuExcluirItemComHistorico() {
        assertThatThrownBy(() -> db.update("UPDATE pedidos SET codigo_orgao='INEXISTENTE' WHERE id=100"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("INSERT INTO pedido_itens VALUES (100,'INEXISTENTE',1)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        db.update("INSERT INTO pedido_itens VALUES (100,'A',1)");
        assertThatThrownBy(() -> db.update("DELETE FROM itens WHERE codigo='A'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void linhasExigemQuantidadePositivaENaoRepetemItem() {
        assertThatThrownBy(() -> db.update("INSERT INTO pedido_itens VALUES (100,'A',0)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("INSERT INTO pedido_itens VALUES (100,'A',-1)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        db.update("INSERT INTO pedido_itens VALUES (100,'A',0.125)");
        assertThatThrownBy(() -> db.update("INSERT INTO pedido_itens VALUES (100,'A',1)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void cancelamentoExigeDataValidaEMotivo() {
        assertThatThrownBy(() -> db.update("UPDATE pedidos SET situacao='CANCELADO' WHERE id=100"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("UPDATE pedidos SET situacao='CANCELADO', "
                + "data_cancelamento='2025-01-01 00:00:00+00', motivo_cancelamento='Erro' WHERE id=100"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("UPDATE pedidos SET situacao='CANCELADO', "
                + "data_cancelamento=CURRENT_TIMESTAMP, motivo_cancelamento=' ' WHERE id=100"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(db.update("UPDATE pedidos SET situacao='CANCELADO', "
                + "data_cancelamento=CURRENT_TIMESTAMP, motivo_cancelamento='Desistência' WHERE id=100"))
                .isEqualTo(1);
    }

    @Test void rejeitaOrigemSituacaoEVersaoInvalidas() {
        assertThatThrownBy(() -> db.update("UPDATE pedidos SET origem='OUTRA' WHERE id=100"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("UPDATE pedidos SET situacao='OUTRA' WHERE id=100"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("UPDATE itens SET versao=-1 WHERE codigo='A'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void rejeitaCamposObrigatoriosEmBranco() {
        assertThatThrownBy(() -> db.update("UPDATE itens SET descricao='   ' WHERE codigo='A'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("UPDATE pedidos SET id_externo='' WHERE id=100"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
