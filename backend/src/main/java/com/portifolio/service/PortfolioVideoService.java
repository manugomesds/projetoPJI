package com.portifolio.service;

import com.portifolio.dto.PortfolioPagina;
import com.portifolio.exception.UnprocessableEntityException;
import com.portifolio.model.EmbedExterno;
import com.portifolio.repository.EmbedExternoRepository;
import com.portifolio.validation.VideoPortfolioValidator;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class PortfolioVideoService {
    private final EmbedExternoRepository videos;
    private final PortfolioAccessService acesso;
    private final VideoPortfolioValidator validator;
    public record VideoResponse(Long id, String urlOriginal, String embedUrl, String provedor) {}

    @Transactional
    public VideoResponse cadastrar(Map<String, String> request) {
        var dono = acesso.artistaAtual();
        if (request == null || request.size() != 1 || !request.containsKey("url"))
            throw new UnprocessableEntityException("Envie somente o link do vídeo, sem HTML ou identificadores de proprietário.");
        var video = validator.validar(request.get("url"));
        var item = new EmbedExterno(); item.setArtista(dono); item.setUrlOriginal(video.urlOriginal());
        item.setCodigoIframe("<iframe src=\"" + video.embedUrl() + "\" title=\"Vídeo do portfólio\" loading=\"lazy\" allowfullscreen></iframe>");
        dono.setUltimaAtualizacao(LocalDateTime.now());
        return dto(videos.saveAndFlush(item));
    }
    @Transactional(readOnly = true)
    public PortfolioPagina<VideoResponse> meus(int page, int size) {
        return listar(acesso.artistaAtual().getUsuarioId(), page, size);
    }
    @Transactional(readOnly = true)
    public PortfolioPagina<VideoResponse> publicos(Long artista, int page, int size) {
        acesso.exigirPublico(artista); return listar(artista, page, size);
    }
    private PortfolioPagina<VideoResponse> listar(Long artista, int page, int size) {
        return PortfolioPagina.of(videos.findByArtistaUsuarioId(artista, PortfolioAccessService.pagina(page, size, "id")).map(this::dto));
    }
    @Transactional
    public void excluir(Long id) {
        var dono = acesso.artistaAtual();
        videos.delete(videos.findByIdAndArtistaUsuarioId(id, dono.getUsuarioId()).orElseThrow(PortfolioAccessService::naoEncontrado));
        videos.flush(); dono.setUltimaAtualizacao(LocalDateTime.now());
    }
    private VideoResponse dto(EmbedExterno item) {
        // Nunca devolve nem confia no HTML armazenado, inclusive registros de versões anteriores.
        var video = validator.validar(item.getUrlOriginal());
        return new VideoResponse(item.getId(), video.urlOriginal(), video.embedUrl(), video.provedor());
    }
}
