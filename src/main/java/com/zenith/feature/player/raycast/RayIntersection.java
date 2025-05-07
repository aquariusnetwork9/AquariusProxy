package com.zenith.feature.player.raycast;

import com.zenith.mc.block.Direction;

public record RayIntersection(double x, double y, double z, Direction intersectingFace) {
}
