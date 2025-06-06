package com.zenith.mc;

import lombok.Getter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@Getter
@NullMarked
public class DynamicRegistry<T extends RegistryData> {
    // registry we initialize directly from vanilla's data
    private final Registry<T> defaultRegistry;
    // reference to default registry, unless the server provides a custom registry
    private Registry<T> loadedRegistry;

    public DynamicRegistry(int size) {
        this.defaultRegistry = new Registry<>(size);
        this.loadedRegistry = defaultRegistry;
    }

    public T register(T value) {
        defaultRegistry.register(value);
        return value;
    }

    public void reset() {
        loadedRegistry = defaultRegistry;
    }

    public void set(Registry<T> registry) {
        this.loadedRegistry = registry;
    }

    public boolean isDefault() {
        return loadedRegistry == defaultRegistry;
    }

    public @Nullable T get(int id) {
        return loadedRegistry.get(id);
    }

    public @Nullable T get(String key) {
        return loadedRegistry.get(key);
    }

    public int size() {
        return loadedRegistry.size();
    }
}
