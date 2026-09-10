package io.voltikor.lyra.hud;

import io.voltikor.lyra.MinecraftTestSupport;
import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.gui.DeskTheme;
import io.voltikor.lyra.song.Note;
import io.voltikor.lyra.song.Song;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import org.junit.jupiter.api.Test;

class RequiredBlocksHudModelTest extends MinecraftTestSupport {
   @Test
   void countsUniqueRequirementsAndOrdersByMissingBlocks() {
      Song song = new Song("test", "author");
      song.addNote(0, new Note(NoteBlockInstrument.HARP, 1));
      song.addNote(1, new Note(NoteBlockInstrument.HARP, 1));
      song.addNote(2, new Note(NoteBlockInstrument.HARP, 2));
      song.addNote(3, new Note(NoteBlockInstrument.BASS, 1));
      song.finishLoading();
      var model = new RequiredBlocksHudModel();
      assertEquals(Map.of(NoteBlockInstrument.HARP, 2, NoteBlockInstrument.BASS, 1),
            model.requiredBlocksByInstrument(song));
      var rows = model.buildRequiredBlocksRows(song, 1, Map.of(NoteBlockInstrument.BASS, 1), new LyraSettings());
      assertEquals(2, rows.size());
      assertEquals(2, rows.get(0).required());
      assertEquals(2, rows.get(0).missing());
      assertEquals(DeskTheme.STATUS_MISSING, rows.get(0).color());
      assertEquals(0, rows.get(1).missing());
      assertEquals(DeskTheme.STATUS_READY, rows.get(1).color());
   }

   @Test
   void anyInstrumentAndScanOnlyRowsPreserveCounts() {
      var model = new RequiredBlocksHudModel();
      Song song = new Song("any", "author");
      song.addNote(0, new Note(null, 1));
      song.addNote(1, new Note(null, 2));
      song.finishLoading();
      var rows = model.buildRequiredBlocksRows(song, -1, Map.of(), new LyraSettings());
      assertEquals(1, rows.size());
      assertEquals(0, rows.getFirst().available());
      assertEquals(2, rows.getFirst().missing());
      rows = model.buildRequiredBlocksRows(null, 7,
            Map.of(NoteBlockInstrument.HARP, 2, NoteBlockInstrument.BASS, 5), new LyraSettings());
      assertEquals(5, rows.getFirst().available());
      assertEquals(0, rows.getFirst().required());
      assertTrue(model.buildRequiredBlocksRows(null, 0, Map.of(), new LyraSettings()).isEmpty());
   }
}
