package io.voltikor.lyra.command;

import io.voltikor.lyra.MinecraftTestSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LyraMessengerTest extends MinecraftTestSupport {

   private record VisitedSegment(String content, Integer colorRgb) {}

   private List<VisitedSegment> visitSegments(Component component) {
      List<VisitedSegment> segments = new ArrayList<>();
      component.visit((style, str) -> {
         Integer rgb = style.getColor() != null ? style.getColor().getValue() : null;
         segments.add(new VisitedSegment(str, rgb));
         return Optional.empty();
      }, Style.EMPTY);
      return segments;
   }

   @Test
   void testLyraTagColors() {
      MutableComponent tag = LyraMessenger.tag();
      List<VisitedSegment> segments = visitSegments(tag);

      Assertions.assertEquals(3, segments.size());

      Assertions.assertEquals("[", segments.get(0).content());
      Assertions.assertEquals(ChatFormatting.GRAY.getColor(), segments.get(0).colorRgb());

      Assertions.assertEquals("Lyra", segments.get(1).content());
      Assertions.assertEquals(LyraMessenger.LIGHT_ORANGE.getValue(), segments.get(1).colorRgb());

      Assertions.assertEquals("]", segments.get(2).content());
      Assertions.assertEquals(ChatFormatting.GRAY.getColor(), segments.get(2).colorRgb());

      Assertions.assertEquals("[Lyra]", tag.getString());
   }

   @Test
   void testFormatReplyChatOutputIsGray() {
      Component msg = Component.literal("Song loaded successfully.");
      MutableComponent formatted = LyraMessenger.formatReply(msg);

      Assertions.assertEquals("[Lyra] Song loaded successfully.", formatted.getString());

      List<VisitedSegment> segments = visitSegments(formatted);
      Assertions.assertEquals(5, segments.size());

      Assertions.assertEquals("[", segments.get(0).content());
      Assertions.assertEquals(ChatFormatting.GRAY.getColor(), segments.get(0).colorRgb());

      Assertions.assertEquals("Lyra", segments.get(1).content());
      Assertions.assertEquals(LyraMessenger.LIGHT_ORANGE.getValue(), segments.get(1).colorRgb());

      Assertions.assertEquals("]", segments.get(2).content());
      Assertions.assertEquals(ChatFormatting.GRAY.getColor(), segments.get(2).colorRgb());

      Assertions.assertEquals(" ", segments.get(3).content());
      Assertions.assertEquals(ChatFormatting.GRAY.getColor(), segments.get(3).colorRgb());

      Assertions.assertEquals("Song loaded successfully.", segments.get(4).content());
      Assertions.assertEquals(ChatFormatting.GRAY.getColor(), segments.get(4).colorRgb());
   }

   @Test
   void testFormatWarning() {
      Component msg = Component.literal("Song is fast.");
      MutableComponent formatted = LyraMessenger.formatWarning(msg);

      Assertions.assertEquals("[Lyra][WARN] Song is fast.", formatted.getString());

      List<VisitedSegment> segments = visitSegments(formatted);
      Assertions.assertEquals(7, segments.size());

      Assertions.assertEquals("[", segments.get(0).content());
      Assertions.assertEquals(ChatFormatting.GRAY.getColor(), segments.get(0).colorRgb());
      Assertions.assertEquals("Lyra", segments.get(1).content());
      Assertions.assertEquals(LyraMessenger.LIGHT_ORANGE.getValue(), segments.get(1).colorRgb());
      Assertions.assertEquals("]", segments.get(2).content());
      Assertions.assertEquals(ChatFormatting.GRAY.getColor(), segments.get(2).colorRgb());

      Assertions.assertEquals("[", segments.get(3).content());
      Assertions.assertEquals(ChatFormatting.GRAY.getColor(), segments.get(3).colorRgb());
      Assertions.assertEquals("WARN", segments.get(4).content());
      Assertions.assertEquals(ChatFormatting.YELLOW.getColor(), segments.get(4).colorRgb());
      Assertions.assertEquals("] ", segments.get(5).content());
      Assertions.assertEquals(ChatFormatting.GRAY.getColor(), segments.get(5).colorRgb());

      Assertions.assertEquals("Song is fast.", segments.get(6).content());
      Assertions.assertEquals(ChatFormatting.GRAY.getColor(), segments.get(6).colorRgb());
   }

   @Test
   void testFormatInfo() {
      Component msg = Component.literal("Transpose is disabled.");
      MutableComponent formatted = LyraMessenger.formatInfo(msg);

      Assertions.assertEquals("[Lyra][INFO] Transpose is disabled.", formatted.getString());

      List<VisitedSegment> segments = visitSegments(formatted);
      Assertions.assertEquals("INFO", segments.get(4).content());
      Assertions.assertEquals(ChatFormatting.AQUA.getColor(), segments.get(4).colorRgb());
   }

   @Test
   void testFormatError() {
      Component msg = Component.literal("Song not found.");
      MutableComponent formatted = LyraMessenger.formatError(msg);

      Assertions.assertEquals("[Lyra][ERROR] Song not found.", formatted.getString());

      List<VisitedSegment> segments = visitSegments(formatted);
      Assertions.assertEquals(7, segments.size());

      Assertions.assertEquals("[", segments.get(0).content());
      Assertions.assertEquals(ChatFormatting.GRAY.getColor(), segments.get(0).colorRgb());
      Assertions.assertEquals("Lyra", segments.get(1).content());
      Assertions.assertEquals(LyraMessenger.LIGHT_ORANGE.getValue(), segments.get(1).colorRgb());
      Assertions.assertEquals("]", segments.get(2).content());
      Assertions.assertEquals(ChatFormatting.GRAY.getColor(), segments.get(2).colorRgb());

      Assertions.assertEquals("[", segments.get(3).content());
      Assertions.assertEquals(ChatFormatting.GRAY.getColor(), segments.get(3).colorRgb());
      Assertions.assertEquals("ERROR", segments.get(4).content());
      Assertions.assertEquals(ChatFormatting.RED.getColor(), segments.get(4).colorRgb());
      Assertions.assertEquals("] ", segments.get(5).content());
      Assertions.assertEquals(ChatFormatting.GRAY.getColor(), segments.get(5).colorRgb());

      Assertions.assertEquals("Song not found.", segments.get(6).content());
      Assertions.assertEquals(ChatFormatting.GRAY.getColor(), segments.get(6).colorRgb());
   }
}
