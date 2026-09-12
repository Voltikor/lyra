package io.voltikor.lyra.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.voltikor.lyra.LyraClient;
import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.config.TransposeSetting;
import io.voltikor.lyra.hud.HudAnchor;
import io.voltikor.lyra.noteblock.InstrumentDetectMode;
import io.voltikor.lyra.noteblock.InstrumentMatchMode;
import io.voltikor.lyra.noteblock.RotateMode;
import io.voltikor.lyra.song.OutOfRangeMode;
import io.voltikor.lyra.song.TempoQuantization;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

@Environment(EnvType.CLIENT)
public final class LyraCommandDispatcher {
   private static final List<Choice<InstrumentMatchMode>> MODES = List.of(
         new Choice<>("exact", InstrumentMatchMode.EXACT_INSTRUMENTS),
         new Choice<>("any", InstrumentMatchMode.ANY_INSTRUMENT));
   private static final List<Choice<InstrumentDetectMode>> DETECT_MODES = List.of(
         new Choice<>("blockstate", InstrumentDetectMode.BLOCK_STATE),
         new Choice<>("below", InstrumentDetectMode.BELOW_BLOCK));
   private static final List<Choice<TempoQuantization>> TEMPOS = enumChoices(
         TempoQuantization.values(), value -> value.name().toLowerCase(Locale.ROOT));
   private static final List<Choice<TransposeSetting>> TRANSPOSES = enumChoices(
         TransposeSetting.values(), TransposeSetting::serialized);
   private static final List<Choice<RotateMode>> ROTATE_MODES = enumChoices(
         RotateMode.values(), value -> value.name().toLowerCase(Locale.ROOT));
   private static final List<Choice<HudAnchor>> HUD_ANCHORS = enumChoices(
         HudAnchor.values(), value -> value.name().toLowerCase(Locale.ROOT));
   private static final List<Choice<OutOfRangeMode>> ROUND_MODES = List.of(
         new Choice<>("drop", OutOfRangeMode.DROP), new Choice<>("clamp", OutOfRangeMode.CLAMP),
         new Choice<>("fold", OutOfRangeMode.FOLD), new Choice<>("remap", OutOfRangeMode.REMAP),
         new Choice<>("on", OutOfRangeMode.CLAMP), new Choice<>("off", OutOfRangeMode.DROP));

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
      addChoice(root, "mode", MODES, this.handlers::setMode);
      addChoice(root, "detect", DETECT_MODES, this.handlers::setInstrumentDetectMode);
      addChoice(root, "tempo", TEMPOS, this.handlers::setTempoQuantization);
      addInteger(root, "delay", "ticks", 1, 20, this.handlers::setTickDelay);
      addInteger(root, "checknoteblocksagaindelay", "ticks", 1, 100,
            this.handlers::setCheckNoteblocksAgainDelay);

      LiteralArgumentBuilder<FabricClientCommandSource> concurrent = ClientCommands.literal("concurrent");
      concurrent.then(ClientCommands.literal("unlimited")
            .executes(context -> result(this.handlers.setConcurrentTuneBlocks(client(context), 0))));
      concurrent.then(ClientCommands.argument("count", IntegerArgumentType.integer(1, 20))
            .executes(context -> result(this.handlers.setConcurrentTuneBlocks(client(context),
                  IntegerArgumentType.getInteger(context, "count")))));
      root.then(concurrent);

      for (BooleanSetting setting : BooleanSetting.values()) {
         addToggle(root, setting.commandName(),
               (client, enabled) -> this.handlers.setBooleanFlag(client, setting, enabled));
      }
      addChoice(root, "transpose", TRANSPOSES, this.handlers::setTranspose);
      addChoice(root, "rotatemode", ROTATE_MODES, this.handlers::setRotateMode);
      addChoice(root, "round", ROUND_MODES, this.handlers::setOutOfRangeMode);

