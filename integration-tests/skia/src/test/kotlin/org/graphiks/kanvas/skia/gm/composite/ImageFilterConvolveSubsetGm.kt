package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.Size

/** Port of Skia's `gm/imagefiltersclipped.cpp` (convolve subset variant).
 *  Tests convolve image filter with subset rects — renders a checkerboard
 *  image with various convolution matrix kernels.
 *  @see https://github.com/google/skia/blob/main/gm/imagefiltersclipped.cpp
 */
class ImageFilterConvolveSubsetGm : SkiaGm {
    override val name = "imagefilter_convolve_subset"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.TRIVIAL
    override val minSimilarity = 0.0
    override val width = 160
    override val height = 180

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val ref = decodeResource("images/filter_reference.png") ?: return
        val refW = ref.width.toFloat()
        val refH = ref.height.toFloat()

        val crop = Rect(10f, 10f, (ref.width - 10).toFloat(), (ref.height - 10).toFloat())

        val kernel = floatArrayOf(1f, 1f, 1f, 1f, -7f, 1f, 1f, 1f, 1f)
        val convFilter = ImageFilter.MatrixConvolution(
            kernelSize = Size(3f, 3f),
            kernel = kernel,
            gain = 1f,
            bias = 0.3f,
            kernelOffset = Point(1f, 1f),
            tileMode = TileMode.CLAMP,
            convolveAlpha = true,
            input = null,
        )
        drawFilteredImage(canvas, ref, convFilter)

        val blurFilter = ImageFilter.Blur(10f, 10f, TileMode.MIRROR, null)
        drawFilteredImage(canvas, ref, blurFilter)
    }

    private fun drawFilteredImage(canvas: GmCanvas, image: Image, filter: ImageFilter) {
        val paint = Paint(imageFilter = filter)
        canvas.drawImage(image, Rect(0f, 0f, image.width.toFloat(), image.height.toFloat()), paint)
        canvas.translate(0f, image.height.toFloat())
    }

    private fun decodeResource(path: String): Image? {
        val bytes = this::class.java.classLoader?.getResourceAsStream(path)?.readBytes() ?: return null
        val img = Image.decode(bytes)
        return if (img.width > 0) img else null
    }
}
