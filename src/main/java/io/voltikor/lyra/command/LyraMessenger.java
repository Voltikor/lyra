package io.voltikor.lyra.command;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class LyraMessenger {
   private static final Logger LOGGER = LoggerFactory.getLogger("Lyra");
   public static final TextColor LIGHT_ORANGE = TextColor.fromRgb(0xFFAA33);

   private LyraMessenger() {
   }

   public static MutableComponent tag() {
      return Component.empty()
            .append(Component.literal("[").withStyle(ChatFormatting.GRAY))
            .append(Component.literal("Lyra").withStyle(Style.EMPTY.withColor(LIGHT_ORANGE)))
            .append(Component.literal("]").withStyle(ChatFormatting.GRAY));
   }

   public static MutableComponent formatReply(Component message) {
      return Component.empty()
            .append(tag())
            .append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
            .append(Component.empty().withStyle(ChatFormatting.GRAY).append(message));
   }

   public static MutableComponent formatWarning(Component message) {
      return Component.empty()
            .append(tag())
            .append(Component.literal("[").withStyle(ChatFormatting.GRAY))
            .append(Component.literal("WARN").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal("] ").withStyle(ChatFormatting.GRAY))
            .append(Component.empty().withStyle(ChatFormatting.GRAY).append(message));
   }

   public static MutableComponent formatInfo(Component message) {
      return Component.empty()
            .append(tag())
            .append(Component.literal("[").withStyle(ChatFormatting.GRAY))
            .append(Component.literal("INFO").withStyle(ChatFormatting.AQUA))
            .append(Component.literal("] ").withStyle(ChatFormatting.GRAY))
            .append(Component.empty().withStyle(ChatFormatting.GRAY).append(message));
   }

   public static MutableComponent formatError(Component message) {
      return Component.empty()
            .append(tag())
            .append(Component.literal("[").withStyle(ChatFormatting.GRAY))
            .append(Component.literal("ERROR").withStyle(ChatFormatting.RED))
            .append(Component.literal("] ").withStyle(ChatFormatting.GRAY))
            .append(Component.empty().withStyle(ChatFormatting.GRAY).append(message));
   }

   public static Component literalComponent(String template, Object... args) {
      if (args.length == 0) {
         return Component.literal(template);
      }
      Object[] printableArgs = Arrays.stream(args)
            .map(arg -> arg instanceof Component component ? component.getString() : arg)
            .toArray();
      return Component.literal(String.format(Locale.ROOT, template, printableArgs));
   }

   public static void replyFormatted(Minecraft client, String template, Object... args) {
      reply(client, literalComponent(template, args));
   }

   public static void warningFormatted(Minecraft client, String template, Object... args) {
      warning(client, literalComponent(template, args));
   }

   public static void infoFormatted(Minecraft client, String template, Object... args) {
      info(client, literalComponent(template, args));
   }

   public static void errorFormatted(Minecraft client, String template, Object... args) {
      error(client, literalComponent(template, args));
   }

   public static void reply(Minecraft client, String message) {
      reply(client, Component.literal(message));
   }

   public static void reply(Minecraft client, Component message) {
      if (client != null && client.player != null) {
         sendPlayerMessage(client.player, formatReply(message), false);
      } else {
         LOGGER.info("[Lyra] {}", message.getString());
      }
   }

   public static void warning(Minecraft client, String message) {
      warning(client, Component.literal(message));
   }

   public static void warning(Minecraft client, Component message) {
      if (client != null && client.player != null) {
         sendPlayerMessage(client.player, formatWarning(message), false);
      } else {
         LOGGER.warn("[Lyra] {}", message.getString());
      }
   }

   public static void info(Minecraft client, String message) {
      info(client, Component.literal(message));
   }

   public static void info(Minecraft client, Component message) {
      if (client != null && client.player != null) {
         sendPlayerMessage(client.player, formatInfo(message), false);
      } else {
         LOGGER.info("[Lyra] {}", message.getString());
      }
   }

   public static void error(Minecraft client, String message) {
      error(client, Component.literal(message));
   }

   public static void error(Minecraft client, Component message) {
      if (client != null && client.player != null) {
         sendPlayerMessage(client.player, formatError(message), false);
      } else {
         LOGGER.error("[Lyra] {}", message.getString());
      }
   }

   public static void sendPlayerMessage(Object player, Component message, boolean overlay) {
      // Keep the legacy LocalPlayer path intact while supporting the 26.1 message API.
      if (invokePlayerMessage(player, "displayClientMessage", new Class<?>[]{Component.class, Boolean.TYPE}, message, overlay)) {
         return;
      }
      String modernMethod = overlay ? "sendOverlayMessage" : "sendSystemMessage";
      invokePlayerMessage(player, modernMethod, new Class<?>[]{Component.class}, message);
   }

   public static boolean invokePlayerMessage(Object player, String methodName, Class<?>[] parameterTypes, Object... args) {
      try {
         Method method = player.getClass().getMethod(methodName, parameterTypes);
         method.invoke(player, args);
         return true;
      } catch (ReflectiveOperationException ignored) {
         return false;
      }
   }

   public static Component instrumentLabel(NoteBlockInstrument instrument) {
      return Component.literal(instrument == null ? "any" : instrument.name().toLowerCase(Locale.ROOT));
   }
}
