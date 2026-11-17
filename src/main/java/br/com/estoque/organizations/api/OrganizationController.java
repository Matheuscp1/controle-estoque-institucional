package br.com.estoque.organizations.api;

import br.com.estoque.organizations.application.OrganizationService;
import br.com.estoque.organizations.domain.Organization;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/orgaos")
public class OrganizationController {
    private final OrganizationService service;
    public OrganizationController(OrganizationService service) {this.service=service;}
    public record Request(@NotBlank @Size(max=64) String codigo,@NotBlank @Size(max=30) String sigla,
                          @NotBlank @Size(max=255) String nome,@NotBlank String uf) {}
    public record Response(String codigo,String sigla,String nome,String uf) {
        static Response from(Organization organization) {return new Response(organization.codigo(),organization.sigla(),organization.nome(),organization.uf());}
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Response cadastrar(@Valid @RequestBody Request request) {
        return Response.from(service.cadastrar(request.codigo(),request.sigla(),request.nome(),request.uf()));
    }
    @GetMapping("/{codigo}") public Response buscar(@PathVariable String codigo) {return Response.from(service.buscar(codigo));}
    @GetMapping public List<Response> listar(@RequestParam(required=false) String depois,@RequestParam(defaultValue="50") int limite) {
        return service.listar(depois,limite).stream().map(Response::from).toList();
    }
}
