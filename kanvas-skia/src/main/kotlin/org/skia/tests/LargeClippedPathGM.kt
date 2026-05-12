package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorCYAN
import org.skia.foundation.SK_ColorMAGENTA
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathDirection
import org.skia.foundation.SkPathFillType
import org.skia.math.SK_ScalarPI
import org.skia.math.SkISize
import org.skia.math.SkRect
import kotlin.math.cos
import kotlin.math.sin

/**
 * Shared body for `largeclippedpath_winding` / `largeclippedpath_evenodd`.
 *
 * Mirrors the C++ helper `draw_clipped_flower(SkCanvas*, SkPathFillType)`
 * from `gm/largeclippedpath.cpp`. Builds a 50×50 grid of alternating-
 * direction rect contours as the clip, then renders a 9-petal flower
 * with an inner disc as a magenta antialiased path under the supplied
 * fill rule.
 */
internal fun drawClippedFlower(canvas: SkCanvas, fillType: SkPathFillType) {
    val kSize = 1000
    canvas.clear(SK_ColorCYAN)

    val clip = SkPathBuilder()
    clip.setFillType(SkPathFillType.kWinding)
    val kGridCount = 50
    val kCellSize: Float = kSize.toFloat() / kGridCount
    for (y in 0 until kGridCount) {
        val dir = if ((y and 1) == 0) SkPathDirection.kCW else SkPathDirection.kCCW
        clip.addRect(
            SkRect.MakeLTRB(0f, y * kCellSize, kSize.toFloat(), (y + 1) * kCellSize),
            dir,
        )
    }
    for (x in 0 until kGridCount) {
        val dir = if ((x and 1) == 0) SkPathDirection.kCW else SkPathDirection.kCCW
        clip.addRect(
            SkRect.MakeLTRB(x * kCellSize, 0f, (x + 1) * kCellSize, kSize.toFloat()),
            dir,
        )
    }
    canvas.clipPath(clip.detach())

    val flower = SkPathBuilder()
    flower.setFillType(fillType)
    flower.moveTo(1f, 0f)
    val kNumPetals = 9
    for (i in 1..kNumPetals) {
        val c = 2f * SK_ScalarPI * (i - 0.5f) / kNumPetals
        val theta = 2f * SK_ScalarPI * i / kNumPetals
        flower.quadTo(
            cos(c) * 2f, sin(c) * 2f,
            cos(theta), sin(theta),
        )
    }
    flower.close()
    flower.addArc(SkRect.MakeLTRB(-0.75f, -0.75f, 0.75f, 0.75f), 0f, 360f)

    canvas.translate(kSize / 2f, kSize / 2f)
    canvas.scale(kSize / 3f, kSize / 3f)

    val paint = SkPaint().apply {
        isAntiAlias = true
        color = SK_ColorMAGENTA
    }
    canvas.drawPath(flower.detach(), paint)
}

/**
 * Port of Skia's `gm/largeclippedpath.cpp::largeclippedpath_winding`.
 *
 * Exercises the inner-triangulation path under a large composite clip
 * that itself is a [SkPathFillType.kWinding] path (the 50×50 alternating-
 * direction rect grid). The clip's winding rule causes overlapping rects
 * to leave only the AA-rasterised seams visible (every cell is filled
 * exactly once, so the union is the full 1000 × 1000 canvas — with the
 * grid AA seams showing through). A 9-petal magenta flower is then drawn
 * on top, scaled to fill the canvas; the flower is rendered with the
 * `kWinding` fill rule, so the interior petals fully fill (no even-odd
 * "donut" cut-outs).
 *
 * C++ original (`gm/largeclippedpath.cpp:48-50`):
 * ```cpp
 * DEF_SIMPLE_GM(largeclippedpath_winding, canvas, kSize, kSize) {
 *     draw_clipped_flower(canvas, SkPathFillType::kWinding);
 * }
 * ```
 */
public class LargeClippedPathWindingGM : GM() {
    override fun getName(): String = "largeclippedpath_winding"
    override fun getISize(): SkISize = SkISize.Make(1000, 1000)
    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        drawClippedFlower(c, SkPathFillType.kWinding)
    }
}

/**
 * Port of Skia's `gm/largeclippedpath.cpp::largeclippedpath_evenodd`.
 *
 * Same scene as [LargeClippedPathWindingGM] but the flower uses the
 * [SkPathFillType.kEvenOdd] rule. The flower path crosses itself at the
 * petal joins and the inner disc subcontour subtracts via even-odd —
 * exposing the "donut" inside the disc and a sparser fill in the petal
 * intersections.
 *
 * C++ original (`gm/largeclippedpath.cpp:52-54`):
 * ```cpp
 * DEF_SIMPLE_GM(largeclippedpath_evenodd, canvas, kSize, kSize) {
 *     draw_clipped_flower(canvas, SkPathFillType::kEvenOdd);
 * }
 * ```
 */
public class LargeClippedPathEvenoddGM : GM() {
    override fun getName(): String = "largeclippedpath_evenodd"
    override fun getISize(): SkISize = SkISize.Make(1000, 1000)
    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        drawClippedFlower(c, SkPathFillType.kEvenOdd)
    }
}
