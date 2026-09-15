package com.portifolio.repository;

import com.portifolio.model.ItemSalvo;
import com.portifolio.model.enums.TipoAlvoSalvo;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface ItemSalvoRepository extends JpaRepository<ItemSalvo, Long> {
    boolean existsByUsuarioIdAndTipoAlvoAndAlvoId(Long usuarioId, TipoAlvoSalvo tipoAlvo, Long alvoId);
    long countByTipoAlvoAndAlvoId(TipoAlvoSalvo tipoAlvo, Long alvoId);
    Page<ItemSalvo> findByUsuarioIdAndTipoAlvoIn(Long usuarioId, Set<TipoAlvoSalvo> tipos, Pageable pageable);

    // A constraint existente arbitra requisições concorrentes sem abortar a transação
    // por unique_violation. Só o INSERT vencedor produz evento de notificação.
    @Modifying
    @Query(value = """
        insert into itens_salvos(usuario_id,tipo_alvo,alvo_id,data_salvamento)
        values (:usuarioId,cast(:tipo as tipo_alvo_salvo_enum),:alvoId,current_timestamp)
        on conflict (usuario_id,tipo_alvo,alvo_id) do nothing
        """, nativeQuery = true)
    int inserirSeAusente(Long usuarioId, String tipo, Long alvoId);

    @Modifying
    @Query("delete from ItemSalvo i where i.usuarioId=:usuarioId and i.tipoAlvo=:tipo and i.alvoId=:alvoId")
    int removerProprio(Long usuarioId, TipoAlvoSalvo tipo, Long alvoId);
}
