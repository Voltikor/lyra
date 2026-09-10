package io.voltikor.lyra.config;

import java.util.Locale;

public enum TransposeSetting {
   OFF(0), AUTO(0), SMART(0),
   DOWN_24(-24), DOWN_18(-18), DOWN_12(-12), DOWN_6(-6),
   UP_6(6), UP_12(12), UP_18(18), UP_24(24);

   private final int semitones;

   TransposeSetting(int semitones) { this.semitones = semitones; }

   public int semitones() { return this.semitones; }

   public static java.util.List<String> options() {
      return java.util.Arrays.stream(values()).map(TransposeSetting::serialized).toList();
   }

   public static String usage() { return String.join("|", options()); }

   public static TransposeSetting parse(String input) {
      if (input == null) return null;
      String value = input.trim().toLowerCase(Locale.ROOT);
      return switch (value) {
         case "off", "none" -> OFF;
         case "auto", "on" -> AUTO;
         case "smart" -> SMART;
         case "-24" -> DOWN_24;
         case "-18" -> DOWN_18;
         case "-12" -> DOWN_12;
         case "-6" -> DOWN_6;
         case "6", "+6" -> UP_6;
         case "12", "+12" -> UP_12;
         case "18", "+18" -> UP_18;
         case "24", "+24" -> UP_24;
         default -> null;
      };
   }

   public boolean enabled() { return this != OFF; }

   public String serialized() {
      return this.semitones == 0 ? this.name().toLowerCase(Locale.ROOT)
            : this.semitones > 0 ? "+" + this.semitones : Integer.toString(this.semitones);
   }

   @Override
   public String toString() { return this.serialized(); }
}
