package com.portifolio.security;

import com.portifolio.model.Usuario;
import com.portifolio.model.enums.StatusConta;

/** Estado persistido prevalece sobre o provedor e sobre JWTs emitidos anteriormente. */
public final class GoogleAccountAccessPolicy {
    private GoogleAccountAccessPolicy() {}

    public static boolean acessoNormalPermitido(Usuario usuario) {
        return usuario.getGoogleId() == null
                || (usuario.getStatusConta() == StatusConta.ATIVA
                    && Boolean.TRUE.equals(usuario.getPerfilCompleto()));
    }
}
