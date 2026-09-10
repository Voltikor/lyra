package io.voltikor.lyra.hud;

import io.voltikor.lyra.command.LyraMessenger;
import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.song.Song;
import io.voltikor.lyra.gui.DeskTheme;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

@Environment(EnvType.CLIENT)
public final class LyraHudRenderer {
   private static final float HEADER_GAP_LINE_RATIO = 0.3F;
   private static final int MAX_PANEL_WIDTH = 280;
   private static final int ICON_COLUMN_WIDTH = 20;
   private static final int COLUMN_GAP = 8;
   private static final int PROGRESS_HEIGHT = 2;
   private static final int TITLE_ACCENT_WIDTH = 2;
   private static final int TITLE_INSET = 7;
   private final SelectedSongCache selectedSongCache;
   private final RequiredBlocksHudModel hudModel = new RequiredBlocksHudModel();

   public LyraHudRenderer(BiFunction<Path, LyraSettings, CompletableFuture<Song>> loader,
         java.util.function.ToLongFunction<Path> songRevision) {
      this.selectedSongCache = new SelectedSongCache(loader, songRevision, System::nanoTime);
   }

   public void renderHud(GuiGraphicsExtractor graphics, Minecraft client, LyraSettings settings, Song song,
         Path loadedSongPath, Path selectedSongPath, int playableBlocksTotal,
         Map<NoteBlockInstrument, Integer> playableBlocksByInstrument, int currentTick) {
      if (client != null && client.player != null && !client.options.hideGui) {
         this.renderRequiredBlocksHud(graphics, client, settings, song, loadedSongPath, selectedSongPath,
               playableBlocksTotal, playableBlocksByInstrument, currentTick);
      }
   }

