package com.aquarius.feature.stash;

import com.aquarius.feature.stash.StashModel.Classification;
import com.aquarius.feature.stash.StashModel.ContentItem;
import com.aquarius.feature.stash.StashModel.KitType;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Classifies a scanned shulker's contents against the kit catalog (stashBot CONTEXT.md section 6).
 *
 * <p>Priority order: (1) <b>content signature</b> — a canonical hash of the sorted (item, count) pairs,
 * compared to {@code kit_types.signature}; this carries the load because most shulkers are unnamed; (2)
 * <b>template match</b> against {@code kit_contents} under a strictness mode; (3) <b>name/color hints</b>,
 * used only as a tiebreaker. Works purely on {@link ContentItem} lists so the modules keep all ItemStack
 * handling.
 */
public final class KitClassifier {
    private KitClassifier() {}

    public static final String ITEM_ONLY = "item_only";
    public static final String ITEM_AND_COUNT = "item_and_count";
    public static final String EXACT_COMPONENTS = "exact_components";

    /** Aggregate a flat list of items (one entry per non-empty slot) into a sorted (item -> total count) list. */
    public static List<ContentItem> aggregate(List<ContentItem> raw) {
        Map<String, Integer> totals = new TreeMap<>();
        for (ContentItem ci : raw) {
            if (ci == null) continue;
            totals.merge(normItem(ci.item()), ci.count(), Integer::sum);
        }
        List<ContentItem> out = new ArrayList<>(totals.size());
        totals.forEach((k, v) -> out.add(new ContentItem(k, v)));
        return out;
    }

    /** Canonical signature (SHA-256 hex) of an item set, count-sensitive. Input need not be pre-aggregated. */
    public static String signatureOf(List<ContentItem> contents) {
        List<ContentItem> agg = aggregate(contents);
        StringBuilder sb = new StringBuilder();
        for (ContentItem ci : agg) sb.append(ci.item()).append('x').append(ci.count()).append(';');
        return sha256Hex(sb.toString());
    }

    /**
     * Classify aggregated {@code contents} against the catalog. {@code defaultStrictness} is used for kits
     * whose own {@code match_strictness} is unset.
     */
    public static Classification classify(List<ContentItem> contents,
                                          @Nullable String customName,
                                          @Nullable String color,
                                          List<KitType> catalog,
                                          String defaultStrictness) {
        List<ContentItem> agg = aggregate(contents);
        String hash = signatureOf(agg);

        // 1) signature match (primary)
        for (KitType kt : catalog) {
            String sig = kt.signature() != null ? kt.signature() : signatureOf(kt.contents());
            if (hash.equals(sig)) return new Classification(kt.id(), "matched", 1.0, hash);
        }

        // 2) template match under each kit's strictness
        List<KitType> candidates = new ArrayList<>();
        for (KitType kt : catalog) {
            String strict = kt.matchStrictness() != null ? kt.matchStrictness() : defaultStrictness;
            if (templateMatches(agg, kt.contents(), strict)) candidates.add(kt);
        }
        if (candidates.size() == 1) return new Classification(candidates.get(0).id(), "matched", 0.9, hash);

        // 3) name/color hint as a tiebreaker for ambiguity
        if (candidates.size() > 1) {
            KitType hinted = hintPick(candidates, customName, color);
            if (hinted != null) return new Classification(hinted.id(), "matched", 0.7, hash);
            return new Classification(null, "ambiguous", 0.5, hash);
        }

        // nothing matched: a last-ditch name hint against the whole catalog
        KitType named = hintPick(catalog, customName, color);
        if (named != null) return new Classification(named.id(), "matched", 0.4, hash);
        return new Classification(null, "unknown", 0.0, hash);
    }

    private static boolean templateMatches(List<ContentItem> agg, List<ContentItem> tpl, String strictness) {
        if (tpl == null || tpl.isEmpty()) return false;
        List<ContentItem> tplAgg = aggregate(tpl);
        if (ITEM_ONLY.equalsIgnoreCase(strictness)) {
            Map<String, Integer> a = toMap(agg), b = toMap(tplAgg);
            return a.keySet().equals(b.keySet());
        }
        // item_and_count and exact_components both require equal (item -> count) multisets here
        // (exact_components NBT comparison is out of scope for the signature-based catalog).
        return toMap(agg).equals(toMap(tplAgg));
    }

    private static @Nullable KitType hintPick(List<KitType> kits, @Nullable String customName, @Nullable String color) {
        String name = customName == null ? null : customName.toLowerCase(Locale.ROOT);
        String col = color == null ? null : color.toLowerCase(Locale.ROOT);
        for (KitType kt : kits) {
            if (name != null && kt.name() != null && name.contains(kt.name().toLowerCase(Locale.ROOT))) return kt;
            if (name != null && kt.displayName() != null && name.contains(kt.displayName().toLowerCase(Locale.ROOT))) return kt;
        }
        if (col != null) {
            for (KitType kt : kits) if (kt.color() != null && col.contains(kt.color().toLowerCase(Locale.ROOT))) return kt;
        }
        return null;
    }

    private static Map<String, Integer> toMap(List<ContentItem> items) {
        Map<String, Integer> m = new HashMap<>();
        for (ContentItem ci : items) m.merge(normItem(ci.item()), ci.count(), Integer::sum);
        return m;
    }

    /** Strip a leading {@code minecraft:} namespace and lowercase, so catalog/world names compare cleanly. */
    public static String normItem(String item) {
        String s = item.toLowerCase(Locale.ROOT);
        int c = s.indexOf(':');
        return c >= 0 ? s.substring(c + 1) : s;
    }

    private static String sha256Hex(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(d.length * 2);
            for (byte b : d) hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
