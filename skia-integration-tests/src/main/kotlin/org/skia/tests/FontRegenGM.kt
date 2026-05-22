package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder for `gm/fontregen.cpp::FontRegenGM` (kSize × kSize where
 * kSize = 512, GPU-only).
 *
 * Forces a glyph atlas regeneration mid-draw by drawing > atlas
 * capacity worth of unique glyphs in a single frame. Exercises the
 * GPU text path's mid-frame atlas flush / recompose.
 *
 * **kanvas-skia** : the `fontregen.png` reference is GPU-rendered ;
 * raster doesn't have a finite glyph atlas to overflow. Stub keeps
 * the class registered ; tests are `@Ignore`'d.
 */
public class FontRegenGM : GM() {
    override fun getName(): String = "fontregen"
    override fun getISize(): SkISize = SkISize.Make(512, 512)
    override fun onDraw(canvas: SkCanvas?) {
        // TODO : port once GPU glyph atlas with overflow handling is
        //   exposed (raster path is N/A).
    }
}
