package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder for Skia's `gm/mac_aa_explorer.cpp::MacAAFontsGM` (1024 × 768).
 *
 * Upstream renders glyphs through CoreText's `CTFontDrawGlyphs` at three
 * CG colour types (`kRGBA_8888`, `kGray_8`, `kAlpha_8`) with the
 * `CGContextSetShouldSmoothFonts(true/false)` axis, comparing CG's
 * native font-smoothing output against Skia's rasteriser. The test is
 * gated on `#ifdef SK_BUILD_FOR_MAC` and pulls in
 * `<ApplicationServices/ApplicationServices.h>` —
 * **fundamentally not portable** to kanvas-skia, which has no
 * CoreText / CoreGraphics dependency on any platform.
 *
 * The visual reference (`macaatest.png`) is preserved in
 * `original-888/` for historical traceability ; the placeholder
 * implementation here just clears to white so the test harness can
 * still find the GM by name (the corresponding test is `@Ignore`d).
 */
public class MacAAFontsGM : GM() {

    override fun getName(): String = "macaatest"
    override fun getISize(): SkISize = SkISize.Make(1024, 768)

    override fun onDraw(canvas: SkCanvas?) {
        // TODO(O4) : not portable — CoreText / CoreGraphics bridge has
        // no kanvas-skia equivalent. Leave a blank canvas to avoid
        // crashing the test harness ; the @Ignored test enforces that
        // we don't accidentally start scoring against the reference.
    }
}