      LiteralArgumentBuilder<FabricClientCommandSource> hud = ClientCommands.literal("hud")
            .executes(context -> result(this.handlers.toggleHud(client(context))));
      addToggleChildren(hud, this.handlers::setShowHud);
      hud.then(choiceNode("anchor", HUD_ANCHORS, this.handlers::setHudAnchor));
      hud.then(toggleNode("autohide", this.handlers::setHudAutoHide));
      root.then(hud);
   }

   private void registerSongSettings(LiteralArgumentBuilder<FabricClientCommandSource> root) {
      LiteralArgumentBuilder<FabricClientCommandSource> song = ClientCommands.literal("song");
      addSongAction(song, "config", this.handlers::showSongConfig);
      addSongAction(song, "clear", this.handlers::clearSongConfig);
      song.then(songChoiceNode("tempo", TEMPOS, this.handlers::setSongTempo, this.handlers::clearSongTempo));
      song.then(songChoiceNode("transpose", TRANSPOSES,
            this.handlers::setSongTranspose, this.handlers::clearSongTranspose));
      song.then(songChoiceNode("round", ROUND_MODES,
            this.handlers::setSongOutOfRangeMode, this.handlers::clearSongOutOfRangeMode));
      this.registerInstrumentMap(song, "map", true);
      root.then(song);
   }

   private void registerInstrumentMap(LiteralArgumentBuilder<FabricClientCommandSource> parent, String name,
         boolean perSong) {
      Function<Minecraft, Boolean> list = perSong
            ? this.handlers::listSongInstrumentMap : this.handlers::listInstrumentMap;
      Function<Minecraft, Boolean> clear = perSong
            ? this.handlers::clearSongInstrumentMap : this.handlers::clearInstrumentMap;
      BiFunction<Minecraft, NoteBlockInstrument, Boolean> remove = perSong
            ? this.handlers::removeSongInstrumentMap : this.handlers::removeInstrumentMap;
      InstrumentMapHandler add = perSong
            ? this.handlers::setSongInstrumentMap : this.handlers::setInstrumentMap;

      LiteralArgumentBuilder<FabricClientCommandSource> map = ClientCommands.literal(name)
            .executes(context -> result(list.apply(client(context))));
      map.then(ClientCommands.literal("list").executes(context -> result(list.apply(client(context)))));
      map.then(ClientCommands.literal("clear").executes(context -> result(clear.apply(client(context)))));
      map.then(ClientCommands.literal("remove")
            .then(instrumentArgument("from").executes(context -> result(remove.apply(
                  client(context), instrument(context, "from"))))));
      map.then(ClientCommands.literal("add")
            .then(instrumentArgument("from")
                  .then(instrumentArgument("to").executes(context -> result(add.apply(
                        client(context), instrument(context, "from"), instrument(context, "to")))))));
      parent.then(map);
   }

   private static com.mojang.brigadier.builder.RequiredArgumentBuilder<FabricClientCommandSource, String>
         instrumentArgument(String name) {
      SuggestionProvider<FabricClientCommandSource> suggestions = (context, builder) -> {
         String remaining = builder.getRemainingLowerCase();
         for (NoteBlockInstrument instrument : NoteBlockInstrument.values()) {
            String label = LyraSettings.prettyName(instrument);
            if (label.startsWith(remaining)) builder.suggest(label);
         }
         return builder.buildFuture();
      };
      return ClientCommands.argument(name, StringArgumentType.word()).suggests(suggestions);
   }

   private static NoteBlockInstrument instrument(CommandContext<FabricClientCommandSource> context, String name) {
      return LyraSettings.parseInstrument(StringArgumentType.getString(context, name));
   }

   private static <T> LiteralArgumentBuilder<FabricClientCommandSource> songChoiceNode(String name,
         List<Choice<T>> choices, BiFunction<Minecraft, T, Boolean> handler,
         Function<Minecraft, Boolean> resetHandler) {
      LiteralArgumentBuilder<FabricClientCommandSource> command = choiceNode(name, choices, handler);
      command.then(ClientCommands.literal("reset")
            .executes(context -> result(resetHandler.apply(client(context)))));
      return command;
   }

   private static void addSongAction(LiteralArgumentBuilder<FabricClientCommandSource> parent, String name,
         Function<Minecraft, Boolean> action) {
      parent.then(ClientCommands.literal(name)
            .executes(context -> result(action.apply(client(context)))));
   }

   private void addFileCommand(LiteralArgumentBuilder<FabricClientCommandSource> root, String name,
         boolean optional, BiFunction<Minecraft, String, Boolean> handler) {
      LiteralArgumentBuilder<FabricClientCommandSource> command = ClientCommands.literal(name);
      if (optional) command.executes(context -> result(handler.apply(client(context), "")));
      command.then(ClientCommands.argument("file", StringArgumentType.greedyString())
            .suggests((context, builder) -> {
               String remaining = builder.getRemainingLowerCase();
               for (String song : this.handlers.availableSongNames()) {
                  if (song.toLowerCase(Locale.ROOT).startsWith(remaining)) builder.suggest(song);
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
         String argumentName, int minimum, int maximum, BiFunction<Minecraft, Integer, Boolean> handler) {
      root.then(ClientCommands.literal(name)
            .then(ClientCommands.argument(argumentName, IntegerArgumentType.integer(minimum, maximum))
                  .executes(context -> result(handler.apply(client(context),
                        IntegerArgumentType.getInteger(context, argumentName))))));
   }

   private static <T> void addChoice(LiteralArgumentBuilder<FabricClientCommandSource> root, String name,
         List<Choice<T>> choices, BiFunction<Minecraft, T, Boolean> handler) {
      root.then(choiceNode(name, choices, handler));
   }

   private static <T> LiteralArgumentBuilder<FabricClientCommandSource> choiceNode(String name,
         List<Choice<T>> choices, BiFunction<Minecraft, T, Boolean> handler) {
      LiteralArgumentBuilder<FabricClientCommandSource> command = ClientCommands.literal(name);
      for (Choice<T> choice : choices) {
         command.then(ClientCommands.literal(choice.literal())
               .executes(context -> result(handler.apply(client(context), choice.value()))));
      }
      return command;
   }

   private static void addToggle(LiteralArgumentBuilder<FabricClientCommandSource> root, String name,
         BiFunction<Minecraft, Boolean, Boolean> handler) {
      root.then(toggleNode(name, handler));
   }

   private static LiteralArgumentBuilder<FabricClientCommandSource> toggleNode(String name,
         BiFunction<Minecraft, Boolean, Boolean> handler) {
      LiteralArgumentBuilder<FabricClientCommandSource> command = ClientCommands.literal(name);
      addToggleChildren(command, handler);
      return command;
   }

   private static void addToggleChildren(LiteralArgumentBuilder<FabricClientCommandSource> command,
         BiFunction<Minecraft, Boolean, Boolean> handler) {
      command.then(ClientCommands.literal("on")
            .executes(context -> result(handler.apply(client(context), true))));
      command.then(ClientCommands.literal("off")
            .executes(context -> result(handler.apply(client(context), false))));
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

   private static <T> List<Choice<T>> enumChoices(T[] values, Function<T, String> literal) {
      return Arrays.stream(values).map(value -> new Choice<>(literal.apply(value), value)).toList();
   }

   private record Choice<T>(String literal, T value) {}

   @FunctionalInterface
   private interface InstrumentMapHandler {
      boolean apply(Minecraft client, NoteBlockInstrument from, NoteBlockInstrument to);
   }
}
