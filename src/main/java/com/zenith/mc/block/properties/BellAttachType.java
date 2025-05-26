package com.zenith.mc.block.properties;

import com.zenith.mc.block.properties.api.StringRepresentable;

public enum BellAttachType implements StringRepresentable {
   FLOOR("floor"),
   CEILING("ceiling"),
   SINGLE_WALL("single_wall"),
   DOUBLE_WALL("double_wall");

   private final String name;

   private BellAttachType(final String name) {
      this.name = name;
   }

   @Override
   public String getSerializedName() {
      return this.name;
   }
}
