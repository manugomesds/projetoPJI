-- Lista os compromissos públicos futuros na agenda de um artista específico
select 
    id,
    titulo_compromisso,
    descricao_compromisso,
    tipo_compromisso,
    data_hora_inicio,
    data_hora_fim,
    localizacao_logistica,
    cache_valor
from agenda_artista
where artista_id = :artista_id
  and exibir_publico = true
  and data_hora_inicio >= current_timestamp
order by data_hora_inicio asc;