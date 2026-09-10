package io.voltikor.lyra.song;

import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum OutOfRangeMode {
    DROP,
    CLAMP,
    FOLD,
    REMAP;

    public static OutOfRangeMode fromInput(String input) {
        if (input == null) {
            return null;
        }
        switch (input.trim().toLowerCase(Locale.ROOT)) {
            case "drop":
            case "off":
                return DROP;
            case "clamp":
            case "on":
                return CLAMP;
            case "fold":
                return FOLD;
            case "remap":
                return REMAP;
            default:
                return null;
        }
    }
}
