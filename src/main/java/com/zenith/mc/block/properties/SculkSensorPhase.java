package com.zenith.mc.block.properties;

import com.zenith.mc.block.properties.api.StringRepresentable;

public enum SculkSensorPhase implements StringRepresentable {
   INACTIVE("inactive"),
   ACTIVE("active"),
   COOLDOWN("cooldown");

   private final String name;

   private SculkSensorPhase(final String name) {
      this.name = name;
   }

   @Override
   public String toString() {
      return this.name;
   }

   @Override
   public String getSerializedName() {
      return this.name;
   }
}
