package com.portifolio.service;

import com.portifolio.dto.NotificacaoResponse;
import com.portifolio.event.NotificacaoEvento;
import com.portifolio.event.NotificacaoPersistida;
import com.portifolio.model.Notificacao;
import com.portifolio.model.Usuario;
import com.portifolio.repository.NotificacaoRepository;
import com.portifolio.repository.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificacaoPersistenceService {

    private final NotificacaoRepository notificacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacaoService notificacaoService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<NotificacaoPersistida> persistir(NotificacaoEvento evento) {
        return salvar(evento);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<NotificacaoPersistida> persistirNaTransacaoAtual(NotificacaoEvento evento) {
        return salvar(evento);
    }

    private List<NotificacaoPersistida> salvar(NotificacaoEvento evento) {
        Map<Long, Usuario> usuarios = usuarioRepository.findAllById(evento.destinatarioIds()).stream()
                .collect(Collectors.toMap(Usuario::getId, Function.identity()));
        LocalDateTime agora = LocalDateTime.now();
        List<Notificacao> novas = evento.destinatarioIds().stream()
                .map(usuarios::get)
                .filter(usuario -> usuario != null)
                .map(usuario -> novaNotificacao(usuario, evento, agora))
                .toList();
        return notificacaoRepository.saveAllAndFlush(novas).stream()
                .map(notificacao -> new NotificacaoPersistida(
                        notificacao.getUsuarioDestino().getId(),
                        notificacao.getUsuarioDestino().getEmail(),
                        notificacaoService.toResponse(notificacao)))
                .toList();
    }

    private Notificacao novaNotificacao(
            Usuario usuario, NotificacaoEvento evento, LocalDateTime agora) {
        Notificacao notificacao = new Notificacao();
        notificacao.setUsuarioDestino(usuario);
        notificacao.setTipo(evento.tipo());
        notificacao.setMensagem(evento.mensagem());
        notificacao.setLink(evento.link());
        notificacao.setLida(false);
        notificacao.setDataCriacao(agora);
        return notificacao;
    }
}
