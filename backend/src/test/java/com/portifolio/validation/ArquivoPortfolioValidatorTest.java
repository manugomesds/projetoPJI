package com.portifolio.validation;

import static org.assertj.core.api.Assertions.*;
import static com.portifolio.validation.PortfolioFixtures.*;
import com.portifolio.exception.UnprocessableEntityException;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.mock.web.MockMultipartFile;

class ArquivoPortfolioValidatorTest {
    final ArquivoPortfolioValidator validator = new ArquivoPortfolioValidator();
    static String mime(String ext) { return switch(ext) { case "pdf" -> "application/pdf"; case "mp3" -> "audio/mpeg"; case "png" -> "image/png"; default -> "image/jpeg"; }; }
    @ParameterizedTest @ValueSource(strings={"jpg","jpeg","png","pdf","mp3"})
    void aceitaFormatosValidos(String ext) {
        byte[] b = ext.equals("pdf") ? pdf() : ext.equals("mp3") ? mp3() : imagem(ext.equals("jpeg") ? "jpg" : ext);
        var result = validator.validar(new MockMultipartFile("arquivo", "obra."+ext.toUpperCase(), mime(ext), b));
        assertThat(result.bytes()).isEqualTo(b); assertThat(result.extensao()).isEqualTo(ext);
        assertThat(result.nome()).isEqualTo("obra."+ext);
    }
    static Stream<Arguments> limites() {
        return Stream.of("jpg","jpeg","png","pdf","mp3").flatMap(ext -> Stream.of(-1,0,1).map(delta -> Arguments.of(ext,delta)));
    }
    @ParameterizedTest @MethodSource("limites") void limitesExatos(String ext, int delta) {
        int limite = (ext.equals("pdf") ? 10 : ext.equals("mp3") ? 20 : 5) * 1024 * 1024;
        var file = new MockMultipartFile("arquivo","obra."+ext,mime(ext),tamanho(ext,limite+delta));
        if(delta>0) assertThatThrownBy(() -> validator.validar(file)).isInstanceOf(UnprocessableEntityException.class);
        else assertThat(validator.validar(file).bytes()).hasSize(limite+delta);
    }
    @ParameterizedTest @ValueSource(strings={"doc","docx","svg","gif","zip","rar","exe","html","js","mp4","mov","avi","webm","jpg.exe","semextensao"})
    void rejeitaOutrosFormatos(String ext) {
        String nome=ext.equals("semextensao") ? ext : "obra."+ext;
        assertThatThrownBy(() -> validator.validar(new MockMultipartFile("arquivo",nome,"image/jpeg",imagem("jpg"))))
                .isInstanceOf(UnprocessableEntityException.class);
    }
    @ParameterizedTest @ValueSource(strings={"../../evil.jpg","..\\evil.jpg","/evil.jpg","C:\\evil.jpg","C:evil.jpg","a\r\n.jpg","a\u0000.jpg","a\u202e.jpg"," ",".jpg","a.jpg "})
    void rejeitaNomesInseguros(String nome) {
        assertThatThrownBy(() -> validator.validar(new MockMultipartFile("arquivo",nome,"image/jpeg",imagem("jpg"))))
                .isInstanceOf(UnprocessableEntityException.class);
    }
    @Test void nomeLimite150() {
        assertThat(validator.validar(new MockMultipartFile("arquivo","a".repeat(146)+".jpg","image/jpeg",imagem("jpg"))).nome()).hasSize(150);
        assertThatThrownBy(() -> validator.validar(new MockMultipartFile("arquivo","a".repeat(147)+".jpg","image/jpeg",imagem("jpg"))))
                .isInstanceOf(UnprocessableEntityException.class);
    }
    @ParameterizedTest @ValueSource(strings={"image/png","application/octet-stream","text/html","","image/jpeg;evil=1"})
    void rejeitaMimeMentiroso(String mime) {
        assertThatThrownBy(() -> validator.validar(new MockMultipartFile("arquivo","obra.jpg",mime,imagem("jpg"))))
                .isInstanceOf(UnprocessableEntityException.class);
    }
    static Stream<Arguments> invalidos() {
        return Stream.of(Arguments.of("jpg",new byte[]{'M','Z',1,2}), Arguments.of("jpg",imagem("png")),
                Arguments.of("pdf","<html>evil</html>".getBytes()), Arguments.of("pdf",new byte[]{'P','K',3,4}),
                Arguments.of("mp3",new byte[]{'P','K',3,4}),Arguments.of("mp3",new byte[200]),
                Arguments.of("mp3",new byte[]{'I','D','3',4,0,0,0,0,0,0}),
                Arguments.of("jpg",Arrays.copyOf(imagem("jpg"),40)), Arguments.of("png",Arrays.copyOf(imagem("png"),40)),
                Arguments.of("mp3",Arrays.copyOf(mp3(),600)),Arguments.of("pdf",new byte[0]));
    }
    @ParameterizedTest @MethodSource("invalidos") void rejeitaSpoofingETruncamento(String ext, byte[] b) {
        assertThatThrownBy(() -> validator.validar(new MockMultipartFile("arquivo","obra."+ext,mime(ext),b)))
                .isInstanceOf(UnprocessableEntityException.class);
    }
    @Test void rejeitaPngCrcCorrompido() {
        byte[] b=imagem("png"); b[45]^=1;
        assertThatThrownBy(() -> validator.validar(new MockMultipartFile("arquivo","obra.png","image/png",b)))
                .isInstanceOf(UnprocessableEntityException.class);
    }
    @Test void tamanhoDeclaradoDivergente() {
        var file=new MockMultipartFile("arquivo","obra.pdf","application/pdf",pdf()) { @Override public long getSize(){return 1;} };
        assertThatThrownBy(() -> validator.validar(file)).isInstanceOf(UnprocessableEntityException.class);
    }
    @Test void campoAusente() { assertThatThrownBy(() -> validator.validar(null)).isInstanceOf(UnprocessableEntityException.class); }
    @Test void entradaReutilizavelDeFotoAplicaMesmaWhitelistELimite() {
        assertThat(validator.validarImagem(new MockMultipartFile("arquivo","a.png","image/png",imagem("png"))).mime()).isEqualTo("image/png");
        assertThatThrownBy(() -> validator.validarImagem(new MockMultipartFile("arquivo","a.pdf","application/pdf",pdf()))).isInstanceOf(UnprocessableEntityException.class);
        assertThatThrownBy(() -> validator.validarImagem(new MockMultipartFile("arquivo","a.jpg","image/jpeg",new byte[5*1024*1024+1]))).isInstanceOf(UnprocessableEntityException.class);
    }
}
