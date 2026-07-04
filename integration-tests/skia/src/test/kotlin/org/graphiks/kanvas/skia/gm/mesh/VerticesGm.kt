package org.graphiks.kanvas.skia.gm.mesh

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Vertices
import org.graphiks.kanvas.types.VertexMode

/**
 * Port of Skia's `gm/vertices.cpp::VerticesGM`.
 * Exercises drawVertices across all blend modes, alpha, color filters, shaders, and vertex attrs.
 * @see https://github.com/google/skia/blob/main/gm/vertices.cpp
 */
open class VerticesGm(private val shaderScale: Float = 1f) : SkiaGm {
    override val name = if (shaderScale != 1f) "vertices_scaled_shader" else "vertices"
    override val renderFamily = RenderFamily.MESH
    override val minSimilarity = 0.0
    override val width = 975
    override val height = 1175

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.translate(4f, 4f)
        for (mode in BLEND_MODES) {
            canvas.save()
            for (alpha in listOf(1f, 0.5f)) {
                for (cf in listOf(null, colorFilter)) {
                    for (shader in listOf(shader1, shader2)) {
                        for (attrs in ATTRS) {
                            val paint = Paint(
                                shader = shader,
                                colorFilter = cf,
                                color = Color.fromRGBA(1f, 1f, 1f, alpha),
                            )
                            val vertices = Vertices(
                                mode = VertexMode.TRIANGLE_FAN,
                                positions = MESH_POSITIONS,
                                texCoords = if (attrs.hasTexs) MESH_TEX_COORDS else null,
                                indices = MESH_FAN_INDICES,
                            )
                            canvas.drawVertices(vertices, paint)
                            canvas.translate(40f, 0f)
                        }
                    }
                }
            }
            canvas.restore()
            canvas.translate(0f, 40f)
        }
    }

    private val shader1: Shader = run {
        val base = Shader.LinearGradient(
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
        if (shaderScale == 1f) base
        else Shader.WithLocalMatrix(Shader.WithLocalMatrix(base, Matrix33.translate(-10f, 0f)), Matrix33.translate(10f, 0f))
    }

    private val shader2: Shader = Shader.SolidColor(Color.BLUE)
    private val colorFilter = ColorFilter.Blend(Color(0xFFAABBCCu), BlendMode.DARKEN)

    private data class Attrs(val hasColors: Boolean, val hasTexs: Boolean)

    private companion object {
        const val K_SHADER_SIZE: Float = 40f
        val MESH_POSITIONS = listOf(
            Point(0f, 0f), Point(15f, 3f), Point(30f, 0f),
            Point(3f, 15f), Point(15f, 15f), Point(27f, 15f),
            Point(0f, 30f), Point(15f, 27f), Point(30f, 30f),
        )
        val MESH_TEX_COORDS = listOf(
            Point(0f, 0f), Point(20f, 0f), Point(40f, 0f),
            Point(0f, 20f), Point(20f, 20f), Point(40f, 20f),
            Point(0f, 40f), Point(20f, 40f), Point(40f, 40f),
        )
        val MESH_FAN_INDICES = listOf(4, 0, 1, 2, 5, 8, 7, 6, 3, 0)

        val ATTRS = listOf(
            Attrs(hasColors = true, hasTexs = false),
            Attrs(hasColors = false, hasTexs = true),
            Attrs(hasColors = true, hasTexs = true),
        )
        val BLEND_MODES = listOf(
            BlendMode.CLEAR, BlendMode.SRC, BlendMode.DST, BlendMode.SRC_OVER,
            BlendMode.DST_OVER, BlendMode.SRC_IN, BlendMode.DST_IN,
            BlendMode.SRC_OUT, BlendMode.DST_OUT, BlendMode.SRC_ATOP,
            BlendMode.DST_ATOP, BlendMode.XOR, BlendMode.PLUS, BlendMode.MODULATE,
            BlendMode.SCREEN, BlendMode.OVERLAY, BlendMode.DARKEN, BlendMode.LIGHTEN,
            BlendMode.COLOR_DODGE, BlendMode.COLOR_BURN, BlendMode.HARD_LIGHT,
            BlendMode.SOFT_LIGHT, BlendMode.DIFFERENCE, BlendMode.EXCLUSION,
            BlendMode.MULTIPLY, BlendMode.HUE, BlendMode.SATURATION,
            BlendMode.COLOR, BlendMode.LUMINOSITY,
        )
    }
}

class VerticesScaledShaderGm : VerticesGm(shaderScale = 0.5f)
