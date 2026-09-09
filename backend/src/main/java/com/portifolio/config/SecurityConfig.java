package com.portifolio.config;

import com.portifolio.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        (request, response, exception) -> response.sendError(
                                HttpServletResponse.SC_UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        // Rotas publicas — sem token
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/cadastro",
                                "/api/auth/login",
                                "/api/auth/google",
                                "/api/auth/refresh",
                                "/api/auth/logout",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password").permitAll()
                        // RF03: listagem/busca de vagas e publica (feed tipo LinkedIn).
                        // Candidatura (RF06) e criacao/edicao continuam exigindo autenticacao.
                        .requestMatchers(HttpMethod.GET, "/api/vagas").permitAll()
                        .requestMatchers(new RegexRequestMatcher("^/api/vagas/\\d+$", "GET")).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/vagas/*/similares").permitAll()
                        // RF10: somente a consulta publica por tipo e ID dispensa JWT.
                        .requestMatchers(HttpMethod.GET, "/api/perfis/publicos/*/*").permitAll()
                        // O handshake nao carrega JWT. A autenticacao ocorre no frame STOMP CONNECT.
                        .requestMatchers(HttpMethod.GET, "/ws", "/ws/**").permitAll()
                        // Não há administração global no MVP. Cadastro convencional só por /auth/cadastro.
                        .requestMatchers(HttpMethod.POST,
                                "/api/usuarios", "/api/usuarios/**",
                                "/api/tags", "/api/tags/**").denyAll()
                        .requestMatchers(HttpMethod.PUT, "/api/tags", "/api/tags/**").denyAll()
                        .requestMatchers(HttpMethod.PATCH, "/api/tags", "/api/tags/**").denyAll()
                        // RF22 ainda não orquestra anonimização/retenção: não expor hard deletes em cascata.
                        .requestMatchers(HttpMethod.DELETE,
                                "/api/usuarios", "/api/usuarios/**",
                                "/api/perfis-artistas", "/api/perfis-artistas/**",
                                "/api/perfis-contratantes", "/api/perfis-contratantes/**",
                                "/api/tags", "/api/tags/**").denyAll()
                        // Terceiros consultam exclusivamente o contrato público RF10; não há diretório privado.
                        .requestMatchers(HttpMethod.GET,
                                "/api/usuarios", "/api/usuarios/",
                                "/api/perfis-artistas", "/api/perfis-artistas/",
                                "/api/perfis-contratantes", "/api/perfis-contratantes/").denyAll()
                        .requestMatchers(HttpMethod.HEAD,
                                "/api/usuarios", "/api/usuarios/",
                                "/api/perfis-artistas", "/api/perfis-artistas/",
                                "/api/perfis-contratantes", "/api/perfis-contratantes/").denyAll()
                        // APIs nao listadas acima preservam o requisito de autenticacao.
                        .requestMatchers("/api/**").authenticated()
                        // Somente a interface e os recursos estaticos sao publicos.
                        // A autorizacao das APIs continua nas regras especificas acima.
                        .requestMatchers(HttpMethod.GET,
                                "/",
                                "/*.html",
                                "/favicon.ico",
                                "/manifest.json",
                                "/robots.txt",
                                "/asset-manifest.json",
                                "/logo*.png",
                                "/static/**",
                                "/assets/**",
                                "/css/**",
                                "/js/**",
                                "/vagas",
                                "/vagas/**").permitAll()
                        // Fora de /api e /ws, GETs desconhecidos precisam chegar ao DispatcherServlet
                        // para resultar em 404 real, sem serem convertidos em 401 pela seguranca.
                        .requestMatchers(HttpMethod.GET, "/**").permitAll()
                        // Tudo mais exige autenticacao
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
