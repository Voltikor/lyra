package io.voltikor.lyra.gui;

import io.voltikor.lyra.command.LyraCommandHandlers;
import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.config.SongConfig;
import io.voltikor.lyra.config.TransposeSetting;
import io.voltikor.lyra.hud.HudAnchor;
import io.voltikor.lyra.noteblock.InstrumentDetectMode;
import io.voltikor.lyra.noteblock.InstrumentIcons;
import io.voltikor.lyra.noteblock.InstrumentMatchMode;
import io.voltikor.lyra.noteblock.RotateMode;
import io.voltikor.lyra.playback.PlaybackCoordinator;
import io.voltikor.lyra.playback.PlaybackStage;
import io.voltikor.lyra.playback.SongLoadIntent;
import io.voltikor.lyra.song.OutOfRangeMode;
import io.voltikor.lyra.song.SongFileManager;
import io.voltikor.lyra.song.TempoQuantization;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

public final class LyraScreen extends Screen {
   private static final int LIBRARY_HEADING_MIN_HEIGHT = 270;
   private static final int LIBRARY_HEADING_HEIGHT = 16;
   private static final int LIBRARY_SEARCH_TOP = 162;
   private static final int LIBRARY_LIST_TOP = 186;
   private static final int FOOTER_RESERVED_HEIGHT = 34;
   private final Screen parent;
   private final LyraSettings settings;
   private final SongFileManager files;
   private final PlaybackCoordinator playback;
   private final LyraCommandHandlers commands;
   private final List<Consumer<Integer>> rows = new ArrayList<>();
   private final List<Runnable> liveUpdates = new ArrayList<>();
   private final List<Label> labels = new ArrayList<>();
   private final EnumMap<NoteBlockInstrument, ItemStack> instrumentIcons = new EnumMap<>(NoteBlockInstrument.class);
   private List<ScrollableChoices.Option<Path>> library;
   private ScrollableChoices<Path> songList;
   private Path librarySelection;
   private ScrollableChoices<?> dropdown;
   private Button dropdownAnchor;
   private AbstractWidget mapsHelp;
   private double libraryScroll;
   private Section section = Section.PLAYER;
   private int page;
   private int pageCount = 1;
   private int left;
   private int span;
   private boolean songMappings;
   private String query = "";
   private String feedback = "";
   private SongSeekBar seekBar;

   private enum Section {
      PLAYER("Player"), TUNING("Tuning"), MUSIC("Music"), HUD("HUD"), SONG("Song"), MAPS("Maps");
      final String label;
      Section(String label) { this.label = label; }
   }

   private record Label(String value, int x, int y, int maxWidth) {}

   public LyraScreen(Screen parent, LyraSettings settings, SongFileManager files,
         PlaybackCoordinator playback, LyraCommandHandlers commands) {
      super(Component.literal("Lyra"));
      this.parent = parent;
      this.settings = settings;
      this.files = files;
      this.playback = playback;
      this.commands = commands;
   }

   @Override
   protected void init() {
      if (songList != null) libraryScroll = songList.scrollAmount();
      clearWidgets();
      rows.clear();
      labels.clear();
      liveUpdates.clear();
      songList = null;
      dropdown = null;
      dropdownAnchor = null;
      mapsHelp = null;
      seekBar = null;
      span = Math.min(520, width - 24);
      left = (width - span) / 2;
      int tabWidth = span / 6;
      for (int i = 0; i < 6; i++) {
         Section target = Section.values()[i];
         DeskButton tab = button(target.label, left + i * tabWidth, 34, tabWidth - 3, () -> navigate(target));
         tab.setSelected(section == target);
      }
      switch (section) {
         case PLAYER -> player();
         case TUNING -> tuning();
         case MUSIC -> music();
         case HUD -> hud();
         case SONG -> song();
         case MAPS -> mappings();
      }
      if (section != Section.PLAYER) buildRows();
      button("Done", left + span - 70, height - 26, 70, this::onClose);
      liveUpdates.forEach(Runnable::run);
   }

   private void navigate(Section next) {
      files.flushSettings(settings);
      section = next;
      page = 0;
      feedback = "";
      rebuildWidgets();
   }

