package io.voltikor.lyra.command;

import io.voltikor.lyra.LyraClient;
import io.voltikor.lyra.config.LyraSettings;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

public final class LyraCommandDispatcher {
   @FunctionalInterface
   interface Handler { boolean run(Minecraft client, String args); }
   record Definition(String name, List<String> aliases, String usage, List<String> patterns, Handler handler) {}
   private final List<Definition> definitions = new ArrayList<>();
   private final Map<String, Definition> byName = new LinkedHashMap<>();
   private final LyraCommandHandlers handlers;

   LyraCommandDispatcher() {
      this(null);
   }

   public LyraCommandDispatcher(LyraCommandHandlers handlers) {
      this.handlers = handlers;
      add("help", "", List.of(), (c, a) -> {
         if (!a.isEmpty()) {
            LyraMessenger.error(c, "Usage: /lyra help");
            return false;
         }
         sendHelp(c);
         return true;
      });
      action("songs", c -> this.handlers().listSongs(c), "list");
      add("load", "<file>", List.of("<file>"), (c, a) -> this.handlers().load(c, a));
      add("play", "[file]", List.of("<file>"), (c, a) -> this.handlers().play(c, a));
      add("preview", "[file]", List.of("<file>"), (c, a) -> this.handlers().preview(c, a));
      add("tune", "", List.of(), (c, a) -> {
         if (!a.isEmpty()) {
            LyraMessenger.error(c, "Usage: /lyra tune");
            return false;
         }
         return this.handlers().tune(c);
      });
      add("pause", "", List.of(), (c, a) -> {
         if (!a.isEmpty()) {
            LyraMessenger.error(c, "Usage: /lyra pause");
            return false;
         }
         return this.handlers().pause(c);
      });
      action("stop", c -> this.handlers().stop(c));
      action("status", c -> this.handlers().sendStatus(c));
      action("center", c -> this.handlers().centerPlayer(c));
      add("random", "", List.of(), (c, a) -> {
         if (!a.isEmpty()) {
            LyraMessenger.error(c, "Usage: /lyra random");
            return false;
         }
         return this.handlers().playRandom(c);
      });
      action("folder", c -> LyraMessenger.replyFormatted(c, "Song folder: %1$s", this.handlers().songsFolderLabel()), "path");
      add("mode", "<exact|any>", List.of("exact", "any"), (c, a) -> this.handlers().setMode(c, a));
      add("detect", "<blockstate|below>", List.of("blockstate", "below"), (c, a) -> this.handlers().setInstrumentDetectMode(c, a));
      add("tempo", "<default|snap_nearest|snap_up|snap_down>", tempos(), (c, a) -> this.handlers().setTempoQuantization(c, a));
      add("delay", "<1-20>", List.of(), (c, a) -> this.handlers().setTickDelay(c, a));
      add("checknoteblocksagaindelay", "<1-100>", List.of(), (c, a) -> this.handlers().setCheckNoteblocksAgainDelay(c, a));
      add("concurrent", "<1-20|unlimited>", List.of("unlimited"), (c, a) -> this.handlers().setConcurrentTuneBlocks(c, a));
      for (String flag : List.of("polyphonic", "rotate", "autoplay", "swing")) {
         add(flag, "<on|off>", List.of("on", "off"), (c, a) -> this.handlers().setBooleanFlag(c, flag, a),
               new String[0]);
      }
      add("transpose", "<" + io.voltikor.lyra.config.TransposeSetting.usage() + ">", io.voltikor.lyra.config.TransposeSetting.options(),
            (c, a) -> this.handlers().setTranspose(c, a));
      add("rotatemode", "<visible_face|closest_face>", List.of("visible_face", "closest_face"), (c, a) -> this.handlers().setRotateMode(c, a));
      add("round", "<drop|clamp|fold|remap>", List.of("drop", "clamp", "fold", "remap", "on", "off"), (c, a) -> this.handlers().setOutOfRangeMode(c, a));
      add("hud", "[on|off|autohide <on|off>|anchor <position>]", List.of("on", "off", "autohide on", "autohide off", "anchor top_left", "anchor top_right", "anchor bottom_left", "anchor bottom_right"), (c, a) -> this.handlers().handleHudCommand(c, a));
      add("map", "[add <from> <to>|remove <from>|clear|list]", mappings(), (c, a) -> this.handlers().setInstrumentMap(c, a));
      List<String> songPatterns = new ArrayList<>(List.of("config", "clear", "map", "tempo reset",
            "transpose reset", "round reset"));
      for (String value : io.voltikor.lyra.config.TransposeSetting.options()) {
         songPatterns.add("transpose " + value);
      }
      for (String value : tempos()) songPatterns.add("tempo " + value);
      for (String value : rounds()) songPatterns.add("round " + value);
      for (String value : mappings()) songPatterns.add("map " + value);
      add("song", "<config|clear|transpose <" + io.voltikor.lyra.config.TransposeSetting.usage() + "|reset>|tempo <mode|reset>|round <mode|reset>|map [add <from> <to>|remove <from>|clear|list]>", songPatterns,
            (c, a) -> this.handlers().handleSongConfig(c, a));
   }

   private static List<String> tempos() { return List.of("default", "snap_nearest", "snap_up", "snap_down"); }
   private static List<String> rounds() { return List.of("default", "drop", "clamp", "fold", "remap", "on", "off"); }
   private static List<String> mappings() { return List.of("list", "clear", "remove <instrument>", "add <instrument> <instrument>"); }

