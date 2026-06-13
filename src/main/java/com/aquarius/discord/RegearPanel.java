package com.aquarius.discord;

import com.aquarius.module.impl.Regear;
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
 * Discord interactive panel for {@link Regear}: an embed of the current resupply config, a match-mode dropdown
 * (name / colour / contents), toggle buttons for the gear-up + safety options, a "Set kit" modal (name + colour)
 * and a "Thresholds" modal (scan radius / ghost reach / sky clearance / relocate attempts), and Run / Stop to
 * fire or halt the one-shot cycle. All controls mutate {@code CONFIG.client.extra.regear.*} directly. Posted with
 * {@code /regear panel}; shared plumbing lives in {@link DiscordPanel}.
 */
public final class RegearPanel extends DiscordPanel {

    private static final String PREFIX = "rg:";
    private static final String MATCH = "rg:match", ARMOR = "rg:armor", ELYTRA = "rg:elytra", TOTEM = "rg:totem",
        RETURN = "rg:return", GHOST = "rg:ghost", RELOCATE = "rg:relocate", PAUSE = "rg:pause", ONCE = "rg:once",
        KIT = "rg:kit", THRESH = "rg:thresh", RUN = "rg:run", STOP = "rg:stop",
        KITMODAL = "rg:kitmodal", THRESHMODAL = "rg:threshmodal";

    @Override protected String prefix() { return PREFIX; }

    // ---------------------------------------------------------------- panel render

    private static String onOff(boolean b) { return b ? "ON" : "off"; }

    @Override
    protected Embed embed() {
        var c = CONFIG.client.extra.regear;
        var module = MODULE.get(Regear.class);
        return new Embed()
            .primaryColor()
            .title("Regear")
            .addField("Enabled", onOff(c.enabled), true)
            .addField("State", module.statusLine(), true)
            .addField("One-shot", onOff(c.disableWhenDone), true)
            .addField("Kit match", c.matchByContents ? "by contents (elytra+fireworks)"
                : c.matchByColor ? "colour: " + (c.kitShulkerColor.isBlank() ? "(unset)" : c.kitShulkerColor)
                : "name: " + c.kitShulkerName, false)
            .addField("Gear up", "armor " + onOff(c.equipArmor) + ", elytra " + onOff(c.equipElytra)
                + ", totem " + onOff(c.offhandTotem), true)
            .addField("Return shulker", onOff(c.returnShulker), true)
            .addField("Echest scan", c.echestScanRadius + "b fallback", true)
            .addField("Ghost-hand", onOff(c.ghostInteract) + " (" + (int) c.ghostReach + "b)", true)
            .addField("Self-kill relocate", onOff(c.selfKillRelocate)
                + " (sky " + c.relocateMinSkyClearance + ", max " + c.relocateMaxAttempts + ")", true)
            .addField("Pause on player", onOff(c.pauseOnPlayer) + " (" + (int) c.playerPauseRange + "b)", true);
    }

    @Override
    protected List<ActionRow> components() {
        var c = CONFIG.client.extra.regear;
        String mode = c.matchByContents ? "by contents" : c.matchByColor ? "by colour" : "by name";
        StringSelectMenu match = StringSelectMenu.create(MATCH)
            .setPlaceholder("Match kit: " + mode)
            .addOption("By name (custom/anvil name)", "name")
            .addOption("By colour (e.g. red, lime)", "color")
            .addOption("By contents (elytra + fireworks)", "contents")
            .build();
        return List.of(
            ActionRow.of(match),
            ActionRow.of(
                Button.secondary(ARMOR, "Armor: " + onOff(c.equipArmor)),
                Button.secondary(ELYTRA, "Elytra: " + onOff(c.equipElytra)),
                Button.secondary(TOTEM, "Totem: " + onOff(c.offhandTotem)),
                Button.secondary(RETURN, "Return: " + onOff(c.returnShulker))
            ),
            ActionRow.of(
                Button.secondary(GHOST, "Ghost: " + onOff(c.ghostInteract)),
                Button.secondary(RELOCATE, "Relocate: " + onOff(c.selfKillRelocate)),
                Button.secondary(PAUSE, "Pause-player: " + onOff(c.pauseOnPlayer)),
                Button.secondary(ONCE, "One-shot: " + onOff(c.disableWhenDone))
            ),
            ActionRow.of(
                Button.secondary(KIT, "Set kit (name/colour)"),
                Button.secondary(THRESH, "Thresholds")
            ),
            ActionRow.of(
                Button.success(RUN, "▶ Run regear"),
                Button.danger(STOP, "🛑 Stop")
            )
        );
    }

