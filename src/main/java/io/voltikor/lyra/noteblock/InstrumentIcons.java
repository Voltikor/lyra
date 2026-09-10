package io.voltikor.lyra.noteblock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

public final class InstrumentIcons {
   private InstrumentIcons() {}

   public static Block blockFor(NoteBlockInstrument instrument) {
      return switch (instrument) {
         case HARP -> Blocks.GRASS_BLOCK;
         case BASEDRUM -> Blocks.STONE;
         case SNARE -> Blocks.SAND;
         case HAT -> Blocks.GLASS;
         case BASS -> Blocks.OAK_PLANKS;
         case FLUTE -> Blocks.CLAY;
         case BELL -> Blocks.GOLD_BLOCK;
         case GUITAR -> Blocks.WHITE_WOOL;
         case CHIME -> Blocks.PACKED_ICE;
         case XYLOPHONE -> Blocks.BONE_BLOCK;
         case IRON_XYLOPHONE -> Blocks.IRON_BLOCK;
         case COW_BELL -> Blocks.SOUL_SAND;
         case DIDGERIDOO -> Blocks.CARVED_PUMPKIN;
         case BIT -> Blocks.EMERALD_BLOCK;
         case BANJO -> Blocks.HAY_BLOCK;
         case PLING -> Blocks.GLOWSTONE;
         //? if >=26.1 {
         case TRUMPET -> Blocks.COPPER_BLOCK;
         case TRUMPET_EXPOSED -> Blocks.EXPOSED_COPPER;
         case TRUMPET_WEATHERED -> Blocks.WEATHERED_COPPER;
         case TRUMPET_OXIDIZED -> Blocks.OXIDIZED_COPPER;
         //? }
         case ZOMBIE -> Blocks.ZOMBIE_HEAD;
         case SKELETON -> Blocks.SKELETON_SKULL;
         case CREEPER -> Blocks.CREEPER_HEAD;
         case DRAGON -> Blocks.DRAGON_HEAD;
         case WITHER_SKELETON -> Blocks.WITHER_SKELETON_SKULL;
         case PIGLIN -> Blocks.PIGLIN_HEAD;
         case CUSTOM_HEAD -> Blocks.PLAYER_HEAD;
         default -> Blocks.NOTE_BLOCK;
      };
   }

}
