package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/surface.cpp` (surface_underdraw).
 * Tests offscreen surface with save/restore, gradient fade, and composite.
 * @see https://github.com/google/skia/blob/main/gm/surface.cpp
 */
class SurfaceUnderdrawGm : SkiaGm {
    override val name = "surface_underdraw"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val surf = Surface(256, 256)
        val subset = Rect(180f, 0f, 256f, 256f)

        surf.canvas {
            val bgShader = Shader.LinearGradient(
                start = Point(0f, 0f),
                end = Point(40f, 50f),
                stops = listOf(
                    GradientStop(0f, Color.RED),
                    GradientStop(1f, Color.BLUE),
                ),
                tileMode = TileMode.REPEAT,
            )
            drawRect(Rect(0f, 0f, 256f, 256f), Paint(shader = bgShader))
        }

        val saveImg = surf.makeImageSnapshot(subset)

        surf.canvas {
            drawRect(subset, Paint(blendMode = BlendMode.CLEAR))

            val stripePaint = Paint(color = Color.fromRGBA(0f, 1f, 0f, 1f))
            var r = Rect(0f, 10f, 256f, 35f)
            while (r.bottom < 256f) {
                drawRect(r, stripePaint)
                val h = r.height
                r = Rect(r.left, r.top + h * 2f, r.right, r.bottom + h * 2f)
            }

            val fadeShader = Shader.LinearGradient(
                start = Point(subset.left, 0f),
                end = Point(subset.right, 0f),
                stops = listOf(
                    GradientStop(0f, Color.fromRGBA(0f, 0f, 0f, 1f)),
                    GradientStop(1f, Color.fromRGBA(0f, 0f, 0f, 0f)),
                ),
            )
            val fadePaint = Paint(shader = fadeShader, blendMode = BlendMode.DST_IN)
            drawRect(subset, fadePaint)

            val restorePaint = Paint(blendMode = BlendMode.DST_OVER)
            if (saveImg != null) {
                drawImage(saveImg, subset, restorePaint)
            }
        }

        val result = surf.makeImageSnapshot()
        canvas.drawImage(result, Rect(0f, 0f, 256f, 256f))
    }
}
