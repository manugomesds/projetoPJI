package com.portifolio.service;

import com.portifolio.dto.CandidaturaRequest;
import com.portifolio.dto.CandidaturaResponse;
import com.portifolio.exception.ConflictException;
import com.portifolio.exception.ResourceNotFoundException;
import com.portifolio.exception.ForbiddenException;
import com.portifolio.model.Candidatura;
import com.portifolio.model.PerfilArtista;
import com.portifolio.model.Vaga;
import com.portifolio.model.enums.StatusCandidatura;
import com.portifolio.repository.CandidaturaRepository;
import com.portifolio.repository.PerfilArtistaRepository;
import com.portifolio.repository.VagaRepository;
import com.portifolio.security.AuthenticatedUserResolver;
import com.portifolio.model.Usuario;
import com.portifolio.model.enums.TipoUsuario;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CandidaturaService {

    private final CandidaturaRepository candidaturaRepository;
    private final VagaRepository vagaRepository;
    private final PerfilArtistaRepository perfilArtistaRepository;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @Transactional(readOnly = true)
    public List<CandidaturaResponse> listarDasMinhasVagas() {
        Usuario usuario = authenticatedUserResolver.usuarioAtual()
                .orElseThrow(() -> new ForbiddenException("Autenticação obrigatória."));
        if (usuario.getTipoUsuario() != TipoUsuario.CONTRATANTE) {
            throw new ForbiddenException("Somente contratantes podem consultar candidaturas recebidas.");
        }
        return candidaturaRepository.findByVagaContratanteUsuarioId(usuario.getId()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CandidaturaResponse> listarTodos() {
        return candidaturaRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CandidaturaResponse buscarPorId(Long id) {
        Candidatura candidatura = candidaturaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidatura não encontrada."));
        return toResponse(candidatura);
    }

    @Transactional
    public CandidaturaResponse criar(CandidaturaRequest request) {
        if (candidaturaRepository.existsByVagaIdAndArtistaUsuarioId(request.getVagaId(), request.getArtistaId())) {
            throw new ConflictException("Já existe candidatura para esta vaga e artista.");
        }
        Vaga vaga = vagaRepository.findById(request.getVagaId())
                .orElseThrow(() -> new ResourceNotFoundException("Vaga não encontrada."));
        PerfilArtista artista = perfilArtistaRepository.findById(request.getArtistaId())
                .orElseThrow(() -> new ResourceNotFoundException("Artista não encontrado."));
        Candidatura candidatura = new Candidatura();
        candidatura.setVaga(vaga);
        candidatura.setArtista(artista);
        preencherCandidatura(candidatura, request);
        candidatura.setStatus(request.getStatus() == null ? StatusCandidatura.PENDENTE : request.getStatus());
        candidatura.setDataCandidatura(LocalDateTime.now());
        return toResponse(candidaturaRepository.save(candidatura));
    }

    @Transactional
    public CandidaturaResponse atualizar(Long id, CandidaturaRequest request) {
        Candidatura candidatura = candidaturaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidatura não encontrada."));
        if (!candidatura.getVaga().getId().equals(request.getVagaId())) {
            Vaga vaga = vagaRepository.findById(request.getVagaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Vaga não encontrada."));
            candidatura.setVaga(vaga);
        }
        if (!candidatura.getArtista().getUsuarioId().equals(request.getArtistaId())) {
            PerfilArtista artista = perfilArtistaRepository.findById(request.getArtistaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Artista não encontrado."));
            if (candidaturaRepository.existsByVagaIdAndArtistaUsuarioId(request.getVagaId(), request.getArtistaId())) {
                throw new ConflictException("Já existe candidatura para esta vaga e artista.");
            }
            candidatura.setArtista(artista);
        }
        preencherCandidatura(candidatura, request);
        if (request.getStatus() != null) {
            candidatura.setStatus(request.getStatus());
        }
        return toResponse(candidaturaRepository.save(candidatura));
    }

    @Transactional
    public void deletar(Long id) {
        Candidatura candidatura = candidaturaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidatura não encontrada."));
        candidaturaRepository.delete(candidatura);
    }

    private void preencherCandidatura(Candidatura candidatura, CandidaturaRequest request) {
        candidatura.setMensagemApresentacao(request.getMensagemApresentacao());
        candidatura.setLinkPortfolioCandidatura(request.getLinkPortfolioCandidatura());
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
