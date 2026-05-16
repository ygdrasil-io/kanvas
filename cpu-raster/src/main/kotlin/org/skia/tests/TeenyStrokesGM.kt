package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkColor
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * Port of Skia's `gm/strokes.cpp` (`TeenyStrokesGM`).
 *
 * Five colour-coded sets of 2 lines each, drawn under a sequence of huge
 * uniform CTM scales (1/scale, 1/scale) paired with miniature stroke widths
 * (`scale * 5`) and miniature line endpoints (`(20*scale, 20*scale)` …
 * `(100*scale, 100*scale)`). Net device-space geometry is identical for
 * every iteration — a 5-pixel-wide stroke from `(20, 20)` to `(20, 100)`
 * (vertical) and from `(20, 20)` to `(100, 100)` (diagonal) — but the
 * extreme range of CTM scale factors (50000× → 500000×) stresses the
 * stroker's numerical robustness when stroke geometry is computed in
 * user space and transformed late by the scanline rasterizer.
 *
 * Reference image: `teenyStrokes.png`, 400 × 800 px. Canvas size matches
 * upstream's `W = 400`, `H = 400`, returning `SkISize::Make(W, H * 2)`.
 *
 * The base canvas is white (no `drawColor` in upstream — the bitmap's
 * profile-invariant white pre-fill from [TestUtils.runGmTest] suffices).
 */
public class TeenyStrokesGM : GM() {

    override fun getName(): String = "teenyStrokes"
    override fun getISize(): SkISize = SkISize.Make(W, H * 2)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        line(c, 0.00005f,  SkColorSetARGB(0xFF, 0x00, 0x00, 0x00)) // BLACK
        line(c, 0.000045f, SkColorSetARGB(0xFF, 0xFF, 0x00, 0x00)) // RED
        line(c, 0.0000035f, SkColorSetARGB(0xFF, 0x00, 0xFF, 0x00)) // GREEN
        line(c, 0.000003f, SkColorSetARGB(0xFF, 0x00, 0x00, 0xFF)) // BLUE
        line(c, 0.000002f, SkColorSetARGB(0xFF, 0x00, 0x00, 0x00)) // BLACK
    }

    private fun line(canvas: SkCanvas, scale: SkScalar, color: SkColor) {
        val p = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            this.color = color
        }
        canvas.translate(50f, 0f)
        canvas.save()
        p.strokeWidth = scale * 5f
        canvas.scale(1f / scale, 1f / scale)
        canvas.drawLine(20f * scale, 20f * scale, 20f * scale, 100f * scale, p)
        canvas.drawLine(20f * scale, 20f * scale, 100f * scale, 100f * scale, p)
        canvas.restore()
    }

    private companion object {
        // Defined in `strokes.cpp` as file-level constants shared with other
        // GMs in the same translation unit (StrokesGM, Strokes2GM, …). The
        // `teenyStrokes.png` reference is 400 × 800, matching `W = H = 400`.
        const val W = 400
        const val H = 400
    }
}
