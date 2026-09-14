package com.portifolio.validation;

import com.portifolio.exception.UnprocessableEntityException;
import java.net.URI;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class VideoPortfolioValidator {
    public record Video(String urlOriginal, String embedUrl, String provedor) {}
    public Video validar(String valor) {
        try {
            if (valor == null || valor.length() > 255 || !valor.equals(valor.strip())) throw new IllegalArgumentException();
            URI u = new URI(valor);
            if (!"https".equalsIgnoreCase(u.getScheme()) || u.getHost() == null || u.getUserInfo() != null
                    || u.getPort() != -1 || u.getFragment() != null || valor.contains("%")) throw new IllegalArgumentException();
            String host = u.getHost().toLowerCase(Locale.ROOT), path = u.getPath(), id = null;
            if (Set.of("youtube.com", "www.youtube.com", "m.youtube.com").contains(host)) {
                if (path.equals("/watch")) {
                    Map<String, String> params = new HashMap<>();
                    for (String pair : Objects.toString(u.getQuery(), "").split("&")) {
                        String[] kv = pair.split("=", 2);
                        if (kv.length != 2 || params.put(kv[0], kv[1]) != null) throw new IllegalArgumentException();
                    }
                    id = params.get("v");
                } else if (path.matches("/(shorts|embed)/[A-Za-z0-9_-]{11}")) id = path.substring(path.lastIndexOf('/') + 1);
            } else if (host.equals("youtu.be") && path.matches("/[A-Za-z0-9_-]{11}")) id = path.substring(1);
            else if (Set.of("vimeo.com", "www.vimeo.com").contains(host) && path.matches("/[1-9][0-9]{0,11}")) {
                id = path.substring(1);
                return new Video("https://vimeo.com/" + id, "https://player.vimeo.com/video/" + id, "VIMEO");
            }
            if (id == null || !id.matches("[A-Za-z0-9_-]{11}")) throw new IllegalArgumentException();
            return new Video("https://www.youtube.com/watch?v=" + id, "https://www.youtube-nocookie.com/embed/" + id, "YOUTUBE");
        } catch (Exception ex) { throw new UnprocessableEntityException("Link inválido. Informe um vídeo HTTPS do YouTube ou Vimeo."); }
    }
}