   private DeskButton button(String label, int x, int y, int w, Runnable action) {
      return addRenderableWidget(new DeskButton(Component.literal(label), x, y, w, ignored -> action.run()));
   }

   private void hint(AbstractWidget widget, String text) {
      widget.setTooltip(Tooltip.create(Component.literal(text)));
   }

   private void player() {
      seekBar = addRenderableWidget(new SongSeekBar(left, 90, span, playback));
      int gap = 4;
      int w = (span - gap * 3) / 4;
      Button play = button("Play", left, 114, w, () -> {
         if (PlayerControls.needsNoteBlocks(playback.snapshot(), selectedIsLoaded())
               && playback.refreshNearbyPlayableBlocks(minecraft) == 0) {
            feedback = "Move near a playable note block, or use Preview.";
            return;
         }
         if (playback.snapshot().playing()) playback.pause(minecraft);
         else startSelected(SongLoadIntent.PLAY);
      });
      Button preview = button("Preview", left + w + gap, 114, w, () -> startSelected(SongLoadIntent.PREVIEW));
      Button stop = button("Stop", left + (w + gap) * 2, 114, w, () -> playback.stop(minecraft));
      button("Song settings", left + (w + gap) * 3, 114, w, () -> navigate(Section.SONG));
      Button tune = button("Tune", left, 138, w, () -> {
         if (selectedIsLoaded()) playback.startTuning(minecraft, false);
         else feedback = "Load the selected song before tuning.";
      });
      Button load = button("Reload", left + w + gap, 138, w, () -> {
         if (playback.selectedSongPath() != null) playback.load(minecraft, playback.selectedSongPath(), SongLoadIntent.LOAD_ONLY);
      });
      Button random = button("Shuffle", left + (w + gap) * 2, 138, w, () -> playback.playRandom(minecraft));
      Button center = button("Center", left + (w + gap) * 3, 138, w, () -> commands.centerPlayer(minecraft));
      library();
      hint(play, "Play requires at least one reachable note block with air above it, and survival mode. Preview needs no blocks.");
      hint(preview, "Listen locally without tuning or playing note blocks. Pause first to switch playback modes.");
      hint(load, "Apply music settings and song overrides by loading the selected song again. This stops current playback.");
      hint(tune, "Tune nearby blocks for the loaded song without starting playback.");
      hint(random, "Pick and play a random song from your library.");
      hint(center, "Move to the center of the block you are standing on.");
      liveUpdates.add(() -> {
         var state = playback.snapshot();
         boolean ready = state.stage() == PlaybackStage.IDLE || state.canSeek();
         boolean selected = playback.selectedSongPath() != null;
         boolean ended = state.hasSong() && state.currentTick() > state.song().lastTick();
         play.setMessage(Component.literal(state.playing() ? "Pause" : ended ? "Replay" : state.stage() == PlaybackStage.PAUSED ? "Resume" : "Play"));
         play.active = PlayerControls.canUseTransport(state, inWorld(), selected, selectedIsLoaded(), playback.nearbyPlayableBlocks());
         preview.active = inWorld() && ready && !state.playing() && (state.hasSong() || selected);
         stop.active = state.stage() != PlaybackStage.IDLE;
         tune.active = inWorld() && selectedIsLoaded() && state.canSeek() && !state.playing();
         load.active = inWorld() && selected;
         random.active = inWorld();
         center.active = inWorld();
      });
   }

   private boolean selectedIsLoaded() {
      return playback.snapshot().hasSong() && SongFileManager.samePath(playback.selectedSongPath(), playback.snapshot().loadedSongPath());
   }

   private void startSelected(SongLoadIntent intent) {
      if (!selectedIsLoaded() && playback.selectedSongPath() != null) {
         playback.load(minecraft, playback.selectedSongPath(), intent);
      } else if (intent == SongLoadIntent.PREVIEW) playback.preview(minecraft);
      else playback.play(minecraft);
   }

   private boolean inWorld() { return minecraft.player != null && minecraft.level != null; }

   private int libraryGap() {
      return height >= LIBRARY_HEADING_MIN_HEIGHT ? LIBRARY_HEADING_HEIGHT : 0;
   }

   private int librarySearchTop() { return LIBRARY_SEARCH_TOP + libraryGap(); }
   private int libraryListTop() { return LIBRARY_LIST_TOP + libraryGap(); }

