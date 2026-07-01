package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/rrects.cpp` — `RRectGM(kBW_Draw_Type)`.
 * GM name: `rrect_draw_bw`. Renders 43 rrects with no anti-alias, tiled
 * in 80 x 40 px cells on a 640 x 480 image. Each rrect is drawn via
 * [Path.addRRect].
 */
/**
 * Port of Skia's `gm/rrects.cpp` (RRectGM with BW draw).
 * 43 non-anti-aliased rrects across a tiled grid.
 * @see https://github.com/google/skia/blob/main/gm/rrects.cpp
 */
class RRectDrawBwGm : SkiaGm {
    override val name = "rrect_draw_bw"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 29.0
    override val width = kImageWidth
    override val height = kImageHeight

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0xDD / 255f, 0xDD / 255f, 0xDD / 255f, 1f)
        val rrects = buildRRects()
        val paint = Paint()

        var x = 1
        var y = 1
        for (idx in 0 until kNumRRects) {
            canvas.save()
            canvas.translate(x.toFloat(), y.toFloat())
            if (idx == kNumRRects - 1) {
                val path = Path { }.apply { addRRect(rrects[idx]) }
                canvas.drawPath(path, paint)
            } else {
                val path = Path { }.apply { addRRect(rrects[idx]) }
                canvas.drawPath(path, paint)
            }
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
// Shared constants and rrect builder reused by the path-package RRect GMs.
// ---------------------------------------------------------------------------

internal const val kImageWidth = 640
internal const val kImageHeight = 480
internal const val kTileX = 80
internal const val kTileY = 40
internal const val kNumSimpleCases = 7
internal const val kNumComplexCases = 35
internal const val kNumRRects = kNumSimpleCases + kNumComplexCases + 1

internal val gRadii: Array<Array<CornerRadii>> = arrayOf(
    arrayOf(CornerRadii(40f, 40f), CornerRadii(40f, 40f), CornerRadii(40f, 40f), CornerRadii(40f, 40f)),
    arrayOf(CornerRadii(8f, 8f), CornerRadii(32f, 32f), CornerRadii(8f, 8f), CornerRadii(32f, 32f)),
    arrayOf(CornerRadii(16f, 8f), CornerRadii(8f, 16f), CornerRadii(16f, 8f), CornerRadii(8f, 16f)),
    arrayOf(CornerRadii(0f, 0f), CornerRadii(16f, 16f), CornerRadii(8f, 8f), CornerRadii(32f, 32f)),
    arrayOf(CornerRadii(30f, 30f), CornerRadii(0f, 0f), CornerRadii(0f, 0f), CornerRadii(0f, 0f)),
    arrayOf(CornerRadii(30f, 15f), CornerRadii(0f, 0f), CornerRadii(0f, 0f), CornerRadii(0f, 0f)),
    arrayOf(CornerRadii(15f, 30f), CornerRadii(0f, 0f), CornerRadii(0f, 0f), CornerRadii(0f, 0f)),
    arrayOf(CornerRadii(0f, 0f), CornerRadii(30f, 30f), CornerRadii(0f, 0f), CornerRadii(0f, 0f)),
    arrayOf(CornerRadii(0f, 0f), CornerRadii(30f, 15f), CornerRadii(0f, 0f), CornerRadii(0f, 0f)),
    arrayOf(CornerRadii(0f, 0f), CornerRadii(15f, 30f), CornerRadii(0f, 0f), CornerRadii(0f, 0f)),
    arrayOf(CornerRadii(0f, 0f), CornerRadii(0f, 0f), CornerRadii(30f, 30f), CornerRadii(0f, 0f)),
    arrayOf(CornerRadii(0f, 0f), CornerRadii(0f, 0f), CornerRadii(30f, 15f), CornerRadii(0f, 0f)),
    arrayOf(CornerRadii(0f, 0f), CornerRadii(0f, 0f), CornerRadii(15f, 30f), CornerRadii(0f, 0f)),
    arrayOf(CornerRadii(0f, 0f), CornerRadii(0f, 0f), CornerRadii(0f, 0f), CornerRadii(30f, 30f)),
    arrayOf(CornerRadii(0f, 0f), CornerRadii(0f, 0f), CornerRadii(0f, 0f), CornerRadii(30f, 15f)),
    arrayOf(CornerRadii(0f, 0f), CornerRadii(0f, 0f), CornerRadii(0f, 0f), CornerRadii(15f, 30f)),
    arrayOf(CornerRadii(0f, 0f), CornerRadii(100f, 400f), CornerRadii(0f, 0f), CornerRadii(0f, 0f)),
    arrayOf(CornerRadii(0f, 0f), CornerRadii(400f, 400f), CornerRadii(0f, 0f), CornerRadii(0f, 0f)),
    arrayOf(CornerRadii(400f, 400f), CornerRadii(400f, 400f), CornerRadii(400f, 400f), CornerRadii(400f, 400f)),
    arrayOf(CornerRadii(0f, 0f), CornerRadii(20f, 20f), CornerRadii(20f, 20f), CornerRadii(0f, 0f)),
    arrayOf(CornerRadii(20f, 20f), CornerRadii(20f, 20f), CornerRadii(0f, 0f), CornerRadii(0f, 0f)),
    arrayOf(CornerRadii(0f, 0f), CornerRadii(0f, 0f), CornerRadii(20f, 20f), CornerRadii(20f, 20f)),
    arrayOf(CornerRadii(20f, 20f), CornerRadii(0f, 0f), CornerRadii(0f, 0f), CornerRadii(20f, 20f)),
    arrayOf(CornerRadii(0f, 0f), CornerRadii(0.2f, 0.2f), CornerRadii(0.2f, 0.2f), CornerRadii(0f, 0f)),
    arrayOf(CornerRadii(0.3f, 0.3f), CornerRadii(0.3f, 0.3f), CornerRadii(0f, 0f), CornerRadii(0f, 0f)),
    arrayOf(CornerRadii(0f, 0f), CornerRadii(0f, 0f), CornerRadii(0f, 0f), CornerRadii(15f, 15f)),
    arrayOf(CornerRadii(0f, 0f), CornerRadii(0f, 0f), CornerRadii(15f, 15f), CornerRadii(0f, 0f)),
    arrayOf(CornerRadii(0f, 0f), CornerRadii(15f, 15f), CornerRadii(0f, 0f), CornerRadii(0f, 0f)),
    arrayOf(CornerRadii(15f, 15f), CornerRadii(0f, 0f), CornerRadii(0f, 0f), CornerRadii(0f, 0f)),
    arrayOf(CornerRadii(5f, 7f), CornerRadii(8f, 7f), CornerRadii(8f, 12f), CornerRadii(5f, 12f)),
    arrayOf(CornerRadii(0f, 7f), CornerRadii(8f, 7f), CornerRadii(8f, 12f), CornerRadii(0f, 12f)),
    arrayOf(CornerRadii(0.4f, 7f), CornerRadii(8f, 7f), CornerRadii(8f, 12f), CornerRadii(0.4f, 12f)),
    arrayOf(CornerRadii(0.4f, 0.4f), CornerRadii(8f, 0.4f), CornerRadii(8f, 12f), CornerRadii(0.4f, 12f)),
    arrayOf(CornerRadii(20f, 0.4f), CornerRadii(18f, 0.4f), CornerRadii(18f, 0.4f), CornerRadii(20f, 0.4f)),
    arrayOf(CornerRadii(0.3f, 0.4f), CornerRadii(0.3f, 0.4f), CornerRadii(0.3f, 0.4f), CornerRadii(0.3f, 0.4f)),
)

internal fun buildRRects(): Array<RRect> {
    val rects = Array(kNumRRects) {
        RRect(Rect(0f, 0f, 0f, 0f), 0f)
    }
    val w = (kTileX - 2).toFloat()
    val h = (kTileY - 2).toFloat()
    val sq = (kTileY - 2).toFloat()

    rects[0] = RRect(Rect(0f, 0f, w, h), 0f)
    rects[1] = RRect(Rect(0f, 0f, w, h), CornerRadii(w / 2f, h / 2f))
    rects[2] = RRect(Rect(0f, 0f, w, h), 10f)
    rects[3] = RRect(Rect(0f, 0f, w, h), CornerRadii(10f, 5f))
    rects[4] = RRect(Rect(0f, 0f, w, h), 1f)
    rects[5] = RRect(Rect(0f, 0f, w, h), 0.5f)
    rects[6] = RRect(Rect(0f, 0f, w, h), 0.2f)

    rects[kNumSimpleCases] = RRect(Rect(0f, 0f, sq, sq), gRadii[0][0], gRadii[0][1], gRadii[0][2], gRadii[0][3])
    for (i in 1 until kNumComplexCases) {
        val ri = gRadii[i]
        rects[kNumSimpleCases + i] = RRect(Rect(0f, 0f, w, h), ri[0], ri[1], ri[2], ri[3])
    }

    rects[kNumRRects - 1] = RRect(Rect.fromLTRB(9f, 9f, 1699f, 1699f), CornerRadii(843.749f, 843.75f))
    return rects
}
