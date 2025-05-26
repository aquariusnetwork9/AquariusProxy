package com.zenith.mc.block.properties;

import com.zenith.mc.block.properties.api.StringRepresentable;

public enum ComparatorMode implements StringRepresentable {
   COMPARE("compare"),
   SUBTRACT("subtract");

   private final String name;

   private ComparatorMode(final String name) {
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
