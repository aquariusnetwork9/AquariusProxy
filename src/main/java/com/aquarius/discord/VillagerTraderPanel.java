package com.aquarius.discord;

import com.aquarius.module.impl.VillagerTrader;
import com.aquarius.util.config.Config.Client.Extra.VillagerTrader.Trade;
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
import java.util.Map;

import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;

/**
 * Discord interactive panel for {@link VillagerTrader}: enable/disable, a dropdown that toggles individual
 * configured trades on/off, a log-to-Discord toggle, and an interact-timeout modal. Adding/editing trades stays
 * on the {@code .trader add} / {@code .trader set} commands — those need the item registry + block-position
 * validation that a panel can't cleanly provide. Posted with {@code .trader panel}; shared plumbing lives in
 * {@link DiscordPanel}.
 */
public final class VillagerTraderPanel extends DiscordPanel {

    private static final String PREFIX = "vt:";
    private static final String TRADE = "vt:trade", RUN = "vt:run", STOP = "vt:stop", LOGDC = "vt:logdc",
        TIMEOUT = "vt:timeout", TIMEOUTMODAL = "vt:timeoutmodal";

    @Override protected String prefix() { return PREFIX; }

    private static String onOff(boolean b) { return b ? "ON" : "off"; }

    private static String tradeLine(String id, Trade t) {
        return id + ": [" + t.villagerProfession.name().toLowerCase() + "] " + t.inputItem1
            + (t.has2InputTrade() ? " + " + t.inputItem2 : "") + " -> " + t.outputItem + (t.enabled ? "" : " (disabled)");
    }

    @Override
    protected Embed embed() {
        var c = CONFIG.client.extra.villagerTrader;
        StringBuilder list = new StringBuilder();
        c.trades.forEach((id, t) -> list.append("• ").append(tradeLine(id, t)).append('\n'));
        return new Embed()
            .primaryColor()
            .title("Villager Trader")
            .addField("Enabled", onOff(c.enabled), true)
            .addField("Trades", String.valueOf(c.trades.size()), true)
            .addField("Log to Discord", onOff(c.logTradeStatusToDiscord), true)
            .addField("Interact timeout", c.waitForInteractTimeoutTicks + " ticks", true)
            .addField("Configured trades", c.trades.isEmpty()
                ? "(none — add with `.trader add`)" : list.toString().trim(), false);
    }

    @Override
    protected List<ActionRow> components() {
        var c = CONFIG.client.extra.villagerTrader;
        List<ActionRow> rows = new ArrayList<>();
        if (!c.trades.isEmpty()) {
            StringSelectMenu.Builder sel = StringSelectMenu.create(TRADE).setPlaceholder("Toggle a trade on/off");
            int n = 0;
            for (Map.Entry<String, Trade> en : c.trades.entrySet()) {
                if (n++ >= 25) break;
                Trade t = en.getValue();
                sel.addOption(en.getKey() + " (" + (t.enabled ? "on" : "off") + ")", en.getKey());
            }
            rows.add(ActionRow.of(sel.build()));
        }
        rows.add(ActionRow.of(
            Button.success(RUN, "🟢 Run"),
            Button.danger(STOP, "🛑 Stop"),
            Button.secondary(LOGDC, "Log to Discord: " + onOff(c.logTradeStatusToDiscord)),
            Button.secondary(TIMEOUT, "Interact timeout")
        ));
        return rows;
    }

    private Modal timeoutModal() {
        var c = CONFIG.client.extra.villagerTrader;
        TextInput t = TextInput.create("vt:ticks", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(c.waitForInteractTimeoutTicks))
            .setPlaceholder("ticks to wait for a server interaction").build();
        return Modal.create(TIMEOUTMODAL, "Interact Timeout")
            .addComponents(Label.of("Wait-for-interact timeout (ticks)", t))
            .build();
    }

    @Override
    protected boolean onButton(ButtonInteractionEvent e) {
        var c = CONFIG.client.extra.villagerTrader;
        switch (e.getComponentId()) {
            case LOGDC -> c.logTradeStatusToDiscord = !c.logTradeStatusToDiscord;
            case TIMEOUT -> { e.replyModal(timeoutModal()).queue(); return false; }
            case RUN -> {
                c.enabled = true;
                MODULE.get(VillagerTrader.class).syncEnabledFromConfig();
                e.getChannel().sendMessage("🟢 Villager Trader started (" + c.trades.size() + " trade(s)).").queue();
            }
            case STOP -> {
                c.enabled = false;
                MODULE.get(VillagerTrader.class).syncEnabledFromConfig();
            }
            default -> { return false; }
        }
        return true;
    }

    @Override
    protected boolean onSelect(StringSelectInteractionEvent e) {
        if (!TRADE.equals(e.getComponentId())) return false;
        var c = CONFIG.client.extra.villagerTrader;
        Trade t = c.trades.get(e.getValues().get(0));
        if (t == null) return false;
        t.enabled = !t.enabled;
        MODULE.get(VillagerTrader.class).onTradeListChange();
        return true;
    }

    @Override
    protected boolean onModal(ModalInteractionEvent e) {
        if (!TIMEOUTMODAL.equals(e.getModalId())) return false;
        try {
            CONFIG.client.extra.villagerTrader.waitForInteractTimeoutTicks =
                Math.max(1, Integer.parseInt(e.getValue("vt:ticks").getAsString().trim()));
        } catch (Exception ex) {
            e.reply("Invalid value — use a whole number of ticks.").setEphemeral(true).queue();
            return false;
        }
        return true;
    }
}
