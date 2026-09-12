package io.voltikor.lyra.song;

import io.voltikor.lyra.MinecraftTestSupport;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SongNormalizerShiftTest extends MinecraftTestSupport {
   @Test
   void prefersNegativeShiftWhenItIsTheBestCandidate() {
      Song song = song(25, 26, 27);

      assertEquals(-12, SongNormalizer.selectShift(song));
   }

   @Test
   void selectsPositiveShiftWhenItImprovesTheBaseline() {
      Song song = song(-1, -2, 0);

      assertEquals(12, SongNormalizer.selectShift(song));
   }

   @Test
   void keepsZeroWhenItTiesAndUsesNegativeOnAnOtherwiseEqualTie() {
      assertEquals(0, SongNormalizer.selectShift(song(0, 25)));
      assertEquals(-12, SongNormalizer.selectShift(song(-1, 25)));
   }

   private static Song song(int... levels) {
      Song song = new Song("test", "test");
      for (int i = 0; i < levels.length; i++) {
         song.addNote(i, new Note(NoteBlockInstrument.HARP, levels[i]));
      }
      return song;
   }
}
