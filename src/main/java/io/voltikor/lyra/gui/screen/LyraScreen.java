package io.voltikor.lyra.gui.screen;

import io.voltikor.lyra.command.LyraCommandHandlers;
import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.config.SongConfig;
import io.voltikor.lyra.config.TransposeSetting;
import io.voltikor.lyra.gui.DeskTheme;
import io.voltikor.lyra.hud.RequiredBlocksHudModel.RequiredHudRow;
import io.voltikor.lyra.noteblock.InstrumentIcons;
import io.voltikor.lyra.playback.PlaybackCoordinator;
import io.voltikor.lyra.playback.SongLoadIntent;
import io.voltikor.lyra.song.SongFileManager;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import org.lwjgl.glfw.GLFW;

public final class LyraScreen extends Screen {
   private final Screen parent;
   private final LyraSettings settings;
   private final SongFileManager files;
   private final PlaybackCoordinator playback;
   private final LyraCommandHandlers commands;
   private final Supplier<List<RequiredHudRow>> instrumentRows;
   private final MenuState menuState;
   private final List<Consumer<Integer>> rows = new ArrayList<>();
   private final List<Runnable> liveUpdates = new ArrayList<>();
   private final List<Label> labels = new ArrayList<>();
   private final EnumMap<NoteBlockInstrument, ItemStack> instrumentIcons = new EnumMap<>(NoteBlockInstrument.class);
   private final java.util.Map<Block, ItemStack> blockIcons = new java.util.HashMap<>();
   private final EnumMap<Section, LyraScreenSection> sections = new EnumMap<>(Section.class);
   private final MusicSection musicSection = new MusicSection();
   private final MappingsSection mappingsSection = new MappingsSection();
   private ScrollableChoices<?> dropdown;
   private Button dropdownAnchor;
   private Section section = Section.PLAYER;
   private int page;
   private int pageCount = 1;
   private int left;
   private int span;
   private String feedback = "";

   enum Section {
      PLAYER("Player"), TUNING("Tuning"), MUSIC("Music"), HUD("List"), MAPS("Maps");

      final String label;

      Section(String label) { this.label = label; }
   }

   private record Label(String value, int x, int y, int maxWidth) {}

   public LyraScreen(Screen parent, LyraSettings settings, SongFileManager files,
         PlaybackCoordinator playback, LyraCommandHandlers commands) {
      this(parent, settings, files, playback, commands, List::of, new MenuState());
   }

   public LyraScreen(Screen parent, LyraSettings settings, SongFileManager files,
         PlaybackCoordinator playback, LyraCommandHandlers commands,
         Supplier<List<RequiredHudRow>> instrumentRows) {
      this(parent, settings, files, playback, commands, instrumentRows, new MenuState());
   }

   public LyraScreen(Screen parent, LyraSettings settings, SongFileManager files,
         PlaybackCoordinator playback, LyraCommandHandlers commands,
         Supplier<List<RequiredHudRow>> instrumentRows, MenuState menuState) {
      super(Component.literal("Lyra"));
      this.parent = parent;
      this.settings = settings;
      this.files = files;
      this.playback = playback;
      this.commands = commands;
      this.instrumentRows = instrumentRows;
      this.menuState = menuState;
      sections.put(Section.PLAYER, new PlayerSection());
      sections.put(Section.TUNING, new TuningSection());
      sections.put(Section.MUSIC, musicSection);
      sections.put(Section.HUD, new HudSection());
      sections.put(Section.MAPS, mappingsSection);
   }

   @Override
   protected void init() {
      clearWidgets();
      rows.clear();
      labels.clear();
      liveUpdates.clear();
      dropdown = null;
      dropdownAnchor = null;
      span = Math.min(520, width - 24);
      left = (width - span) / 2;
      int tabWidth = span / Section.values().length;
      for (int i = 0; i < Section.values().length; i++) {
         Section target = Section.values()[i];
         DeskButton tab = button(target.label, left + i * tabWidth, 34, tabWidth - 3, () -> navigate(target));
         tab.setSelected(section == target);
      }
      activeSection().build(this);
      if (section != Section.PLAYER) buildRows();
      button("Done", left + span - 70, height - 26, 70, this::onClose);
      liveUpdates.forEach(Runnable::run);
   }

   private LyraScreenSection activeSection() { return sections.get(section); }

   void navigate(Section next) {
      files.flushSettings(settings);
      section = next;
      firstPage();
      feedback = "";
      rebuildWidgets();
   }

   void editSongMappings() {
      mappingsSection.editSelectedSong();
      navigate(Section.MAPS);
   }

   void editSelectedSongMusic() {
      musicSection.editSelectedSong(this);
      navigate(Section.MUSIC);
   }

   void firstPage() { page = 0; }
   void rebuild() { rebuildWidgets(); }

   <T extends AbstractWidget> T widget(T widget) { return addRenderableWidget(widget); }

