package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.Size

/**
 * Port of Skia's `gm/imagefiltersgraph.cpp::ImageFiltersGraphGM` (600 x 150).
 * Walks 6 image-filter graphs exercising the filter DAG.
 * @see https://github.com/google/skia/blob/main/gm/imagefiltersgraph.cpp
 */
class ImageFiltersGraphGm : SkiaGm {
    override val name = "imagefiltersgraph"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 600
    override val height = 150

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint(color = Color.BLACK))
        val fImage = makeStringImage(100, 100, Color.WHITE, 20f, 70f, 96f, "e")
        val imgRect = Rect.fromXYWH(0f, 0f, fImage.width.toFloat(), fImage.height.toFloat())

        // 1 - Merge(Blur, ColorFilter(Erode(Blur)))
        run {
            val cf = ColorFilter.Blend(Color.RED, BlendMode.SRC_IN)
            val blur = ImageFilter.Blur(4f, 4f, input = null)
            val erode = ImageFilter.Erode(4f, 4f, input = blur)
            val color = ImageFilter.ColorFilter(cf, input = erode)
            val merge = ImageFilter.Merge(listOf(blur, color))
            canvas.drawImage(fImage, imgRect, Paint(imageFilter = merge))
            canvas.translate(100f, 0f)
        }

        // 2 - Blend(SrcOver, ColorFilter(Matrix(alpha*0.5), Dilate))
        run {
            val morph = ImageFilter.Dilate(5f, 5f, input = null)
            val matrix = floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 0.5f, 0f,
            )
            val matrixFilter = ImageFilter.ColorFilter(ColorFilter.Matrix(matrix), input = morph)
            val blend = ImageFilter.Blend(
                BlendMode.SRC_OVER, matrixFilter, ImageFilter.ColorFilter(ColorFilter.Matrix(matrix), input = null),
            )
            canvas.drawImage(fImage, imgRect, Paint(imageFilter = blend))
            canvas.translate(100f, 0f)
        }

        // 3 - Arithmetic approximated via Blend + Offset
        run {
            val matrix = floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 0.5f, 0f,
            )
            val matrixCF = ImageFilter.ColorFilter(ColorFilter.Matrix(matrix), input = null)
            val offsetFilter = ImageFilter.Offset(10f, 10f, input = matrixCF)
            val blend = ImageFilter.Blend(BlendMode.SRC_OVER, matrixCF, offsetFilter)
            canvas.drawImage(fImage, imgRect, Paint(imageFilter = blend))
            canvas.translate(100f, 0f)
        }

        // 4 - Blend(SrcIn, Blur(10,10)) with crop
        run {
            val blur = ImageFilter.Blur(10f, 10f, input = null)
            val blend = ImageFilter.Blend(
                BlendMode.SRC_IN, ImageFilter.Blur(1f, 1f, input = null), blur,
            )
            canvas.save()
            canvas.clipRect(Rect.fromXYWH(0f, 0f, 95f, 100f))
            canvas.drawImage(fImage, imgRect, Paint(imageFilter = blend))
            canvas.restore()
            canvas.translate(100f, 0f)
        }

        // 5 - MatrixConvolution([3,3] sharpen) over Dilate(5,5)
        run {
            val dilate = ImageFilter.Dilate(5f, 5f, input = null)
            val kernel = floatArrayOf(
                -1f, -1f, -1f,
                -1f, 7f, -1f,
                -1f, -1f, -1f,
            )
            val convolve = ImageFilter.MatrixConvolution(
                kernelSize = Size(3f, 3f),
                kernel = kernel, gain = 1f, bias = 0f,
                kernelOffset = org.graphiks.kanvas.types.Point(1f, 1f),
                tileMode = org.graphiks.kanvas.paint.TileMode.CLAMP,
                convolveAlpha = false, input = dilate,
            )
            canvas.drawImage(fImage, imgRect, Paint(imageFilter = convolve))
            canvas.translate(100f, 0f)
        }

        // 6 - ColorFilter(GREEN/SrcIn over BLUE/SrcIn) on red rect
        run {
            val cf1 = ImageFilter.ColorFilter(ColorFilter.Blend(Color.BLUE, BlendMode.SRC_IN), input = null)
            val cf2 = ImageFilter.ColorFilter(ColorFilter.Blend(Color.GREEN, BlendMode.SRC_IN), input = cf1)
            val paint = Paint(imageFilter = cf2, color = Color.RED)
            canvas.drawRect(Rect.fromXYWH(0f, 0f, 100f, 100f), paint)
            canvas.translate(100f, 0f)
        }
    }

    private fun makeStringImage(w: Int, h: Int, color: Color, x: Float, y: Float, textSize: Float, str: String): Image {
        val surface = Surface(w, h)
        surface.canvas {
            val paint = Paint(color = color)
            val font = Font(typeface, size = textSize)
            drawString(str, x, y, font, paint)
        }
        return surface.makeImageSnapshot()
    }
}
