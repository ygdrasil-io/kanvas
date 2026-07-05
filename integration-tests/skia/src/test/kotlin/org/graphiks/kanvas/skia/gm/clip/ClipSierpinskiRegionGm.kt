package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/clip_sierpinski_region.cpp`.
 * Tests clip region performance by building a Sierpinski carpet fractal pattern of nested clip rects.
 * @see https://github.com/google/skia/blob/main/gm/clip_sierpinski_region.cpp
 */
class ClipSierpinskiRegionGm : SkiaGm {
    override val name = "clip_sierpinski_region"
    override val renderFamily = RenderFamily.CLIP
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 2 * kTrans + kSize
    override val height = 2 * kTrans + kSize

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rects = buildSierpinskiRects()

        canvas.saveLayer(Rect.fromXYWH(kTrans.toFloat(), kTrans.toFloat(), 1000f, 1000f), null)

        val cx = 50f
        val cy = 50f
        canvas.translate(cx, cy)
        canvas.rotate(25f)
        canvas.translate(-cx, -cy)

        for (r in rects) {
            canvas.clipPath(
                org.graphiks.kanvas.geometry.Path { }.apply { addRect(r) },
            )
        }

        canvas.drawRect(
            Rect(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(color = Color.RED),
        )
        canvas.restore()
    }

    private fun buildSierpinskiRects(): List<Rect> {
        val rects = mutableListOf<Rect>()
        var n = 1
        var s = kSize / 3f
        for (i in 0 until kSteps) {
            for (x in 0 until n) {
                for (y in 0 until n) {
                    val l = (3 * x + 1) * s
                    val t = (3 * y + 1) * s
                    rects.add(Rect.fromXYWH(l, t, s, s))
                }
            }
            n *= 3
            s /= 3f
        }
        return rects.map { Rect(it.left + kTrans, it.top + kTrans, it.right + kTrans, it.bottom + kTrans) }
    }

    private companion object {
        const val kSize: Int = 3 * 3 * 3 * 3 * 3
        const val kTrans: Int = 10
        const val kSteps: Int = 4
    }
}
