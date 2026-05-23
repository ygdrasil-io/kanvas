package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Port of upstream Skia's `gm/vertices.cpp::vertices_batching`
 * (`DEF_SIMPLE_GM(vertices_batching, canvas, 100, 500)`).
 *
 * Exercises the GPU back-end's triangle-mesh **batching** path.
 * Upstream converts a 9-vertex triangle-fan mesh (the `kMeshFan`
 * grid defined in `fill_mesh`) into 7 plain triangles via
 * `SkVertices::Builder`, then draws the same vertex data three
 * times per combination of (useShader × useTex × matrixVariant)
 * at progressively more complex transforms (identity, translate,
 * rotate+scale) to force the batcher to merge or split draw calls.
 *
 * The key APIs required :
 *  - `SkVertices::Builder` (Builder pattern with kHasColors +
 *    kHasTexCoords flags + custom index buffer) — **not yet
 *    exposed** in `:kanvas-skia`.
 *  - `SkCanvas.drawVertices(vertices, blendMode, paint)` —
 *    **not yet implemented** in `:kanvas-skia` (flag-planted as
 *    [TODO] with tag `STUB.DRAW_VERTICES`).
 *
 * Both missing pieces share the same root cause: the vertex-mesh
 * rasterisation pipeline lives in the GPU plan and is not wired
 * through the CPU-raster canvas dispatcher. This stub plants the
 * flag so the GM is tracked and discoverable via
 * `grep 'TODO("STUB.DRAW_VERTICES")'`.
 */
public class VerticesBatchingGM : GM() {

    override fun getName(): String = "vertices_batching"
    override fun getISize(): SkISize = SkISize.Make(100, 500)

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.DRAW_VERTICES")
    }
}
