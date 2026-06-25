package com.aquarius.feature.viewer;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies the hand-rolled PNG encoder produces a valid file: the signature is right and the bytes decode back
 * (via ImageIO, test-only) to the same dimensions and pixel colors that went in.
 */
class PngEncoderTest {

    @Test
    void encodesADecodableTruecolorPng() throws Exception {
        final int w = 4, h = 3;
        final byte[] rgb = new byte[w * h * 3];
        put(rgb, w, 1, 2, 10, 20, 30);
        put(rgb, w, 3, 0, 200, 100, 50);

        final byte[] png = PngEncoder.encodeRgb(w, h, rgb);

        assertEquals((byte) 0x89, png[0], "PNG signature byte 0");
        assertEquals('P', png[1]);
        assertEquals('N', png[2]);
        assertEquals('G', png[3]);

        final BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(img, "encoded bytes must be a decodable PNG");
        assertEquals(w, img.getWidth());
        assertEquals(h, img.getHeight());
        assertPixel(img, 1, 2, 10, 20, 30);
        assertPixel(img, 3, 0, 200, 100, 50);
        assertPixel(img, 0, 0, 0, 0, 0);   // untouched -> black
    }

    private static void put(byte[] rgb, int w, int x, int y, int r, int g, int b) {
        final int o = (y * w + x) * 3;
        rgb[o] = (byte) r;
        rgb[o + 1] = (byte) g;
        rgb[o + 2] = (byte) b;
    }

    private static void assertPixel(BufferedImage img, int x, int y, int r, int g, int b) {
        final int p = img.getRGB(x, y);
        assertEquals(r, (p >> 16) & 0xFF, "red @" + x + "," + y);
        assertEquals(g, (p >> 8) & 0xFF, "green @" + x + "," + y);
        assertEquals(b, p & 0xFF, "blue @" + x + "," + y);
    }
}
