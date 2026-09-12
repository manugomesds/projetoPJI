-- Resumo analítico das vagas publicadas pelo contratante e volume de candidaturas
select 
    v.id as vaga_id,
    v.titulo as titulo_vaga,
    v.status as status_vaga,
    v.data_publicacao,
    count(c.id) as total_candidaturas,
    count(case when c.status = 'aprovado' then 1 end) as total_aprovados
from vagas v
left join candidaturas c on v.id = c.vaga_id
where v.contratante_id = :contratante_id
group by v.id, v.titulo, v.status, v.data_publicacao
order by v.data_publicacao desc;