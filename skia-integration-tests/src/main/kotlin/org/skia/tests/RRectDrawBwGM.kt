package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.graphiks.math.SkVector

/**
 * Port of Skia's `gm/rrects.cpp` — `RRectGM(kBW_Draw_Type)`.
 *
 * GM name: `rrect_draw_bw`. Renders 43 rrects (7 simple + 35 complex + 1 big
 * clipped) with [SkPaint.Style.kFill_Style] and **no** anti-alias, tiled in
 * 80 × 40 px cells on a 640 × 480 image. The last rrect is larger than one
 * tile and is drawn translated + clipped to reveal its corner curve.
 *
 * The same [setUpRRects] / tile-loop logic is shared by
 * [RRectDrawAaGM], [RRectClipBwGM], and [RRectClipAaGM].
 *
 * Reference image: `rrect_draw_bw.png`, 640 × 480, BG 0xFFDDDDDD.
 */
public class RRectDrawBwGM : GM() {

    init { setBGColor(0xFFDDDDDD.toInt()) }

    override fun getName(): String = "rrect_draw_bw"
    override fun getISize(): SkISize = SkISize.Make(kImageWidth, kImageHeight)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val rrects = buildRRects()
        val paint = SkPaint() // no antiAlias, fill style (defaults)

        var x = 1
        var y = 1
        for (idx in 0 until kNumRRects) {
            c.save()
            c.translate(x.toFloat(), y.toFloat())
            if (idx == kNumRRects - 1) {
                c.clipRect(SkRect.MakeWH((kTileX - 2).toFloat(), (kTileY - 2).toFloat()))
                c.translate(-0.14f * rrects[idx].rect().width(),
                             -0.14f * rrects[idx].rect().height())
            }
            c.drawRRect(rrects[idx], paint)
            c.restore()
            x += kTileX
            if (x > kImageWidth) {
                x = 1
                y += kTileY
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared constants and rrect builder reused by the four RRectGM variants.
// ---------------------------------------------------------------------------

internal const val kImageWidth  = 640
internal const val kImageHeight = 480
internal const val kTileX = 80
internal const val kTileY = 40
internal const val kNumSimpleCases  = 7
internal const val kNumComplexCases = 35
internal const val kNumRRects = kNumSimpleCases + kNumComplexCases + 1 // +1 big clipped

/** Radii table: 35 complex cases, order is UL, UR, LR, LL. */
internal val gRadii: Array<Array<SkVector>> = arrayOf(
    // a circle
    arrayOf(SkVector(kTileY.toFloat(), kTileY.toFloat()), SkVector(kTileY.toFloat(), kTileY.toFloat()),
            SkVector(kTileY.toFloat(), kTileY.toFloat()), SkVector(kTileY.toFloat(), kTileY.toFloat())),
    // odd ball cases
    arrayOf(SkVector(8f,8f),   SkVector(32f,32f), SkVector(8f,8f),   SkVector(32f,32f)),
    arrayOf(SkVector(16f,8f),  SkVector(8f,16f),  SkVector(16f,8f),  SkVector(8f,16f)),
    arrayOf(SkVector(0f,0f),   SkVector(16f,16f), SkVector(8f,8f),   SkVector(32f,32f)),
    // UL
    arrayOf(SkVector(30f,30f), SkVector(0f,0f),   SkVector(0f,0f),   SkVector(0f,0f)),
    arrayOf(SkVector(30f,15f), SkVector(0f,0f),   SkVector(0f,0f),   SkVector(0f,0f)),
    arrayOf(SkVector(15f,30f), SkVector(0f,0f),   SkVector(0f,0f),   SkVector(0f,0f)),
    // UR
    arrayOf(SkVector(0f,0f),   SkVector(30f,30f), SkVector(0f,0f),   SkVector(0f,0f)),
    arrayOf(SkVector(0f,0f),   SkVector(30f,15f), SkVector(0f,0f),   SkVector(0f,0f)),
    arrayOf(SkVector(0f,0f),   SkVector(15f,30f), SkVector(0f,0f),   SkVector(0f,0f)),
    // LR
    arrayOf(SkVector(0f,0f),   SkVector(0f,0f),   SkVector(30f,30f), SkVector(0f,0f)),
    arrayOf(SkVector(0f,0f),   SkVector(0f,0f),   SkVector(30f,15f), SkVector(0f,0f)),
    arrayOf(SkVector(0f,0f),   SkVector(0f,0f),   SkVector(15f,30f), SkVector(0f,0f)),
    // LL
    arrayOf(SkVector(0f,0f),   SkVector(0f,0f),   SkVector(0f,0f),   SkVector(30f,30f)),
    arrayOf(SkVector(0f,0f),   SkVector(0f,0f),   SkVector(0f,0f),   SkVector(30f,15f)),
    arrayOf(SkVector(0f,0f),   SkVector(0f,0f),   SkVector(0f,0f),   SkVector(15f,30f)),
    // over-sized radii
    arrayOf(SkVector(0f,0f),   SkVector(100f,400f), SkVector(0f,0f), SkVector(0f,0f)),
    arrayOf(SkVector(0f,0f),   SkVector(400f,400f), SkVector(0f,0f), SkVector(0f,0f)),
    arrayOf(SkVector(400f,400f), SkVector(400f,400f), SkVector(400f,400f), SkVector(400f,400f)),
    // circular corner tabs
    arrayOf(SkVector(0f,0f),   SkVector(20f,20f), SkVector(20f,20f), SkVector(0f,0f)),
    arrayOf(SkVector(20f,20f), SkVector(20f,20f), SkVector(0f,0f),   SkVector(0f,0f)),
    arrayOf(SkVector(0f,0f),   SkVector(0f,0f),   SkVector(20f,20f), SkVector(20f,20f)),
    arrayOf(SkVector(20f,20f), SkVector(0f,0f),   SkVector(0f,0f),   SkVector(20f,20f)),
    // small radius circular corner tabs
    arrayOf(SkVector(0f,0f),     SkVector(0.2f,0.2f), SkVector(0.2f,0.2f), SkVector(0f,0f)),
    arrayOf(SkVector(0.3f,0.3f), SkVector(0.3f,0.3f), SkVector(0f,0f),     SkVector(0f,0f)),
    // single circular corner cases
    arrayOf(SkVector(0f,0f),   SkVector(0f,0f),   SkVector(0f,0f),   SkVector(15f,15f)),
    arrayOf(SkVector(0f,0f),   SkVector(0f,0f),   SkVector(15f,15f), SkVector(0f,0f)),
    arrayOf(SkVector(0f,0f),   SkVector(15f,15f), SkVector(0f,0f),   SkVector(0f,0f)),
    arrayOf(SkVector(15f,15f), SkVector(0f,0f),   SkVector(0f,0f),   SkVector(0f,0f)),
    // nine patch elliptical
    arrayOf(SkVector(5f,7f),   SkVector(8f,7f),   SkVector(8f,12f),  SkVector(5f,12f)),
    arrayOf(SkVector(0f,7f),   SkVector(8f,7f),   SkVector(8f,12f),  SkVector(0f,12f)),
    // nine patch elliptical, small radii
    arrayOf(SkVector(0.4f,7f),    SkVector(8f,7f),    SkVector(8f,12f),  SkVector(0.4f,12f)),
    arrayOf(SkVector(0.4f,0.4f),  SkVector(8f,0.4f),  SkVector(8f,12f),  SkVector(0.4f,12f)),
    arrayOf(SkVector(20f,0.4f),   SkVector(18f,0.4f), SkVector(18f,0.4f), SkVector(20f,0.4f)),
    arrayOf(SkVector(0.3f,0.4f),  SkVector(0.3f,0.4f), SkVector(0.3f,0.4f), SkVector(0.3f,0.4f)),
)

/**
 * Builds the 43 test [SkRRect]s used by all four `RRectGM` variants.
 * Mirrors `RRectGM::setUpRRects()` in `rrects.cpp`.
 */
internal fun buildRRects(): Array<SkRRect> {
    val rects = Array(kNumRRects) { SkRRect() }
    val w = (kTileX - 2).toFloat()
    val h = (kTileY - 2).toFloat()
    val sq = (kTileY - 2).toFloat() // square for first complex case

    // simple cases
    rects[0].setRect(SkRect.MakeWH(w, h))
    rects[1].setOval(SkRect.MakeWH(w, h))
    rects[2].setRectXY(SkRect.MakeWH(w, h), 10f, 10f)
    rects[3].setRectXY(SkRect.MakeWH(w, h), 10f, 5f)
    rects[4].setRectXY(SkRect.MakeWH(w, h), 1f, 1f)
    rects[5].setRectXY(SkRect.MakeWH(w, h), 0.5f, 0.5f)
    rects[6].setRectXY(SkRect.MakeWH(w, h), 0.2f, 0.2f)

    // complex cases — first is square
    rects[kNumSimpleCases].setRectRadii(SkRect.MakeWH(sq, sq), gRadii[0])
    for (i in 1 until kNumComplexCases) {
        rects[kNumSimpleCases + i].setRectRadii(SkRect.MakeWH(w, h), gRadii[i])
    }

    // last: large rrect drawn offset+clipped
    rects[kNumRRects - 1].setRectXY(SkRect.MakeLTRB(9f, 9f, 1699f, 1699f), 843.749f, 843.75f)
    return rects
}
