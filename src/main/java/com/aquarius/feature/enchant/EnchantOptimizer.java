package com.aquarius.feature.enchant;

import com.aquarius.feature.enchant.EnchantCost.EnchItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the cheapest anvil combine order to fuse a base item with a set of enchanted books into one
 * item, minimizing total XP cost while keeping every single operation under the survival "Too
 * Expensive!" cap ({@link EnchantCost#TOO_EXPENSIVE}).
 *
 * <p>Minecraft's anvil cost is order-dependent because of the doubling prior-work penalty
 * ({@code 0,1,3,7,15,31,...}): applying N books one-by-one onto the item blows past 40 fast, so the
 * books must be merged into intermediates first, in an optimal binary-tree order. This is the
 * well-studied "enchant order" problem; for the small N per item (&le; ~8 leaves) an exact subset-DP
 * over all binary combine-trees is cheap and provably optimal.
 *
 * <p>Leaf 0 is the base item and may never be the sacrifice (you can't put the gear in the right
 * slot and keep it); leaves {@code 1..N} are the books. {@code dp[mask]} = the optimal plan to fuse
 * all leaves in {@code mask} into one, memoized over the {@code (N+1)}-bit subset.
 */
public final class EnchantOptimizer {
    private EnchantOptimizer() {}

    /** One physical anvil operation: put {@code target} in the left slot, {@code sacrifice} in the right. */
    public static final class Step {
        public final EnchItem target;
        public final EnchItem sacrifice;
        public final EnchItem result;
        public final int cost;       // level cost of THIS operation

        public Step(EnchItem target, EnchItem sacrifice, EnchItem result, int cost) {
            this.target = target;
            this.sacrifice = sacrifice;
            this.result = result;
            this.cost = cost;
        }
    }

    /** The ordered combine sequence (post-order of the optimal tree) plus its total cost. */
    public static final class Plan {
        public final List<Step> steps;
        public final int totalCost;
        public final boolean feasible;
        public final EnchItem result;

        Plan(List<Step> steps, int totalCost, boolean feasible, EnchItem result) {
            this.steps = steps;
            this.totalCost = totalCost;
            this.feasible = feasible;
            this.result = result;
        }
    }

    /** Memoized solution for a subset of leaves. */
    private static final class Node {
        EnchItem item;
        int cost;
        int leftMask;     // target subtree (0 for a single leaf)
        int rightMask;    // sacrifice subtree
        boolean feasible;
    }

    /**
     * Compute the optimal plan to enchant {@code base} with {@code books}. Returns an infeasible plan
     * if no order keeps every step under the 40-level cap.
     */
    public static Plan optimize(EnchItem base, List<EnchItem> books) {
        int n = books.size();
        if (n == 0) return new Plan(new ArrayList<>(), 0, true, base);

        int leaves = n + 1;                       // leaf 0 = base
        EnchItem[] leaf = new EnchItem[leaves];
        leaf[0] = base;
        for (int i = 0; i < n; i++) leaf[i + 1] = books.get(i);

        int full = (1 << leaves) - 1;
        Node[] memo = new Node[1 << leaves];
        Node root = solve(full, leaf, memo);
        if (root == null || !root.feasible) return new Plan(new ArrayList<>(), -1, false, null);

        List<Step> steps = new ArrayList<>();
        reconstruct(full, memo, steps);
        return new Plan(steps, root.cost, true, root.item);
    }

    private static Node solve(int mask, EnchItem[] leaf, Node[] memo) {
        Node cached = memo[mask];
        if (cached != null) return cached;

        Node node = new Node();
        if (Integer.bitCount(mask) == 1) {
            node.item = leaf[Integer.numberOfTrailingZeros(mask)];
            node.cost = 0;
            node.feasible = true;
            memo[mask] = node;
            return node;
        }

        node.feasible = false;
        node.cost = Integer.MAX_VALUE;
        boolean hasBase = (mask & 1) != 0;
        int lowestBit = mask & -mask;

        for (int right = (mask - 1) & mask; right > 0; right = (right - 1) & mask) {
            int left = mask ^ right;
            if (left == 0) continue;
            if (hasBase) {
                // the base (bit 0) must stay on the target side — skip splits that put it on the right
                if ((right & 1) != 0) continue;
            } else {
                // book+book: enumerate each unordered partition once (left holds the lowest bit),
                // then try both orientations explicitly since the cost is orientation-dependent
                if ((left & -left) != lowestBit) continue;
            }
            Node ln = solve(left, leaf, memo);
            Node rn = solve(right, leaf, memo);
            if (!ln.feasible || !rn.feasible) continue;

            tryCombine(node, ln, rn, left, right);          // left target, right sacrifice
            if (!hasBase) tryCombine(node, rn, ln, right, left);  // right target, left sacrifice
        }

        memo[mask] = node;
        return node;
    }

    private static void tryCombine(Node out, Node target, Node sacrifice, int targetMask, int sacMask) {
        int opCost = EnchantCost.combineCost(target.item, sacrifice.item);
        if (EnchantCost.tooExpensive(opCost)) return;        // this single op is refused by the anvil
        long total = (long) target.cost + sacrifice.cost + opCost;
        if (total < out.cost) {
            out.cost = (int) total;
            out.feasible = true;
            out.item = EnchantCost.combine(target.item, sacrifice.item);
            out.leftMask = targetMask;
            out.rightMask = sacMask;
        }
    }

    private static void reconstruct(int mask, Node[] memo, List<Step> steps) {
        Node n = memo[mask];
        if (n == null || n.leftMask == 0) return;            // a leaf — nothing to do
        reconstruct(n.leftMask, memo, steps);                // produce both inputs first
        reconstruct(n.rightMask, memo, steps);
        EnchItem target = memo[n.leftMask].item;
        EnchItem sacrifice = memo[n.rightMask].item;
        int cost = EnchantCost.combineCost(target, sacrifice);
        steps.add(new Step(target, sacrifice, EnchantCost.combine(target, sacrifice), cost));
    }
}
