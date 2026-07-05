package org.graphiks.kanvas.skia.gm.mesh

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Vertices
import org.graphiks.kanvas.types.VertexMode

/**
 * Port of Skia's `gm/mesh.cpp::MeshGM`.
 * Exercises mesh draws through inlined CPU vertices lowering across
 * indexed/non-indexed, position-only/color, alpha, shader, and blender
 * combinations.
 * @see https://github.com/google/skia/blob/main/gm/mesh.cpp
 */
class CustomMeshGm : SkiaGm {
    override val name = "custommesh"
    override val renderFamily = RenderFamily.MESH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 84.0
    override val width = 435
    override val height = 1180

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val shader = Shader.LinearGradient(
            start = Point(20f, 20f),
            end = Point(120f, 120f),
            stops = listOf(
                GradientStop(0f, Color.WHITE),
                GradientStop(1f, Color.TRANSPARENT),
            ),
        )

        var i = 0
        for (mode in listOf(BlendMode.DST, BlendMode.SRC, BlendMode.SATURATION)) {
            canvas.save()
            for (alpha in listOf(255, 64)) {
                for (colors in listOf(false, true)) {
                    for (useShader in listOf(false, true)) {
                        val verts = buildVertices(indexed = i and 1 == 1, colors = colors)
                        val alphaMask = (alpha and 0xFF).toUInt() shl 24
                        val paint = Paint(
                            color = Color(alphaMask or 0x00FF00u),
                            blendMode = mode,
                            shader = if (useShader) shader else null,
                        )
                        canvas.drawVertices(verts, paint)
                        canvas.translate(0f, 150f)
                        i++
                    }
                }
            }
            canvas.restore()
            canvas.translate(150f, 0f)
        }
    }

    private companion object {
        private val quadPositions = listOf(
            Point(20f, 20f), Point(120f, 20f),
            Point(20f, 120f), Point(120f, 120f),
        )
        private val quadColors = listOf(
            Color(0xFFFFFF00u),
            Color.WHITE,
            Color(0xFFFF00FFu),
            Color(0xFF00FFFFu),
        )
        private val indexedPositions = listOf(
            Point(20f, 20f), Point(100f, 0f),
            Point(120f, 20f), Point(200f, 10f),
            Point(20f, 120f), Point(120f, 120f),
        )
        private val indexedColors = listOf(
            Color(0xFFFFFF00u),
            Color.TRANSPARENT,
            Color.WHITE,
            Color.TRANSPARENT,
            Color(0xFFFF00FFu),
            Color(0xFF00FFFFu),
        )
        private val indices = listOf(0, 2, 4, 2, 5, 4)

        fun buildVertices(indexed: Boolean, colors: Boolean): Vertices {
            return if (indexed) {
                Vertices(
                    mode = VertexMode.TRIANGLES,
                    positions = indexedPositions,
                    colors = if (colors) indexedColors else null,
                    indices = indices,
                )
            } else {
                Vertices(
                    mode = VertexMode.TRIANGLE_STRIP,
                    positions = quadPositions,
                    colors = if (colors) quadColors else null,
                )
            }
        }
    }
}
