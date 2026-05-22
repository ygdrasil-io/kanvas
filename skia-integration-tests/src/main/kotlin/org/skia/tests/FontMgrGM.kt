package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder for `gm/fontmgr.cpp::FontMgrGM` (1536 × 768).
 *
 * Renders one row per font family discovered by the default
 * `SkFontMgr`, then one column per style within each family. Pure
 * `SkFontMgr` enumeration probe.
 *
 * **API gap** : same as [FontMgrBoundsGM] — `JvmAwtFontMgr`
 * enumerates the system JVM fonts (Helvetica / Arial / …) rather
 * than the bundled Liberation set used by upstream's
 * `LiberationFontMgr`. PNG comparison against `fontmgr_iter.png`
 * would be nonsensical until the portable manager lands.
 */
public class FontMgrGM : GM() {
    override fun getName(): String = "fontmgr_iter"
    override fun getISize(): SkISize = SkISize.Make(1536, 768)
    override fun onDraw(canvas: SkCanvas?) {
        // TODO : port once portable Liberation SkFontMgr exists.
    }
}
