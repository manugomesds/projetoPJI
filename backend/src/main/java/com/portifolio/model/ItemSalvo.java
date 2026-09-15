package com.portifolio.model;

import com.portifolio.model.enums.TipoAlvoSalvo;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Mapeia somente a tabela existente; alvo_id não é uma associação polimórfica JPA. */
@Entity @Table(name = "itens_salvos") @Getter @NoArgsConstructor
public class ItemSalvo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "usuario_id", nullable = false) private Long usuarioId;
    @Column(name = "tipo_alvo", nullable = false, columnDefinition = "tipo_alvo_salvo_enum") private TipoAlvoSalvo tipoAlvo;
    @Column(name = "alvo_id", nullable = false) private Long alvoId;
    @Column(name = "data_salvamento") private LocalDateTime dataSalvamento;
}
