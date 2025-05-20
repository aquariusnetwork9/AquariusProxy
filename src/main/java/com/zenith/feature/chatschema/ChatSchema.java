package com.zenith.feature.chatschema;

public record ChatSchema(
    String publicChat,
    String whisperInbound,
    String whisperOutbound
) {
    public static final ChatSchema DEFAULT_SCHEMA = new ChatSchema(
        "<$s> $m",
        "$s whispers: $m",
        "to $r: $m"
    );
}
