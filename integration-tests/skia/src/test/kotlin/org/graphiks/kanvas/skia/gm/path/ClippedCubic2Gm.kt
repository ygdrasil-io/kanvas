package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/cubicpaths.cpp:ClippedCubic2GM`.
 *
 * 8 cells in a 4×2 layout: a self-intersecting cubic and its 90°-flipped
 * variant (built via [Path.transform]) drawn under various clip
 * rectangles to expose the rasterizer's behaviour with curves that exit
 * and re-enter the clip.
 *
 * The "flipped" path is constructed by swapping x and y coordinates,
 * mirroring the cubic across the diagonal.
 *
 * Reference image: `clippedcubic2.png`, 1240 × 390, default white BG.
 * @see https://github.com/google/skia/blob/main/gm/cubicpaths.cpp
 */
class ClippedCubic2Gm : SkiaGm {
    override val name = "clippedcubic2"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 94.2
    override val width = 1240
    override val height = 390

    private val path: Path by lazy {
        Path {
            moveTo(69.7030518991886f, 0f)
            cubicTo(69.7030518991886f, 21.831149999999997f, 58.08369508178456f, 43.66448333333333f, 34.8449814469765f, 65.5f)
            cubicTo(11.608591683531916f, 87.33115f, -0.010765133872116195f, 109.16448333333332f, -0.013089005235602302f, 131f)
            close()
        }
    }

    private val flipped: Path by lazy {
        Path {
            moveTo(0f, 69.7030518991886f)
            cubicTo(21.831149999999997f, 69.7030518991886f, 43.66448333333333f, 58.08369508178456f, 65.5f, 34.8449814469765f)
            cubicTo(87.33115f, 11.608591683531916f, 109.16448333333332f, -0.010765133872116195f, 131f, -0.013089005235602302f)
            close()
        }
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.save()
        canvas.translate(-2f, 120f)
        drawOne(canvas, path, Rect(0f, 0f, 80f, 150f))
        canvas.translate(0f, 170f)
        drawOne(canvas, path, Rect(0f, 0f, 80f, 100f))
        canvas.translate(0f, 170f)
        drawOne(canvas, path, Rect(0f, 0f, 30f, 150f))
        canvas.translate(0f, 170f)
        drawOne(canvas, path, Rect(0f, 0f, 10f, 150f))
        canvas.restore()

        canvas.save()
        canvas.translate(20f, -2f)
        drawOne(canvas, flipped, Rect(0f, 0f, 150f, 80f))
        canvas.translate(170f, 0f)
        drawOne(canvas, flipped, Rect(0f, 0f, 100f, 80f))
        canvas.translate(170f, 0f)
        drawOne(canvas, flipped, Rect(0f, 0f, 150f, 30f))
        canvas.translate(170f, 0f)
        drawOne(canvas, flipped, Rect(0f, 0f, 150f, 10f))
        canvas.restore()
    }

    private fun drawOne(canvas: GmCanvas, p: Path, clip: Rect) {
        val frame = Paint(style = PaintStyle.STROKE)
        val fill = Paint()
        canvas.drawRect(clip, frame)
        canvas.drawPath(p, frame)
        canvas.save()
        canvas.clipRect(clip)
        canvas.drawPath(p, fill)
        canvas.restore()
    }
}
