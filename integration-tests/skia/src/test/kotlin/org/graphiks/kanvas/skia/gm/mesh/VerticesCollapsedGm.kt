package org.graphiks.kanvas.skia.gm.mesh

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Vertices
import org.graphiks.kanvas.types.VertexMode

/**
 * Port of Skia's `gm/vertices.cpp::vertices_collapsed`.
 * Stress test for drawVertices with collapsed texture coordinates.
 * @see https://github.com/google/skia/blob/main/gm/vertices.cpp
 */
class VerticesCollapsedGm : SkiaGm {
    override val name = "vertices_collapsed"
    override val renderFamily = RenderFamily.MESH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 50
    override val height = 50

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)

        val pixels = ByteArray(4)
        pixels[0] = 0x00.toByte()
        pixels[1] = 0xFF.toByte()
        pixels[2] = 0x00.toByte()
        pixels[3] = 0xFF.toByte()
        val src = Image.fromPixels(1, 1, pixels, ColorType.RGBA_8888, "green")
        val shader = src.makeShader()

        val positions = listOf(
            Point(5f, 5f), Point(45f, 5f),
            Point(45f, 45f), Point(5f, 45f),
        )
        val texs = listOf(
            Point(0f, 0f), Point(0f, 0f),
            Point(0f, 0f), Point(0f, 0f),
        )
        val indices = listOf(0, 1, 2, 2, 3, 0)

        val verts = Vertices(
            mode = VertexMode.TRIANGLES,
            positions = positions,
            texCoords = texs,
            indices = indices,
        )
        val paint = Paint(
            shader = Shader.WithLocalMatrix(shader, Matrix33.identity()),
            blendMode = BlendMode.DST,
        )
        canvas.drawVertices(verts, paint)
    }
}
