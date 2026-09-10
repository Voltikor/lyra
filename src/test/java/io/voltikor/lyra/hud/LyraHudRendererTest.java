package io.voltikor.lyra.hud;

import io.voltikor.lyra.MinecraftTestSupport;
import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.song.Note;
import io.voltikor.lyra.song.Song;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LyraHudRendererTest extends MinecraftTestSupport {
   @Test
   void playbackTimeUsesMinecraftTicksNotTuningDelay() {
      assertEquals(0, LyraHudRenderer.playbackSeconds(-1));
      assertEquals(0, LyraHudRenderer.playbackSeconds(19));
      assertEquals(1, LyraHudRenderer.playbackSeconds(20));
      assertEquals(3, LyraHudRenderer.playbackSeconds(60));
   }

   @Test
   void autoHideUsesActiveSongInsteadOfCachedRequirementsPreview() {
      LyraSettings settings = new LyraSettings();
      Song song = new Song("test", "author");
      song.addNote(0, new Note(NoteBlockInstrument.HARP, 12));
      song.finishLoading();

      assertFalse(LyraHudRenderer.shouldAutoHide(settings, song));
      assertTrue(LyraHudRenderer.shouldAutoHide(settings, null));
      settings.setHudAutoHide(false);
      assertFalse(LyraHudRenderer.shouldAutoHide(settings, null));
   }
}
