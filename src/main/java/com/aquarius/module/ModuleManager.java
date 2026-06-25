package com.aquarius.module;

import com.aquarius.module.api.Module;
import com.aquarius.module.impl.*;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

import java.util.List;

import static com.aquarius.Globals.MODULE_LOG;
import static java.util.Arrays.asList;

public class ModuleManager {
    private final Reference2ObjectMap<Class<? extends Module>, Module> moduleClassMap = new Reference2ObjectOpenHashMap<>();

    public void init() {
        asList(
            new ActionLimiter(),
            new ActiveHours(),
            new AntiAFK(),
            new AntiKick(),
            new AntiLeak(),
            new Boat(),
            new AquariusMiner(),
            new Regear(),
            new KitMaker(),
            new StashScanner(),
            new OrderFiller(),
            new Enchanter(),
            new AquariusSniffer(),
            new AutoArmor(),
            new AutoDisconnect(),
            new AutoDrop(),
            new AutoEat(),
            new AutoFish(),
            new AutoDetectModule(),
            new AutoLoadModule(),
            new PearlDrop(),
            new Bridge(),
            new LitematicaBuilder(),
            new RbacGuard(),
            new RbacApiServer(),
            new ViewerApiServer(),
            new AutoMend(),
            new AutoOmen(),
            new AutoReconnect(),
            new AutoReply(),
            new AutoRespawn(),
            new AutoTotem(),
            new AutoPortal(),
            new ChatHistory(),
            new Click(),
            new CoordObfuscation(),
            new ElytraPilot(),
            new ElytraTrip(),
            new ExtraChat(),
            new KillAura(),
            new AutoBow(),
            new QueueWarning(),
            new ReplayMod(),
            new Requeue(),
            new SessionTimeLimit(),
            new Spammer(),
            new Spook(),
            new SpawnPatrol(),
            new WhisperControl(),
            new Tasks(),
            new VillagerTrader(),
            new VisualRange()
        ).forEach(m -> {
            addModule(m);
            m.syncEnabledFromConfig();
        });
    }

    private void addModule(Module module) {
        moduleClassMap.put(module.getClass(), module);
    }

    public <T extends Module> T get(final Class<T> clazz) {
        try {
            return (T) moduleClassMap.get(clazz);
        } catch (final Throwable e) {
            return null;
        }
    }

    public void registerModule(Module module) {
        if (moduleClassMap.containsKey(module.getClass())) {
            MODULE_LOG.warn("Duplicate module class being registered: {}", module.getClass().getSimpleName(), new RuntimeException());
        }
        addModule(module);
        module.syncEnabledFromConfig();
    }

    public List<Module> getModules() {
        return moduleClassMap.values().stream().toList();
    }
}
