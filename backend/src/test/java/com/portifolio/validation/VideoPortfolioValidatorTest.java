package com.portifolio.validation;

import static org.assertj.core.api.Assertions.*;
import com.portifolio.exception.UnprocessableEntityException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class VideoPortfolioValidatorTest {
    final VideoPortfolioValidator validator = new VideoPortfolioValidator();
    @ParameterizedTest @CsvSource({
        "https://youtube.com/watch?v=dQw4w9WgXcQ,YOUTUBE", "https://youtu.be/dQw4w9WgXcQ?si=test,YOUTUBE",
        "https://www.youtube.com/shorts/dQw4w9WgXcQ,YOUTUBE", "https://vimeo.com/123456789,VIMEO"})
    void geraUrlConfiavel(String url,String provider) {
        var v=validator.validar(url);assertThat(v.provedor()).isEqualTo(provider);
        assertThat(v.embedUrl()).matches("https://(www.youtube-nocookie.com/embed/[A-Za-z0-9_-]{11}|player.vimeo.com/video/[0-9]+)");
    }
    @ParameterizedTest @ValueSource(strings={"https://youtube.com.evil.test/watch?v=dQw4w9WgXcQ","https://evilyoutube.com/watch?v=dQw4w9WgXcQ",
        "javascript:alert(1)","https://example.com/123","<iframe src='https://youtube.com'></iframe>","https://youtube.com@evil.test/123",
        "https://evil@youtube.com/watch?v=dQw4w9WgXcQ","https://youtube.com:8443/watch?v=dQw4w9WgXcQ",
        "https://youtube.com/watch?v=dQw4w9WgXcQ&v=aaaaaaaaaaa","https://youtu.be/%2e%2e/evil","https://vimeo.com/../123",
        "http://vimeo.com/123","https://vimeo.com/123#evil","https://youtube.com/watch?v=wrong"})
    void rejeitaUrlMaliciosa(String url) {assertThatThrownBy(() -> validator.validar(url)).isInstanceOf(UnprocessableEntityException.class);}
}
