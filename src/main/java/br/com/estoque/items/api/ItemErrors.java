package br.com.estoque.items.api;

import br.com.estoque.items.domain.ErroItem;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=ItemController.class)
public class ItemErrors {
    public record Resposta(String mensagem, List<String> erros) {}

    @ExceptionHandler(ErroItem.NaoEncontrado.class)
    ResponseEntity<Resposta> ausente(ErroItem.NaoEncontrado e) { return resposta(404,e.getMessage()); }

    @ExceptionHandler({ErroItem.Conflito.class, ErroItem.CodigoDuplicado.class})
    ResponseEntity<Resposta> conflito(RuntimeException e) { return resposta(409,e.getMessage()); }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Resposta> invalido(IllegalArgumentException e) { return resposta(400,e.getMessage()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Resposta> validacao(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(new Resposta("Dados inválidos.", e.getBindingResult().getFieldErrors()
                .stream().map(f -> f.getField() + ": " + f.getDefaultMessage()).toList()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    ResponseEntity<Resposta> formato(Exception e) { return resposta(400,"Corpo ou parâmetros inválidos."); }

    @ExceptionHandler(ConcurrencyFailureException.class)
    ResponseEntity<Resposta> concorrencia(Exception e) { return resposta(409,"Conflito concorrente. Recarregue e tente novamente."); }

    private ResponseEntity<Resposta> resposta(int status, String mensagem) {
        return ResponseEntity.status(status).body(new Resposta(mensagem,List.of()));
    }
}
