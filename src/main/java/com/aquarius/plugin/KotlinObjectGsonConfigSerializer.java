package com.aquarius.plugin;

import com.google.gson.Gson;
import com.google.gson.InstanceCreator;
import com.aquarius.plugin.api.ConfigSerializer;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Modifier;

import static com.aquarius.Globals.GSON;
import static com.aquarius.util.KotlinUtil.getKotlinObject;
import static com.aquarius.util.KotlinUtil.isKotlinObject;

public class KotlinObjectGsonConfigSerializer implements ConfigSerializer {
    private final Gson gson;
    private final Object instance;

    public KotlinObjectGsonConfigSerializer(Class<?> configClass) {
        if (!isKotlinObject(configClass)) {
            throw new IllegalArgumentException("Config class must be a Kotlin object");
        }
        this.instance = getKotlinObject(configClass);
        this.gson = GSON.newBuilder()
            .excludeFieldsWithModifiers(Modifier.TRANSIENT)
            .registerTypeAdapter(configClass, (InstanceCreator<?>) type -> instance)
            .create();
    }

    @Override
    public void write(final Object config, final Writer writer) {
        gson.toJson(config, writer);
    }

    @Override
    public <T> T read(final Class<T> configClass, final Reader reader) {
        return gson.fromJson(reader, configClass);
    }
}
