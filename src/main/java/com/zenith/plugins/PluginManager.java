package com.zenith.plugins;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zenith.api.PluginInfo;
import com.zenith.api.ZenithProxyPlugin;
import com.zenith.event.proxy.PluginLoadFailureEvent;
import com.zenith.util.ImageInfo;
import lombok.SneakyThrows;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import static com.zenith.Shared.*;

public class PluginManager {
    public static final Path PLUGINS_PATH = Path.of("plugins");
    private final BiMap<String, ZenithProxyPlugin> pluginInstances = Maps.synchronizedBiMap(HashBiMap.create());
    private final Map<String, PluginInfo> pluginInfos = new ConcurrentHashMap<>();
    private final Map<String, ConfigInstance> pluginConfigurations = new ConcurrentHashMap<>();
    private final Map<String, URLClassLoader> pluginClassLoaders = new ConcurrentHashMap<>();
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public List<PluginInfo> getPluginInfos() {
        return List.copyOf(pluginInfos.values());
    }

    public String getId(final ZenithProxyPlugin pluginInstance) {
        return pluginInstances.inverse().get(pluginInstance);
    }

    public PluginInfo getPluginInfo(final ZenithProxyPlugin pluginInstance) {
        return pluginInfos.get(getId(pluginInstance));
    }

    public void saveConfigs(BiConsumer<File, Object> saveFunction) {
        pluginConfigurations.values()
            .forEach(config -> saveFunction.accept(config.file(), config.instance()));
    }

    public record ConfigInstance(Object instance, Class<?> clazz, File file) { }

    public synchronized void initialize() {
        if (initialized.compareAndSet(false, true)) {
            if (!ImageInfo.inImageCode()) {
                loadPlugins();
            } else {
                if (ImageInfo.inImageRuntimeCode())
                    linuxChannelIncompatibilityWarning();
            }
        }
    }

