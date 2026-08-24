package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32

/**
 * Port of Skia's `gm/rrects.cpp` (RRectGM with BW clip).
 * 43 rrects drawn with non-anti-aliased clipRRect.
 * @see https://github.com/google/skia/blob/main/gm/rrects.cpp
 */
class RRectClipBwGm : SkiaGm {
    override val name = "rrect_clip_bw"
    override val renderFamily = RenderFamily.CLIP
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = kImageWidth
    override val height = kImageHeight

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0xDD / 255f, 0xDD / 255f, 0xDD / 255f, 1f)
        val rrects = buildRRects()

        var x = 1
        var y = 1
        for (idx in 0 until kNumRRects) {
            canvas.save()
            canvas.translate(x.toFloat(), y.toFloat())
            val path = Path { }.apply { addRRect(rrects[idx]) }
            val paint = Paint(color = ColorARGB.fromRGBA(0f, 0f, 0f, 1f))
            canvas.drawPath(path, paint)
            canvas.restore()
            x += kTileX
            if (x > kImageWidth) {
                x = 1
                y += kTileY
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared constants and rrect builder reused by the clip-package RRectF32 GMs.
// ---------------------------------------------------------------------------

internal const val kImageWidth = 640
internal const val kImageHeight = 480
internal const val kTileX = 80
internal const val kTileY = 40
internal const val kNumSimpleCases = 7
internal const val kNumComplexCases = 35
internal const val kNumRRects = kNumSimpleCases + kNumComplexCases + 1

internal val gRadii: Array<Array<CornerRadiiF32>> = arrayOf(
    arrayOf(CornerRadiiF32.of(40f, 40f), CornerRadiiF32.of(40f, 40f), CornerRadiiF32.of(40f, 40f), CornerRadiiF32.of(40f, 40f)),
    arrayOf(CornerRadiiF32.of(8f, 8f), CornerRadiiF32.of(32f, 32f), CornerRadiiF32.of(8f, 8f), CornerRadiiF32.of(32f, 32f)),
    arrayOf(CornerRadiiF32.of(16f, 8f), CornerRadiiF32.of(8f, 16f), CornerRadiiF32.of(16f, 8f), CornerRadiiF32.of(8f, 16f)),
    arrayOf(CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(16f, 16f), CornerRadiiF32.of(8f, 8f), CornerRadiiF32.of(32f, 32f)),
    arrayOf(CornerRadiiF32.of(30f, 30f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f)),
    arrayOf(CornerRadiiF32.of(30f, 15f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f)),
    arrayOf(CornerRadiiF32.of(15f, 30f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f)),
    arrayOf(CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(30f, 30f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f)),
    arrayOf(CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(30f, 15f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f)),
    arrayOf(CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(15f, 30f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f)),
    arrayOf(CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(30f, 30f), CornerRadiiF32.of(0f, 0f)),
    arrayOf(CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(30f, 15f), CornerRadiiF32.of(0f, 0f)),
    arrayOf(CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(15f, 30f), CornerRadiiF32.of(0f, 0f)),
    arrayOf(CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(30f, 30f)),
    arrayOf(CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(30f, 15f)),
    arrayOf(CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(15f, 30f)),
    arrayOf(CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(100f, 400f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f)),
    arrayOf(CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(400f, 400f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f)),
    arrayOf(CornerRadiiF32.of(400f, 400f), CornerRadiiF32.of(400f, 400f), CornerRadiiF32.of(400f, 400f), CornerRadiiF32.of(400f, 400f)),
    arrayOf(CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(20f, 20f), CornerRadiiF32.of(20f, 20f), CornerRadiiF32.of(0f, 0f)),
    arrayOf(CornerRadiiF32.of(20f, 20f), CornerRadiiF32.of(20f, 20f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f)),
    arrayOf(CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(20f, 20f), CornerRadiiF32.of(20f, 20f)),
    arrayOf(CornerRadiiF32.of(20f, 20f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(20f, 20f)),
    arrayOf(CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0.2f, 0.2f), CornerRadiiF32.of(0.2f, 0.2f), CornerRadiiF32.of(0f, 0f)),
    arrayOf(CornerRadiiF32.of(0.3f, 0.3f), CornerRadiiF32.of(0.3f, 0.3f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f)),
    arrayOf(CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(15f, 15f)),
    arrayOf(CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(15f, 15f), CornerRadiiF32.of(0f, 0f)),
    arrayOf(CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(15f, 15f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f)),
    arrayOf(CornerRadiiF32.of(15f, 15f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f), CornerRadiiF32.of(0f, 0f)),
    arrayOf(CornerRadiiF32.of(5f, 7f), CornerRadiiF32.of(8f, 7f), CornerRadiiF32.of(8f, 12f), CornerRadiiF32.of(5f, 12f)),
    arrayOf(CornerRadiiF32.of(0f, 7f), CornerRadiiF32.of(8f, 7f), CornerRadiiF32.of(8f, 12f), CornerRadiiF32.of(0f, 12f)),
    arrayOf(CornerRadiiF32.of(0.4f, 7f), CornerRadiiF32.of(8f, 7f), CornerRadiiF32.of(8f, 12f), CornerRadiiF32.of(0.4f, 12f)),
    arrayOf(CornerRadiiF32.of(0.4f, 0.4f), CornerRadiiF32.of(8f, 0.4f), CornerRadiiF32.of(8f, 12f), CornerRadiiF32.of(0.4f, 12f)),
    arrayOf(CornerRadiiF32.of(20f, 0.4f), CornerRadiiF32.of(18f, 0.4f), CornerRadiiF32.of(18f, 0.4f), CornerRadiiF32.of(20f, 0.4f)),
    arrayOf(CornerRadiiF32.of(0.3f, 0.4f), CornerRadiiF32.of(0.3f, 0.4f), CornerRadiiF32.of(0.3f, 0.4f), CornerRadiiF32.of(0.3f, 0.4f)),
)

internal fun buildRRects(): Array<RRectF32> {
    val rects = Array(kNumRRects) {
        RRectF32.of(RectF32(0f, 0f, 0f, 0f), 0f)
    }
    val w = (kTileX - 2).toFloat()
    val h = (kTileY - 2).toFloat()
    val sq = (kTileY - 2).toFloat()

    rects[0] = RRectF32.of(RectF32(0f, 0f, w, h), 0f)
    rects[1] = RRectF32.of(RectF32(0f, 0f, w, h), CornerRadiiF32.of(w / 2f, h / 2f))
    rects[2] = RRectF32.of(RectF32(0f, 0f, w, h), 10f)
    rects[3] = RRectF32.of(RectF32(0f, 0f, w, h), CornerRadiiF32.of(10f, 5f))
    rects[4] = RRectF32.of(RectF32(0f, 0f, w, h), 1f)
    rects[5] = RRectF32.of(RectF32(0f, 0f, w, h), 0.5f)
    rects[6] = RRectF32.of(RectF32(0f, 0f, w, h), 0.2f)

    rects[kNumSimpleCases] = RRectF32.of(RectF32(0f, 0f, sq, sq), gRadii[0][0], gRadii[0][1], gRadii[0][2], gRadii[0][3])
    for (i in 1 until kNumComplexCases) {
        val ri = gRadii[i]
        rects[kNumSimpleCases + i] = RRectF32.of(RectF32(0f, 0f, w, h), ri[0], ri[1], ri[2], ri[3])
    }

    rects[kNumRRects - 1] = RRectF32.of(RectF32.ofLTRB(9f, 9f, 1699f, 1699f), CornerRadiiF32.of(843.749f, 843.75f))
    return rects
}
