package com.zenith.api.event.chat;

import net.kyori.adventure.text.Component;

public record SystemChatEvent(
    Component component,
    String message
) { }
