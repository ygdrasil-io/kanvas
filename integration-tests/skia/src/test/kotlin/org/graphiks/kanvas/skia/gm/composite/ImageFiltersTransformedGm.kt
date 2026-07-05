package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/imagefilterstransformed.cpp::ImageFilterMatrixWLocalMatrix` (512 × 512).
 * **Adaptation**: Upstream uses [SkImageFilters.MatrixTransform] and
 * [SkImageFilter.makeWithLocalMatrix]; Kanvas does not have either.
 * A Dilate + Offset chain is used instead.
 * @see https://github.com/google/skia/blob/main/gm/imagefilterstransformed.cpp
 */
class ImageFiltersTransformedGm : SkiaGm {
    override val name = "imagefilter_matrix_localmatrix"
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
        val image = fImage ?: return

        val dilate: ImageFilter = ImageFilter.Dilate(4f, 4f, null)
        val filter: ImageFilter = ImageFilter.Offset(10f, 10f, dilate)

        val paint = Paint(imageFilter = filter)
        canvas.drawImage(image, Rect(128f, 128f, 384f, 384f), paint)
    }
}
