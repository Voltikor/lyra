package io.voltikor.lyra.noteblock;

import io.voltikor.lyra.command.LyraMessenger;
import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.song.Note;
import io.voltikor.lyra.song.Song;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public final class NoteBlockScanner {
   private static final int PLAYABLE_SCAN_INTERVAL_TICKS = 10;

   private final Map<Note, BlockPos> noteBlockPositions = new HashMap<>();
   private final Map<BlockPos, Integer> tuneHits = new HashMap<>();
   private final Map<BlockPos, Direction> noteBlockFaces = new HashMap<>();
   private NearbyNoteBlocksSnapshot nearbySnapshot = NearbyNoteBlocksSnapshot.empty();
   private int playableScanTimer;

   public Map<Note, BlockPos> noteBlockPositions() {
      return this.noteBlockPositions;
   }

   public Map<BlockPos, Integer> tuneHits() {
      return this.tuneHits;
   }

   public Map<BlockPos, Direction> noteBlockFaces() {
      return this.noteBlockFaces;
   }

   public NearbyNoteBlocksSnapshot nearbySnapshot() {
      return this.nearbySnapshot;
   }

   public void reset() {
      this.noteBlockPositions.clear();
      this.tuneHits.clear();
      this.noteBlockFaces.clear();
      this.nearbySnapshot = NearbyNoteBlocksSnapshot.empty();
      this.playableScanTimer = 0;
   }

   public void scanNearbyNoteblocks(Minecraft client, LyraSettings settings) {
      this.playableScanTimer = 0;
      Map<Note, List<BlockPos>> candidates = new HashMap<>();
      Map<NoteBlockInstrument, Integer> instrumentCounts = new HashMap<>();
      int playableTotal = 0;
      if (client != null && client.player != null && client.level != null) {
         double reach = client.player.blockInteractionRange();
         double maxDistSq = (reach + 1.5D) * (reach + 1.5D);
         int min = (int) (-reach) - 2;
         int max = (int) reach + 2;
         BlockPos playerPos = client.player.blockPosition();
         BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

         for (int y = min; y < max; ++y) {
            int yOff = y + 1;
            int ySq = yOff * yOff;
            for (int x = min; x < max; ++x) {
               int xSq = x * x;
               for (int z = min; z < max; ++z) {
                  if ((double) (xSq + ySq + z * z) > maxDistSq) {
                     continue;
                  }
                  pos.set(playerPos.getX() + x, playerPos.getY() + yOff, playerPos.getZ() + z);
                  BlockState state = client.level.getBlockState(pos);
                  if (state.getBlock() != Blocks.NOTE_BLOCK
                        || !client.player.isWithinBlockInteractionRange(pos, 1.0F)
                        || !client.level.getBlockState(pos.above()).isAir()) {
                     continue;
                  }

                  NoteBlockInstrument instrument = settings.instrumentDetectMode().detect(state, pos, client.level);
                  int level = (Integer) state.getValue(NoteBlock.NOTE);
                  recordNearbyNoteblock(candidates, instrumentCounts, pos.immutable(), level, instrument, settings.mode());
                  ++playableTotal;
               }
            }
         }
      }
      this.nearbySnapshot = new NearbyNoteBlocksSnapshot(candidates, playableTotal, instrumentCounts);
   }

   void recordNearbyNoteblock(BlockPos pos, int level, NoteBlockInstrument instrument, InstrumentMatchMode mode) {
      Map<Note, List<BlockPos>> candidates = mutableCandidates(this.nearbySnapshot.tuningCandidates());
      Map<NoteBlockInstrument, Integer> instrumentCounts = new HashMap<>(this.nearbySnapshot.playableByInstrument());
      recordNearbyNoteblock(candidates, instrumentCounts, pos, level, instrument, mode);
      this.nearbySnapshot = new NearbyNoteBlocksSnapshot(candidates,
            this.nearbySnapshot.playableTotal() + 1, instrumentCounts);
   }

   private static void recordNearbyNoteblock(Map<Note, List<BlockPos>> candidates,
         Map<NoteBlockInstrument, Integer> instrumentCounts, BlockPos pos, int level,
         NoteBlockInstrument instrument, InstrumentMatchMode mode) {
      instrumentCounts.merge(instrument, 1, Integer::sum);
      Note note = new Note(mode == InstrumentMatchMode.EXACT_INSTRUMENTS ? instrument : null, level);
      candidates.computeIfAbsent(note, (ignored) -> new ArrayList<>()).add(pos);
   }

   private void clearNearbyScanResults() {
      this.nearbySnapshot = NearbyNoteBlocksSnapshot.empty();
   }

   private static Map<Note, List<BlockPos>> mutableCandidates(Map<Note, List<BlockPos>> source) {
      Map<Note, List<BlockPos>> result = new HashMap<>();
      source.forEach((note, positions) -> result.put(note, new ArrayList<>(positions)));
      return result;
   }

   public void setupNoteblocksMap(Minecraft client, Song song, LyraSettings settings) {
      this.noteBlockPositions.clear();
      if (song != null) {
         List<Note> uniqueNotesToUse = new ArrayList<>(song.requirements());
         Map<NoteBlockInstrument, List<BlockPos>> incorrectNoteBlocks = new HashMap<>();

         for (Map.Entry<Note, List<BlockPos>> entry : this.nearbySnapshot.tuningCandidates().entrySet()) {
            Note note = entry.getKey();
            List<BlockPos> noteblocks = new ArrayList<>(entry.getValue());
            if (uniqueNotesToUse.contains(note) && !noteblocks.isEmpty()) {
               this.noteBlockPositions.put(note, noteblocks.removeFirst());
               uniqueNotesToUse.remove(note);
            }

            if (!noteblocks.isEmpty()) {
               incorrectNoteBlocks.computeIfAbsent(note.instrument(), (ignored) -> new ArrayList<>())
                     .addAll(noteblocks);
            }
         }

         for (Map.Entry<NoteBlockInstrument, List<BlockPos>> entry : incorrectNoteBlocks.entrySet()) {
            List<BlockPos> positions = entry.getValue();
            if (settings.mode() == InstrumentMatchMode.EXACT_INSTRUMENTS) {
               NoteBlockInstrument instrument = entry.getKey();
               List<Note> foundNotes = uniqueNotesToUse.stream().filter((notex) -> notex.instrument() == instrument)
                     .toList();
               List<Note> mutableFoundNotes = new ArrayList<>(foundNotes);

               for (BlockPos pos : positions) {
                  if (mutableFoundNotes.isEmpty()) {
                     break;
                  }

                  Note note = mutableFoundNotes.removeFirst();
                  this.noteBlockPositions.put(note, pos);
                  uniqueNotesToUse.remove(note);
               }
            } else {
               for (BlockPos pos : positions) {
                  if (uniqueNotesToUse.isEmpty()) {
                     break;
                  }

                  Note note = uniqueNotesToUse.removeFirst();
                  this.noteBlockPositions.put(note, pos);
               }
            }
         }

         if (!uniqueNotesToUse.isEmpty()) {
            int printed = Math.min(5, uniqueNotesToUse.size());

            for (int i = 0; i < printed; ++i) {
               Note missing = uniqueNotesToUse.get(i);
               LyraMessenger.warningFormatted(client, "Missing note: instrument=%1$s level=%2$s",
                     LyraMessenger.instrumentLabel(missing.instrument()), missing.noteLevel());
            }

            LyraMessenger.warningFormatted(client, "%1$s notes are missing nearby noteblocks.", uniqueNotesToUse.size());
         }
      }
   }

   public void setupTuneHitsMap(Minecraft client) {
      if (client != null && client.level != null) {
         this.tuneHits.clear();

         for (Map.Entry<Note, BlockPos> entry : this.noteBlockPositions.entrySet()) {
            Note targetNote = entry.getKey();
            BlockPos blockPos = entry.getValue();
            BlockState blockState = client.level.getBlockState(blockPos);
            if (blockState.getBlock() == Blocks.NOTE_BLOCK) {
               int targetLevel = targetNote.noteLevel();
               int currentLevel = (Integer) blockState.getValue(NoteBlock.NOTE);
               if (targetLevel != currentLevel) {
                  this.tuneHits.put(blockPos, NoteBlockTuner.calcHits(currentLevel, targetLevel));
               }
            }
         }
      }
   }

   public void cacheNoteBlockFaces(Minecraft client) {
      if (client == null || client.player == null || client.level == null) {
         return;
      }
      this.noteBlockFaces.clear();
      double eyeX = client.player.getX();
      double eyeY = client.player.getY() + client.player.getEyeHeight();
      double eyeZ = client.player.getZ();
      double reach = client.player.blockInteractionRange();
      double reachSq = reach * reach;

      for (BlockPos pos : this.noteBlockPositions.values()) {
         if (this.noteBlockFaces.containsKey(pos)) {
            continue;
         }
         double bx = pos.getX();
         double by = pos.getY();
         double bz = pos.getZ();

         Direction bestFace = null;
         double bestDistSq = Double.MAX_VALUE;

         for (Direction face : Direction.values()) {
            if (!client.level.getBlockState(pos.relative(face)).isAir()) {
               continue;
            }

            boolean facing;
            switch (face) {
               case UP -> facing = eyeY > by + 1.0D;
               case DOWN -> facing = eyeY < by;
               case NORTH -> facing = eyeZ < bz;
               case SOUTH -> facing = eyeZ > bz + 1.0D;
               case WEST -> facing = eyeX < bx;
               case EAST -> facing = eyeX > bx + 1.0D;
               default -> facing = false;
            }
            if (!facing) {
               continue;
            }

            double cpx;
            double cpy;
            double cpz;
            switch (face) {
               case UP -> {
                  cpx = Math.max(bx, Math.min(bx + 1.0D, eyeX));
                  cpy = by + 1.0D;
                  cpz = Math.max(bz, Math.min(bz + 1.0D, eyeZ));
               }
               case DOWN -> {
                  cpx = Math.max(bx, Math.min(bx + 1.0D, eyeX));
                  cpy = by;
                  cpz = Math.max(bz, Math.min(bz + 1.0D, eyeZ));
               }
               case NORTH -> {
                  cpx = Math.max(bx, Math.min(bx + 1.0D, eyeX));
                  cpy = Math.max(by, Math.min(by + 1.0D, eyeY));
                  cpz = bz;
               }
               case SOUTH -> {
                  cpx = Math.max(bx, Math.min(bx + 1.0D, eyeX));
                  cpy = Math.max(by, Math.min(by + 1.0D, eyeY));
                  cpz = bz + 1.0D;
               }
               case WEST -> {
                  cpx = bx;
                  cpy = Math.max(by, Math.min(by + 1.0D, eyeY));
                  cpz = Math.max(bz, Math.min(bz + 1.0D, eyeZ));
               }
               case EAST -> {
                  cpx = bx + 1.0D;
                  cpy = Math.max(by, Math.min(by + 1.0D, eyeY));
                  cpz = Math.max(bz, Math.min(bz + 1.0D, eyeZ));
               }
               default -> {
                  continue;
               }
            }

            double distSq = (eyeX - cpx) * (eyeX - cpx) + (eyeY - cpy) * (eyeY - cpy) + (eyeZ - cpz) * (eyeZ - cpz);
            if (distSq <= reachSq && distSq < bestDistSq) {
               bestDistSq = distSq;
               bestFace = face;
            }
         }

         this.noteBlockFaces.put(pos, bestFace != null ? bestFace : Direction.UP);
      }
   }

   public void tickPlayableBlocksScan(Minecraft client, Song requirementsSong, LyraSettings settings) {
      this.tickPlayableBlocksScan(client, requirementsSong != null, settings);
   }

   public void tickPlayableBlocksScan(Minecraft client, boolean needed, LyraSettings settings) {
      if (needed && client != null && client.player != null && client.level != null) {
         ++this.playableScanTimer;
         if (this.playableScanTimer >= PLAYABLE_SCAN_INTERVAL_TICKS) {
            this.scanNearbyNoteblocks(client, settings);
         }
      } else {
         this.playableScanTimer = 0;
         this.clearNearbyScanResults();
      }
   }
}
