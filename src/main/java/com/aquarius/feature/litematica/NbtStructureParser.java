package com.aquarius.feature.litematica;

import com.viaversion.nbt.io.NBTIO;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.IntTag;
import com.viaversion.nbt.tag.ListTag;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads vanilla structure-block {@code .nbt} files (gzipped, named NBT): a {@code size} list, a {@code palette}
 * list of block states, and a sparse {@code blocks} list of {@code {state, pos[x,y,z]}}. Coordinates are already
 * 0-based from the structure's minimum corner, so the mapping to {@link Schematic} is direct.
 */
public final class NbtStructureParser {
    private NbtStructureParser() {}

    public static Schematic parse(Path path) throws IOException, LitematicaParseException {
        CompoundTag root;
        try {
            root = NBTIO.reader(CompoundTag.class).named().read(path, true);
        } catch (IOException io) {
            throw io;
        } catch (Exception e) {
            throw new LitematicaParseException("Not a valid NBT structure file: " + e.getMessage(), e);
        }
        if (root == null) throw new LitematicaParseException("Empty structure file");

        ListTag<CompoundTag> paletteList = root.getListTag("palette", CompoundTag.class);
        if (paletteList == null || paletteList.size() == 0) throw new LitematicaParseException("Structure has no palette");
        PaletteEntry[] palette = new PaletteEntry[paletteList.size()];
        for (int i = 0; i < palette.length; i++) {
            String blockName = paletteList.get(i).getString("Name");
            palette[i] = new PaletteEntry(blockName == null ? "air" : blockName);
        }

        ListTag<CompoundTag> blockList = root.getListTag("blocks", CompoundTag.class);
        if (blockList == null) throw new LitematicaParseException("Structure has no blocks");

        List<Schematic.BlockEntry> blocks = new ArrayList<>();
        int skipped = 0;
        for (int i = 0; i < blockList.size(); i++) {
            CompoundTag b = blockList.get(i);
            int state = b.getInt("state");
            if (state < 0 || state >= palette.length) continue;
            PaletteEntry entry = palette[state];
            if (entry.isAir()) continue;
            ListTag<IntTag> pos = b.getListTag("pos", IntTag.class);
            if (pos == null || pos.size() < 3) continue;
            int x = pos.get(0).asInt(), y = pos.get(1).asInt(), z = pos.get(2).asInt();
            if (entry.placeable()) blocks.add(new Schematic.BlockEntry(x, y, z, entry));
            else skipped++;
        }

        int sizeX = 0, sizeY = 0, sizeZ = 0;
        ListTag<IntTag> sizeTag = root.getListTag("size", IntTag.class);
        if (sizeTag != null && sizeTag.size() >= 3) {
            sizeX = sizeTag.get(0).asInt();
            sizeY = sizeTag.get(1).asInt();
            sizeZ = sizeTag.get(2).asInt();
        }
        int dataVersion = root.getInt("DataVersion", 0);
        return new Schematic(path.getFileName().toString(), sizeX, sizeY, sizeZ, dataVersion,
            blocks, skipped, Schematic.tally(blocks));
    }
}
