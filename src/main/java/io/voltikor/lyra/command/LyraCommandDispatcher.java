package io.voltikor.lyra.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.voltikor.lyra.LyraClient;
import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.config.TransposeSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

/** Registers Lyra as a native Fabric client-side Brigadier command tree. */
@Environment(EnvType.CLIENT)
public final class LyraCommandDispatcher {
   private static final List<String> TEMPOS = List.of("default", "snap_nearest", "snap_up", "snap_down");
   private static final List<String> ROUND_MODES = List.of("drop", "clamp", "fold", "remap", "on", "off");
   private static final List<String> HUD_ANCHORS = List.of("top_left", "top_right", "bottom_left", "bottom_right");

   private final LyraCommandHandlers handlers;
   private CommandDispatcher<FabricClientCommandSource> dispatcher;
   private LiteralCommandNode<FabricClientCommandSource> rootNode;

   public LyraCommandDispatcher(LyraCommandHandlers handlers) {
      this.handlers = handlers;
   }

   public void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
      LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal("lyra")
            .executes(this::sendHelp);
      this.registerActions(root);
      this.registerSettings(root);
      this.registerInstrumentMap(root, "map", false);
      this.registerSongSettings(root);
      this.dispatcher = dispatcher;
      this.rootNode = dispatcher.register(root);
   }

   private void registerActions(LiteralArgumentBuilder<FabricClientCommandSource> root) {
      root.then(ClientCommands.literal("help").executes(this::sendHelp));
      addAction(root, "songs", this.handlers::listSongs);
      addAction(root, "list", this.handlers::listSongs);
      addFileCommand(root, "load", false, this.handlers::load);
      addFileCommand(root, "play", true, this.handlers::play);
      addFileCommand(root, "preview", true, this.handlers::preview);
      addBooleanAction(root, "tune", this.handlers::tune);
      addBooleanAction(root, "pause", this.handlers::pause);
      addAction(root, "stop", this.handlers::stop);
      addAction(root, "status", this.handlers::sendStatus);
      addAction(root, "center", this.handlers::centerPlayer);
      addBooleanAction(root, "random", this.handlers::playRandom);
      Consumer<Minecraft> folder = client -> LyraMessenger.replyFormatted(client,
            "Song folder: %1$s", this.handlers.songsFolderLabel());
      addAction(root, "folder", folder);
      addAction(root, "path", folder);
   }

   private void registerSettings(LiteralArgumentBuilder<FabricClientCommandSource> root) {
      addChoice(root, "mode", List.of("exact", "any"), this.handlers::setMode);
      addChoice(root, "detect", List.of("blockstate", "below"), this.handlers::setInstrumentDetectMode);
      addChoice(root, "tempo", TEMPOS, this.handlers::setTempoQuantization);
      addInteger(root, "delay", "ticks", 1, 20, this.handlers::setTickDelay);
      addInteger(root, "checknoteblocksagaindelay", "ticks", 1, 100,
            this.handlers::setCheckNoteblocksAgainDelay);

      LiteralArgumentBuilder<FabricClientCommandSource> concurrent = ClientCommands.literal("concurrent");
      concurrent.then(ClientCommands.literal("unlimited")
            .executes(context -> result(this.handlers.setConcurrentTuneBlocks(client(context), "unlimited"))));
      concurrent.then(ClientCommands.argument("count", IntegerArgumentType.integer(1, 20))
            .executes(context -> result(this.handlers.setConcurrentTuneBlocks(client(context),
                  Integer.toString(IntegerArgumentType.getInteger(context, "count"))))));
      root.then(concurrent);

      for (String flag : List.of("polyphonic", "rotate", "autoplay", "swing")) {
         addToggle(root, flag, (client, value) -> this.handlers.setBooleanFlag(client, flag, value));
      }
      addChoice(root, "transpose", TransposeSetting.options(), this.handlers::setTranspose);
      addChoice(root, "rotatemode", List.of("visible_face", "closest_face"), this.handlers::setRotateMode);
      addChoice(root, "round", ROUND_MODES, this.handlers::setOutOfRangeMode);

      LiteralArgumentBuilder<FabricClientCommandSource> hud = ClientCommands.literal("hud")
            .executes(context -> result(this.handlers.handleHudCommand(client(context), "")));
      addToggleChildren(hud, this.handlers::handleHudCommand);
      hud.then(choiceNode("anchor", HUD_ANCHORS,
            (minecraft, value) -> this.handlers.handleHudCommand(minecraft, "anchor " + value)));
      hud.then(toggleNode("autohide",
            (minecraft, value) -> this.handlers.handleHudCommand(minecraft, "autohide " + value)));
      root.then(hud);
   }

   private void registerSongSettings(LiteralArgumentBuilder<FabricClientCommandSource> root) {
      LiteralArgumentBuilder<FabricClientCommandSource> song = ClientCommands.literal("song");
      addSongAction(song, "config", "config");
      addSongAction(song, "clear", "clear");
      song.then(songChoiceNode("tempo", append(TEMPOS, "reset")));
      song.then(songChoiceNode("transpose", append(TransposeSetting.options(), "reset")));
      song.then(songChoiceNode("round", append(ROUND_MODES, "reset")));
      this.registerInstrumentMap(song, "map", true);
      root.then(song);
   }

   private void registerInstrumentMap(LiteralArgumentBuilder<FabricClientCommandSource> parent, String name,
         boolean perSong) {
      BiFunction<Minecraft, String, Boolean> handler = perSong
            ? (client, args) -> this.handlers.handleSongConfig(client, "map" + (args.isEmpty() ? "" : " " + args))
            : this.handlers::setInstrumentMap;
      LiteralArgumentBuilder<FabricClientCommandSource> map = ClientCommands.literal(name)
            .executes(context -> result(handler.apply(client(context), "list")));
      map.then(ClientCommands.literal("list")
            .executes(context -> result(handler.apply(client(context), "list"))));
      map.then(ClientCommands.literal("clear")
            .executes(context -> result(handler.apply(client(context), "clear"))));

      SuggestionProvider<FabricClientCommandSource> instruments = (context, builder) -> {
         String remaining = builder.getRemainingLowerCase();
         for (NoteBlockInstrument instrument : NoteBlockInstrument.values()) {
            String label = LyraSettings.prettyName(instrument);
            if (label.toLowerCase(Locale.ROOT).startsWith(remaining)) {
               builder.suggest(label);
            }
         }
         return builder.buildFuture();
      };
      map.then(ClientCommands.literal("remove")
            .then(ClientCommands.argument("from", StringArgumentType.word()).suggests(instruments)
                  .executes(context -> result(handler.apply(client(context),
                        "remove " + StringArgumentType.getString(context, "from"))))));
      map.then(ClientCommands.literal("add")
            .then(ClientCommands.argument("from", StringArgumentType.word()).suggests(instruments)
                  .then(ClientCommands.argument("to", StringArgumentType.word()).suggests(instruments)
                        .executes(context -> result(handler.apply(client(context), "add "
                              + StringArgumentType.getString(context, "from") + " "
                              + StringArgumentType.getString(context, "to")))))));
      parent.then(map);
   }

   private LiteralArgumentBuilder<FabricClientCommandSource> songChoiceNode(String name, List<String> values) {
      return choiceNode(name, values,
            (client, value) -> this.handlers.handleSongConfig(client, name + " " + value));
   }

   private void addSongAction(LiteralArgumentBuilder<FabricClientCommandSource> parent, String name, String args) {
      parent.then(ClientCommands.literal(name)
            .executes(context -> result(this.handlers.handleSongConfig(client(context), args))));
   }

   private void addFileCommand(LiteralArgumentBuilder<FabricClientCommandSource> root, String name,
         boolean optional, BiFunction<Minecraft, String, Boolean> handler) {
      LiteralArgumentBuilder<FabricClientCommandSource> command = ClientCommands.literal(name);
      if (optional) {
         command.executes(context -> result(handler.apply(client(context), "")));
      }
      command.then(ClientCommands.argument("file", StringArgumentType.greedyString())
            .suggests((context, builder) -> {
               String remaining = builder.getRemainingLowerCase();
               for (String song : this.handlers.availableSongNames()) {
                  if (song.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                     builder.suggest(song);
                  }
               }
               return builder.buildFuture();
            })
            .executes(context -> result(handler.apply(client(context),
                  StringArgumentType.getString(context, "file")))));
      root.then(command);
   }

   private static void addAction(LiteralArgumentBuilder<FabricClientCommandSource> root, String name,
         Consumer<Minecraft> action) {
      root.then(ClientCommands.literal(name).executes(context -> {
         action.accept(client(context));
         return 1;
      }));
   }

   private static void addBooleanAction(LiteralArgumentBuilder<FabricClientCommandSource> root, String name,
         Predicate<Minecraft> action) {
      root.then(ClientCommands.literal(name).executes(context -> result(action.test(client(context)))));
   }

   private static void addInteger(LiteralArgumentBuilder<FabricClientCommandSource> root, String name,
         String argumentName, int minimum, int maximum, BiFunction<Minecraft, String, Boolean> handler) {
      root.then(ClientCommands.literal(name)
            .then(ClientCommands.argument(argumentName, IntegerArgumentType.integer(minimum, maximum))
                  .executes(context -> result(handler.apply(client(context),
                        Integer.toString(IntegerArgumentType.getInteger(context, argumentName)))))));
   }

   private static void addChoice(LiteralArgumentBuilder<FabricClientCommandSource> root, String name,
         List<String> choices, BiFunction<Minecraft, String, Boolean> handler) {
      root.then(choiceNode(name, choices, handler));
   }

   private static LiteralArgumentBuilder<FabricClientCommandSource> choiceNode(String name, List<String> choices,
         BiFunction<Minecraft, String, Boolean> handler) {
      LiteralArgumentBuilder<FabricClientCommandSource> command = ClientCommands.literal(name);
      for (String choice : choices) {
         command.then(ClientCommands.literal(choice)
               .executes(context -> result(handler.apply(client(context), choice))));
      }
      return command;
   }

   private static void addToggle(LiteralArgumentBuilder<FabricClientCommandSource> root, String name,
         BiFunction<Minecraft, String, Boolean> handler) {
      root.then(toggleNode(name, handler));
   }

   private static LiteralArgumentBuilder<FabricClientCommandSource> toggleNode(String name,
         BiFunction<Minecraft, String, Boolean> handler) {
      LiteralArgumentBuilder<FabricClientCommandSource> command = ClientCommands.literal(name);
      addToggleChildren(command, handler);
      return command;
   }

   private static void addToggleChildren(LiteralArgumentBuilder<FabricClientCommandSource> command,
         BiFunction<Minecraft, String, Boolean> handler) {
      for (String value : List.of("on", "off")) {
         command.then(ClientCommands.literal(value)
               .executes(context -> result(handler.apply(client(context), value))));
      }
   }

   private static Minecraft client(CommandContext<FabricClientCommandSource> context) {
      return context.getSource().getClient();
   }

   private static int result(boolean success) {
      return success ? 1 : 0;
   }

   private int sendHelp(CommandContext<FabricClientCommandSource> context) {
      Minecraft client = client(context);
      LyraMessenger.reply(client, "Lyra commands:");
      for (String usage : this.dispatcher.getAllUsage(this.rootNode, context.getSource(), true)) {
         LyraMessenger.reply(client, "/lyra " + usage);
      }
      LyraMessenger.reply(client, "Play/Pause: "
            + LyraClient.TOGGLE_PLAY_PAUSE_KEYBIND.getTranslatedKeyMessage().getString()
            + "; TAB: command completion");
      return 1;
   }

   private static List<String> append(List<String> values, String extra) {
      List<String> result = new ArrayList<>(values);
      result.add(extra);
      return List.copyOf(result);
   }
}