    private void linuxChannelIncompatibilityWarning() {
        int potentialPluginCount = 0;
        if (!PLUGINS_PATH.toFile().exists()) return;
        try (var stream = Files.walk(PLUGINS_PATH)) {
            potentialPluginCount = (int) stream
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".jar"))
                .count();
        } catch (Throwable e) {
            PLUGIN_LOG.error("Error walking plugins directory", e);
        }
        if (potentialPluginCount > 0) {
            PLUGIN_LOG.warn("""
                Plugins are not supported on the `linux` release channel.
                Detected {} potential plugin jars in the plugins directory.
                
                To use plugins, switch to the `java` channel: `channel set java <mcVersion>`
                """, potentialPluginCount);
        }
    }

    private void loadPlugins() {
        if (!PLUGINS_PATH.toFile().exists()) return;
        try (var jarStream = Files.newDirectoryStream(PLUGINS_PATH, p -> p.toFile().isFile() && p.toString().endsWith(".jar"))) {
            for (var jarPath : jarStream) {
                loadPotentialPluginJar(jarPath);
            }
        } catch (Throwable e) {
            PLUGIN_LOG.error("Error loading plugins", e);
        }
    }

    private void loadPotentialPluginJar(final Path jarPath) {
        String id = null;
        URLClassLoader classLoader = null;
        try {
            classLoader = new URLClassLoader(new URL[]{jarPath.toUri().toURL()}, getClass().getClassLoader());;
            PluginInfo pluginInfo = readPluginInfo(classLoader, jarPath);
            id = Objects.requireNonNull(pluginInfo.id(), "Plugin id is null");
            if (pluginInfos.containsKey(id)) {
                throw new RuntimeException("Plugin id already exists (json)");
            }
            if (pluginInfo.mcVersions().isEmpty()) {
                PLUGIN_LOG.error("Plugin: {} has no MC versions specified", jarPath);
                throw new RuntimeException("Plugin has no MC versions specified");
            }
            if (!pluginInfo.mcVersions().contains("*") && !pluginInfo.mcVersions().contains(MC_VERSION)) {
                PLUGIN_LOG.warn("Plugin: {} not compatible with current MC version. Actual: {}, Plugin Required: {}", jarPath, MC_VERSION, pluginInfo.mcVersions());
                return;
            }
            String entrypoint = Objects.requireNonNull(pluginInfo.entrypoint(), "Plugin entrypoint is null");

            PLUGIN_LOG.info(
                "Loading Plugin:\n  id: {}\n  version: {}\n  description: {}\n  url: {}\n  authors: {}\n  jar: {}",
                pluginInfo.id(),
                pluginInfo.version(),
                pluginInfo.description(),
                pluginInfo.url(),
                pluginInfo.authors(),
                jarPath.getFileName()
            );

            Class<?> pluginClass = classLoader.loadClass(entrypoint);
            if (!ZenithProxyPlugin.class.isAssignableFrom(pluginClass)) {
                throw new RuntimeException("Plugin does not implement ZenithProxyPlugin interface");
            }
            ZenithProxyPlugin plugin = (ZenithProxyPlugin) pluginClass.getDeclaredConstructor().newInstance();

            if (pluginInstances.get(id) != null) {
                PLUGIN_LOG.error("Plugin id already exists: {} from: {} (instance)", id, jarPath);
                throw new RuntimeException("Plugin id already exists (instance)");
            }
            pluginInstances.put(id, plugin);
            pluginInfos.put(id, pluginInfo);
            pluginClassLoaders.put(id, classLoader);
            try {
                plugin.onLoad(new InstancedPluginAPI(plugin, pluginInfo));
            } catch (final Throwable e) {
                PLUGIN_LOG.error("Exception in plugin onLoad: {}", jarPath, e);
                pluginInstances.remove(id);
                pluginInfos.remove(id);
                pluginClassLoaders.remove(id);
                throw new RuntimeException("Exception in plugin onLoad: + " + e.getMessage(), e);
            }
        } catch (Throwable e) {
            if (classLoader != null) {
                try {
                    classLoader.close();
                } catch (IOException ignored) { }
            }
            PLUGIN_LOG.error("Error loading plugin: {}", jarPath, e);
            EVENT_BUS.postAsync(new PluginLoadFailureEvent(id, jarPath, e.getMessage()));
        }
    }

    @SneakyThrows
    private PluginInfo readPluginInfo(URLClassLoader classLoader, Path path) {
        try (var stream = classLoader.getResourceAsStream("plugin.json")) {
            return OBJECT_MAPPER.readValue(stream, PluginInfo.class);
        } catch (IOException e) {
            PLUGIN_LOG.error("Error reading plugin.json: {}", path, e);
            throw e;
        }
    }

    public synchronized <T> T registerConfig(String fileName, Class<T> clazz) {
        if (pluginConfigurations.containsKey(fileName)) {
            throw new RuntimeException("Config already registered: " + fileName);
        }
        var config = loadPluginConfig(fileName, clazz);
        File configFile = PLUGINS_PATH.resolve("config").resolve(fileName + ".json").toFile();
        if (!configFile.exists()) {
            if (!configFile.getParentFile().mkdirs() && !configFile.getParentFile().exists()) {
                throw new RuntimeException("Unable to create plugin config directory: " + configFile.getParentFile());
            }
        }
        var configInstance = new ConfigInstance(
            config,
            clazz,
            configFile
        );
        pluginConfigurations.put(fileName, configInstance);
        return config;
    }

    @SneakyThrows
    private <T> T loadPluginConfig(String fileName, Class<T> clazz) {
        try {
            PLUGIN_LOG.info("Loading plugin config...");
            File configFile = new File("plugins/" + fileName + ".json");
            T config;
            if (configFile.exists()) {
                try (Reader reader = new FileReader(configFile)) {
                    final Gson GSON = new GsonBuilder()
                        .disableHtmlEscaping()
                        .setPrettyPrinting()
                        .create();
                    config = GSON.fromJson(reader, clazz);
                } catch (IOException e) {
                    throw new RuntimeException("Unable to load plugin config: " + fileName, e);
                }
                PLUGIN_LOG.info("Plugin config: {} loaded.", fileName);
            } else {
                config = clazz.getDeclaredConstructor().newInstance();
                PLUGIN_LOG.info("Plugin config: {} not found.", fileName);
            }
            return config;
        } catch (final Throwable e) {
            PLUGIN_LOG.error("Unable to load plugin config: {}", fileName, e);
            PLUGIN_LOG.error("Config must be manually fixed or deleted");
            throw e;
        }
    }
}
