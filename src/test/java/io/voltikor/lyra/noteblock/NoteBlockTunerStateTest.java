package io.voltikor.lyra.noteblock;

import io.voltikor.lyra.MinecraftTestSupport;
import io.voltikor.lyra.config.LyraSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoteBlockTunerStateTest extends MinecraftTestSupport {
   @Test
   void emptyUntouchedQueueReportsReady() {
      NoteBlockTuner tuner = new NoteBlockTuner();
      NoteBlockScanner scanner = new NoteBlockScanner();

      assertEquals(NoteBlockTuner.TuneOutcome.READY,
            tuner.tune(null, new LyraSettings(), scanner, 0, () -> {}));
   }

   @Test
   void emptyQueueAfterTuningRequestsRecheckOnce() {
      NoteBlockTuner tuner = new NoteBlockTuner();
      tuner.setAnyNoteblockTuned(true);
      NoteBlockScanner scanner = new NoteBlockScanner();

      assertEquals(NoteBlockTuner.TuneOutcome.RECHECK_REQUIRED,
            tuner.tune(null, new LyraSettings(), scanner, 0, () -> {}));
      assertEquals(NoteBlockTuner.TuneOutcome.READY,
            tuner.tune(null, new LyraSettings(), scanner, 0, () -> {}));
   }
}
