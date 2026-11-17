package br.com.estoque.imports.api;

import br.com.estoque.imports.infrastructure.*;
import java.io.IOException;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/imports")
@ConditionalOnProperty(name="imports.enabled",havingValue="true")
public class ImportController {
    private final ArchiveStorage archives;
    private final ImportStore store;
    public ImportController(ArchiveStorage archives,ImportStore store) {this.archives=archives;this.store=store;}

    @PostMapping(consumes="multipart/form-data") @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String,String> upload(@RequestPart("file") MultipartFile file) throws IOException {
        try(var stream=file.getInputStream()) {return Map.of("id",store.register(archives.store(stream)));}
    }
    @GetMapping("/{id}") public ImportJobEntity.View status(@PathVariable String id) {return store.status(id);}
    @GetMapping("/{id}/issues") public Object issues(@PathVariable String id,@RequestParam(defaultValue="0") long after,@RequestParam(defaultValue="50") int limit) {
        return store.issues(id,after,limit);
    }
    @PostMapping("/{id}/retry") @ResponseStatus(HttpStatus.ACCEPTED)
    public void retry(@PathVariable String id) {store.retry(id);}
}
