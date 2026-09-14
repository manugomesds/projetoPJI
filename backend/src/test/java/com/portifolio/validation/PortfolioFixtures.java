package com.portifolio.validation;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

public final class PortfolioFixtures {
    private PortfolioFixtures() {}
    public static byte[] imagem(String formato) {
        try {
            var out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB), formato, out);
            return out.toByteArray();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    public static byte[] pdf() { return "%PDF-1.7\n1 0 obj<</Type/Catalog>>endobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII); }
    public static byte[] mp3() {
        byte[] b = new byte[834];
        for (int p : new int[]{0,417}) { b[p]=(byte)255; b[p+1]=(byte)251; b[p+2]=(byte)144; }
        return b;
    }
    public static byte[] tamanho(String ext, int size) {
        byte[] base = ext.equals("pdf") ? pdf() : ext.equals("mp3") ? mp3() : imagem(ext.equals("jpeg") ? "jpg" : ext);
        if (ext.equals("png")) {
            int pad = size - base.length - 12, offset = base.length - 12;
            byte[] b = new byte[size]; System.arraycopy(base,0,b,0,offset);
            ByteBuffer.wrap(b,offset,4).putInt(pad);
            System.arraycopy(new byte[]{116,69,88,116},0,b,offset+4,4);
            CRC32 crc = new CRC32(); crc.update(b,offset+4,pad+4);
            ByteBuffer.wrap(b,offset+pad+8,4).putInt((int)crc.getValue());
            System.arraycopy(base,offset,b,size-12,12); return b;
        }
        if (ext.equals("mp3")) {
            byte[] b = new byte[size]; int pad = size - base.length - 10;
            b[0]='I';b[1]='D';b[2]='3';b[3]=4;
            for(int i=9;i>=6;i--) { b[i]=(byte)(pad&127);pad>>>=7; }
            System.arraycopy(base,0,b,size-base.length,base.length); return b;
        }
        byte[] b = Arrays.copyOf(base,size);
        if (!ext.equals("pdf")) { b[size-2]=(byte)255;b[size-1]=(byte)217; }
        return b;
    }
}
