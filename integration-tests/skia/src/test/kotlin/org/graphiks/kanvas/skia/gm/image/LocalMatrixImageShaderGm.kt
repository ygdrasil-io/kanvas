package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/localmatriximageshader.cpp::localmatriximageshader` (250 x 250).
 * Validates that a Shader.Image wrapped in a local matrix composes correctly.
 * @see https://github.com/google/skia/blob/main/gm/localmatriximageshader.cpp
 */
class LocalMatrixImageShaderGm : SkiaGm {
    override val name = "localmatriximageshader"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 250
    override val height = 250

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val redImage = makeImage(Color.RED)
        val translate = Matrix33.translate(100f, 0f)
        val rotate = Matrix33.rotate(45f)

        val redComposed = translate * rotate
        var paint = Paint(
            shader = Shader.Image(redImage, TileMode.CLAMP, TileMode.CLAMP, SamplingOptions.NEAREST)
                .let { Shader.WithLocalMatrix(it, redComposed) },
        )
        canvas.drawRect(Rect.fromXYWH(0f, 0f, 250f, 250f), paint)

        val blueImage = makeImage(Color.BLUE)
        val blueComposed = rotate * translate
        paint = paint.copy(
            shader = Shader.Image(blueImage, TileMode.CLAMP, TileMode.CLAMP, SamplingOptions.NEAREST)
                .let { Shader.WithLocalMatrix(it, blueComposed) },
        )
        canvas.drawRect(Rect.fromXYWH(0f, 0f, 250f, 250f), paint)

        canvas.translate(100f, 0f)
        paint = paint.copy(
            shader = Shader.Image(redImage, TileMode.CLAMP, TileMode.CLAMP, SamplingOptions.NEAREST)
                .let { Shader.WithLocalMatrix(it, redComposed) },
        )
        canvas.drawRect(Rect.fromXYWH(0f, 0f, 250f, 250f), paint)
        paint = paint.copy(
            shader = Shader.Image(blueImage, TileMode.CLAMP, TileMode.CLAMP, SamplingOptions.NEAREST)
                .let { Shader.WithLocalMatrix(it, blueComposed) },
        )
        canvas.drawRect(Rect.fromXYWH(0f, 0f, 250f, 250f), paint)
    }

    private fun makeImage(color: Color): org.graphiks.kanvas.image.Image {
        val surface = Surface(100, 100)
        surface.canvas {
            drawRect(Rect.fromXYWH(25f, 25f, 50f, 50f), Paint(antiAlias = true, color = color))
        }
        return surface.makeImageSnapshot()
    }
}
