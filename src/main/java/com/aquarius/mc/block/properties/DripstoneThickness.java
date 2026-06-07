package com.aquarius.mc.block.properties;

import com.aquarius.mc.block.properties.api.StringRepresentable;

public enum DripstoneThickness implements StringRepresentable {
   TIP_MERGE("tip_merge"),
   TIP("tip"),
   FRUSTUM("frustum"),
   MIDDLE("middle"),
   BASE("base");

   private final String name;

   private DripstoneThickness(final String name) {
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
