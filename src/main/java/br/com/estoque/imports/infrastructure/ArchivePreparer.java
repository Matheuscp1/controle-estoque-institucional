    package br.com.estoque.imports.infrastructure;

    import br.com.estoque.imports.domain.*;
    import java.io.*;
    import java.nio.charset.StandardCharsets;
    import java.util.*;
    import java.util.zip.ZipFile;
    import org.apache.commons.csv.CSVFormat;
    import org.springframework.scheduling.annotation.Scheduled;

    public class ArchivePreparer {
        private final ImportStore store;
        private final ArchiveStorage archives;
        public ArchivePreparer(ImportStore store,ArchiveStorage archives) {this.store=store;this.archives=archives;}

        @Scheduled(fixedDelayString="${imports.prepare-delay-ms:3000}",initialDelayString="${imports.initial-delay-ms:0}")
        public void prepareNext() {
            String id=store.claimPreparation();
            if(id==null) return;
            try(var zip=new ZipFile(archives.path(id).toFile())) {
                int total=0;
                for(String source:List.of("orgaos.csv","itens.csv","pedidos.csv","pedido_itens.csv")) {
                    try(var stream=zip.getInputStream(zip.getEntry(source));
                        var reader=new PushbackReader(new InputStreamReader(stream,StandardCharsets.UTF_8),1)) {
                        int first=reader.read();if(first!=-1&&first!='\ufeff') reader.unread(first);
                        var format=CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(false).build();
                        try(var csv=format.parse(reader)) {
                            if(!csv.getHeaderNames().equals(LegacyRow.HEADERS.get(source))) throw new IllegalArgumentException("Cabeçalho inválido: "+source);
                            List<BatchPayload.Row> rows=new ArrayList<>(500);int batch=0;
                            for(var record:csv) {
                                if(!record.isConsistent()) throw new IllegalArgumentException("CSV malformado: "+source+" registro "+record.getRecordNumber());
                                if(record.getRecordNumber()>4_000_000) throw new IllegalArgumentException("Arquivo excede limite de registros.");
                                rows.add(new BatchPayload.Row(record.getRecordNumber()+1,new TreeMap<>(record.toMap())));
                                if(rows.size()==500) {store.addBatch(id,source,batch++,new BatchPayload(rows));total++;rows=new ArrayList<>(500);}
                            }
                            if(!rows.isEmpty()) {store.addBatch(id,source,batch,new BatchPayload(rows));total++;}
                            store.sourcePrepared(id,source,batch+(rows.isEmpty()?0:1));
                        }
                    }
                }
                store.preparationDone(id,total);
            } catch(Exception e) {store.preparationFailed(id,e.getMessage());}
        }
    }
