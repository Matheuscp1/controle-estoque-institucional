package br.com.estoque.shared.infrastructure;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.math.BigDecimal;
import java.util.*;
import org.springdoc.core.customizers.OpenApiCustomiser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentation only: does not change validation or business rules. */
@Configuration(proxyBeanMethods=false)
public class OpenApiConfiguration {
    @Bean OpenAPI estoqueOpenApi() {
        return new OpenAPI().info(new Info().title("Controle de estoque institucional").version("v1")
                .description("Catálogo e pedidos de órgãos públicos com carga histórica assíncrona. "
                        +"Durante a carga inicial, consultas mostram registros provisórios e alterações retornam 503. "
                        +"Após a validação, registros em quarentena continuam protegidos (409). "
                        +"Consultas podem usar cache Redis de curta duração; decisões de saldo sempre usam PostgreSQL. "
                        +"A API atual não possui autenticação. Os exemplos devem ser adaptados aos códigos e revisões da sua base."));
    }

    private Schema<?> errorSchema() {
        return new ObjectSchema()
                        .addProperty("mensagem",new StringSchema().example("Dados inválidos."))
                        .addProperty("erros",new ArraySchema().items(new StringSchema()))
                        .description("Erros de negócio usam mensagem; itens, órgãos e pedidos também retornam erros. "
                                +"Erros de transporte tratados pelo Spring podem ter o formato padrão do framework.");
    }

    @Bean OpenApiCustomiser apiContracts() {
        return api->{
            // Springdoc removes unused schemas before running customisers. Register the schema
            // together with its references so that it survives in the generated document.
            if(api.getComponents()==null)api.setComponents(new Components());
            api.getComponents().addSchemas("ApiError",errorSchema());
            api.getPaths().forEach((path,item)->item.readOperationsMap().forEach((method,operation)-> {
            boolean imports=path.startsWith("/api/imports");
            boolean read=method==PathItem.HttpMethod.GET;
            var responses=operation.getResponses();
            responses.addApiResponse("400",error("Corpo, campos ou parâmetros inválidos."));
            if(path.contains("{")||path.equals("/api/pedidos"))
                responses.addApiResponse("404",error("Registro não encontrado."));
            if(!read||path.equals("/api/orgaos/{codigo}/pedidos"))
                responses.addApiResponse("409",error("Duplicidade, saldo insuficiente, versão/revisão desatualizada, quarentena ou estado incompatível com a operação."));
            if(!imports&&!read)
                responses.addApiResponse("503",error("Carga inicial ainda em validação. Consultas continuam disponíveis."));
            responses.values().forEach(response-> {
                response.addHeaderObject("X-Request-ID",new Header().description("Identificador desta requisição para localizar os logs.").schema(new StringSchema()));
                if(!imports)response.addHeaderObject("X-Import-Ready",new Header().description("Quando a importação está habilitada: false indica dados provisórios; true indica que a validação inicial terminou.").schema(new BooleanSchema()));
            });
            if(!imports&&read&&responses.containsKey("200"))
                responses.get("200").addHeaderObject("X-Cache",new Header().description("Presente nas consultas elegíveis ao cache. HIT: cache; MISS: banco; BYPASS: carga inicial ou cache indisponível.")
                        .schema(new StringSchema()._enum(List.of("HIT","MISS","BYPASS"))));
            parameters(path,operation);
            Object example=requestExample(path,method);
            if(example!=null&&operation.getRequestBody()!=null&&operation.getRequestBody().getContent()!=null)
                operation.getRequestBody().getContent().values().forEach(media->media.setExample(example));
            if(imports) importResponses(path,method,operation);
            }));
        };
    }

    private ApiResponse error(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiError"))));
    }

    private void parameters(String path,Operation operation) {
        if(operation.getParameters()==null)return;
        for(var parameter:operation.getParameters()) {
            String name=parameter.getName();
            switch(name) {
                case "limite", "limit" -> {
                    parameter.setDescription("Tamanho da página: 1 a 100; padrão 50.");
                    parameter.getSchema().setMinimum(BigDecimal.ONE);parameter.getSchema().setMaximum(BigDecimal.valueOf(100));
                }
                case "depois" -> parameter.setDescription("Código do último registro da página anterior; omita na primeira página.");
                case "antes" -> parameter.setDescription("ID do último pedido da página anterior; paginação decrescente.");
                case "after" -> parameter.setDescription("Cursor exclusivo da página anterior: line em rows, key em quarantine, id nas demais listas. Omita na primeira página.");
                case "source" -> {
                    parameter.setDescription("Arquivo CSV de origem.");parameter.setExample("itens.csv");
                    parameter.setSchema(new StringSchema()._enum(List.of("orgaos.csv","itens.csv","pedidos.csv","pedido_itens.csv")));
                }
                case "type" -> parameter.setSchema(new StringSchema()._enum(List.of("ITEM","ORDER","ORGANIZATION")));
                case "key" -> parameter.setDescription("Identificador do registro no legado; em pedido_itens.csv, id externo do pedido.");
                case "line" -> parameter.setDescription("Número da linha no CSV, incluindo o cabeçalho; primeira linha de dados: 2.");
                case "versao" -> parameter.setDescription("Versão retornada pela consulta do item; uma versão antiga gera 409.");
                case "inicio" -> parameter.setDescription("Início inclusivo em ISO-8601 com fuso, por exemplo 2026-01-01T00:00:00-03:00.");
                case "fim" -> parameter.setDescription("Fim exclusivo em ISO-8601 com fuso; deve ser posterior a inicio.");
                case "id" -> parameter.setDescription(path.startsWith("/api/imports")?"Identificador SHA-256 retornado pelo upload.":"ID numérico interno do pedido.");
                case "history" -> parameter.setDescription("true inclui ocorrências anteriores; false mostra apenas as ativas.");
                default -> { }
            }
        }
    }

