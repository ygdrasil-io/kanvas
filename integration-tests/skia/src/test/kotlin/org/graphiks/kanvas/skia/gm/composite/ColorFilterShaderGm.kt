package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.math.color.ColorMatrixF32

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32

/**
 * Port of Skia's gm/colorfilterimagefilter.cpp (colorfiltershader).
 * Tests Shader.WithColorFilter across gradient shaders.
 * @see https://github.com/google/skia/blob/main/gm/colorfilterimagefilter.cpp
 */
class ColorFilterShaderGm : SkiaGm {
    override val name = "colorfiltershader"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 34.3
    override val width = 610
    override val height = 610

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val filters: List<ColorFilter> = listOf(
            cfMakeBrightness(0.5f),
            cfMakeGrayscale(),
            cfMakeColorize(ColorARGB.Blue),
        )

        val shaders: List<Shader> = listOf(
            shMakeLinearGradient0(),
            shMakeLinearGradient1(),
            Shader.ConicalGradient(
                start = Point2F32(0f, 0f), startRadius = 50f,
                end = Point2F32(0f, 0f), endRadius = 150f,
                stops = listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                tileMode = TileMode.CLAMP,
            ),
        )

        val r = RectF32(0f, 0f, 120f, 120f)
        canvas.translate(20f, 20f)
        for (shader in shaders) {
            canvas.save()
            val rawPaint = Paint(shader = shader)
            canvas.drawRect(r, rawPaint)
            canvas.translate(150f, 0f)
            for (filter in filters) {
                val filteredShader = Shader.WithColorFilter(shader, filter)
                val paint = Paint(shader = filteredShader)
                canvas.drawRect(r, paint)
                canvas.translate(150f, 0f)
            }
            canvas.restore()
            canvas.translate(0f, 150f)
        }
    }

    private fun cfMakeBrightness(brightness: Float): ColorFilter {
        val matrix = floatArrayOf(
            1f, 0f, 0f, 0f, brightness,
            0f, 1f, 0f, 0f, brightness,
            0f, 0f, 1f, 0f, brightness,
            0f, 0f, 0f, 1f, 0f,
        )
        return ColorFilter.Matrix(ColorMatrixF32.of(matrix))
    }

    private fun cfMakeGrayscale(): ColorFilter {
        val matrix = FloatArray(20)
        matrix[0] = 0.2126f; matrix[1] = 0.7152f; matrix[2] = 0.0722f
        matrix[5] = 0.2126f; matrix[6] = 0.7152f; matrix[7] = 0.0722f
        matrix[10] = 0.2126f; matrix[11] = 0.7152f; matrix[12] = 0.0722f
        matrix[18] = 1.0f
        return ColorFilter.Matrix(ColorMatrixF32.of(matrix))
    }

    private fun cfMakeColorize(color: ColorARGB): ColorFilter =
        ColorFilter.Blend(color, BlendMode.SRC)

    private fun shMakeLinearGradient0(): Shader = Shader.LinearGradient(
        start = Point2F32(0f, 0f),
        end = Point2F32(100f, 100f),
        stops = listOf(
            GradientStop(0f, ColorARGB.fromRGBA(1f, 0f, 0f, 1f)),
            GradientStop(0.5f, ColorARGB.fromRGBA(0f, 1f, 0f, 1f)),
            GradientStop(1f, ColorARGB.fromRGBA(0f, 0f, 1f, 1f)),
        ),
        tileMode = TileMode.REPEAT,
    )

    private fun shMakeLinearGradient1(): Shader = Shader.LinearGradient(
        start = Point2F32(0f, 0f),
        end = Point2F32(100f, 100f),
        stops = listOf(
            GradientStop(0f, ColorARGB.fromRGBA(1f, 0f, 0f, 1f)),
            GradientStop(0.5f, ColorARGB.fromRGBA(0f, 1f, 0f, 0f)),
            GradientStop(1f, ColorARGB.fromRGBA(0f, 0f, 1f, 1f)),
        ),
        tileMode = TileMode.REPEAT,
    )
}
