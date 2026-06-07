package com.aquarius.mc.block.properties;

import com.aquarius.mc.block.properties.api.StringRepresentable;

public enum BedPart implements StringRepresentable {
   HEAD("head"),
   FOOT("foot");

   private final String name;

   private BedPart(final String name) {
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
