package io.voltikor.lyra.gui.screen;

import io.voltikor.lyra.gui.DeskTheme;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

final class MappingsSection implements LyraScreenSection {
   private boolean songMappings;
   private AbstractWidget help;

   void editSelectedSong() {
      songMappings = true;
   }

   @Override
   public void build(LyraScreen screen) {
      help = screen.widget(new AbstractWidget(screen.left() + screen.span() - 16, 58, 16, 14, Component.literal("?")) {
         @Override
         protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            graphics.text(screen.font(), "?", getX() + 5, getY() + 3,
                  isHovered() || isFocused() ? DeskTheme.ACCENT : DeskTheme.TEXT_SECONDARY, false);
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
      screen.scopeToggle(songMappings, "global mappings", value -> songMappings = value);
      if (songMappings && screen.songKey() == null) {
         screen.feedback("Choose a song first, or switch to global mappings.");
         return;
      }
      for (NoteBlockInstrument source : NoteBlockInstrument.values()) {
         instrumentMapping(screen, source,
               () -> songMappings
                     ? screen.songConfig() != null && screen.songConfig().mappedSources().contains(source)
                           ? screen.songConfig().mapInstrument(source)
                           : null
                     : screen.settings().mappedSources().contains(source) ? screen.settings().mapInstrument(source)
                           : null,
               target -> {
                  if (songMappings)
                     screen.songChange(() -> {
                        if (target == null)
                           screen.files().songConfigManager().removeInstrumentOverride(screen.songKey(), source);
                        else
                           screen.files().songConfigManager().setInstrumentOverride(screen.songKey(), source, target);
                     });
                  else
                     screen.settings().setInstrumentOverride(source, target);
               });
      }
      screen.row(y -> screen.button("Clear " + (songMappings ? "song" : "global") + " mappings",
            screen.left(), y, screen.span(), () -> {
               if (songMappings)
                  screen.songChange(
                        () -> screen.files().songConfigManager().clearInstrumentOverrides(screen.songKey()));
               else
                  screen.settings().clearInstrumentOverrides();
               screen.rebuild();
            }));
   }

   private void instrumentMapping(LyraScreen screen, NoteBlockInstrument source,
         Supplier<NoteBlockInstrument> read, Consumer<NoteBlockInstrument> write) {
      List<NoteBlockInstrument> choices = new ArrayList<>();
      choices.add(null);
      choices.addAll(Arrays.asList(NoteBlockInstrument.values()));
      screen.row(y -> {
         Button control = screen.widget(new DeskButton(Component.literal(LyraScreen.pretty(source)),
               screen.left() + 24, y, screen.span() - 48,
               anchor -> screen.openDropdown(anchor, LyraScreen.pretty(source), choices, read, write)));
         screen.liveUpdate(() -> control.setMessage(Component.literal(
               LyraScreen.pretty(source) + ": " + LyraScreen.pretty(read.get()))));
         screen.hint(control,
               "Choose a replacement instrument. Inherit removes this override. Reload the song to apply.");
         screen.renderOnly((graphics, mouseX, mouseY, delta) -> {
            graphics.item(screen.instrumentIcon(source), screen.left() + 4, y + 2);
            NoteBlockInstrument target = read.get();
            if (target == null)
               target = songMappings ? screen.settings().mapInstrument(source) : source;
            graphics.item(screen.instrumentIcon(target), screen.left() + screen.span() - 20, y + 2);
         });
      });
   }

   @Override
   public String subtitle(LyraScreen screen) {
      return songMappings ? "Song: " + screen.songKey() : "Global instrument replacements";
   }

   @Override
   public void renderHeader(LyraScreen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
      String subtitle = subtitle(screen);
      graphics.text(screen.font(), screen.font().plainSubstrByWidth(subtitle, screen.span() - 24),
            screen.left(), 62, DeskTheme.TEXT_SECONDARY, false);
   }

   @Override
   public void renderOverlay(LyraScreen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
      if (!screen.hasDropdown() && help != null
            && (help.isHovered() || help.isFocused() && screen.minecraftClient().getLastInputType().isKeyboard())) {
         graphics.setTooltipForNextFrame(screen.font(),
               screen.font().split(Component.literal(instrumentRangeGuide()), Math.min(270, screen.width() - 24)),
               help.isHovered() ? mouseX : help.getX(), help.isHovered() ? mouseY : help.getBottom());
      }
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
      // ? if >=26.1 {
      guide += "\nTrumpet / Exposed: F#3 - F#5\n"
            + "Weathered / Oxidized: F#2 - F#4\n";
      // ? }
      return guide + "\nBass drum, Snare, Hat: percussion\n"
            + "Mob heads: fixed sounds; Custom head: varies\n"
            + "\nF# = F sharp; C4 = middle C\n"
            + "Ranges assume the default sound pack.";
   }
}
