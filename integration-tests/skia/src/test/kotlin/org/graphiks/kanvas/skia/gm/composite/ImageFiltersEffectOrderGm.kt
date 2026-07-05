package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.Size

/**
 * Port of Skia's `gm/imagefilters.cpp::DEF_SIMPLE_GM(imagefilters_effect_order, ...)` (512 × 512).
 * Tests that color filters and mask filters are applied *before* the image filter.
 * @see https://github.com/google/skia/blob/main/gm/imagefilters.cpp
 */
class ImageFiltersEffectOrderGm : SkiaGm {
    override val name = "imagefilters_effect_order"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.TRIVIAL
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val bytes = javaClass.classLoader?.getResourceAsStream("images/mandrill_256.png")?.readAllBytes() ?: return
        val image = Image.decode(bytes)
        if (image.width == 0) return

        val kernel = floatArrayOf(
            -1f, -1f, -1f,
            -1f, 8f, -1f,
            -1f, -1f, -1f,
        )
        val edgeDetector: ImageFilter = ImageFilter.MatrixConvolution(
            Size(3f, 3f), kernel, 1f, 0f, Point(1f, 1f),
            org.graphiks.kanvas.paint.TileMode.CLAMP, false, null,
        )

        val edgeAmplify = ColorFilter.HighContrast

        val expectedCFPaint = Paint(
            imageFilter = ImageFilter.Compose(
                edgeDetector,
                ImageFilter.ColorFilter(edgeAmplify, null),
            ),
        )

        val testCFPaint = Paint(
            colorFilter = edgeAmplify,
            imageFilter = edgeDetector,
        )

        val crop = Rect(0f, 0f, image.width.toFloat(), image.height.toFloat())

        canvas.save()
        canvas.clipRect(crop)
        canvas.drawImage(image, crop, expectedCFPaint)
        canvas.restore()

        canvas.save()
        canvas.translate(image.width.toFloat(), 0f)
        canvas.clipRect(crop)
        canvas.drawImage(image, crop, testCFPaint)
        canvas.restore()

        val alphaMaskShader = Shader.RadialGradient(
            Point(128f, 128f), 128f,
            listOf(GradientStop(0.4f, Color.BLACK), GradientStop(0.9f, Color.TRANSPARENT)),
        )
        val maskFilter = MaskFilter.Shader(alphaMaskShader)

        val edgeBlend: ImageFilter = ImageFilter.Blend(
            BlendMode.SRC_OVER,
            ImageFilter.Offset(0f, 0f, null),
            edgeDetector,
        )

        val testMaskPaint = Paint(
            maskFilter = maskFilter,
            imageFilter = edgeBlend,
        )

        val expectedMaskPaint = Paint(
            imageFilter = ImageFilter.Compose(
                edgeBlend,
                ImageFilter.Blend(
                    BlendMode.SRC_IN,
                    ImageFilter.Offset(0f, 0f, null),
                    ImageFilter.ColorFilter(ColorFilter.Blend(Color.BLACK, BlendMode.SRC_IN), null),
                ),
            ),
        )

        canvas.save()
        canvas.translate(0f, image.height.toFloat())
        canvas.clipRect(crop)
        canvas.drawImage(image, crop, expectedMaskPaint)
        canvas.restore()

        canvas.save()
        canvas.translate(image.width.toFloat(), image.height.toFloat())
        canvas.clipRect(crop)
        canvas.drawImage(image, crop, testMaskPaint)
        canvas.restore()
    }
}
