package com.aquarius.feature.elytra;

import java.util.List;

/**
 * A saved, named multi-leg elytra trip plan. Each {@link Leg} is traversed in order; between them the bot either
 * e-bounces a road ({@code ride}) or open-nether cruises ({@code fly}) to that leg's endpoint NETHER coords. A
 * ride leg's road line runs from the previous waypoint (origin 0,0 for the first leg) to its endpoint, so spawn
 * highways and player-built (off-origin) roads are handled uniformly.
 *
 * <p>The route ends either at an overworld base — the planner exits through the portal nearest the last leg's
 * nether coords and flies the overworld to {@code (destX,destY,destZ)} — or, when {@code endInNether}, by landing
 * at the last leg's nether endpoint. Stored in {@code CONFIG.client.extra.elytraPilot.tripRoutes} and serialized
 * with the rest of the config (records, like {@code Waypoint}).
 */
public record Route(
    String id,
    boolean endInNether,
    int destX, int destY, int destZ,
    List<Leg> legs
) {
    /** One leg: e-bounce a road ({@code ride=true}) or open-nether cruise to the endpoint NETHER coords (x,z). */
    public record Leg(boolean ride, int x, int z, int roadY) {}
}
