package org.skia.tests

import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkPaint

/**
 * Port of Skia's `gm/color4f.cpp::DEF_SIMPLE_GM(color4blendcf, …)`
 * (360 × 480).
 *
 * Draws a 3-column × 4-row grid of 100×100 rectangles. The paint colour
 * is fixed to [SK_ColorWHITE]; each cell applies a colour-filter built
 * with [SkColorFilters.Blend] using [SkBlendMode.kModulate] so the final
 * drawn colour is the colour-space-managed [SkColor4f] in column:
 *  - column 0 : `null` colour space
 *  - column 1 : sRGB ([SkColorSpace.MakeSRGB])
 *  - column 2 : "spin" gamut (`sRGB.makeColorSpin()`) — RGB → GBR rotation
 *
 * Upstream comment: "Use kModulate and a paint color of white so the final
 * drawn color is color-space managed 'c4'."
 *
 * **STUB.COLOR4F_BLEND_CF** — [SkColorFilters.Blend] with an
 * [SkColor4f] + [SkColorSpace] argument is not yet implemented; the very
 * first call in [onDraw] throws [NotImplementedError].  The matching
 * [Color4blendcfTest] is `@Disabled("STUB.COLOR4F_BLEND_CF")` until the
 * colour-space–aware blend filter lands.
 */
public class Color4blendcfGM : GM() {

    override fun getName(): String = "color4blendcf"
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
        paint.color = SK_ColorWHITE
        val r = SkRect.MakeWH(100f, 100f)

        for (c4 in colors) {
            // Build the three filters — the first call throws STUB.COLOR4F_BLEND_CF.
            val filters = arrayOf(
                SkColorFilters.Blend(c4, null, SkBlendMode.kModulate),
                SkColorFilters.Blend(c4, srgb, SkBlendMode.kModulate),
                SkColorFilters.Blend(c4, spin, SkBlendMode.kModulate),
            )

            c.save()
            for (f in filters) {
                paint.colorFilter = f
                c.drawRect(r, paint)
                c.translate(r.width() * 6f / 5f, 0f)
            }
            c.restore()
            c.translate(0f, r.height() * 6f / 5f)
        }
    }
}
