-- Busca notificações pendentes de leitura para o usuário logado
select 
    id,
    tipo_notificacao,
    mensagem_alerta,
    link_contexto,
    data_criacao
from notificacoes
where usuario_destino_id = :usuario_id
  and lida = false
order by data_criacao desc;