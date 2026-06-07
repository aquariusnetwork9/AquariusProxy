package com.aquarius.mc;

public interface RegistryData {
    int id();
    // equivalent to resource key (minus the 'minecraft:' prefix
    String name();
}
