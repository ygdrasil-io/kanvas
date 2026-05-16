package org.skia.tests

import org.skia.core.SaveLayerFlags
import org.skia.core.SaveLayerRec
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorCYAN
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorMAGENTA
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/backdrop_imagefilter_croprect.cpp::
 * DEF_SIMPLE_GM(backdrop_imagefilter_croprect, 600, 500)`.
 *
 * Validates that an image filter's crop rect is honoured in both
 * "primary save layer" mode (filter applied at restore) and
 * "backdrop mode" (filter applied on layer init). The filter here
 * is a colour-inverting matrix [SkColorFilters.Matrix] wrapped in
 * [SkImageFilters.ColorFilter] with an explicit crop.
 *
 * Expected output : a small cyan rectangle above a much larger
 * magenta rectangle, with no red around the cyan one and no green
 * inside the magenta one — the inverted-colour rect (red→cyan,
 * green→magenta) only appears within the filter's crop bounds.
 */
public class BackdropImagefilterCroprectGM : GM() {

    override fun getName(): String = "backdrop_imagefilter_croprect"
    override fun getISize(): SkISize = SkISize.Make(600, 500)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        drawBackdropFilterGm(c, 0f, 0f) { crop ->
            makeInvertFilter(crop)
        }
    }
}

// -- shared helpers ---------------------------------------------------

@Suppress("UNUSED_PARAMETER")
internal fun makeInvertFilter(crop: org.graphiks.math.SkIRect?): SkImageFilter {
    val matrix = floatArrayOf(
        -1f, 0f, 0f, 0f, 1f,
        0f, -1f, 0f, 0f, 1f,
        0f, 0f, -1f, 0f, 1f,
        0f, 0f, 0f, 1f, 0f,
    )
    val cf = SkImageFilters.ColorFilter(SkColorFilters.Matrix(matrix), null)
    return if (crop != null) {
        SkImageFilters.Crop(SkRect.Make(crop), input = cf)
    } else cf
}

internal fun makeBlurFilter(crop: org.graphiks.math.SkIRect?): SkImageFilter? {
    // Different sigmas to make rotated CTM visible
    return SkImageFilters.Blur(16f, 4f, org.skia.foundation.SkTileMode.kDecal, null, crop)
}

/**
 * Mirrors the C++ `draw_backdrop_filter_gm` helper. Draws two stacked
 * rows : the top row applies the image filter as a normal saveLayer
 * paint filter ; the bottom row applies it as a backdrop filter
 * (via [SaveLayerRec.backdrop] + the `kInitWithPrevious` flag).
 */
internal fun drawBackdropFilterGm(
    canvas: SkCanvas,
    outsetX: Float,
    outsetY: Float,
    factory: (org.graphiks.math.SkIRect?) -> SkImageFilter?,
) {
    val origin = SkPoint(150f, 150f)
    val clip = SkRect.MakeXYWH(-50f, -50f, 400f, 150f)
    val cropInLocal = SkRect.MakeLTRB(50f, 10f, 250f, 40f)
    val cropRect = cropInLocal.makeOutset(outsetX, outsetY).roundOut()
    val imageFilter = factory(cropRect)

    val p = SkPaint()
    var oy = origin.fY
    for (i in 0 until 2) {
        canvas.save()
        canvas.translate(origin.fX, oy)
        canvas.clipRect(clip)

        if (i == 0) {
            val imfPaint = SkPaint().apply { this.imageFilter = imageFilter }
            canvas.saveLayer(null, imfPaint)
        }

        p.color = if (i == 0) SK_ColorCYAN else SK_ColorMAGENTA
        canvas.drawPaint(p)
        p.color = if (i == 0) SK_ColorRED else SK_ColorGREEN
        canvas.drawRect(cropInLocal, p)

        if (i == 1) {
            // kInitWithPrevious_SaveLayerFlag — mirror its value (1 << 1 == 2)
            // even though kanvas-skia ignores the bit (the backdrop seeds the
            // layer via [SaveLayerRec.backdrop] regardless).
            canvas.saveLayer(SaveLayerRec(
                bounds = null,
                paint = null,
                backdrop = imageFilter,
                flags = INIT_WITH_PREVIOUS_FLAG,
            ))
        }

        canvas.restore()
        canvas.restore()
        oy += 150f
    }
}

private const val INIT_WITH_PREVIOUS_FLAG: SaveLayerFlags = 1 shl 1
