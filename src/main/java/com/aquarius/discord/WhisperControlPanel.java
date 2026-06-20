package com.aquarius.discord;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;

import java.util.List;

import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;

/**
 * Discord interactive panel for {@link com.aquarius.module.impl.WhisperControl}: toggle the handler + follow-killaura,
 * set the follow radius / come threshold, and set the patrol + mine presets that the {@code patrol} / {@code mine}
 * whisper verbs use. All controls mutate {@code CONFIG.client.extra.whisperControl.*} directly. Posted with {@code wc panel}.
 */
public final class WhisperControlPanel extends DiscordPanel {

    private static final String PREFIX = "wc:";
    private static final String TOGGLE = "wc:toggle", KILLAURA = "wc:killaura", CHESTSWAP = "wc:chestswap",
        FOLLOWR = "wc:followr", COMET = "wc:comet", PATROL = "wc:patrol", MINE = "wc:mine",
        FRMODAL = "wc:frmodal", CTMODAL = "wc:ctmodal", PMODAL = "wc:pmodal", MMODAL = "wc:mmodal";

    @Override protected String prefix() { return PREFIX; }

    @Override
    protected Embed embed() {
        var wc = CONFIG.client.extra.whisperControl;
        return new Embed()
            .primaryColor()
            .title("Whisper Control")
            .description("Authorized players whisper the bot: `protect`, `come`, `goto`, `patrol`, `mine`, `stop`.")
            .addField("Handler", wc.enabled ? "🟢 ON" : "off", true)
            .addField("Protect", "radius " + wc.followRadius + (wc.followEnablesKillAura ? " + killaura" : "")
                + (wc.protectSwapToChestplate ? " + chestplate" : ""), true)
            .addField("Come / Goto", "walk ≤ " + wc.comeFlyThreshold + "b, else fly", true)
            .addField("Patrol preset", wc.patrolConfigured
                ? wc.patrolCenterX + ", " + wc.patrolCenterY + ", " + wc.patrolCenterZ + "  (range " + wc.patrolRange + ")"
                : "⚠ not set", false)
            .addField("Mine preset", wc.mineConfigured
                ? wc.mineCorner1X + ", " + wc.mineCorner1Z + " → " + wc.mineCorner2X + ", " + wc.mineCorner2Z + "  (y " + wc.mineMinY + ".." + wc.mineMaxY + ")"
                : "⚠ not set", false);
    }

    @Override
    protected List<ActionRow> components() {
        var wc = CONFIG.client.extra.whisperControl;
        return List.of(
            ActionRow.of(
                Button.secondary(TOGGLE, "Handler: " + (wc.enabled ? "ON" : "off")),
                Button.secondary(KILLAURA, "Protect killaura: " + (wc.followEnablesKillAura ? "ON" : "off")),
                Button.secondary(CHESTSWAP, "Chestplate swap: " + (wc.protectSwapToChestplate ? "ON" : "off"))
            ),
            ActionRow.of(
                Button.secondary(FOLLOWR, "Protect radius"),
                Button.secondary(COMET, "Come/goto threshold")
            ),
            ActionRow.of(
                Button.primary(PATROL, "🛡 Set patrol area"),
                Button.primary(MINE, "⛏ Set mine area")
            )
        );
    }

    // ---------------------------------------------------------------- modals

    private Modal followRadiusModal() {
        TextInput r = TextInput.create("wc:frval", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(CONFIG.client.extra.whisperControl.followRadius))
            .setPlaceholder("blocks the bot ranges from you, e.g. 32").build();
        return Modal.create(FRMODAL, "Follow radius").addComponents(Label.of("Radius (blocks)", r)).build();
    }

    private Modal comeThresholdModal() {
        TextInput r = TextInput.create("wc:ctval", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(CONFIG.client.extra.whisperControl.comeFlyThreshold))
            .setPlaceholder("walk within this, fly beyond it, e.g. 200").build();
        return Modal.create(CTMODAL, "Come fly-threshold").addComponents(Label.of("Threshold (blocks)", r)).build();
    }

    private Modal patrolModal() {
        var wc = CONFIG.client.extra.whisperControl;
        TextInput center = TextInput.create("wc:pcenter", TextInputStyle.SHORT).setRequired(true)
            .setValue(wc.patrolCenterX + " " + wc.patrolCenterY + " " + wc.patrolCenterZ)
            .setPlaceholder("x y z, e.g. 0 64 0").build();
        TextInput range = TextInput.create("wc:prange", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(wc.patrolRange)).setPlaceholder("patrol range in blocks, e.g. 64").build();
        return Modal.create(PMODAL, "Patrol area")
            .addComponents(Label.of("Center (x y z)", center), Label.of("Range (blocks)", range)).build();
    }

