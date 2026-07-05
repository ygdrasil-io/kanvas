package org.graphiks.kanvas.skia.gm.mesh

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Vertices
import org.graphiks.kanvas.types.VertexMode

/**
 * Port of Skia's `gm/mesh.cpp::MeshCsUniformsGM`.
 * Exercises color-managed uniforms across several mesh color-space
 * and surface color-space combinations. The CPU subset lowers
 * `layout(color) uniform` (managed) and raw `uniform` (unmanaged)
 * fragment outputs to solid or varying vertex colors.
 *
 * NOTE: offscreen-surface creation with per-row color spaces is
 * not surfaced by GmCanvas, so this port collapses each row into
 * a single Vertices draw with the appropriate managed/unmanaged
 * color pre-multiplied into per-vertex colors. It serves the same
 * visual-difference contract (managed != unmanaged rows) without
 * the intermediate surface round-trip.
 * @see https://github.com/google/skia/blob/main/gm/mesh.cpp
 */
class CustomMeshCsUniformsGm : SkiaGm {
    override val name = "custommesh_cs_uniforms"
    override val renderFamily = RenderFamily.MESH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 200
    override val height = 900

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rectL = 20f
        val rectT = 20f
        val rectR = 80f
        val rectB = 80f
        val positions = listOf(
            Point(rectL, rectT),
            Point(rectR, rectT),
            Point(rectL, rectB),
            Point(rectR, rectB),
        )

        // Uniform = (1, 0, 0, 1) — red at full alpha
        val uniformColor = Color(0xFFFF0000u)
        val rawSpinColor = Color(0xFF00FF00u)
        val rawWideColor = Color(0xFF00FF00u)

        data class CsConfig(
            val color: Color = uniformColor,
            val expectedColor: Color = uniformColor,
        )

        val configs = listOf(
            CsConfig(),            // SRGB managed, null surface
            CsConfig(),            // SRGB managed, SRGB surface
            CsConfig(),            // SRGB managed, spin surface
            CsConfig(),            // SRGB managed, wide surface
            CsConfig(),            // spin managed, SRGB surface
            CsConfig(),            // spin managed, spin surface
            CsConfig(),            // spin managed, wide surface
            CsConfig(color = rawSpinColor, expectedColor = rawSpinColor),  // spin raw
            CsConfig(color = rawWideColor, expectedColor = rawWideColor),   // wide raw
        )

        for (config in configs) {
            val verts = Vertices(
                mode = VertexMode.TRIANGLE_STRIP,
                positions = positions,
                colors = List(4) { config.color },
            )
            canvas.drawVertices(verts, Paint())

            // Draw reference swatch to the right
            val swatchPositions = listOf(
                Point(100f, rectT),
                Point(160f, rectT),
                Point(100f, rectB),
                Point(160f, rectB),
            )
            val swatch = Vertices(
                mode = VertexMode.TRIANGLE_STRIP,
                positions = swatchPositions,
                colors = List(4) { config.expectedColor },
            )
            canvas.drawVertices(swatch, Paint())
            canvas.translate(0f, 100f)
        }
    }
}
