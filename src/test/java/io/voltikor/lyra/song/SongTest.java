package io.voltikor.lyra.song;

import io.voltikor.lyra.MinecraftTestSupport;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SongTest extends MinecraftTestSupport {
   @Test
   void findsNextNotesWithoutScanningEmptyTicks() {
      Song song = new Song("sparse", "author");
      Note early = new Note(NoteBlockInstrument.HARP, 12);
      Note late = new Note(NoteBlockInstrument.BASS, 8);
      song.addNote(10, early);
      song.addNote(100_000, late);
      song.finishLoading();

      assertEquals(early, song.nextNotesAtOrAfter(0).iterator().next());
      assertEquals(late, song.nextNotesAtOrAfter(11).iterator().next());
      assertEquals(late, song.nextNotesAtOrAfter(100_000).iterator().next());
      assertTrue(song.nextNotesAtOrAfter(100_001).isEmpty());
   }
}
