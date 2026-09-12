-- Consulta o histórico de moderação de conteúdo gerado pelo motor de IA/NLP
select 
    mc.id as moderacao_id,
    mc.tipo_conteudo,
    mc.conteudo_id,
    u.nome as autor_nome,
    u.email as autor_email,
    mc.status_moderacao,
    mc.score_risco,
    mc.justificativa_acao,
    mc.data_analise,
    mc.data_criacao
from moderacao_conteudo mc
join usuarios u on mc.autor_id = u.id
order by mc.data_criacao desc;