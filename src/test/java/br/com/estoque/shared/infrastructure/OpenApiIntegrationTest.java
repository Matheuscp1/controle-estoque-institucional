package br.com.estoque.shared.infrastructure;

import br.com.estoque.items.api.ItemController;
import br.com.estoque.items.application.ItemService;
import br.com.estoque.orders.api.OrderController;
import br.com.estoque.orders.application.OrderService;
import br.com.estoque.organizations.api.OrganizationController;
import br.com.estoque.organizations.application.OrganizationService;
import br.com.estoque.imports.api.*;
import br.com.estoque.imports.infrastructure.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes=OpenApiIntegrationTest.Context.class,properties={"imports.enabled=true",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"})
@AutoConfigureMockMvc
@ActiveProfiles("openapi-contract")
class OpenApiIntegrationTest {
    @Configuration @EnableAutoConfiguration
    @Profile("openapi-contract")
    @Import({ItemController.class,OrderController.class,OrganizationController.class,ImportController.class,ImportReadinessFilter.class,OpenApiConfiguration.class})
    static class Context {}
    @MockBean ItemService items;
    @MockBean OrderService orders;
    @MockBean OrganizationService organizations;
    @MockBean ImportStore store;
    @MockBean ImportReview review;
    @MockBean ArchiveStorage archives;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test void everyInternalReferenceResolvesInGeneratedDocument() throws Exception {
        var spec=json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(spec.at("/components/schemas/ApiError").isMissingNode()).isFalse();
        checkReferences(spec,spec);
    }

    private void checkReferences(com.fasterxml.jackson.databind.JsonNode root,com.fasterxml.jackson.databind.JsonNode node) {
        if(node.has("$ref")) {
            String reference=node.path("$ref").asText();
            if(reference.startsWith("#/"))assertThat(root.at(reference.substring(1)).isMissingNode())
                    .as("Unresolved reference: %s",reference).isFalse();
        }
        node.elements().forEachRemaining(child->checkReferences(root,child));
    }

    @Test void documentationAndUiRemainAvailableBeforeInitialImport() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Swagger UI")));
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
        var spec=json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(spec.path("info").path("title").asText()).isEqualTo("Controle de estoque institucional");
        var paths=spec.path("paths");
        for(String path:java.util.List.of("/api/itens","/api/orgaos/{codigo}/pedidos")) {
            var page=paths.path(path).path("get").path("responses").path("200").path("content").elements().next().path("schema");
            if(page.has("$ref"))page=spec.at(page.path("$ref").asText().substring(1));
            assertThat(page.path("properties").has("registros")).as(path).isTrue();
            assertThat(page.path("properties").path("total").path("format").asText()).isEqualTo("int64");
        }
        assertThat(paths.path("/api/pedidos").path("post").path("description").asText()).contains("idExterno");
        assertThat(paths.path("/api/pedidos").path("post").path("responses").has("200")).isTrue();
        assertThat(paths.path("/api/pedidos").path("post").path("responses").has("503")).isTrue();
        assertThat(paths.path("/api/itens").path("get").path("responses").path("200").path("headers").has("X-Import-Ready")).isTrue();
        assertThat(paths.path("/api/itens").path("get").path("responses").path("200").path("headers").has("X-Cache")).isTrue();
        assertThat(paths.path("/api/imports").path("post").path("requestBody").path("content").has("multipart/form-data")).isTrue();
        assertThat(paths.path("/api/imports").path("post").path("responses").has("202")).isTrue();
        var uploadSchema=paths.path("/api/imports").path("post").path("requestBody").path("content").path("multipart/form-data").path("schema");
        if(uploadSchema.has("$ref"))uploadSchema=spec.at(uploadSchema.path("$ref").asText().substring(1));
        assertThat(uploadSchema.path("properties").path("file").path("format").asText()).isEqualTo("binary");
        assertThat(paths.path("/api/pedidos").path("post").path("requestBody").path("content").path("application/json").path("example").path("idExterno").asText()).isEqualTo("integracao-0001");
        assertThat(paths.path("/api/imports/{id}/issues").path("get").path("responses").path("200").path("content").path("application/json").path("schema").path("items").path("properties").has("motivo")).isTrue();
        assertThat(paths.path("/api/imports/{id}/rows/{source}/{line}/exclude-duplicate").path("post").path("responses").has("204")).isTrue();
        paths.fields().forEachRemaining(path->path.getValue().fields().forEachRemaining(operation->{
            assertThat(operation.getValue().path("summary").asText()).as(path.getKey()).isNotBlank();
        }));
    }
}
