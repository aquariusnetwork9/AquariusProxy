package com.aquarius.discord;

import com.aquarius.feature.litematica.BuildPlan;
import com.aquarius.feature.litematica.Schematic;
import com.aquarius.module.impl.LitematicaBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;

import java.util.ArrayList;
import java.util.List;

import static com.aquarius.Globals.CACHE;
import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;

/**
 * Discord interactive panel for the Litematica auto-builder: pick a schematic from the dropdown, set the build
 * origin (typed modal or "here"), toggle chest / shulker restock, and Start / Pause / Stop. All controls mutate
 * {@code CONFIG.client.extra.litematica.*} and drive {@link LitematicaBuilder}; shared plumbing lives in
 * {@link DiscordPanel}. Posted with {@code .litematica panel}.
 */
public final class LitematicaPanel extends DiscordPanel {

    private static final String PREFIX = "lite:";
    private static final String SEL = "lite:sel", ORIGIN = "lite:origin", HERE = "lite:here",
        CHESTS = "lite:chests", SHULKERS = "lite:shulkers",
        START = "lite:start", PAUSE = "lite:pause", STOP = "lite:stop",
        ORIGINMODAL = "lite:originmodal";

    @Override protected String prefix() { return PREFIX; }

    private LitematicaBuilder module() {
        return MODULE.get(LitematicaBuilder.class);
    }

    // ---------------------------------------------------------------- render

    @Override
    protected Embed embed() {
        var cfg = CONFIG.client.extra.litematica;
        LitematicaBuilder m = module();
        Schematic s = m.schematic();
        BuildPlan p = m.plan();
        Embed e = new Embed().primaryColor().title("Litematica Builder")
            .addField("Phase", m.phase().name(), true)
            .addField("Schematic", s != null ? s.name() : (cfg.schematicFile.isBlank() ? "none" : cfg.schematicFile), true)
            .addField("Size", s != null ? s.sizeX() + " × " + s.sizeY() + " × " + s.sizeZ() : "—", true)
            .addField("Origin", cfg.originSet ? cfg.originX + ", " + cfg.originY + ", " + cfg.originZ : "not set", true)
            .addField("Restock", "chests " + (cfg.restockFromChests ? "ON" : "off")
                + " · shulkers " + (cfg.restockFromShulkers ? "ON" : "off"), true);
        if (p != null) {
            e.addField("Progress", p.doneCount() + " / " + p.total(), true);
            List<Schematic.MaterialEntry> missing = p.remainingMaterials();
            if (!missing.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(6, missing.size()); i++) {
                    sb.append(missing.get(i).item()).append("  ×").append(missing.get(i).count()).append('\n');
                }
                e.addField("Missing materials", sb.toString(), false);
            }
        }
        return e;
    }

    @Override
    protected List<ActionRow> components() {
        var cfg = CONFIG.client.extra.litematica;
        List<ActionRow> rows = new ArrayList<>();
        List<String> files = module().listSchematics();
        if (!files.isEmpty()) {
            var sel = StringSelectMenu.create(SEL).setPlaceholder("Select a schematic…");
            int n = 0;
            for (String f : files) {
                if (++n > 25) break;
                if (f.equals(cfg.schematicFile)) sel.addOption(f, f, "✓ loaded");
                else sel.addOption(f, f);
            }
            rows.add(ActionRow.of(sel.build()));
        }
        rows.add(ActionRow.of(
            Button.secondary(ORIGIN, "🎯 Set Origin"),
            Button.secondary(HERE, "📍 Origin Here"),
            Button.secondary(CHESTS, "Chests: " + (cfg.restockFromChests ? "ON" : "off")),
            Button.secondary(SHULKERS, "Shulkers: " + (cfg.restockFromShulkers ? "ON" : "off"))
        ));
        rows.add(ActionRow.of(
            Button.success(START, "▶ Start"),
            Button.secondary(PAUSE, "⏸ Pause"),
            Button.danger(STOP, "🛑 Stop")
        ));
        return rows;
    }

    private Modal originModal() {
        var cfg = CONFIG.client.extra.litematica;
        TextInput x = TextInput.create("lite:x", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(cfg.originX)).setPlaceholder("e.g. 100").build();
        TextInput y = TextInput.create("lite:y", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(cfg.originY)).setPlaceholder("e.g. 64").build();
        TextInput z = TextInput.create("lite:z", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(cfg.originZ)).setPlaceholder("e.g. -200").build();
        return Modal.create(ORIGINMODAL, "Build Origin (min corner)")
            .addComponents(Label.of("X", x), Label.of("Y", y), Label.of("Z", z))
            .build();
    }

    // ---------------------------------------------------------------- interaction handlers

    @Override
    protected boolean onButton(ButtonInteractionEvent e) {
        var cfg = CONFIG.client.extra.litematica;
        switch (e.getComponentId()) {
            case ORIGIN -> { e.replyModal(originModal()).queue(); return false; }
            case HERE -> {
                cfg.originX = (int) Math.floor(CACHE.getPlayerCache().getX());
                cfg.originY = (int) Math.floor(CACHE.getPlayerCache().getY());
                cfg.originZ = (int) Math.floor(CACHE.getPlayerCache().getZ());
                cfg.originSet = true;
            }
            case CHESTS -> cfg.restockFromChests = !cfg.restockFromChests;
            case SHULKERS -> cfg.restockFromShulkers = !cfg.restockFromShulkers;
            case START -> {
                String err = module().start();
                if (err != null) { e.getChannel().sendMessage("⚠ " + err).queue(); }
                else { e.getChannel().sendMessage("▶ Litematica build started.").queue(); }
            }
            case PAUSE -> module().pause();
            case STOP -> module().stop();
            default -> { return false; }
        }
        return true;
    }

    @Override
    protected boolean onSelect(StringSelectInteractionEvent e) {
        if (SEL.equals(e.getComponentId())) {
            String file = e.getValues().get(0);
            String err = module().load(file);
            if (err != null) e.getChannel().sendMessage("⚠ Load failed: " + err).queue();
            return true;
        }
        return false;
    }

    @Override
    protected boolean onModal(ModalInteractionEvent e) {
        if (ORIGINMODAL.equals(e.getModalId())) {
            var cfg = CONFIG.client.extra.litematica;
            try {
                cfg.originX = Integer.parseInt(e.getValue("lite:x").getAsString().trim());
                cfg.originY = Integer.parseInt(e.getValue("lite:y").getAsString().trim());
                cfg.originZ = Integer.parseInt(e.getValue("lite:z").getAsString().trim());
                cfg.originSet = true;
            } catch (Exception ex) {
                e.reply("Invalid coordinates — use whole numbers.").setEphemeral(true).queue();
                return false;
            }
            return true;
        }
        return false;
    }
}
