package com.zenith.plugin;

import com.google.auto.service.AutoService;
import com.google.gson.GsonBuilder;
import com.zenith.api.plugin.Plugin;
import com.zenith.api.plugin.PluginInfo;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

@AutoService(Processor.class)
@SupportedAnnotationTypes({"com.zenith.api.Plugin"})
public class PluginAnnotationProcessor extends AbstractProcessor {

    private ProcessingEnvironment environment;
    private String pluginClassFound;
    private boolean warnedAboutMultiplePlugins;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        this.environment = processingEnv;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized boolean process(Set<? extends TypeElement> annotations,
                                        RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        for (Element element : roundEnv.getElementsAnnotatedWith(Plugin.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                environment.getMessager()
                    .printMessage(Diagnostic.Kind.ERROR, "Only classes can be annotated with "
                        + Plugin.class.getCanonicalName());
                return false;
            }

            TypeElement typeElement = (TypeElement) element;
            Name qualifiedName = typeElement.getQualifiedName();

            if (Objects.equals(pluginClassFound, qualifiedName.toString())) {
                if (!warnedAboutMultiplePlugins) {
                    environment.getMessager()
                        .printMessage(Diagnostic.Kind.WARNING, "UNSUPPORTED: Multiple Plugin classes found");
                    warnedAboutMultiplePlugins = true;
                }
                return false;
            }

            Plugin plugin = element.getAnnotation(Plugin.class);

            // All good, generate the plugin.json
            PluginInfo pluginJson = new PluginInfo(
                qualifiedName.toString(),
                plugin.id(),
                plugin.version(),
                plugin.description(),
                plugin.url(),
                Arrays.stream(plugin.authors()).filter(a -> !a.isBlank()).toList(),
                Arrays.stream(plugin.mcVersions()).filter(a -> !a.isBlank()).toList()
            );
            try {
                FileObject object = environment.getFiler()
                    .createResource(StandardLocation.CLASS_OUTPUT, "", "plugin.json");
                try (Writer writer = new BufferedWriter(object.openWriter())) {
                    new GsonBuilder()
                        .setPrettyPrinting()
                        .disableHtmlEscaping()
                        .create()
                        .toJson(pluginJson, writer);
                }
                pluginClassFound = qualifiedName.toString();
            } catch (IOException e) {
                environment.getMessager()
                    .printMessage(Diagnostic.Kind.ERROR, "Unable to generate plugin file");
            }
        }

        return false;
    }
}

