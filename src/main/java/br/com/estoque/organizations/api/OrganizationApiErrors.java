package br.com.estoque.organizations.api;

import br.com.estoque.organizations.domain.OrganizationErrors;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=OrganizationController.class)
public class OrganizationApiErrors {
    public record ErrorResponse(String mensagem,List<String> erros) {}
    @ExceptionHandler(OrganizationErrors.NotFound.class)
    ResponseEntity<?> missing(OrganizationErrors.NotFound e) {return response(404,e.getMessage());}
    @ExceptionHandler(OrganizationErrors.DuplicateCode.class)
    ResponseEntity<?> duplicate(OrganizationErrors.DuplicateCode e) {return response(409,e.getMessage());}
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> invalid(IllegalArgumentException e) {return response(400,e.getMessage());}
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("Dados inválidos.",e.getBindingResult().getFieldErrors()
                .stream().map(f->f.getField()+": "+f.getDefaultMessage()).toList()));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> format(Exception e) {return response(400,"Corpo ou parâmetros inválidos.");}
    private ResponseEntity<?> response(int status,String message) {return ResponseEntity.status(status).body(new ErrorResponse(message,List.of()));}
}
