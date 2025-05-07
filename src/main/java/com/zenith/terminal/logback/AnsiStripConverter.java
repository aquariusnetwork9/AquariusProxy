package com.zenith.terminal.logback;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Pattern;

public class AnsiStripConverter extends MessageConverter {
    final private Pattern ANSI_PATTERN = Pattern.compile("\\e\\[[\\d;]*[^\\d;]");

    @Override
    public String convert(ILoggingEvent event) {
        final String formattedMessage = event.getFormattedMessage();
        try {
            return ANSI_PATTERN.matcher(formattedMessage).replaceAll("");
        } catch (final Exception e) {
            // fall through
        }
        return formattedMessage;
    }
}
