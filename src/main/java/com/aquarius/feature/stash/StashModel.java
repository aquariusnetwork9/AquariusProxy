package com.aquarius.feature.stash;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Plain data records shared across the stash system (scanner, filler, order service, database).
 * Kept together so the small DTOs don't sprawl across the package.
 */
public final class StashModel {
    private StashModel() {}

    /** One item line inside a shulker or a kit template: the item name (no namespace) and its count. */
    public record ContentItem(String item, int count) {}

    /** A sellable kit definition from {@code kit_types} (+ its {@code kit_contents}). */
    public record KitType(
        int id,
        String name,
        @Nullable String displayName,
        @Nullable String color,
        String matchStrictness,                  // 'item_only' | 'item_and_count' | 'exact_components'
        @Nullable String signature,              // canonical content hash (computed from contents if null)
        List<ContentItem> contents
    ) {}

    /** A row of {@code kit_stock}: on-hand and reservation-free counts for a kit. */
    public record KitStock(int kitTypeId, String name, int onHand, int available) {}

    /** A physical container row from {@code chests}. */
    public record ChestRow(
        int id, @Nullable String label, String dimension,
        int x, int y, int z, boolean isDouble, String role, boolean reachable
    ) {}

    /** A reserved shulker the filler must withdraw, joined with its chest's coords. */
    public record ReservedShulker(
        int id, int chestId, int slot,
        @Nullable Integer kitTypeId, @Nullable String kitName,
        @Nullable String customName, @Nullable String color,
        int x, int y, int z, boolean isDouble
    ) {}

    /** The outcome of classifying a scanned shulker. */
    public record Classification(@Nullable Integer kitTypeId, String classification, double confidence, String contentHash) {}

    /** One manifest line: requested vs delivered for a kit ({@code shortQty} is derived). */
    public record ManifestLine(String kitName, int requested, int filled) {
        public int shortQty() { return Math.max(0, requested - filled); }
    }

    /** Lightweight order header for {@code .status} / fill loading. */
    public record OrderSummary(
        int id, String discordUserId, @Nullable String discordChannelId, String status,
        @Nullable Integer outgoingChestId, @Nullable String paymentRef
    ) {}
}
