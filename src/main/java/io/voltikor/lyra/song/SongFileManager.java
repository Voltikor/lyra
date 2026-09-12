package io.voltikor.lyra.song;

import io.voltikor.lyra.command.LyraMessenger;
import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.config.LyraSettingsStorage;
import io.voltikor.lyra.config.SongConfig;
import io.voltikor.lyra.config.SongConfigManager;
import io.voltikor.lyra.song.decoder.SongDecoders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Environment(EnvType.CLIENT)
public final class SongFileManager {
   private static final Logger LOGGER = LogManager.getLogger(SongFileManager.class);

   private Path configDir;
   private Path songsDir;
   private Path settingsFile;
   private final io.voltikor.lyra.config.PendingSettingsSave pendingSave = new io.voltikor.lyra.config.PendingSettingsSave();
   private final SongConfigManager songConfigManager = new SongConfigManager();

   public SongConfigManager songConfigManager() {
      return this.songConfigManager;
   }

   public Path configDir() {
      return this.configDir;
   }

   public Path songsDir() {
      return this.songsDir;
   }

   public Path settingsFile() {
      return this.settingsFile;
   }

   public void initialize(LyraSettings settings) {
      this.configDir = FabricLoader.getInstance().getConfigDir().resolve("Lyra");
      this.songsDir = FabricLoader.getInstance().getGameDir().resolve("Lyra").resolve("Songs");
      this.settingsFile = this.configDir.resolve("settings.json");

      try {
         Files.createDirectories(this.songsDir);
      } catch (IOException e) {
         LOGGER.error("Failed to create song folder {}", this.songsDir, e);
      }

      LyraSettingsStorage.load(this.settingsFile, settings);
      LyraSettingsStorage.save(this.settingsFile, settings);
      this.songConfigManager.initialize(this.configDir);
      LOGGER.info("Lyra initialized. Song folder: {}", this.songsDir.toAbsolutePath());
   }

   public CompletableFuture<Song> loadSongAsync(Path path, LyraSettings settings, Consumer<String> warningSink) {
      LyraSettings snapshot = this.settingsForSong(path, settings);
      return CompletableFuture.supplyAsync(() -> {
         try {
            Song decoded = SongDecoders.decode(path, snapshot);
            return new SongNormalizer().normalize(decoded, snapshot, warningSink);
         } catch (CancellationException cancelled) {
            throw cancelled;
         } catch (Exception exception) {
            throw new CompletionException(exception);
         }
      });
   }

   LyraSettings settingsForSong(Path path, LyraSettings settings) {
      SongConfig config = this.songConfigManager.getConfig(this.toStoredSongPath(path));
      LyraSettings snapshot = new LyraSettings();
      snapshot.setMode(settings.mode());
      snapshot.setTempoQuantization(config != null && config.tempoOverride() != null
            ? config.tempoOverride() : settings.tempoQuantization());
      snapshot.setOutOfRangeMode(config != null && config.outOfRangeModeOverride() != null
            ? config.outOfRangeModeOverride() : settings.outOfRangeMode());
      snapshot.setTranspose(config != null && config.transposeOverride() != null
            ? config.transposeOverride() : settings.transpose());
      for (NoteBlockInstrument source : settings.mappedSources()) {
         snapshot.setInstrumentOverride(source, settings.mapInstrument(source));
      }
      if (config != null) {
         for (NoteBlockInstrument source : config.mappedSources()) {
            snapshot.setInstrumentOverride(source, config.mapInstrument(source));
         }
      }
      return snapshot;
   }

   public Path selectedSongPath(LyraSettings settings) {
      return this.resolveStoredSongPath(settings.selectedSongPath());
   }

   public void setSelectedSongPath(LyraSettings settings, Path songPath, Runnable onStateChanged) {
      settings.setSelectedSongPath(this.toStoredSongPath(songPath));
      if (onStateChanged != null) {
         onStateChanged.run();
      }
   }

   static List<Path> listSongFiles(Path directory) throws IOException {
      try (Stream<Path> stream = Files.list(directory)) {
         return stream.filter(Files::isRegularFile).filter(SongDecoders::hasDecoder).sorted().toList();
      }
   }

   public List<Path> availableSongs() {
      if (this.songsDir == null) {
         return List.of();
      }
      try {
         return listSongFiles(this.songsDir);
      } catch (IOException e) {
         LOGGER.error("Failed to list songs from {}", this.songsDir, e);
         return List.of();
      }
   }

