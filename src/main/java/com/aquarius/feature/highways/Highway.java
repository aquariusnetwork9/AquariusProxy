package com.aquarius.feature.highways;

import java.util.List;

/**
 * One 2b2t <b>nether</b> road from the bundled highway map ({@code highways/nether_highways.json}, traced from the
 * EnigmA_008 / Dwellur Desmos map). A road is a polyline flattened to a list of straight {@link Segment}s in the
 * nether XZ plane (x = east+/west-, z = south+/north-).
 *
 * <p>The map's cardinals and diagonals run all the way to the nether world border (±30,000,000) as dug tunnel; the
 * {@code surface}/{@code dim} fields say how much of that is actually paved versus dug versus merely planned. Ring
 * and diamond roads are concentric squares/diamonds at {@link #radius}.
 *
 * @param name     human label from the map (e.g. {@code "50k ringroad (5x4)"})
 * @param category {@code axis} (cardinals + diagonals), {@code ring}, {@code diamond}, or {@code grid}
 * @param surface  {@code paved}, {@code dug}, {@code planned}, or {@code unknown}
 * @param dim      tunnel profile such as {@code "6x4"} (width x height), or {@code null} if the map didn't say
 * @param radius   ring/diamond radius in blocks, or {@code null} for {@code axis}/{@code grid}
 * @param yLevel   road Y if the map pinned one (e.g. the 7.5k ring at y113), else {@code null} -> network default
 * @param segments the road's straight runs, in map order
 */
public record Highway(
    String name,
    String category,
    String surface,
    String dim,
    Integer radius,
    Integer yLevel,
    List<Segment> segments
) {
    /** True if the road is actually traversable (paved or dug) rather than merely {@code planned}. */
    public boolean usable() {
        return !"planned".equals(surface);
    }

    /** A straight run of road between two nether coordinates, in the XZ plane. */
    public record Segment(int x1, int z1, int x2, int z2) {
        /**
         * Closest point on this segment to {@code (px, pz)}, clamped to the endpoints.
         *
         * @return a two-element array {@code {x, z}} of the nearest on-road point
         */
        public double[] closestPoint(double px, double pz) {
            double dx = (double) x2 - x1;
            double dz = (double) z2 - z1;
            double len2 = dx * dx + dz * dz;
            if (len2 == 0.0) {
                return new double[] {x1, z1};
            }
            double t = ((px - x1) * dx + (pz - z1) * dz) / len2;
            t = Math.max(0.0, Math.min(1.0, t));
            return new double[] {x1 + t * dx, z1 + t * dz};
        }

        /** Euclidean XZ distance from {@code (px, pz)} to the nearest point on this segment. */
        public double distanceTo(double px, double pz) {
            double[] c = closestPoint(px, pz);
            return Math.hypot(px - c[0], pz - c[1]);
        }
    }
}
