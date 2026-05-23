package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkColorMatrix
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/colorfilterimagefilter.cpp::DEF_SIMPLE_GM(colorfilterimagefilter_layer, …, 32, 32)`.
 *
 * Opens a `saveLayer` whose paint carries an image-filter built from a
 * grayscale-saturation colour matrix filter. Everything drawn inside the
 * layer is composited through the filter before landing on the destination.
 * The only draw inside the layer is `canvas->clear(SK_ColorRED)`, so the
 * output is a fully desaturated red (i.e. 18 % gray) 32 × 32 square.
 *
 * C++ original :
 * ```cpp
 * DEF_SIMPLE_GM(colorfilterimagefilter_layer, canvas, 32, 32) {
 *     SkAutoCanvasRestore autoCanvasRestore(canvas, false);
 *     SkColorMatrix cm;
 *     cm.setSaturation(0.0f);
 *     sk_sp<SkColorFilter> cf(SkColorFilters::Matrix(cm));
 *     SkPaint p;
 *     p.setImageFilter(SkImageFilters::ColorFilter(std::move(cf), nullptr));
 *     canvas->saveLayer(nullptr, &p);
 *     canvas->clear(SK_ColorRED);
 * }
 * ```
 *
 * All APIs used here are implemented in `:kanvas-skia` :
 *  - [SkColorMatrix.setSaturation] (`:math`)
 *  - [SkColorFilters.Matrix] (20-float and SkColorMatrix overloads)
 *  - [SkImageFilters.ColorFilter]
 *  - [SkCanvas.saveLayer] with a paint that carries an image filter
 *  - [SkCanvas.drawColor] (equivalent to `canvas->clear(...)`)
 */
public class ColorFilterImageFilterLayerGM : GM() {

    override fun getName(): String = "colorfilterimagefilter_layer"

    override fun getISize(): SkISize = SkISize.Make(32, 32)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // SkColorMatrix cm; cm.setSaturation(0.0f);
        val cm = SkColorMatrix()
        cm.setSaturation(0.0f)

        // sk_sp<SkColorFilter> cf(SkColorFilters::Matrix(cm));
        val cf = SkColorFilters.Matrix(cm)

        // SkPaint p; p.setImageFilter(SkImageFilters::ColorFilter(std::move(cf), nullptr));
        val p = SkPaint()
        p.imageFilter = SkImageFilters.ColorFilter(cf, null)

        // canvas->saveLayer(nullptr, &p);
        c.saveLayer(null, p)

        // canvas->clear(SK_ColorRED);
        c.drawColor(SK_ColorRED)

        // SkAutoCanvasRestore restores on scope exit — in Kotlin saveLayer
        // without explicit restore is fine as long as we let the GM harness
        // call restoreToCount(0). Since the harness wraps onDraw in its own
        // save/restore pair the layer is still composited. For correctness we
        // restore explicitly.
        c.restore()
    }
}
