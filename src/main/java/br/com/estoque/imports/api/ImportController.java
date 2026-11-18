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
    private final ImportReview review;
    public ImportController(ArchiveStorage archives,ImportStore store,ImportReview review) {this.archives=archives;this.store=store;this.review=review;}

    @PostMapping(consumes="multipart/form-data") @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String,String> upload(@RequestPart("file") MultipartFile file) throws IOException {
        try(var stream=file.getInputStream()) {return Map.of("id",store.register(archives.store(stream)));}
    }
    @GetMapping("/{id}") public ImportJobEntity.View status(@PathVariable String id) {return store.status(id);}
    @GetMapping("/{id}/issues") public Object issues(@PathVariable String id,@RequestParam(defaultValue="0") long after,@RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="false") boolean history) {
        return store.issues(id,after,limit,history);
    }
    @PostMapping("/{id}/retry") @ResponseStatus(HttpStatus.ACCEPTED)
    public void retry(@PathVariable String id) {store.retry(id);}

    @PutMapping("/{id}/rows/{source}/{line}")
    public Object correct(@PathVariable String id,@PathVariable String source,@PathVariable long line,@RequestBody ImportReview.Correction correction) {
        return review.correct(id,source,line,correction);
    }
    @PostMapping("/{id}/rows/{source}") @ResponseStatus(HttpStatus.CREATED)
    public Object add(@PathVariable String id,@PathVariable String source,@RequestBody ImportReview.Correction correction) {return review.add(id,source,correction);}
    @GetMapping("/{id}/rows")
    public Object rows(@PathVariable String id,@RequestParam String source,@RequestParam String key,@RequestParam(defaultValue="0") long after,@RequestParam(defaultValue="50") int limit) {
        return review.rows(id,source,key,after,limit);
    }
    @GetMapping("/{id}/corrections")
    public Object history(@PathVariable String id,@RequestParam(defaultValue="0") long after,@RequestParam(defaultValue="50") int limit) {return review.history(id,after,limit);}
    @GetMapping("/{id}/quarantine")
    public Object quarantine(@PathVariable String id,@RequestParam String type,@RequestParam(defaultValue="") String after,@RequestParam(defaultValue="50") int limit) {return review.quarantine(id,type,after,limit);}
    @PostMapping("/{id}/reprocess") @ResponseStatus(HttpStatus.ACCEPTED)
    public void reprocess(@PathVariable String id) {review.reprocess(id);}
    @PostMapping("/{id}/rows/{source}/{line}/exclude-duplicate") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excludeDuplicate(@PathVariable String id,@PathVariable String source,@PathVariable long line,@RequestBody ImportReview.Exclusion request) {
        review.excludeDuplicate(id,source,line,request);
    }
}
