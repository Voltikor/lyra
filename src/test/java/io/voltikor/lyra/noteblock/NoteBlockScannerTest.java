package io.voltikor.lyra.noteblock;

import io.voltikor.lyra.MinecraftTestSupport;
import io.voltikor.lyra.song.Note;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NoteBlockScannerTest extends MinecraftTestSupport {
   @Test
   void sharedScanEntryFeedsTuningAndHudViews() {
      NoteBlockScanner scanner = new NoteBlockScanner();
      BlockPos first = new BlockPos(1, 2, 3);
      BlockPos second = new BlockPos(2, 2, 3);

      scanner.recordNearbyNoteblock(first, 12, NoteBlockInstrument.HARP, InstrumentMatchMode.EXACT_INSTRUMENTS);
      scanner.recordNearbyNoteblock(second, 12, NoteBlockInstrument.HARP, InstrumentMatchMode.EXACT_INSTRUMENTS);

      NearbyNoteBlocksSnapshot snapshot = scanner.nearbySnapshot();
      assertEquals(2, snapshot.playableTotal());
      assertEquals(2, snapshot.playableByInstrument().get(NoteBlockInstrument.HARP));
      assertEquals(List.of(first, second), snapshot.tuningCandidates().get(new Note(NoteBlockInstrument.HARP, 12)));
   }

   @Test
   void anyInstrumentMappingStillPreservesHudInstrumentCounts() {
      NoteBlockScanner scanner = new NoteBlockScanner();
      BlockPos pos = new BlockPos(4, 5, 6);

      scanner.recordNearbyNoteblock(pos, 7, NoteBlockInstrument.BASS, InstrumentMatchMode.ANY_INSTRUMENT);

      NearbyNoteBlocksSnapshot snapshot = scanner.nearbySnapshot();
      assertEquals(1, snapshot.playableByInstrument().get(NoteBlockInstrument.BASS));
      assertEquals(List.of(pos), snapshot.tuningCandidates().get(new Note(null, 7)));
      assertFalse(snapshot.tuningCandidates().containsKey(new Note(NoteBlockInstrument.BASS, 7)));
   }

   @Test
   void nearbySnapshotDoesNotExposeMutableScannerCollections() {
      NoteBlockScanner scanner = new NoteBlockScanner();
      scanner.recordNearbyNoteblock(new BlockPos(1, 2, 3), 12, NoteBlockInstrument.HARP,
            InstrumentMatchMode.EXACT_INSTRUMENTS);
      NearbyNoteBlocksSnapshot snapshot = scanner.nearbySnapshot();

      assertThrows(UnsupportedOperationException.class,
            () -> snapshot.playableByInstrument().put(NoteBlockInstrument.BASS, 2));
      assertThrows(UnsupportedOperationException.class,
            () -> snapshot.tuningCandidates().get(new Note(NoteBlockInstrument.HARP, 12)).add(new BlockPos(9, 9, 9)));
   }
}
