package com.zenith.mc.block.properties;

import com.zenith.mc.block.properties.api.StringRepresentable;

public enum Half implements StringRepresentable {
   TOP("top"),
   BOTTOM("bottom");

   private final String name;

   private Half(final String name) {
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
