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
 * The smaller [VerticesBatchingGM] slice now exercises the existing
 * raster [SkCanvas.drawVertices] path. This broader GM still needs a
 * faithful port of upstream's blend-mode grid, per-vertex colour
 * interpolation, per-vertex UV shader sampling, and scaled variant.
 *
 * TODO: complete the full `gm/vertices.cpp::VerticesGM` port.
 * Flag-planting stub: empty draw, fixed size.
 */
public class VerticesGM(
    private val shaderScale: Int = 1,
) : GM() {

    override fun getName(): String =
        "vertices" + if (shaderScale != 1) "_scaled" else ""

    override fun getISize(): SkISize = SkISize.Make(975, 1175)

    override fun onDraw(canvas: SkCanvas?) {
        // TODO: complete the full upstream vertices GM.
    }
}
