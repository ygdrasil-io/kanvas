package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Paint
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
 * Port of Skia's `gm/drawatlas.cpp::compare_atlas_vertices` (560 × 585).
 * Draws pairs of (drawAtlas | drawVertices) for alpha × colorFilter × blendMode combinations.
 * @see https://github.com/google/skia/blob/main/gm/drawatlas.cpp
 */
class CompareAtlasVerticesGm : SkiaGm {
    override val name = "compare_atlas_vertices"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 560
    override val height = 585

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        canvas.drawColor(r = 0.8f, g = 0.8f, b = 0.8f)

        val tex = Rect.fromXYWH(0f, 0f, 128f, 128f)
        val atlas = makeTestImage()
        val identity = Matrix33.identity()
        val colorList = listOf(Color.fromRGBA(0.53f, 0.27f, 0.53f, 0.53f))

        val modes = listOf(BlendMode.SRC_OVER, BlendMode.PLUS)
        val filters = listOf<ColorFilter?>(null, ColorFilter.Blend(Color.fromRGBA(0f, 1f, 0.53f, 1f), BlendMode.MODULATE))

        canvas.translate(10f, 10f)

        for (mode in modes) {
            for (alpha in listOf(1f, 0.5f)) {
                canvas.save()
                for (cf in filters) {
                    val atlasPaint = Paint(blendMode = mode, colorFilter = cf, color = Color.fromRGBA(1f, 1f, 1f, alpha))
                    canvas.drawAtlas(atlas, listOf(identity), listOf(tex), colors = colorList, blendMode = mode, paint = atlasPaint)
                    canvas.translate(128f, 0f)

                    val verts = makeVertices(tex, colorList[0])
                    val vertPaint = Paint(blendMode = mode, colorFilter = cf, color = Color.fromRGBA(1f, 1f, 1f, alpha))
                    canvas.drawVertices(verts, vertPaint)
                    canvas.translate(145f, 0f)
                }
                canvas.restore()
                canvas.translate(0f, 145f)
            }
        }
    }

    private fun makeTestImage(): Image {
        val w = 128
        val h = 128
        val pixels = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = (y * w + x) * 4
                val r = ((x.toFloat() / w) * 255f).toInt().coerceIn(0, 255)
                val g = ((y.toFloat() / h) * 255f).toInt().coerceIn(0, 255)
                val b = (255 - r).coerceIn(0, 255)
                pixels[i] = r.toByte()
                pixels[i + 1] = g.toByte()
                pixels[i + 2] = b.toByte()
                pixels[i + 3] = (-1).toByte()
            }
        }
        return Image.fromPixels(w, h, pixels, ColorType.RGBA_8888, "test-atlas")
    }

    private fun makeVertices(r: Rect, color: Color): Vertices {
        val pos = listOf(Point(r.left, r.top), Point(r.right, r.top), Point(r.right, r.bottom), Point(r.left, r.bottom))
        val colors = List(4) { color }
        return Vertices(mode = VertexMode.TRIANGLE_FAN, positions = pos, texCoords = pos, colors = colors)
    }
}
