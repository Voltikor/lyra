package io.voltikor.lyra.command;

import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.config.TransposeSetting;
import io.voltikor.lyra.hud.HudAnchor;
import io.voltikor.lyra.noteblock.InstrumentDetectMode;
import io.voltikor.lyra.noteblock.InstrumentMatchMode;
import io.voltikor.lyra.noteblock.NoteBlockScanner;
import io.voltikor.lyra.noteblock.PlayerCenterer;
import io.voltikor.lyra.noteblock.RotateMode;
import io.voltikor.lyra.playback.PlaybackCoordinator;
import io.voltikor.lyra.playback.PlaybackSnapshot;
import io.voltikor.lyra.playback.SongLoadIntent;
import io.voltikor.lyra.song.OutOfRangeMode;
import io.voltikor.lyra.song.Song;
import io.voltikor.lyra.song.SongFileManager;
import io.voltikor.lyra.song.TempoQuantization;
import io.voltikor.lyra.song.decoder.SongDecoders;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

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

   public boolean showSongConfig(Minecraft client) {
      return this.applySongCommand(client, activePath -> this.songConfigCommands.showConfig(client, activePath));
   }

   public boolean clearSongConfig(Minecraft client) {
      return this.applySongCommand(client, activePath -> this.songConfigCommands.clearConfig(client, activePath));
   }

   public boolean setSongTempo(Minecraft client, TempoQuantization mode) {
      return this.applySongCommand(client, activePath -> this.songConfigCommands.setTempoOverride(client, activePath, mode));
   }

   public boolean clearSongTempo(Minecraft client) {
      return this.applySongCommand(client, activePath -> this.songConfigCommands.clearTempoOverride(client, activePath));
   }

   public boolean setSongTranspose(Minecraft client, TransposeSetting transpose) {
      return this.applySongCommand(client,
            activePath -> this.songConfigCommands.setTransposeOverride(client, activePath, transpose));
   }

   public boolean clearSongTranspose(Minecraft client) {
      return this.applySongCommand(client, activePath -> this.songConfigCommands.clearTransposeOverride(client, activePath));
   }

   public boolean setSongOutOfRangeMode(Minecraft client, OutOfRangeMode mode) {
      return this.applySongCommand(client,
            activePath -> this.songConfigCommands.setOutOfRangeModeOverride(client, activePath, mode));
   }

   public boolean clearSongOutOfRangeMode(Minecraft client) {
      return this.applySongCommand(client,
            activePath -> this.songConfigCommands.clearOutOfRangeModeOverride(client, activePath));
   }

   public boolean listSongInstrumentMap(Minecraft client) {
      return this.applySongCommand(client,
            activePath -> this.songConfigCommands.listInstrumentOverrides(client, activePath));
   }

   public boolean clearSongInstrumentMap(Minecraft client) {
      return this.applySongCommand(client,
            activePath -> this.songConfigCommands.clearInstrumentOverrides(client, activePath));
   }

   public boolean removeSongInstrumentMap(Minecraft client, NoteBlockInstrument from) {
      return this.applySongCommand(client,
            activePath -> this.songConfigCommands.removeInstrumentOverride(client, activePath, from));
   }

   public boolean setSongInstrumentMap(Minecraft client, NoteBlockInstrument from, NoteBlockInstrument to) {
      return this.applySongCommand(client,
            activePath -> this.songConfigCommands.setInstrumentOverride(client, activePath, from, to));
   }

   public boolean setMode(Minecraft client, InstrumentMatchMode mode) {
      return this.settingsCommands.setMode(client, this.settings, mode);
   }

   public boolean setInstrumentDetectMode(Minecraft client, InstrumentDetectMode mode) {
      return this.settingsCommands.setInstrumentDetectMode(client, this.settings, mode);
   }

   public boolean setTempoQuantization(Minecraft client, TempoQuantization quantization) {
      return this.settingsCommands.setTempoQuantization(client, this.settings, quantization);
   }

   public boolean setTickDelay(Minecraft client, int ticks) {
      return this.settingsCommands.setTickDelay(client, this.settings, ticks);
   }

   public boolean setCheckNoteblocksAgainDelay(Minecraft client, int ticks) {
      return this.settingsCommands.setCheckNoteblocksAgainDelay(client, this.settings, ticks);
   }

   public boolean setConcurrentTuneBlocks(Minecraft client, int count) {
      return this.settingsCommands.setConcurrentTuneBlocks(client, this.settings, count);
   }

   public boolean setBooleanFlag(Minecraft client, BooleanSetting setting, boolean enabled) {
      return this.settingsCommands.setBooleanFlag(client, this.settings, setting, enabled);
   }

   public boolean setTranspose(Minecraft client, TransposeSetting transpose) {
      return this.settingsCommands.setTranspose(client, this.settings, transpose);
   }

   public boolean setRotateMode(Minecraft client, RotateMode mode) {
      return this.settingsCommands.setRotateMode(client, this.settings, mode);
   }

   public boolean setOutOfRangeMode(Minecraft client, OutOfRangeMode mode) {
      return this.settingsCommands.setOutOfRangeMode(client, this.settings, mode);
   }

   public boolean setShowHud(Minecraft client, boolean enabled) {
      return this.settingsCommands.setShowHud(client, this.settings, enabled);
   }

   public boolean toggleHud(Minecraft client) {
      return this.settingsCommands.toggleHud(client, this.settings);
   }

   public boolean setHudAnchor(Minecraft client, HudAnchor anchor) {
      return this.settingsCommands.setHudAnchor(client, this.settings, anchor);
   }

   public boolean setHudAutoHide(Minecraft client, boolean enabled) {
      return this.settingsCommands.setHudAutoHide(client, this.settings, enabled);
   }

   public boolean listInstrumentMap(Minecraft client) {
      return this.settingsCommands.listInstrumentMap(client, this.settings);
   }

   public boolean clearInstrumentMap(Minecraft client) {
      return this.settingsCommands.clearInstrumentMap(client, this.settings);
   }

   public boolean removeInstrumentMap(Minecraft client, NoteBlockInstrument from) {
      return this.settingsCommands.removeInstrumentMap(client, this.settings, from);
   }

   public boolean setInstrumentMap(Minecraft client, NoteBlockInstrument from, NoteBlockInstrument to) {
      return this.settingsCommands.setInstrumentMap(client, this.settings, from, to);
   }

   private boolean applySongCommand(Minecraft client, Function<Path, Boolean> command) {
      PlaybackSnapshot snapshot = this.playback.snapshot();
      Path activePath = snapshot.loadedSongPath() != null ? snapshot.loadedSongPath() : this.playback.selectedSongPath();
      boolean valid = command.apply(activePath);
      if (valid && !this.songs.songConfigManager().lastSaveSucceeded()) {
         LyraMessenger.error(client, "Song overrides changed in memory, but could not be saved. See the log.");
         return false;
      }
      return valid;
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
