package io.voltikor.lyra;

import io.voltikor.lyra.command.LyraCommandDispatcher;
import io.voltikor.lyra.command.LyraCommandHandlers;
import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.hud.LyraHudRenderer;
import io.voltikor.lyra.noteblock.NoteBlockScanner;
import io.voltikor.lyra.noteblock.NoteBlockTuner;
import io.voltikor.lyra.noteblock.NearbyNoteBlocksSnapshot;
import io.voltikor.lyra.noteblock.PlayerCenterer;
import io.voltikor.lyra.playback.PlaybackCoordinator;
import io.voltikor.lyra.playback.PlaybackSession;
import io.voltikor.lyra.playback.PlaybackSnapshot;
import io.voltikor.lyra.playback.SongPlaybackEngine;
import io.voltikor.lyra.song.Song;
import io.voltikor.lyra.song.SongFileManager;
import io.voltikor.lyra.song.SongLoadCoordinator;

import java.nio.file.Path;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class LyraController {
   private static final Logger LOGGER = LoggerFactory.getLogger("Lyra");
   private static final LyraController INSTANCE = new LyraController();

   private final LyraSettings settings = new LyraSettings();
   private final NoteBlockScanner scanner = new NoteBlockScanner();
   private final NoteBlockTuner tuner = new NoteBlockTuner();
   private final PlayerCenterer playerCenterer = new PlayerCenterer();
   private final SongFileManager fileManager = new SongFileManager();
   private final SongLoadCoordinator loadCoordinator = new SongLoadCoordinator();
   private final SongPlaybackEngine playbackEngine = new SongPlaybackEngine();
   private final PlaybackSession session = new PlaybackSession();
   private final PlaybackCoordinator playback = new PlaybackCoordinator(this.settings, this.fileManager, this.loadCoordinator,
         this.session, this.playbackEngine, this.scanner, this.tuner, this.playerCenterer::reset);
   private final LyraHudRenderer hudRenderer = new LyraHudRenderer((path, settings) -> this.fileManager.loadSongAsync(
         path, settings, warning -> LOGGER.debug("[HudSongAdjust] {}", warning)),
         path -> this.fileManager.songConfigManager().revision(this.fileManager.toStoredSongPath(path)));
   private final LyraCommandHandlers commandHandlers = new LyraCommandHandlers(this.settings, this.fileManager,
         this.playback, this.scanner, this.playerCenterer);
   private final LyraCommandDispatcher commandDispatcher = new LyraCommandDispatcher(this.commandHandlers);

   private boolean initialized;

   private LyraController() {
   }

   public static LyraController get() {
      return INSTANCE;
   }

   public net.minecraft.client.gui.screens.Screen createConfigScreen(net.minecraft.client.gui.screens.Screen parent) {
      this.initialize();
      this.playback.refreshNearbyPlayableBlocks(Minecraft.getInstance());
      return new io.voltikor.lyra.gui.LyraScreen(parent, this.settings, this.fileManager, this.playback, this.commandHandlers);
   }

   public void registerCommands(com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> dispatcher) {
      this.commandDispatcher.register(dispatcher);
   }

   public void initialize() {
      if (!this.initialized) {
         this.fileManager.initialize(this.settings);
         this.initialized = true;
      }
   }

   private Path selectedSongPath() {
      return this.playback.selectedSongPath();
   }

   public void onTick(Minecraft client) {
      if (this.initialized && client != null) {
         this.refreshSelectedHudSongState();
         this.ensureSelectedHudSongLoaded(client);

         this.playback.tick(client);
         this.tickCentering(client);
         this.saveSettingsIfChanged();
         this.tickPlayableBlocksScan(client);
      }
   }

   public void renderHud(GuiGraphicsExtractor graphics, Minecraft client) {
      PlaybackSnapshot snapshot = this.playback.snapshot();
      NearbyNoteBlocksSnapshot nearby = this.scanner.nearbySnapshot();
      this.hudRenderer.renderHud(graphics, client, this.settings, snapshot.song(),
            snapshot.loadedSongPath(), this.selectedSongPath(), nearby.playableTotal(),
            nearby.playableByInstrument(), snapshot.currentTick());
   }

   public boolean handleChatCommand(Minecraft client, String chatMessage) {
      return this.commandDispatcher.handleChatCommand(client, chatMessage);
   }

   public boolean handleSlashCommand(Minecraft client, String commandLine) {
      return this.commandDispatcher.handleSlashCommand(client, commandLine);
   }

   private void tickCentering(Minecraft client) {
      this.playerCenterer.tickCentering(client);
   }

   public void onTogglePlayPauseKeybind(Minecraft client) {
      this.playback.togglePlayPause(client);
   }

   public void flushSettings() {
      this.fileManager.flushSettings(this.settings);
   }

   private void tickPlayableBlocksScan(Minecraft client) {
      this.scanner.tickPlayableBlocksScan(client,
            this.requiredSongForHud() != null || client.screen instanceof io.voltikor.lyra.gui.LyraScreen, this.settings);
   }

   private Song requiredSongForHud() {
      PlaybackSnapshot snapshot = this.playback.snapshot();
      return this.hudRenderer.requiredSongForHud(snapshot.song(), snapshot.loadedSongPath(),
            this.selectedSongPath());
   }

   private void refreshSelectedHudSongState() {
      PlaybackSnapshot snapshot = this.playback.snapshot();
      this.hudRenderer.refreshSelectedHudSongState(this.selectedSongPath(), snapshot.song(),
            snapshot.loadedSongPath());
   }

   private void ensureSelectedHudSongLoaded(Minecraft client) {
      PlaybackSnapshot snapshot = this.playback.snapshot();
      this.hudRenderer.ensureSelectedHudSongLoaded(client, this.selectedSongPath(), snapshot.song(),
            snapshot.loadedSongPath(), this.settings);
   }

   private void saveSettingsIfChanged() {
      this.fileManager.saveSettingsIfChanged(this.settings);
   }

}
