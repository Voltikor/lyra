package io.voltikor.lyra.noteblock;

import io.voltikor.lyra.MinecraftTestSupport;
import java.util.Arrays;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InstrumentIconsTest extends MinecraftTestSupport {
   @Test
   void everyInstrumentHasAnExplicitItemIcon() {
      assertAll(Arrays.stream(NoteBlockInstrument.values()).map(instrument -> () -> {
         var block = InstrumentIcons.blockFor(instrument);
         assertNotSame(Items.AIR, block.asItem(), instrument.name());
         assertNotSame(Blocks.NOTE_BLOCK, block, "Missing explicit icon for " + instrument);
      }));
   }

   //? if >=26.1 {
   @Test
   void trumpetsUseTheirCopperOxidationStage() {
      assertSame(Blocks.COPPER_BLOCK, InstrumentIcons.blockFor(NoteBlockInstrument.TRUMPET));
      assertSame(Blocks.EXPOSED_COPPER, InstrumentIcons.blockFor(NoteBlockInstrument.TRUMPET_EXPOSED));
      assertSame(Blocks.WEATHERED_COPPER, InstrumentIcons.blockFor(NoteBlockInstrument.TRUMPET_WEATHERED));
      assertSame(Blocks.OXIDIZED_COPPER, InstrumentIcons.blockFor(NoteBlockInstrument.TRUMPET_OXIDIZED));
   }
   //? }
}
