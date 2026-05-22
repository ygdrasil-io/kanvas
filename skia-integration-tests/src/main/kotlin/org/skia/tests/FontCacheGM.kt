package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder for `gm/fontcache.cpp::FontCacheGM` (kSize × kSize where
 * kSize = 1280, GPU-only).
 *
 * Forces a glyph-atlas eviction by drawing two character runs at
 * many sizes — the second run revisits sizes that should have been
 * cached, exercising the GPU `GrTextStrike` LRU eviction policy.
 *
 * **kanvas-skia** : the `fontcache.png` reference is GPU-rendered ;
 * raster has no equivalent (the AWT/FreeType scaler caches per-size
 * glyphs invisibly with no exposed eviction probe). Stub keeps the
 * class registered.
 */
public class FontCacheGM : GM() {
    override fun getName(): String = "fontcache"
    override fun getISize(): SkISize = SkISize.Make(1280, 1280)
    override fun onDraw(canvas: SkCanvas?) {
        // TODO : port once GPU glyph atlas with eviction tracking
        //   is hooked up (or skip permanently — raster-only doesn't
        //   match this GM's intent).
    }
}
