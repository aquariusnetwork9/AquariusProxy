package com.zenith.mc.block.properties;

import com.zenith.mc.block.properties.api.StringRepresentable;

public enum WallSide implements StringRepresentable {
   NONE("none"),
   LOW("low"),
   TALL("tall");

   private final String name;

   private WallSide(final String name) {
      this.name = name;
   }

   @Override
   public String toString() {
      return this.getSerializedName();
   }

   @Override
   public String getSerializedName() {
      return this.name;
   }
}
