package com.zenith.mc.block.properties;

import com.zenith.mc.block.properties.api.StringRepresentable;

public enum AttachFace implements StringRepresentable {
   FLOOR("floor"),
   WALL("wall"),
   CEILING("ceiling");

   private final String name;

   private AttachFace(final String name) {
      this.name = name;
   }

   @Override
   public String getSerializedName() {
      return this.name;
   }
}
