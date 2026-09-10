package io.voltikor.lyra.song;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class SongLoadCoordinator {
   private CompletableFuture<Song> activeFuture;

   public synchronized CompletableFuture<Song> activeFuture() {
      return this.activeFuture;
   }

   public synchronized boolean isLoading() {
      return this.activeFuture != null;
   }

   public CompletableFuture<LoadResult> track(CompletableFuture<Song> future, long timeout, TimeUnit unit) {
      if (future == null) {
         return CompletableFuture.completedFuture(LoadResult.failed(new NullPointerException("Song load future was null")));
      }
      CompletableFuture<Song> previous;
      synchronized (this) {
         previous = this.activeFuture;
         this.activeFuture = future;
      }
      if (previous != null && previous != future) {
         previous.cancel(true);
      }
      return future.orTimeout(timeout, unit).handle((song, error) -> this.complete(future, song, error));
   }

   public synchronized void cancel() {
      CompletableFuture<Song> future = this.activeFuture;
      this.activeFuture = null;
      if (future != null) {
         future.cancel(true);
      }
   }

   private synchronized LoadResult complete(CompletableFuture<Song> source, Song song, Throwable error) {
      if (this.activeFuture != source) {
         return LoadResult.stale();
      }
      this.activeFuture = null;
      Throwable actual = SongFileManager.unwrap(error);
      if (actual instanceof CancellationException) {
         return LoadResult.cancelled(actual);
      }
      if (actual instanceof TimeoutException) {
         return LoadResult.timedOut(actual);
      }
      if (actual != null) {
         return LoadResult.failed(actual);
      }
      if (song == null) {
         return LoadResult.empty();
      }
      return LoadResult.success(song);
   }

   public static String failureMessage(LoadResult result) {
      return switch (result.status()) {
         case CANCELLED -> "Song loading cancelled.";
         case TIMED_OUT -> "Song loading timed out.";
         case EMPTY -> "Song loader returned no song.";
         case FAILED -> {
            Throwable error = result.error();
            String detail = error == null ? null : error.getMessage();
            if (detail == null || detail.isBlank()) {
               detail = error == null ? "Unknown error" : error.getClass().getSimpleName();
            }
            yield "Song loading failed: " + detail;
         }
         default -> throw new IllegalArgumentException("No failure message for " + result.status());
      };
   }

   public enum Status { SUCCESS, CANCELLED, TIMED_OUT, FAILED, EMPTY, STALE }

   public record LoadResult(Status status, Song song, Throwable error) {
      static LoadResult success(Song song) { return new LoadResult(Status.SUCCESS, song, null); }
      static LoadResult cancelled(Throwable error) { return new LoadResult(Status.CANCELLED, null, error); }
      static LoadResult timedOut(Throwable error) { return new LoadResult(Status.TIMED_OUT, null, error); }
      static LoadResult failed(Throwable error) { return new LoadResult(Status.FAILED, null, error); }
      static LoadResult empty() { return new LoadResult(Status.EMPTY, null, null); }
      static LoadResult stale() { return new LoadResult(Status.STALE, null, null); }
   }
}