   void renderOnly(Renderable renderable) { addRenderableOnly(renderable); }

   DeskButton button(String label, int x, int y, int width, Runnable action) {
      return addRenderableWidget(new DeskButton(Component.literal(label), x, y, width, ignored -> action.run()));
   }

   void hint(AbstractWidget widget, String text) {
      widget.setTooltip(Tooltip.create(Component.literal(text)));
   }

   void row(Consumer<Integer> row) { rows.add(row); }
   void liveUpdate(Runnable update) { liveUpdates.add(update); }
   void feedback(String message) { feedback = message; }

   void scopeToggle(boolean selectedSong, String globalLabel, Consumer<Boolean> write) {
      row(y -> button("Editing: " + (selectedSong ? "selected song" : globalLabel), left, y, span, () -> {
         write.accept(!selectedSong);
         firstPage();
         rebuild();
      }));
   }

   LyraSettings settings() { return settings; }
   SongFileManager files() { return files; }
   PlaybackCoordinator playback() { return playback; }
   LyraCommandHandlers commands() { return commands; }
   List<RequiredHudRow> instrumentRows() { return instrumentRows.get(); }
   MenuState menuState() { return menuState; }
   Minecraft minecraftClient() { return minecraft; }
   Font font() { return font; }
   int left() { return left; }
   int span() { return span; }
   int width() { return width; }
   int height() { return height; }
   boolean hasDropdown() { return dropdown != null; }

   boolean selectedIsLoaded() {
      return playback.snapshot().hasSong()
            && SongFileManager.samePath(playback.selectedSongPath(), playback.snapshot().loadedSongPath());
   }

   void startSelected(SongLoadIntent intent) {
      if (!selectedIsLoaded() && playback.selectedSongPath() != null) {
         playback.load(minecraft, playback.selectedSongPath(), intent);
      } else if (intent == SongLoadIntent.PREVIEW) playback.preview(minecraft);
      else playback.play(minecraft);
   }

   boolean inWorld() { return minecraft.player != null && minecraft.level != null; }

   String songKey() {
      Path selected = playback.selectedSongPath();
      return selected == null ? null : files.toStoredSongPath(selected);
   }

   SongConfig songConfig() {
      String key = songKey();
      return key == null ? null : files.songConfigManager().getConfig(key);
   }

   void songChange(Runnable action) {
      action.run();
      feedback = files.songConfigManager().lastSaveSucceeded()
            ? "Saved. Reload the song to hear these changes."
            : "Could not save overrides. Check the log and try again.";
   }

   ItemStack instrumentIcon(NoteBlockInstrument instrument) {
      // Item components are bound when joining a world in 26.1+.
      if (minecraft.level == null) return ItemStack.EMPTY;
      return instrumentIcons.computeIfAbsent(instrument, value -> new ItemStack(InstrumentIcons.blockFor(value)));
   }

   ItemStack blockIcon(Block block) {
      if (minecraft.level == null || block == null) return ItemStack.EMPTY;
      return blockIcons.computeIfAbsent(block, ItemStack::new);
   }

   void toggle(String name, String description, BooleanSupplier read, Consumer<Boolean> write) {
      rows.add(y -> {
         Button control = button(name + ": " + (read.getAsBoolean() ? "On" : "Off"), left, y, span,
               () -> {
                  write.accept(!read.getAsBoolean());
                  files.saveSettingsIfChanged(settings);
               });
         liveUpdates.add(() -> control.setMessage(
               Component.literal(name + ": " + (read.getAsBoolean() ? "On" : "Off"))));
         hint(control, description);
      });
   }

   <T> void cycle(String name, String description, T[] choices, Supplier<T> read, Consumer<T> write) {
      choice(name, description, Arrays.asList(choices), read, write);
   }

   <T> void cycleNullable(String name, String description, List<T> choices, Supplier<T> read, Consumer<T> write) {
      List<T> inherited = new ArrayList<>();
      inherited.add(null);
      inherited.addAll(choices);
      choice(name, description, inherited, read, write);
   }

   private <T> void choice(String name, String description, List<T> choices, Supplier<T> read, Consumer<T> write) {
      rows.add(y -> {
         Button control = widget(new DeskButton(Component.literal(name), left, y, span,
               anchor -> openDropdown(anchor, name, choices, read, write)));
         liveUpdates.add(() -> control.setMessage(Component.literal(name + ": " + pretty(read.get()))));
         hint(control, description + " Open the list to select a value.");
      });
   }

