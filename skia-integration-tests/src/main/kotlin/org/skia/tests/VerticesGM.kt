package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Stub port of Skia's `gm/vertices.cpp::VerticesGM` (registered
 * as `vertices`, 975 x 1175).
 *
 * Upstream constructs an [org.skia.core.SkVertices] mesh with
 * per-vertex colours + texture coords, then draws it through
 * [SkCanvas.drawVertices] under every [SkBlendMode]. The point
 * is to verify the GPU vertex-mesh pipeline +
 * per-vertex-colour interpolation + per-vertex-uv shader
 * sampling together.
 *
 * `:kanvas-skia` does not implement [SkCanvas.drawVertices] at
 * all -- the mesh primitive lives in the GPU plan but is not
 * wired through the canvas dispatcher. The
 * [VerticesPerspectiveGM] / [VerticesCollapsedGM] ports cover
 * the degenerate-mesh cases that don't need the full pipeline,
 * but the base `vertices` reference image needs the real
 * `drawVertices` call.
 *
 * TODO: missing API -- `SkCanvas.drawVertices(vertices, blendMode, paint)`.
 * Flag-planting stub: empty draw, fixed size.
 */
public class VerticesGM(
    private val shaderScale: Int = 1,
) : GM() {

    override fun getName(): String =
        "vertices" + if (shaderScale != 1) "_scaled" else ""

    override fun getISize(): SkISize = SkISize.Make(975, 1175)

    override fun onDraw(canvas: SkCanvas?) {
        // TODO: missing API -- SkCanvas.drawVertices.
    }
}