   private void library() {
      if (library == null) readLibrary();
      librarySelection = playback.selectedSongPath();
      songList = addRenderableWidget(new ScrollableChoices<>(left, libraryListTop(), span, Math.max(ScrollListModel.ROW_HEIGHT, height - libraryListTop() - FOOTER_RESERVED_HEIGHT),
            "Song library", path -> SongFileManager.samePath(path, librarySelection), path -> {
               librarySelection = path;
               playback.setSelectedSongPath(path);
               if (inWorld()) playback.load(minecraft, path, SongLoadIntent.LOAD_ONLY);
            }));
      EditBox search = addRenderableWidget(new DeskEditBox(font, left, librarySearchTop(), span - 74, 20, Component.literal("Search songs")));
      search.setMaxLength(256);
      search.setHint(Component.literal("Search your songs…"));
      search.setValue(query);
      search.setResponder(value -> { query = value; libraryScroll = 0; refreshLibrary(); });
      button("Refresh", left + span - 70, librarySearchTop(), 70, () -> { readLibrary(); refreshLibrary(); });
      refreshLibrary();
      hint(search, "Songs in " + commands.songsFolderLabel());
   }

   private void readLibrary() {
      library = files.availableSongs().stream().map(path -> new ScrollableChoices.Option<>(path, files.toStoredSongPath(path))).toList();
   }

   private void refreshLibrary() {
      String filter = query.strip().toLowerCase(Locale.ROOT);
      var found = library.stream().filter(entry -> entry.label().toLowerCase(Locale.ROOT).contains(filter)).toList();
      songList.setOptions(found);
      songList.scrollTo(libraryScroll);
      feedback = found.isEmpty() ? "No songs found. Add .nbs or .txt files to the song folder." : found.size() + " songs in your library";
   }

   private void tuning() {
      number("Tune delay", "Ticks between tuning clicks, from 1 to 20.", settings::tickDelay, settings::setTickDelay, 1, 20);
      number("Tune together", "Blocks tuned at once: 1–20. Use 0 for unlimited.", settings::concurrentTuneBlocks, settings::setConcurrentTuneBlocks, 0, 20);
      number("Recheck delay", "Wait this many ticks before checking tuning again: 1–100.", settings::checkNoteblocksAgainDelay, settings::setCheckNoteblocksAgainDelay, 1, 100);
      cycle("Detect instruments", "Read the note block state, or inspect the block below it.", InstrumentDetectMode.values(), settings::instrumentDetectMode, settings::setInstrumentDetectMode);
      toggle("Turn toward notes", "Automatically face the next note block.", settings::autoRotate, settings::setAutoRotate);
      cycle("Aim at", "Choose the visible face or the closest face of each note block.", RotateMode.values(), settings::rotateMode, settings::setRotateMode);
      toggle("Swing arm", "Show an arm swing when playing notes.", settings::swingArm, settings::setSwingArm);
   }

   private void music() {
      cycle("Instruments", "Exact instruments preserves timbre. Any uses the available blocks. Reload the song after changing.", InstrumentMatchMode.values(), settings::mode, settings::setMode);
      cycle("Tempo rounding", "Fit song timing to Minecraft ticks. Reload the song after changing.", TempoQuantization.values(), settings::tempoQuantization, settings::setTempoQuantization);
      transpose(false);
      cycle("Out-of-range notes", "Drop, clamp, fold by octaves, or remap notes outside the playable range. Reload to apply.", OutOfRangeMode.values(), settings::outOfRangeMode, settings::setOutOfRangeMode);
      toggle("Play chords", "Play simultaneous notes together. Off plays one note at a time.", settings::polyphonic, settings::setPolyphonic);
      toggle("Keep music going", "Automatically play a random song when note-block playback finishes.", settings::autoPlay, settings::setAutoPlay);
   }

   private void hud() {
      toggle("Show HUD", "Show the song and required note blocks in the game overlay.", settings::showHud, settings::setShowHud);
      cycle("Position", "Choose which corner contains the overlay.", HudAnchor.values(), settings::hudAnchor, settings::setHudAnchor);
      toggle("Hide when empty", "Hide the overlay when no song is selected or loaded.", settings::hudAutoHide, settings::setHudAutoHide);
   }

