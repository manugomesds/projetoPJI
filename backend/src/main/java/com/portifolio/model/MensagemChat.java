package com.portifolio.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "mensagens_chat")
@Getter
@Setter
@NoArgsConstructor
public class MensagemChat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sala_id", nullable = false)
    private SalaChat sala;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "remetente_id")
    private Usuario remetente;

    @Column(name = "texto_mensagem", columnDefinition = "text")
    private String texto;

    @Column(name = "url_anexo", length = 255)
    private String urlAnexo;

    @Column(name = "lida")
    private Boolean lida;

    @Column(name = "data_envio")
    private LocalDateTime dataEnvio;
    @Column(name = "editada")
    private Boolean editada = false;
    @Column(name = "data_edicao")
    private LocalDateTime dataEdicao;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "texto_original", columnDefinition = "text")
    private String textoOriginal;
    @Column(name = "excluida")
    private Boolean excluida = false;
    @Column(name = "data_exclusao")
    private LocalDateTime dataExclusao;
}
