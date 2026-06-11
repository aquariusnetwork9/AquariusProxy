package com.aquarius.command.impl;

import com.aquarius.command.api.Command;
import com.aquarius.command.api.CommandCategory;
import com.aquarius.command.api.CommandContext;
import com.aquarius.command.api.CommandUsage;
import com.aquarius.database.StashDatabase;
import com.aquarius.discord.Embed;
import com.aquarius.feature.stash.OrderService;
import com.aquarius.feature.stash.StashModel.KitStock;
import com.aquarius.feature.stash.StashModel.KitType;
import com.aquarius.feature.stash.StashModel.OrderSummary;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.DATABASE;

/**
 * Native (no Node bot) order intake + admin surface. Works in-game, in the terminal, and in the proxy's
 * Discord relay channel (same Brigadier command framework as every other command). Customer-facing:
 * {@code .order catalog/stock}, {@code .order <kit:qty ...>}. Admin/test: {@code .order status/cancel/paid},
 * {@code .order outgoing} to register a pickup chest. Delivery stays gated on the {@code orders:paid} signal.
 */
public class OrderCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("order")
            .category(CommandCategory.MODULE)
            .aliases("ord")
            .description("""
                Stash order intake + manifest (native, payment-gated). Place orders, view the catalog/stock,
                register outgoing chests, and check/cancel orders. Delivery only runs after a paid signal.
                """)
            .usageLines(
                "catalog | stock",
                "<kit:qty> [kit:qty ...]   (place an order; soft-holds stock, awaits payment)",
                "status <order_id> | cancel <order_id>",
                "paid <order_id> [ref]     (admin/test: mark paid + enqueue the fill)",
                "outgoing <x> <y> <z> [label]   (admin: register a pickup chest)"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("order")
            .then(literal("catalog").executes(c -> {
                StashDatabase db = DATABASE.getStashDatabase();
                if (db == null) { c.getSource().getEmbed().errorColor().title("Stash DB not enabled"); return; }
                List<KitType> kits = db.getCatalog();
                StringBuilder sb = new StringBuilder();
                for (KitType kt : kits) {
                    sb.append("• ").append(kt.name());
                    if (kt.displayName() != null) sb.append(" — ").append(kt.displayName());
                    sb.append('\n');
                }
                c.getSource().getEmbed().title("Kit catalog (" + kits.size() + ")")
                    .description(kits.isEmpty() ? "empty — seed with .ss seed" : sb.toString());
            }))
            .then(literal("stock").executes(c -> {
                StashDatabase db = DATABASE.getStashDatabase();
                if (db == null) { c.getSource().getEmbed().errorColor().title("Stash DB not enabled"); return; }
                List<KitStock> stock = db.kitStock();
                StringBuilder sb = new StringBuilder();
                for (KitStock s : stock) sb.append("• ").append(s.name()).append(": ").append(s.available())
                    .append(" available (").append(s.onHand()).append(" on hand)\n");
                c.getSource().getEmbed().title("Stock").description(sb.length() == 0 ? "no stock" : sb.toString());
            }))
            .then(literal("status").then(argument("order_id", integer(1)).executes(c -> {
                StashDatabase db = DATABASE.getStashDatabase();
                if (db == null) { c.getSource().getEmbed().errorColor().title("Stash DB not enabled"); return; }
                int id = getInteger(c, "order_id");
                OrderSummary s = db.orderSummary(id);
                if (s == null) { c.getSource().getEmbed().errorColor().title("No order #" + id); return; }
                String log = String.join("\n", db.recentLog(id, 8));
                c.getSource().getEmbed().title("Order #" + id + " — " + s.status())
                    .description((log.isEmpty() ? "" : log));
            })))
            .then(literal("cancel").then(argument("order_id", integer(1)).executes(c -> {
                StashDatabase db = DATABASE.getStashDatabase();
                if (db == null) { c.getSource().getEmbed().errorColor().title("Stash DB not enabled"); return; }
                int id = getInteger(c, "order_id");
                boolean ok = db.cancelOrder(id);
                c.getSource().getEmbed().title(ok ? "Order #" + id + " cancelled" : "Can't cancel order #" + id)
                    .description(ok ? "reservations released" : "already filling/delivered or not found");
            })))
            .then(literal("paid").then(argument("order_id", integer(1)).executes(c -> { markPaid(c, getInteger(c, "order_id"), null); })
                .then(argument("ref", greedyString()).executes(c -> { markPaid(c, getInteger(c, "order_id"), getString(c, "ref")); }))))
            .then(literal("outgoing")
                .then(argument("x", integer()).then(argument("y", integer()).then(argument("z", integer())
                    .executes(c -> { registerOutgoing(c, null); })
                    .then(argument("label", greedyString()).executes(c -> { registerOutgoing(c, getString(c, "label")); }))))))
            .then(argument("spec", greedyString()).executes(c -> { placeOrder(c); }));
    }

    private int placeOrder(com.mojang.brigadier.context.CommandContext<CommandContext> c) {
        if (!OrderService.get().isReady()) { c.getSource().getEmbed().errorColor().title("Stash system not ready (DB off?)"); return 1; }
        String spec = getString(c, "spec");
        Map<String, Integer> kits = new LinkedHashMap<>();
        for (String tok : spec.trim().split("\\s+")) {
            if (tok.isBlank()) continue;
            String name; int qty = 1;
            int colon = tok.indexOf(':');
            if (colon >= 0) {
                name = tok.substring(0, colon);
                try { qty = Integer.parseInt(tok.substring(colon + 1).trim()); } catch (NumberFormatException e) {
                    c.getSource().getEmbed().errorColor().title("Bad quantity in '" + tok + "'"); return 1;
                }
            } else name = tok;
            if (qty < 1) qty = 1;
            kits.merge(name.toLowerCase(), qty, Integer::sum);
        }
        if (kits.isEmpty()) { c.getSource().getEmbed().errorColor().title("Usage: .order <kit:qty> [kit:qty ...]"); return 1; }

        var r = OrderService.get().placeOrder(c.getSource().getSource().name(), null, kits);
        if (!r.ok()) { c.getSource().getEmbed().errorColor().title("Order failed").description(r.message()); return 1; }
        c.getSource().getEmbed()
            .title("Order #" + r.orderId() + (r.partial() ? " (partial — some kits short)" : " placed"))
            .description("Held:" + r.message()
                + "\n\nReserved for " + CONFIG.client.extra.orderFiller.softHoldMinutes
                + " min. Complete payment, then a paid signal (or `.order paid " + r.orderId() + "`) releases the fill.");
        return 1;
    }

    private int markPaid(com.mojang.brigadier.context.CommandContext<CommandContext> c, int id, String ref) {
        boolean ok = OrderService.get().markPaidAndEnqueue(id, ref);
        c.getSource().getEmbed().title(ok ? "Order #" + id + " paid → enqueued" : "Couldn't mark #" + id + " paid")
            .description(ok ? "the filler will pick it up" : "order not found or stash system off");
        return 1;
    }

    private int registerOutgoing(com.mojang.brigadier.context.CommandContext<CommandContext> c, String label) {
        StashDatabase db = DATABASE.getStashDatabase();
        if (db == null) { c.getSource().getEmbed().errorColor().title("Stash DB not enabled"); return 1; }
        int x = getInteger(c, "x"), y = getInteger(c, "y"), z = getInteger(c, "z");
        int cid = db.setOutgoingChest(CONFIG.client.extra.orderFiller.dimension, x, y, z, label);
        c.getSource().getEmbed().title("Outgoing chest registered (#" + cid + ")")
            .description("(" + x + ", " + y + ", " + z + ")" + (label != null ? " — " + label : ""));
        return 1;
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed.primaryColor()
            .addField("Catalog", ".order catalog / .order stock")
            .addField("Place", ".order pvp:5 obby:3 food:5")
            .addField("Manage", ".order status <id> / .order cancel <id>")
            .addField("Admin", ".order paid <id> [ref] / .order outgoing <x> <y> <z> [label]");
    }
}
