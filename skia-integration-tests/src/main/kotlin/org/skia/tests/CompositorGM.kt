package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder port of upstream Skia
 * `gm/compositor_quads.cpp::CompositorGM` (sibling of the already-
 * ported `CompositorQuadsImageGM`).
 *
 * Original is the chromium-compositor batched-quad GM matrix —
 * `SkCanvas.experimental_DrawEdgeAAQuad` + variants under several
 * draw families. Already partially covered by `CompositorQuadsImageGM`.
 *
 * TODO: full port — the entire `compositor_quads.cpp` matrix
 * (color/texture/quad/anti-alias permutations). Flag-planting stub.
 */
public class CompositorGM : GM() {
    override fun getName(): String = "compositor"
    override fun getISize(): SkISize = SkISize.Make(800, 800)

    override fun onDraw(canvas: SkCanvas?) {
        // No-op : partial coverage already in CompositorQuadsImageGM.
        // TODO: full compositor_quads.cpp matrix.
    }
}
