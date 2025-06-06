package com.zenith.mc.block.properties;

import com.zenith.mc.block.properties.api.StringRepresentable;

public enum StairsShape implements StringRepresentable {
   STRAIGHT("straight"),
   INNER_LEFT("inner_left"),
   INNER_RIGHT("inner_right"),
   OUTER_LEFT("outer_left"),
   OUTER_RIGHT("outer_right");

   private final String name;

   private StairsShape(final String name) {
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
