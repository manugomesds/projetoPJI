package com.portifolio.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GoogleAuthConfig {

    public static final List<String> ISSUERS_OFICIAIS =
            List.of("accounts.google.com", "https://accounts.google.com");

    @Bean
    public GoogleIdTokenVerifier googleIdTokenVerifier(
            @Value("${google.client-id}") String googleClientId) {
        return new GoogleIdTokenVerifier.Builder(
                        new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(googleClientId))
                .setIssuers(ISSUERS_OFICIAIS)
                .build();
    }
}
