package com.portifolio.validation;

import com.portifolio.exception.PortfolioOperationException;
import com.portifolio.exception.UnprocessableEntityException;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ArquivoPortfolioValidator {
    public static final int IMAGE_LIMIT = 5 * 1024 * 1024;
    public static final int PDF_LIMIT = 10 * 1024 * 1024;
    public static final int AUDIO_LIMIT = 20 * 1024 * 1024;
    private static final Map<String, String> MIMES = Map.of("jpg", "image/jpeg", "jpeg", "image/jpeg",
            "png", "image/png", "pdf", "application/pdf", "mp3", "audio/mpeg");
    private static final byte[] PNG = {(byte)137,80,78,71,13,10,26,10};
    public record Validado(String nome, String extensao, String mime, byte[] bytes) {}

    public Validado validar(MultipartFile file) {
        if (file == null) throw invalido("Selecione um arquivo.");
        String nome = nomeSeguro(file.getOriginalFilename());
        int ponto = nome.lastIndexOf('.');
        String ext = ponto < 1 ? "" : nome.substring(ponto + 1).toLowerCase(Locale.ROOT);
        String mime = MIMES.get(ext);
        if (mime == null) throw invalido("Formato não permitido. Use JPG, JPEG, PNG, PDF ou MP3; vídeos apenas por link.");
        int limite = mime.startsWith("image/") ? IMAGE_LIMIT : ext.equals("pdf") ? PDF_LIMIT : AUDIO_LIMIT;
        if (file.getSize() <= 0 || file.getSize() > limite) throw invalido("Arquivo vazio ou acima do limite de " + limite / 1024 / 1024 + " MB.");
        if (!mime.equalsIgnoreCase(file.getContentType())) throw invalido("MIME incompatível com a extensão do arquivo.");
        byte[] bytes;
        try (InputStream in = file.getInputStream()) {
            bytes = in.readNBytes(limite + 1);
        } catch (IOException ex) { throw new PortfolioOperationException(ex); }
        if (bytes.length != file.getSize() || bytes.length > limite) throw invalido("Tamanho de arquivo inválido.");
        if (mime.startsWith("image/")) validarImagem(bytes, ext);
        else if (ext.equals("pdf")) {
            if (!inicia(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))) throw invalido("Assinatura PDF inválida.");
        } else validarMp3(bytes);
        return new Validado(nome.substring(0, ponto + 1) + ext, ext, mime, bytes);
    }

    public static String nomeSeguro(String nome) {
        if (nome == null || nome.isBlank() || nome.length() > 150 || !nome.equals(nome.strip())
                || nome.contains("/") || nome.contains("\\") || nome.contains(":")
                || nome.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT))
            throw invalido("Nome de arquivo inválido: use até 150 caracteres, sem caminhos ou controles.");
        return nome;
    }

    // Entrada reutilizável pelo futuro RF29: inclui formato, MIME, conteúdo e o mesmo limite de 5 MB.
    public Validado validarImagem(MultipartFile file) {
        Validado validado = validar(file);
        if (!validado.mime().startsWith("image/")) throw invalido("Foto deve ser JPG, JPEG ou PNG até 5 MB.");
        return validado;
    }

    // Não redimensiona nem altera o arquivo original.
    private void validarImagem(byte[] bytes, String extensao) {
        boolean png = extensao.equals("png");
        if (png) validarPng(bytes);
        else if (bytes.length < 4 || (bytes[0] & 255) != 255 || (bytes[1] & 255) != 216
                || (bytes[2] & 255) != 255 || (bytes[bytes.length-2] & 255) != 255 || (bytes[bytes.length-1] & 255) != 217)
            throw invalido("JPEG inválido ou truncado.");
        try (var in = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw invalido("Imagem inválida.");
            var reader = readers.next();
            try {
                reader.setInput(in, true, true);
                String formato = reader.getFormatName();
                if (!(png ? formato.equalsIgnoreCase("png") : formato.equalsIgnoreCase("jpeg"))) throw invalido("Conteúdo de imagem incompatível.");
                // Orçamento de memória da JVM, não critério dimensional/resize do RF16.
                long memoriaEstimada = Math.multiplyExact(Math.multiplyExact((long)reader.getWidth(0), reader.getHeight(0)), 8L);
                if (memoriaEstimada > Runtime.getRuntime().maxMemory() / 8) throw invalido("Imagem excede a memória disponível para processamento.");
                boolean[] aviso = {false};
                reader.addIIOReadWarningListener((source, warning) -> aviso[0] = true);
                var imagem = reader.read(0);
                if (imagem == null || aviso[0]) throw invalido("Imagem corrompida ou truncada.");
                imagem.flush();
            } finally { reader.dispose(); }
        } catch (UnprocessableEntityException ex) { throw ex; }
        catch (IOException | RuntimeException ex) { throw invalido("Imagem corrompida ou truncada."); }
    }

    private void validarPng(byte[] b) {
        if (!inicia(b, PNG)) throw invalido("Assinatura PNG inválida.");
        int p = 8;
        boolean dados = false;
        while (p + 12 <= b.length) {
            int tamanho = ByteBuffer.wrap(b, p, 4).getInt();
            if (tamanho < 0 || (long)p + tamanho + 12 > b.length) throw invalido("PNG truncado.");
            String tipo = new String(b, p + 4, 4, StandardCharsets.US_ASCII);
            if (p == 8 && (!tipo.equals("IHDR") || tamanho != 13)) throw invalido("PNG inválido.");
            CRC32 crc = new CRC32(); crc.update(b, p + 4, tamanho + 4);
            long esperado = Integer.toUnsignedLong(ByteBuffer.wrap(b, p + 8 + tamanho, 4).getInt());
            if (crc.getValue() != esperado) throw invalido("PNG corrompido.");
            if (tipo.equals("IDAT")) dados = true;
            p += tamanho + 12;
            if (tipo.equals("IEND")) {
                if (!dados || tamanho != 0 || p != b.length) throw invalido("PNG inválido.");
                return;
            }
        }
        throw invalido("PNG truncado.");
    }

    private void validarMp3(byte[] b) {
        int p = 0;
        if (inicia(b, new byte[]{73,68,51})) {
            if (b.length < 10 || b[3] < 2 || b[3] > 4 || b[4] == (byte)255) throw invalido("ID3 inválido.");
            int tamanho = 0;
            for (int i = 6; i < 10; i++) { if (b[i] < 0) throw invalido("ID3 inválido."); tamanho = (tamanho << 7) | b[i]; }
            p = 10 + tamanho + (b[3] == 4 && (b[5] & 16) != 0 ? 10 : 0);
            if (p > b.length) throw invalido("ID3 truncado.");
        }
        int frames = 0, padrao = -1;
        while (p + 4 <= b.length) {
            if (b.length - p == 128 && b[p] == 'T' && b[p+1] == 'A' && b[p+2] == 'G') { p = b.length; break; }
            int h = ByteBuffer.wrap(b, p, 4).getInt();
            int versao = h >>> 19 & 3, layer = h >>> 17 & 3, taxa = h >>> 12 & 15, sample = h >>> 10 & 3;
            if ((h & 0xffe00000) != 0xffe00000 || versao == 1 || layer != 1 || taxa == 0 || taxa == 15 || sample == 3 || (h & 3) == 2)
                throw invalido("Conteúdo MP3 inválido.");
            int identidade = versao * 4 + sample;
            if (padrao != -1 && identidade != padrao) throw invalido("Frames MP3 incompatíveis.");
            padrao = identidade;
            int[] taxas = versao == 3 ? new int[]{0,32,40,48,56,64,80,96,112,128,160,192,224,256,320}
                    : new int[]{0,8,16,24,32,40,48,56,64,80,96,112,128,144,160};
            int frequencia = new int[]{44100,48000,32000}[sample] / (versao == 3 ? 1 : versao == 2 ? 2 : 4);
            int comprimento = (versao == 3 ? 144000 : 72000) * taxas[taxa] / frequencia + (h >>> 9 & 1);
            if (p + comprimento > b.length) throw invalido("MP3 truncado.");
            p += comprimento; frames++;
        }
        if (frames < 2 || p != b.length) throw invalido("MP3 precisa conter frames MPEG Layer III completos.");
    }
    private static boolean inicia(byte[] b, byte[] assinatura) {
        return b.length >= assinatura.length && Arrays.equals(b, 0, assinatura.length, assinatura, 0, assinatura.length);
    }
    private static UnprocessableEntityException invalido(String mensagem) { return new UnprocessableEntityException(mensagem); }
}
