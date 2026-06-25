package com.aquarius.feature.viewer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Minimal truecolor (8-bit RGB) PNG encoder. Deliberately avoids {@code java.awt}/{@code ImageIO} so it stays
 * native-image friendly and dependency-free — the viewer map is tiny (a few hundred px square) so a hand-rolled
 * single-IDAT, filter-none encoder is plenty.
 */
final class PngEncoder {
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private PngEncoder() {}

    /** Encodes a row-major {@code width*height*3} RGB buffer as a PNG. */
    static byte[] encodeRgb(int width, int height, byte[] rgb) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(SIGNATURE, 0, SIGNATURE.length);

            ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
            writeInt(ihdr, width);
            writeInt(ihdr, height);
            ihdr.write(8);   // bit depth
            ihdr.write(2);   // color type 2 = truecolor RGB
            ihdr.write(0);   // compression: deflate
            ihdr.write(0);   // filter method 0
            ihdr.write(0);   // no interlace
            writeChunk(out, "IHDR", ihdr.toByteArray());

            // Raw image data: each scanline prefixed with filter byte 0 (none).
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            for (int y = 0; y < height; y++) {
                raw.write(0);
                raw.write(rgb, y * width * 3, width * 3);
            }
            ByteArrayOutputStream comp = new ByteArrayOutputStream();
            Deflater def = new Deflater(Deflater.BEST_SPEED);
            try (DeflaterOutputStream dos = new DeflaterOutputStream(comp, def)) {
                byte[] r = raw.toByteArray();
                dos.write(r, 0, r.length);
            }
            def.end();
            writeChunk(out, "IDAT", comp.toByteArray());
            writeChunk(out, "IEND", new byte[0]);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("PNG encode failed", e);
        }
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) {
        writeInt(out, data.length);
        byte[] t = type.getBytes(StandardCharsets.US_ASCII);
        out.write(t, 0, t.length);
        out.write(data, 0, data.length);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        writeInt(out, (int) crc.getValue());
    }

    private static void writeInt(ByteArrayOutputStream out, int v) {
        out.write((v >>> 24) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write(v & 0xFF);
    }
}
