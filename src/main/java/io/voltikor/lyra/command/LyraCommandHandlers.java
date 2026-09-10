package io.voltikor.lyra.command;

import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.noteblock.NoteBlockScanner;
import io.voltikor.lyra.noteblock.PlayerCenterer;
import io.voltikor.lyra.playback.PlaybackCoordinator;
import io.voltikor.lyra.playback.PlaybackSnapshot;
import io.voltikor.lyra.playback.SongLoadIntent;
import io.voltikor.lyra.song.Song;
import io.voltikor.lyra.song.SongFileManager;
import io.voltikor.lyra.song.decoder.SongDecoders;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public final class LyraCommandHandlers {
   private final LyraSettings settings;
   private final SongFileManager songs;
   private final PlaybackCoordinator playback;
   private final NoteBlockScanner scanner;
   private final PlayerCenterer centerer;
   private final LyraSettingsCommands settingsCommands = new LyraSettingsCommands();
   private final SongConfigCommands songConfigCommands;

   public LyraCommandHandlers(LyraSettings settings, SongFileManager songs, PlaybackCoordinator playback,
         NoteBlockScanner scanner, PlayerCenterer centerer) {
      this.settings = settings;
      this.songs = songs;
      this.playback = playback;
      this.scanner = scanner;
      this.centerer = centerer;
      this.songConfigCommands = new SongConfigCommands(songs);
   }

   public void listSongs(Minecraft client) {
      this.songs.listSongs(client);
   }

   public boolean load(Minecraft client, String args) {
      if (args.isEmpty()) {
         LyraMessenger.errorFormatted(client, "Usage: /lyra load <file>");
         return false;
      }
      return this.resolveAndLoad(client, args, SongLoadIntent.LOAD_ONLY);
   }

   public boolean play(Minecraft client, String args) {
      return args.isEmpty() ? this.playback.play(client) : this.resolveAndLoad(client, args, SongLoadIntent.PLAY);
   }

   public boolean preview(Minecraft client, String args) {
      return args.isEmpty() ? this.playback.preview(client) : this.resolveAndLoad(client, args, SongLoadIntent.PREVIEW);
   }

   public boolean tune(Minecraft client) {
      return this.playback.startTuning(client, false);
   }

   public boolean pause(Minecraft client) {
      return this.playback.pause(client);
   }

   public void stop(Minecraft client) {
      this.playback.stop(client);
   }

   public boolean playRandom(Minecraft client) {
      return this.playback.playRandom(client);
   }

   public void centerPlayer(Minecraft client) {
      this.centerer.centerPlayer(client);
   }

   public void sendStatus(Minecraft client) {
      PlaybackSnapshot snapshot = this.playback.snapshot();
      Song song = snapshot.song();
      Path loadedSongPath = snapshot.loadedSongPath();
      String songName = song == null ? "none" : song.title();
      String fileName = loadedSongPath == null ? "none" : loadedSongPath.getFileName().toString();
      LyraMessenger.replyFormatted(client, "Stage=%1$s, playing=%2$s, mode=%3$s, song=%4$s (%5$s)",
            snapshot.stage().displayName(), snapshot.playing(), snapshot.mode(), songName, fileName);
      LyraMessenger.replyFormatted(client,
            "tick=%1$s, mapped=%2$s, tuneQueue=%3$s, settings={delay=%4$s, concurrent=%5$s, polyphonic=%6$s}",
            snapshot.currentTick(), this.scanner.noteBlockPositions().size(), this.scanner.tuneHits().size(),
            this.settings.tickDelay(), this.settingsCommands.concurrentTuneBlocksLabel(this.settings), this.settings.polyphonic());
   }

   public String songsFolderLabel() {
      Path directory = this.songs.songsDir();
      return directory == null ? "not initialized" : directory.toAbsolutePath().toString();
   }

   public List<String> availableSongNames() {
      return this.songs.availableSongs().stream().map(path -> path.getFileName().toString()).toList();
   }

   public boolean handleSongConfig(Minecraft client, String args) {
      PlaybackSnapshot snapshot = this.playback.snapshot();
      Path activePath = snapshot.loadedSongPath() != null ? snapshot.loadedSongPath() : this.playback.selectedSongPath();
      boolean valid = this.songConfigCommands.handleSongCommand(client, args, activePath);
      if (valid && !this.songs.songConfigManager().lastSaveSucceeded()) {
         LyraMessenger.error(client, "Song overrides changed in memory, but could not be saved. See the log.");
         return false;
      }
      return valid;
   }

   public boolean setMode(Minecraft client, String args) {
      return this.settingsCommands.setMode(client, this.settings, args);
   }

   public boolean setInstrumentDetectMode(Minecraft client, String args) {
      return this.settingsCommands.setInstrumentDetectMode(client, this.settings, args);
   }

   public boolean setTempoQuantization(Minecraft client, String args) {
      return this.settingsCommands.setTempoQuantization(client, this.settings, args);
   }

   public boolean setTickDelay(Minecraft client, String args) {
      return this.settingsCommands.setTickDelay(client, this.settings, args);
   }

   public boolean setCheckNoteblocksAgainDelay(Minecraft client, String args) {
      return this.settingsCommands.setCheckNoteblocksAgainDelay(client, this.settings, args);
   }

   public boolean setConcurrentTuneBlocks(Minecraft client, String args) {
      return this.settingsCommands.setConcurrentTuneBlocks(client, this.settings, args);
   }

   public boolean setBooleanFlag(Minecraft client, String key, String args) {
      return this.settingsCommands.setBooleanFlag(client, this.settings, key, args);
   }

   public boolean setTranspose(Minecraft client, String args) {
      return this.settingsCommands.setTranspose(client, this.settings, args);
   }

   public boolean setRotateMode(Minecraft client, String args) {
      return this.settingsCommands.setRotateMode(client, this.settings, args);
   }

   public boolean setOutOfRangeMode(Minecraft client, String args) {
      return this.settingsCommands.setOutOfRangeMode(client, this.settings, args);
   }

   public boolean handleHudCommand(Minecraft client, String args) {
      return this.settingsCommands.handleHudCommand(client, this.settings, args);
   }

   public boolean setInstrumentMap(Minecraft client, String args) {
      return this.settingsCommands.setInstrumentMap(client, this.settings, args);
   }

   private boolean resolveAndLoad(Minecraft client, String args, SongLoadIntent intent) {
      Path songPath = this.songs.resolveSongPath(args);
      if (songPath == null || !Files.isRegularFile(songPath)) {
         LyraMessenger.errorFormatted(client, "Song not found: %1$s", args);
         return false;
      }
      if (!SongDecoders.hasDecoder(songPath)) {
         LyraMessenger.errorFormatted(client, "Unsupported song extension. Use .nbs or .txt");
         return false;
      }
      return this.playback.load(client, songPath, intent);
   }
}