   private String songKey() {
      Path selected = playback.selectedSongPath();
      return selected == null ? null : files.toStoredSongPath(selected);
   }

   private SongConfig config() { return songKey() == null ? null : files.songConfigManager().getConfig(songKey()); }

   private void song() {
      if (songKey() == null) { feedback = "Choose a song in the player first."; return; }
      cycleNullable("Tempo rounding", "Inherit uses your global music setting. Reload the song to apply.",
            Arrays.stream(TempoQuantization.values()).filter(v -> v != TempoQuantization.DEFAULT).toList(),
            () -> config() == null ? null : config().tempoOverride(),
            value -> songChange(() -> files.songConfigManager().setTempoOverride(songKey(), value)));
      transpose(true);
      cycleNullable("Out-of-range notes", "Override the range policy for this song, or inherit the global setting.",
            Arrays.asList(OutOfRangeMode.values()), () -> config() == null ? null : config().outOfRangeModeOverride(),
            value -> songChange(() -> files.songConfigManager().setOutOfRangeModeOverride(songKey(), value)));
      rows.add(y -> button("Edit this song's instrument mappings", left, y, span, () -> { songMappings = true; navigate(Section.MAPS); }));
      rows.add(y -> button("Reset this song to global settings", left, y, span, () -> {
         songChange(() -> files.songConfigManager().clearConfig(songKey()));
         rebuildWidgets();
      }));
   }

   private void songChange(Runnable action) {
      action.run();
      feedback = files.songConfigManager().lastSaveSucceeded() ? "Saved. Reload the song to hear these changes."
            : "Could not save overrides. Check the log and try again.";
   }

   private void mappings() {
      mapsHelp = addRenderableWidget(new AbstractWidget(left + span - 16, 58, 16, 14, Component.literal("?")) {
         @Override
         protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            graphics.text(font, "?", getX() + 5, getY() + 3, isHovered() || isFocused() ? DeskTheme.ACCENT : DeskTheme.TEXT_SECONDARY, false);
         }

         @Override
         protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
            output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
                  Component.literal("Instrument ranges. " + instrumentRangeGuide()));
         }

