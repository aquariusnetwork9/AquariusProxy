package com.aquarius.discord;

import com.github.rfresh2.EventConsumer;
import com.aquarius.module.impl.ElytraTrip;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static com.aquarius.Globals.CONFIG;
import static com.aquarius.Globals.MODULE;
import static com.github.rfresh2.EventConsumer.of;

/**
 * Discord interactive panel for the trip planner: an embed showing the current trip config, a dimension
 * dropdown, a "Set Coordinates" button that opens a modal with typed X/Y/Z fields, highways/gear-up toggle
 * buttons, and Launch / Cancel. All controls mutate {@code CONFIG.client.extra.elytraPilot.trip*} directly
 * (the config IS the panel state) and re-render the message. Posted with {@code /fly panel}; the persistent
 * interaction handlers are wired onto {@link DiscordBot}'s JDA event bus. Owner-gated by the account-owner role.
 */
public final class TripPanel {
    private TripPanel() {}

    private static final String DIM = "trip:dim", COORDS = "trip:coords", HIGHWAYS = "trip:highways",
        GEARUP = "trip:gearup", STARTCONN = "trip:startconn", LAUNCH = "trip:launch", CANCEL = "trip:cancel",
        MODAL = "trip:coordsmodal";

    // ---------------------------------------------------------------- panel render

    static Embed embed() {
        var c = CONFIG.client.extra.elytraPilot;
        return new Embed()
            .primaryColor()
            .title("Trip Planner")
            .addField("Destination", c.tripTargetX + ", " + c.tripTargetY + ", " + c.tripTargetZ
                + (c.tripTargetIsNether ? "  (nether coords)" : ""), false)
            .addField("Dimension", c.tripTargetIsNether ? "Nether destination" : "Overworld (auto-route)", true)
            .addField("Nether leg", c.tripUseHighways ? "highways" : "open-nether", true)
            .addField("Gear-up", c.tripGearUp ? "on" : "off", true)
            .addField("Start on connect", c.tripStartOnConnect ? "on" : "off", true)
            .addField("Status", c.tripActive ? "🟢 ACTIVE" : "idle", true);
    }

    static List<ActionRow> components() {
        var c = CONFIG.client.extra.elytraPilot;
        StringSelectMenu dim = StringSelectMenu.create(DIM)
            .setPlaceholder(c.tripTargetIsNether ? "Dimension: Nether destination" : "Dimension: Overworld")
            .addOption("Overworld (direct, or auto-route via nether)", "ow")
            .addOption("Nether destination (coords are nether)", "nether")
            .build();
        return List.of(
            ActionRow.of(dim),
            ActionRow.of(
                Button.secondary(COORDS, "Set Coordinates"),
                Button.secondary(HIGHWAYS, "Highways: " + (c.tripUseHighways ? "ON" : "off")),
                Button.secondary(GEARUP, "Gear-up: " + (c.tripGearUp ? "ON" : "off")),
                Button.secondary(STARTCONN, "Start on connect: " + (c.tripStartOnConnect ? "ON" : "off"))
            ),
            ActionRow.of(
                Button.success(LAUNCH, "🚀 Launch"),
                Button.danger(CANCEL, "🛑 Cancel / Reset")
            )
        );
    }

    public static void open(MessageChannel channel) {
        channel.sendMessageEmbeds(embed().toJDAEmbed()).setComponents(components()).queue();
    }

    private static Modal coordsModal() {
        var c = CONFIG.client.extra.elytraPilot;
        TextInput x = TextInput.create("trip:x", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(c.tripTargetX)).setPlaceholder("e.g. 25000").build();
        TextInput y = TextInput.create("trip:y", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(c.tripTargetY)).setPlaceholder("landing height, e.g. 100").build();
        TextInput z = TextInput.create("trip:z", TextInputStyle.SHORT).setRequired(true)
            .setValue(String.valueOf(c.tripTargetZ)).setPlaceholder("e.g. 0").build();
        return Modal.create(MODAL, "Trip Destination")
            .addComponents(Label.of("X", x), Label.of("Y (landing height)", y), Label.of("Z", z))
            .build();
    }

    // ---------------------------------------------------------------- interaction handlers (jdaEventBus)

    public static List<EventConsumer<?>> listeners() {
        return List.of(
            of(ButtonInteractionEvent.class, TripPanel::onButton),
            of(StringSelectInteractionEvent.class, TripPanel::onSelect),
            of(ModalInteractionEvent.class, TripPanel::onModal)
        );
    }

    private static boolean owner(@Nullable Member m) {
        return m != null && m.getRoles().stream().map(ISnowflake::getId)
            .anyMatch(r -> r.equals(CONFIG.discord.accountOwnerRoleId));
    }

    private static void onButton(ButtonInteractionEvent e) {
        String id = e.getComponentId();
        if (!id.startsWith("trip:")) return;
        if (!owner(e.getMember())) { e.reply("Not authorized.").setEphemeral(true).queue(); return; }
        var c = CONFIG.client.extra.elytraPilot;
        switch (id) {
            case COORDS -> { e.replyModal(coordsModal()).queue(); return; }   // modal reply IS the response
            case HIGHWAYS -> c.tripUseHighways = !c.tripUseHighways;
            case GEARUP -> c.tripGearUp = !c.tripGearUp;
            case STARTCONN -> c.tripStartOnConnect = !c.tripStartOnConnect;
            case LAUNCH -> {
                c.tripActive = true;
                MODULE.get(ElytraTrip.class).syncEnabledFromConfig();
                e.editMessageEmbeds(embed().toJDAEmbed()).setComponents(components()).queue();
                e.getChannel().sendMessage("🚀 Trip launched to " + c.tripTargetX + ", " + c.tripTargetY + ", "
                    + c.tripTargetZ + (c.tripTargetIsNether ? " (nether)" : "")).queue();
                return;
            }
            case CANCEL -> {
                c.tripActive = false;
                MODULE.get(ElytraTrip.class).syncEnabledFromConfig();
            }
            default -> { return; }
        }
        e.editMessageEmbeds(embed().toJDAEmbed()).setComponents(components()).queue();
    }

    private static void onSelect(StringSelectInteractionEvent e) {
        if (!DIM.equals(e.getComponentId())) return;
        if (!owner(e.getMember())) { e.reply("Not authorized.").setEphemeral(true).queue(); return; }
        CONFIG.client.extra.elytraPilot.tripTargetIsNether = "nether".equals(e.getValues().get(0));
        e.editMessageEmbeds(embed().toJDAEmbed()).setComponents(components()).queue();
    }

    private static void onModal(ModalInteractionEvent e) {
        if (!MODAL.equals(e.getModalId())) return;
        if (!owner(e.getMember())) { e.reply("Not authorized.").setEphemeral(true).queue(); return; }
        var c = CONFIG.client.extra.elytraPilot;
        try {
            c.tripTargetX = Integer.parseInt(e.getValue("trip:x").getAsString().trim());
            c.tripTargetY = Integer.parseInt(e.getValue("trip:y").getAsString().trim());
            c.tripTargetZ = Integer.parseInt(e.getValue("trip:z").getAsString().trim());
        } catch (Exception ex) {
            e.reply("Invalid coordinates — use whole numbers.").setEphemeral(true).queue();
            return;
        }
        e.editMessageEmbeds(embed().toJDAEmbed()).setComponents(components()).queue();
    }
}
