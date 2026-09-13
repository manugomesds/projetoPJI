package com.portifolio.repository;

import com.portifolio.model.ParticipanteChat;
import com.portifolio.model.ParticipanteChatId;
import com.portifolio.repository.projection.ChatSalaResumoProjection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParticipanteChatRepository
        extends JpaRepository<ParticipanteChat, ParticipanteChatId> {

    boolean existsBySala_IdAndUsuario_Id(Long salaId, Long usuarioId);

    @Query("select p.usuario.id from ParticipanteChat p where p.sala.id = :salaId order by p.usuario.id")
    List<Long> findUsuarioIdsBySalaId(@Param("salaId") Long salaId);

    @Query(value = """
            select pc.sala_id
            from participantes_chat pc
            group by pc.sala_id
            having count(*) = 2
               and sum(case when pc.usuario_id = :usuarioA or pc.usuario_id = :usuarioB
                            then 1 else 0 end) = 2
            order by pc.sala_id
            limit 1
            """, nativeQuery = true)
    Optional<Long> findSalaIdDaDupla(
            @Param("usuarioA") Long usuarioA,
            @Param("usuarioB") Long usuarioB);

    @Query(value = """
            select s.id as "salaId",
                   outro.usuario_id as "participanteId",
                   u.nome as "participanteNome",
                   u.foto_perfil_url as "participanteAvatar",
                   ultima.texto_mensagem as "ultimaMensagem",
                   ultima.data_envio as "ultimaMensagemData",
                   count(nao_lida.id) as "naoLidas"
            from participantes_chat eu
            join salas_chat s on s.id = eu.sala_id
            join participantes_chat outro
              on outro.sala_id = s.id and outro.usuario_id <> :usuarioId
            join usuarios u on u.id = outro.usuario_id
            left join lateral (
                select m.texto_mensagem, m.data_envio
                from mensagens_chat m
                where m.sala_id = s.id
                order by m.data_envio desc nulls last, m.id desc
                limit 1
            ) ultima on true
            left join mensagens_chat nao_lida
              on nao_lida.sala_id = s.id
             and nao_lida.remetente_id <> :usuarioId
             and coalesce(nao_lida.lida, false) = false
            where eu.usuario_id = :usuarioId
            group by s.id, s.data_criacao, outro.usuario_id, u.nome, u.foto_perfil_url,
                     ultima.texto_mensagem, ultima.data_envio
            order by coalesce(ultima.data_envio, s.data_criacao) desc nulls last, s.id desc
            """, countQuery = """
            select count(*)
            from participantes_chat eu
            where eu.usuario_id = :usuarioId
            """, nativeQuery = true)
    Page<ChatSalaResumoProjection> findSalasDoUsuario(
            @Param("usuarioId") Long usuarioId,
            Pageable pageable);
}
