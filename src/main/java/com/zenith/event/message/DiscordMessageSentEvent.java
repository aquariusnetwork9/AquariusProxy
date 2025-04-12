package com.zenith.event.message;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public record DiscordMessageSentEvent(String message, MessageReceivedEvent event) { }
