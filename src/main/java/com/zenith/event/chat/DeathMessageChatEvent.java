package com.zenith.event.chat;

import com.zenith.feature.deathmessages.DeathMessageParseResult;
import net.kyori.adventure.text.Component;

public record DeathMessageChatEvent(
    DeathMessageParseResult deathMessage,
    Component component,
    String message
) { }
