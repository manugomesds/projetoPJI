-- Lista as salas de chat ativas do usuário com informações do interlocutor e última mensagem
select 
    sc.id as sala_id,
    u_outro.id as outro_usuario_id,
    u_outro.nome as outro_usuario_nome,
    u_outro.foto_perfil as outro_usuario_foto,
    (
        select mc.texto_mensagem 
        from mensagens_chat mc 
        where mc.sala_id = sc.id 
        order by mc.data_envio desc 
        limit 1
    ) as ultima_mensagem,
    (
        select mc.data_envio 
        from mensagens_chat mc 
        where mc.sala_id = sc.id 
        order by mc.data_envio desc 
        limit 1
    ) as data_ultima_mensagem
from participantes_chat pc1
join salas_chat sc on pc1.sala_id = sc.id
join participantes_chat pc2 on sc.id = pc2.sala_id and pc2.usuario_id != pc1.usuario_id
join usuarios u_outro on pc2.usuario_id = u_outro.id
where pc1.usuario_id = :usuario_id
order by data_ultima_mensagem desc nulls last;