   private void renderRequiredBlocksHud(GuiGraphicsExtractor graphics, Minecraft client, LyraSettings settings, Song song,
         Path loadedSongPath, Path selectedSongPath, int playableBlocksTotal,
         Map<NoteBlockInstrument, Integer> playableBlocksByInstrument, int currentTick) {
      if (settings.showHud()) {
         if (shouldAutoHide(settings, song)) {
            return;
         }

         Song requirementsSong = this.requiredSongForHud(song, loadedSongPath, selectedSongPath);
         if (this.shouldRenderHudOnScreen(client.screen)) {
            List<RequiredBlocksHudModel.RequiredHudRow> rows = this.hudModel.buildRequiredBlocksRows(requirementsSong, playableBlocksTotal,
                  playableBlocksByInstrument, settings);
            if (!rows.isEmpty()) {
               int screenWidth = client.getWindow().getGuiScaledWidth();
               int screenHeight = client.getWindow().getGuiScaledHeight();
               float uiScale = Mth.clamp(Math.min((float) screenWidth / 320.0F, (float) screenHeight / 180.0F), 0.4F,
                     1.35F);
               int panelPadding = Math.max(5, Math.round(8.0F * uiScale));
               int rowHeight = Math.max(17, Math.round(18.0F * uiScale));
               int headerGap = Math.max(1, Math.round(client.font.lineHeight * HEADER_GAP_LINE_RATIO * uiScale));
               int timeOffset = client.font.lineHeight + headerGap;
               int progressOffset = timeOffset + client.font.lineHeight + headerGap;
               int headerHeight = progressOffset + PROGRESS_HEIGHT + headerGap;


               boolean showSongProgress = song != null || loadedSongPath != null || selectedSongPath != null
                     || requirementsSong != null;
               int headerSectionHeight = showSongProgress ? headerHeight : 0;
               int basePanelHeight = panelPadding * 2 + headerSectionHeight;
               int maxPanelHeight = Math.max(basePanelHeight + rowHeight, screenHeight - 8);

               if (showSongProgress && basePanelHeight + rowHeight > maxPanelHeight) {
                  showSongProgress = false;
                  headerSectionHeight = 0;
                  basePanelHeight = panelPadding * 2;
               }

               int maxRowsThatFit = Math.max(1, (maxPanelHeight - basePanelHeight) / rowHeight);
               int visibleRows = Math.min(rows.size(), maxRowsThatFit);
               int detailRows = visibleRows;
               int hiddenRows = 0;
               if (rows.size() > visibleRows && visibleRows > 0) {
                  detailRows = visibleRows - 1;
                  hiddenRows = rows.size() - detailRows;
               }

               int panelHeight = basePanelHeight + visibleRows * rowHeight;
               int valueColWidth = valueColumnWidth(client.font, rows);
               int contentWidth = 0;
               for (int i = 0; i < detailRows; i++) {
                  contentWidth = Math.max(contentWidth, ICON_COLUMN_WIDTH
                        + client.font.width(rows.get(i).instrumentName()) + COLUMN_GAP + valueColWidth * 2);
               }
               if (hiddenRows > 0) {
                  contentWidth = Math.max(contentWidth, ICON_COLUMN_WIDTH
                        + client.font.width(LyraMessenger.literalComponent("... +%1$s more", hiddenRows)));
               }
               if (showSongProgress) {
                  contentWidth = Math.max(contentWidth, TITLE_INSET
                        + client.font.width(formatSongName(song, loadedSongPath, selectedSongPath)));
                  Song activeSong = song != null ? song : requirementsSong;
                  int duration = playbackSeconds(activeSong == null ? 0 : activeSong.lastTick());
                  // Reserve the full timing width so the panel does not resize every second.
                  contentWidth = Math.max(contentWidth, client.font.width(formatTiming(duration, duration, 100)));
               }
               int panelWidth = Math.min(contentWidth + panelPadding * 2, Math.min(MAX_PANEL_WIDTH, screenWidth - 8));

               int x = 4;
               int y = 4;
               HudAnchor anchor = settings.hudAnchor() != null ? settings.hudAnchor() : HudAnchor.BOTTOM_RIGHT;
               switch (anchor) {
                  case TOP_LEFT -> {
                     x = 4;
                     y = 4;
                  }
                  case TOP_RIGHT -> {
                     x = Math.max(4, screenWidth - panelWidth - 4);
                     y = 4;
                  }
                  case BOTTOM_LEFT -> {
                     x = 4;
                     y = Math.max(4, screenHeight - panelHeight - 4);
                  }
                  case BOTTOM_RIGHT -> {
                     x = Math.max(4, screenWidth - panelWidth - 4);
                     y = Math.max(4, screenHeight - panelHeight - 4);
                  }
               }

               this.drawHudBackground(graphics, x, y, panelWidth, panelHeight);

               if (showSongProgress) {
                  int headerY = y + panelPadding;
                  int timeY = headerY + timeOffset;
                  int barX = x + panelPadding;
                  int barWidth = panelWidth - panelPadding * 2;

                  Song activeSong = song != null ? song : requirementsSong;
                  int lastTick = activeSong != null ? activeSong.lastTick() : 0;
                  int clampedCurrentTick = song != null ? Math.max(0, Math.min(currentTick, Math.max(lastTick, 0))) : 0;
                  int totalTicks = Math.max(1, lastTick);
                  float progress = Math.min(1.0F, (float) clampedCurrentTick / (float) totalTicks);
                  int progressPercent = Math.round(progress * 100.0F);

                  String songName = this.formatSongName(song, loadedSongPath, selectedSongPath);
                  String left = client.font.plainSubstrByWidth(songName, barWidth - TITLE_INSET);

                  int currentSec = playbackSeconds(clampedCurrentTick);
                  int totalSec = playbackSeconds(totalTicks);
                  String right = formatTiming(currentSec, totalSec, progressPercent);

                  graphics.fill(barX, headerY, barX + TITLE_ACCENT_WIDTH, headerY + client.font.lineHeight, DeskTheme.ACCENT);
                  graphics.text(client.font, left, barX + TITLE_INSET, headerY, DeskTheme.TEXT_PRIMARY, false);
                  graphics.text(client.font, client.font.plainSubstrByWidth(right, barWidth), barX, timeY,
                        DeskTheme.TEXT_SECONDARY, false);
                  drawProgressTrack(graphics, barX, headerY + progressOffset, barWidth, progress);
               }

               int iconX = x + panelPadding;
               int instrumentX = iconX + ICON_COLUMN_WIDTH;
               int valuesStartX = x + panelWidth - panelPadding - valueColWidth * 2;
               int requiredX = valuesStartX;
               int missingX = valuesStartX + valueColWidth;

               int rowY = y + panelPadding + headerSectionHeight;
               int maxInstrumentWidth = Math.max(20, valuesStartX - instrumentX - 4);

               for (int i = 0; i < detailRows; ++i) {
                  RequiredBlocksHudModel.RequiredHudRow row = rows.get(i);
                  this.drawHudSlot(graphics, new ItemStack(row.icon()), iconX, rowY + 1);
                  if (row.replacedIcon() != null) {
                     org.joml.Matrix3x2fStack matrixStack = graphics.pose();
                     matrixStack.pushMatrix();
                     matrixStack.translate((float) (iconX - 2), (float) (rowY + 8));
                     matrixStack.scale(0.6F, 0.6F);
                     graphics.item(new ItemStack(row.replacedIcon()), 0, 0);
                     matrixStack.popMatrix();
                  }
                  String instrumentName = client.font.plainSubstrByWidth(row.instrumentName().getString(),
                        maxInstrumentWidth);
                  graphics.text(client.font, instrumentName, instrumentX, rowY + 4, DeskTheme.TEXT_PRIMARY, false);
                  graphics.text(client.font, Integer.toString(row.required()), requiredX, rowY + 4, DeskTheme.TEXT_PRIMARY, false);
                  int displayVal = row.missing() == 0 ? row.available() : row.missing();
                  graphics.text(client.font, Integer.toString(displayVal), missingX, rowY + 4,
                        row.color(), false);
                  rowY += rowHeight;
               }

               if (hiddenRows > 0) {
                  graphics.text(client.font, LyraMessenger.literalComponent("... +%1$s more", hiddenRows),
                        instrumentX, rowY + 4, DeskTheme.TEXT_SECONDARY, false);
                  rowY += rowHeight;
               }
            }
         }
      }
   }

