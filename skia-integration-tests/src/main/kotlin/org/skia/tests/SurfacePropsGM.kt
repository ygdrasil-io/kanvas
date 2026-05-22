package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Stub port of Skia's `gm/surfaceprops.cpp::SurfacePropsGM`
 * (registered as `surfaceprops` / `surfaceprops_df`, 500 x 500).
 *
 * Upstream creates an offscreen [org.skia.foundation.SkSurface]
 * with a custom [org.skia.foundation.SkSurfaceProps]
 * (`kUseDistanceFieldFonts_Flag` when the `_df` variant is
 * requested) and verifies that text rendered into that surface
 * picks up the LCD / distance-field hinting hint. The point is
 * to verify the surface-level prop propagation through to the
 * glyph rasteriser.
 *
 * `:kanvas-skia` does not yet expose
 * `SkSurface.MakeRenderTarget(... , SkSurfaceProps)` (only the
 * default-props constructor), so the variant `_df` reference
 * image cannot be reproduced.
 *
 * TODO: missing API -- `SkSurface.MakeRenderTarget` overload
 * taking `SkSurfaceProps`. Flag-planting stub: empty draw,
 * fixed size.
 */
public class SurfacePropsGM(
    private val useDistanceField: Boolean = false,
) : GM() {

    override fun getName(): String =
        "surfaceprops" + if (useDistanceField) "_df" else ""

    override fun getISize(): SkISize = SkISize.Make(500, 500)

    override fun onDraw(canvas: SkCanvas?) {
        // TODO: missing API -- SkSurface.MakeRenderTarget(SkSurfaceProps).
    }
}
