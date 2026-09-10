package io.voltikor.lyra.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingSettingsSaveTest {
   @TempDir Path directory;

   @Test
   void noOpChangesStayCleanAndFailedWritesRetryAfterFiveSeconds() {
      long[] now = {0L};
      var pending = new PendingSettingsSave(() -> now[0]);
      var settings = new LyraSettings();
      AtomicInteger writes = new AtomicInteger();

      assertFalse(settings.setTickDelay(settings.tickDelay()));
      assertFalse(settings.isDirty());
      pending.saveIfDue(settings, () -> {
         writes.incrementAndGet();
         return true;
      }, false);
      assertEquals(0, writes.get());

      assertTrue(settings.setTickDelay(2));
      pending.saveIfDue(settings, () -> {
         writes.incrementAndGet();
         return false;
      }, false);
      assertEquals(1, writes.get());
      assertTrue(settings.isDirty());

      now[0] = 4_999_000_000L;
      pending.saveIfDue(settings, () -> {
         writes.incrementAndGet();
         return true;
      }, false);
      assertEquals(1, writes.get());

      now[0] = 5_000_000_000L;
      pending.saveIfDue(settings, () -> {
         writes.incrementAndGet();
         return true;
      }, false);
      assertEquals(2, writes.get());
      assertFalse(settings.isDirty());
   }

   @Test
   void revisionsOnlyAdvanceForSettingsUsedByDecoding() {
      var settings = new LyraSettings();
      assertEquals(0L, settings.decodingRevision());
      assertTrue(settings.setMode(io.voltikor.lyra.noteblock.InstrumentMatchMode.ANY_INSTRUMENT));
      assertEquals(1L, settings.decodingRevision());
      assertFalse(settings.setMode(io.voltikor.lyra.noteblock.InstrumentMatchMode.ANY_INSTRUMENT));
      assertEquals(1L, settings.decodingRevision());
      assertTrue(settings.setTickDelay(2));
      assertEquals(1L, settings.decodingRevision());
   }

   @Test
   void atomicWriterLeavesThePreviousFileWhenSerializationFails() throws IOException {
      Path destination = directory.resolve("settings.json");
      Files.writeString(destination, "old");

      assertThrows(IOException.class, () -> AtomicJsonWriter.write(destination, writer -> {
         writer.write("new");
         throw new IOException("simulated serialization failure");
      }));

      assertEquals("old", Files.readString(destination));
      try (var files = Files.list(directory)) {
         assertEquals(1L, files.count());
      }
   }
}
