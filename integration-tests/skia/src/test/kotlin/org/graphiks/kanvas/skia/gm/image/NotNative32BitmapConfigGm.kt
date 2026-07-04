package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/all_bitmap_configs.cpp::DEF_SIMPLE_GM(not_native32_bitmap_config, ...)` (128 × 128).
 * Tests drawing a non-native (kBGRA_8888) bitmap.
 * Missing API: SkBitmap pixel manipulation in kanvas.
 * @see https://github.com/google/skia/blob/main/gm/all_bitmap_configs.cpp
 */
class NotNative32BitmapConfigGm : SkiaGm {
    override val name = "not_native32_bitmap_config"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 128
    override val height = 128
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        /* TODO: requires SkBitmap pixel manipulation */
    }
}
