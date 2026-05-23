package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.foundation.SkBlendMode
import org.graphiks.math.SkMatrix
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions

/**
 * Port of Skia's `gm/imagefilters.cpp::DEF_SIMPLE_GM(imagefilters_xfermodes, …)`
 * (registered as `imagefilters_xfermodes`, 480 × 480).
 *
 * Tests that xfermode is applied *after* the image has been created and
 * filtered, not during layer creation — see skbug.com/40034872. Two blend
 * modes (SrcATop, DstIn) × two columns (no filter, identity MatrixTransform
 * filter) force the code path that creates a tmp layer.
 */
public class ImageFiltersXfermodesGM : GM() {

    override fun getName(): String = "imagefilters_xfermodes"
    override fun getISize(): SkISize = SkISize.Make(480, 480)

    private fun doDraw(canvas: SkCanvas, mode: SkBlendMode, imf: SkImageFilter?) {
        canvas.save()
        canvas.clipRect(SkRect.MakeWH(220f, 220f))
        canvas.saveLayer(null, null)
        canvas.drawColor(SK_ColorGREEN)

        val paint = SkPaint().apply { isAntiAlias = true }

        val r0 = SkRect.MakeXYWH(10f, 60f, 200f, 100f)
        val r1 = SkRect.MakeXYWH(60f, 10f, 100f, 200f)

        paint.color = SK_ColorRED
        canvas.drawOval(r0, paint)

        paint.color = 0x660000FF.toInt()
        paint.imageFilter = imf
        paint.blendMode = mode
        canvas.drawOval(r1, paint)

        canvas.restore()
        canvas.restore()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(10f, 10f)

        // An identity MatrixTransform filter triggers the tmp-layer code path.
        val imf: SkImageFilter? = SkImageFilters.MatrixTransform(
            SkMatrix.Identity,
            SkSamplingOptions.Default,
            null,
        )

        val modes = arrayOf(SkBlendMode.kSrcATop, SkBlendMode.kDstIn)
        for (mode in modes) {
            c.save()
            doDraw(c, mode, null)
            c.translate(240f, 0f)
            doDraw(c, mode, imf)
            c.restore()
            c.translate(0f, 240f)
        }
    }
}
