package br.com.estoque.imports.api;

import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes=ImportController.class)
@ConditionalOnProperty(name="imports.enabled",havingValue="true")
public class ImportErrors {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> invalid(IllegalArgumentException e) {return ResponseEntity.badRequest().body(Map.of("mensagem",e.getMessage()));}
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<?> conflict(IllegalStateException e) {return ResponseEntity.status(409).body(Map.of("mensagem",e.getMessage()));}
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<?> missing(NoSuchElementException e) {return ResponseEntity.status(404).body(Map.of("mensagem",e.getMessage()));}
}
