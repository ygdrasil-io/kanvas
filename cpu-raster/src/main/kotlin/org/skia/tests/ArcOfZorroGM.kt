package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.SkRandom

/**
 * Port of Skia's `gm/arcofzorro.cpp` (`ArcOfZorroGM`).
 *
 * Two hundred consecutive open-arc strokes (`useCenter=false`,
 * `strokeWidth=35`, default `kButt_Cap` + `kMiter_Join`) of slowly
 * increasing sweep angle (134° → 136° in 0.01° steps), laid out by a
 * boustrophedon-style direction switch ("→ then ↙ then →"). Random
 * opaque colour per arc from a default-seeded [SkRandom] (bit-compatible
 * with upstream).
 *
 * Hits every Phase 3 piece end-to-end:
 *  - `SkCanvas.drawArc(useCenter=false)` → builds an open path via
 *    [SkPathBuilder.arcTo] + cubic-Bézier flattening.
 *  - The path stroker (`Phase 3c`) converts the stroked open arc into a
 *    filled outline path with `kButt_Cap` ends and `kMiter_Join`
 *    bends — many overlapping strokes per cell exercise the AA edge
 *    arithmetic heavily.
 */
public class ArcOfZorroGM : GM() {
    // Skia sets `setBGColor(0xFFCCCCCC)` here. We instead seed the bitmap to
    // SK_ColorWHITE (a profile-invariant pre-fill) and repaint the grey via
    // [SkCanvas.drawPaint] in [onDraw], which routes through the device's
    // sRGB → Rec.2020 transform. `bitmap.eraseColor` skips that transform
    // (TestUtils calls it raw), so non-trivial bg colours need this dance —
    // see the comment in `TestUtils.runGmTest`.

    override fun getName(): String = "arcofzorro"
    override fun getISize(): SkISize = SkISize.Make(1000, 1000)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.drawPaint(SkPaint().apply { color = SkColorSetARGB(0xFF, 0xCC, 0xCC, 0xCC) })
        val rand = SkRandom()
        val rect = SkRect.MakeXYWH(10f, 10f, 200f, 200f)
        val p = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 35f
        }
        var xOffset = 0
        var yOffset = 0
        var direction = 0
        var arc = 134.0f
        while (arc < 136.0f) {
            val color = rand.nextU() or 0xFF000000.toInt()
            p.color = color

            c.save()
            c.translate(xOffset.toFloat(), yOffset.toFloat())
            c.drawArc(rect, 0f, arc, useCenter = false, paint = p)
            c.restore()

            when (direction) {
                0 -> {
                    xOffset += 10
                    if (xOffset >= 700) direction = 1
                }
                1 -> {
                    xOffset -= 10
                    yOffset += 10
                    if (xOffset < 50) direction = 2
                }
                2 -> xOffset += 10
            }
            arc += 0.01f
        }
    }
}
