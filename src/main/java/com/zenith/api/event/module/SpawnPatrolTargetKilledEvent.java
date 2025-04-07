package com.zenith.api.event.module;

import com.zenith.feature.deathmessages.DeathMessageParseResult;
import net.kyori.adventure.text.Component;
import org.geysermc.mcprotocollib.auth.GameProfile;

public record SpawnPatrolTargetKilledEvent(GameProfile profile, Component component, String message, DeathMessageParseResult deathMessageParseResult) {
}
