package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathDirection
import org.skia.foundation.SkPathFillType
import org.skia.foundation.SkRRect
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/pathinterior.cpp:PathInteriorGM`.
 *
 * 64 cells laid out 8 × 8, each containing a path with two contours
 * — outer + inner — under every combination of:
 *  - inset-first vs inset-second
 *  - even-odd vs winding fill
 *  - outer rect vs outer rrect
 *  - inner rect vs inner rrect
 *  - outer CW vs outer CCW
 *  - inner CW vs inner CCW
 *
 * Each path is gray-filled and red-stroked. Upstream's
 * `path.hasRectangularInterior(...)` shortcut is `#if 0`-disabled, so
 * the green "interior found" overlay never draws — matching us.
 *
 * Reference image: `pathinterior.png`, 770 × 770, BG `0xFFDDDDDD`.
 *
 * Stresses the path direction encoding (CW / CCW) on rect / rrect
 * contours and the winding / even-odd fill rule dispatch on the
 * resulting two-contour donut shapes.
 */
public class PathInteriorGM : GM() {

    // Skia upstream sets `setBGColor(0xFFDDDDDD)`. Our test harness's
    // `eraseColor` writes raw bytes — bypassing the sRGB → working-space
    // transform — so a non-white / non-black BG drifts from upstream.
    // Route through `drawPaint` at the start of onDraw instead, which
    // does pass through the device's colour-space pipeline. (Same
    // workaround used by ArcOfZorroGM and ClampedGradientsGM.)

    override fun getName(): String = "pathinterior"
    override fun getISize(): SkISize = SkISize.Make(770, 770)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.drawPaint(SkPaint().apply { color = 0xFFDDDDDD.toInt() })
        c.translate(8.5f, 8.5f)

        val rect = SkRect.MakeLTRB(0f, 0f, 80f, 80f)
        val rad = rect.width() / 8f

        var i = 0
        for (insetFirst in 0..1) {
            for (doEvenOdd in 0..1) {
                for (outerRR in 0..1) {
                    for (innerRR in 0..1) {
                        for (outerCW in 0..1) {
                            for (innerCW in 0..1) {
                                val builder = SkPathBuilder().setFillType(
                                    if (doEvenOdd != 0) SkPathFillType.kEvenOdd
                                    else SkPathFillType.kWinding
                                )
                                val outerDir = if (outerCW != 0) SkPathDirection.kCW else SkPathDirection.kCCW
                                val innerDir = if (innerCW != 0) SkPathDirection.kCW else SkPathDirection.kCCW

                                var r = if (insetFirst != 0) inset(rect) else rect
                                if (outerRR != 0) {
                                    builder.addRRect(SkRRect.MakeRectXY(r, rad, rad), outerDir)
                                } else {
                                    builder.addRect(r, outerDir)
                                }
                                r = if (insetFirst != 0) rect else inset(rect)
                                if (innerRR != 0) {
                                    builder.addRRect(SkRRect.MakeRectXY(r, rad, rad), innerDir)
                                } else {
                                    builder.addRect(r, innerDir)
                                }

                                val dx = (i / 8) * rect.width() * 6f / 5f
                                val dy = (i % 8) * rect.height() * 6f / 5f
                                i++
                                show(c, builder.detach().makeOffset(dx, dy))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun show(c: SkCanvas, path: SkPath) {
        val paint = SkPaint().apply {
            isAntiAlias = true
            color = 0xFF888888.toInt()    // SK_ColorGRAY
        }
        c.drawPath(path, paint)
        paint.style = SkPaint.Style.kStroke_Style
        paint.color = 0xFFFF0000.toInt()  // SK_ColorRED
        c.drawPath(path, paint)
    }

    private fun inset(r: SkRect): SkRect {
        val ix = r.width() / 8f
        val iy = r.height() / 8f
        return SkRect.MakeLTRB(r.left + ix, r.top + iy, r.right - ix, r.bottom - iy)
    }
}
