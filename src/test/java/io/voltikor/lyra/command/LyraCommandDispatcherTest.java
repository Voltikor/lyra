package io.voltikor.lyra.command;

import com.mojang.brigadier.CommandDispatcher;
import io.voltikor.lyra.MinecraftTestSupport;
import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.song.SongFileManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LyraCommandDispatcherTest extends MinecraftTestSupport {
   @Test
   void brigadierTreeValidatesRangesAndNestedArguments() {
      var handlers = new LyraCommandHandlers(new LyraSettings(), new SongFileManager(), null, null, null);
      var commands = new LyraCommandDispatcher(handlers);
      var brigadier = new CommandDispatcher<FabricClientCommandSource>();
      commands.register(brigadier);

      var map = brigadier.getRoot().getChild("lyra").getChild("map");
      assertEquals(5, brigadier.getAllUsage(map, null, false).length);
      assertTrue(brigadier.getRoot().getChild("lyra").getChildren().stream()
            .map(node -> node.getName())
            .toList()
            .containsAll(java.util.List.of("songs", "list", "preview", "transpose", "folder", "path")));
      assertParses(brigadier, "lyra delay 20");
      assertDoesNotParse(brigadier, "lyra delay 21");
      assertParses(brigadier, "lyra song map add harp bass");
      assertDoesNotParse(brigadier, "lyra song map add harp bass extra");
      assertParses(brigadier, "lyra play My Song.nbs");
   }

   private static void assertParses(CommandDispatcher<FabricClientCommandSource> dispatcher, String command) {
      var result = dispatcher.parse(command, (FabricClientCommandSource) null);
      assertFalse(result.getReader().canRead(), command);
      assertTrue(result.getExceptions().isEmpty(), command);
   }

   private static void assertDoesNotParse(CommandDispatcher<FabricClientCommandSource> dispatcher, String command) {
      var result = dispatcher.parse(command, (FabricClientCommandSource) null);
      assertTrue(result.getReader().canRead() || !result.getExceptions().isEmpty(), command);
   }
}
