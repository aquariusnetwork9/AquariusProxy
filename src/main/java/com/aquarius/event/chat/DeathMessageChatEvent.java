package com.aquarius.event.chat;

import com.aquarius.feature.deathmessages.DeathMessageParseResult;
import net.kyori.adventure.text.Component;

public record DeathMessageChatEvent(
    DeathMessageParseResult deathMessage,
    Component component,
    String message
) { }
