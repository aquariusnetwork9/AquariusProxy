package com.zenith.mc.block.properties;

import com.zenith.mc.block.properties.api.StringRepresentable;

public enum DoorHingeSide implements StringRepresentable {
   LEFT,
   RIGHT;

   @Override
   public String toString() {
      return this.getSerializedName();
   }

   @Override
   public String getSerializedName() {
      return this == LEFT ? "left" : "right";
   }
}
