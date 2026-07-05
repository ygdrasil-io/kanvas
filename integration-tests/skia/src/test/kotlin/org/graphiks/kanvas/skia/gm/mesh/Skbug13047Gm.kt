package org.graphiks.kanvas.skia.gm.mesh

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.BlendMode
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
 * Port of upstream Skia's `gm/vertices.cpp::skbug_13047`.
 * Regression for skbug.com/13047 — drawVertices with shader carrying localMatrix.
 * @see https://github.com/google/skia/blob/main/gm/vertices.cpp
 */
class Skbug13047Gm : SkiaGm {
    override val name = "skbug_13047"
    override val renderFamily = RenderFamily.MESH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 200
    override val height = 200

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val w = 128; val h = 128
        val pixels = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = (y * w + x) * 4
                val r = (x * 255 / (w - 1)) and 0xFF
                val g = (y * 255 / (h - 1)) and 0xFF
                val b = ((x + y) * 255 / (w + h - 2)) and 0xFF
                pixels[i] = r.toByte(); pixels[i + 1] = g.toByte()
                pixels[i + 2] = b.toByte(); pixels[i + 3] = 0xFF.toByte()
            }
        }
        val image = Image.fromPixels(w, h, pixels, ColorType.RGBA_8888, "gradient-standin")

        val positions = listOf(
            Point(0f, 0f), Point(200f, 0f), Point(200f, 200f), Point(0f, 200f),
        )
        val texCoords = listOf(
            Point(0f, 0f), Point(w.toFloat(), 0f),
            Point(w.toFloat(), h.toFloat()), Point(0f, h.toFloat()),
        )
        val indices = listOf(0, 1, 2, 2, 3, 0)

        val verts = Vertices(
            mode = VertexMode.TRIANGLES,
            positions = positions,
            texCoords = texCoords,
            indices = indices,
        )

        val m = Matrix33.scale(2f, 2f)
        val baseShader = image.makeShader()
        val paint = Paint(shader = Shader.WithLocalMatrix(baseShader, m))

        canvas.drawVertices(verts, paint)
    }
}
