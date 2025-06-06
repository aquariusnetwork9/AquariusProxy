package com.zenith.mc;

import com.zenith.feature.player.World;
import com.zenith.mc.block.Block;
import com.zenith.mc.block.BlockRegistry;
import com.zenith.mc.block.BlockStatePropertyRegistry;
import com.zenith.mc.block.properties.SlabType;
import com.zenith.mc.block.properties.api.BlockStateProperties;
import com.zenith.mc.block.properties.api.Property;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BlockStatePropertiesTest {
    @Test
    public void testMangrovePropaguleProperties() {
        var stateDefinition = BlockStatePropertyRegistry.STATES.get(BlockRegistry.MANGROVE_PROPAGULE.id());
        assertNotNull(stateDefinition);
        var state = stateDefinition.getStates().get(14);
        assertNotNull(state);
        assertEquals(1, state.get(BlockStateProperties.AGE_4));
        assertEquals(false, state.get(BlockStateProperties.HANGING));
        assertEquals(1, state.get(BlockStateProperties.STAGE));
        assertEquals(true, state.get(BlockStateProperties.WATERLOGGED));
    }

    @Test
    public void testRedstoneTorchBlockProperties() {
        var stateDefinition = BlockStatePropertyRegistry.STATES.get(BlockRegistry.DEEPSLATE_REDSTONE_ORE.id());
        assertNotNull(stateDefinition);
        var state = stateDefinition.getStates().get(0);
        assertNotNull(state);
        assertEquals(true, state.get(BlockStateProperties.LIT));
    }

    @Test
    public void getWorldBlockStatePropertiesMethods() {
        Block block = BlockRegistry.WAXED_OXIDIZED_CUT_COPPER_SLAB;
        int stateId = block.minStateId();
        assertTrue(World.hasBlockStateProperty(stateId, BlockStateProperties.SLAB_TYPE));
        assertTrue(World.hasBlockStateProperty(stateId, BlockStateProperties.WATERLOGGED));
        var slabType = World.getBlockStateProperty(stateId, BlockStateProperties.SLAB_TYPE);
        assertNotNull(slabType);
        assertEquals(SlabType.TOP, slabType);
        var waterlogged = World.getBlockStateProperty(stateId, BlockStateProperties.WATERLOGGED);
        assertNotNull(waterlogged);
        assertEquals(true, waterlogged);
        ReferenceSet<Property<?>> blockStateProperties = World.getBlockStateProperties(stateId);
        assertNotNull(blockStateProperties);
        assertEquals(ReferenceSet.of(BlockStateProperties.SLAB_TYPE, BlockStateProperties.WATERLOGGED), blockStateProperties);

        stateId = block.maxStateId();
        assertTrue(World.hasBlockStateProperty(stateId, BlockStateProperties.SLAB_TYPE));
        assertTrue(World.hasBlockStateProperty(stateId, BlockStateProperties.WATERLOGGED));
        slabType = World.getBlockStateProperty(stateId, BlockStateProperties.SLAB_TYPE);
        assertNotNull(slabType);
        assertEquals(SlabType.DOUBLE, slabType);
        waterlogged = World.getBlockStateProperty(stateId, BlockStateProperties.WATERLOGGED);
        assertNotNull(waterlogged);
        assertEquals(false, waterlogged);
        blockStateProperties = World.getBlockStateProperties(stateId);
        assertNotNull(blockStateProperties);
        assertEquals(ReferenceSet.of(BlockStateProperties.SLAB_TYPE, BlockStateProperties.WATERLOGGED), blockStateProperties);
    }
}
