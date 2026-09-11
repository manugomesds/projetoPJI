package com.portifolio.service.google;

public interface GoogleTokenVerifier {

    String MENSAGEM_ERRO = "Não foi possível autenticar com o Google. Tente novamente.";

    GoogleTokenClaims verificar(String idToken);
}
