package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33

/**
 * Port of Skia's `gm/manypathatlases.cpp`.
 * Tests path atlas behavior by applying multiple clip-path rotations before drawing.
 * @see https://github.com/google/skia/blob/main/gm/manypathatlases.cpp
 */
open class ManyPathAtlasesGm(private val maxAtlasSize: Int) : SkiaGm {
    override val name = "manypathatlases_$maxAtlasSize"
    override val renderFamily = RenderFamily.CLIP
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 128
    override val height = 128

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(r = 1f, g = 1f, b = 0f)

        val clip = Path {
            moveTo(-50f, 20f)
            cubicTo(-50f, -20f, 50f, -20f, 50f, 40f)
            cubicTo(20f, 0f, -20f, 0f, -50f, 20f)
        }
        val translateM = Matrix33.translate(64f, 70f)
        val transformedClip = Path { }.apply { reverseAddPath(clip) }.transform(translateM)

        for (i in 0 until 4) {
            val angle = 30f * i + 128f
            val rot = Matrix33.translate(64f, 70f) * Matrix33.rotate(angle) * Matrix33.translate(-64f, -70f)
            val rotatedClip = Path { }.apply { reverseAddPath(transformedClip) }.transform(rot)
            canvas.clipPath(rotatedClip)
        }

        val path = Path {
            moveTo(20f, 0f)
            lineTo(108f, 0f)
            cubicTo(108f, 20f, 108f, 20f, 128f, 20f)
            lineTo(128f, 108f)
            cubicTo(108f, 108f, 108f, 108f, 108f, 128f)
            lineTo(20f, 128f)
            cubicTo(20f, 108f, 20f, 108f, 0f, 108f)
            lineTo(0f, 20f)
            cubicTo(20f, 20f, 20f, 20f, 20f, 0f)
        }

        val teal = Paint(antiAlias = true, color = Color.fromRGBA(0.03f, 0.91f, 0.87f, 1f))
        canvas.drawPath(path, teal)
    }
}

class ManyPathAtlases128Gm : ManyPathAtlasesGm(128) {
    override val renderCost = RenderCost.FAST
}
class ManyPathAtlases2048Gm : ManyPathAtlasesGm(2048) {
    override val renderCost = RenderCost.FAST
}
