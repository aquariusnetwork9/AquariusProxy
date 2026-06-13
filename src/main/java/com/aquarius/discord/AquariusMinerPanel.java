package com.aquarius.discord;

import com.aquarius.module.impl.AquariusMiner;
import com.aquarius.util.config.Config.Client.Extra.AquariusMiner.AreaMode;
import com.aquarius.util.config.Config.Client.Extra.AquariusMiner.MineMode;
import com.aquarius.util.config.Config.Client.Extra.AquariusMiner.OreTool;
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

import java.util.List;

import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;

/**
 * Discord interactive panel for {@link AquariusMiner}: the day-to-day quarry controls — a mode+tool dropdown
 * (strip-mine / OreSearch-Fortune / OreSearch-Silk), an area-mode dropdown, toggle buttons for the common
 * behaviour/safety options, a "Bounds" modal (Y band + chunk dimensions), Scan, and Run / Stop. All controls
 * mutate {@code CONFIG.client.extra.aquariusMiner.*} directly. The deep config (ore-target list, keep list,
 * deposit/supply chest coords, packet sniffer, fine numerics) stays on the {@code .aqm} commands. Posted with
 * {@code .aqm panel}; shared plumbing lives in {@link DiscordPanel}.
 */
public final class AquariusMinerPanel extends DiscordPanel {

    private static final String PREFIX = "aqm:";
    private static final String MODE = "aqm:mode", AREA = "aqm:area",
        LEGIT = "aqm:legit", SPRINT = "aqm:sprint", CAVE = "aqm:cave", PAUSE = "aqm:pause",
        RESTOCK = "aqm:restock", FOOD = "aqm:food", DEPOSIT = "aqm:deposit", FULLSTACK = "aqm:fullstack",
        RUN = "aqm:run", STOP = "aqm:stop", BOUNDS = "aqm:bounds", SCAN = "aqm:scan",
        BOUNDSMODAL = "aqm:boundsmodal";

    @Override protected String prefix() { return PREFIX; }

    // ---------------------------------------------------------------- panel render

    private static String onOff(boolean b) { return b ? "ON" : "off"; }

    private static String modeStr() {
        var m = CONFIG.client.extra.aquariusMiner;
        return m.mineMode == MineMode.OreSearch
            ? "OreSearch (" + (m.oreTool == OreTool.SilkTouch ? "silk" : "fortune") + ")"
            : "Strip-mine";
    }

    private static String areaStr() {
        var m = CONFIG.client.extra.aquariusMiner;
        return switch (m.areaMode) {
            case Unlimited -> "Unlimited";
            case ChunksFromStart -> m.areaWidthChunks + "x" + m.areaLengthChunks + " chunks (" + m.areaAnchor + ")";
            case Corners -> "(" + m.corner1X + "," + m.corner1Z + ")->(" + m.corner2X + "," + m.corner2Z + ")";
            case LoadedAtStart -> "loaded-at-start";
        };
    }

    @Override
    protected Embed embed() {
        var m = CONFIG.client.extra.aquariusMiner;
        var module = MODULE.get(AquariusMiner.class);
        return new Embed()
            .primaryColor()
            .title("Aquarius Miner")
            .addField("Enabled", onOff(m.enabled), true)
            .addField("State", module.statusLine(), true)
            .addField("Mode", modeStr(), true)
            .addField("Y band", m.minY + " .. " + m.maxY, true)
            .addField("Area", areaStr(), true)
            .addField("Mining", (m.legitMine ? "legit" : "fast") + ", sprint " + onOff(m.sprint)
                + ", reach " + String.format("%.1f", m.miningReach), false)
            .addField("Storage", (m.requireFullStacks ? "full-stacks" : "margin " + m.freeSlotsBeforeFull)
                + ", echest min-Y " + m.minEchestY, true)
            .addField("Restock", onOff(m.restockTools) + " / food " + onOff(m.restockFood), true)
            .addField("Deposit", onOff(m.depositToChests) + " (" + m.depositChests.size() + " chests)", true)
            .addField("Safety", "cave " + onOff(m.caveHandling) + ", player-pause " + onOff(m.pauseOnPlayer)
                + ", auto-dc " + onOff(m.autoDisconnect), false);
    }

    @Override
    protected List<ActionRow> components() {
        var m = CONFIG.client.extra.aquariusMiner;
        StringSelectMenu mode = StringSelectMenu.create(MODE)
            .setPlaceholder("Mode: " + modeStr())
            .addOption("Strip-mine (clear the whole area)", "strip")
            .addOption("OreSearch — Fortune (break ore, keep drops)", "ore_fortune")
            .addOption("OreSearch — Silk Touch (keep the ore block)", "ore_silk")
            .build();
        StringSelectMenu area = StringSelectMenu.create(AREA)
            .setPlaceholder("Area: " + areaStr())
            .addOption("Unlimited (spiral out forever)", "unlimited")
            .addOption("Chunks from start (set size in Bounds)", "chunks")
            .addOption("Loaded chunks at start (mine out, then stop)", "loaded")
            .addOption("Corners (set via .aqm here / corners)", "corners")
            .build();
        return List.of(
            ActionRow.of(mode),
            ActionRow.of(area),
            ActionRow.of(
                Button.secondary(LEGIT, "Legit: " + onOff(m.legitMine)),
                Button.secondary(SPRINT, "Sprint: " + onOff(m.sprint)),
                Button.secondary(CAVE, "Cave: " + onOff(m.caveHandling)),
                Button.secondary(PAUSE, "Pause-player: " + onOff(m.pauseOnPlayer))
            ),
            ActionRow.of(
                Button.secondary(RESTOCK, "Restock: " + onOff(m.restockTools)),
                Button.secondary(FOOD, "Food: " + onOff(m.restockFood)),
                Button.secondary(DEPOSIT, "Deposit: " + onOff(m.depositToChests)),
                Button.secondary(FULLSTACK, "Full-stacks: " + onOff(m.requireFullStacks))
            ),
            ActionRow.of(
                Button.success(RUN, "⛏ Run"),
                Button.danger(STOP, "🛑 Stop"),
                Button.secondary(BOUNDS, "Bounds (Y + size)"),
                Button.secondary(SCAN, "📊 Scan")
            )
        );
    }

