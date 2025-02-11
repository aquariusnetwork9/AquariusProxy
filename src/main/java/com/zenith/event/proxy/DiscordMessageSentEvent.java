package com.zenith.event.proxy;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public record DiscordMessageSentEvent(String message, MessageReceivedEvent event) { }
