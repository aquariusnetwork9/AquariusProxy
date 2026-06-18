package com.aquarius.feature.litematica;

import com.viaversion.nbt.io.NBTIO;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.ListTag;
import com.viaversion.nbt.tag.LongArrayTag;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads Litematica's native {@code .litematic} format (gzipped, named NBT). A file holds one or more named
 * <i>regions</i>, each with a {@code Position} (corner, relative to the schematic origin), a (possibly negative)
 * {@code Size}, a {@code BlockStatePalette}, and a {@code BlockStates} packed long array. We unpack each region
 * into absolute-size local space, map every non-air cell to the schematic minimum corner, and return a flat
 * {@link Schematic}.
 *
 * <p>The {@code BlockStates} packing is Litematica's spanning bit array (entries may cross a 64-bit boundary),
 * {@code bitsPerEntry = max(2, ceil(log2(paletteSize)))}; see {@link #getAt}.
 */
public final class LitematicParser {
    private LitematicParser() {}

    public static Schematic parse(Path path) throws IOException, LitematicaParseException {
        CompoundTag root;
        try {
            root = NBTIO.reader(CompoundTag.class).named().read(path, true);
        } catch (IOException io) {
            throw io;
        } catch (Exception e) {
            throw new LitematicaParseException("Not a valid NBT/.litematic file: " + e.getMessage(), e);
        }
        if (root == null) throw new LitematicaParseException("Empty schematic file");

        CompoundTag regions = root.getCompoundTag("Regions");
        if (regions == null || regions.size() == 0) throw new LitematicaParseException("No Regions in schematic");

        int dataVersion = root.getInt("MinecraftDataVersion", 0);
        String name = path.getFileName().toString();
        CompoundTag metadata = root.getCompoundTag("Metadata");
        if (metadata != null) {
            String metaName = metadata.getString("Name");
            if (metaName != null && !metaName.isBlank()) name = metaName;
        }

        // pass 1: parse each region's geometry + palette + states, and find the global minimum corner.
        List<Region> parsed = new ArrayList<>();
        int gMinX = Integer.MAX_VALUE, gMinY = Integer.MAX_VALUE, gMinZ = Integer.MAX_VALUE;
        int gMaxX = Integer.MIN_VALUE, gMaxY = Integer.MIN_VALUE, gMaxZ = Integer.MIN_VALUE;
        for (String regionName : regions.keySet()) {
            CompoundTag region = regions.getCompoundTag(regionName);
            if (region == null) continue;
            Region r = parseRegion(regionName, region);
            parsed.add(r);
            gMinX = Math.min(gMinX, r.minX); gMinY = Math.min(gMinY, r.minY); gMinZ = Math.min(gMinZ, r.minZ);
            gMaxX = Math.max(gMaxX, r.minX + r.sizeX - 1);
            gMaxY = Math.max(gMaxY, r.minY + r.sizeY - 1);
            gMaxZ = Math.max(gMaxZ, r.minZ + r.sizeZ - 1);
        }
        if (parsed.isEmpty()) throw new LitematicaParseException("No readable regions in schematic");

        // pass 2: expand every non-air cell relative to the global minimum corner.
        List<Schematic.BlockEntry> blocks = new ArrayList<>();
        int skipped = 0;
        for (Region r : parsed) {
            long max = (1L << r.bits) - 1;
            for (int y = 0; y < r.sizeY; y++) {
                for (int z = 0; z < r.sizeZ; z++) {
                    for (int x = 0; x < r.sizeX; x++) {
                        int index = (y * r.sizeZ + z) * r.sizeX + x;
                        int pi = (int) getAt(r.states, r.bits, max, index);
                        if (pi < 0 || pi >= r.palette.length) continue;
                        PaletteEntry entry = r.palette[pi];
                        if (entry.isAir()) continue;
                        int wx = (r.minX - gMinX) + x;
                        int wy = (r.minY - gMinY) + y;
                        int wz = (r.minZ - gMinZ) + z;
                        if (entry.placeable()) blocks.add(new Schematic.BlockEntry(wx, wy, wz, entry));
                        else skipped++;
                    }
                }
            }
        }

        int sizeX = gMaxX - gMinX + 1, sizeY = gMaxY - gMinY + 1, sizeZ = gMaxZ - gMinZ + 1;
        return new Schematic(name, sizeX, sizeY, sizeZ, dataVersion, blocks, skipped, Schematic.tally(blocks));
    }

    private static Region parseRegion(String name, CompoundTag region) throws LitematicaParseException {
        CompoundTag pos = region.getCompoundTag("Position");
        CompoundTag size = region.getCompoundTag("Size");
        if (pos == null || size == null) throw new LitematicaParseException("Region '" + name + "' missing Position/Size");
        int px = pos.getInt("x"), py = pos.getInt("y"), pz = pos.getInt("z");
        int sx = size.getInt("x"), sy = size.getInt("y"), sz = size.getInt("z");
        int asx = Math.abs(sx), asy = Math.abs(sy), asz = Math.abs(sz);
        if (asx == 0 || asy == 0 || asz == 0) throw new LitematicaParseException("Region '" + name + "' has zero size");
        // Position is a corner; the min corner is offset when Size is negative.
        int minX = sx < 0 ? px + sx + 1 : px;
        int minY = sy < 0 ? py + sy + 1 : py;
        int minZ = sz < 0 ? pz + sz + 1 : pz;

        ListTag<CompoundTag> paletteList = region.getListTag("BlockStatePalette", CompoundTag.class);
        if (paletteList == null || paletteList.size() == 0) throw new LitematicaParseException("Region '" + name + "' missing BlockStatePalette");
        PaletteEntry[] palette = new PaletteEntry[paletteList.size()];
        for (int i = 0; i < palette.length; i++) {
            String blockName = paletteList.get(i).getString("Name");
            palette[i] = new PaletteEntry(blockName == null ? "air" : blockName);
        }

        LongArrayTag statesTag = region.getLongArrayTag("BlockStates");
        long[] states = statesTag == null ? new long[0] : statesTag.getValue();
        int bits = bitsPerEntry(palette.length);
        return new Region(minX, minY, minZ, asx, asy, asz, palette, states, bits);
    }

    /** Litematica bit width: at least 2, otherwise ceil(log2(paletteSize)). */
    static int bitsPerEntry(int paletteSize) {
        return Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, paletteSize - 1)));
    }

    /**
     * Read entry {@code index} from Litematica's spanning packed long array. An entry of {@code bits} width may
     * straddle a 64-bit word boundary, so we may need to OR together the tail of one word and the head of the next.
     */
    static long getAt(long[] arr, int bits, long maxEntryValue, int index) {
        long startOffset = (long) index * bits;
        int startArrIndex = (int) (startOffset >> 6);
        int endArrIndex = (int) (((long) (index + 1) * bits - 1) >> 6);
        int startBitOffset = (int) (startOffset & 0x3F);
        if (startArrIndex < 0 || endArrIndex >= arr.length) return 0;
        if (startArrIndex == endArrIndex) {
            return (arr[startArrIndex] >>> startBitOffset) & maxEntryValue;
        }
        int endOffset = 64 - startBitOffset;
        return ((arr[startArrIndex] >>> startBitOffset) | (arr[endArrIndex] << endOffset)) & maxEntryValue;
    }

    /** Per-region geometry (min corner + absolute size), palette, and packed states. */
    private static final class Region {
        final int minX, minY, minZ, sizeX, sizeY, sizeZ, bits;
        final PaletteEntry[] palette;
        final long[] states;
        Region(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ,
               PaletteEntry[] palette, long[] states, int bits) {
            this.minX = minX; this.minY = minY; this.minZ = minZ;
            this.sizeX = sizeX; this.sizeY = sizeY; this.sizeZ = sizeZ;
            this.palette = palette; this.states = states; this.bits = bits;
        }
    }
}
