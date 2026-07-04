package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/imagefilterstransformed.cpp::ImageFilterComposedTransform` (512 × 512).
 * Verifies that composing filters produces the same result regardless of composition order.
 * **Adaptation**: Upstream uses [SkImageFilters.MatrixTransform]; Kanvas does not have
 * a MatrixTransform filter. Dilate is used as a composable filter instead.
 * @see https://github.com/google/skia/blob/main/gm/imagefilterstransformed.cpp
 */
class ImageFilterComposedTransformGm : SkiaGm {
    override val name = "imagefilter_composed_transform"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    private var fImage: Image? = null

    override fun onOnceBeforeDraw(canvas: GmCanvas) {
        val bytes = javaClass.classLoader?.getResourceAsStream("images/mandrill_256.png")?.readAllBytes() ?: return
        fImage = Image.decode(bytes)
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val filter = ImageFilter.Dilate(4f, 4f, null) as ImageFilter

        drawFilter(canvas, 0f, 0f, makeDirectFilter())
        drawFilter(canvas, 256f, 0f, makeEarlyComposeFilter())
        drawFilter(canvas, 0f, 256f, makeLateComposeFilter())
        drawFilter(canvas, 256f, 256f, makeFullComposeFilter())
    }

    private fun drawFilter(canvas: GmCanvas, tx: Float, ty: Float, filter: ImageFilter?) {
        val image = fImage ?: return
        val paint = Paint(imageFilter = filter)
        canvas.save()
        canvas.translate(tx, ty)
        canvas.clipRect(Rect(0f, 0f, 256f, 256f))
        canvas.scale(0.5f, 0.5f)
        canvas.translate(128f, 128f)
        canvas.drawImage(image, Rect(0f, 0f, image.width.toFloat(), image.height.toFloat()), paint)
        canvas.restore()
    }

    private fun makeDirectFilter(): ImageFilter? {
        var filter: ImageFilter = ImageFilter.Offset(-50f, -50f, null)
        filter = ImageFilter.Dilate(4f, 4f, filter)
        filter = ImageFilter.Offset(50f, 50f, filter)
        return filter
    }

    private fun makeEarlyComposeFilter(): ImageFilter? {
        val offset: ImageFilter = ImageFilter.Offset(-50f, -50f, null)
        val dilate: ImageFilter = ImageFilter.Dilate(4f, 4f, null)
        val composed: ImageFilter = ImageFilter.Compose(dilate, offset)
        val result: ImageFilter = ImageFilter.Offset(50f, 50f, composed)
        return result
    }

    private fun makeLateComposeFilter(): ImageFilter? {
        val innerOffset: ImageFilter = ImageFilter.Offset(-50f, -50f, null)
        val dilate: ImageFilter = ImageFilter.Dilate(4f, 4f, innerOffset)
        val outerOffset: ImageFilter = ImageFilter.Offset(50f, 50f, null)
        return ImageFilter.Compose(outerOffset, dilate)
    }

    private fun makeFullComposeFilter(): ImageFilter? {
        val offset1: ImageFilter = ImageFilter.Offset(-50f, -50f, null)
        val dilate: ImageFilter = ImageFilter.Dilate(4f, 4f, null)
        val composed: ImageFilter = ImageFilter.Compose(dilate, offset1)
        val offset2: ImageFilter = ImageFilter.Offset(50f, 50f, null)
        return ImageFilter.Compose(offset2, composed)
    }
}
