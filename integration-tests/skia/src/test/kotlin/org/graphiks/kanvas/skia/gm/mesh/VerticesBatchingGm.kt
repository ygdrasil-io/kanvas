package org.graphiks.kanvas.skia.gm.mesh

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Vertices
import org.graphiks.kanvas.types.VertexMode
import kotlin.random.Random

/**
 * Port of upstream Skia's `gm/vertices.cpp::vertices_batching`.
 * Exercises indexed triangle-fan vertices with shader, colors, and matrix transforms.
 * @see https://github.com/google/skia/blob/main/gm/vertices.cpp
 */
class VerticesBatchingGm : SkiaGm {
    override val name = "vertices_batching"
    override val renderFamily = RenderFamily.MESH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 100
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawBatching(canvas)
        canvas.translate(50f, 0f)
        drawBatching(canvas)
    }

    private fun drawBatching(canvas: GmCanvas) {
        val indices = listOf(4, 0, 1, 2, 5, 8, 7, 6, 3, 0)
        val matrices = listOf(
            Matrix33.identity(),
            Matrix33.translate(0f, 40f),
            Matrix33.translate(0f, 80f) * Matrix33.scale(1.2f, 0.8f) * Matrix33.rotate(45f),
        )

        val shader = Shader.LinearGradient(
            start = Point(K_SHADER_SIZE / 4f, 0f),
            end = Point(3f * K_SHADER_SIZE / 4f, K_SHADER_SIZE),
            stops = listOf(
                GradientStop(0f, Color.RED),
                GradientStop(1f / 6f, Color(0xFF00FFFFu)),
                GradientStop(2f / 6f, Color.GREEN),
                GradientStop(3f / 6f, Color.WHITE),
                GradientStop(4f / 6f, Color(0xFFFF00FFu)),
                GradientStop(5f / 6f, Color.BLUE),
                GradientStop(1f, Color(0xFFFFFF00u)),
            ),
            tileMode = TileMode.MIRROR,
        )

        canvas.save()
        canvas.translate(10f, 10f)
        for (useShader in listOf(false, true)) {
            for (useTex in listOf(false, true)) {
                for (matrix in matrices) {
                    canvas.save()
                    canvas.concat(matrix)
                    val paint = if (useShader) Paint(shader = shader) else Paint(color = Color.WHITE)
                    val verts = Vertices(
                        mode = VertexMode.TRIANGLES,
                        positions = MESH_POSITIONS,
                        texCoords = if (useTex) MESH_TEX_COORDS else null,
                        indices = indices,
                    )
                    canvas.drawVertices(verts, paint)
                    canvas.restore()
                }
                canvas.translate(0f, 120f)
            }
        }
        canvas.restore()
    }

    private companion object {
        const val K_SHADER_SIZE: Float = 40f
        val MESH_POSITIONS = listOf(
            Point(0f, 0f), Point(15f, 3f), Point(30f, 0f),
            Point(3f, 15f), Point(15f, 15f), Point(27f, 15f),
            Point(0f, 30f), Point(15f, 27f), Point(30f, 30f),
        )
        val MESH_TEX_COORDS = listOf(
            Point(0f, 0f), Point(K_SHADER_SIZE / 2f, 0f), Point(K_SHADER_SIZE, 0f),
            Point(0f, K_SHADER_SIZE / 2f), Point(K_SHADER_SIZE / 2f, K_SHADER_SIZE / 2f), Point(K_SHADER_SIZE, K_SHADER_SIZE / 2f),
            Point(0f, K_SHADER_SIZE), Point(K_SHADER_SIZE / 2f, K_SHADER_SIZE), Point(K_SHADER_SIZE, K_SHADER_SIZE),
        )
    }
}
