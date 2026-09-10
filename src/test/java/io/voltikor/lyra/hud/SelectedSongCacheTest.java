package io.voltikor.lyra.hud;

import io.voltikor.lyra.MinecraftTestSupport;
import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.song.Song;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SelectedSongCacheTest extends MinecraftTestSupport {
   @TempDir Path directory;

   @Test
   void completionIsAppliedOnClientExecutorAndStaleResultsAreDiscarded() throws IOException {
      Path first = Files.createFile(directory.resolve("first.nbs"));
      Path second = Files.createFile(directory.resolve("second.nbs"));
      Queue<Runnable> clientTasks = new ArrayDeque<>();
      Queue<CompletableFuture<Song>> loads = new ArrayDeque<>();
      var cache = new SelectedSongCache((path, settings) -> {
         var future = new CompletableFuture<Song>();
         loads.add(future);
         return future;
      });
      var settings = new LyraSettings();
      cache.ensureLoaded(clientTasks::add, first, null, null, settings);
      Song stale = new Song("stale", "author");
      loads.remove().complete(stale);
      assertNull(cache.requiredSongForHud(null, null, first));
      cache.refreshSelectedHudSongState(second, null, null);
      cache.ensureLoaded(clientTasks::add, second, null, null, settings);
      clientTasks.remove().run();
      assertNull(cache.requiredSongForHud(null, null, second));
      Song current = new Song("current", "author");
      loads.remove().complete(current);
      clientTasks.remove().run();
      assertSame(current, cache.requiredSongForHud(null, null, second));
      assertNull(cache.requiredSongForHud(null, null, first));
   }

   @Test
   void clearingCancelsPendingLoadAndLoadedSongCanBeReused() throws IOException {
      Path path = Files.createFile(directory.resolve("song.txt"));
      var future = new CompletableFuture<Song>();
      Queue<Runnable> tasks = new ArrayDeque<>();
      var cache = new SelectedSongCache((p, settings) -> future);
      cache.ensureLoaded(tasks::add, path, null, null, new LyraSettings());
      cache.clearSelectedHudSong();
      assertTrue(future.isCancelled());
      tasks.remove().run();
      assertNull(cache.requiredSongForHud(null, null, path));
      Song song = new Song("loaded", "author");
      cache.refreshSelectedHudSongState(path, song, path);
      assertSame(song, cache.requiredSongForHud(null, null, path));
      assertSame(song, cache.requiredSongForHud(song, path, null));
   }

   @Test
   void failedPathIsNotRetriedUntilSelectionChanges() throws IOException {
      Path path = Files.createFile(directory.resolve("song.nbs"));
      Queue<Runnable> tasks = new ArrayDeque<>();
      int[] calls = {0};
      var cache = new SelectedSongCache((p, settings) -> {
         calls[0]++;
         return CompletableFuture.failedFuture(new IOException("invalid song"));
      });
      var settings = new LyraSettings();
      cache.ensureLoaded(tasks::add, path, null, null, settings);
      tasks.remove().run();
      cache.ensureLoaded(tasks::add, path, null, null, settings);
      assertEquals(1, calls[0]);
      cache.refreshSelectedHudSongState(null, null, null);
      cache.ensureLoaded(tasks::add, path, null, null, settings);
      assertEquals(2, calls[0]);
      tasks.remove().run();
   }

   @Test
   void globalAndSongConfigurationRevisionsInvalidateOnlyPreviews() throws IOException {
      Path path = Files.createFile(directory.resolve("revision.nbs"));
      Queue<Runnable> tasks = new ArrayDeque<>();
      Queue<CompletableFuture<Song>> loads = new ArrayDeque<>();
      long[] songRevision = {0L};
      var cache = new SelectedSongCache((p, settings) -> {
         var future = new CompletableFuture<Song>();
         loads.add(future);
         return future;
      }, p -> songRevision[0], () -> 0L);
      var settings = new LyraSettings();

      cache.ensureLoaded(tasks::add, path, null, null, settings);
      Song first = new Song("first", "author");
      loads.remove().complete(first);
      tasks.remove().run();
      assertSame(first, cache.requiredSongForHud(null, null, path));

      settings.setTranspose(io.voltikor.lyra.config.TransposeSetting.OFF);
      cache.ensureLoaded(tasks::add, path, null, null, settings);
      assertNull(cache.requiredSongForHud(null, null, path));
      Song second = new Song("second", "author");
      loads.remove().complete(second);
      tasks.remove().run();
      assertSame(second, cache.requiredSongForHud(null, null, path));

      songRevision[0]++;
      cache.ensureLoaded(tasks::add, path, null, null, settings);
      assertNull(cache.requiredSongForHud(null, null, path));
      assertEquals(1, loads.size());
   }

   @Test
   void failedLoadsBackOffAndSelectionChangesResetTheDelay() throws IOException {
      Path path = Files.createFile(directory.resolve("retry.nbs"));
      Queue<Runnable> tasks = new ArrayDeque<>();
      long[] now = {0L};
      int[] calls = {0};
      var cache = new SelectedSongCache((p, settings) -> {
         calls[0]++;
         return CompletableFuture.failedFuture(new IOException("bad song"));
      }, p -> 0L, () -> now[0]);
      var settings = new LyraSettings();
      long fiveSeconds = TimeUnit.SECONDS.toNanos(5);

      cache.ensureLoaded(tasks::add, path, null, null, settings);
      tasks.remove().run();
      assertEquals(1, calls[0]);
      cache.ensureLoaded(tasks::add, path, null, null, settings);
      assertEquals(1, calls[0]);

      now[0] = fiveSeconds;
      cache.ensureLoaded(tasks::add, path, null, null, settings);
      tasks.remove().run();
      assertEquals(2, calls[0]);
      now[0] += fiveSeconds * 2 - 1;
      cache.ensureLoaded(tasks::add, path, null, null, settings);
      assertEquals(2, calls[0]);
      now[0]++;
      cache.ensureLoaded(tasks::add, path, null, null, settings);
      tasks.remove().run();
      assertEquals(3, calls[0]);

      cache.refreshSelectedHudSongState(null, null, null);
      cache.ensureLoaded(tasks::add, path, null, null, settings);
      tasks.remove().run();
      assertEquals(4, calls[0]);
   }
}
