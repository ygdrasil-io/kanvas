package org.graphiks.kanvas.skia.gm.mesh

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.ReferenceStatusEntry
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Vertices
import org.graphiks.kanvas.types.VertexMode

/**
 * Port of Skia's `gm/mesh.cpp::MeshColorSpaceGM`.
 * Exercises mesh color-space and alpha-type conversion for
 * position + float4 color attributes across premul/unpremul
 * and sRGB/color-spun specifications.
 * @see https://github.com/google/skia/blob/main/gm/mesh.cpp
 */
class CustomMeshCsGm : SkiaGm {
    override val name = "custommesh_cs"
    override val renderFamily = RenderFamily.MESH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 83.0
    override val referenceStatus = ReferenceStatusEntry(
        status = "untrustable",
        reason = "white placeholder reference does not represent Skia custommesh_cs mesh output",
    )
    override val width = 468
    override val height = 258

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rectW = RECT_W
        val rectH = RECT_H
        val shader = Shader.LinearGradient(
            start = Point(X0, 0f),
            end = Point(X0 + rectW / 2f, 0f),
            stops = listOf(
                GradientStop(0f, Color.WHITE),
                GradientStop(1f, Color.TRANSPARENT),
            ),
            tileMode = TileMode.MIRROR,
        )

        for (useShader in listOf(false, true)) {
            for (unpremul in listOf(false, true)) {
                canvas.save()
                for (spin in listOf(false, true)) {
                    val verts = buildColorSpaceVertices(unpremul, spin)
                    val paint = Paint(
                        color = Color.BLACK,
                        shader = if (useShader) shader else null,
                    )
                    canvas.drawVertices(verts, paint)
                    canvas.translate(0f, rectH + 10f)
                }
                canvas.restore()
                canvas.translate(rectW + 10f, 0f)
            }
        }
    }

    private companion object {
        private const val X0 = 20f
        private const val Y0 = 20f
        private const val RECT_W = 100f
        private const val RECT_H = 100f
        private val colorSpacePoints = listOf(
            Point(X0, Y0),
            Point(X0 + RECT_W, Y0),
            Point(X0, Y0 + RECT_H),
            Point(X0 + RECT_W, Y0 + RECT_H),
        )
        // Unpremul RGBA: red, green(alpha=0), yellow(alpha=0), blue
        private val srcColors = listOf(
            Color(0xFFFF0000u),
            Color(0x0000FF00u),
            Color(0x00FFFF00u),
            Color(0xFF0000FFu),
        )

        fun buildColorSpaceVertices(unpremul: Boolean, spin: Boolean): Vertices {
            val colors = if (unpremul) {
                if (spin) spinColors(srcColors) else srcColors
            } else {
                val premul = srcColors.map { premultiply(it) }
                if (spin) spinColors(premul) else premul
            }
            return Vertices(
                mode = VertexMode.TRIANGLE_STRIP,
                positions = colorSpacePoints,
                colors = colors,
            )
        }

        private fun premultiply(c: Color): Color {
            val p = c.packed
            val a = (p shr 24) and 0xFFu
            if (a == 0u) return Color.TRANSPARENT
            val r = (((p shr 16) and 0xFFu).toInt() * a.toInt() / 255).toUInt()
            val g = (((p shr 8) and 0xFFu).toInt() * a.toInt() / 255).toUInt()
            val b = ((p and 0xFFu).toInt() * a.toInt() / 255).toUInt()
            return Color((a shl 24) or (r shl 16) or (g shl 8) or b)
        }

        private fun spinColors(colors: List<Color>): List<Color> = colors.map { c ->
            val p = c.packed
            val a = (p shr 24) and 0xFFu
            val r = (p shr 16) and 0xFFu
            val g = (p shr 8) and 0xFFu
            val b = p and 0xFFu
            Color((a shl 24) or (g shl 16) or (b shl 8) or r)
        }
    }
}
