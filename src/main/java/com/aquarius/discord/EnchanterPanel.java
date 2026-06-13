package com.aquarius.discord;

import com.aquarius.module.impl.Enchanter;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import java.util.List;

import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;

/**
 * Discord interactive panel for {@link Enchanter}: the common anvil-station controls — XP-bottle / pause / auto-dc
 * toggles, a rescan button, a scan-settings modal (radius + Y band + max items), an XP-settings modal (buffer +
 * reserve), and Run / Stop. The variant / curse template choices stay on the {@code .enc} commands (they need the
 * valid group/family names). Posted with {@code .enc panel}; shared plumbing lives in {@link DiscordPanel}.
 */
public final class EnchanterPanel extends DiscordPanel {

    private static final String PREFIX = "enc:";
    private static final String XP = "enc:xp", PAUSE = "enc:pause", AUTODC = "enc:autodc", RESCAN = "enc:rescan",
        RUN = "enc:run", STOP = "enc:stop", SCANSET = "enc:scanset", XPSET = "enc:xpset",
        SCANMODAL = "enc:scanmodal", XPMODAL = "enc:xpmodal";

    @Override protected String prefix() { return PREFIX; }

    private static String onOff(boolean b) { return b ? "ON" : "off"; }

    @Override
    protected Embed embed() {
        var c = CONFIG.client.extra.enchanter;
        var module = MODULE.get(Enchanter.class);
        return new Embed()
            .primaryColor()
            .title("Enchanter")
            .addField("Enabled", onOff(c.enabled), true)
            .addField("State", module.statusLine(), true)
            .addField("Max items", c.maxItems == 0 ? "unlimited" : String.valueOf(c.maxItems), true)
            .addField("Layout", module.setupLine(), false)
            .addField("Scan", "radius " + c.scanRadius + ", band -" + c.bandDown + "/+" + c.bandUp, true)
            .addField("XP", c.throwXp ? "bottles on (buffer +" + c.xpBufferLevels + ", reserve " + c.xpBottleReserve + ")" : "off", true)
            .addField("Safety", "player-pause " + onOff(c.pauseOnPlayer) + ", auto-dc " + onOff(c.autoDisconnect), false);
    }

    @Override
    protected List<ActionRow> components() {
        var c = CONFIG.client.extra.enchanter;
        return List.of(
            ActionRow.of(
                Button.secondary(XP, "XP bottles: " + onOff(c.throwXp)),
                Button.secondary(PAUSE, "Pause-player: " + onOff(c.pauseOnPlayer)),
                Button.secondary(AUTODC, "Auto-dc: " + onOff(c.autoDisconnect)),
                Button.secondary(RESCAN, "🔄 Rescan")
            ),
            ActionRow.of(
                Button.success(RUN, "✨ Run"),
                Button.danger(STOP, "🛑 Stop"),
                Button.secondary(SCANSET, "Scan settings"),
                Button.secondary(XPSET, "XP settings")
            )
        );
    }

    private net.dv8tion.jda.api.modals.Modal scanModal() {
        var c = CONFIG.client.extra.enchanter;
        TextInput radius = TextInput.create("enc:radius", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(c.scanRadius)).setPlaceholder("discovery radius, 1-32").build();
        TextInput down = TextInput.create("enc:down", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(c.bandDown)).setPlaceholder("Y band below feet").build();
        TextInput up = TextInput.create("enc:up", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(c.bandUp)).setPlaceholder("Y band above feet").build();
        TextInput max = TextInput.create("enc:max", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(c.maxItems)).setPlaceholder("max items (0 = unlimited)").build();
        return net.dv8tion.jda.api.modals.Modal.create(SCANMODAL, "Enchanter Scan Settings")
            .addComponents(Label.of("Scan radius (1-32)", radius), Label.of("Band down", down),
                Label.of("Band up", up), Label.of("Max items (0 = unlimited)", max))
            .build();
    }

    private net.dv8tion.jda.api.modals.Modal xpModal() {
        var c = CONFIG.client.extra.enchanter;
        TextInput buf = TextInput.create("enc:buf", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(c.xpBufferLevels)).setPlaceholder("extra levels over each step cost").build();
        TextInput res = TextInput.create("enc:res", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(c.xpBottleReserve)).setPlaceholder("XP bottles to keep carried").build();
        return net.dv8tion.jda.api.modals.Modal.create(XPMODAL, "Enchanter XP Settings")
            .addComponents(Label.of("XP buffer levels", buf), Label.of("XP bottle reserve", res))
            .build();
    }

    @Override
    protected boolean onButton(ButtonInteractionEvent e) {
        var c = CONFIG.client.extra.enchanter;
        switch (e.getComponentId()) {
            case XP -> c.throwXp = !c.throwXp;
            case PAUSE -> c.pauseOnPlayer = !c.pauseOnPlayer;
            case AUTODC -> c.autoDisconnect = !c.autoDisconnect;
            case RESCAN -> {
                MODULE.get(Enchanter.class).requestRescan();
                e.reply(c.enabled ? "Re-scanning the station." : "Module is off — turn it on first.").setEphemeral(true).queue();
                return false;
            }
            case SCANSET -> { e.replyModal(scanModal()).queue(); return false; }
            case XPSET -> { e.replyModal(xpModal()).queue(); return false; }
            case RUN -> {
                c.enabled = true;
                MODULE.get(Enchanter.class).syncEnabledFromConfig();
                e.getChannel().sendMessage("✨ Enchanter started.").queue();
            }
            case STOP -> {
                c.enabled = false;
                MODULE.get(Enchanter.class).syncEnabledFromConfig();
            }
            default -> { return false; }
        }
        return true;
    }

    @Override
    protected boolean onModal(ModalInteractionEvent e) {
        var c = CONFIG.client.extra.enchanter;
        try {
            if (SCANMODAL.equals(e.getModalId())) {
                c.scanRadius = Math.min(32, Math.max(1, Integer.parseInt(e.getValue("enc:radius").getAsString().trim())));
                c.bandDown = Math.max(0, Integer.parseInt(e.getValue("enc:down").getAsString().trim()));
                c.bandUp = Math.max(0, Integer.parseInt(e.getValue("enc:up").getAsString().trim()));
                c.maxItems = Math.max(0, Integer.parseInt(e.getValue("enc:max").getAsString().trim()));
                return true;
            }
            if (XPMODAL.equals(e.getModalId())) {
                c.xpBufferLevels = Math.max(0, Integer.parseInt(e.getValue("enc:buf").getAsString().trim()));
                c.xpBottleReserve = Math.max(0, Integer.parseInt(e.getValue("enc:res").getAsString().trim()));
                return true;
            }
        } catch (Exception ex) {
            e.reply("Invalid value — use whole numbers.").setEphemeral(true).queue();
            return false;
        }
        return false;
    }
}
