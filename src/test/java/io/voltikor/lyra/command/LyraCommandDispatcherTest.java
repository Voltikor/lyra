package io.voltikor.lyra.command;

import io.voltikor.lyra.MinecraftTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LyraCommandDispatcherTest extends MinecraftTestSupport {
   @Test
   void catalogContainsCanonicalCommandsAndAliases() {
      var dispatcher = new LyraCommandDispatcher();

      assertTrue(dispatcher.commandNames().containsAll(List.of("songs", "list", "preview", "transpose")));
      assertTrue(java.util.Collections.disjoint(dispatcher.commandNames(),
            List.of("previewinstruments", "preview-instruments", "instruments")));
      assertEquals("", dispatcher.extractSlashPayload("/LYRA"));
      assertEquals("play My Song.nbs", dispatcher.extractSlashPayload("/lyra play My Song.nbs"));
      assertEquals("preview My Song.nbs", dispatcher.extractChatPayload("/lyra preview My Song.nbs"));
   }

   @Test
   void suggestionsComeFromCommandMetadataAndPreserveFileSpaces() {
      var dispatcher = new LyraCommandDispatcher();

      assertEquals(List.of("My Song.nbs"), dispatcher.suggestions("load", "My ",
            List.of("My Song.nbs", "Other.txt")));
      assertTrue(dispatcher.suggestions("map", "add ", List.of()).contains("add harp"));
      assertTrue(dispatcher.suggestions("hud", "anchor ", List.of()).contains("anchor top_left"));
      assertTrue(dispatcher.suggestions("song", "round ", List.of()).contains("round clamp"));
      assertTrue(dispatcher.suggestions("song", "transpose ", List.of()).contains("transpose off"));
      assertEquals(io.voltikor.lyra.config.TransposeSetting.options(), dispatcher.suggestions("transpose", "", List.of()));
      assertTrue(dispatcher.suggestions("song", "transpose ", List.of()).contains("transpose +24"));
   }
}
