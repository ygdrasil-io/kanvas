package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Stub port of Skia's `gm/rsxtext.cpp::RSXShaderGM`
 * (`rsx_blob_shader`, 882 x 882).
 *
 * Upstream builds a `kRSXform`-flavoured text blob (one RSX matrix per
 * glyph, distributed horizontally) and renders it 4 times with various
 * `(localMatrix, outerLocalMatrix)` shader combinations -- the shader
 * is a 30 x 30 yellow-with-inset-green tile that should repeat under
 * the text.
 *
 * `:kanvas-skia`'s `SkTextBlobBuilder` does not currently expose the
 * `allocRunRSXform` overload, so the per-glyph RSX positioning can't
 * be reproduced. The GM is kept as a stub for class-shape parity ;
 * the matching test is `@Ignore`d.
 */
public class RSXShaderGM : GM() {

    override fun getName(): String = "rsx_blob_shader"

    override fun getISize(): SkISize = SkISize.Make(882, 882)

    override fun onDraw(canvas: SkCanvas?) {
        // No-op : SkTextBlobBuilder.allocRunRSXform not implemented.
    }
}