    private Modal kitModal() {
        var c = CONFIG.client.extra.regear;
        TextInput.Builder name = TextInput.create("rg:name", TextInputStyle.SHORT).setRequired(false)
            .setPlaceholder("custom/anvil name, e.g. regear");
        if (!c.kitShulkerName.isBlank()) name.setValue(c.kitShulkerName);
        TextInput.Builder color = TextInput.create("rg:color", TextInputStyle.SHORT).setRequired(false)
            .setPlaceholder("colour, e.g. red — blank for none");
        if (!c.kitShulkerColor.isBlank()) color.setValue(c.kitShulkerColor);
        return Modal.create(KITMODAL, "Kit Shulker")
            .addComponents(Label.of("Name", name.build()), Label.of("Colour", color.build()))
            .build();
    }

    private Modal threshModal() {
        var c = CONFIG.client.extra.regear;
        TextInput scan = TextInput.create("rg:scan", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(c.echestScanRadius)).setPlaceholder("echest fallback scan radius (blocks)").build();
        TextInput reach = TextInput.create("rg:reach", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(c.ghostReach)).setPlaceholder("ghost-hand reach (2b2t tolerates ~6)").build();
        TextInput sky = TextInput.create("rg:sky", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(c.relocateMinSkyClearance)).setPlaceholder("relocate: air blocks above head").build();
        TextInput att = TextInput.create("rg:att", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(c.relocateMaxAttempts)).setPlaceholder("relocate: max self-kills").build();
        return Modal.create(THRESHMODAL, "Regear Thresholds")
            .addComponents(Label.of("Echest scan radius (blocks)", scan), Label.of("Ghost-hand reach (blocks)", reach),
                Label.of("Relocate sky clearance (blocks)", sky), Label.of("Relocate max self-kills", att))
            .build();
    }

    // ---------------------------------------------------------------- interaction handlers

    @Override
    protected boolean onButton(ButtonInteractionEvent e) {
        var c = CONFIG.client.extra.regear;
        switch (e.getComponentId()) {
            case ARMOR -> c.equipArmor = !c.equipArmor;
            case ELYTRA -> c.equipElytra = !c.equipElytra;
            case TOTEM -> c.offhandTotem = !c.offhandTotem;
            case RETURN -> c.returnShulker = !c.returnShulker;
            case GHOST -> c.ghostInteract = !c.ghostInteract;
            case RELOCATE -> c.selfKillRelocate = !c.selfKillRelocate;
            case PAUSE -> c.pauseOnPlayer = !c.pauseOnPlayer;
            case ONCE -> c.disableWhenDone = !c.disableWhenDone;
            case KIT -> { e.replyModal(kitModal()).queue(); return false; }
            case THRESH -> { e.replyModal(threshModal()).queue(); return false; }
            case RUN -> {
                c.enabled = true;
                MODULE.get(Regear.class).syncEnabledFromConfig();
                e.getChannel().sendMessage("▶ Regear started (" + (c.matchByContents ? "by contents"
                    : c.matchByColor ? "colour " + c.kitShulkerColor : "name " + c.kitShulkerName) + ").").queue();
            }
            case STOP -> {
                c.enabled = false;
                MODULE.get(Regear.class).syncEnabledFromConfig();
            }
            default -> { return false; }
        }
        return true;
    }

    @Override
    protected boolean onSelect(StringSelectInteractionEvent e) {
        if (!MATCH.equals(e.getComponentId())) return false;
        var c = CONFIG.client.extra.regear;
        switch (e.getValues().get(0)) {
            case "name" -> { c.matchByColor = false; c.matchByContents = false; }
            case "color" -> { c.matchByColor = true; c.matchByContents = false; }
            case "contents" -> { c.matchByContents = true; c.matchByColor = false; }
            default -> { return false; }
        }
        return true;
    }

    @Override
    protected boolean onModal(ModalInteractionEvent e) {
        var c = CONFIG.client.extra.regear;
        if (KITMODAL.equals(e.getModalId())) {
            var nameM = e.getValue("rg:name");
            var colorM = e.getValue("rg:color");
            String name = nameM == null ? "" : nameM.getAsString().trim();
            String color = colorM == null ? "" : colorM.getAsString().trim().toLowerCase();
            if (!name.isBlank()) c.kitShulkerName = name;
            c.kitShulkerColor = color;   // blank clears the colour
            return true;
        }
        if (THRESHMODAL.equals(e.getModalId())) {
            try {
                c.echestScanRadius = Math.max(1, Integer.parseInt(e.getValue("rg:scan").getAsString().trim()));
                c.ghostReach = Math.max(1.0, Double.parseDouble(e.getValue("rg:reach").getAsString().trim()));
                c.relocateMinSkyClearance = Math.max(1, Integer.parseInt(e.getValue("rg:sky").getAsString().trim()));
                c.relocateMaxAttempts = Math.max(1, Integer.parseInt(e.getValue("rg:att").getAsString().trim()));
            } catch (Exception ex) {
                e.reply("Invalid threshold — use whole numbers (reach may be decimal).").setEphemeral(true).queue();
                return false;
            }
            return true;
        }
        return false;
    }
}