    private Modal mineModal() {
        var wc = CONFIG.client.extra.whisperControl;
        TextInput c1 = TextInput.create("wc:mc1", TextInputStyle.SHORT).setRequired(true)
            .setValue(wc.mineCorner1X + " " + wc.mineCorner1Z).setPlaceholder("x z, e.g. -100 -100").build();
        TextInput c2 = TextInput.create("wc:mc2", TextInputStyle.SHORT).setRequired(true)
            .setValue(wc.mineCorner2X + " " + wc.mineCorner2Z).setPlaceholder("x z, e.g. 100 100").build();
        TextInput yr = TextInput.create("wc:myr", TextInputStyle.SHORT).setRequired(true)
            .setValue(wc.mineMinY + " " + wc.mineMaxY).setPlaceholder("minY maxY, e.g. -59 -50").build();
        return Modal.create(MMODAL, "Mine box")
            .addComponents(Label.of("Corner 1 (x z)", c1), Label.of("Corner 2 (x z)", c2), Label.of("Y range (minY maxY)", yr)).build();
    }

    // ---------------------------------------------------------------- handlers

    @Override
    protected boolean onButton(ButtonInteractionEvent e) {
        var wc = CONFIG.client.extra.whisperControl;
        switch (e.getComponentId()) {
            case TOGGLE -> {
                wc.enabled = !wc.enabled;
                MODULE.get(com.aquarius.module.impl.WhisperControl.class).syncEnabledFromConfig();
            }
            case KILLAURA -> wc.followEnablesKillAura = !wc.followEnablesKillAura;
            case CHESTSWAP -> wc.protectSwapToChestplate = !wc.protectSwapToChestplate;
            case FOLLOWR -> { e.replyModal(followRadiusModal()).queue(); return false; }
            case COMET -> { e.replyModal(comeThresholdModal()).queue(); return false; }
            case PATROL -> { e.replyModal(patrolModal()).queue(); return false; }
            case MINE -> { e.replyModal(mineModal()).queue(); return false; }
            default -> { return false; }
        }
        return true;
    }

    @Override
    protected boolean onModal(ModalInteractionEvent e) {
        var wc = CONFIG.client.extra.whisperControl;
        try {
            switch (e.getModalId()) {
                case FRMODAL -> {
                    int r = Integer.parseInt(e.getValue("wc:frval").getAsString().trim());
                    if (r < 1) throw new NumberFormatException();
                    wc.followRadius = r;
                }
                case CTMODAL -> {
                    int t = Integer.parseInt(e.getValue("wc:ctval").getAsString().trim());
                    if (t < 0) throw new NumberFormatException();
                    wc.comeFlyThreshold = t;
                }
                case PMODAL -> {
                    int[] c = ints(e.getValue("wc:pcenter").getAsString(), 3);
                    int r = Integer.parseInt(e.getValue("wc:prange").getAsString().trim());
                    if (c == null || r < 1) throw new NumberFormatException();
                    wc.patrolCenterX = c[0]; wc.patrolCenterY = c[1]; wc.patrolCenterZ = c[2];
                    wc.patrolRange = r; wc.patrolConfigured = true;
                }
                case MMODAL -> {
                    int[] c1 = ints(e.getValue("wc:mc1").getAsString(), 2);
                    int[] c2 = ints(e.getValue("wc:mc2").getAsString(), 2);
                    int[] yr = ints(e.getValue("wc:myr").getAsString(), 2);
                    if (c1 == null || c2 == null || yr == null) throw new NumberFormatException();
                    wc.mineCorner1X = c1[0]; wc.mineCorner1Z = c1[1];
                    wc.mineCorner2X = c2[0]; wc.mineCorner2Z = c2[1];
                    wc.mineMinY = yr[0]; wc.mineMaxY = yr[1]; wc.mineConfigured = true;
                }
                default -> { return false; }
            }
        } catch (Exception ex) {
            e.reply("Invalid input — use whole numbers in the shown format.").setEphemeral(true).queue();
            return false;
        }
        return true;
    }

    /** Parse exactly {@code n} whitespace-separated whole numbers, or null. */
    private static int[] ints(String s, int n) {
        String[] t = s.trim().split("\\s+");
        if (t.length != n) return null;
        int[] out = new int[n];
        try { for (int i = 0; i < n; i++) out[i] = Integer.parseInt(t[i]); }
        catch (NumberFormatException nf) { return null; }
        return out;
    }
}
