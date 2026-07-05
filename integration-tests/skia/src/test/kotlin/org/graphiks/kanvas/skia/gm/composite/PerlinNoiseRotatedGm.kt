package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/perlinnoise.cpp::PerlinNoiseRotatedGM` (320 × 220).
 *
 * Repro for skbug.com/40045243 — Perlin shader should track canvas rotation.
 * 3x2 grid: two noise types (fractal, turbulence) x three rotations (0, 10, 80).
 * @see https://github.com/google/skia/blob/main/gm/perlinnoise.cpp
 */
class PerlinNoiseRotatedGm : SkiaGm {
    override val name = "perlinnoise_rotated"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = 320
    override val height = 220

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val outline = Paint(
            color = Color.BLACK,
            strokeWidth = 2f,
            style = PaintStyle.STROKE,
            antiAlias = true,
        )

        val rectToDraw = Rect.fromXYWH(0f, 0f, 60f, 60f)
        val marker = Rect.fromXYWH(0f, 0f, 5f, 5f)
        val cellW = 100f
        val cellH = 100f
        val kPad = 10

        var yOffset = kPad.toFloat()
        for (type in listOf(Type.FRACTAL, Type.TURBULENCE)) {
            var xOffset = kPad.toFloat()
            val noisePaint = Paint(shader = makeShader(type))
            for (rotation in listOf(0f, 10f, 80f)) {
                canvas.save()
                canvas.translate(xOffset, yOffset)
                canvas.drawRect(Rect.fromXYWH(0f, 0f, cellW, cellH), outline)

                canvas.save()
                canvas.translate(cellW / 2f, cellH / 2f)
                canvas.rotate(rotation)
                canvas.translate(-rectToDraw.width / 2f, -rectToDraw.height / 2f)
                canvas.drawRect(rectToDraw, noisePaint)
                canvas.drawRect(rectToDraw, outline)
                canvas.drawRect(marker, outline)
                canvas.restore()
                canvas.restore()

                xOffset += cellW
            }
            yOffset += cellH
        }
    }

    private enum class Type { FRACTAL, TURBULENCE }

    private fun makeShader(type: Type): Shader = when (type) {
        Type.FRACTAL -> Shader.FractalNoise(0.05f, 0.05f, 1, 0, null)
        Type.TURBULENCE -> Shader.PerlinNoise(0.05f, 0.05f, 1, 0, null)
    }
}
