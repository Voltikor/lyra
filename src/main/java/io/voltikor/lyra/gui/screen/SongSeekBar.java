package io.voltikor.lyra.gui.screen;

import io.voltikor.lyra.gui.DeskTheme;
import io.voltikor.lyra.playback.PlaybackCoordinator;
import io.voltikor.lyra.playback.PlaybackSnapshot;
import io.voltikor.lyra.song.Song;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

final class SongSeekBar extends AbstractSliderButton {
   private static final int HEIGHT = 20;
   // Match AbstractSliderButton's four-pixel inset so rendering agrees with seeking.
   private static final int TRACK_INSET = 4;
   private static final int TRACK_BOTTOM_OFFSET = 5;
   private static final int TRACK_HEIGHT = 2;
   private static final int THUMB_HALF_WIDTH = 2;
   private static final int THUMB_OVERHANG = 2;
   private final PlaybackCoordinator playback;
   private Song song;
   private Song dragSong;
   private boolean dragging;

   SongSeekBar(int x, int y, int width, PlaybackCoordinator playback) {
      super(x, y, width, HEIGHT, Component.literal("Seek"), 0);
      this.playback = playback;
      setTooltip(Tooltip.create(Component.literal("Click or drag to seek. Use the arrow keys for fine adjustments.")));
      sync();
   }

   void sync() {
      PlaybackSnapshot snapshot = playback.snapshot();
      active = snapshot.canSeek();
      if (song != snapshot.song()) {
         song = snapshot.song();
      }
      if (!dragging || dragSong != song) {
         value = song == null || song.lastTick() == 0 ? 0
               : Math.clamp((double) snapshot.currentTick() / song.lastTick(), 0, 1);
         updateMessage();
      }
   }

   @Override
   public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
      var font = Minecraft.getInstance().font;
      graphics.text(font, getMessage(), getX(), getY() + 1, DeskTheme.TEXT_SECONDARY, false);
      int trackY = getBottom() - TRACK_BOTTOM_OFFSET;
      int cursor = getX() + TRACK_INSET + (int) Math.round(value * (width - 2 * TRACK_INSET));
      graphics.fill(getX() + TRACK_INSET, trackY, getRight() - TRACK_INSET, trackY + TRACK_HEIGHT, DeskTheme.TRACK);
      if (active) {
         graphics.fill(getX() + TRACK_INSET, trackY, cursor, trackY + TRACK_HEIGHT, DeskTheme.ACCENT);
         graphics.fill(cursor - THUMB_HALF_WIDTH, trackY - THUMB_OVERHANG, cursor + THUMB_HALF_WIDTH, trackY + TRACK_HEIGHT + THUMB_OVERHANG,
               isHovered() || isFocused() ? DeskTheme.TEXT_PRIMARY : DeskTheme.ACCENT);
      }
   }

   @Override
   public void onClick(MouseButtonEvent event, boolean doubleClick) {
      dragging = true;
      dragSong = song;
      super.onClick(event, doubleClick);
   }

   @Override
   public void onRelease(MouseButtonEvent event) {
      super.onRelease(event);
      dragging = false;
      dragSong = null;
      sync();
   }

   @Override
   protected void updateMessage() {
      setMessage(Component.literal(song == null ? "Choose a song to begin"
            : time((int) Math.round(value * song.lastTick())) + "  /  " + time(song.lastTick())));
   }

   @Override
   protected void applyValue() {
      // A delayed drag event must never seek a newly loaded song.
      if (song != null && playback.snapshot().song() == song && (!dragging || dragSong == song)) {
         playback.seek(Minecraft.getInstance(), (int) Math.round(value * song.lastTick()));
      }
   }

   static String time(int ticks) {
      int seconds = ticks / 20;
      return "%d:%02d".formatted(seconds / 60, seconds % 60);
   }
}
