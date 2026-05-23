package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Stub port of Skia's `gm/imagefilterstext.cpp::ImageFiltersTextBaseGM`
 * (registered as `textfilter_<suffix>`, 800 x 500).
 *
 * Upstream paints a grid of small text snippets, each composited
 * through a different [SkImageFilter] (blur, drop-shadow, offset,
 * colour-matrix, lighting). The point of the GM is to verify that
 * image-filter sampling on text-rasterised glyphs preserves stem
 * widths / antialias coverage. The dispatch needs `saveLayer`
 * with an `imageFilter` paint, full glyph-cache pixel-readback
 * to feed the filter, and the filter sub-classes themselves --
 * which are partially implemented in `:kanvas-skia` but not yet
 * wired through `SkPaint.imageFilter` for text runs.
 *
 * TODO: missing API -- `SkPaint.imageFilter` for text glyphs and
 * the GM's variadic-suffix `SubclassGM` machinery (per-instance
 * sub-class providing its own filter and label). Flag-planting
 * stub: empty draw, fixed size.
 */
public class ImageFiltersTextBaseGM(
    private val suffix: String = "blur",
) : GM() {

    override fun getName(): String = "textfilter_$suffix"
    override fun getISize(): SkISize = SkISize.Make(800, 500)

    override fun onDraw(canvas: SkCanvas?) {
        // TODO: missing API -- SkPaint.imageFilter for text glyph runs.
    }
}
