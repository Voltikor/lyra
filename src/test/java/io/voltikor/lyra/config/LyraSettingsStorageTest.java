package io.voltikor.lyra.config;

import io.voltikor.lyra.MinecraftTestSupport;
import io.voltikor.lyra.hud.HudAnchor;
import io.voltikor.lyra.noteblock.InstrumentDetectMode;
import io.voltikor.lyra.noteblock.InstrumentMatchMode;
import io.voltikor.lyra.noteblock.RotateMode;
import io.voltikor.lyra.song.OutOfRangeMode;
import io.voltikor.lyra.song.TempoQuantization;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LyraSettingsStorageTest extends MinecraftTestSupport {
   @TempDir Path directory;

   @Test
   void oldTransposeNumbersKeepSupportedPresetsAndDiscardArbitraryOffsets() throws IOException {
      Path file = directory.resolve("transpose.json");
      Files.writeString(file, "{\"transpose\":\"5\",\"tickDelay\":7}");
      var settings = new LyraSettings();
      LyraSettingsStorage.load(file, settings);
      assertEquals(TransposeSetting.AUTO, settings.transpose());
      assertEquals(7, settings.tickDelay());
      Files.writeString(file, "{\"transpose\":\"12\"}");
      LyraSettingsStorage.load(file, settings);
      assertEquals(TransposeSetting.UP_12, settings.transpose());
      assertTrue(LyraSettingsStorage.save(file, settings));
      assertTrue(Files.readString(file).contains("\"transpose\": \"+12\""));
   }

   @Test
   void settingsRoundTripAndLegacyMigration() throws IOException {
      Path file = directory.resolve("settings.json");
      var original = new LyraSettings();
      original.setTickDelay(4);
      original.setSelectedSongPath("nested/song.nbs");
      original.setInstrumentOverride(NoteBlockInstrument.HARP, NoteBlockInstrument.BASS);
      LyraSettingsStorage.save(file, original);
      var restored = new LyraSettings();
      LyraSettingsStorage.load(file, restored);
      assertEquals(LyraSettingsStorage.serialize(original), LyraSettingsStorage.serialize(restored));
      Files.writeString(file, "{\"roundOutOfRange\":true,\"tickDelay\":99}");
      LyraSettingsStorage.load(file, restored);
      assertEquals(OutOfRangeMode.CLAMP, restored.outOfRangeMode());
      assertEquals(20, restored.tickDelay());
      Files.writeString(file, "{\"roundOutOfRange\":true,\"outOfRangeMode\":\"DROP\"}");
      LyraSettingsStorage.load(file, restored);
      assertEquals(OutOfRangeMode.DROP, restored.outOfRangeMode());
   }

   @Test
   void legacyWorldRenderingSettingsAreIgnoredWhileHudPreferencesSurvive() throws IOException {
      Path file = directory.resolve("settings.json");
      Files.writeString(file, """
            {"renderBoxes":true,"renderText":true,"markerStyle":"BUTTON",
             "showScannedNoteblocks":true,"renderLineWidth":3,
             "showHud":false,"hudAnchor":"TOP_LEFT","hudAutoHide":false,"tickDelay":4}
            """);
      var settings = new LyraSettings();
      LyraSettingsStorage.load(file, settings);
      assertFalse(settings.showHud());
      assertEquals(HudAnchor.TOP_LEFT, settings.hudAnchor());
      assertFalse(settings.hudAutoHide());
      assertEquals(4, settings.tickDelay());
      LyraSettingsStorage.save(file, settings);
      String json = Files.readString(file);
      for (String removed : List.of("renderBoxes", "renderText", "markerStyle", "showScannedNoteblocks", "renderLineWidth")) {
         assertFalse(json.contains(removed));
      }
      var restored = new LyraSettings();
      LyraSettingsStorage.load(file, restored);
      assertEquals(LyraSettingsStorage.serialize(settings), LyraSettingsStorage.serialize(restored));
   }

   @Test
   void invalidPacketAndRecheckLimitsRecoverSafely() throws IOException {
      Path file = directory.resolve("limits.json");
      Files.writeString(file, "{\"concurrentTuneBlocks\":999,\"checkNoteblocksAgainDelay\":999}");
      var settings = new LyraSettings();
      LyraSettingsStorage.load(file, settings);
      assertEquals(20, settings.concurrentTuneBlocks());
      assertEquals(100, settings.checkNoteblocksAgainDelay());

      Files.writeString(file, "{\"concurrentTuneBlocks\":-5,\"checkNoteblocksAgainDelay\":-5}");
      LyraSettingsStorage.load(file, settings);
      assertEquals(1, settings.concurrentTuneBlocks());
      assertEquals(1, settings.checkNoteblocksAgainDelay());

      Files.writeString(file, "{\"concurrentTuneBlocks\":0}");
      LyraSettingsStorage.load(file, settings);
      assertEquals(0, settings.concurrentTuneBlocks());
   }

   @Test
   void defaultSettingsValues() {
      var settings = new LyraSettings();
      assertEquals(1, settings.tickDelay());
      assertEquals(1, settings.concurrentTuneBlocks());
      assertEquals(InstrumentMatchMode.EXACT_INSTRUMENTS, settings.mode());
      assertEquals(InstrumentDetectMode.BLOCK_STATE, settings.instrumentDetectMode());
      assertEquals(TempoQuantization.SNAP_NEAREST, settings.tempoQuantization());
      assertTrue(settings.polyphonic());
      assertTrue(settings.autoRotate());
      assertEquals(RotateMode.VISIBLE_FACE, settings.rotateMode());
      assertFalse(settings.autoPlay());
      assertEquals(OutOfRangeMode.REMAP, settings.outOfRangeMode());
      assertEquals(TransposeSetting.AUTO, settings.transpose());
      assertTrue(settings.swingArm());
      assertEquals(10, settings.checkNoteblocksAgainDelay());
      assertTrue(settings.showHud());
      assertEquals(HudAnchor.BOTTOM_RIGHT, settings.hudAnchor());
      assertTrue(settings.hudAutoHide());

      String json = LyraSettingsStorage.serialize(settings);
      assertTrue(json.contains("\"tickDelay\": 1"));
      assertTrue(json.contains("\"concurrentTuneBlocks\": 1"));
      assertTrue(json.contains("\"mode\": \"EXACT_INSTRUMENTS\""));
      assertTrue(json.contains("\"instrumentDetectMode\": \"BLOCK_STATE\""));
      assertTrue(json.contains("\"tempoQuantization\": \"SNAP_NEAREST\""));
      assertTrue(json.contains("\"polyphonic\": true"));
      assertTrue(json.contains("\"autoRotate\": true"));
      assertTrue(json.contains("\"rotateMode\": \"VISIBLE_FACE\""));
      assertTrue(json.contains("\"autoPlay\": false"));
      assertTrue(json.contains("\"roundOutOfRange\": false"));
      assertTrue(json.contains("\"outOfRangeMode\": \"REMAP\""));
      assertTrue(json.contains("\"transpose\": \"auto\""));
      assertTrue(json.contains("\"swingArm\": true"));
      assertTrue(json.contains("\"checkNoteblocksAgainDelay\": 10"));
      assertTrue(json.contains("\"showHud\": true"));
      assertTrue(json.contains("\"hudAnchor\": \"BOTTOM_RIGHT\""));
      assertTrue(json.contains("\"hudAutoHide\": true"));
   }

}
