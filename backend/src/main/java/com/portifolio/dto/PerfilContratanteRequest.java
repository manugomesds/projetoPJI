package com.portifolio.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;

@Getter
@Setter
@JsonIgnoreProperties(value = {"usuarioId", "perfilCompleto"})
public class PerfilContratanteRequest {

    @Size(max = 150, message = "Nome da empresa deve ter no máximo 150 caracteres")
    private String nomeEmpresa;

    @Size(max = 100, message = "Tipo de perfil deve ter no máximo 100 caracteres")
    private String tipoPerfil;

    private String biografia;

    @Size(max = 150, message = "Localização deve ter no máximo 150 caracteres")
    private String localizacao;

    @Size(max = 255, message = "URL do banner deve ter no máximo 255 caracteres")
    @URL(message = "URL do banner deve ser válida")
    private String bannerUrl;
}
