package br.com.estoque.imports.api;

import br.com.estoque.imports.infrastructure.*;
import java.io.IOException;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/imports")
@io.swagger.v3.oas.annotations.tags.Tag(name="Importação",description="Upload, processamento assíncrono e revisão manual do histórico legado.")
@ConditionalOnProperty(name="imports.enabled",havingValue="true")
public class ImportController {
    private final ArchiveStorage archives;
    private final ImportStore store;
    private final ImportReview review;
    public ImportController(ArchiveStorage archives,ImportStore store,ImportReview review) {this.archives=archives;this.store=store;this.review=review;}

    @PostMapping(consumes="multipart/form-data") @ResponseStatus(HttpStatus.ACCEPTED)
    @io.swagger.v3.oas.annotations.Operation(summary="Enviar ZIP do histórico",description="Selecione o ZIP no campo file (até 128 MiB), contendo orgaos.csv, itens.csv, pedidos.csv e pedido_itens.csv. Retorna somente o id da carga; 202 não significa conclusão. Acompanhe GET /api/imports/{id}. Reenvio do mesmo arquivo é reconhecido pelo hash.")
    public Map<String,String> upload(@RequestPart("file") MultipartFile file) throws IOException {
        try(var stream=file.getInputStream()) {return Map.of("id",store.register(archives.store(stream)));}
    }
    @io.swagger.v3.oas.annotations.Operation(summary="Acompanhar importação",description="Estados: PREPARING, STAGING, COMPLETED, COMPLETED_WITH_ISSUES, REVIEW_PENDING, FAILED e NEEDS_REVIEW (legado). Contadores mostram lotes processados e registros publicados. COMPLETED_WITH_ISSUES conclui a carga e libera os registros consistentes; revise as ocorrências restantes.")
    @GetMapping("/{id}") public ImportJobEntity.View status(@PathVariable String id) {return store.status(id);}
    @io.swagger.v3.oas.annotations.Operation(summary="Consultar ocorrências",description="Lista ocorrências ativas; history=true inclui as anteriores. Use o último id em after e limit de 1 a 100. As ocorrências podem ser corrigidas pelas rotas de rows e revalidadas por reprocess.")
    @GetMapping("/{id}/issues") public Object issues(@PathVariable String id,@RequestParam(defaultValue="0") long after,@RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="false") boolean history) {
        return store.issues(id,after,limit,history);
    }
    @PostMapping("/{id}/retry") @ResponseStatus(HttpStatus.ACCEPTED)
    @io.swagger.v3.oas.annotations.Operation(summary="Retomar falha técnica",description="Retoma lotes de cargas FAILED ou cargas antigas NEEDS_REVIEW sem manifesto incremental. Para publicar correções manuais, use reprocess.")
    public void retry(@PathVariable String id) {store.retry(id);}

    @PutMapping("/{id}/rows/{source}/{line}")
    @io.swagger.v3.oas.annotations.Operation(summary="Corrigir linha do histórico",description="Envie todos os fields da linha, revision atual e justification. Não pode trocar o identificador. Uma revisão desatualizada retorna 409. A correção só é publicada após reprocess.")
    public Object correct(@PathVariable String id,@PathVariable String source,@PathVariable long line,@RequestBody ImportReview.Correction correction) {
        return review.correct(id,source,line,correction);
    }
    @PostMapping("/{id}/rows/{source}") @ResponseStatus(HttpStatus.CREATED)
    @io.swagger.v3.oas.annotations.Operation(summary="Adicionar registro faltante",description="Inclui uma linha necessária à revisão, com fields completos, revision=0 e justification. Não é um cadastro operacional genérico: precisa pertencer ao escopo das pendências.")
    public Object add(@PathVariable String id,@PathVariable String source,@RequestBody ImportReview.Correction correction) {return review.add(id,source,correction);}
    @GetMapping("/{id}/rows")
    @io.swagger.v3.oas.annotations.Operation(summary="Consultar linhas para revisão",description="Filtra por source e key. Retorna dados originais, campos efetivos e revisão. Use a última line retornada em after.")
    public Object rows(@PathVariable String id,@RequestParam String source,@RequestParam String key,@RequestParam(defaultValue="0") long after,@RequestParam(defaultValue="50") int limit) {
        return review.rows(id,source,key,after,limit);
    }
    @GetMapping("/{id}/corrections")
    @io.swagger.v3.oas.annotations.Operation(summary="Consultar histórico de correções",description="Histórico auditável das alterações e justificativas. Use o último id retornado em after.")
    public Object history(@PathVariable String id,@RequestParam(defaultValue="0") long after,@RequestParam(defaultValue="50") int limit) {return review.history(id,after,limit);}
    @GetMapping("/{id}/quarantine")
    @io.swagger.v3.oas.annotations.Operation(summary="Consultar registros em quarentena",description="Tipos: ITEM, ORDER ou ORGANIZATION. Use a última key retornada em after. Esses registros permanecem protegidos contra alterações operacionais até a revisão.")
    public Object quarantine(@PathVariable String id,@RequestParam String type,@RequestParam(defaultValue="") String after,@RequestParam(defaultValue="50") int limit) {return review.quarantine(id,type,after,limit);}
    @PostMapping("/{id}/reprocess") @ResponseStatus(HttpStatus.ACCEPTED)
    @io.swagger.v3.oas.annotations.Operation(summary="Revalidar e publicar correções",description="Agenda REVIEW_PENDING sem reenviar o ZIP. Acompanhe o status até COMPLETED ou COMPLETED_WITH_ISSUES. Repetir enquanto a revisão está agendada não duplica o processamento.")
    public void reprocess(@PathVariable String id) {review.reprocess(id);}
    @PostMapping("/{id}/rows/{source}/{line}/exclude-duplicate") @ResponseStatus(HttpStatus.NO_CONTENT)
    @io.swagger.v3.oas.annotations.Operation(summary="Desconsiderar duplicata idêntica",description="Exige revision atual e justification. Preserva outra cópia válida idêntica e o histórico da linha. Não permite excluir linhas diferentes para atender um pedido parcialmente.")
    public void excludeDuplicate(@PathVariable String id,@PathVariable String source,@PathVariable long line,@RequestBody ImportReview.Exclusion request) {
        review.excludeDuplicate(id,source,line,request);
    }
}
