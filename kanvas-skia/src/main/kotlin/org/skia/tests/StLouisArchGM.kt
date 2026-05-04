package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/stlouisarch.cpp` (`StLouisArchGM`).
 *
 * Six paths drawn as **hairlines** (`strokeWidth = 0`, AA on) under a
 * `scale(1, -1)` + `translate(0, -kHeight)` flip — each is the
 * "St. Louis Arch" parabola from `(0, 0)` to `(kWidth, 0)` peaking at
 * `(kWidth/2, kHeight)`, expressed in three different curve types
 * (quad, cubic, conic) plus a degenerate flat variant of each.
 *
 *  1. `bigQuad`         — single `quadTo(W/2, H, W, 0)`
 *  2. `degenBigQuad`    — `quadTo(0, y, W, y)` (collapsed control)
 *  3. `bigCubic`        — `cubicTo(0, H, W, H, W, 0)`
 *  4. `degenBigCubic`   — `cubicTo(0, y, 0, y, W, y)` (3-collinear-points)
 *  5. `bigConic`        — `conicTo(W/2, H, W, 0, .5)`
 *  6. `degenBigConic`   — `conicTo(0, y, W, y, .5)` (collapsed control)
 *
 * Reference image: `stlouisarch.png`, 256 × 256. Stresses the curve
 * flattening of degenerate curves (quad/cubic/conic with zero-length
 * control segments) under the stroker's hairline path. Note: our port
 * falls back to `strokeWidth = 1` for the `0` request (true hairline
 * scan-line is a future phase), so coverage will broaden by one row of
 * pixels per stroke vs upstream — the test is held to a forgiving
 * floor that absorbs that one-pixel band.
 */
public class StLouisArchGM : GM() {

    private val paths: MutableList<SkPath> = mutableListOf()

    override fun getName(): String = "stlouisarch"
    override fun getISize(): SkISize = SkISize.Make(kWidth.toInt(), kHeight.toInt())

    override fun onOnceBeforeDraw() {
        // 1. Big quad parabola (W/2, H) → (W, 0).
        paths.add(SkPathBuilder()
            .moveTo(0f, 0f)
            .quadTo(kWidth / 2f, kHeight, kWidth, 0f)
            .detach())

        // 2. Degenerate quad (control on same y as endpoints).
        run {
            val yPos = kHeight / 2f + 10f
            paths.add(SkPathBuilder()
                .moveTo(0f, yPos)
                .quadTo(0f, yPos, kWidth, yPos)
                .detach())
        }

        // 3. Big cubic.
        paths.add(SkPathBuilder()
            .moveTo(0f, 0f)
            .cubicTo(0f, kHeight, kWidth, kHeight, kWidth, 0f)
            .detach())

        // 4. Degenerate cubic (3 collinear points).
        run {
            val yPos = kHeight / 2f
            paths.add(SkPathBuilder()
                .moveTo(0f, yPos)
                .cubicTo(0f, yPos, 0f, yPos, kWidth, yPos)
                .detach())
        }

        // 5. Big conic.
        paths.add(SkPathBuilder()
            .moveTo(0f, 0f)
            .conicTo(kWidth / 2f, kHeight, kWidth, 0f, 0.5f)
            .detach())

        // 6. Degenerate conic (control on same y as endpoints).
        run {
            val yPos = kHeight / 2f - 10f
            paths.add(SkPathBuilder()
                .moveTo(0f, yPos)
                .conicTo(0f, yPos, kWidth, yPos, 0.5f)
                .detach())
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.save()
        c.scale(1f, -1f)
        c.translate(0f, -kHeight)
        for (p in paths) {
            val paint = SkPaint().apply {
                color = SkColorSetARGB(0xFF, 0, 0, 0)
                isAntiAlias = true
                style = SkPaint.Style.kStroke_Style
                strokeWidth = 0f
            }
            c.drawPath(p, paint)
        }
        c.restore()
    }

    private companion object {
        private const val kWidth: Float = 256f
        private const val kHeight: Float = 256f
    }
}