   private static int valueColumnWidth(Font font, List<RequiredBlocksHudModel.RequiredHudRow> rows) {
      int width = 0;
      for (var row : rows) {
         width = Math.max(width, font.width(Integer.toString(row.required())));
         width = Math.max(width, font.width(Integer.toString(Math.max(row.available(), row.missing()))));
      }
      return width + COLUMN_GAP;
   }

   private static String formatTiming(int currentSeconds, int totalSeconds, int percent) {
      return String.format(java.util.Locale.ROOT, "%02d:%02d / %02d:%02d (%d%%)",
            currentSeconds / 60, currentSeconds % 60, totalSeconds / 60, totalSeconds % 60, percent);
   }

   static int playbackSeconds(int tick) {
      return Math.max(0, tick) / 20;
   }

   static boolean shouldAutoHide(LyraSettings settings, Song activeSong) {
      return settings.hudAutoHide() && activeSong == null;
   }

   private boolean shouldRenderHudOnScreen(Screen screen) {
      return screen == null || screen instanceof ChatScreen || screen instanceof AbstractContainerScreen;
   }

   private String formatSongName(Song song, Path loadedSongPath, Path selectedSongPath) {
      if (song != null && song.title() != null && !song.title().trim().isEmpty()) {
         return song.title().trim();
      }
      Path activePath = loadedSongPath != null ? loadedSongPath : selectedSongPath;
      if (activePath != null) {
         String fileName = activePath.getFileName().toString();
         int dotIndex = fileName.lastIndexOf('.');
         return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
      }
      return "Unknown";
   }

   private void drawHudBackground(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
      graphics.fill(x, y, x + width, y + height, DeskTheme.PANEL_BACKGROUND);
      graphics.fill(x, y, x + width, y + 1, DeskTheme.BORDER_SUBTLE);
      graphics.fill(x, y + height - 1, x + width, y + height, DeskTheme.BORDER_SUBTLE);
   }

   private void drawProgressTrack(GuiGraphicsExtractor graphics, int x, int y, int width, float progress) {
      graphics.fill(x, y, x + width, y + PROGRESS_HEIGHT, DeskTheme.TRACK);
      int filledWidth = Math.round(width * progress);
      if (filledWidth > 0) {
         graphics.fill(x, y, x + filledWidth, y + PROGRESS_HEIGHT, DeskTheme.ACCENT);
      }
   }

   private void drawHudSlot(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
      graphics.item(stack, x, y);
   }

   public Song requiredSongForHud(Song currentSong, Path loadedSongPath, Path selectedSongPath) {
      return this.selectedSongCache.requiredSongForHud(currentSong, loadedSongPath, selectedSongPath);
   }

   public void refreshSelectedHudSongState(Path selectedSongPath, Song currentSong, Path loadedSongPath) {
      this.selectedSongCache.refreshSelectedHudSongState(selectedSongPath, currentSong, loadedSongPath);
   }

   public void ensureSelectedHudSongLoaded(Minecraft client, Path selectedSongPath, Song currentSong,
         Path loadedSongPath, LyraSettings settings) {
      this.selectedSongCache.ensureSelectedHudSongLoaded(client, selectedSongPath, currentSong, loadedSongPath, settings);
   }

   public void clearSelectedHudSong() {
      this.selectedSongCache.clearSelectedHudSong();
   }
}
