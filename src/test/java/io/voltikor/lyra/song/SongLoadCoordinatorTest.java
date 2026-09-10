package io.voltikor.lyra.song;

import io.voltikor.lyra.MinecraftTestSupport;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SongLoadCoordinatorTest extends MinecraftTestSupport {
   @Test
   void successfulLoadClearsActiveFuture() {
      SongLoadCoordinator coordinator = new SongLoadCoordinator();
      CompletableFuture<Song> source = new CompletableFuture<>();
      CompletableFuture<SongLoadCoordinator.LoadResult> result = coordinator.track(source, 1, TimeUnit.SECONDS);
      Song song = new Song("test", "author");

      source.complete(song);

      assertEquals(SongLoadCoordinator.Status.SUCCESS, result.join().status());
      assertEquals(song, result.join().song());
      assertFalse(coordinator.isLoading());
      assertNull(coordinator.activeFuture());
   }

   @Test
   void failuresAreClassifiedAndFormatted() {
      SongLoadCoordinator coordinator = new SongLoadCoordinator();
      CompletableFuture<Song> source = new CompletableFuture<>();
      var result = coordinator.track(source, 1, TimeUnit.SECONDS);
      source.completeExceptionally(new CompletionException(new IllegalArgumentException("bad song")));

      SongLoadCoordinator.LoadResult failure = result.join();
      assertEquals(SongLoadCoordinator.Status.FAILED, failure.status());
      assertEquals("Song loading failed: bad song", SongLoadCoordinator.failureMessage(failure));
   }

   @Test
   void timeoutAndCancellationHaveDistinctResults() {
      SongLoadCoordinator timeoutCoordinator = new SongLoadCoordinator();
      CompletableFuture<Song> timedOut = new CompletableFuture<>();
      var timeoutResult = timeoutCoordinator.track(timedOut, 1, TimeUnit.SECONDS);
      timedOut.completeExceptionally(new TimeoutException());

      assertEquals(SongLoadCoordinator.Status.TIMED_OUT, timeoutResult.join().status());
      assertEquals("Song loading timed out.", SongLoadCoordinator.failureMessage(timeoutResult.join()));

      SongLoadCoordinator cancelledCoordinator = new SongLoadCoordinator();
      CompletableFuture<Song> cancelled = new CompletableFuture<>();
      var cancelledResult = cancelledCoordinator.track(cancelled, 1, TimeUnit.SECONDS);
      cancelled.cancel(true);

      assertEquals(SongLoadCoordinator.Status.CANCELLED, cancelledResult.join().status());
      assertEquals("Song loading cancelled.", SongLoadCoordinator.failureMessage(cancelledResult.join()));
   }

   @Test
   void failureWithoutMessageFallsBackToExceptionType() {
      SongLoadCoordinator coordinator = new SongLoadCoordinator();
      CompletableFuture<Song> source = new CompletableFuture<>();
      var result = coordinator.track(source, 1, TimeUnit.SECONDS);
      source.completeExceptionally(new IllegalStateException());

      assertEquals("Song loading failed: IllegalStateException",
            SongLoadCoordinator.failureMessage(result.join()));
   }

   @Test
   void nullSongIsAnExplicitEmptyResult() {
      SongLoadCoordinator coordinator = new SongLoadCoordinator();
      CompletableFuture<Song> source = CompletableFuture.completedFuture(null);
      SongLoadCoordinator.LoadResult result = coordinator.track(source, 1, TimeUnit.SECONDS).join();

      assertEquals(SongLoadCoordinator.Status.EMPTY, result.status());
      assertEquals("Song loader returned no song.", SongLoadCoordinator.failureMessage(result));
   }

   @Test
   void cancellationMakesOldCompletionStale() {
      SongLoadCoordinator coordinator = new SongLoadCoordinator();
      CompletableFuture<Song> first = new CompletableFuture<>();
      CompletableFuture<SongLoadCoordinator.LoadResult> firstResult = coordinator.track(first, 1, TimeUnit.SECONDS);

      coordinator.cancel();

      assertTrue(first.isCancelled());
      assertEquals(SongLoadCoordinator.Status.STALE, firstResult.join().status());
      assertFalse(coordinator.isLoading());
   }

   @Test
   void startingNewLoadMakesSupersededCompletionStale() {
      SongLoadCoordinator coordinator = new SongLoadCoordinator();
      CompletableFuture<Song> first = new CompletableFuture<>();
      CompletableFuture<SongLoadCoordinator.LoadResult> firstResult = coordinator.track(first, 1, TimeUnit.SECONDS);
      CompletableFuture<Song> second = new CompletableFuture<>();
      CompletableFuture<SongLoadCoordinator.LoadResult> secondResult = coordinator.track(second, 1, TimeUnit.SECONDS);

      assertTrue(first.isCancelled());
      assertEquals(SongLoadCoordinator.Status.STALE, firstResult.join().status());
      assertEquals(second, coordinator.activeFuture());

      Song song = new Song("second", "author");
      second.complete(song);
      assertEquals(SongLoadCoordinator.Status.SUCCESS, secondResult.join().status());
      assertEquals(song, secondResult.join().song());
   }
}
