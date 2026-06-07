package com.aquarius.feature.player.raycast;

import com.aquarius.mc.block.Direction;

public record RayIntersection(double x, double y, double z, Direction intersectingFace) {
}
