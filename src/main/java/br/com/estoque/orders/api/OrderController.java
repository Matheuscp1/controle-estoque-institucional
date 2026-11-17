package br.com.estoque.orders.api;

import br.com.estoque.orders.application.*;
import br.com.estoque.orders.domain.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
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
    public OrderResponse create(@Valid @RequestBody Create request) {
        return OrderResponse.from(service.create(new NewOrder(request.idExterno(), request.codigoOrgao(),
                request.solicitante(), request.origem(), request.itens().stream()
                    .map(line -> new OrderLine(line.codigoItem(), line.quantidade())).toList())));
    }
    @GetMapping("/pedidos/{id}")
    public OrderResponse get(@PathVariable long id) { return OrderResponse.from(service.get(id)); }

    @PostMapping("/pedidos/{id}/cancelamento")
    public OrderResponse cancel(@PathVariable long id, @Valid @RequestBody Cancel request) {
        return OrderResponse.from(service.cancel(id, request.motivo()));
    }
    @GetMapping("/orgaos/{codigo}/pedidos")
    public List<OrderResponse> list(@PathVariable String codigo,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime inicio,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fim,
            @RequestParam(required=false) OrderStatus situacao, @RequestParam(required=false) Long antes,
            @RequestParam(defaultValue="50") int limite) {
        return service.list(new OrderFilter(codigo, inicio, fim, situacao, antes, limite)).stream().map(OrderResponse::from).toList();
    }
}
