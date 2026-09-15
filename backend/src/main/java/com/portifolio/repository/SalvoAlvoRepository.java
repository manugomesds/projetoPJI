package com.portifolio.repository;

import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Duas consultas em lote por página, independentemente do número de salvos. */
@Repository @RequiredArgsConstructor
public class SalvoAlvoRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public record Alvo(Long id, String nome, String foto, String localizacao, String contratante,
                       String status, boolean disponivel, String funcoes) {}

    public Map<Long, Alvo> perfis(Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        // Mesma política pública do RF10: tipo ARTISTA e maioridade; sem dados privados no DTO.
        var rows = jdbc.query("""
            select u.id,u.nome,u.foto_perfil_url,p.localizacao,
                   (select string_agg(f.nome, ', ' order by f.nome,f.id)
                    from perfil_artista_funcao af join funcoes f on f.id=af.funcao_id
                    where af.perfil_artista_id=p.usuario_id) as funcoes
            from perfis_artistas p join usuarios u on u.id=p.usuario_id
            where u.id in (:ids) and u.tipo_usuario='ARTISTA'
              and u.data_nascimento + interval '18 years' <= cast(:hoje as date)
            """, Map.of("ids",ids,"hoje",LocalDate.now()),
            (r,n) -> new Alvo(r.getLong("id"),r.getString("nome"),r.getString("foto_perfil_url"),
                    r.getString("localizacao"),null,null,true,r.getString("funcoes")));
        return mapear(rows);
    }

    public Map<Long, Alvo> vagas(Set<Long> ids, Long usuarioId) {
        if (ids.isEmpty()) return Map.of();
        var rows = jdbc.query("""
            select v.id,v.titulo,v.cidade,v.estado,v.status,
                   coalesce(nullif(p.nome_empresa,''),u.nome) as contratante,
                   (v.status='ABERTA' or v.contratante_id=:usuarioId
                    or exists(select 1 from candidaturas c where c.vaga_id=v.id and c.artista_id=:usuarioId)) as disponivel
            from vagas v join perfis_contratantes p on p.usuario_id=v.contratante_id
            join usuarios u on u.id=p.usuario_id where v.id in (:ids)
            """, Map.of("ids",ids,"usuarioId",usuarioId),
            (r,n) -> new Alvo(r.getLong("id"),r.getString("titulo"),null,
                    r.getString("cidade")+"/"+r.getString("estado"),r.getString("contratante"),
                    r.getString("status"),r.getBoolean("disponivel"),null));
        return mapear(rows);
    }

    private Map<Long, Alvo> mapear(List<Alvo> rows) {
        Map<Long, Alvo> result = new HashMap<>();
        rows.forEach(a -> result.put(a.id(),a));
        return result;
    }
}
