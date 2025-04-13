package com.zenith.feature.pathfinder.util;

import com.google.common.collect.Sets;
import com.zenith.mc.block.Block;
import com.zenith.mc.block.BlockState;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Data
public class BlockOptionalMetaLookup {
    private final Set<Block> blockSet;
    // sets preferable for fast contains
    private final IntSet blockStateIds = new IntOpenHashSet();
    // lists preferable for indexed iteration
    private final List<Block> blockList = new ArrayList<>();
    private final IntList blockStateIdList = new IntArrayList();

    public BlockOptionalMetaLookup(Set<Block> blocks) {
        this.blockSet = blocks;
        for (Block block : blocks) {
            for (int stateId = block.minStateId(); stateId <= block.maxStateId(); stateId++) {
                blockStateIds.add(stateId);
                blockStateIdList.add(stateId);
            }
            this.blockList.add(block);
        }
    }
    public BlockOptionalMetaLookup(Block... blocks) {
        this(Sets.newHashSet(blocks));
    }

    public boolean has(Block block) {
        return blockSet.contains(block);
    }

    public boolean has(BlockState state) {
        return blockStateIds.contains(state.id());
    }

    public boolean has(int state) {
        return blockStateIds.contains(state);
    }
}
