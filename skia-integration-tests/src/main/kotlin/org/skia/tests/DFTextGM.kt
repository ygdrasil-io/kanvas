package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder for `gm/dftext.cpp::DFTextGM` (1235 × 700, GPU-only).
 *
 * Exercises `SkSurfaceProps` with the legacy distance-field-text
 * flag (`kUseDeviceIndependentFonts_Flag`) — under that flag the
 * GPU backend rasterises glyphs via a signed-distance-field shader
 * instead of the per-size glyph atlas. This GM is **GPU-only** in
 * upstream Skia : the `original-888/dftext.png` reference was
 * rendered by a Ganesh sink, not a raster sink, and there is no
 * raster fallback for the SDF code path (which lives entirely in
 * the GPU pipeline).
 *
 * **kanvas-skia** : no SDF text yet (deferred — depends on the GPU
 * text shaders + atlas page management). The associated test is
 * `@Ignore`'d ; this stub keeps the GM class registered so the
 * cross-backend ratchet can pick it up once the SDF path lands.
 */
public class DFTextGM : GM() {
    override fun getName(): String = "dftext"
    override fun getISize(): SkISize = SkISize.Make(1235, 700)
    override fun onDraw(canvas: SkCanvas?) {
        // TODO : implement once distance-field text is wired through
        //   SkSurfaceProps.kUseDeviceIndependentFonts_Flag on GPU.
    }
}
