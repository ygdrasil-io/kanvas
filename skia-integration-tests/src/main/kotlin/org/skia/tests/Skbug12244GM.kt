package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/strokes.cpp` `DEF_SIMPLE_GM(skbug12244, …)`.
 *
 * Hand-baked stroked-triangle outline (the result of running the
 * `SkStroker` on a `(0,0) → (100,40) → (0,80) → (0,0)` triangle with
 * `strokeWidth = 15`), drawn here as a *plain green fill* to highlight
 * that the regression — [skbug.com/12244](https://bugs.skia.org/12244)
 * — was in the GPU triangulating path renderer, **not** in the stroker.
 *
 * Two contours, multi-edge, AA fill. Reference image:
 * `skbug12244.png`, 150 × 150, default white BG.
 */
public class Skbug12244GM : GM() {

    override fun getName(): String = "skbug12244"
    override fun getISize(): SkISize = SkISize.Make(150, 150)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val path = SkPathBuilder()
            .moveTo(2.7854299545288085938f, -6.9635753631591796875f)
            .lineTo(120.194366455078125f, 40f)
            .lineTo(-7.5000004768371582031f, 91.07775115966796875f)
            .lineTo(-7.5000004768371582031f, -11.077748298645019531f)
            .lineTo(2.7854299545288085938f, -6.9635753631591796875f)
            .moveTo(-2.7854299545288085938f, 6.9635753631591796875f)
            .lineTo(0f, 0f)
            .lineTo(7.5f, 0f)
            .lineTo(7.5000004768371582031f, 68.92224884033203125f)
            .lineTo(79.805633544921875f, 40f)
            .lineTo(-2.7854299545288085938f, 6.9635753631591796875f)
            .detach()

        val p = SkPaint().apply { color = 0xFF00FF00.toInt() }    // SK_ColorGREEN
        c.translate(20f, 20f)
        c.drawPath(path, p)
    }
}
