package org.skia.tests

import org.graphiks.math.SkColor4f
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkPaint
import org.skia.foundation.SkShaders

/**
 * Port of Skia's `gm/color4f.cpp::DEF_SIMPLE_GM(color4shader, …)`
 * (360 × 480).
 *
 * Draws a 3-column × 4-row grid of 100×100 rectangles, where each row
 * uses one of four [SkColor4f] values (red, green, blue, grey) and each
 * column varies the colour space passed to [SkShaders.Color]:
 *  - column 0 : `null` (colours interpreted as sRGB)
 *  - column 1 : explicit sRGB ([SkColorSpace.MakeSRGB])
 *  - column 2 : "spin" gamut (`sRGB.makeColorSpin()`) — RGB → GBR rotation
 *
 * Upstream uses `SkShaders::Color(c4, cs)` directly;
 * `:kanvas-skia` exposes this as [SkShaders.Color] with the same
 * `(SkColor4f, SkColorSpace?)` signature, which eagerly converts to
 * sRGB bytes and delegates to the byte-colour shader pipeline.
 *
 * All three colour spaces work without a stub — this GM is active.
 */
public class Color4shaderGM : GM() {

    override fun getName(): String = "color4shader"
    override fun getISize(): SkISize = SkISize.Make(360, 480)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(10f, 10f)

        val srgb = SkColorSpace.MakeSRGB()
        val spin = srgb.makeColorSpin() // RGB -> GBR

        val colors = arrayOf(
            SkColor4f(1f, 0f, 0f, 1f),
            SkColor4f(0f, 1f, 0f, 1f),
            SkColor4f(0f, 0f, 1f, 1f),
            SkColor4f(0.5f, 0.5f, 0.5f, 1f),
        )

        val paint = SkPaint()
        val r = SkRect.MakeWH(100f, 100f)

        for (c4 in colors) {
            val shaders = arrayOf(
                SkShaders.Color(c4, null),
                SkShaders.Color(c4, srgb),
                SkShaders.Color(c4, spin),
            )

            c.save()
            for (s in shaders) {
                paint.shader = s
                c.drawRect(r, paint)
                c.translate(r.width() * 6f / 5f, 0f)
            }
            c.restore()
            c.translate(0f, r.height() * 6f / 5f)
        }
    }
}