    private Object requestExample(String path,PathItem.HttpMethod method) {
        if(path.equals("/api/itens")&&method==PathItem.HttpMethod.POST)
            return Map.of("codigo","CANETA-001","descricao","Caneta azul","unidade","UN","saldo",100);
        if(path.equals("/api/itens/{codigo}")&&method==PathItem.HttpMethod.PUT)
            return Map.of("descricao","Caneta azul","unidade","UN","saldo",100,"ativo",true,"versao",0);
        if(path.equals("/api/orgaos")&&method==PathItem.HttpMethod.POST)
            return Map.of("codigo","ORG-001","sigla","SME","nome","Secretaria Municipal de Educação","uf","SP");
        if(path.equals("/api/pedidos")&&method==PathItem.HttpMethod.POST)
            return Map.of("idExterno","integracao-0001","codigoOrgao","ORG-001","solicitante","Maria Silva","origem","INTEGRACAO",
                    "itens",List.of(Map.of("codigoItem","CANETA-001","quantidade",5)));
        if(path.endsWith("/cancelamento"))return Map.of("motivo","Solicitação cancelada pelo órgão.");
        if(path.endsWith("/exclude-duplicate"))return Map.of("revision",0,"justification","Duplicação idêntica confirmada na origem.");
        if(path.startsWith("/api/imports")&&path.contains("/rows/{source}")&&(method==PathItem.HttpMethod.PUT||method==PathItem.HttpMethod.POST))
            return Map.of("revision",0,"justification","Saldo conferido com o histórico; exemplo para itens.csv.",
                    "fields",Map.of("codigo","CANETA-001","descricao","Caneta azul","unidade","UN","saldo_inicial","100",
                            "saldo_atual","100","ativo","S","data_cadastro","2026-01-01"));
        return null;
    }

    private void importResponses(String path,PathItem.HttpMethod method,Operation operation) {
        var responses=operation.getResponses();
        if(path.equals("/api/imports")&&method==PathItem.HttpMethod.POST) {
            responses.get("202").setContent(new Content().addMediaType("application/json",new MediaType()
                    .schema(new ObjectSchema().addProperty("id",new StringSchema().pattern("[a-f0-9]{64}")).required(List.of("id")))
                    .example(Map.of("id","a".repeat(64)))));
            responses.get("202").setDescription("Arquivo recebido ou já conhecido. Acompanhe o processamento pelo id.");
        } else if(method==PathItem.HttpMethod.GET&&!Set.of("/api/imports/{id}","/api/imports/active").contains(path)) {
            responses.get("200").setContent(new Content().addMediaType("application/json",new MediaType()
                    .schema(new ArraySchema().items(importListSchema(path)))));
        } else if(path.contains("/rows/{source}")&&!path.endsWith("/exclude-duplicate")) {
            String status=method==PathItem.HttpMethod.POST?"201":"200";
            responses.get(status).setContent(new Content().addMediaType("application/json",new MediaType()
                    .schema(new ObjectSchema().addProperty("line",new IntegerSchema().format("int64"))
                            .addProperty("revision",new IntegerSchema().format("int64")))
                    .example(Map.of("line",2,"revision",1))));
        }
    }

    private Schema<?> importListSchema(String path) {
        var schema=new ObjectSchema();
        if(path.endsWith("/quarantine"))return schema
                .addProperty("type",new StringSchema()._enum(List.of("ITEM","ORDER","ORGANIZATION")))
                .addProperty("key",new StringSchema().example("CANETA-001"));
        if(path.endsWith("/issues"))return schema
                .addProperty("id",new IntegerSchema().format("int64").example(1))
                .addProperty("arquivo",new StringSchema().example("itens.csv"))
                .addProperty("linha",new IntegerSchema().format("int64").example(2))
                .addProperty("chave",new StringSchema().example("CANETA-001"))
                .addProperty("motivo",new StringSchema().example("BALANCE_MISMATCH: saldo divergente"))
                .addProperty("original",jsonText()).addProperty("fields",jsonText())
                .addProperty("revision",new IntegerSchema().format("int64").nullable(true).example(0))
                .addProperty("active",new BooleanSchema().example(true));
        schema.addProperty("line",new IntegerSchema().format("int64").example(2));
        schema.addProperty("revision",new IntegerSchema().format("int64").example(0));
        if(path.endsWith("/rows"))return schema
                .addProperty("original",jsonText()).addProperty("fields",jsonText())
                .addProperty("error",new StringSchema().nullable(true))
                .addProperty("excluded",new BooleanSchema().example(false));
        return schema.addProperty("id",new IntegerSchema().format("int64").example(1))
                .addProperty("source",new StringSchema().example("itens.csv"))
                .addProperty("previous",jsonText()).addProperty("corrected",jsonText())
                .addProperty("justification",new StringSchema().example("Conferência com o histórico."))
                .addProperty("createdAt",new DateTimeSchema())
                .addProperty("operation",new StringSchema().description("Tipo de correção registrada, incluindo EXCLUDE_DUPLICATE."));
    }

    private Schema<?> jsonText() {
        return new StringSchema().nullable(true).description("JSON serializado como texto; converta para objeto antes de editar os campos.");
    }
}
