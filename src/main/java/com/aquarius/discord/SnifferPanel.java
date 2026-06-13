package com.aquarius.discord;

import com.aquarius.module.impl.AquariusSniffer;
import com.aquarius.module.impl.AquariusSnifferCodec;
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
 * Discord interactive panel for the packet sniffer ({@link AquariusSniffer}; config under
 * {@code CONFIG.client.extra.aquariusMiner.sniff*}): direction + template dropdowns, enabled/live/body toggles,
 * a filter modal, and dump / clear / timed-capture buttons. Posted with {@code .aqm sniff panel}; shared plumbing
 * lives in {@link DiscordPanel}.
 */
public final class SnifferPanel extends DiscordPanel {

    private static final String PREFIX = "snf:";
    private static final String DIR = "snf:dir", TEMPLATE = "snf:template", ENABLED = "snf:enabled",
        LIVE = "snf:live", BODY = "snf:body", DUMP = "snf:dump", CLEAR = "snf:clear", CAP3 = "snf:cap3",
        CAP5 = "snf:cap5", FILTER = "snf:filter", FILTERMODAL = "snf:filtermodal", TEMPLATE_OFF = "__off__";

    @Override protected String prefix() { return PREFIX; }

    private static String onOff(boolean b) { return b ? "ON" : "off"; }

    private AquariusSniffer module() { return MODULE.get(AquariusSniffer.class); }

    @Override
    protected Embed embed() {
        var c = CONFIG.client.extra.aquariusMiner;
        return new Embed()
            .primaryColor()
            .title("Packet Sniffer")
            .addField("Enabled", onOff(c.sniffEnabled), true)
            .addField("Direction", c.sniffDir, true)
            .addField("Buffer", module().bufferSize() + " / " + c.sniffBufferLines + " lines", true)
            .addField("Live log", onOff(c.sniffLive), true)
            .addField("Packet body", onOff(c.sniffBody), true)
            .addField("Template", c.sniffTemplate.isEmpty() ? "(none)" : c.sniffTemplate, true)
            .addField("Filter", c.sniffFilter.isEmpty() ? "(none)" : "'" + c.sniffFilter + "'", false);
    }

    @Override
    protected List<ActionRow> components() {
        var c = CONFIG.client.extra.aquariusMiner;
        StringSelectMenu dir = StringSelectMenu.create(DIR)
            .setPlaceholder("Direction: " + c.sniffDir)
            .addOption("Inbound (from server)", "in")
            .addOption("Outbound (to server)", "out")
            .addOption("Both", "both")
            .build();
        StringSelectMenu.Builder tmpl = StringSelectMenu.create(TEMPLATE)
            .setPlaceholder("Template: " + (c.sniffTemplate.isEmpty() ? "(none)" : c.sniffTemplate))
            .addOption("(clear template)", TEMPLATE_OFF);
        int n = 1;
        for (String name : AquariusSnifferCodec.templateNames()) {
            if (n++ >= 25) break;
            tmpl.addOption(name, name);
        }
        return List.of(
            ActionRow.of(dir),
            ActionRow.of(tmpl.build()),
            ActionRow.of(
                Button.secondary(ENABLED, "Enabled: " + onOff(c.sniffEnabled)),
                Button.secondary(LIVE, "Live: " + onOff(c.sniffLive)),
                Button.secondary(BODY, "Body: " + onOff(c.sniffBody)),
                Button.secondary(FILTER, "Filter")
            ),
            ActionRow.of(
                Button.secondary(DUMP, "📄 Dump"),
                Button.danger(CLEAR, "🗑 Clear"),
                Button.success(CAP3, "Capture 3s"),
                Button.success(CAP5, "Capture 5s")
            )
        );
    }

    private Modal filterModal() {
        var c = CONFIG.client.extra.aquariusMiner;
        TextInput.Builder f = TextInput.create("snf:filtertext", TextInputStyle.SHORT).setRequired(false)
            .setPlaceholder("class-name substring — blank to clear");
        if (!c.sniffFilter.isEmpty()) f.setValue(c.sniffFilter);
        return Modal.create(FILTERMODAL, "Sniffer Filter")
            .addComponents(Label.of("Filter (packet class contains)", f.build()))
            .build();
    }

    @Override
    protected boolean onButton(ButtonInteractionEvent e) {
        var c = CONFIG.client.extra.aquariusMiner;
        switch (e.getComponentId()) {
            case ENABLED -> {
                c.sniffEnabled = !c.sniffEnabled;
                module().syncEnabledFromConfig();
            }
            case LIVE -> c.sniffLive = !c.sniffLive;
            case BODY -> c.sniffBody = !c.sniffBody;
            case FILTER -> { e.replyModal(filterModal()).queue(); return false; }
            case DUMP -> {
                int size = module().bufferSize();
                module().dump();
                e.reply("Dumped " + size + " buffered packet line(s) to the console.").setEphemeral(true).queue();
                return false;
            }
            case CLEAR -> module().clearBuffer();
            case CAP3 -> {
                module().startTimed(3);
                e.reply("Capturing packets for 3s (verbose) — then auto-dump to console.").setEphemeral(true).queue();
                return false;
            }
            case CAP5 -> {
                module().startTimed(5);
                e.reply("Capturing packets for 5s (verbose) — then auto-dump to console.").setEphemeral(true).queue();
                return false;
            }
            default -> { return false; }
        }
        return true;
    }

    @Override
    protected boolean onSelect(StringSelectInteractionEvent e) {
        var c = CONFIG.client.extra.aquariusMiner;
        String id = e.getComponentId();
        String v = e.getValues().get(0);
        if (DIR.equals(id)) {
            if (v.equals("in") || v.equals("out") || v.equals("both")) { c.sniffDir = v; return true; }
            return false;
        }
        if (TEMPLATE.equals(id)) {
            if (TEMPLATE_OFF.equals(v)) { c.sniffTemplate = ""; return true; }
            String resolved = AquariusSnifferCodec.resolveTemplate(v);
            if (resolved == null) {
                e.reply("Unknown template: " + v).setEphemeral(true).queue();
                return false;
            }
            c.sniffTemplate = resolved;
            return true;
        }
        return false;
    }

    @Override
    protected boolean onModal(ModalInteractionEvent e) {
        if (!FILTERMODAL.equals(e.getModalId())) return false;
        var m = e.getValue("snf:filtertext");
        CONFIG.client.extra.aquariusMiner.sniffFilter = m == null ? "" : m.getAsString().trim();
        return true;
    }
}
