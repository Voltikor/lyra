package io.voltikor.lyra.gui.screen;

import io.voltikor.lyra.gui.DeskTheme;
import io.voltikor.lyra.hud.HudAnchor;
import io.voltikor.lyra.hud.RequiredBlocksHudModel.RequiredHudRow;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class HudSection implements LyraScreenSection {
   private List<RequiredHudRow> instrumentRows = List.of();

   @Override
   public void build(LyraScreen screen) {
      var settings = screen.settings();
      screen.toggle("Show HUD", "Show the song and required note blocks in the game overlay.",
            settings::showHud, settings::setShowHud);
      screen.cycle("Position", "Choose which corner contains the overlay.",
            HudAnchor.values(), settings::hudAnchor, settings::setHudAnchor);
      screen.toggle("Hide when empty", "Hide the overlay when no song is selected or loaded.",
            settings::hudAutoHide, settings::setHudAutoHide);

      instrumentRows = screen.instrumentRows();
      for (RequiredHudRow row : instrumentRows) {
         screen.row(y -> screen.renderOnly((graphics, mouseX, mouseY, delta) ->
               renderInstrumentRow(screen, graphics, row, y)));
      }
   }

   @Override
   public void tick(LyraScreen screen) {
      if (!instrumentRows.equals(screen.instrumentRows())) screen.rebuild();
   }

   @Override
   public String subtitle(LyraScreen screen) {
      return "HUD options and required instruments";
   }

   private static void renderInstrumentRow(LyraScreen screen, GuiGraphicsExtractor graphics,
         RequiredHudRow row, int y) {
      graphics.item(screen.blockIcon(row.icon()), screen.left() + 4, y + 2);
      if (row.replacedIcon() != null) {
         var pose = graphics.pose();
         pose.pushMatrix();
         pose.translate(screen.left() + 2, y + 10);
         pose.scale(0.6F, 0.6F);
         graphics.item(screen.blockIcon(row.replacedIcon()), 0, 0);
         pose.popMatrix();
      }

      String counts = countLabel(row);
      int countsWidth = screen.font().width(counts);
      int nameWidth = Math.max(20, screen.span() - countsWidth - 36);
      String name = screen.font().plainSubstrByWidth(row.instrumentName().getString(), nameWidth);
      graphics.text(screen.font(), name, screen.left() + 28, y + 6, DeskTheme.TEXT_PRIMARY, false);
      graphics.text(screen.font(), counts, screen.left() + screen.span() - countsWidth - 4, y + 6,
            row.color(), false);
   }

   private static String countLabel(RequiredHudRow row) {
      if (row.required() == 0) return row.available() + " nearby";
      if (row.missing() > 0) return row.required() + " required · " + row.missing() + " missing";
      return row.required() + " required · " + row.available() + " nearby";
   }
}
