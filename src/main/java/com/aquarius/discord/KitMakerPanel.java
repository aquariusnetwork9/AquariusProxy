package com.aquarius.discord;

import com.aquarius.module.impl.KitMaker;
import com.aquarius.util.config.Config.Client.Extra.KitMaker.MatchMode;
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
 * Discord interactive panel for {@link KitMaker}: a match-mode dropdown (loose / smart / exact), toggle buttons
 * (ignore-enchant-levels, pause-player, auto-dc), a template-chest modal (x/y/z), a scan-settings modal (radius +
 * floor band + max kits), and Run / Stop. All controls mutate {@code CONFIG.client.extra.kitMaker.*} directly.
 * Posted with {@code .km panel}; shared plumbing lives in {@link DiscordPanel}.
 */
public final class KitMakerPanel extends DiscordPanel {

    private static final String PREFIX = "km:";
    private static final String MATCH = "km:match", ENCHLVL = "km:enchlvl", PAUSE = "km:pause", AUTODC = "km:autodc",
        RUN = "km:run", STOP = "km:stop", TEMPLATE = "km:template", SETTINGS = "km:settings",
        TEMPLATEMODAL = "km:templatemodal", SETTINGSMODAL = "km:settingsmodal";

    @Override protected String prefix() { return PREFIX; }

    private static String onOff(boolean b) { return b ? "ON" : "off"; }

    @Override
    protected Embed embed() {
        var c = CONFIG.client.extra.kitMaker;
        var module = MODULE.get(KitMaker.class);
        return new Embed()
            .primaryColor()
            .title("Kit Maker")
            .addField("Enabled", onOff(c.enabled), true)
            .addField("State", module.statusLine(), true)
            .addField("Max kits", c.maxKits == 0 ? "unlimited" : String.valueOf(c.maxKits), true)
            .addField("Template chest", "(" + c.templateChestX + ", " + c.templateChestY + ", " + c.templateChestZ + ")", false)
            .addField("Scan", "radius " + c.scanRadius + ", floor -" + c.floorBandDown + "/+" + c.floorBandUp, true)
            .addField("Match", c.matchMode + (c.matchMode == MatchMode.Smart && c.ignoreEnchantLevels ? " (ignore enchant lvls)" : ""), true)
            .addField("Safety", "player-pause " + onOff(c.pauseOnPlayer) + ", auto-dc " + onOff(c.autoDisconnect), false);
    }

    @Override
    protected List<ActionRow> components() {
        var c = CONFIG.client.extra.kitMaker;
        StringSelectMenu match = StringSelectMenu.create(MATCH)
            .setPlaceholder("Match: " + c.matchMode)
            .addOption("Loose (same item type only)", "loose")
            .addOption("Smart (ignore cosmetic name/lore/durability)", "smart")
            .addOption("Exact (all components must match)", "exact")
            .build();
        return List.of(
            ActionRow.of(match),
            ActionRow.of(
                Button.secondary(ENCHLVL, "Ignore enchant lvls: " + onOff(c.ignoreEnchantLevels)),
                Button.secondary(PAUSE, "Pause-player: " + onOff(c.pauseOnPlayer)),
                Button.secondary(AUTODC, "Auto-dc: " + onOff(c.autoDisconnect))
            ),
            ActionRow.of(
                Button.success(RUN, "📦 Run"),
                Button.danger(STOP, "🛑 Stop"),
                Button.secondary(TEMPLATE, "Template chest"),
                Button.secondary(SETTINGS, "Scan settings")
            )
        );
    }

    private Modal templateModal() {
        var c = CONFIG.client.extra.kitMaker;
        TextInput x = TextInput.create("km:x", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(c.templateChestX)).setPlaceholder("template chest X").build();
        TextInput y = TextInput.create("km:y", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(c.templateChestY)).setPlaceholder("template chest Y").build();
        TextInput z = TextInput.create("km:z", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(c.templateChestZ)).setPlaceholder("template chest Z").build();
        return Modal.create(TEMPLATEMODAL, "Template Chest")
            .addComponents(Label.of("X", x), Label.of("Y", y), Label.of("Z", z))
            .build();
    }

    private Modal settingsModal() {
        var c = CONFIG.client.extra.kitMaker;
        TextInput radius = TextInput.create("km:radius", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(c.scanRadius)).setPlaceholder("container discovery radius").build();
        TextInput down = TextInput.create("km:down", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(c.floorBandDown)).setPlaceholder("floor band below feet").build();
        TextInput up = TextInput.create("km:up", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(c.floorBandUp)).setPlaceholder("floor band above feet").build();
        TextInput max = TextInput.create("km:max", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(c.maxKits)).setPlaceholder("max kits (0 = unlimited)").build();
        return Modal.create(SETTINGSMODAL, "Kit Maker Scan Settings")
            .addComponents(Label.of("Scan radius", radius), Label.of("Floor band down", down),
                Label.of("Floor band up", up), Label.of("Max kits (0 = unlimited)", max))
            .build();
    }

    @Override
    protected boolean onButton(ButtonInteractionEvent e) {
        var c = CONFIG.client.extra.kitMaker;
        switch (e.getComponentId()) {
            case ENCHLVL -> c.ignoreEnchantLevels = !c.ignoreEnchantLevels;
            case PAUSE -> c.pauseOnPlayer = !c.pauseOnPlayer;
            case AUTODC -> c.autoDisconnect = !c.autoDisconnect;
            case TEMPLATE -> { e.replyModal(templateModal()).queue(); return false; }
            case SETTINGS -> { e.replyModal(settingsModal()).queue(); return false; }
            case RUN -> {
                c.enabled = true;
                MODULE.get(KitMaker.class).syncEnabledFromConfig();
                e.getChannel().sendMessage("📦 Kit Maker started.").queue();
            }
            case STOP -> {
                c.enabled = false;
                MODULE.get(KitMaker.class).syncEnabledFromConfig();
            }
            default -> { return false; }
        }
        return true;
    }

    @Override
    protected boolean onSelect(StringSelectInteractionEvent e) {
        if (!MATCH.equals(e.getComponentId())) return false;
        var c = CONFIG.client.extra.kitMaker;
        switch (e.getValues().get(0)) {
            case "loose" -> c.matchMode = MatchMode.Loose;
            case "smart" -> c.matchMode = MatchMode.Smart;
            case "exact" -> c.matchMode = MatchMode.Exact;
            default -> { return false; }
        }
        return true;
    }

    @Override
    protected boolean onModal(ModalInteractionEvent e) {
        var c = CONFIG.client.extra.kitMaker;
        try {
            if (TEMPLATEMODAL.equals(e.getModalId())) {
                c.templateChestX = Integer.parseInt(e.getValue("km:x").getAsString().trim());
                c.templateChestY = Integer.parseInt(e.getValue("km:y").getAsString().trim());
                c.templateChestZ = Integer.parseInt(e.getValue("km:z").getAsString().trim());
                return true;
            }
            if (SETTINGSMODAL.equals(e.getModalId())) {
                c.scanRadius = Math.max(1, Integer.parseInt(e.getValue("km:radius").getAsString().trim()));
                c.floorBandDown = Math.max(0, Integer.parseInt(e.getValue("km:down").getAsString().trim()));
                c.floorBandUp = Math.max(0, Integer.parseInt(e.getValue("km:up").getAsString().trim()));
                c.maxKits = Math.max(0, Integer.parseInt(e.getValue("km:max").getAsString().trim()));
                return true;
            }
        } catch (Exception ex) {
            e.reply("Invalid value — use whole numbers.").setEphemeral(true).queue();
            return false;
        }
        return false;
    }
}
