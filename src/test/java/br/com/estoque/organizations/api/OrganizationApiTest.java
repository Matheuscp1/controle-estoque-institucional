package br.com.estoque.organizations.api;

import br.com.estoque.organizations.application.OrganizationService;
import br.com.estoque.organizations.domain.OrganizationErrors;
import br.com.estoque.organizations.infrastructure.*;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes=OrganizationApiTest.Context.class,properties={
    "spring.datasource.url=jdbc:h2:mem:organizations;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"})
@AutoConfigureMockMvc
class OrganizationApiTest {
    @TestConfiguration @EnableAutoConfiguration
    @Import({OrganizationConfiguration.class,JpaOrganizationRepository.class,OrganizationController.class,OrganizationApiErrors.class})
    static class Context {}
    @Autowired MockMvc mvc;
    @Autowired OrganizationService service;
    @Autowired JdbcTemplate db;
    @BeforeEach void reset() {db.update("DELETE FROM orgaos");}

    @Test void cadastraEConsultaCodigoPreservandoZeros() throws Exception {
        mvc.perform(post("/api/orgaos").contentType("application/json").content("""
            {"codigo":"ORG-0001","sigla":"SEC","nome":"Secretaria","uf":" sp "}
            """)).andExpect(status().isCreated()).andExpect(jsonPath("$.uf").value("SP"));
        mvc.perform(get("/api/orgaos/ORG-0001")).andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("ORG-0001"));
    }
    @Test void codigoDuplicadoNaoAtualizaCadastroExistente() throws Exception {
        service.cadastrar("O","SEC","Original","SP");
        mvc.perform(post("/api/orgaos").contentType("application/json").content("""
            {"codigo":"O","sigla":"OUT","nome":"Outro","uf":"RJ"}
            """)).andExpect(status().isConflict());
        assertThat(service.buscar("O").nome()).isEqualTo("Original");
    }
    @Test void paginaPorCodigoSemRepetirRegistros() throws Exception {
        service.cadastrar("A","A","Orgao A","SP");service.cadastrar("B","B","Orgao B","RJ");
        mvc.perform(get("/api/orgaos?limite=1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].codigo").value("A"));
        mvc.perform(get("/api/orgaos?limite=1&depois=A")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("B"));
        mvc.perform(get("/api/orgaos?depois=B")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
    @Test void rejeitaUfInvalidaEDadosAusentes() throws Exception {
        mvc.perform(post("/api/orgaos").contentType("application/json").content("""
            {"codigo":"O","sigla":"SEC","nome":"Secretaria","uf":"XX"}
            """)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/orgaos").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/orgaos?limite=101")).andExpect(status().isBadRequest());
    }
    @Test void codigoAusenteRetorna404() throws Exception {
        mvc.perform(get("/api/orgaos/inexistente")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").exists());
    }
    @Test void cadastrosConcorrentesTemUmUnicoVencedor() throws Exception {
        var pool=Executors.newFixedThreadPool(2);var go=new CountDownLatch(1);
        Callable<Boolean> create=()->{go.await();try {service.cadastrar("O","SEC","Secretaria","SP");return true;}
            catch(OrganizationErrors.DuplicateCode e){return false;}};
        try {var a=pool.submit(create);var b=pool.submit(create);go.countDown();
            assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }finally{pool.shutdownNow();}
        assertThat(db.queryForObject("SELECT COUNT(*) FROM orgaos",Integer.class)).isEqualTo(1);
    }
}