         @Override
         public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            return false;
         }
      });
      rows.add(y -> button("Editing: " + (songMappings ? "selected song" : "global mappings"), left, y, span, () -> {
         songMappings = !songMappings;
         page = 0;
         rebuildWidgets();
      }));
      if (songMappings && songKey() == null) { feedback = "Choose a song first, or switch to global mappings."; return; }
      for (NoteBlockInstrument source : NoteBlockInstrument.values()) {
         instrumentMapping(source,
               () -> songMappings ? config() != null && config().mappedSources().contains(source) ? config().mapInstrument(source) : null
                     : settings.mappedSources().contains(source) ? settings.mapInstrument(source) : null,
               target -> {
                  if (songMappings) songChange(() -> {
                     if (target == null) files.songConfigManager().removeInstrumentOverride(songKey(), source);
                     else files.songConfigManager().setInstrumentOverride(songKey(), source, target);
                  });
                  else settings.setInstrumentOverride(source, target);
               });
      }
      rows.add(y -> button("Clear " + (songMappings ? "song" : "global") + " mappings", left, y, span, () -> {
         if (songMappings) songChange(() -> files.songConfigManager().clearInstrumentOverrides(songKey()));
         else settings.clearInstrumentOverrides();
         rebuildWidgets();
      }));
   }

   private static String instrumentRangeGuide() {
      // Reference: https://minecraft.wiki/w/Tutorial:Redstone_music#Pitch
      // Trumpet registers also checked against the bundled Java sound samples.
      String guide = "Instrument ranges\n"
            + "25 notes per melodic instrument / 2 octaves\n"
            + "\nF#1 - F#3: Bass, Didgeridoo\n"
            + "F#2 - F#4: Guitar\n"
            + "F#3 - F#5: Harp, Iron xylophone,\n"
            + "                  Bit, Banjo, Pling\n"
            + "F#4 - F#6: Flute, Cow bell\n"
            + "F#5 - F#7: Bell, Chime, Xylophone\n";
      //? if >=26.1 {
      guide += "\nTrumpet / Exposed: F#3 - F#5\n"
            + "Weathered / Oxidized: F#2 - F#4\n";
      //? }
      return guide + "\nBass drum, Snare, Hat: percussion\n"
            + "Mob heads: fixed sounds; Custom head: varies\n"
            + "\nF# = F sharp; C4 = middle C\n"
            + "Ranges assume the default sound pack.";
   }

   private ItemStack instrumentIcon(NoteBlockInstrument instrument) {
      // Item components are bound when joining a world in 26.1+.
      if (minecraft.level == null) return ItemStack.EMPTY;
      return instrumentIcons.computeIfAbsent(instrument, value -> new ItemStack(InstrumentIcons.blockFor(value)));
   }

   private void instrumentMapping(NoteBlockInstrument source, Supplier<NoteBlockInstrument> read, Consumer<NoteBlockInstrument> write) {
      List<NoteBlockInstrument> choices = new ArrayList<>();
      choices.add(null);
      choices.addAll(Arrays.asList(NoteBlockInstrument.values()));
      rows.add(y -> {
         Button control = addRenderableWidget(new DeskButton(Component.literal(pretty(source)),
               left + 24, y, span - 48, anchor -> openDropdown(anchor, pretty(source), choices, read, write)));
         liveUpdates.add(() -> control.setMessage(Component.literal(pretty(source) + ": " + pretty(read.get()))));
         hint(control, "Choose a replacement instrument. Inherit removes this override. Reload the song to apply.");
         addRenderableOnly((graphics, mouseX, mouseY, delta) -> {
            graphics.item(instrumentIcon(source), left + 4, y + 2);
            NoteBlockInstrument target = read.get();
            if (target == null) target = songMappings ? settings.mapInstrument(source) : source;
            graphics.item(instrumentIcon(target), left + span - 20, y + 2);
         });
      });
   }

   private void toggle(String name, String description, BooleanSupplier read, Consumer<Boolean> write) {
      rows.add(y -> {
         // Keep keyboard focus on the same control when its value changes.
         Button control = button(name + ": " + (read.getAsBoolean() ? "On" : "Off"), left, y, span,
               () -> { write.accept(!read.getAsBoolean()); files.saveSettingsIfChanged(settings); });
         Button valueButton = control;
         liveUpdates.add(() -> valueButton.setMessage(Component.literal(name + ": " + (read.getAsBoolean() ? "On" : "Off"))));
         hint(control, description);
      });
   }

   private <T> void cycle(String name, String description, T[] choices, Supplier<T> read, Consumer<T> write) {
      choice(name, description, Arrays.asList(choices), read, write);
   }

   private <T> void cycleNullable(String name, String description, List<T> choices, Supplier<T> read, Consumer<T> write) {
      List<T> inherited = new ArrayList<>();
      inherited.add(null);
      inherited.addAll(choices);
      choice(name, description, inherited, read, write);
   }

   private <T> void choice(String name, String description, List<T> choices, Supplier<T> read, Consumer<T> write) {
      rows.add(y -> {
         Button control = addRenderableWidget(new DeskButton(Component.literal(name),
               left, y, span, anchor -> openDropdown(anchor, name, choices, read, write)));
         liveUpdates.add(() -> control.setMessage(Component.literal(name + ": " + pretty(read.get()))));
         hint(control, description + " Open the list to select a value.");
      });
   }

   private <T> void openDropdown(Button anchor, String name, List<T> choices, Supplier<T> read, Consumer<T> write) {
      closeDropdown();
      int menuHeight = Math.min(choices.size() * 22 + 2, height - 92);
      int y = Math.clamp(anchor.getBottom() + 2, 58, height - 34 - menuHeight);
      var menu = new ScrollableChoices<T>(left, y, span, menuHeight, name,
            value -> Objects.equals(read.get(), value), value -> {
               write.accept(value);
               files.saveSettingsIfChanged(settings);
               closeDropdown();
               liveUpdates.forEach(Runnable::run);
            });
      menu.setOptions(choices.stream().map(value -> new ScrollableChoices.Option<>(value, pretty(value),
            value instanceof NoteBlockInstrument instrument ? instrumentIcon(instrument) : ItemStack.EMPTY)).toList());
      menu.revealSelection();
      dropdownAnchor = anchor;
      dropdown = addWidget(menu);
      setFocused(menu);
   }

   private void closeDropdown() {
      if (dropdown == null) return;
      removeWidget(dropdown);
      dropdown = null;
      setFocused(dropdownAnchor);
      dropdownAnchor = null;
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      if (dropdown == null) {
         boolean handled = super.mouseClicked(event, doubleClick);
         if (dropdown != null) setFocused(dropdown);
         return handled;
      }
      if (dropdown.isMouseOver(event.x(), event.y())) dropdown.mouseClicked(event, doubleClick);
      else closeDropdown();
      return true;
   }

   @Override
   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      return dropdown == null ? super.mouseDragged(event, dx, dy) : dropdown.mouseDragged(event, dx, dy);
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent event) {
      if (dropdown == null) return super.mouseReleased(event);
      setDragging(false);
      return dropdown.mouseReleased(event);
   }

   @Override
   public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
      if (dropdown == null) return super.mouseScrolled(x, y, horizontal, vertical);
      dropdown.mouseScrolled(x, y, horizontal, vertical);
      return true;
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      if (dropdown == null) return super.keyPressed(event);
      if (event.key() == GLFW.GLFW_KEY_ESCAPE || event.key() == GLFW.GLFW_KEY_TAB) closeDropdown();
      else dropdown.keyPressed(event);
      return true;
   }

   private void number(String name, String description, Supplier<Integer> read, Consumer<Integer> write, int min, int max) {
      input(name, description, () -> read.get().toString(), value -> {
         try {
            int number = Integer.parseInt(value.trim());
            if (number < min || number > max) return "Enter a number from " + min + " to " + max + ".";
            write.accept(number);
            return null;
         } catch (NumberFormatException e) { return "Enter a whole number from " + min + " to " + max + "."; }
      });
   }

   private void transpose(boolean perSong) {
      String description = "Off leaves pitches unchanged. Auto chooses an octave shift. Smart finds the best semitone shift. Signed presets shift by 6, 12, 18 or 24 semitones. Reload to apply.";
      if (perSong) {
         cycleNullable("Transpose", description + " Inherit follows the global setting.", Arrays.asList(TransposeSetting.values()),
               () -> config() == null ? null : config().transposeOverride(),
               value -> songChange(() -> files.songConfigManager().setTransposeOverride(songKey(), value)));
      } else {
         cycle("Transpose", description, TransposeSetting.values(), settings::transpose, settings::setTranspose);
      }
   }

   private void input(String name, String description, Supplier<String> read, Function<String, String> apply) {
      rows.add(y -> {
         int labelWidth = span / 3;
         labels.add(new Label(name, left + 4, y + 6, labelWidth - 8));
         EditBox field = addRenderableWidget(new DeskEditBox(font, left + labelWidth, y, span - labelWidth - 58, 20, Component.literal(name)));
         field.setMaxLength(32);
         field.setValue(read.get());
         hint(field, description);
         Button save = button("Apply", left + span - 54, y, 54, () -> {
            String error = apply.apply(field.getValue());
            field.setTextColor(error == null ? DeskTheme.TEXT_INPUT : DeskTheme.TEXT_ERROR);
            if (error != null) feedback = error;
            else {
               files.saveSettingsIfChanged(settings);
               if (section != Section.SONG) feedback = "Applied " + name.toLowerCase(Locale.ROOT) + ".";
               field.setValue(read.get());
            }
         });
         hint(save, description);
      });
   }

   private void buildRows() {
      int count = Math.max(1, (height - 128) / 28);
      pageCount = Math.max(1, (rows.size() + count - 1) / count);
      page = Math.clamp(page, 0, pageCount - 1);
      for (int i = page * count; i < Math.min(rows.size(), (page + 1) * count); i++) rows.get(i).accept(76 + (i % count) * 28);
      if (pageCount > 1) {
         button("Previous", left, height - 54, 74, () -> { page--; rebuildWidgets(); }).active = page > 0;
         button("Next", left + span - 74, height - 54, 74, () -> { page++; rebuildWidgets(); }).active = page + 1 < pageCount;
      }
   }

   private static String pretty(Object value) {
      if (value == null) return "Inherit";
      String text = value.toString().toLowerCase(Locale.ROOT).replace('_', ' ');
      return Character.toUpperCase(text.charAt(0)) + text.substring(1);
   }

   @Override
   public void tick() {
      liveUpdates.forEach(Runnable::run);
      if (seekBar != null) seekBar.sync();
      if (songList != null) librarySelection = playback.selectedSongPath();
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      graphics.fill(0, 0, width, height, DeskTheme.SCREEN_SCRIM);
      graphics.fill(left - 12, 10, left + span + 12, height - 8, DeskTheme.PANEL_BACKGROUND);
      graphics.fill(left, 30, left + span, 31, DeskTheme.BORDER_SUBTLE);
      graphics.fill(left, 18, left + 3, 27, DeskTheme.ACCENT);
      graphics.text(font, "LYRA", left + 10, 18, DeskTheme.TEXT_PRIMARY, false);
      if (section == Section.PLAYER) {
         if (libraryGap() > 0) graphics.text(font, "LIBRARY", left, 165, DeskTheme.TEXT_SECONDARY, false);
         var state = playback.snapshot();
         String title = SongDisplay.title(state, playback.selectedSongPath());
         graphics.text(font, font.plainSubstrByWidth(title, span), left, 60, DeskTheme.TEXT_PRIMARY, false);
         String subtitle = !inWorld() ? "Join a world to listen, tune, and play."
               : state.hasSong() ? (state.currentTick() > state.song().lastTick() ? "Finished" : pretty(state.stage()))
                     + " · " + pretty(state.mode()) + " · " + (state.loadedSongPath() == null ? "" : state.loadedSongPath().getFileName())
               : "";
         graphics.text(font, font.plainSubstrByWidth(subtitle, span), left, 76, DeskTheme.TEXT_SECONDARY, false);
         if (dropdown == null && mouseY >= 58 && mouseY < 88 && mouseX >= left && mouseX <= left + span) {
            graphics.setTooltipForNextFrame(font, Component.literal(title + "\n" + subtitle
                  + (state.hasSong() && state.song().author() != null && !state.song().author().isBlank() ? "\n" + state.song().author() : "")), mouseX, mouseY);
         }
      } else {
         String subtitle = section == Section.SONG ? "For: " + (songKey() == null ? "no song selected" : songKey())
               : section == Section.MAPS ? (songMappings ? "Song: " + songKey() : "Global instrument replacements")
               : section == Section.MUSIC ? "Music changes apply when you reload a song." : "";
         graphics.text(font, font.plainSubstrByWidth(subtitle, section == Section.MAPS ? span - 24 : span), left, 62, DeskTheme.TEXT_SECONDARY, false);
      }
      for (Label label : labels) graphics.text(font, font.plainSubstrByWidth(label.value(), label.maxWidth()), label.x(), label.y(), DeskTheme.TEXT_PRIMARY, false);
      if (section != Section.PLAYER && pageCount > 1) {
         String pages = (page + 1) + " / " + pageCount;
         graphics.text(font, pages, width / 2 - font.width(pages) / 2, height - 48, DeskTheme.TEXT_SECONDARY, false);
      }
      String message = feedback;
      var lines = font.split(Component.literal(message), span - 80);
      for (int i = 0; i < Math.min(2, lines.size()); i++) {
         graphics.text(font, lines.get(i), left, height - 26 + i * 10, DeskTheme.TEXT_SECONDARY, false);
      }
      super.extractRenderState(graphics, dropdown == null ? mouseX : -1, dropdown == null ? mouseY : -1, delta);
      if (dropdown == null && mapsHelp != null && (mapsHelp.isHovered()
            || mapsHelp.isFocused() && minecraft.getLastInputType().isKeyboard())) {
         graphics.setTooltipForNextFrame(font,
               font.split(Component.literal(instrumentRangeGuide()), Math.min(270, width - 24)),
               mapsHelp.isHovered() ? mouseX : mapsHelp.getX(),
               mapsHelp.isHovered() ? mouseY : mapsHelp.getBottom());
      }
      if (dropdown != null) {
         graphics.nextStratum();
         dropdown.extractRenderState(graphics, mouseX, mouseY, delta);
      }
   }

   @Override
   public boolean isPauseScreen() { return false; }

   @Override
   public void removed() { files.flushSettings(settings); }

   @Override
   public void onClose() {
      files.flushSettings(settings);
      minecraft.setScreen(parent);
   }
}
