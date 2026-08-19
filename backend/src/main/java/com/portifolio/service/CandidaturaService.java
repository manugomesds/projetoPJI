package com.portifolio.service;

import com.portifolio.dto.CandidaturaRequest;
import com.portifolio.dto.CandidaturaResponse;
import com.portifolio.dto.CandidaturaStatusRequest;
import com.portifolio.exception.ConflictException;
import com.portifolio.exception.ForbiddenException;
import com.portifolio.exception.ResourceNotFoundException;
import com.portifolio.exception.UnprocessableEntityException;
import com.portifolio.model.Candidatura;
import com.portifolio.model.PerfilArtista;
import com.portifolio.model.Usuario;
import com.portifolio.model.Vaga;
import com.portifolio.model.enums.StatusCandidatura;
import com.portifolio.model.enums.StatusVaga;
import com.portifolio.model.enums.TipoUsuario;
import com.portifolio.repository.CandidaturaRepository;
import com.portifolio.repository.PerfilArtistaRepository;
import com.portifolio.repository.VagaRepository;
import com.portifolio.security.AuthenticatedUserResolver;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CandidaturaService {

    private static final int TAMANHO_PADRAO = 20;
    private static final int TAMANHO_MAXIMO = 50;
    private static final Set<StatusCandidatura> STATUS_RETIRAVEIS =
            EnumSet.of(StatusCandidatura.PENDENTE, StatusCandidatura.EM_ANALISE);

    private final CandidaturaRepository candidaturaRepository;
    private final VagaRepository vagaRepository;
    private final PerfilArtistaRepository perfilArtistaRepository;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @Transactional(readOnly = true)
    public Page<CandidaturaResponse> listarDasMinhasVagas(Integer pagina, Integer tamanho) {
        Usuario usuario = exigirUsuarioAtual();
        exigirTipo(usuario, TipoUsuario.CONTRATANTE,
                "Somente contratantes podem consultar candidaturas recebidas.");
        return candidaturaRepository
                .findByVagaContratanteUsuarioId(usuario.getId(), paginacao(pagina, tamanho))
                .map(this::toResponse);
    }

    /**
     * A rota genérica é mantida por compatibilidade, mas nunca retorna dados
     * globais: cada ator vê somente candidaturas dentro do seu limite de acesso.
     */
    @Transactional(readOnly = true)
    public Page<CandidaturaResponse> listarDoUsuarioAtual(Integer pagina, Integer tamanho) {
        Usuario usuario = exigirUsuarioAtual();
        Pageable pageable = paginacao(pagina, tamanho);
        Page<Candidatura> candidaturas = switch (usuario.getTipoUsuario()) {
            case ARTISTA -> candidaturaRepository.findByArtistaUsuarioId(usuario.getId(), pageable);
            case CONTRATANTE -> candidaturaRepository
                    .findByVagaContratanteUsuarioId(usuario.getId(), pageable);
        };
        return candidaturas.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CandidaturaResponse buscarPorId(Long id) {
        Usuario usuario = exigirUsuarioAtual();
        Candidatura candidatura = buscarCandidatura(id);
        if (!podeAcessar(candidatura, usuario)) {
            throw new ResourceNotFoundException("Candidatura não encontrada.");
        }
        return toResponse(candidatura);
    }

    @Transactional
    public CandidaturaResponse criar(CandidaturaRequest request) {
        Usuario usuario = exigirUsuarioAtual();
        exigirTipo(usuario, TipoUsuario.ARTISTA, "Somente artistas podem se candidatar.");

        Vaga vaga = vagaRepository.findById(request.getVagaId())
                .orElseThrow(() -> new ResourceNotFoundException("Vaga não encontrada."));
        if (vaga.getStatus() != StatusVaga.ABERTA) {
            throw new UnprocessableEntityException(
                    "A vaga não aceita candidaturas porque está com status " + vaga.getStatus() + ".");
        }
        if (!Boolean.TRUE.equals(usuario.getPerfilCompleto())) {
            throw new UnprocessableEntityException("Complete seu perfil antes de se candidatar.");
        }

        PerfilArtista artista = perfilArtistaRepository.findById(usuario.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Perfil de artista não encontrado."));
        if (candidaturaRepository.existsByVagaIdAndArtistaUsuarioId(vaga.getId(), usuario.getId())) {
            throw new ConflictException("Já existe candidatura para esta vaga e artista.");
        }

        Candidatura candidatura = new Candidatura();
        candidatura.setVaga(vaga);
        candidatura.setArtista(artista);
        candidatura.setMensagemApresentacao(request.getMensagemApresentacao());
        candidatura.setLinkPortfolioCandidatura(request.getLinkPortfolioCandidatura());
        candidatura.setStatus(StatusCandidatura.PENDENTE);
        candidatura.setDataCandidatura(LocalDateTime.now());
        return toResponse(candidaturaRepository.save(candidatura));
    }

    @Transactional
    public CandidaturaResponse atualizar(Long id, CandidaturaStatusRequest request) {
        Usuario usuario = exigirUsuarioAtual();
        Candidatura candidatura = buscarCandidatura(id);
        StatusCandidatura destino = request.getStatus();

        if (usuario.getTipoUsuario() == TipoUsuario.ARTISTA) {
            if (!ehArtistaProprietario(candidatura, usuario)) {
                throw new ResourceNotFoundException("Candidatura não encontrada.");
            }
            if (destino != StatusCandidatura.RETIRADA) {
                throw new ForbiddenException("Artistas só podem retirar a própria candidatura.");
            }
            retirar(candidatura);
        } else {
            if (!ehContratanteProprietario(candidatura, usuario)) {
                throw new ForbiddenException(
                        "Somente o proprietário da vaga pode analisar esta candidatura.");
            }
            validarTransicaoDoContratante(candidatura.getStatus(), destino);
            candidatura.setStatus(destino);
        }

        return toResponse(candidaturaRepository.save(candidatura));
    }

    /** DELETE executa retirada lógica; o histórico não é removido. */
    @Transactional
    public void deletar(Long id) {
        Usuario usuario = exigirUsuarioAtual();
        exigirTipo(usuario, TipoUsuario.ARTISTA,
                "Somente o artista pode retirar uma candidatura.");
        Candidatura candidatura = buscarCandidatura(id);
        if (!ehArtistaProprietario(candidatura, usuario)) {
            throw new ResourceNotFoundException("Candidatura não encontrada.");
        }
        retirar(candidatura);
        candidaturaRepository.save(candidatura);
    }

    private Pageable paginacao(Integer pagina, Integer tamanho) {
        int paginaNormalizada = pagina == null || pagina < 0 ? 0 : pagina;
        int tamanhoNormalizado = tamanho == null || tamanho < 1
                ? TAMANHO_PADRAO
                : Math.min(tamanho, TAMANHO_MAXIMO);
        return PageRequest.of(
                paginaNormalizada,
                tamanhoNormalizado,
                Sort.by(Sort.Direction.DESC, "dataCandidatura").and(Sort.by("id")));
    }

    private void retirar(Candidatura candidatura) {
        if (!STATUS_RETIRAVEIS.contains(candidatura.getStatus())) {
            throw transicaoInvalida(candidatura.getStatus(), StatusCandidatura.RETIRADA);
        }
        candidatura.setStatus(StatusCandidatura.RETIRADA);
    }

    private void validarTransicaoDoContratante(StatusCandidatura atual, StatusCandidatura destino) {
        boolean permitida = switch (atual) {
            case PENDENTE -> destino == StatusCandidatura.EM_ANALISE
                    || destino == StatusCandidatura.APROVADO
                    || destino == StatusCandidatura.REJEITADO;
            case EM_ANALISE -> destino == StatusCandidatura.APROVADO
                    || destino == StatusCandidatura.REJEITADO;
            case APROVADO, REJEITADO, RETIRADA, CANCELADA_POR_VAGA -> false;
        };
        if (!permitida) {
            throw transicaoInvalida(atual, destino);
        }
    }

    private UnprocessableEntityException transicaoInvalida(
            StatusCandidatura atual, StatusCandidatura destino) {
        return new UnprocessableEntityException(
                "Transição de candidatura inválida: " + atual + " -> " + destino + ".");
    }

    private Candidatura buscarCandidatura(Long id) {
        return candidaturaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidatura não encontrada."));
    }

    private Usuario exigirUsuarioAtual() {
        return authenticatedUserResolver.usuarioAtual()
                .orElseThrow(() -> new ForbiddenException("Autenticação obrigatória."));
    }

    private void exigirTipo(Usuario usuario, TipoUsuario tipo, String mensagem) {
        if (usuario.getTipoUsuario() != tipo) {
            throw new ForbiddenException(mensagem);
        }
    }

    private boolean podeAcessar(Candidatura candidatura, Usuario usuario) {
        return usuario.getTipoUsuario() == TipoUsuario.ARTISTA
                ? ehArtistaProprietario(candidatura, usuario)
                : ehContratanteProprietario(candidatura, usuario);
    }

    private boolean ehArtistaProprietario(Candidatura candidatura, Usuario usuario) {
        return candidatura.getArtista().getUsuarioId().equals(usuario.getId());
    }

    private boolean ehContratanteProprietario(Candidatura candidatura, Usuario usuario) {
        return candidatura.getVaga().getContratante().getUsuarioId().equals(usuario.getId());
    }

    private CandidaturaResponse toResponse(Candidatura candidatura) {
        return CandidaturaResponse.builder()
                .id(candidatura.getId())
                .vagaId(candidatura.getVaga().getId())
                .artistaId(candidatura.getArtista().getUsuarioId())
                .mensagemApresentacao(candidatura.getMensagemApresentacao())
                .linkPortfolioCandidatura(candidatura.getLinkPortfolioCandidatura())
                .status(candidatura.getStatus())
                .dataCandidatura(candidatura.getDataCandidatura())
                .build();
    }
}
