package com.portifolio.controller;

import com.portifolio.dto.*;
import com.portifolio.service.*;
import com.portifolio.validation.ArquivoPortfolioValidator;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/portfolio") @RequiredArgsConstructor
public class PortfolioController {
    private final PortfolioArquivoService arquivos;
    private final PortfolioVideoService videos;
    @PostMapping(value = "/arquivos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PortfolioArquivoResponse enviar(@RequestPart(value = "arquivo", required = false) MultipartFile arquivo) {
        return arquivos.enviar(arquivo);
    }
    @GetMapping("/me/arquivos")
    public PortfolioPagina<PortfolioArquivoResponse> meus(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return arquivos.meus(page, size);
    }
    @GetMapping("/publico/artistas/{artista}/arquivos")
    public PortfolioPagina<PortfolioArquivoResponse> publicos(@PathVariable Long artista, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return arquivos.publicos(artista, page, size);
    }
    @GetMapping("/arquivos/{id}/conteudo")
    public ResponseEntity<Resource> privado(@PathVariable Long id) { return conteudo(arquivos.conteudo(id, false)); }
    @GetMapping("/publico/arquivos/{id}/conteudo")
    public ResponseEntity<Resource> publico(@PathVariable Long id) { return conteudo(arquivos.conteudo(id, true)); }
    @DeleteMapping("/arquivos/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) { arquivos.excluir(id); }
    @PostMapping("/videos") @ResponseStatus(HttpStatus.CREATED)
    public PortfolioVideoService.VideoResponse video(@RequestBody Map<String, String> request) { return videos.cadastrar(request); }
    @GetMapping("/me/videos")
    public PortfolioPagina<PortfolioVideoService.VideoResponse> meusVideos(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return videos.meus(page, size);
    }
    @GetMapping("/publico/artistas/{artista}/videos")
    public PortfolioPagina<PortfolioVideoService.VideoResponse> videosPublicos(@PathVariable Long artista, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return videos.publicos(artista, page, size);
    }
    @DeleteMapping("/videos/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirVideo(@PathVariable Long id) { videos.excluir(id); }
    private ResponseEntity<Resource> conteudo(PortfolioArquivoService.Conteudo item) {
        var m = item.metadata();
        var disposition = m.tipo().equals("PDF") ? ContentDisposition.attachment() : ContentDisposition.inline();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(m.tipoMime())).contentLength(m.tamanhoBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.filename(ArquivoPortfolioValidator.nomeSeguro(m.nomeOriginal()), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .cacheControl(CacheControl.noStore())
                .body(item.resource());
    }
}
