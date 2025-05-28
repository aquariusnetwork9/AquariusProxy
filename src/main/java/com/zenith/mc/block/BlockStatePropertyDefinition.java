package com.zenith.mc.block;

import com.google.common.collect.ImmutableSortedMap;
import com.zenith.mc.block.properties.api.Property;
import com.zenith.util.ImageInfo;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import lombok.Data;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Data
public class BlockStatePropertyDefinition {
    private final ReferenceSet<Property<?>> properties;
    private final ImmutableSortedMap<String, Property<?>> propertiesByName;
    @Getter(lazy = true)
    private final Int2ObjectMap<Reference2ObjectMap<Property<?>, Comparable<?>>> states = initializeStates();

    public BlockStatePropertyDefinition(Property<?>... inputProperties) {
        var propNamesMap = new HashMap<String, Property<?>>();
        for (var property : inputProperties) {
            String name = property.getName();
            if (propNamesMap.containsKey(name)) {
                throw new IllegalArgumentException("Duplicate property name: " + name);
            }
            propNamesMap.put(name, property);
        }
        propertiesByName = ImmutableSortedMap.copyOf(propNamesMap);
        properties = new ReferenceOpenHashSet<>();
        Collections.addAll(properties, inputProperties);
        // init at build time in graalvm
        if (ImageInfo.inImageCode()) getStates();
    }

    public @Nullable <T extends Comparable<T>> T getValue(Property<T> property, int stateIdOffset) {
        if (!hasProperty(property)) return null;
        var properties = getStates().get(stateIdOffset);
        if (properties == null) return null;
        var value =  properties.get(property);
        if (value == null) return null;
        return (T) value;
    }

    public boolean hasProperty(Property<?> property) {
        return properties.contains(property);
    }

    private Int2ObjectMap<Reference2ObjectMap<Property<?>, Comparable<?>>> initializeStates() {
        AtomicInteger stateIndex = new AtomicInteger(0);
        var propertyCount = propertiesByName.size();
        var properties = propertiesByName.values().toArray(new Property[0]);
        var valueLists = new ArrayList<List<? extends Comparable<?>>>(propertyCount);
        for (int i = 0; i < properties.length; i++) {
            final Property<?> property = properties[i];
            valueLists.add(property.getPossibleValues());
        }
        var indices = new int[propertyCount];

        int expectedSize = valueLists.stream().mapToInt(List::size).sum();
        var states = new Int2ObjectOpenHashMap<Reference2ObjectMap<Property<?>, Comparable<?>>>(expectedSize);
        OUTER: while (true) {
            var map = new Reference2ObjectArrayMap<Property<?>, Comparable<?>>(propertyCount);
            for (int i = 0; i < propertyCount; i++) {
                map.put(properties[i], valueLists.get(i).get(indices[i]));
            }
            states.put(stateIndex.getAndIncrement(), map);

            // Increment property values for the next state
            // in reverse alphabetical order by property name
            for (int i = propertyCount - 1; i >= 0; i--) {
                // If we can increment this property, do so and break
                if (++indices[i] < valueLists.get(i).size()) {
                    break;
                }
                // Otherwise, reset this property and continue to the next
                indices[i] = 0;
                // If we reset the first property, we are done
                if (i == 0) {
                    break OUTER;
                }
            }
        }
        return states;
    }
}
