package io.voltikor.lyra.noteblock;

import io.voltikor.lyra.command.LyraMessenger;
import io.voltikor.lyra.config.LyraSettings;

import java.util.Iterator;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class NoteBlockTuner {
   private final NoteBlockTargetResolver targetResolver = new NoteBlockTargetResolver();
   private boolean anyNoteblockTuned;

   public boolean anyNoteblockTuned() {
      return this.anyNoteblockTuned;
   }

   public void setAnyNoteblockTuned(boolean value) {
      this.anyNoteblockTuned = value;
   }

   public void reset() {
      this.anyNoteblockTuned = false;
   }

   public TuneOutcome tune(Minecraft client, LyraSettings settings, NoteBlockScanner scanner,
         int ticks, Runnable resetTicks) {
      if (scanner.tuneHits().isEmpty()) {
         if (this.anyNoteblockTuned) {
            this.anyNoteblockTuned = false;
            LyraMessenger.replyFormatted(client, "Delaying re-check...");
            return TuneOutcome.RECHECK_REQUIRED;
         } else {
            LyraMessenger.replyFormatted(client, "Tuning complete.");
            return TuneOutcome.READY;
         }
      } else if (ticks >= settings.tickDelay()) {
         this.tuneBlocks(client, settings, scanner, () -> {
            LyraMessenger.error(client, "Stopped tuning because client/player/world was null");
         });
         resetTicks.run();
      }
      return TuneOutcome.IN_PROGRESS;
   }

   public enum TuneOutcome { IN_PROGRESS, RECHECK_REQUIRED, READY }

   public void tuneBlocks(Minecraft client, LyraSettings settings, NoteBlockScanner scanner, Runnable onNullStop) {
      if (client.player != null && client.level != null && client.gameMode != null) {
         if (settings.swingArm()) {
            client.player.swing(InteractionHand.MAIN_HAND);
         }

         int iteration = 0;
         int clickBudget = this.packetClickBudget(settings);
         Iterator<Map.Entry<BlockPos, Integer>> iterator = scanner.tuneHits().entrySet().iterator();

         while (iterator.hasNext()) {
            Map.Entry<BlockPos, Integer> entry = iterator.next();
            BlockPos pos = entry.getKey();
            int hitsLeft = entry.getValue();
            Vec3 optimalTarget = this.resolveTarget(client, pos, settings, scanner);
            if (settings.autoRotate()) {
               this.rotateTo(client, optimalTarget);
            }

            if (!this.tuneNoteblock(client, pos, optimalTarget, scanner)) {
               return;
            }

            --hitsLeft;
            if (hitsLeft <= 0) {
               iterator.remove();
            } else {
               entry.setValue(hitsLeft);
            }

            ++iteration;
            if (iteration >= clickBudget) {
               return;
            }
         }
      } else if (onNullStop != null) {
         onNullStop.run();
      }
   }

   public Vec3 resolveTarget(Minecraft client, BlockPos pos, LyraSettings settings, NoteBlockScanner scanner) {
      return this.targetResolver.resolveTarget(client, pos, settings, scanner);
   }

   public Vec3 getOptimalTarget(Minecraft client, BlockPos pos, NoteBlockScanner scanner) {
      return this.targetResolver.getOptimalTarget(client, pos, scanner);
   }

   public record FaceTarget(Vec3 point, Direction face) {
   }

   public FaceTarget getClosestFaceTarget(Minecraft client, BlockPos pos) {
      return this.targetResolver.getClosestFaceTarget(client, pos);
   }

   public boolean tuneNoteblock(Minecraft client, BlockPos pos, Vec3 optimalTarget, NoteBlockScanner scanner) {
      if (client.player != null && client.gameMode != null && client.level != null) {
         Direction face = scanner.noteBlockFaces().getOrDefault(pos, Direction.UP);
         BlockHitResult hitResult = new BlockHitResult(optimalTarget, face, pos, false);
         client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hitResult);
         this.anyNoteblockTuned = true;
         return true;
      } else {
         return false;
      }
   }

   public boolean triggerNoteBlock(Minecraft client, BlockPos pos, NoteBlockScanner scanner) {
      if (client.getConnection() == null) {
         return false;
      } else {
         Direction face = scanner.noteBlockFaces().getOrDefault(pos, Direction.UP);
         client.getConnection().send(
               new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, face));
         client.getConnection().send(
               new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, pos, face));
         return true;
      }
   }

   public int packetClickBudget(LyraSettings settings) {
      return settings.concurrentTuneBlocks() == 0 ? Integer.MAX_VALUE
            : Math.max(1, Math.min(20, settings.concurrentTuneBlocks()));
   }

   public void rotateTo(Minecraft client, Vec3 optimalTarget) {
      if (client.player != null) {
         double eyeY = client.player.getY() + client.player.getEyeHeight();
         double dx = optimalTarget.x - client.player.getX();
         double dy = optimalTarget.y - eyeY;
         double dz = optimalTarget.z - client.player.getZ();
         double horizontalDist = Math.sqrt(dx * dx + dz * dz);
         float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
         float pitch = (float) (-(Mth.atan2(dy, horizontalDist) * (180.0D / Math.PI)));
         client.player.setYRot(yaw);
         client.player.setXRot(pitch);
      }
   }

   public static float pitchForNoteLevel(int noteLevel) {
      return (float) Math.pow(2.0, (noteLevel - 12) / 12.0);
   }

   public static int calcHits(int from, int to) {
      return from > to ? 25 - from + to : to - from;
   }
}
