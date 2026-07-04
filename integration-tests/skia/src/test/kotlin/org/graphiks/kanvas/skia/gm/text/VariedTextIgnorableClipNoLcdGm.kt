package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Stub — Skia's `gm/varied_text_ignorable_clip_no_lcd.cpp` (640 × 480).
 * Missing API: ignorable clip interaction with text.
 * @see https://github.com/google/skia/blob/main/gm/varied_text_ignorable_clip_no_lcd.cpp
 */
class VariedTextIgnorableClipNoLcdGm : SkiaGm {
    override val name = "varied_text_ignorable_clip_no_lcd"
    override val renderFamily = RenderFamily.TEXT
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 480
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        /* TODO: requires ignorable clip support */
    }
}
