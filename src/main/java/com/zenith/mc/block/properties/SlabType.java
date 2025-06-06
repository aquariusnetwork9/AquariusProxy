package com.zenith.mc.block.properties;

import com.zenith.mc.block.properties.api.StringRepresentable;

public enum SlabType implements StringRepresentable {
   TOP("top"),
   BOTTOM("bottom"),
   DOUBLE("double");

   private final String name;

   private SlabType(final String name) {
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
