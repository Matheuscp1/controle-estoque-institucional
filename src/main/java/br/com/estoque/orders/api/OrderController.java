package br.com.estoque.orders.api;

import br.com.estoque.orders.application.*;
import br.com.estoque.orders.domain.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import br.com.estoque.shared.application.PageResult;
import javax.validation.Valid;
import javax.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name="Pedidos",description="Atendimento integral, idempotência e cancelamento com devolução de saldo.")
@RequestMapping("/api")
public class OrderController {
    private final OrderService service;
    public OrderController(OrderService service) { this.service = service; }

    public record Line(@NotBlank @Size(max=64) String codigoItem,
                       @NotNull @DecimalMin(value="0", inclusive=false) @Digits(integer=16, fraction=3) BigDecimal quantidade) {}
    public record Create(@NotBlank @Size(max=128) String idExterno, @NotBlank @Size(max=64) String codigoOrgao,
                         @NotBlank @Size(max=255) String solicitante, @NotNull OrderSource origem,
                         @NotEmpty List<@NotNull @Valid Line> itens) {}
    public record Cancel(@NotBlank @Size(max=500) String motivo) {}

    @PostMapping("/pedidos")
    @io.swagger.v3.oas.annotations.Operation(summary="Registrar pedido",description="Todos os itens são atendidos na mesma transação ou nenhum é atendido. idExterno é único: repetir o mesmo conteúdo retorna o pedido existente sem novo débito; reutilizar o identificador com conteúdo diferente retorna 409. A resposta é 200, inclusive na primeira criação.")
    public OrderResponse create(@Valid @RequestBody Create request) {
        return OrderResponse.from(service.create(new NewOrder(request.idExterno(), request.codigoOrgao(),
                request.solicitante(), request.origem(), request.itens().stream()
                    .map(line -> new OrderLine(line.codigoItem(), line.quantidade())).toList())));
    }
    @GetMapping("/pedidos/{id}")
    @io.swagger.v3.oas.annotations.Operation(summary="Consultar pedido",description="Busca pelo ID numérico interno, diferente de idExterno.")
    public OrderResponse get(@PathVariable long id) { return OrderResponse.from(service.get(id)); }

    @PostMapping("/pedidos/{id}/cancelamento")
    @io.swagger.v3.oas.annotations.Operation(summary="Cancelar pedido",description="Exige motivo e devolve todas as quantidades na mesma transação. Um segundo cancelamento retorna 409 sem devolver saldo novamente.")
    public OrderResponse cancel(@PathVariable long id, @Valid @RequestBody Cancel request) {
        return OrderResponse.from(service.cancel(id, request.motivo()));
    }
    @GetMapping("/orgaos/{codigo}/pedidos")
    @io.swagger.v3.oas.annotations.Operation(summary="Pesquisar pedidos de um órgão",description="Retorna registros da página e total de pedidos do órgão que correspondem aos filtros, independentemente do cursor e limite. Ordenados por ID decrescente; use o último id em antes. Limite de 1 a 100, padrão 50. Datas ISO-8601 com fuso; inicio inclusivo e fim exclusivo. Filtros de período e situação são combináveis.")
    public PageResult<OrderResponse> list(@PathVariable String codigo,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime inicio,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fim,
            @RequestParam(required=false) OrderStatus situacao, @RequestParam(required=false) Long antes,
            @RequestParam(defaultValue="50") int limite) {
        return service.list(new OrderFilter(codigo, inicio, fim, situacao, antes, limite)).map(OrderResponse::from);
    }
}
