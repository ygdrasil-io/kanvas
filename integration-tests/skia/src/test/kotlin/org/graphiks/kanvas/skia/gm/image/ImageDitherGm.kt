package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
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
 * Port of Skia's `gm/imagedither.cpp` image_dither (425 × 110).
 * Verifies that dithering is applied consistently for image shaders
 * and drawImage calls with F16 gradient source images.
 * @see https://github.com/google/skia/blob/main/gm/imagedither.cpp
 */
class ImageDitherGm : SkiaGm {
    override val name = "image_dither"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.SLOW
    override val minSimilarity = 0.0
    override val width = 425
    override val height = 110

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val gradientShader = Shader.LinearGradient(
            start = Point(0f, 0f), end = Point(100f, 100f),
            stops = listOf(
                GradientStop(0f, Color.fromRGBA(0.333f, 0.333f, 0.333f, 1f)),
                GradientStop(1f, Color.fromRGBA(0.267f, 0.267f, 0.267f, 1f)),
            ),
            tileMode = TileMode.CLAMP,
        )
        val surface = Surface(100, 100)
        surface.canvas { drawRect(Rect(0f, 0f, 100f, 100f), Paint(shader = gradientShader)) }
        val image = surface.makeImageSnapshot()

        canvas.translate(5f, 5f)
        canvas.drawImage(image, Rect(0f, 0f, 100f, 100f))
        canvas.translate(105f, 0f)

        val imgShader = Shader.Image(image, TileMode.CLAMP, TileMode.CLAMP)
        canvas.drawRect(Rect(0f, 0f, 100f, 100f), Paint(shader = imgShader))
        canvas.translate(105f, 0f)

        canvas.drawImage(image, Rect(0f, 0f, 100f, 100f))
        canvas.translate(105f, 0f)

        canvas.drawRect(Rect(0f, 0f, 100f, 100f), Paint(shader = gradientShader))
    }
}
