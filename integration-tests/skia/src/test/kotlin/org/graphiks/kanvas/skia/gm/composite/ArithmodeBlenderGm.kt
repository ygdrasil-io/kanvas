package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Blender
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
 * Port of Skia's `gm/arithmode.cpp::ArithmodeBlenderGM`.
 *
 * Exercises `Blender.Arithmetic` on two side-by-side scenes:
 *  1. Blender via paint + saveLayer.
 *  2. Placeholder (gray) — no ImageFilter.Arithmetic equivalent in Kanvas.
 *  3–4. Placeholder (gray) — deferred runtime shader cells.
 * @see https://github.com/google/skia/blob/main/gm/arithmode.cpp
 */
class ArithmodeBlenderGm : SkiaGm {
    override val name = "arithmode_blender"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = (W + 30) * 2
    override val height = (H + 30) * 4

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val src = makeSrc(W, H)
        val dst = makeDst(W, H)
        val checker = makeChecker(W, H, Color(0xFFBBBBBBu), Color(0xFFEEEEEEu), 8)

        val k1 = -0.25f; val k2 = 0.25f; val k3 = 0.25f; val k4 = 0f

        canvas.drawImage(src, Rect(10f, 10f, (10 + W).toFloat(), (10 + H).toFloat()))
        canvas.drawImage(dst, Rect(10f, (10 + H + 10).toFloat(), (10 + W).toFloat(), (10 + H + 10 + H).toFloat()))

        canvas.translate((10 + W + 10).toFloat(), 10f)

        val rect = Rect(0f, 0f, W.toFloat(), H.toFloat())

        // Cell 1 — paint.blender via saveLayer chain.
        canvas.drawImage(checker, rect)
        canvas.saveLayer(rect, null)
        canvas.drawImage(dst, rect)
        canvas.drawImage(src, rect, Paint(blender = Blender.Arithmetic(k1, k2, k3, k4)))
        canvas.restore()

        canvas.translate(0f, (10 + H).toFloat())

        // Cell 2 — placeholder (no ImageFilter.Arithmetic equivalent).
        canvas.drawImage(checker, rect)
        canvas.drawRect(rect, Paint(color = Color(0xFF888888u)))

        canvas.translate(0f, (10 + H).toFloat())

        // Cell 3 — placeholder for SkShaders.Blend (deferred).
        canvas.drawImage(checker, rect)
        canvas.drawRect(rect, Paint(color = Color(0xFF888888u)))

        canvas.translate(0f, (10 + H).toFloat())

        // Cell 4 — placeholder for runtime-effect shader (deferred).
        canvas.drawImage(checker, rect)
        canvas.drawRect(rect, Paint(color = Color(0xFF888888u)))
    }

    private fun makeSrc(w: Int, h: Int): Image {
        val surface = Surface(w, h)
        surface.canvas {
            drawRect(
                Rect(0f, 0f, w.toFloat(), h.toFloat()),
                Paint(shader = Shader.LinearGradient(
                    Point(0f, 0f), Point(w.toFloat(), h.toFloat()),
                    listOf(
                        GradientStop(0f, Color.TRANSPARENT),
                        GradientStop(0.2f, Color.GREEN),
                        GradientStop(0.4f, Color(0xFF00FFFFu)),
                        GradientStop(0.6f, Color.RED),
                        GradientStop(0.8f, Color(0xFFFF00FFu)),
                        GradientStop(1f, Color.WHITE),
                    ),
                    TileMode.CLAMP,
                )),
            )
        }
        return surface.makeImageSnapshot()
    }

    private fun makeDst(w: Int, h: Int): Image {
        val surface = Surface(w, h)
        surface.canvas {
            drawRect(
                Rect(0f, 0f, w.toFloat(), h.toFloat()),
                Paint(shader = Shader.LinearGradient(
                    Point(0f, h.toFloat()), Point(w.toFloat(), 0f),
                    listOf(
                        GradientStop(0f, Color.BLUE),
                        GradientStop(0.25f, Color(0xFFFFFF00u)),
                        GradientStop(0.5f, Color.BLACK),
                        GradientStop(0.75f, Color.GREEN),
                        GradientStop(1f, Color(0xFF888888u)),
                    ),
                    TileMode.CLAMP,
                )),
            )
        }
        return surface.makeImageSnapshot()
    }

    private fun makeChecker(w: Int, h: Int, c1: Color, c2: Color, size: Int): Image {
        val surface = Surface(w, h)
        surface.canvas {
            for (y in 0 until h step size) {
                for (x in 0 until w step size) {
                    val cellX = x / size
                    val cellY = y / size
                    val color = if ((cellX + cellY) % 2 == 0) c1 else c2
                    drawRect(Rect(x.toFloat(), y.toFloat(), (x + size).toFloat(), (y + size).toFloat()), Paint(color = color))
                }
            }
        }
        return surface.makeImageSnapshot()
    }

    private companion object {
        private const val W: Int = 200
        private const val H: Int = 200
    }
}
