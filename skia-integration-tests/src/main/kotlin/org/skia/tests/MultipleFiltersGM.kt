package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SkISize
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint

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
 * The body intentionally calls [SkCanvas.saveLayerWithMultipleFilters] to
 * document the missing call-sites; the test is @Disabled until the stub is
 * implemented.
 */
public class MultipleFiltersGM : GM() {

    override fun getName(): String = "multiple_filters"
    override fun getISize(): SkISize = SkISize.Make(415, 210)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        // TODO("STUB.IFX.MULTIPLE_FILTERS_SPAN") — draw_checkerboard background
        c.translate(5f, 5f)

        // Case 1: two non-null filters (dilate + erode)
        val restorePaint1 = SkPaint().apply { alpha = 128 }
        c.saveLayerWithMultipleFilters(
            bounds = null,
            paint = restorePaint1,
            filters = listOf(
                SkImageFilters.Dilate(5, 5, null),
                SkImageFilters.Erode(5, 5, null),
            ),
        )
        val circlePaint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 20f
            color = SK_ColorGREEN
        }
        c.drawCircle(100f, 100f, 70f, circlePaint)
        c.restore()
        c.translate(205f, 0f)

        // Case 2: one null filter (mirrors Canvas2D drop-shadow-only use-case)
        val restorePaint2 = SkPaint().apply { alpha = 128 }
        c.saveLayerWithMultipleFilters(
            bounds = null,
            paint = restorePaint2,
            filters = listOf(
                SkImageFilters.DropShadowOnly(7f, 7f, 5f, 5f, 0xFF0000FF.toInt(), null),
                null,
            ),
        )
        c.drawCircle(100f, 100f, 70f, circlePaint)
        c.restore()
    }
}
