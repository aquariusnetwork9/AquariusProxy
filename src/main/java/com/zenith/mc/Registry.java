package com.zenith.mc;

import com.zenith.util.struct.Maps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;

@Getter
@NullMarked
public class Registry<T extends RegistryData> {
    private final Int2ObjectOpenHashMap<T> idMap;
    private final HashMap<String, T> keyMap;

    public Registry(int size) {
        idMap = new Int2ObjectOpenHashMap<>(size, Maps.FAST_LOAD_FACTOR);
        keyMap = new HashMap<>(size, Maps.FAST_LOAD_FACTOR);
    }

    public T register(T value) {
        idMap.put(value.id(), value);
        keyMap.put(value.name(), value);
        return value;
    }

    public @Nullable T get(int id) {
        return idMap.get(id);
    }

    public @Nullable T get(String key) {
        return keyMap.get(key);
    }

    public int size() {
        return idMap.size();
    }
}
