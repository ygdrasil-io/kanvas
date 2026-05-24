package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/imagefilters.cpp::DEF_SIMPLE_GM(multiple_filters, …)`
 * (registered as `multiple_filters`, 415 × 210).
 *
 * Tests `SkCanvas::FilterSpan` via `SkCanvasPriv::ScaledBackdropLayer` —
 * a Canvas2D-specific extension that applies multiple image filters
 * simultaneously to the same saved layer (e.g. dilate + erode in one
 * pass, or drop-shadow + null). The `FilterSpan` / `saveLayerWithMultipleFilters`
 * path is not yet part of the public `:kanvas-skia` API.
 *
 * The body calls [SkCanvas.saveLayerWithMultipleFilters], the Kotlin slice
 * for upstream's private FilterSpan saveLayer path.
 */
public class MultipleFiltersGM : GM() {

    override fun getName(): String = "multiple_filters"
    override fun getISize(): SkISize = SkISize.Make(415, 210)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        ToolUtils.draw_checkerboard(c, 0xFF999999.toInt(), 0xFF666666.toInt(), 8)
        c.translate(5f, 5f)

        drawFilteredLayer(c, listOf(
            SkImageFilters.Dilate(5, 5, null),
            SkImageFilters.Erode(5, 5, null),
        ))

        drawFilteredLayer(c, listOf(
            SkImageFilters.DropShadowOnly(7f, 7f, 5f, 5f, SK_ColorBLUE, null),
            null,
        ))
    }

    private fun drawFilteredLayer(c: SkCanvas, filters: List<SkImageFilter?>) {
        val restorePaint = SkPaint().apply { alphaf = 0.5f }
        c.save()
        c.clipRect(SkRect.MakeLTRB(0f, 0f, 200f, 200f))
        c.saveLayerWithMultipleFilters(bounds = null, paint = restorePaint, filters = filters)

        val circlePaint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 20f
            color = SK_ColorGREEN
        }
        c.drawCircle(100f, 100f, 70f, circlePaint)
        c.restore()
        c.restore()
        c.translate(205f, 0f)
    }
}
