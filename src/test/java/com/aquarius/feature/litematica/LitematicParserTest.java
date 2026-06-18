package com.aquarius.feature.litematica;

import com.viaversion.nbt.io.NBTIO;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.IntTag;
import com.viaversion.nbt.tag.ListTag;
import com.viaversion.nbt.tag.LongArrayTag;
import com.viaversion.nbt.tag.StringTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the parser's risky math (bit width, spanning packed-long unpacking, index ordering, negative-size
 * normalization, air-skipping) against a tiny in-memory schematic written through the same NBT library the parser
 * reads. Pure NBT + arithmetic — no Minecraft connection. The full-parse cases assume the item registry resolves
 * {@code stone} (it loads from bundled resources, as the block/item registries do everywhere else).
 */
class LitematicParserTest {

    @Test
    void bitsPerEntry() {
        assertEquals(2, LitematicParser.bitsPerEntry(1));
        assertEquals(2, LitematicParser.bitsPerEntry(2));
        assertEquals(2, LitematicParser.bitsPerEntry(4));
        assertEquals(3, LitematicParser.bitsPerEntry(5));
        assertEquals(4, LitematicParser.bitsPerEntry(16));
        assertEquals(5, LitematicParser.bitsPerEntry(17));
    }

    @Test
    void getAtRoundTripSpanningWords() {
        // 40 entries of 5 bits = 200 bits -> 4 longs, with entries straddling 64-bit boundaries.
        int bits = 5, n = 40;
        long max = (1L << bits) - 1;
        long[] arr = new long[(int) Math.ceil(n * bits / 64.0)];
        for (int i = 0; i < n; i++) setAt(arr, bits, i, (i * 7) & max);
        for (int i = 0; i < n; i++) {
            assertEquals((i * 7) & max, LitematicParser.getAt(arr, bits, max, i), "entry " + i);
        }
    }

    @Test
    void parsesTwoByTwoBox(@TempDir Path tmp) throws IOException, LitematicaParseException {
        Path file = tmp.resolve("box.litematic");
        writeBox(file, 0, 0, 0, 2, 2, 2);

        Schematic s = LitematicParser.parse(file);
        assertEquals(2, s.sizeX());
        assertEquals(2, s.sizeY());
        assertEquals(2, s.sizeZ());
        assertEquals(7, s.totalBlocks(), "8 cells minus one air corner");
        assertEquals(0, s.skipped());
        assertEquals(1, s.materials().size());
        assertEquals("stone", s.materials().get(0).item());
        assertEquals(7, s.materials().get(0).count());

        assertFalse(hasBlock(s, 0, 0, 0), "the (0,0,0) air corner is skipped");
        assertTrue(hasBlock(s, 1, 1, 1));
    }

    @Test
    void negativeSizeStillYields2x2x2(@TempDir Path tmp) throws IOException, LitematicaParseException {
        Path file = tmp.resolve("neg.litematic");
        // Position at a corner with negative X size: the min corner is offset, normalised back to a 0-based 2x2x2.
        writeBox(file, 5, 0, 0, -2, 2, 2);

        Schematic s = LitematicParser.parse(file);
        assertEquals(2, s.sizeX());
        assertEquals(2, s.sizeY());
        assertEquals(2, s.sizeZ());
        assertEquals(7, s.totalBlocks());
    }

    // ---------------------------------------------------------------- helpers

    private static boolean hasBlock(Schematic s, int x, int y, int z) {
        return s.blocks().stream().anyMatch(b -> b.x() == x && b.y() == y && b.z() == z);
    }

    /** Write a |size|^3 box of stone with a single air cell at local (0,0,0). */
    private static void writeBox(Path file, int px, int py, int pz, int sx, int sy, int sz) throws IOException {
        int asx = Math.abs(sx), asy = Math.abs(sy), asz = Math.abs(sz);
        int total = asx * asy * asz, bits = LitematicParser.bitsPerEntry(2);
        long[] states = new long[(int) Math.ceil(total * bits / 64.0)];
        for (int y = 0; y < asy; y++)
            for (int z = 0; z < asz; z++)
                for (int x = 0; x < asx; x++) {
                    int index = (y * asz + z) * asx + x;
                    setAt(states, bits, index, index == 0 ? 0 : 1); // palette 0=air, 1=stone
                }

        CompoundTag region = new CompoundTag();
        region.put("Position", xyz(px, py, pz));
        region.put("Size", xyz(sx, sy, sz));
        ListTag<CompoundTag> palette = new ListTag<>(CompoundTag.class);
        palette.add(named("minecraft:air"));
        palette.add(named("minecraft:stone"));
        region.put("BlockStatePalette", palette);
        region.put("BlockStates", new LongArrayTag(states));

        CompoundTag regions = new CompoundTag();
        regions.put("Main", region);
        CompoundTag root = new CompoundTag();
        root.put("MinecraftDataVersion", new IntTag(3700));
        root.put("Version", new IntTag(6));
        root.put("Regions", regions);

        NBTIO.writer().named().write(file, root, true);
    }

    private static CompoundTag xyz(int x, int y, int z) {
        CompoundTag t = new CompoundTag();
        t.put("x", new IntTag(x));
        t.put("y", new IntTag(y));
        t.put("z", new IntTag(z));
        return t;
    }

    private static CompoundTag named(String name) {
        CompoundTag t = new CompoundTag();
        t.put("Name", new StringTag(name));
        return t;
    }

    /** Inverse of {@link LitematicParser#getAt}: write a {@code bits}-wide value, spanning words if needed. */
    private static void setAt(long[] arr, int bits, int index, long value) {
        long max = (1L << bits) - 1;
        value &= max;
        long startOffset = (long) index * bits;
        int startArr = (int) (startOffset >> 6);
        int endArr = (int) (((long) (index + 1) * bits - 1) >> 6);
        int startBit = (int) (startOffset & 0x3F);
        arr[startArr] |= value << startBit;
        if (startArr != endArr) {
            int endOffset = 64 - startBit;
            arr[endArr] |= value >>> endOffset;
        }
    }
}
