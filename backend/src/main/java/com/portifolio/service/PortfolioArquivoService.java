package com.portifolio.service;

import com.portifolio.dto.*;
import com.portifolio.exception.*;
import com.portifolio.model.PortfolioArquivo;
import com.portifolio.repository.PortfolioArquivoRepository;
import com.portifolio.validation.ArquivoPortfolioValidator;
import java.time.LocalDateTime;
import java.util.function.Supplier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;

@Service
public class PortfolioArquivoService {
    private final PortfolioArquivoRepository arquivos;
    private final PortfolioStorageService storage;
    private final PortfolioAccessService acesso;
    private final ArquivoPortfolioValidator validator;
    private final TransactionTemplate tx;
    public PortfolioArquivoService(PortfolioArquivoRepository arquivos, PortfolioStorageService storage,
            PortfolioAccessService acesso, ArquivoPortfolioValidator validator, PlatformTransactionManager manager) {
        this.arquivos = arquivos; this.storage = storage; this.acesso = acesso; this.validator = validator;
        this.tx = new TransactionTemplate(manager);
    }
    public PortfolioArquivoResponse enviar(org.springframework.web.multipart.MultipartFile file) {
        return operacao(() -> tx.execute(status -> {
            var dono = acesso.artistaAtual();
            var validado = validator.validar(file);
            String ref = storage.novaReferencia(dono.getUsuarioId(), validado.extensao());
            compensarRollback(() -> storage.limparUpload(dono.getUsuarioId(), ref));
            storage.gravar(dono.getUsuarioId(), ref, validado.bytes());
            var item = new PortfolioArquivo();
            item.setArtista(dono); item.setUrlArquivo(ref); item.setNomeOriginal(validado.nome());
            item.setTipoMime(validado.mime()); item.setTamanhoBytes(validado.bytes().length); item.setDataUpload(LocalDateTime.now());
            dono.setUltimaAtualizacao(item.getDataUpload());
            return dto(arquivos.saveAndFlush(item), false);
        }));
    }
    @Transactional(readOnly = true)
    public PortfolioPagina<PortfolioArquivoResponse> meus(int page, int size) {
        return listar(acesso.artistaAtual().getUsuarioId(), page, size, false);
    }
    @Transactional(readOnly = true)
    public PortfolioPagina<PortfolioArquivoResponse> publicos(Long artista, int page, int size) {
        acesso.exigirPublico(artista);
        return listar(artista, page, size, true);
    }
    private PortfolioPagina<PortfolioArquivoResponse> listar(Long artista, int page, int size, boolean publico) {
        return PortfolioPagina.of(arquivos.findByArtistaUsuarioId(artista, PortfolioAccessService.pagina(page, size, "dataUpload", "id"))
                .map(a -> dto(a, publico)));
    }
    @Transactional(readOnly = true)
    public Conteudo conteudo(Long id, boolean publico) {
        Long dono = publico ? null : acesso.artistaAtual().getUsuarioId();
        var item = arquivos.findById(id).orElseThrow(PortfolioAccessService::naoEncontrado);
        if (publico) acesso.exigirPublico(item.getArtista().getUsuarioId());
        else if (!item.getArtista().getUsuarioId().equals(dono)) throw PortfolioAccessService.naoEncontrado();
        var bytes = storage.ler(item.getArtista().getUsuarioId(), item.getUrlArquivo(), item.getTamanhoBytes());
        // Bytes ficam consistentes mesmo quando ocorre exclusão concorrente após a leitura.
        return new Conteudo(dto(item, publico), new ByteArrayResource(bytes));
    }
    public void excluir(Long id) {
        operacao(() -> tx.execute(status -> {
            var dono = acesso.artistaAtual();
            var item = arquivos.bloquearProprio(id, dono.getUsuarioId()).orElseThrow(PortfolioAccessService::naoEncontrado);
            String ref = item.getUrlArquivo();
            byte[] backup = storage.ler(dono.getUsuarioId(), ref, item.getTamanhoBytes());
            // DELETE físico antes do commit: se falhar, o banco reverte; se o commit falhar, restaura o arquivo.
            arquivos.delete(item); arquivos.flush();
            storage.remover(dono.getUsuarioId(), ref);
            compensarRollback(() -> storage.gravar(dono.getUsuarioId(), ref, backup));
            dono.setUltimaAtualizacao(LocalDateTime.now());
            return null;
        }));
    }
    private void compensarRollback(Runnable acao) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) acao.run();
            }
        });
    }
    private <T> T operacao(Supplier<T> acao) {
        try { return acao.get(); }
        catch (UnprocessableEntityException | UnauthorizedException | ForbiddenException | ResourceNotFoundException | PortfolioOperationException ex) { throw ex; }
        catch (RuntimeException ex) { throw new PortfolioOperationException(ex); }
    }
    private PortfolioArquivoResponse dto(PortfolioArquivo a, boolean publico) {
        String tipo = a.getTipoMime().startsWith("image/") ? "IMAGEM" : a.getTipoMime().equals("application/pdf") ? "PDF" : "AUDIO";
        return new PortfolioArquivoResponse(a.getId(), a.getNomeOriginal(), a.getTamanhoBytes(), a.getTipoMime(), a.getDataUpload(), tipo,
                "/api/portfolio/" + (publico ? "publico/" : "") + "arquivos/" + a.getId() + "/conteudo");
    }
    public record Conteudo(PortfolioArquivoResponse metadata, ByteArrayResource resource) {}
}
