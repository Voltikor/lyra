package io.voltikor.lyra.gui.screen;

import io.voltikor.lyra.gui.DeskTheme;
import io.voltikor.lyra.playback.PlaybackStage;
import io.voltikor.lyra.playback.SongLoadIntent;
import io.voltikor.lyra.song.SongFileManager;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class PlayerSection implements LyraScreenSection {
   private static final int LIBRARY_HEADING_MIN_HEIGHT = 270;
   private static final int LIBRARY_HEADING_HEIGHT = 16;
   private static final int LIBRARY_SEARCH_TOP = 162;
   private static final int LIBRARY_LIST_TOP = 186;
   private static final int FOOTER_RESERVED_HEIGHT = 34;

   private List<ScrollableChoices.Option<Path>> library;
   private ScrollableChoices<Path> songList;
   private Path librarySelection;
   private double libraryScroll;
   private String query = "";
   private SongSeekBar seekBar;

   @Override
   public void build(LyraScreen screen) {
      if (songList != null) libraryScroll = songList.scrollAmount();
      songList = null;
      seekBar = screen.widget(new SongSeekBar(screen.left(), 90, screen.span(), screen.playback()));
      int gap = 4;
      int width = (screen.span() - gap * 3) / 4;
      Button play = screen.button("Play", screen.left(), 114, width, () -> {
         if (PlayerControls.needsNoteBlocks(screen.playback().snapshot(), screen.selectedIsLoaded())
               && screen.playback().refreshNearbyPlayableBlocks(screen.minecraftClient()) == 0) {
            screen.feedback("Move near a playable note block, or use Preview.");
            return;
         }
         if (screen.playback().snapshot().playing()) screen.playback().pause(screen.minecraftClient());
         else screen.startSelected(SongLoadIntent.PLAY);
      });
      Button preview = screen.button("Preview", screen.left() + width + gap, 114, width,
            () -> screen.startSelected(SongLoadIntent.PREVIEW));
      Button stop = screen.button("Stop", screen.left() + (width + gap) * 2, 114, width,
            () -> screen.playback().stop(screen.minecraftClient()));
      screen.button("Music settings", screen.left() + (width + gap) * 3, 114, width,
            screen::editSelectedSongMusic);
      Button tune = screen.button("Tune", screen.left(), 138, width, () -> {
         if (screen.selectedIsLoaded()) screen.playback().startTuning(screen.minecraftClient(), false);
         else screen.feedback("Load the selected song before tuning.");
      });
      Button load = screen.button("Reload", screen.left() + width + gap, 138, width, () -> {
         if (screen.playback().selectedSongPath() != null) {
            screen.playback().load(screen.minecraftClient(), screen.playback().selectedSongPath(), SongLoadIntent.LOAD_ONLY);
         }
      });
      Button random = screen.button("Shuffle", screen.left() + (width + gap) * 2, 138, width,
            () -> screen.playback().playRandom(screen.minecraftClient()));
      Button center = screen.button("Center", screen.left() + (width + gap) * 3, 138, width,
            () -> screen.commands().centerPlayer(screen.minecraftClient()));
      buildLibrary(screen);

      screen.hint(play, "Play requires at least one reachable note block with air above it, and survival mode. Preview needs no blocks.");
      screen.hint(preview, "Listen locally without tuning or playing note blocks. Pause first to switch playback modes.");
      screen.hint(load, "Apply music settings and song overrides by loading the selected song again. This stops current playback.");
      screen.hint(tune, "Tune nearby blocks for the loaded song without starting playback.");
      screen.hint(random, "Pick and play a random song from your library.");
      screen.hint(center, "Move to the center of the block you are standing on.");
      screen.liveUpdate(() -> {
         var state = screen.playback().snapshot();
         boolean ready = state.stage() == PlaybackStage.IDLE || state.canSeek();
         boolean selected = screen.playback().selectedSongPath() != null;
         boolean ended = state.hasSong() && state.currentTick() > state.song().lastTick();
         play.setMessage(Component.literal(state.playing() ? "Pause" : ended ? "Replay"
               : state.stage() == PlaybackStage.PAUSED ? "Resume" : "Play"));
         play.active = PlayerControls.canUseTransport(state, screen.inWorld(), selected,
               screen.selectedIsLoaded(), screen.playback().nearbyPlayableBlocks());
         preview.active = screen.inWorld() && ready && !state.playing() && (state.hasSong() || selected);
         stop.active = state.stage() != PlaybackStage.IDLE;
         tune.active = screen.inWorld() && screen.selectedIsLoaded() && state.canSeek() && !state.playing();
         load.active = screen.inWorld() && selected;
         random.active = screen.inWorld();
         center.active = screen.inWorld();
      });
   }

   private void buildLibrary(LyraScreen screen) {
      if (library == null) readLibrary(screen);
      librarySelection = screen.playback().selectedSongPath();
      songList = screen.widget(new ScrollableChoices<>(screen.left(), libraryListTop(screen), screen.span(),
            Math.max(ScrollListModel.ROW_HEIGHT, screen.height() - libraryListTop(screen) - FOOTER_RESERVED_HEIGHT),
            "Song library", path -> SongFileManager.samePath(path, librarySelection), path -> {
               librarySelection = path;
               screen.playback().setSelectedSongPath(path);
               if (screen.inWorld()) screen.playback().load(screen.minecraftClient(), path, SongLoadIntent.LOAD_ONLY);
            }));
      EditBox search = screen.widget(new DeskEditBox(screen.font(), screen.left(), librarySearchTop(screen),
            screen.span() - 74, 20, Component.literal("Search songs")));
      search.setMaxLength(256);
      search.setHint(Component.literal("Search your songs…"));
      search.setValue(query);
      search.setResponder(value -> {
         query = value;
         libraryScroll = 0;
         refreshLibrary(screen);
      });
      screen.button("Refresh", screen.left() + screen.span() - 70, librarySearchTop(screen), 70, () -> {
         readLibrary(screen);
         refreshLibrary(screen);
      });
      refreshLibrary(screen);
      screen.hint(search, "Songs in " + screen.commands().songsFolderLabel());
   }

   private void readLibrary(LyraScreen screen) {
      library = screen.files().availableSongs().stream()
            .map(path -> new ScrollableChoices.Option<>(path, screen.files().toStoredSongPath(path))).toList();
   }

   private void refreshLibrary(LyraScreen screen) {
      String filter = query.strip().toLowerCase(Locale.ROOT);
      var found = library.stream().filter(entry -> entry.label().toLowerCase(Locale.ROOT).contains(filter)).toList();
      songList.setOptions(found);
      songList.scrollTo(libraryScroll);
      screen.feedback(found.isEmpty() ? "No songs found. Add .nbs or .txt files to the song folder."
            : found.size() + " songs in your library");
   }

   private int libraryGap(LyraScreen screen) {
      return screen.height() >= LIBRARY_HEADING_MIN_HEIGHT ? LIBRARY_HEADING_HEIGHT : 0;
   }

   private int librarySearchTop(LyraScreen screen) { return LIBRARY_SEARCH_TOP + libraryGap(screen); }
   private int libraryListTop(LyraScreen screen) { return LIBRARY_LIST_TOP + libraryGap(screen); }

   @Override
   public void tick(LyraScreen screen) {
      if (seekBar != null) seekBar.sync();
      if (songList != null) librarySelection = screen.playback().selectedSongPath();
   }

   @Override
   public void renderHeader(LyraScreen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
      if (libraryGap(screen) > 0) graphics.text(screen.font(), "LIBRARY", screen.left(), 165, DeskTheme.TEXT_SECONDARY, false);
      var state = screen.playback().snapshot();
      String title = SongDisplay.title(state, screen.playback().selectedSongPath());
      graphics.text(screen.font(), screen.font().plainSubstrByWidth(title, screen.span()),
            screen.left(), 60, DeskTheme.TEXT_PRIMARY, false);
      String subtitle = !screen.inWorld() ? "Join a world to listen, tune, and play."
            : state.hasSong() ? (state.currentTick() > state.song().lastTick() ? "Finished" : LyraScreen.pretty(state.stage()))
                  + " · " + LyraScreen.pretty(state.mode()) + " · "
                  + (state.loadedSongPath() == null ? "" : state.loadedSongPath().getFileName())
            : "";
      graphics.text(screen.font(), screen.font().plainSubstrByWidth(subtitle, screen.span()),
            screen.left(), 76, DeskTheme.TEXT_SECONDARY, false);
      if (!screen.hasDropdown() && mouseY >= 58 && mouseY < 88
            && mouseX >= screen.left() && mouseX <= screen.left() + screen.span()) {
         graphics.setTooltipForNextFrame(screen.font(), Component.literal(title + "\n" + subtitle
               + (state.hasSong() && state.song().author() != null && !state.song().author().isBlank()
                     ? "\n" + state.song().author() : "")), mouseX, mouseY);
      }
   }
}
