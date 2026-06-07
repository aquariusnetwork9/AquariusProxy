package com.aquarius.mc.enchantment;

import com.aquarius.mc.RegistryData;

public record EnchantmentData(
    int id,
    String name,
    int maxLevel
) implements RegistryData { }
