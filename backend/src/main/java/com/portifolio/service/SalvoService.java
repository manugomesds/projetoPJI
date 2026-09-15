package com.portifolio.service;

import com.portifolio.dto.*;
import com.portifolio.event.NotificacaoEvento;
import com.portifolio.exception.*;
import com.portifolio.model.*;
import com.portifolio.model.enums.*;
import com.portifolio.repository.*;
import com.portifolio.security.AuthenticatedUserResolver;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class SalvoService {
    private final ItemSalvoRepository salvos;
    private final SalvoAlvoRepository alvos;
    private final PerfilPublicoService perfis;
    private final VagaService vagas;
    private final AvatarService avatars;
    private final AuthenticatedUserResolver autenticado;
    private final ApplicationEventPublisher eventos;

    public record Salvamento(boolean criado, SalvoResponse.Estado estado) {}

    @Transactional
    public Salvamento salvar(SalvoRequest request) {
        Usuario usuario = usuario();
        TipoAlvoSalvo tipo = validar(request.tipoAlvo(),request.alvoId());
        Long alvoId = request.alvoId();
        if (salvos.existsByUsuarioIdAndTipoAlvoAndAlvoId(usuario.getId(),tipo,alvoId))
            return new Salvamento(false,estado(usuario.getId(),tipo,alvoId));
        Long dono;
        if (tipo == TipoAlvoSalvo.PERFIL_ARTISTA) {
            perfis.buscar(TipoUsuario.ARTISTA,alvoId); // RF10 é a autoridade sobre visibilidade.
            dono = alvoId;
        } else {
            dono = vagas.buscarPorId(alvoId).getContratanteId(); // RF05: inclui proteção de descoberta por ID.
        }
        boolean criado = salvos.inserirSeAusente(usuario.getId(),tipo.name(),alvoId) == 1;
        if (criado && !usuario.getId().equals(dono)) {
            eventos.publishEvent(new NotificacaoEvento(Set.of(dono),TipoNotificacao.SALVO,
                    tipo == TipoAlvoSalvo.PERFIL_ARTISTA ? "Seu perfil foi salvo." : "Sua vaga foi salva.",
                    href(tipo,alvoId)));
        }
        return new Salvamento(criado,estado(usuario.getId(),tipo,alvoId));
    }

    @Transactional
    public void remover(TipoAlvoSalvo tipo, Long alvoId) {
        Long usuarioId = usuario().getId();
        validar(tipo,alvoId);
        // Não exige que o alvo ainda exista: o usuário pode limpar seu próprio histórico.
        salvos.removerProprio(usuarioId,tipo,alvoId);
    }

    public SalvoResponse.Estado estado(TipoAlvoSalvo tipo, Long alvoId) {
        Long usuarioId = usuario().getId();
        validar(tipo,alvoId);
        return estado(usuarioId,tipo,alvoId);
    }

    public SalvoResponse.Pagina listar(TipoAlvoSalvo tipo, int page, int size) {
        Usuario usuario = usuario();
        if (page<0 || size<1) throw new IllegalArgumentException("page deve ser não negativo e size deve ser positivo.");
        if (tipo != null) validar(tipo,1L);
        Set<TipoAlvoSalvo> tipos = tipo == null ? Set.of(TipoAlvoSalvo.PERFIL_ARTISTA,TipoAlvoSalvo.VAGA) : Set.of(tipo);
        Page<ItemSalvo> resultado = salvos.findByUsuarioIdAndTipoAlvoIn(usuario.getId(),tipos,
                PageRequest.of(page,Math.min(size,50),Sort.by(Sort.Order.desc("dataSalvamento"),Sort.Order.desc("id"))));
        var perfisPublicos = alvos.perfis(ids(resultado,TipoAlvoSalvo.PERFIL_ARTISTA));
        var vagasPublicas = alvos.vagas(ids(resultado,TipoAlvoSalvo.VAGA),usuario.getId());
        var content = resultado.stream().map(item -> {
            boolean artista = item.getTipoAlvo()==TipoAlvoSalvo.PERFIL_ARTISTA;
            var alvo = (artista ? perfisPublicos : vagasPublicas).get(item.getAlvoId());
            boolean disponivel = alvo != null && alvo.disponivel();
            return new SalvoResponse(item.getTipoAlvo(),item.getAlvoId(),item.getDataSalvamento(),
                    alvo == null ? (artista ? "Perfil indisponível" : "Vaga indisponível") : alvo.nome(),
                    artista && alvo != null ? avatars.resolverUrl(alvo.id(),alvo.foto(),null) : null,
                    alvo == null ? null : alvo.localizacao(),alvo == null ? null : alvo.contratante(),
                    alvo == null ? null : alvo.status(),disponivel,disponivel ? href(item.getTipoAlvo(),item.getAlvoId()) : null,
                    alvo == null ? null : alvo.funcoes());
        }).toList();
        return new SalvoResponse.Pagina(content,resultado.getNumber(),resultado.getSize(),resultado.getTotalElements(),
                resultado.getTotalPages(),resultado.hasNext(),resultado.hasPrevious());
    }

    private Set<Long> ids(Page<ItemSalvo> page, TipoAlvoSalvo tipo) {
        return page.stream().filter(i -> i.getTipoAlvo()==tipo).map(ItemSalvo::getAlvoId).collect(Collectors.toSet());
    }

    private SalvoResponse.Estado estado(Long usuarioId, TipoAlvoSalvo tipo, Long alvoId) {
        return new SalvoResponse.Estado(salvos.existsByUsuarioIdAndTipoAlvoAndAlvoId(usuarioId,tipo,alvoId),
                tipo==TipoAlvoSalvo.PERFIL_ARTISTA && !alvos.perfis(Set.of(alvoId)).isEmpty()
                        ? salvos.countByTipoAlvoAndAlvoId(tipo,alvoId) : null);
    }

    private Usuario usuario() {
        Usuario usuario = autenticado.usuarioAtual().orElseThrow(() -> new UnauthorizedException("Autenticação necessária."));
        if (usuario.getStatusConta()!=StatusConta.ATIVA) throw new ForbiddenException("Ative sua conta para usar salvos.");
        return usuario;
    }

    private TipoAlvoSalvo validar(TipoAlvoSalvo tipo, Long id) {
        if (tipo==null || tipo==TipoAlvoSalvo.OBRA) throw new IllegalArgumentException("Tipo permitido: PERFIL_ARTISTA ou VAGA.");
        if (id==null || id<=0) throw new IllegalArgumentException("alvoId deve ser positivo.");
        return tipo;
    }

    private String href(TipoAlvoSalvo tipo, Long id) {
        return tipo==TipoAlvoSalvo.PERFIL_ARTISTA ? "/perfis/ARTISTA/"+id : "/vagas/"+id;
    }
}
