package com.portifolio.service;

import com.portifolio.exception.PortfolioOperationException;
import com.portifolio.validation.ArquivoPortfolioValidator;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PortfolioStorageService {
    private final Path root;
    public PortfolioStorageService(@Value("${app.portfolio.storage-root:./storage/portfolio}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        for (Path parte : this.root) {
            if (Set.of("frontend", "target", "build", "static").contains(parte.toString().toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("Storage deve ficar fora de diretórios de código, build e conteúdo estático.");
        }
    }

    public String novaReferencia(long artista, String ext) {
        if (artista <= 0 || !Set.of("jpg", "jpeg", "png", "pdf", "mp3").contains(ext)) throw falha();
        return artista + "/" + UUID.randomUUID() + "." + ext;
    }

    public void gravar(long artista, String ref, byte[] bytes) {
        Path destino = resolver(artista, ref), temporario = null;
        try {
            Files.createDirectories(destino.getParent());
            verificarLinks(destino);
            temporario = Files.createTempFile(destino.getParent(), ".upload-", ".tmp");
            try (var out = java.nio.channels.FileChannel.open(temporario, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                var buffer = java.nio.ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) out.write(buffer);
                out.force(true);
            }
            verificarLinks(destino);
            if (Files.exists(destino, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Destino já existe");
            try { Files.move(temporario, destino, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporario, destino); }
        } catch (IOException ex) { throw new PortfolioOperationException(ex); }
        finally {
            if (temporario != null) {
                try { Files.deleteIfExists(temporario); }
                catch (IOException ex) { throw new PortfolioOperationException(ex); }
            }
        }
    }

    public byte[] ler(long artista, String ref, int esperado) {
        Path path = resolver(artista, ref);
        if (esperado <= 0 || esperado > ArquivoPortfolioValidator.AUDIO_LIMIT) throw falha();
        try (var in = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = in.readNBytes(esperado + 1);
            if (bytes.length != esperado) throw new IOException("Tamanho divergente do metadata");
            return bytes;
        } catch (IOException ex) { throw new PortfolioOperationException(ex); }
    }

    public void remover(long artista, String ref) {
        Path path = resolver(artista, ref);
        try { Files.delete(path); }
        catch (IOException ex) { throw new PortfolioOperationException(ex); }
    }

    public void limparUpload(long artista, String ref) {
        Path path = resolver(artista, ref);
        try { Files.deleteIfExists(path); }
        catch (IOException ex) { throw new PortfolioOperationException(ex); }
    }

    private Path resolver(long artista, String ref) {
        // A gramática também impede referências absolutas, drives, NUL e nomes vindos do cliente.
        if (artista <= 0 || ref == null || !ref.matches("[1-9][0-9]*/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|jpeg|png|pdf|mp3)")
                || !ref.startsWith(artista + "/")) throw falha();
        Path path = root.resolve(ref).normalize();
        if (!path.startsWith(root) || path.equals(root)) throw falha();
        verificarLinks(path);
        return path;
    }

    private void verificarLinks(Path path) {
        // Verifica inclusive ancestrais do root. O diretório deve ser gravável apenas pelo processo/operador.
        for (Path p = path; p != null; p = p.getParent()) {
            if (Files.isSymbolicLink(p)) throw falha();
            if (Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    if (!p.toRealPath().equals(p.toAbsolutePath().normalize())) throw falha();
                } catch (IOException ex) { throw new PortfolioOperationException(ex); }
            }
        }
    }
    private PortfolioOperationException falha() { return new PortfolioOperationException(new IOException("Referência de storage inválida")); }
}
