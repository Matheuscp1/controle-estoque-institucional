package br.com.estoque.orders.api;

import br.com.estoque.orders.domain.OrderErrors;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=OrderController.class)
public class OrderApiErrors {
    public record Response(String mensagem, List<String> erros) {}

    @ExceptionHandler(OrderErrors.NotFound.class)
    ResponseEntity<Response> missing(OrderErrors.NotFound e) { return response(404, e.getMessage()); }
    @ExceptionHandler(OrderErrors.Conflict.class)
    ResponseEntity<Response> conflict(OrderErrors.Conflict e) { return response(409, e.getMessage()); }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Response> invalid(IllegalArgumentException e) { return response(400, e.getMessage()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Response> validation(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(new Response("Dados inválidos.", e.getBindingResult().getFieldErrors()
                .stream().map(field -> field.getField() + ": " + field.getDefaultMessage()).toList()));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Response> format(Exception e) { return response(400, "Corpo ou parâmetros inválidos."); }
    @ExceptionHandler(ConcurrencyFailureException.class)
    ResponseEntity<Response> concurrency(Exception e) { return response(409, "Conflito concorrente. Tente novamente."); }
    private ResponseEntity<Response> response(int status, String message) {
        return ResponseEntity.status(status).body(new Response(message, List.of()));
    }
}
