package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.r
import org.graphiks.kanvas.types.g
import org.graphiks.kanvas.types.b
import kotlin.random.Random

/** Port of Skia's `gm/dstreadshuffle.cpp`.
 *  Tests destination-read shuffle with various blend modes — draws
 *  overlapping shapes and text to verify read-shuffle behaviour.
 *  @see https://github.com/google/skia/blob/main/gm/dstreadshuffle.cpp
 */
class DstReadShuffleGm : SkiaGm {
    override val name = "dstreadshuffle"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 530
    override val height = 680

    private val kBackground = Color.fromRGBA(0.66f, 0.66f, 0.66f)
    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    private var fConvexPath: Path? = null
    private var fConcavePath: Path? = null

    private enum class ShapeType { Circle, RoundRect, Rect, ConvexPath, ConcavePath, Text }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(kBackground.r, kBackground.g, kBackground.b)
        var y = 5f
        val types = ShapeType.entries
        for (type in types) {
            val colorRandom = Random(0)
            var x = 5f
            for (r in 0..15) {
                var paint = Paint(
                    color = getColor(colorRandom),
                    antiAlias = true,
                    blendMode = if (r % 3 == 0) BlendMode.COLOR_BURN else BlendMode.SRC_OVER,
                )
                canvas.save()
                canvas.translate(x, y)
                drawShape(canvas, paint, type)
                canvas.restore()
                x += 15f
            }
            y += 110f
        }
        val surface = Surface(35, 35)
        surface.canvas { drawHairlines(this) }
        canvas.scale(5f, 5f)
        canvas.translate(67f, 10f)
        canvas.drawImage(surface.makeImageSnapshot(), Rect.fromXYWH(0f, 0f, 35f, 35f))
    }

    private fun drawShape(canvas: GmCanvas, paint: Paint, type: ShapeType) {
        val kRect = Rect.fromXYWH(0f, 0f, 75f, 85f)
        when (type) {
            ShapeType.Circle -> canvas.drawCircle(kRect.center.x, kRect.center.y, kRect.width / 2f, paint)
            ShapeType.RoundRect -> canvas.drawRRect(RRect(kRect, 15f), paint)
            ShapeType.Rect -> canvas.drawRect(kRect, paint)
            ShapeType.ConvexPath -> {
                if (fConvexPath == null) {
                    val p0 = Point(kRect.left, kRect.top)
                    val p1 = Point(kRect.right, kRect.top)
                    val p2 = Point(kRect.right, kRect.bottom)
                    val p3 = Point(kRect.left, kRect.bottom)
                    fConvexPath = Path {
                        moveTo(p0.x, p0.y)
                        quadTo(p1.x, p1.y, p2.x, p2.y)
                        quadTo(p3.x, p3.y, p0.x, p0.y)
                        close()
                    }
                }
                canvas.drawPath(fConvexPath!!, paint)
            }
            ShapeType.ConcavePath -> {
                if (fConcavePath == null) {
                    val pts = arrayOf(
                        Point(50f, 0f), Point(0f, 0f), Point(0f, 0f),
                        Point(0f, 0f), Point(0f, 0f),
                    )
                    val rot = Matrix33.rotate(360f / 5f)
                    for (i in 1 until 5) {
                        val dst = rot * Point(pts[i - 1].x - 50f, pts[i - 1].y - 70f)
                        pts[i] = Point(50f + dst.x, 70f + dst.y)
                    }
                    fConcavePath = Path {
                        moveTo(pts[0].x, pts[0].y)
                        for (i in 0 until 5) {
                            val idx = (2 * i) % 5
                            lineTo(pts[idx].x, pts[idx].y)
                        }
                        close()
                    }
                    fConcavePath = fConcavePath!!.apply { fillType = org.graphiks.kanvas.geometry.FillType.EVEN_ODD }
                }
                canvas.drawPath(fConcavePath!!, paint)
            }
            ShapeType.Text -> {
                val font = Font(typeface, 100f)
                canvas.drawString("N", 0f, 100f, font, paint)
            }
        }
    }

    private fun getColor(random: Random): Color {
        val c = random.nextInt()
        val r = ((c ushr 16) and 0xFF) / 255f
        val g = ((c ushr 8) and 0xFF) / 255f
        val b = (c and 0xFF) / 255f
        return Color.fromRGBA(r, g, b, 0.5f)
    }

    private fun drawHairlines(canvas: org.graphiks.kanvas.canvas.Canvas) {
        val background = Paint(color = kBackground)
        canvas.drawRect(Rect.fromXYWH(0f, 0f, 35f, 35f), background)
        val hairPaint = Paint(style = PaintStyle.STROKE, strokeWidth = 0f, antiAlias = true)
        var x1 = 3f; var y1 = 7f; var x2 = 29f; var y2 = 7f
        val colorRandom = Random(0)
        val cx = 15.5f; val cy = 12f
        val cosA = kotlin.math.cos(Math.toRadians(360.0 / 12.0)).toFloat()
        val sinA = kotlin.math.sin(Math.toRadians(360.0 / 12.0)).toFloat()
        for (i in 0 until 12) {
            val paint = hairPaint.copy(color = getColor(colorRandom))
            canvas.drawPath(Path { moveTo(x1, y1); lineTo(x2, y2) }, paint)
            val nx1 = cx + cosA * (x1 - cx) - sinA * (y1 - cy)
            val ny1 = cy + sinA * (x1 - cx) + cosA * (y1 - cy)
            val nx2 = cx + cosA * (x2 - cx) - sinA * (y2 - cy)
            val ny2 = cy + sinA * (x2 - cx) + cosA * (y2 - cy)
            x1 = nx1; y1 = ny1; x2 = nx2; y2 = ny2
        }
    }
}
