package com.zenith.command.api;

import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.zenith.Globals.COMMAND;
import static com.zenith.Globals.DEFAULT_LOG;

@Getter
@Setter
public class CommandUsage {
    private final String name;
    private final CommandCategory category;
    private final String description;
    private final List<String> usageLines;
    private final List<String> aliases;

    private CommandUsage(final String name, final CommandCategory category, final String description, final List<String> usageLines, final List<String> aliases) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.usageLines = usageLines;
        this.aliases = aliases;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name;
        private CommandCategory category;
        private String description;
        private List<String> usageLines = Collections.singletonList("");
        private List<String> aliases = Collections.emptyList();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder category(CommandCategory category) {
            this.category = category;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder usageLines(List<String> usageLines) {
            this.usageLines = usageLines;
            return this;
        }

        public Builder usageLines(String... usageLines) {
            this.usageLines = List.of(usageLines);
            return this;
        }

        public Builder aliases(List<String> aliases) {
            this.aliases = aliases;
            return this;
        }

        public Builder aliases(String... aliases) {
            this.aliases = List.of(aliases);
            return this;
        }

        public CommandUsage build() {
            if (name == null) {
                throw new IllegalStateException("name cannot be null");
            }
            if (category == null) {
                throw new IllegalStateException("category cannot be null");
            }
            if (description == null) {
                throw new IllegalStateException("description cannot be null");
            }
            return new CommandUsage(name, category, description, usageLines, aliases);
        }
    }

    @Deprecated
    public static CommandUsage simple(final String name, final CommandCategory category, final String description) {
        return new CommandUsage(name, category, description, Collections.singletonList(""), Collections.emptyList());
    }

    @Deprecated
    public static CommandUsage simpleAliases(final String name, final CommandCategory category, final String description, final List<String> aliases) {
        return new CommandUsage(name, category, description, Collections.singletonList(""), aliases);
    }

    @Deprecated
    public static CommandUsage args(final String name, final CommandCategory category, final String description, final List<String> usageLines) {
        return new CommandUsage(name, category, description, usageLines, Collections.emptyList());
    }

    @Deprecated
    public static CommandUsage full(final String name, final CommandCategory category, final String description, final List<String> usageLines, final List<String> aliases) {
        return new CommandUsage(name, category, description, usageLines, aliases);
    }

    // serializes everything
    public String serialize(CommandSource commandSource) {
        var result = this.description
            + "\n**Commands**"
            + usageLines.stream()
            .map(line -> "\n" + COMMAND.getCommandPrefix(commandSource) + name + " " + line)
            .collect(Collectors.joining());
        result += "\n\n[Commands Wiki](https://link.2b2t.vc/0)";
        if (isTooLongForDiscordDescription(result)) {
            DEFAULT_LOG.debug("Full command usage too long for discord description: {}", name);
            return this.mediumSerialize(commandSource);
        }
        return result;
    }

    // serializes usage lines only
    public String mediumSerialize(CommandSource commandSource) {
        var result = "**Commands**"
            + usageLines.stream()
            .map(line -> "\n" + COMMAND.getCommandPrefix(commandSource) + name + " " + line)
            .collect(Collectors.joining());
        result += "\n\n[Commands Wiki](https://link.2b2t.vc/0)";
        if (isTooLongForDiscordDescription(result)) {
            DEFAULT_LOG.debug("Medium command usage too long for discord description: {}", name);
            return this.shortSerialize(commandSource);
        }
        return result;
    }

    // serializes aliases only
    public String shortSerialize(CommandSource commandSource) {
        String result = COMMAND.getCommandPrefix(commandSource) + this.name;
        if (!aliases.isEmpty()) {
            result += aliases.stream()
                    .collect(Collectors.joining(" / " + COMMAND.getCommandPrefix(commandSource),
                            " / " + COMMAND.getCommandPrefix(commandSource),
                            ""));
        }
        result += "\n\n[Commands Wiki](https://link.2b2t.vc/0)";
        return result;
    }

    public String shortSerializeButNoWikiFooter(CommandSource commandSource) {
        String result = COMMAND.getCommandPrefix(commandSource) + this.name;
        if (!aliases.isEmpty()) {
            result += aliases.stream()
                .collect(Collectors.joining(" / " + COMMAND.getCommandPrefix(commandSource),
                                            " / " + COMMAND.getCommandPrefix(commandSource),
                                            ""));
        }
        return result;
    }

    private boolean isTooLongForDiscordDescription(String str) {
        return str.length() > 1024;
    }
}
