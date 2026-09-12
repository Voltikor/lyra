package io.voltikor.lyra.noteblock;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.client.multiplayer.ClientLevel;

@Environment(EnvType.CLIENT)
public enum InstrumentDetectMode {
   BLOCK_STATE {
      public NoteBlockInstrument detect(BlockState noteBlockState, BlockPos noteBlockPos, ClientLevel world) {
         return noteBlockState.getValue(NoteBlock.INSTRUMENT);
      }
   },
   BELOW_BLOCK {
      public NoteBlockInstrument detect(BlockState noteBlockState, BlockPos noteBlockPos, ClientLevel world) {
         return world.getBlockState(noteBlockPos.below()).instrument();
      }
   };

   public abstract NoteBlockInstrument detect(BlockState noteBlockState, BlockPos noteBlockPos, ClientLevel world);

   public static InstrumentDetectMode parse(String input) {
      if (input == null) {
         return null;
      }

      return switch (input.trim().toLowerCase()) {
         case "state", "blockstate" -> BLOCK_STATE;
         case "below", "belowblock" -> BELOW_BLOCK;
         default -> null;
      };
   }
}
