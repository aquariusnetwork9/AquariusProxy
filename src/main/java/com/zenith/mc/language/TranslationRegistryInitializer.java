package com.zenith.mc.language;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationStore;

import java.text.MessageFormat;
import java.util.Iterator;
import java.util.Locale;

import static com.zenith.Globals.OBJECT_MAPPER;

@UtilityClass
public class TranslationRegistryInitializer {
    @SneakyThrows
    public static void registerAllTranslations() {
        var translationRegistry = TranslationStore.messageFormat(Key.key("minecraft"));
        try (JsonParser langParse = OBJECT_MAPPER.createParser(TranslationRegistryInitializer.class.getResourceAsStream("/mcdata/language.json"))) {
            ObjectNode node = langParse.getCodec().readTree(langParse);
            for (Iterator<String> fieldIterator = node.fieldNames(); fieldIterator.hasNext(); ) {
                String key = fieldIterator.next();
                String value = node.get(key).asText();
                translationRegistry.register(key, Locale.ENGLISH, new MessageFormat(value));
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
        GlobalTranslator.translator().addSource(translationRegistry);
    }
}
