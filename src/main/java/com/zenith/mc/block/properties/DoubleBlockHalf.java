package com.zenith.mc.block.properties;

import com.zenith.mc.block.Direction;
import com.zenith.mc.block.properties.api.StringRepresentable;

public enum DoubleBlockHalf implements StringRepresentable {
   UPPER(Direction.DOWN),
   LOWER(Direction.UP);

   private final Direction directionToOther;

   private DoubleBlockHalf(final Direction directionToOther) {
      this.directionToOther = directionToOther;
   }

   public Direction getDirectionToOther() {
      return this.directionToOther;
   }

   @Override
   public String toString() {
      return this.getSerializedName();
   }

   @Override
   public String getSerializedName() {
      return this == UPPER ? "upper" : "lower";
   }

   public DoubleBlockHalf getOtherHalf() {
      return this == UPPER ? LOWER : UPPER;
   }
}