    private Modal boundsModal() {
        var m = CONFIG.client.extra.aquariusMiner;
        TextInput minY = TextInput.create("aqm:miny", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(m.minY)).setPlaceholder("lowest Y to mine, e.g. -59").build();
        TextInput maxY = TextInput.create("aqm:maxy", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(m.maxY)).setPlaceholder("highest Y to mine, e.g. 60").build();
        TextInput w = TextInput.create("aqm:w", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(m.areaWidthChunks)).setPlaceholder("area width in chunks").build();
        TextInput l = TextInput.create("aqm:l", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(m.areaLengthChunks)).setPlaceholder("area length in chunks").build();
        return Modal.create(BOUNDSMODAL, "Mining Bounds")
            .addComponents(Label.of("Min Y", minY), Label.of("Max Y", maxY),
                Label.of("Area width (chunks)", w), Label.of("Area length (chunks)", l))
            .build();
    }

    // ---------------------------------------------------------------- interaction handlers

    @Override
    protected boolean onButton(ButtonInteractionEvent e) {
        var m = CONFIG.client.extra.aquariusMiner;
        switch (e.getComponentId()) {
            case LEGIT -> m.legitMine = !m.legitMine;
            case SPRINT -> m.sprint = !m.sprint;
            case CAVE -> m.caveHandling = !m.caveHandling;
            case PAUSE -> m.pauseOnPlayer = !m.pauseOnPlayer;
            case RESTOCK -> m.restockTools = !m.restockTools;
            case FOOD -> m.restockFood = !m.restockFood;
            case DEPOSIT -> m.depositToChests = !m.depositToChests;
            case FULLSTACK -> m.requireFullStacks = !m.requireFullStacks;
            case BOUNDS -> { e.replyModal(boundsModal()).queue(); return false; }
            case SCAN -> {
                MODULE.get(AquariusMiner.class).printScan();
                e.reply("Resource scan printed to the console + in-game alert.").setEphemeral(true).queue();
                return false;
            }
            case RUN -> {
                m.enabled = true;
                MODULE.get(AquariusMiner.class).syncEnabledFromConfig();
                e.getChannel().sendMessage("⛏ Aquarius Miner started — " + modeStr() + ", area " + areaStr()
                    + ", Y " + m.minY + ".." + m.maxY + ".").queue();
            }
            case STOP -> {
                m.enabled = false;
                MODULE.get(AquariusMiner.class).syncEnabledFromConfig();
            }
            default -> { return false; }
        }
        return true;
    }

    @Override
    protected boolean onSelect(StringSelectInteractionEvent e) {
        var m = CONFIG.client.extra.aquariusMiner;
        String id = e.getComponentId();
        String v = e.getValues().get(0);
        if (MODE.equals(id)) {
            switch (v) {
                case "strip" -> m.mineMode = MineMode.AreaClear;
                case "ore_fortune" -> { m.mineMode = MineMode.OreSearch; m.oreTool = OreTool.Fortune; }
                case "ore_silk" -> { m.mineMode = MineMode.OreSearch; m.oreTool = OreTool.SilkTouch; }
                default -> { return false; }
            }
            MODULE.get(AquariusMiner.class).syncOreConfig();
            return true;
        }
        if (AREA.equals(id)) {
            switch (v) {
                case "unlimited" -> m.areaMode = AreaMode.Unlimited;
                case "chunks" -> m.areaMode = AreaMode.ChunksFromStart;
                case "loaded" -> m.areaMode = AreaMode.LoadedAtStart;
                case "corners" -> m.areaMode = AreaMode.Corners;
                default -> { return false; }
            }
            if (m.enabled) MODULE.get(AquariusMiner.class).requestReanchor();   // re-anchor live to the new area
            return true;
        }
        return false;
    }

    @Override
    protected boolean onModal(ModalInteractionEvent e) {
        if (!BOUNDSMODAL.equals(e.getModalId())) return false;
        var m = CONFIG.client.extra.aquariusMiner;
        int minY, maxY, w, l;
        try {
            minY = Integer.parseInt(e.getValue("aqm:miny").getAsString().trim());
            maxY = Integer.parseInt(e.getValue("aqm:maxy").getAsString().trim());
            w = Integer.parseInt(e.getValue("aqm:w").getAsString().trim());
            l = Integer.parseInt(e.getValue("aqm:l").getAsString().trim());
        } catch (Exception ex) {
            e.reply("Invalid bounds — use whole numbers.").setEphemeral(true).queue();
            return false;
        }
        if (minY > maxY) {
            e.reply("Min Y must be ≤ Max Y.").setEphemeral(true).queue();
            return false;
        }
        m.minY = minY; m.maxY = maxY;
        m.areaWidthChunks = Math.max(1, w); m.areaLengthChunks = Math.max(1, l);
        if (m.enabled && m.areaMode == AreaMode.ChunksFromStart) MODULE.get(AquariusMiner.class).requestReanchor();
        return true;
    }
}
