package br.com.estoque.items.api;

import br.com.estoque.items.application.*;
import java.math.BigDecimal;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
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
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse cadastrar(@Valid @RequestBody Cadastro request) {
        return ItemResponse.de(service.cadastrar(request.codigo(),request.descricao(),request.unidade(),request.saldo()));
    }

    @GetMapping("/{codigo}")
    public ItemResponse buscar(@PathVariable String codigo) { return ItemResponse.de(service.buscar(codigo)); }

    @PutMapping("/{codigo}")
    public ItemResponse editar(@PathVariable String codigo, @Valid @RequestBody Edicao request) {
        return ItemResponse.de(service.editar(codigo,request.descricao(),request.unidade(),request.saldo(),request.ativo(),request.versao()));
    }

    @DeleteMapping("/{codigo}")
    public ItemResponse inativar(@PathVariable String codigo, @RequestParam long versao) {
        return ItemResponse.de(service.inativar(codigo,versao));
    }

    @GetMapping
    public List<ItemResponse> listar(@RequestParam(required=false) String descricao, @RequestParam(required=false) String unidade,
                                     @RequestParam(required=false) BigDecimal saldoMin, @RequestParam(required=false) BigDecimal saldoMax,
                                     @RequestParam(required=false) String depois, @RequestParam(defaultValue="50") int limite) {
        return service.listar(new FiltroItens(descricao,unidade,saldoMin,saldoMax,depois,limite)).stream()
                .map(ItemResponse::de).toList();
    }
}
