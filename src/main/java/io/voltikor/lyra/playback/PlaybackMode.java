package io.voltikor.lyra.playback;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum PlaybackMode {
   NONE,
   PREVIEW,
   NOTEBLOCKS;
}