   private void action(String name, Consumer<Minecraft> action, String... aliases) {
      add(name, "", List.of(), (client, args) -> {
         if (!args.isEmpty()) {
            LyraMessenger.error(client, "Usage: /lyra " + name);
            return false;
         }
         action.accept(client);
         return true;
      }, aliases);
   }

   private void add(String name, String usage, List<String> patterns, Handler handler, String... aliases) {
      Definition definition = new Definition(name, List.of(aliases), usage, List.copyOf(patterns), handler);
      this.definitions.add(definition);
      this.byName.put(name, definition);
      for (String alias : aliases) this.byName.put(alias, definition);
   }

   List<Definition> definitions() { return List.copyOf(this.definitions); }
   public List<String> commandNames() { return List.copyOf(this.byName.keySet()); }

   public void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
      var root = ClientCommands.literal("lyra").executes(ctx -> execute(ctx.getSource().getClient(), "help", ""));
      this.definitions.forEach(definition -> {
         registerLiteral(root, definition.name(), definition);
         for (String alias : definition.aliases()) registerLiteral(root, alias, definition);
      });
      dispatcher.register(root);
   }

   private void registerLiteral(com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> root,
         String name, Definition definition) {
      root.then(ClientCommands.literal(name)
            .executes(ctx -> execute(ctx.getSource().getClient(), definition.name(), ""))
            .then(ClientCommands.argument("args", StringArgumentType.greedyString())
                  .suggests((ctx, builder) -> {
                     List<String> songs = this.handlers == null ? List.of() : this.handlers.availableSongNames();
                     for (String value : suggestions(definition.name(), builder.getRemaining(), songs)) builder.suggest(value);
                     return builder.buildFuture();
                  })
                  .executes(ctx -> execute(ctx.getSource().getClient(), definition.name(), StringArgumentType.getString(ctx, "args")))));
   }

   public int execute(Minecraft client, String name, String args) {
      Definition command = this.byName.get(name.toLowerCase(Locale.ROOT));
      if (command == null) {
         LyraMessenger.error(client, "Unknown command. Use /lyra help");
         return 0;
      }
      return command.handler().run(client, args.trim()) ? 1 : 0;
   }

   public boolean handleChatCommand(Minecraft client, String input) {
      String payload = extractChatPayload(input);
      if (payload == null) return false;
      executePayload(client, payload);
      return true; // Keep recognized Lyra commands local even when their arguments are invalid.
   }

   public boolean handleSlashCommand(Minecraft client, String input) {
      String payload = extractSlashPayload(input);
      if (payload == null) return false;
      executePayload(client, payload);
      return true;
   }

   public int executePayload(Minecraft client, String payload) {
      String[] parts = payload.trim().split("\\s+", 2);
      return execute(client, parts[0].isEmpty() ? "help" : parts[0], parts.length == 2 ? parts[1] : "");
   }

   private LyraCommandHandlers handlers() {
      if (this.handlers == null) {
         throw new IllegalStateException("Command handlers are not configured");
      }
      return this.handlers;
   }

   public String extractChatPayload(String input) {
      return input != null && input.startsWith("/") ? extractSlashPayload(input) : null;
   }

   public String extractSlashPayload(String input) {
      if (input == null) return null;
      String value = input.trim();
      if (value.startsWith("/")) value = value.substring(1).trim();
      String[] parts = value.split("\\s+", 2);
      return parts[0].equalsIgnoreCase("lyra") ? (parts.length == 2 ? parts[1] : "") : null;
   }

   public void sendHelp(Minecraft client) {
      LyraMessenger.reply(client, "Lyra commands:");
      for (Definition definition : this.definitions) {
         String aliases = definition.aliases().isEmpty() ? "" : " (aliases: " + String.join(", ", definition.aliases()) + ")";
         LyraMessenger.reply(client, "/lyra " + definition.name() + (definition.usage().isEmpty() ? "" : " " + definition.usage()) + aliases);
      }
      LyraMessenger.reply(client, "Play/Pause: " + LyraClient.TOGGLE_PLAY_PAUSE_KEYBIND.getTranslatedKeyMessage().getString() + "; TAB: command completion");
   }

   public List<String> suggestions(String name, String args, List<String> songs) {
      Definition definition = this.byName.get(name.toLowerCase(Locale.ROOT));
      if (definition == null) return List.of();
      if (definition.patterns().contains("<file>")) return songs.stream().filter(value -> startsWith(value, args)).toList();
      String[] entered = args.split("\\s+", -1);
      int index = entered.length - 1;
      String prefix = index == 0 ? "" : args.substring(0, args.length() - entered[index].length());
      List<String> result = new ArrayList<>();
      for (String pattern : definition.patterns()) {
         String[] tokens = pattern.split(" ");
         if (index >= tokens.length) continue;
         boolean matches = true;
         for (int i = 0; i < index; i++) {
            if (!tokens[i].equals("<instrument>") && !tokens[i].equalsIgnoreCase(entered[i])) matches = false;
         }
         if (!matches) continue;
         List<String> options = tokens[index].equals("<instrument>")
               ? Arrays.stream(NoteBlockInstrument.values()).map(LyraSettings::prettyName).toList() : List.of(tokens[index]);
         for (String option : options) if (startsWith(option, entered[index])) result.add(prefix + option);
      }
      return result.stream().distinct().toList();
   }

   private static boolean startsWith(String value, String prefix) {
      return value.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT));
   }
}
