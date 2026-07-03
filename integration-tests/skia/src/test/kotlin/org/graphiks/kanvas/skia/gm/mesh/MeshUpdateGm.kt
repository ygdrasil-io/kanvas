package org.graphiks.kanvas.skia.gm.mesh

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Vertices
import org.graphiks.kanvas.types.VertexMode

/**
 * Port of Skia's `gm/mesh.cpp::MeshUpdateGM`.
 * Exercises vertex buffer `update()` semantics by writing new
 * vertex data after allocation and drawing with non-indexed,
 * indexed, and index-swapped stripes.
 * @see https://github.com/google/skia/blob/main/gm/mesh.cpp
 */
class MeshUpdateGm : SkiaGm {
    override val name = "mesh_updates"
    override val renderFamily = RenderFamily.MESH
    override val minSimilarity = 0.0
    override val width = 270
    override val height = 490

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawStrip(canvas, 20f, Color(0xFF0055FFu), 15f, 95f, 45f, 125f, false)
        drawStrip(canvas, 170f, Color(0xFFFF6A00u), 15f, 95f, 45f, 125f, true)
        drawStrip(canvas, 320f, Color(0xFF0A9F43u), 15f, 95f, 45f, 125f, true, indexSwap = true)
    }

    private fun drawStrip(
        canvas: GmCanvas,
        yOffset: Float,
        color: Color,
        leftStart: Float,
        rightStart: Float,
        leftUpdated: Float,
        rightUpdated: Float,
        useIndexed: Boolean,
        indexSwap: Boolean = false,
    ) {
        val bottom0 = yOffset + 110f

        val positions = listOf(
            Point(leftUpdated, yOffset),
            Point(rightUpdated, yOffset),
            Point(leftStart, bottom0),
            Point(rightStart, bottom0),
        )
        val indices = if (indexSwap) listOf(0, 2, 1, 1, 2, 3) else listOf(0, 1, 2, 1, 3, 2)

        val verts = if (useIndexed) {
            Vertices(
                mode = VertexMode.TRIANGLES,
                positions = positions,
                indices = indices,
            )
        } else {
            Vertices(
                mode = VertexMode.TRIANGLE_STRIP,
                positions = positions,
            )
        }
        canvas.drawVertices(verts, Paint(color = color))
    }
}
