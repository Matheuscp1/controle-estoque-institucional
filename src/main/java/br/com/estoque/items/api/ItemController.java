package br.com.estoque.items.api;

import br.com.estoque.items.application.*;
import java.math.BigDecimal;
import br.com.estoque.shared.application.PageResult;
import javax.validation.Valid;
import javax.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name="Itens",description="Catálogo, saldo disponível e controle de versão.")
@RequestMapping("/api/itens")
public class ItemController {
    private final ItemService service;

    public ItemController(ItemService service) { this.service = service; }

    public record Cadastro(@NotBlank @Size(max=64) String codigo, @NotBlank @Size(max=500) String descricao,
                           @NotBlank @Size(max=30) String unidade,
                           @NotNull @DecimalMin("0") @Digits(integer=16,fraction=3) BigDecimal saldo) {}

    public record Edicao(@NotBlank @Size(max=500) String descricao, @NotBlank @Size(max=30) String unidade,
                         @NotNull @DecimalMin("0") @Digits(integer=16,fraction=3) BigDecimal saldo,
                         @NotNull Boolean ativo, @NotNull @Min(0) Long versao) {}

    @PostMapping
    @io.swagger.v3.oas.annotations.Operation(summary="Cadastrar item",description="Código único e saldo não negativo; retorna 409 se o código já existir.")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse cadastrar(@Valid @RequestBody Cadastro request) {
        return ItemResponse.de(service.cadastrar(request.codigo(),request.descricao(),request.unidade(),request.saldo()));
    }

    @GetMapping("/{codigo}")
    @io.swagger.v3.oas.annotations.Operation(summary="Consultar item",description="Retorna o saldo e a versão atual. Durante a carga inicial os dados são provisórios.")
    public ItemResponse buscar(@PathVariable String codigo) { return ItemResponse.de(service.buscar(codigo)); }

    @PutMapping("/{codigo}")
    @io.swagger.v3.oas.annotations.Operation(summary="Editar item",description="Envie a versao retornada pela consulta. Se outro usuário alterou o item, retorna 409; recarregue os dados antes de tentar novamente.")
    public ItemResponse editar(@PathVariable String codigo, @Valid @RequestBody Edicao request) {
        return ItemResponse.de(service.editar(codigo,request.descricao(),request.unidade(),request.saldo(),request.ativo(),request.versao()));
    }

    @DeleteMapping("/{codigo}")
    @io.swagger.v3.oas.annotations.Operation(summary="Inativar item",description="Inativação lógica, preservando o histórico. Exige a versao atual; conflito concorrente retorna 409.")
    public ItemResponse inativar(@PathVariable String codigo, @RequestParam long versao) {
        return ItemResponse.de(service.inativar(codigo,versao));
    }

    @GetMapping
    @io.swagger.v3.oas.annotations.Operation(summary="Pesquisar itens",description="Retorna registros da página e total correspondente aos filtros, sem considerar cursor ou limite na contagem. Use o último codigo recebido em depois; limite de 1 a 100, padrão 50.")
    public PageResult<ItemResponse> listar(@RequestParam(required=false) String descricao, @RequestParam(required=false) String unidade,
                                     @RequestParam(required=false) BigDecimal saldoMin, @RequestParam(required=false) BigDecimal saldoMax,
                                     @RequestParam(required=false) String depois, @RequestParam(defaultValue="50") int limite) {
        return service.listar(new FiltroItens(descricao,unidade,saldoMin,saldoMax,depois,limite)).map(ItemResponse::de);
    }
}
