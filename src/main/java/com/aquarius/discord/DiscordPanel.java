package com.aquarius.discord;

import com.github.rfresh2.EventConsumer;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static com.aquarius.Globals.CONFIG;
import static com.github.rfresh2.EventConsumer.of;

/**
 * Base class for an interactive Discord control panel: an embed showing a module's live config, plus button /
 * dropdown / modal controls that mutate the config directly (the config IS the panel state) and re-render the
 * message in place. One concrete subclass per module ({@link TripPanel}, {@link RegearPanel}, …); each is a
 * singleton registered on {@link DiscordBot}'s JDA event bus via {@link Panels}.
 *
 * <p>Component-id discipline: every button / select / modal owned by a panel MUST use an id beginning with the
 * panel's {@link #prefix()} (e.g. {@code "rg:"}). The shared dispatchers below route an interaction to a panel
 * only when the id matches its prefix, so several panels coexist on the same bus without colliding. All controls
 * are owner-gated by the Discord account-owner role.
 *
 * <p>Handler contract: {@link #onButton}/{@link #onSelect}/{@link #onModal} mutate state and return {@code true}
 * to have the framework re-render the message afterwards. Return {@code false} when the handler has already
 * produced the interaction response itself (e.g. {@code replyModal(...)} for a button that opens a modal, or an
 * ephemeral validation error) — the framework then leaves the interaction alone.
 */
public abstract class DiscordPanel {

    /** Unique component-id prefix for this panel; MUST end with ':' (e.g. {@code "trip:"}, {@code "rg:"}). */
    protected abstract String prefix();

    /** Render the embed reflecting the current config state. */
    protected abstract Embed embed();

    /** Render the interactive control rows (≤5 rows; ≤5 buttons per row). */
    protected abstract List<ActionRow> components();

    /** Handle a button whose id starts with {@link #prefix()}. Return true to re-render the panel afterwards. */
    protected boolean onButton(ButtonInteractionEvent e) { return false; }

    /** Handle a select whose id starts with {@link #prefix()}. Return true to re-render the panel afterwards. */
    protected boolean onSelect(StringSelectInteractionEvent e) { return false; }

    /** Handle a modal whose id starts with {@link #prefix()}. Return true to re-render the panel afterwards. */
    protected boolean onModal(ModalInteractionEvent e) { return false; }

    // ---------------------------------------------------------------- shared plumbing

    /** Post a fresh copy of this panel to the channel. */
    public final void open(MessageChannel channel) {
        channel.sendMessageEmbeds(embed().toJDAEmbed()).setComponents(components()).queue();
    }

    /** Re-render the message the interaction came from, in place (this also acknowledges the interaction). */
    protected final void rerender(IMessageEditCallback e) {
        e.editMessageEmbeds(embed().toJDAEmbed()).setComponents(components()).queue();
    }

    protected static boolean owner(@Nullable Member m) {
        return m != null && m.getRoles().stream().map(ISnowflake::getId)
            .anyMatch(r -> r.equals(CONFIG.discord.accountOwnerRoleId));
    }

    /** The three persistent listeners that route matching interactions into this panel's handlers. */
    public final List<EventConsumer<?>> listeners() {
        return List.of(
            of(ButtonInteractionEvent.class, this::dispatchButton),
            of(StringSelectInteractionEvent.class, this::dispatchSelect),
            of(ModalInteractionEvent.class, this::dispatchModal)
        );
    }

    private void dispatchButton(ButtonInteractionEvent e) {
        if (e.getComponentId() == null || !e.getComponentId().startsWith(prefix())) return;
        if (!owner(e.getMember())) { e.reply("Not authorized.").setEphemeral(true).queue(); return; }
        if (onButton(e) && !e.isAcknowledged()) rerender(e);
    }

    private void dispatchSelect(StringSelectInteractionEvent e) {
        if (e.getComponentId() == null || !e.getComponentId().startsWith(prefix())) return;
        if (!owner(e.getMember())) { e.reply("Not authorized.").setEphemeral(true).queue(); return; }
        if (onSelect(e) && !e.isAcknowledged()) rerender(e);
    }

    private void dispatchModal(ModalInteractionEvent e) {
        if (e.getModalId() == null || !e.getModalId().startsWith(prefix())) return;
        if (!owner(e.getMember())) { e.reply("Not authorized.").setEphemeral(true).queue(); return; }
        if (onModal(e) && !e.isAcknowledged()) rerender(e);
    }
}
