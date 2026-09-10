package io.voltikor.lyra.noteblock;

import io.voltikor.lyra.config.LyraSettings;
import io.voltikor.lyra.noteblock.NoteBlockTuner.FaceTarget;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
final class NoteBlockTargetResolver {
   Vec3 resolveTarget(Minecraft client, BlockPos pos, LyraSettings settings, NoteBlockScanner scanner) {
      if (settings.rotateMode() == RotateMode.CLOSEST_FACE) {
         FaceTarget ft = this.getClosestFaceTarget(client, pos);
         scanner.noteBlockFaces().put(pos, ft.face());
         return ft.point();
      }
      return this.getOptimalTarget(client, pos, scanner);
   }

   Vec3 getOptimalTarget(Minecraft client, BlockPos pos, NoteBlockScanner scanner) {
      return optimalTarget(pos, scanner.noteBlockFaces().getOrDefault(pos, Direction.UP),
            client.player.getX(), client.player.getY() + client.player.getEyeHeight(),
            client.player.getZ(), client.player.blockInteractionRange());
   }

   Vec3 optimalTarget(BlockPos pos, Direction face, double eyeX, double eyeY, double eyeZ, double reach) {

      double bx = pos.getX();
      double by = pos.getY();
      double bz = pos.getZ();

      double fixedVal;
      double freeMin1, freeMax1, freeMin2, freeMax2;
      double eyeFree1, eyeFree2;
      int fixedAxis;

      switch (face) {
         case UP -> {
            fixedAxis = 1;
            fixedVal = by + 1.0D;
            freeMin1 = bx;
            freeMax1 = bx + 1.0D;
            eyeFree1 = eyeX;
            freeMin2 = bz;
            freeMax2 = bz + 1.0D;
            eyeFree2 = eyeZ;
         }
         case DOWN -> {
            fixedAxis = 1;
            fixedVal = by;
            freeMin1 = bx;
            freeMax1 = bx + 1.0D;
            eyeFree1 = eyeX;
            freeMin2 = bz;
            freeMax2 = bz + 1.0D;
            eyeFree2 = eyeZ;
         }
         case NORTH -> {
            fixedAxis = 2;
            fixedVal = bz;
            freeMin1 = bx;
            freeMax1 = bx + 1.0D;
            eyeFree1 = eyeX;
            freeMin2 = by;
            freeMax2 = by + 1.0D;
            eyeFree2 = eyeY;
         }
         case SOUTH -> {
            fixedAxis = 2;
            fixedVal = bz + 1.0D;
            freeMin1 = bx;
            freeMax1 = bx + 1.0D;
            eyeFree1 = eyeX;
            freeMin2 = by;
            freeMax2 = by + 1.0D;
            eyeFree2 = eyeY;
         }
         case WEST -> {
            fixedAxis = 0;
            fixedVal = bx;
            freeMin1 = by;
            freeMax1 = by + 1.0D;
            eyeFree1 = eyeY;
            freeMin2 = bz;
            freeMax2 = bz + 1.0D;
            eyeFree2 = eyeZ;
         }
         case EAST -> {
            fixedAxis = 0;
            fixedVal = bx + 1.0D;
            freeMin1 = by;
            freeMax1 = by + 1.0D;
            eyeFree1 = eyeY;
            freeMin2 = bz;
            freeMax2 = bz + 1.0D;
            eyeFree2 = eyeZ;
         }
         default -> {
            return new Vec3(bx + 0.5D, by + 1.0D, bz + 0.5D);
         }
      }

      double closest1 = Math.max(freeMin1, Math.min(freeMax1, eyeFree1));
      double closest2 = Math.max(freeMin2, Math.min(freeMax2, eyeFree2));
      double center1 = (freeMin1 + freeMax1) / 2.0D;
      double center2 = (freeMin2 + freeMax2) / 2.0D;

      double eyeFixed;
      if (fixedAxis == 0) {
         eyeFixed = eyeX;
      } else if (fixedAxis == 1) {
         eyeFixed = eyeY;
      } else {
         eyeFixed = eyeZ;
      }

      double safeReachSq = (reach - 0.05D) * (reach - 0.05D);

      double dFixed = eyeFixed - fixedVal;
      double dCenter1 = eyeFree1 - center1;
      double dCenter2 = eyeFree2 - center2;
      double distCenterSq = dFixed * dFixed + dCenter1 * dCenter1 + dCenter2 * dCenter2;

      double resultFree1;
      double resultFree2;

      if (distCenterSq <= safeReachSq) {
         resultFree1 = center1;
         resultFree2 = center2;
      } else {
         double low = 0.0D;
         double high = 1.0D;
         double bestT = 0.0D;

         for (int i = 0; i < 10; ++i) {
            double mid = (low + high) / 2.0D;
            double mid1 = closest1 + (center1 - closest1) * mid;
            double mid2 = closest2 + (center2 - closest2) * mid;
            double d1 = eyeFree1 - mid1;
            double d2 = eyeFree2 - mid2;
            double distSq = dFixed * dFixed + d1 * d1 + d2 * d2;
            if (distSq <= safeReachSq) {
               bestT = mid;
               low = mid;
            } else {
               high = mid;
            }
         }

         resultFree1 = closest1 + (center1 - closest1) * bestT;
         resultFree2 = closest2 + (center2 - closest2) * bestT;
      }

      switch (fixedAxis) {
         case 0 -> {
            return new Vec3(fixedVal, resultFree1, resultFree2);
         }
         case 1 -> {
            return new Vec3(resultFree1, fixedVal, resultFree2);
         }
         case 2 -> {
            return new Vec3(resultFree1, resultFree2, fixedVal);
         }
         default -> {
            return new Vec3(bx + 0.5D, by + 1.0D, bz + 0.5D);
         }
      }
   }

   FaceTarget getClosestFaceTarget(Minecraft client, BlockPos pos) {
      return closestFaceTarget(pos, client.player.getX(),
            client.player.getY() + client.player.getEyeHeight(), client.player.getZ(),
            client.player.getY(), client.player.blockInteractionRange());
   }

   FaceTarget closestFaceTarget(BlockPos pos, double eyeX, double eyeY, double eyeZ,
         double playerY, double reach) {

      double bx = pos.getX();
      double by = pos.getY();
      double bz = pos.getZ();

      if (playerY - by >= 2.0D) {
         return new FaceTarget(new Vec3(bx + 0.5D, by + 1.0D, bz + 0.5D), Direction.UP);
      }

      Direction bestFace = Direction.UP;
      double bestDistSq = Double.MAX_VALUE;

      for (Direction face : Direction.values()) {
         double cpx, cpy, cpz;
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
         if (distSq < bestDistSq) {
            bestDistSq = distSq;
            bestFace = face;
         }
      }

      return new FaceTarget(this.optimalTarget(pos, bestFace, eyeX, eyeY, eyeZ, reach), bestFace);
   }

}
