package com.aquarius.discord;

import com.aquarius.module.impl.PearlDrop;
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
 * Discord interactive panel for {@link PearlDrop}: scan for pearl stasis chambers, then deposit. Buttons for
 * Run / Stop / Scan / Drop-all-empty, a "Drop near" modal (x/y/z/count), a settings modal (count + scan radius +
 * depth + Y range), and a close-trapdoor toggle. The index-pick deposit ({@code /pd pick 1 3 4}) stays on the
 * command. Posted with {@code /pd panel}; shared plumbing lives in {@link DiscordPanel}.
 */
public final class PearlDropPanel extends DiscordPanel {

    private static final String PREFIX = "pd:";
    private static final String RUN = "pd:run", STOP = "pd:stop", SCAN = "pd:scan", ALL = "pd:all",
        DROPNEAR = "pd:dropnear", SETTINGS = "pd:settings", CLOSE = "pd:close",
        DROPMODAL = "pd:dropmodal", SETTINGSMODAL = "pd:settingsmodal";

    @Override protected String prefix() { return PREFIX; }

    private static String onOff(boolean b) { return b ? "ON" : "off"; }

    private PearlDrop module() { return MODULE.get(PearlDrop.class); }

    @Override
    protected Embed embed() {
        var c = CONFIG.client.extra.pearlDrop;
        return new Embed()
            .primaryColor()
            .title("Pearl Drop")
            .addField("Enabled", onOff(c.enabled), true)
            .addField("Last scan", module().getLastScan().size() + " chamber(s)", true)
            .addField("Pearls/chamber", String.valueOf(c.pearlsPerChamber), true)
            .addField("Scan", "radius " + c.scanRadius + ", depth ≥" + c.minWaterDepth + ", Y ±" + c.scanYRange, false)
            .addField("Close trapdoor", onOff(c.closeOpenTrapdoor), true)
            .addField("Throw spacing", c.throwSpacingTicks + " ticks", true);
    }

    @Override
    protected List<ActionRow> components() {
        var c = CONFIG.client.extra.pearlDrop;
        return List.of(
            ActionRow.of(
                Button.success(RUN, "▶ Run"),
                Button.danger(STOP, "🛑 Stop"),
                Button.secondary(SCAN, "🔍 Scan"),
                Button.secondary(ALL, "Drop all empty")
            ),
            ActionRow.of(
                Button.secondary(DROPNEAR, "Drop near coords"),
                Button.secondary(SETTINGS, "Settings"),
                Button.secondary(CLOSE, "Close trapdoor: " + onOff(c.closeOpenTrapdoor))
            )
        );
    }

    private Modal dropModal() {
        var c = CONFIG.client.extra.pearlDrop;
        TextInput x = TextInput.create("pd:x", TextInputStyle.SHORT).setRequired(true).setPlaceholder("chamber X").build();
        TextInput y = TextInput.create("pd:y", TextInputStyle.SHORT).setRequired(true).setPlaceholder("chamber Y").build();
        TextInput z = TextInput.create("pd:z", TextInputStyle.SHORT).setRequired(true).setPlaceholder("chamber Z").build();
        TextInput n = TextInput.create("pd:n", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(c.pearlsPerChamber)).setPlaceholder("pearls to drop").build();
        return Modal.create(DROPMODAL, "Drop Near Coords")
            .addComponents(Label.of("X", x), Label.of("Y", y), Label.of("Z", z), Label.of("Count", n))
            .build();
    }

    private Modal settingsModal() {
        var c = CONFIG.client.extra.pearlDrop;
        TextInput count = TextInput.create("pd:count", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(c.pearlsPerChamber)).setPlaceholder("pearls per chamber").build();
        TextInput radius = TextInput.create("pd:radius", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(c.scanRadius)).setPlaceholder("scan horizontal radius").build();
        TextInput depth = TextInput.create("pd:depth", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(c.minWaterDepth)).setPlaceholder("min water depth").build();
        TextInput yr = TextInput.create("pd:yr", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(c.scanYRange)).setPlaceholder("scan Y half-range").build();
        return Modal.create(SETTINGSMODAL, "Pearl Drop Settings")
            .addComponents(Label.of("Pearls per chamber", count), Label.of("Scan radius", radius),
                Label.of("Min water depth", depth), Label.of("Scan Y range (±)", yr))
            .build();
    }

    @Override
    protected boolean onButton(ButtonInteractionEvent e) {
        var c = CONFIG.client.extra.pearlDrop;
        switch (e.getComponentId()) {
            case CLOSE -> c.closeOpenTrapdoor = !c.closeOpenTrapdoor;
            case DROPNEAR -> { e.replyModal(dropModal()).queue(); return false; }
            case SETTINGS -> { e.replyModal(settingsModal()).queue(); return false; }
            case SCAN -> {
                int found = module().scan(c.scanRadius).size();
                e.getChannel().sendMessage("🔍 Pearl Drop scan — " + found + " chamber(s) in loaded chunks.").queue();
            }
            case ALL -> {
                c.enabled = true;
                module().syncEnabledFromConfig();
                int n = module().dropAllEmpty(c.pearlsPerChamber);
                e.getChannel().sendMessage(n == 0
                    ? "Pearl Drop: no empty, reachable chambers in the last scan — run Scan first."
                    : "Pearl Drop: queued " + n + " chamber(s) for deposit.").queue();
            }
            case RUN -> {
                c.enabled = true;
                module().syncEnabledFromConfig();
            }
            case STOP -> {
                module().stopAll();
                c.enabled = false;
                module().syncEnabledFromConfig();
            }
            default -> { return false; }
        }
        return true;
    }

    @Override
    protected boolean onModal(ModalInteractionEvent e) {
        var c = CONFIG.client.extra.pearlDrop;
        if (DROPMODAL.equals(e.getModalId())) {
            int x, y, z, n;
            try {
                x = Integer.parseInt(e.getValue("pd:x").getAsString().trim());
                y = Integer.parseInt(e.getValue("pd:y").getAsString().trim());
                z = Integer.parseInt(e.getValue("pd:z").getAsString().trim());
                n = Math.max(1, Integer.parseInt(e.getValue("pd:n").getAsString().trim()));
            } catch (Exception ex) {
                e.reply("Invalid coordinates — use whole numbers.").setEphemeral(true).queue();
                return false;
            }
            c.enabled = true;
            module().syncEnabledFromConfig();
            boolean started = module().beginDepositNear(x, y, z, n);
            e.getChannel().sendMessage(started
                ? "Pearl Drop: depositing " + n + " pearl(s) near " + x + ", " + y + ", " + z + "."
                : "Pearl Drop: no unoccupied chamber within tolerance of " + x + ", " + y + ", " + z + ".").queue();
            return true;
        }
        if (SETTINGSMODAL.equals(e.getModalId())) {
            try {
                c.pearlsPerChamber = Math.max(1, Integer.parseInt(e.getValue("pd:count").getAsString().trim()));
                c.scanRadius = Math.max(1, Integer.parseInt(e.getValue("pd:radius").getAsString().trim()));
                c.minWaterDepth = Math.max(1, Integer.parseInt(e.getValue("pd:depth").getAsString().trim()));
                c.scanYRange = Math.max(0, Integer.parseInt(e.getValue("pd:yr").getAsString().trim()));
            } catch (Exception ex) {
                e.reply("Invalid value — use whole numbers.").setEphemeral(true).queue();
                return false;
            }
            return true;
        }
        return false;
    }
}