   <T> void openDropdown(Button anchor, String name, List<T> choices, Supplier<T> read, Consumer<T> write) {
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
      Button anchor = dropdownAnchor;
      if (anchor instanceof DeskButton deskButton) deskButton.clearPressed();
      removeWidget(dropdown);
      dropdown = null;
      setFocused(anchor);
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
      Button anchor = dropdownAnchor;
      setDragging(false);
      boolean handled = dropdown.mouseReleased(event);
      if (anchor instanceof DeskButton deskButton) deskButton.clearPressed();
      return handled;
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

   void number(String name, String description, Supplier<Integer> read, Consumer<Integer> write, int min, int max) {
      input(name, description, () -> read.get().toString(), value -> {
         try {
            int number = Integer.parseInt(value.trim());
            if (number < min || number > max) return "Enter a number from " + min + " to " + max + ".";
            write.accept(number);
            return null;
         } catch (NumberFormatException e) {
            return "Enter a whole number from " + min + " to " + max + ".";
         }
      });
   }

   void transpose(boolean perSong) {
      String description = "Off leaves pitches unchanged. Auto chooses an octave shift. Smart finds the best semitone shift. "
            + "Signed presets shift by 6, 12, 18 or 24 semitones. Reload to apply.";
      if (perSong) {
         cycleNullable("Transpose", description + " Inherit follows the global setting.",
               Arrays.asList(TransposeSetting.values()),
               () -> songConfig() == null ? null : songConfig().transposeOverride(),
               value -> songChange(() -> files.songConfigManager().setTransposeOverride(songKey(), value)));
      } else {
         cycle("Transpose", description, TransposeSetting.values(), settings::transpose, settings::setTranspose);
      }
   }

   private void input(String name, String description, Supplier<String> read, Function<String, String> apply) {
      rows.add(y -> {
         int labelWidth = span / 3;
         labels.add(new Label(name, left + 4, y + 6, labelWidth - 8));
         EditBox field = widget(new DeskEditBox(font, left + labelWidth, y,
               span - labelWidth - 58, 20, Component.literal(name)));
         field.setMaxLength(32);
         field.setValue(read.get());
         hint(field, description);
         Button save = button("Apply", left + span - 54, y, 54, () -> {
            String error = apply.apply(field.getValue());
            field.setTextColor(error == null ? DeskTheme.TEXT_INPUT : DeskTheme.TEXT_ERROR);
            if (error != null) feedback = error;
            else {
               files.saveSettingsIfChanged(settings);
               feedback = "Applied " + name.toLowerCase(Locale.ROOT) + ".";
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
      for (int i = page * count; i < Math.min(rows.size(), (page + 1) * count); i++) {
         rows.get(i).accept(76 + (i % count) * 28);
      }
      if (pageCount > 1) {
         button("Previous", left, height - 54, 74, () -> {
            page--;
            rebuildWidgets();
         }).active = page > 0;
         button("Next", left + span - 74, height - 54, 74, () -> {
            page++;
            rebuildWidgets();
         }).active = page + 1 < pageCount;
      }
   }

   static String pretty(Object value) {
      if (value == null) return "Inherit";
      String text = value.toString().toLowerCase(Locale.ROOT).replace('_', ' ');
      return Character.toUpperCase(text.charAt(0)) + text.substring(1);
   }

   @Override
   public void tick() {
      liveUpdates.forEach(Runnable::run);
      activeSection().tick(this);
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      graphics.fill(0, 0, width, height, DeskTheme.SCREEN_SCRIM);
      graphics.fill(left - 12, 10, left + span + 12, height - 8, DeskTheme.PANEL_BACKGROUND);
      graphics.fill(left, 30, left + span, 31, DeskTheme.BORDER_SUBTLE);
      graphics.fill(left, 18, left + 3, 27, DeskTheme.ACCENT);
      graphics.text(font, "LYRA", left + 10, 18, DeskTheme.TEXT_PRIMARY, false);
      activeSection().renderHeader(this, graphics, mouseX, mouseY);
      for (Label label : labels) {
         graphics.text(font, font.plainSubstrByWidth(label.value(), label.maxWidth()),
               label.x(), label.y(), DeskTheme.TEXT_PRIMARY, false);
      }
      if (section != Section.PLAYER && pageCount > 1) {
         String pages = (page + 1) + " / " + pageCount;
         graphics.text(font, pages, width / 2 - font.width(pages) / 2,
               height - 48, DeskTheme.TEXT_SECONDARY, false);
      }
      var lines = font.split(Component.literal(feedback), span - 80);
      for (int i = 0; i < Math.min(2, lines.size()); i++) {
         graphics.text(font, lines.get(i), left, height - 26 + i * 10, DeskTheme.TEXT_SECONDARY, false);
      }
      super.extractRenderState(graphics, dropdown == null ? mouseX : -1,
            dropdown == null ? mouseY : -1, delta);
      activeSection().renderOverlay(this, graphics, mouseX, mouseY);
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

   public static final class MenuState {
      private boolean editSelectedSongMusic;

      boolean editSelectedSongMusic() { return editSelectedSongMusic; }
      void setEditSelectedSongMusic(boolean value) { editSelectedSongMusic = value; }
   }
}
