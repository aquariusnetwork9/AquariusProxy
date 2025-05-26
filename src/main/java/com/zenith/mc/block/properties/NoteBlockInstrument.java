package com.zenith.mc.block.properties;

import com.zenith.mc.block.properties.api.StringRepresentable;

public enum NoteBlockInstrument implements StringRepresentable {
   HARP("harp", Type.BASE_BLOCK),
   BASEDRUM("basedrum", Type.BASE_BLOCK),
   SNARE("snare", Type.BASE_BLOCK),
   HAT("hat", Type.BASE_BLOCK),
   BASS("bass", Type.BASE_BLOCK),
   FLUTE("flute", Type.BASE_BLOCK),
   BELL("bell", Type.BASE_BLOCK),
   GUITAR("guitar", Type.BASE_BLOCK),
   CHIME("chime", Type.BASE_BLOCK),
   XYLOPHONE("xylophone", Type.BASE_BLOCK),
   IRON_XYLOPHONE("iron_xylophone", Type.BASE_BLOCK),
   COW_BELL("cow_bell", Type.BASE_BLOCK),
   DIDGERIDOO("didgeridoo", Type.BASE_BLOCK),
   BIT("bit", Type.BASE_BLOCK),
   BANJO("banjo", Type.BASE_BLOCK),
   PLING("pling", Type.BASE_BLOCK),
   ZOMBIE("zombie", Type.MOB_HEAD),
   SKELETON("skeleton", Type.MOB_HEAD),
   CREEPER("creeper", Type.MOB_HEAD),
   DRAGON("dragon", Type.MOB_HEAD),
   WITHER_SKELETON("wither_skeleton", Type.MOB_HEAD),
   PIGLIN("piglin", Type.MOB_HEAD),
   CUSTOM_HEAD("custom_head", Type.CUSTOM);

   private final String name;
   private final Type type;

   NoteBlockInstrument(final String name, final Type type) {
      this.name = name;
      this.type = type;
   }

   @Override
   public String getSerializedName() {
      return this.name;
   }

   public boolean isTunable() {
      return this.type == Type.BASE_BLOCK;
   }

   public boolean hasCustomSound() {
      return this.type == Type.CUSTOM;
   }

   public boolean worksAboveNoteBlock() {
      return this.type != Type.BASE_BLOCK;
   }

   enum Type {
      BASE_BLOCK,
      MOB_HEAD,
      CUSTOM;
   }
}
