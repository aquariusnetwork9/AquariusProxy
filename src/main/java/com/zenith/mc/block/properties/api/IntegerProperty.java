package com.zenith.mc.block.properties.api;

import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import it.unimi.dsi.fastutil.ints.IntImmutableList;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

public class IntegerProperty extends Property<Integer> {
    private final IntImmutableList values;
    private final int min;
    private final int max;

    protected IntegerProperty(String name, int min, int max) {
        super(name, Integer.class);
        if (min < 0) {
            throw new IllegalArgumentException("Min value of " + name + " must be 0 or greater");
        } else if (max <= min) {
            throw new IllegalArgumentException("Max value of " + name + " must be greater than min (" + min + ")");
        } else {
            this.min = min;
            this.max = max;
            Set<Integer> set = Sets.newHashSet();

            for (int i = min; i <= max; i++) {
                set.add(i);
            }

            this.values = IntImmutableList.toList(IntStream.range(min, max + 1));
        }
    }

    @Override
    public List<Integer> getPossibleValues() {
        return this.values;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else {
            return object instanceof IntegerProperty integerProperty && super.equals(object) && this.values.equals(integerProperty.values);
        }
    }

    @Override
    public int generateHashCode() {
        return 31 * super.generateHashCode() + this.values.hashCode();
    }

    public static IntegerProperty create(String name, int min, int max) {
        return new IntegerProperty(name, min, max);
    }

    @Override
    public Optional<Integer> getValue(String value) {
        Integer i = Ints.tryParse(value);
        if (i == null) {
            return Optional.empty();
        }
        return i >= this.min && i <= this.max ? Optional.of(i) : Optional.empty();
    }

    public String getName(Integer value) {
        return value.toString();
    }
}

