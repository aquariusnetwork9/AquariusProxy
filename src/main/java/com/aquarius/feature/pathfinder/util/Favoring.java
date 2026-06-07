package com.aquarius.feature.pathfinder.util;

import com.aquarius.Globals;
import com.aquarius.feature.pathfinder.PlayerContext;
import com.aquarius.feature.pathfinder.calc.IPath;
import com.aquarius.feature.pathfinder.movement.CalculationContext;
import com.aquarius.mc.block.BlockPos;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;

public class Favoring {

    private final Long2DoubleOpenHashMap favorings;

    public Favoring(PlayerContext ctx, IPath previous, CalculationContext context) {
        this(previous, context);
//        for (Avoidance avoid : Avoidance.create(ctx)) {
//            avoid.applySpherical(favorings);
//        }
        Globals.PATH_LOG.debug("Favoring size: {}", favorings.size());
    }

    public Favoring(IPath previous, CalculationContext context) { // create one just from previous path, no mob avoidances
        favorings = new Long2DoubleOpenHashMap();
        favorings.defaultReturnValue(1.0D);
        double coeff = context.backtrackCostFavoringCoefficient;
        if (coeff != 1D && previous != null) {
            previous.positions().forEach(pos -> favorings.put(BlockPos.longHash(pos), coeff));
        }
    }

    public boolean isEmpty() {
        return favorings.isEmpty();
    }

    public double calculate(long hash) {
        return favorings.get(hash);
    }
}
