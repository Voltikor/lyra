package io.voltikor.lyra.song;

import io.voltikor.lyra.MinecraftTestSupport;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.song.OutOfRangeMode;
import io.voltikor.lyra.song.decoder.SongDecoders;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SongFileManagerTest extends MinecraftTestSupport {
   @TempDir Path directory;

   @Test
   void enumerationFiltersFilesAndSortsWithoutSwallowingErrors() throws IOException {
      Path last = Files.createFile(directory.resolve("z.txt"));
      Path first = Files.createFile(directory.resolve("a.nbs"));
      Files.createFile(directory.resolve("ignored.json"));
      Files.createDirectory(directory.resolve("folder.nbs"));
      assertEquals(List.of(first, last), SongFileManager.listSongFiles(directory));
      assertThrows(IOException.class, () -> SongFileManager.listSongFiles(directory.resolve("missing")));
      assertTrue(new SongFileManager().availableSongs().isEmpty());
   }

   @Test
   void songOverridesTakePrecedenceWithoutReplacingOtherGlobalMappings() {
      var files = new SongFileManager();
      files.songConfigManager().initialize(directory);
      Path path = directory.resolve("song.txt");
      String key = files.toStoredSongPath(path);
      var settings = new LyraSettings();
      settings.setTempoQuantization(TempoQuantization.SNAP_DOWN);
      settings.setOutOfRangeMode(OutOfRangeMode.DROP);
      settings.setTranspose(io.voltikor.lyra.config.TransposeSetting.AUTO);
      settings.setInstrumentOverride(NoteBlockInstrument.HARP, NoteBlockInstrument.BASS);
      settings.setInstrumentOverride(NoteBlockInstrument.BELL, NoteBlockInstrument.FLUTE);
      var global = files.settingsForSong(path, settings);
      assertEquals(TempoQuantization.SNAP_DOWN, global.tempoQuantization());
      assertEquals(OutOfRangeMode.DROP, global.outOfRangeMode());
      assertEquals(io.voltikor.lyra.config.TransposeSetting.AUTO, global.transpose());
      assertEquals(NoteBlockInstrument.BASS, global.mapInstrument(NoteBlockInstrument.HARP));
      files.songConfigManager().setTempoOverride(key, TempoQuantization.SNAP_UP);
      files.songConfigManager().setOutOfRangeModeOverride(key, OutOfRangeMode.CLAMP);
      files.songConfigManager().setTransposeOverride(key, io.voltikor.lyra.config.TransposeSetting.OFF);
      files.songConfigManager().setInstrumentOverride(key, NoteBlockInstrument.HARP, NoteBlockInstrument.GUITAR);
      var snapshot = files.settingsForSong(path, settings);
      assertEquals(TempoQuantization.SNAP_UP, snapshot.tempoQuantization());
      assertEquals(OutOfRangeMode.CLAMP, snapshot.outOfRangeMode());
      assertEquals(io.voltikor.lyra.config.TransposeSetting.OFF, snapshot.transpose());
      assertEquals(NoteBlockInstrument.GUITAR, snapshot.mapInstrument(NoteBlockInstrument.HARP));
      assertEquals(NoteBlockInstrument.FLUTE, snapshot.mapInstrument(NoteBlockInstrument.BELL));
      settings.clearInstrumentOverrides();
      files.songConfigManager().setInstrumentOverride(key, NoteBlockInstrument.HARP, NoteBlockInstrument.BASS);
      files.songConfigManager().setTempoOverride(key, TempoQuantization.SNAP_NEAREST);
      files.songConfigManager().setOutOfRangeModeOverride(key, null);
      files.songConfigManager().setTransposeOverride(key, null);
      assertEquals(TempoQuantization.SNAP_UP, snapshot.tempoQuantization());
      assertEquals(NoteBlockInstrument.GUITAR, snapshot.mapInstrument(NoteBlockInstrument.HARP));
      assertEquals(NoteBlockInstrument.FLUTE, snapshot.mapInstrument(NoteBlockInstrument.BELL));
   }

   @Test
   void asynchronousLoadUsesSnapshotCapturedBeforeBackgroundWork() throws Exception {
      var started = new CountDownLatch(1);
      var release = new CountDownLatch(1);
      SongDecoders.registerDecoder("snapshot_test", path -> {
         started.countDown();
         if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Decoder timed out");
         Song song = new Song("snapshot", "test");
         song.addNote(0, new Note(NoteBlockInstrument.HARP, 12));
         return song;
      });
      var files = new SongFileManager();
      var settings = new LyraSettings();
      settings.setTranspose(io.voltikor.lyra.config.TransposeSetting.OFF);
      settings.setInstrumentOverride(NoteBlockInstrument.HARP, NoteBlockInstrument.BASS);
      var future = files.loadSongAsync(directory.resolve("song.snapshot_test"), settings, warning -> {});
      try {
         assertTrue(started.await(5, TimeUnit.SECONDS));
         settings.setInstrumentOverride(NoteBlockInstrument.HARP, NoteBlockInstrument.GUITAR);
      } finally {
         release.countDown();
      }
      Song song = future.get(5, TimeUnit.SECONDS);
      assertEquals(NoteBlockInstrument.BASS, song.notesAt(0).iterator().next().instrument());
      assertEquals(0, song.lastTick());
   }

   @Test
   void decodingErrorsRemainExceptionalFutureResults() {
      var future = new SongFileManager().loadSongAsync(directory.resolve("missing.txt"), new LyraSettings(), warning -> {});
      var error = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
      assertInstanceOf(IOException.class, SongFileManager.unwrap(error.getCause()));
   }
}
