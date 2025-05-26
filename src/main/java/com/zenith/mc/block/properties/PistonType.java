package com.zenith.mc.block.properties;

import com.zenith.mc.block.properties.api.StringRepresentable;

public enum PistonType implements StringRepresentable {
   DEFAULT("normal"),
   STICKY("sticky");

   private final String name;

   private PistonType(final String name) {
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
