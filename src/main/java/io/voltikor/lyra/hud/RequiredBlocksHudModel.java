package io.voltikor.lyra.hud;

import io.voltikor.lyra.command.LyraMessenger;
import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.song.Note;
import io.voltikor.lyra.gui.DeskTheme;
import io.voltikor.lyra.song.Song;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

@Environment(EnvType.CLIENT)
public final class RequiredBlocksHudModel {
   public List<RequiredHudRow> buildRequiredBlocksRows(Song requirementsSong, int playableBlocksTotal,
         Map<NoteBlockInstrument, Integer> playableBlocksByInstrument, LyraSettings settings) {
      Map<NoteBlockInstrument, Integer> requiredByInstrument = this.requiredBlocksByInstrument(requirementsSong);
      if (requiredByInstrument.isEmpty()) {
         int requiredTotal = requirementsSong != null ? requirementsSong.requirements().size() : 0;
         if (requiredTotal > 0) {
            int available = Math.max(0, playableBlocksTotal);
            int missing = Math.max(0, requiredTotal - available);
            return List.of(
                  new RequiredHudRow(LyraMessenger.literalComponent("any"), Blocks.NOTE_BLOCK,
                        null, available, requiredTotal, missing, requiredProgressColor(available, requiredTotal)));
         } else if (!playableBlocksByInstrument.isEmpty()) {
            List<RequiredHudRow> rows = new ArrayList<>();
            List<Map.Entry<NoteBlockInstrument, Integer>> sorted = new ArrayList<>(
                  playableBlocksByInstrument.entrySet());
            sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            for (Map.Entry<NoteBlockInstrument, Integer> entry : sorted) {
               int available = entry.getValue();
               Block replaced = this.resolveReplacedIcon(entry.getKey(), settings, requirementsSong);
               rows.add(new RequiredHudRow(LyraMessenger.instrumentLabel(entry.getKey()),
                     io.voltikor.lyra.noteblock.InstrumentIcons.blockFor(entry.getKey()), replaced, available, 0, 0, DeskTheme.STATUS_READY));
            }
            return rows;
         } else {
            return List.of();
         }
      } else {
         List<Map.Entry<NoteBlockInstrument, Integer>> entries = new ArrayList<>(requiredByInstrument.entrySet());
         entries.sort((a, b) -> {
            int diffA = Math.max(0, a.getValue() - playableBlocksByInstrument.getOrDefault(a.getKey(), 0));
            int diffB = Math.max(0, b.getValue() - playableBlocksByInstrument.getOrDefault(b.getKey(), 0));
            if (diffA != diffB)
               return Integer.compare(diffB, diffA);

            int nameCmp = LyraSettings.prettyName(a.getKey()).compareTo(LyraSettings.prettyName(b.getKey()));
            if (nameCmp != 0)
               return nameCmp;

            return a.getKey().name().compareTo(b.getKey().name());
         });
         List<RequiredHudRow> rows = new ArrayList<>();

         for (Map.Entry<NoteBlockInstrument, Integer> entry : entries) {
            int required = entry.getValue();
            int available = playableBlocksByInstrument.getOrDefault(entry.getKey(), 0);
            int missing = Math.max(0, required - available);
            Block replaced = this.resolveReplacedIcon(entry.getKey(), settings, requirementsSong);
            rows.add(new RequiredHudRow(LyraMessenger.instrumentLabel(entry.getKey()),
                  io.voltikor.lyra.noteblock.InstrumentIcons.blockFor(entry.getKey()), replaced, available, required, missing,
                  requiredProgressColor(available, required)));
         }

         return rows;
      }
   }

   private Block resolveReplacedIcon(NoteBlockInstrument targetInstrument, LyraSettings settings,
         Song requirementsSong) {
      if (settings != null && targetInstrument != null && requirementsSong != null) {
         for (NoteBlockInstrument source : settings.mappedSources()) {
            if (source != targetInstrument && settings.mapInstrument(source) == targetInstrument) {
               if (requirementsSong.isSourceRemappedToTarget(source, targetInstrument)) {
                  return io.voltikor.lyra.noteblock.InstrumentIcons.blockFor(source);
               }
            }
         }
      }
      return null;
   }

   Map<NoteBlockInstrument, Integer> requiredBlocksByInstrument(Song requirementsSong) {
      Map<NoteBlockInstrument, Integer> counts = new HashMap<>();
      if (requirementsSong == null) {
         return counts;
      }

      for (Note note : requirementsSong.requirements()) {
         NoteBlockInstrument instrument = note.instrument();
         if (instrument != null) {
            counts.merge(instrument, 1, Integer::sum);
         }
      }

      return counts;
   }

   private static int requiredProgressColor(int available, int required) {
      if (required > 0 && available < required) {
         return DeskTheme.STATUS_MISSING;
      } else {
         return DeskTheme.STATUS_READY;
      }
   }

   public record RequiredHudRow(Component instrumentName, Block icon, Block replacedIcon, int available,
         int required, int missing, int color) {
   }
}
