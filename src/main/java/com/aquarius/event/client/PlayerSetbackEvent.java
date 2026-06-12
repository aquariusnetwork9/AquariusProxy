package com.aquarius.event.client;

/**
 * The server reset our position/rotation ({@code ClientboundPlayerPositionPacket}) — a teleport, rubberband,
 * or login/respawn sync. Flight modules use this to stop thrusting against the correction (Baritone's
 * {@code elytraFireworkSetbackUseDelay}): boosting into a rubberband fight is how a bot dies on the ground.
 */
public record PlayerSetbackEvent(int teleportId) { }
