package org.graphiks.kanvas.skia.gm.mesh

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.ReferenceStatusEntry
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.kanvas.types.Vertices
import org.graphiks.kanvas.types.VertexMode

/**
 * Port of Skia's `gm/mesh.cpp::MeshUniformsGM`.
 * Exercises a uniform-color block multiplied with per-vertex colors
 * via `uniformColor * meshColor` fragment lowering.
 * @see https://github.com/google/skia/blob/main/gm/mesh.cpp
 */
class CustomMeshUniformsGm : SkiaGm {
    override val name = "custommesh_uniforms"
    override val renderFamily = RenderFamily.MESH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 99.0
    override val referenceStatus = ReferenceStatusEntry(
        status = "untrustable",
        reason = "white placeholder reference does not represent Skia custommesh_uniforms mesh output",
    )
    override val width = 140
    override val height = 250

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rectL = 20f
        val rectT = 20f
        val rectR = 120f
        val rectB = 220f

        val positions = listOf(
            Point2F32(rectL, rectT),
            Point2F32(rectR, rectT),
            Point2F32(rectL, rectB),
            Point2F32(rectR, rectB),
        )

        // Uniform = (0.8, 0.35, 0.9, 0.85) multiplied with per-vertex colors
        fun applyUniform(r: Int, g: Int, b: Int, a: Int): ColorARGB {
            val ur = (r * 0.8f + 0.5f).toInt().coerceIn(0, 255)
            val ug = (g * 0.35f + 0.5f).toInt().coerceIn(0, 255)
            val ub = (b * 0.9f + 0.5f).toInt().coerceIn(0, 255)
            val ua = (a * 0.85f + 0.5f).toInt().coerceIn(0, 255)
            return ColorARGB.fromPackedUInt((ua.toUInt() shl 24) or (ur.toUInt() shl 16) or (ug.toUInt() shl 8) or ub.toUInt())
        }

        val vertexColors = listOf(
            applyUniform(255, 255, 255, 255), // white
            applyUniform(0, 0, 255, 255),     // blue
            applyUniform(0, 255, 0, 255),     // green
            applyUniform(255, 0, 0, 255),     // red
        )

        val verts = Vertices(
            mode = VertexMode.TRIANGLE_STRIP,
            positions = positions,
            colors = vertexColors,
        )
        canvas.drawVertices(verts, Paint(color = ColorARGB.White))
    }
}
