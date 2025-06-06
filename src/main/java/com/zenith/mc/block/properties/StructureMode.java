package com.zenith.mc.block.properties;

import com.zenith.mc.block.properties.api.StringRepresentable;

public enum StructureMode implements StringRepresentable {
   SAVE("save"),
   LOAD("load"),
   CORNER("corner"),
   DATA("data");

   private final String name;
   private StructureMode(final String name) {
      this.name = name;
   }

   @Override
   public String getSerializedName() {
      return this.name;
   }
}
