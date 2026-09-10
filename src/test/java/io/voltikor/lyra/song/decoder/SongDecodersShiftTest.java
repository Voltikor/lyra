package io.voltikor.lyra.song.decoder;

import io.voltikor.lyra.MinecraftTestSupport;
import io.voltikor.lyra.song.Note;
import io.voltikor.lyra.song.Song;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SongDecodersShiftTest extends MinecraftTestSupport {
   @Test
   void prefersNegativeShiftWhenItIsTheBestCandidate() {
      Song song = song(25, 26, 27);

      assertEquals(-12, SongDecoders.selectShift(song));
   }

   @Test
   void selectsPositiveShiftWhenItImprovesTheBaseline() {
      Song song = song(-1, -2, 0);

      assertEquals(12, SongDecoders.selectShift(song));
   }

   @Test
   void keepsZeroWhenItTiesAndUsesNegativeOnAnOtherwiseEqualTie() {
      assertEquals(0, SongDecoders.selectShift(song(0, 25)));
      assertEquals(-12, SongDecoders.selectShift(song(-1, 25)));
   }

   private static Song song(int... levels) {
      Song song = new Song("test", "test");
      for (int i = 0; i < levels.length; i++) {
         song.addNote(i, new Note(NoteBlockInstrument.HARP, levels[i]));
      }
      return song;
   }
}
