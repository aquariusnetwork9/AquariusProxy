package com.zenith.mc.block.properties.api;

import com.google.common.base.MoreObjects;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public abstract class Property<T extends Comparable<T>> {
    private final Class<T> clazz;
    private final String name;
    @Nullable
    private Integer hashCode;

    protected Property(String name, Class<T> clazz) {
        this.clazz = clazz;
        this.name = name;
    }

    public Property.Value<T> value(T value) {
        return new Property.Value<>(this, value);
    }

    public Stream<Value<T>> getAllValues() {
        return this.getPossibleValues().stream().map(this::value);
    }

    public String getName() {
        return this.name;
    }

    public Class<T> getValueClass() {
        return this.clazz;
    }

    public abstract List<T> getPossibleValues();

    public abstract String getName(T value);

    public abstract Optional<T> getValue(String value);

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("name", this.name).add("clazz", this.clazz).add("values", this.getPossibleValues()).toString();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else {
            return !(object instanceof Property<?> property) ? false : this.clazz.equals(property.clazz) && this.name.equals(property.name);
        }
    }

    @Override
    public final int hashCode() {
        if (this.hashCode == null) {
            this.hashCode = this.generateHashCode();
        }

        return this.hashCode;
    }

    public int generateHashCode() {
        return 31 * this.clazz.hashCode() + this.name.hashCode();
    }

    public record Value<T extends Comparable<T>>(Property<T> property, T value) {
        public Value(Property<T> property, T value) {
            if (!property.getPossibleValues().contains(value)) {
                throw new IllegalArgumentException("Value " + value + " does not belong to property " + property);
            } else {
                this.property = property;
                this.value = value;
            }
        }

        @Override
        public String toString() {
            return this.property.getName() + "=" + this.property.getName(this.value);
        }
    }
}

