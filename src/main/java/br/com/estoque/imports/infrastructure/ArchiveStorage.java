package br.com.estoque.imports.infrastructure;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;
import java.util.zip.ZipFile;
import br.com.estoque.imports.domain.LegacyRow;

public class ArchiveStorage {
    private final Path root;
    public ArchiveStorage(Path root) {this.root=root.toAbsolutePath().normalize();}
    public Path path(String id) {
        if(!id.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Identificador inválido.");
        return root.resolve(id+".zip");
    }
    public String store(InputStream input) throws IOException {
        Files.createDirectories(root);
        Path temporary=Files.createTempFile(root,"upload-",".tmp");
        try {
            MessageDigest digest;
            try {digest=MessageDigest.getInstance("SHA-256");} catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
            long size=0;
            try(var output=Files.newOutputStream(temporary)) {
                byte[] buffer=new byte[65536];int read;
                while((read=input.read(buffer))!=-1) {
                    size+=read;if(size>128L*1024*1024) throw new IllegalArgumentException("ZIP excede 128 MiB.");
                    digest.update(buffer,0,read);output.write(buffer,0,read);
                }
            }
            try(var zip=new ZipFile(temporary.toFile())) {
                var entries=zip.stream().toList();
                if(entries.size()!=4 || !entries.stream().map(e->e.getName()).collect(java.util.stream.Collectors.toSet()).equals(LegacyRow.HEADERS.keySet()))
                    throw new IllegalArgumentException("O ZIP deve conter somente os quatro CSVs na raiz.");
                long expanded=0;
                for(var entry:entries) {
                    if(entry.getSize()<0) throw new IllegalArgumentException("Tamanho de arquivo desconhecido.");
                    expanded+=entry.getSize();
                }
                if(expanded>512L*1024*1024) throw new IllegalArgumentException("Conteúdo do ZIP excede 512 MiB.");
            } catch(java.util.zip.ZipException e) {
                throw new IllegalArgumentException("Arquivo ZIP inválido ou corrompido.",e);
            }
            String id=HexFormat.of().formatHex(digest.digest());
            try {Files.move(temporary,path(id));} catch(FileAlreadyExistsException e) { /* Mesmo conteúdo já armazenado. */ }
            return id;
        } finally {Files.deleteIfExists(temporary);}
    }
}
