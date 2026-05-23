package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/imagefilterstransformed.cpp::ImageFilterComposedTransform`
 * (registered name `"imagefilter_composed_transform"`, 512 × 512).
 *
 * Verifies that composing MatrixTransform + Offset filters produces the same
 * result regardless of composition order. Draws 4 quadrants that should match:
 *  - top-left  : `Offset(v) → MatrixTransform(rotate) → Offset(-v)`  (direct)
 *  - top-right : `Compose(Offset(-v), Compose(MatrixTransform, Offset(v)))` (early compose)
 *  - bot-left  : `Compose(Offset(-v), MatrixTransform(rotate(Offset(v))))` (late compose)
 *  - bot-right : `Compose(Offset(-v), Compose(MatrixTransform, Offset(v)))` (full compose)
 *
 * Rendered at fixed angle 70°  (upstream starts there to exercise skbug.com/40042261).
 *
 * C++ original (collapsed):
 * ```cpp
 * class ImageFilterComposedTransform : public skiagm::GM {
 *     ImageFilterComposedTransform() : fDegrees(70.f) {}
 *     SkString getName() const override { return "imagefilter_composed_transform"; }
 *     SkISize getISize() override { return SkISize::Make(512, 512); }
 *     void onDraw(SkCanvas* canvas) override {
 *         SkMatrix matrix = SkMatrix::RotateDeg(fDegrees);
 *         this->drawFilter(canvas, 0.f, 0.f, this->makeDirectFilter(matrix));
 *         this->drawFilter(canvas, 256.f, 0.f, this->makeEarlyComposeFilter(matrix));
 *         this->drawFilter(canvas, 0.f, 256.f, this->makeLateComposeFilter(matrix));
 *         this->drawFilter(canvas, 256.f, 256.f, this->makeFullComposeFilter(matrix));
 *     }
 * };
 * ```
 */
public class ImageFilterComposedTransformGM : GM() {

    private val fDegrees: Float = 70f
    private var fImage: SkImage? = null

    override fun getName(): String = "imagefilter_composed_transform"
    override fun getISize(): SkISize = SkISize.Make(512, 512)

    override fun onOnceBeforeDraw() {
        fImage = ToolUtils.GetResourceAsImage("images/mandrill_256.png")
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        // SkMatrix::RotateDeg → SkMatrix.MakeRotate
        val matrix = SkMatrix.MakeRotate(fDegrees)

        drawFilter(c, 0f, 0f, makeDirectFilter(matrix))
        drawFilter(c, 256f, 0f, makeEarlyComposeFilter(matrix))
        drawFilter(c, 0f, 256f, makeLateComposeFilter(matrix))
        drawFilter(c, 256f, 256f, makeFullComposeFilter(matrix))
    }

    private fun drawFilter(canvas: SkCanvas, tx: Float, ty: Float, filter: SkImageFilter?) {
        val image = fImage ?: return
        val paint = SkPaint().apply { imageFilter = filter }
        canvas.save()
        canvas.translate(tx, ty)
        canvas.clipRect(SkRect.MakeWH(256f, 256f))
        canvas.scale(0.5f, 0.5f)
        canvas.translate(128f, 128f)
        canvas.drawImage(image, 0f, 0f, SkSamplingOptions(SkFilterMode.kLinear), paint)
        canvas.restore()
    }

    // offset(matrix(offset))
    private fun makeDirectFilter(matrix: SkMatrix): SkImageFilter? {
        val image = fImage ?: return null
        val vx = image.width / 2f
        val vy = image.height / 2f
        var filter: SkImageFilter? = SkImageFilters.Offset(-vx, -vy, null)
        filter = SkImageFilters.MatrixTransform(matrix, SkSamplingOptions(SkFilterMode.kLinear), filter)
        filter = SkImageFilters.Offset(vx, vy, filter)
        return filter
    }

    // offset(compose(matrix, offset))
    private fun makeEarlyComposeFilter(matrix: SkMatrix): SkImageFilter? {
        val image = fImage ?: return null
        val vx = image.width / 2f
        val vy = image.height / 2f
        val offset: SkImageFilter = SkImageFilters.Offset(-vx, -vy, null)
        var filter: SkImageFilter? = SkImageFilters.MatrixTransform(
            matrix, SkSamplingOptions(SkFilterMode.kLinear), null
        )
        filter = SkImageFilters.Compose(filter, offset)
        filter = SkImageFilters.Offset(vx, vy, filter)
        return filter
    }

    // compose(offset, matrix(offset))
    private fun makeLateComposeFilter(matrix: SkMatrix): SkImageFilter? {
        val image = fImage ?: return null
        val vx = image.width / 2f
        val vy = image.height / 2f
        var filter: SkImageFilter? = SkImageFilters.Offset(-vx, -vy, null)
        filter = SkImageFilters.MatrixTransform(matrix, SkSamplingOptions(SkFilterMode.kLinear), filter)
        val offset: SkImageFilter = SkImageFilters.Offset(vx, vy, null)
        filter = SkImageFilters.Compose(offset, filter)
        return filter
    }

    // compose(offset, compose(matrix, offset))
    private fun makeFullComposeFilter(matrix: SkMatrix): SkImageFilter? {
        val image = fImage ?: return null
        val vx = image.width / 2f
        val vy = image.height / 2f
        val offset1: SkImageFilter = SkImageFilters.Offset(-vx, -vy, null)
        var filter: SkImageFilter? = SkImageFilters.MatrixTransform(
            matrix, SkSamplingOptions(SkFilterMode.kLinear), null
        )
        filter = SkImageFilters.Compose(filter, offset1)
        val offset2: SkImageFilter = SkImageFilters.Offset(vx, vy, null)
        filter = SkImageFilters.Compose(offset2, filter)
        return filter
    }
}
