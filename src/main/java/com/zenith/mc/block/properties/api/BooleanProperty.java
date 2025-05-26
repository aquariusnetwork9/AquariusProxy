package com.zenith.mc.block.properties.api;

import java.util.List;
import java.util.Optional;

public final class BooleanProperty extends Property<Boolean> {
    private static final List<Boolean> VALUES = List.of(true, false);

    private BooleanProperty(String name) {
        super(name, Boolean.class);
    }

    @Override
    public List<Boolean> getPossibleValues() {
        return VALUES;
    }

    public static BooleanProperty create(String name) {
        return new BooleanProperty(name);
    }

    @Override
    public Optional<Boolean> getValue(String value) {
        return switch (value) {
            case "true" -> Optional.of(true);
            case "false" -> Optional.of(false);
            default -> Optional.empty();
        };
    }

    public String getName(Boolean value) {
        return value.toString();
    }
}

