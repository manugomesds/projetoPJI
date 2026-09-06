-- Lista os tokens de atualização válidos para gerenciamento de sessões ativas do usuário
select 
    id,
    token_hash,
    expiracao,
    ativo,
    data_criacao
from refresh_tokens
where usuario_id = :usuario_id
  and ativo = true
  and expiracao > current_timestamp
order by data_criacao desc;