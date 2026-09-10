package io.voltikor.lyra.hud;

import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.song.Song;
import io.voltikor.lyra.song.SongFileManager;
import io.voltikor.lyra.song.decoder.SongDecoders;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;
import java.util.function.ToLongFunction;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Caches decoded previews by selection and configuration, never by mutable settings contents. */
final class SelectedSongCache {
   private static final Logger LOGGER = LoggerFactory.getLogger("Lyra");
   private record Key(Path path, long globalRevision, long songRevision) {}
   private final BiFunction<Path, LyraSettings, CompletableFuture<Song>> loader;
   private final ToLongFunction<Path> songRevision;
   private final LongSupplier clock;
   private LyraSettings settings;
   private Key key;
   private Song preview;
   private CompletableFuture<Song> pending;
   private int failures;
   private long retryAt;
   private String lastFailure;

   SelectedSongCache(BiFunction<Path, LyraSettings, CompletableFuture<Song>> loader) {
      this(loader, path -> 0, System::nanoTime);
   }

   SelectedSongCache(BiFunction<Path, LyraSettings, CompletableFuture<Song>> loader,
         ToLongFunction<Path> songRevision, LongSupplier clock) {
      this.loader = loader;
      this.songRevision = songRevision;
      this.clock = clock;
   }

   private Key keyFor(Path path) {
      Path normalized = SongFileManager.normalizePath(path);
      return normalized == null ? null : new Key(normalized,
            this.settings == null ? 0 : this.settings.decodingRevision(), this.songRevision.applyAsLong(normalized));
   }

   Song requiredSongForHud(Song currentSong, Path loadedSongPath, Path selectedSongPath) {
      if (selectedSongPath == null || currentSong != null && SongFileManager.samePath(loadedSongPath, selectedSongPath)) {
         return currentSong;
      }
      return Objects.equals(this.key, this.keyFor(selectedSongPath)) ? this.preview : null;
   }

   void refreshSelectedHudSongState(Path path, Song currentSong, Path loadedPath) {
      if (path == null) this.clearSelectedHudSong();
      else if (currentSong != null && SongFileManager.samePath(loadedPath, path)) {
         // Keep the active song available as the HUD requirements source.
         this.clearSelectedHudSong();
         this.key = this.keyFor(path);
         this.preview = currentSong;
      }
      else this.select(this.keyFor(path));
   }

   private void select(Key next) {
      if (Objects.equals(this.key, next)) return;
      this.clearSelectedHudSong();
      this.key = next;
   }

   void ensureSelectedHudSongLoaded(Minecraft client, Path path, Song currentSong, Path loadedPath, LyraSettings settings) {
      this.ensureLoaded(task -> (client == null ? Minecraft.getInstance() : client).execute(task),
            path, currentSong, loadedPath, settings);
   }

   void ensureLoaded(Executor clientExecutor, Path path, Song currentSong, Path loadedPath, LyraSettings settings) {
      this.settings = settings;
      if (path == null || currentSong != null && SongFileManager.samePath(loadedPath, path)) {
         this.clearSelectedHudSong();
         return;
      }
      Key next = this.keyFor(path);
      this.select(next);
      if (!Files.isRegularFile(next.path()) || !SongDecoders.hasDecoder(next.path())) return;
      if (this.preview != null || this.pending != null) return;
      if (this.failures > 0 && this.clock.getAsLong() - this.retryAt < 0) return;
      CompletableFuture<Song> future;
      try {
         future = Objects.requireNonNull(this.loader.apply(next.path(), settings));
      } catch (Exception error) {
         future = CompletableFuture.failedFuture(error);
      }
      this.pending = future;
      CompletableFuture<Song> request = future;
      future.orTimeout(30, TimeUnit.SECONDS).whenComplete((song, error) ->
            clientExecutor.execute(() -> this.complete(request, next, song, error)));
   }

   private void complete(CompletableFuture<Song> request, Key requestKey, Song song, Throwable error) {
      if (this.pending != request) return;
      this.pending = null;
      if (!Objects.equals(this.key, requestKey) || !Objects.equals(requestKey, this.keyFor(requestKey.path()))) return;
      if (error == null && song != null) {
         this.preview = song;
         this.failures = 0;
         this.lastFailure = null;
         return;
      }
      Throwable actual = error == null ? new IllegalStateException("Decoder returned no song") : SongFileManager.unwrap(error);
      if (actual instanceof CancellationException) return;
      int seconds = switch (Math.min(this.failures++, 3)) { case 0 -> 5; case 1 -> 10; case 2 -> 20; default -> 30; };
      this.retryAt = this.clock.getAsLong() + TimeUnit.SECONDS.toNanos(seconds);
      String description = actual.getClass().getName() + ": " + actual.getMessage();
      if (!description.equals(this.lastFailure)) LOGGER.warn("Failed to load selected HUD song {}", requestKey.path(), actual);
      this.lastFailure = description;
   }

   void clearSelectedHudSong() {
      CompletableFuture<Song> previous = this.pending;
      this.pending = null;
      if (previous != null) previous.cancel(true);
      this.preview = null;
      this.key = null;
      this.failures = 0;
      this.lastFailure = null;
   }
}
