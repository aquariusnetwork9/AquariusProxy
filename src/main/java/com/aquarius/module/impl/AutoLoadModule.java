package com.aquarius.module.impl;

import com.github.rfresh2.EventConsumer;
import com.aquarius.event.chat.WhisperChatEvent;
import com.aquarius.module.api.Module;
import com.aquarius.util.ChatUtil;

import java.util.List;
import java.util.UUID;

import static com.github.rfresh2.EventConsumer.of;
import static com.aquarius.Globals.*;

public class AutoLoadModule extends Module {
    private final PearlManager pearlManager = new PearlManager(this);

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.pearlPlus.autoLoad.enabled;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(WhisperChatEvent.class, this::onWhisper)
        );
    }

    private void onWhisper(WhisperChatEvent event) {
        if (!CONFIG.client.extra.pearlPlus.autoLoad.enabled || event.outgoing()) return;

        String rawMessage = event.message().trim();
        String msg = rawMessage.toLowerCase();
        String[] lowerParts = msg.split("\\s+");
        String[] parts = rawMessage.trim().split("\\s+");
        var sender = event.sender();
        String name = sender.getName();
        UUID uuid = sender.getProfileId();

        if (msg.equals("pearls")) {
            var playerEntry = CONFIG.client.extra.pearlPlus.players.get(uuid);
            if (playerEntry != null && !playerEntry.pearls.isEmpty()) {
                String list = pearlManager.pearlsList(uuid);
                sendClientPacketAsync(ChatUtil.getWhisperChatPacket(name, list));
            }
            return;
        }

        if (lowerParts.length > 0 && "default".equals(lowerParts[0])) {
            var playerEntry = CONFIG.client.extra.pearlPlus.players.get(uuid);
            if (playerEntry == null || playerEntry.pearls.isEmpty()) {
                info("Default request from player without pearls: " + name);
                return;
            }
            if (parts.length < 2) {
                sendClientPacketAsync(ChatUtil.getWhisperChatPacket(name, "Specify a pearl ID to set as default."));
                return;
            }
            String resolved = pearlManager.resolvePearlId(uuid, parts[1]);
            if (resolved == null) {
                sendClientPacketAsync(ChatUtil.getWhisperChatPacket(name, "Pearl not found."));
                return;
            }
            pearlManager.setDefaultPearl(uuid, resolved);
            sendClientPacketAsync(ChatUtil.getWhisperChatPacket(name, "Default pearl set to " + resolved + "."));
            return;
        }

        if (lowerParts.length > 0 && "rename".equals(lowerParts[0])) {
            var playerEntry = CONFIG.client.extra.pearlPlus.players.get(uuid);
            if (playerEntry == null || playerEntry.pearls.isEmpty()) {
                info("Rename request from player without pearls: " + name);
                return;
            }
            if (parts.length < 3) {
                sendClientPacketAsync(ChatUtil.getWhisperChatPacket(name, "Usage: rename <oldId> <newId>"));
                return;
            }
            String oldPearlId = pearlManager.resolvePearlId(uuid, parts[1]);
            if (oldPearlId == null) {
                sendClientPacketAsync(ChatUtil.getWhisperChatPacket(name, "Pearl not found."));
                return;
            }
            String newPearlId = parts[2];
            boolean exists = playerEntry.pearls.keySet().stream()
                    .anyMatch(id -> id.equalsIgnoreCase(newPearlId));
            if (exists) {
                sendClientPacketAsync(ChatUtil.getWhisperChatPacket(name, "A pearl with that id already exists."));
                return;
            }
            boolean renamed = pearlManager.renamePearl(uuid, oldPearlId, newPearlId);
            if (renamed) {
                sendClientPacketAsync(ChatUtil.getWhisperChatPacket(name, "Renamed " + oldPearlId + " to " + newPearlId + "."));
            } else {
                sendClientPacketAsync(ChatUtil.getWhisperChatPacket(name, "Unable to rename pearl."));
            }
            return;
        }

        if (!msg.startsWith("load")) return;

        // Chat-specific arg-count guard (reject extra words after the pearl id); the shared core handles the rest.
        if (!CONFIG.client.extra.pearlPlus.autoLoad.allowNoiseAfterPearl) {
            if (lowerParts.length > 2) { info("Extra arguments not allowed for " + name); return; }
        } else if (lowerParts.length > 3) {
            info("Too many arguments from " + name); return;
        }

        String pearlArg = lowerParts.length == 1 ? null : parts[1];
        PullResult result = requestPull(uuid, name, pearlArg);
        if (result.notifyChat() && result.message() != null) {
            sendClientPacketAsync(ChatUtil.getWhisperChatPacket(name, result.message()));
        }
    }

    /** Outcome of a pearl-pull request. {@code message} is a human-readable line for the requester; {@code notifyChat}
     *  is false for outcomes the in-game whisper path intentionally keeps silent (so an unauthorized stranger learns
     *  nothing) — other transports (the ProxyBridge channel / HTTP API) relay {@code message} unconditionally. */
    public record PullResult(boolean started, boolean notifyChat, String message) {}

    /**
     * Core pearl-pull logic shared by the in-game whisper path, the {@code pearlpull} command (HTTP API / terminal /
     * in-game) and the ProxyBridge plugin channel. Always <b>self-scoped</b>: it only ever loads a pearl stored under
     * {@code uuid} (the requester's own). Permission is gated by RBAC ({@code pearl.pull}) when RBAC is enabled
     * (default-deny), otherwise by the legacy PearlPlus whitelist. {@code pearlArg} null/blank loads the default pearl.
     */
    public PullResult requestPull(UUID uuid, String name, String pearlArg) {
        if (!CONFIG.client.extra.pearlPlus.autoLoad.enabled)
            return new PullResult(false, false, "Pearl loading is disabled.");

        if (PERMISSIONS.isEnabled()) {
            if (!PERMISSIONS.allows(PERMISSIONS.resolve(uuid, name), "pearl.pull")) {
                info("Denied pearl pull for {} (lacks pearl.pull)", name);
                return new PullResult(false, false, "You are not authorized to pull pearls.");
            }
        } else if (CONFIG.client.extra.pearlPlus.autoLoad.whitelistEnabled
                && !CONFIG.client.extra.pearlPlus.whitelist.containsKey(uuid)) {
            return new PullResult(false, false, "You are not whitelisted to pull pearls.");
        }

        var playerEntry = CONFIG.client.extra.pearlPlus.players.get(uuid);
        if (playerEntry == null || playerEntry.pearls.isEmpty()) {
            info("No pearls assigned to " + name);
            return new PullResult(false, false, "You have no pearls assigned.");
        }

        String requestedPearl;
        if (pearlArg == null || pearlArg.isBlank()) {
            requestedPearl = pearlManager.defaultPearlId(uuid);
        } else {
            String resolved = pearlManager.resolvePearlId(uuid, pearlArg.trim());
            requestedPearl = resolved != null ? resolved
                : (CONFIG.client.extra.pearlPlus.autoLoad.allowNoiseAfterPearl ? pearlManager.defaultPearlId(uuid) : null);
        }
        if (requestedPearl == null || !playerEntry.pearls.containsKey(requestedPearl)) {
            info("Unauthorized/unknown pearl from " + name + " (arg='" + pearlArg + "')");
            return new PullResult(false, true, "No authorized pearls found.");
        }

        var pearl = playerEntry.pearls.get(requestedPearl);
        int pearlsLeft = pearlManager.countPresentPearls(uuid) - 1;
        String feedback = pearlsLeft == 1
            ? "Loading pearl " + requestedPearl + "... Don't forget to drop a new pearl, this is your last one!"
            : "Loading pearl " + requestedPearl + "... You have " + pearlsLeft + " pearls left.";
        if (!pearlManager.isPearlPresent(pearl)) {
            feedback = "No pearl detected. Attempting to load " + requestedPearl + " anyways.";
        }

        discordAndIngameNotification(com.aquarius.discord.Embed.builder()
            .title("Pearl Pull Request")
            .addField("Player", name)
            .addField("Pearl", requestedPearl));

        pearlManager.loadPearl(pearl, name);
        return new PullResult(true, true, feedback);
    }
}