   public void listSongs(Minecraft client) {
      if (this.songsDir == null) {
         LyraMessenger.errorFormatted(client, "Song folder is not initialized.");
      } else {
         List<Path> songs;
         try {
            songs = listSongFiles(this.songsDir);
         } catch (IOException e) {
            LyraMessenger.errorFormatted(client, "Failed to list songs: %1$s", e.getMessage());
            return;
         }

         if (songs.isEmpty()) {
            LyraMessenger.replyFormatted(client, "No .nbs/.txt songs in %1$s", this.songsDir.toAbsolutePath());
         } else {
            LyraMessenger.replyFormatted(client, "Songs (%1$s):", songs.size());
            int max = Math.min(20, songs.size());

            for (int i = 0; i < max; ++i) {
               LyraMessenger.replyFormatted(client, "- %1$s", songs.get(i).getFileName());
            }

            if (songs.size() > max) {
               LyraMessenger.replyFormatted(client, "... and %1$s more", songs.size() - max);
            }
         }
      }
   }

   public Path resolveSongInput(String raw) {
      return this.resolveSongPath(raw);
   }

   public Path resolveSongPath(String raw) {
      if (raw == null) {
         return null;
      }
      try {
         Path input = Path.of(raw.trim());
         if (input.isAbsolute()) {
            return Files.isRegularFile(input) ? input.normalize() : this.tryAddExtensions(input);
         } else {
            if (this.songsDir == null) {
               return null;
            }
            Path inSongDir = this.songsDir.resolve(input).normalize();
            return Files.isRegularFile(inSongDir) ? inSongDir : this.tryAddExtensions(inSongDir);
         }
      } catch (InvalidPathException e) {
         return null;
      }
   }

   public Path tryAddExtensions(Path basePath) {
      if (basePath.getFileName() == null) {
         return null;
      } else {
         String name = basePath.getFileName().toString();
         if (name.contains(".")) {
            return null;
         } else {
            Path nbs = basePath.resolveSibling(name + ".nbs");
            if (Files.isRegularFile(nbs)) {
               return nbs;
            } else {
               Path txt = basePath.resolveSibling(name + ".txt");
               return Files.isRegularFile(txt) ? txt : null;
            }
         }
      }
   }

   public String toStoredSongPath(Path songPath) {
      if (songPath == null) {
         return "";
      } else {
         try {
            Path normalized = songPath.toAbsolutePath().normalize();
            if (this.songsDir != null) {
               Path root = this.songsDir.toAbsolutePath().normalize();
               if (normalized.startsWith(root)) {
                  return root.relativize(normalized).toString().replace('\\', '/');
               }
            }

            return normalized.toString();
         } catch (Exception e) {
            return songPath.toString();
         }
      }
   }

   public Path resolveStoredSongPath(String storedPath) {
      if (storedPath != null && !storedPath.isBlank()) {
         try {
            Path path = Path.of(storedPath);
            if (!path.isAbsolute() && this.songsDir != null) {
               path = this.songsDir.resolve(path);
            }

            Path normalized = path.normalize();
            return Files.isRegularFile(normalized) ? normalized : null;
         } catch (InvalidPathException e) {
            return null;
         }
      } else {
         return null;
      }
   }

   public static Path normalizePath(Path path) {
      if (path == null) {
         return null;
      } else {
         try {
            return path.toAbsolutePath().normalize();
         } catch (Exception e) {
            try {
               return path.normalize();
            } catch (Exception e2) {
               return null;
            }
         }
      }
   }

   public static boolean samePath(Path left, Path right) {
      Path normalizedLeft = normalizePath(left);
      Path normalizedRight = normalizePath(right);
      return normalizedLeft != null && normalizedRight != null && normalizedLeft.equals(normalizedRight);
   }

   public void saveSettingsIfChanged(LyraSettings settings) {
      this.pendingSave.saveIfDue(settings, () -> LyraSettingsStorage.save(this.settingsFile, settings), false);
   }

   public void flushSettings(LyraSettings settings) {
      this.pendingSave.saveIfDue(settings, () -> LyraSettingsStorage.save(this.settingsFile, settings), true);
   }

   public static Throwable unwrap(Throwable error) {
      return error instanceof CompletionException && error.getCause() != null ? unwrap(error.getCause()) : error;
   }
}
