package io.voltikor.lyra.noteblock;

import io.voltikor.lyra.song.Note;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

@Environment(EnvType.CLIENT)
public record NearbyNoteBlocksSnapshot(
      Map<Note, List<BlockPos>> tuningCandidates,
      int playableTotal,
      Map<NoteBlockInstrument, Integer> playableByInstrument) {

   private static final NearbyNoteBlocksSnapshot EMPTY =
         new NearbyNoteBlocksSnapshot(Map.of(), 0, Map.of());

   public NearbyNoteBlocksSnapshot {
      Map<Note, List<BlockPos>> candidates = new HashMap<>();
      if (tuningCandidates != null) {
         tuningCandidates.forEach((note, positions) -> candidates.put(note, List.copyOf(positions)));
      }
      tuningCandidates = Map.copyOf(candidates);
      playableTotal = Math.max(0, playableTotal);
      playableByInstrument = playableByInstrument == null ? Map.of() : Map.copyOf(playableByInstrument);
   }

   public static NearbyNoteBlocksSnapshot empty() {
      return EMPTY;
   }
}
