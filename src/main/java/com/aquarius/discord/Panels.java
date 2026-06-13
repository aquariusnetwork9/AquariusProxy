package com.aquarius.discord;

import java.util.List;

/**
 * Singleton registry of the interactive Discord control panels. {@link DiscordBot} subscribes each panel's
 * listeners onto the JDA event bus (one owner key per panel, so their prefix-routed handlers never collide),
 * and module commands post a panel via {@code DISCORD.openPanel(Panels.XXX)}.
 */
public final class Panels {
    private Panels() {}

    public static final TripPanel TRIP = new TripPanel();
    public static final RegearPanel REGEAR = new RegearPanel();
    public static final AquariusMinerPanel MINER = new AquariusMinerPanel();
    public static final EnchanterPanel ENCHANTER = new EnchanterPanel();
    public static final KitMakerPanel KITMAKER = new KitMakerPanel();
    public static final PearlDropPanel PEARLDROP = new PearlDropPanel();
    public static final VillagerTraderPanel TRADER = new VillagerTraderPanel();
    public static final SnifferPanel SNIFFER = new SnifferPanel();

    /** All panels, in registration order. */
    public static final List<DiscordPanel> ALL =
        List.of(TRIP, REGEAR, MINER, ENCHANTER, KITMAKER, PEARLDROP, TRADER, SNIFFER);
}
