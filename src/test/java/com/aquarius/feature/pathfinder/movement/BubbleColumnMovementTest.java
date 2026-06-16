package com.aquarius.feature.pathfinder.movement;

import com.aquarius.feature.player.World;
import com.aquarius.mc.block.Block;
import com.aquarius.mc.block.BlockRegistry;
import com.aquarius.mc.block.properties.api.BlockStateProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the {@code drag} -> column-direction mapping that {@link MovementBubbleAscend} and
 * {@link MovementBubbleDescend} rely on. The full path-planning behaviour (chaining BUBBLE_UP/DOWN,
 * exiting at an opening, the magma guard) is validated against a populated world model in the live /
 * replay-trajectory harness — see CONTEXT (2).md; this test pins the deterministic core.
 */
public class BubbleColumnMovementTest {

    @Test
    public void bubbleColumnHasExactlyTwoDragStates() {
        Block block = BlockRegistry.BUBBLE_COLUMN;
        assertEquals(2, block.maxStateId() - block.minStateId() + 1,
            "bubble_column should have exactly two states: drag=true (down) and drag=false (up)");
    }

    @Test
    public void classifiesUpwardAndDownwardColumns() {
        Block block = BlockRegistry.BUBBLE_COLUMN;
        boolean sawUp = false;
        boolean sawDown = false;
        for (int id = block.minStateId(); id <= block.maxStateId(); id++) {
            Boolean drag = World.getBlockStateProperty(id, BlockStateProperties.DRAG);
            assertNotNull(drag, "every bubble_column state must expose the drag property");
            assertTrue(MovementHelper.isBubbleColumn(id));
            if (drag) {
                // drag=true -> magma base -> pulls down
                assertTrue(MovementHelper.isBubbleColumnDown(id), "drag=true must classify as down");
                assertFalse(MovementHelper.isBubbleColumnUp(id), "drag=true must not classify as up");
                sawDown = true;
            } else {
                // drag=false -> soul sand base -> pushes up
                assertTrue(MovementHelper.isBubbleColumnUp(id), "drag=false must classify as up");
                assertFalse(MovementHelper.isBubbleColumnDown(id), "drag=false must not classify as down");
                sawUp = true;
            }
        }
        assertTrue(sawUp, "expected an upward (drag=false) bubble_column state");
        assertTrue(sawDown, "expected a downward (drag=true) bubble_column state");
    }

    @Test
    public void nonColumnBlocksAreNeitherUpNorDown() {
        Block[] others = {BlockRegistry.WATER, BlockRegistry.MAGMA_BLOCK, BlockRegistry.SOUL_SAND, BlockRegistry.STONE};
        for (Block block : others) {
            int id = block.minStateId();
            assertFalse(MovementHelper.isBubbleColumn(id), block.name());
            assertFalse(MovementHelper.isBubbleColumnUp(id), block.name());
            assertFalse(MovementHelper.isBubbleColumnDown(id), block.name());
        }
    }
}
