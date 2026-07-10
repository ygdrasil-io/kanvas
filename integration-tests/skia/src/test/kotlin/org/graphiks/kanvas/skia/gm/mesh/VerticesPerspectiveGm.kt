package org.graphiks.kanvas.skia.gm.mesh

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
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
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.Vertices
import org.graphiks.kanvas.types.VertexMode

/**
 * Port of upstream Skia's `gm/vertices.cpp::vertices_perspective`.
 * Regression for skbug.com/40041407 — drawVertices with a perspective matrix.
 * @see https://github.com/google/skia/blob/main/gm/vertices.cpp
 */
class VerticesPerspectiveGm : SkiaGm {
    override val name = "vertices_perspective"
    override val renderFamily = RenderFamily.MESH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val r = Rect(0f, 0f, 128f, 128f)

        val checker = makeCheckerboardShader(Color.BLACK, Color.WHITE, 32)
        val paint = Paint(shader = checker)

        val pos = listOf(
            Point(0f, 0f), Point(128f, 0f), Point(128f, 128f), Point(0f, 128f),
        )
        val verts = Vertices(
            mode = VertexMode.TRIANGLE_FAN,
            positions = pos,
            texCoords = pos,
        )

        val persp = Matrix33.makeAll(
            1f, 0f, 0f,
            0f, 1f, 0f,
            1f / 100f, 0f, 1f,
        )

        canvas.save()
        canvas.concat(persp)
        canvas.drawRect(r, paint)
        canvas.restore()

        canvas.save()
        canvas.translate(r.width, 0f)
        canvas.concat(persp)
        canvas.drawRect(r, paint)
        canvas.restore()

        canvas.save()
        canvas.translate(0f, r.height)
        canvas.concat(persp)
        canvas.drawVertices(verts, paint)
        canvas.restore()

        canvas.save()
        canvas.translate(r.width, r.height)
        canvas.concat(persp)
        canvas.drawVertices(verts, paint)
        canvas.restore()
    }

    private fun makeCheckerboardShader(c1: Color, c2: Color, size: Int): Shader {
        val side = 2 * size
        val pixels = ByteArray(side * side * 4)
        for (y in 0 until side) {
            for (x in 0 until side) {
                val i = (y * side + x) * 4
                val onTopLeft = (x < size) xor (y < size)
                val c = if (onTopLeft) c1 else c2
                pixels[i] = ((c.packed shr 16) and 0xFFu).toByte()
                pixels[i + 1] = ((c.packed shr 8) and 0xFFu).toByte()
                pixels[i + 2] = (c.packed and 0xFFu).toByte()
                pixels[i + 3] = 0xFF.toByte()
            }
        }
        val img = Image.fromPixels(side, side, pixels, ColorType.RGBA_8888, "checkerboard")
        return img.makeShader(tileModeX = TileMode.REPEAT, tileModeY = TileMode.REPEAT)
    }
}
