package com.zenith.mc.block.properties.api;

import com.google.common.collect.ImmutableMap;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class EnumProperty<T extends Enum<T> & StringRepresentable> extends Property<T> {
    private final List<T> values;
    private final Map<String, T> names;

    protected EnumProperty(String name, Class<T> clazz, Collection<T> values) {
        super(name, clazz);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Trying to make empty EnumProperty '" + name + "'");
        } else {
            this.values = List.copyOf(values);
            var builder = ImmutableMap.<String, T>builder();
            for (T enumValue : values) {
                String string = enumValue.getSerializedName();
                builder.put(string, enumValue);
            }
            this.names = builder.buildOrThrow();
        }
    }

    @Override
    public List<T> getPossibleValues() {
        return this.values;
    }

    @Override
    public Optional<T> getValue(String value) {
        return Optional.ofNullable(this.names.get(value));
    }

    public String getName(T value) {
        return value.getSerializedName();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else {
            if (object instanceof EnumProperty<?> enumProperty && super.equals(object)) {
                return this.values.equals(enumProperty.values) && this.names.equals(enumProperty.names);
            }

            return false;
        }
    }

    @Override
    public int generateHashCode() {
        int i = super.generateHashCode();
        return 31 * i + this.values.hashCode();
    }

    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> clazz) {
        return create(name, clazz, enum_ -> true);
    }

    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> clazz, Predicate<T> filter) {
        return create(name, clazz, Arrays.stream(clazz.getEnumConstants()).filter(filter).collect(Collectors.toList()));
    }

    @SafeVarargs
    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> clazz, T... values) {
        return create(name, clazz, List.of(values));
    }

    public static <T extends Enum<T> & StringRepresentable> EnumProperty<T> create(String name, Class<T> clazz, List<T> values) {
        return new EnumProperty<>(name, clazz, values);
    }
}
