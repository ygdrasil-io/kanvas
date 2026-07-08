package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.PointMode
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

/** Port of Skia's `gm/skbug_257.cpp`.
 *  Regression test for skbug.com/257 — draws text blobs with rotated
 *  image shaders to test glyph positioning with shaders.
 *  @see https://github.com/google/skia/blob/main/gm/skbug_257.cpp
 */
class Skbug257Gm : SkiaGm {
    override val name = "skbug_257"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val size = 256f
        val scale = 1.00168f

        canvas.save()
        canvas.scale(scale, scale)

        canvas.save()
        val paint = Paint(color = Color.fromRGBA(1f, 1f, 1f, 1f))

        canvas.drawColor(0.8078f, 0.8118f, 0.8078f, 1f)
        val translate = 225364f
        canvas.translate(0f, -translate)

        val rect = Rect(8f, 8f + translate, size - 8f, size - 8f + translate)
        val shader = makeRotatedCheckerboardShader()
        val checkPaint = Paint(shader = shader)
        canvas.drawRect(rect, checkPaint)

        canvas.translate(size, 0f)
        val rrect = RRect(rect, CornerRadii(40f, 40f), CornerRadii(40f, 40f), CornerRadii(40f, 40f), CornerRadii(40f, 40f))
        canvas.drawRRect(rrect, checkPaint)

        canvas.translate(-size, size)
        val delta = 1f / 64f
        val mid = size / 2
        val points = listOf(
            Point(mid, 8f + translate),
            Point(mid, 8f + translate + delta),
            Point(8f, mid + translate),
            Point(8f, mid + translate + delta),
            Point(mid, size - 8f + translate),
            Point(mid, size - 8f + translate + delta),
            Point(size - 8f, mid + translate),
            Point(size - 8f, mid + translate + delta),
        )
        val strokePaint = paint.copy(
            style = PaintStyle.STROKE, strokeWidth = 8f, strokeCap = org.graphiks.kanvas.paint.StrokeCap.ROUND,
        )
        canvas.drawPoints(PointMode.LINES, points, strokePaint)

        canvas.translate(size, 0f)
        testText(canvas, size, 0f, translate)

        canvas.restore()

        val refStroke = Paint(color = Color.fromRGBA(0f, 1f, 1f, 1f), style = PaintStyle.STROKE, strokeWidth = 5f)
        canvas.drawCircle(mid, mid, mid - 10f, refStroke)
        canvas.drawCircle(3f * mid, mid, mid - 10f, refStroke)
        canvas.drawCircle(mid, 384f, mid - 10f, refStroke)
        canvas.translate(size, size)
        testText(canvas, size, 0f, 0f)

        canvas.restore()
    }

    private fun makeRotatedCheckerboardShader(): Shader {
        val tileSize = 16
        val bmSize = 2 * tileSize
        val surface = Surface(bmSize, bmSize)
        surface.canvas {
            drawColor(Color.fromRGBA(1f, 1f, 1f, 1f))
            val fill = Paint(color = Color.fromRGBA(0f, 0f, 0f, 1f))
            for (yy in 0 until tileSize) {
                for (xx in 0 until tileSize) {
                    val r = Rect(xx.toFloat(), yy.toFloat(), (xx + 1).toFloat(), (yy + 1).toFloat())
                    drawRect(r, fill)
                    val r2 = Rect((xx + tileSize).toFloat(), (yy + tileSize).toFloat(), (xx + tileSize + 1).toFloat(), (yy + tileSize + 1).toFloat())
                    drawRect(r2, fill)
                }
            }
        }
        val image = surface.makeImageSnapshot()
        val matrix = Matrix33.scale(0.75f, 0.75f) * Matrix33.rotate(30f)
        return Shader.WithLocalMatrix(Shader.Image(image, TileMode.REPEAT, TileMode.REPEAT), matrix)
    }

    private fun testText(canvas: GmCanvas, size: Float, y: Float, translateY: Float) {
        val font = Font(typeface, size = 24f, antiAlias = false)
        val text = "HELLO WORLD"
        val paint = Paint()
        canvas.drawString(text, 32f, size / 2f + translateY, font, paint)

        val metrics = font.getMetrics()
        val lineSpacing = if (metrics != null) metrics.descent - metrics.ascent + metrics.leading else 28f
        val baseY = size / 2f + translateY + lineSpacing
        val glyphIds = mutableListOf<UShort>()
        val positions = mutableListOf<Point>()
        var cursorX = 32f
        for (cp in text.codePoints()) {
            val gid = typeface.glyphIdForCodepoint(cp)
            glyphIds.add(gid.toUShort())
            positions.add(Point(cursorX, baseY))
            cursorX += typeface.getAdvance(gid, font.size)
        }
        val blob1 = TextBlob(listOf(KanvasGlyphRun(glyphIds, positions, fontSize = font.size)), typeface, font.size)
        canvas.drawTextBlob(blob1, 0f, 0f, paint)

        val baseY2 = baseY + lineSpacing
        val glyphIds2 = mutableListOf<UShort>()
        val positions2 = mutableListOf<Point>()
        cursorX = 32f
        for (cp in text.codePoints()) {
            val gid = typeface.glyphIdForCodepoint(cp)
            glyphIds2.add(gid.toUShort())
            positions2.add(Point(cursorX, baseY2))
            cursorX += typeface.getAdvance(gid, font.size)
        }
        val blob2 = TextBlob(listOf(KanvasGlyphRun(glyphIds2, positions2, fontSize = font.size)), typeface, font.size)
        canvas.drawTextBlob(blob2, 0f, 0f, paint)
    }
}
