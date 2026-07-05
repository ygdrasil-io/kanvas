package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import kotlin.math.sqrt

/**
 * Port of Skia's `gm/tablecolorfilter.cpp`.
 * Tests table color filters composed via image filter chains.
 * @see https://github.com/google/skia/blob/main/gm/tablecolorfilter.cpp
 */
class TableColorFilterGm : SkiaGm {
    override val name = "tablecolorfilter"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 700
    override val height = 1650

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.866f, 0.866f, 0.866f, 1f)
        canvas.translate(20f, 20f)

        val filterMakers: List<() -> ColorFilter?> = listOf(
            { null },
            { ColorFilter.Table(makeTable0()) },
            { ColorFilter.Table(makeTable1()) },
            { ColorFilter.Table(makeTable2()) },
            { makeCF3() },
        )
        val images = listOf(makeImage0(120, 120), makeImage1(120, 120))

        var y = 0f
        for (image in images) {
            val xOffset = 120f * 9f / 8f
            val yOffset = 120f * 9f / 8f

            var x = 0f
            canvas.drawImage(image, Rect(x, y, x + 120f, y + 120f))
            for (i in 1 until filterMakers.size) {
                x += xOffset
                val cf = filterMakers[i]()
                canvas.drawImage(image, Rect(x, y, x + 120f, y + 120f), Paint(colorFilter = cf))
            }

            for (i in filterMakers.indices) {
                val cf1 = filterMakers[i]()
                val ifLeaf: ImageFilter? = cf1?.let { ImageFilter.ColorFilter(it, null) }
                y += yOffset
                x = 0f
                for (j in 1 until filterMakers.size) {
                    val cf2 = filterMakers[j]()
                    val ifChain: ImageFilter? =
                        if (cf2 != null) ImageFilter.ColorFilter(cf2, ifLeaf) else ifLeaf
                    val paint = Paint(imageFilter = ifChain)
                    drawImageWithImageFilter(canvas, image, x, y, paint)
                    x += xOffset
                }
            }
            y += yOffset
        }
    }

    private fun drawImageWithImageFilter(
        canvas: GmCanvas,
        image: Image,
        x: Float,
        y: Float,
        paint: Paint,
    ) {
        if (paint.imageFilter == null) {
            canvas.drawImage(image, Rect(x, y, x + 120f, y + 120f))
            return
        }
        val bounds = Rect(x, y, x + 120f, y + 120f)
        canvas.saveLayer(bounds, paint)
        canvas.drawImage(image, bounds)
        canvas.restore()
    }

    private fun makeImage0(w: Int, h: Int): Image {
        val surface = Surface(w, h)
        surface.canvas {
            drawRect(Rect(0f, 0f, w.toFloat(), h.toFloat()), Paint(shader = makeShader0(w, h)))
        }
        return surface.makeImageSnapshot()
    }

    private fun makeShader0(w: Int, h: Int): Shader {
        val stops = listOf(
            GradientStop(0f, Color.BLACK),
            GradientStop(1f / 6f, Color.fromRGBA(0f, 1f, 0f, 1f)),
            GradientStop(2f / 6f, Color.fromRGBA(0f, 1f, 1f, 1f)),
            GradientStop(3f / 6f, Color.RED),
            GradientStop(4f / 6f, Color.TRANSPARENT),
            GradientStop(5f / 6f, Color.BLUE),
            GradientStop(1f, Color.WHITE),
        )
        return Shader.LinearGradient(
            start = Point(0f, 0f),
            end = Point(w.toFloat(), h.toFloat()),
            stops = stops,
        )
    }

    private fun makeImage1(w: Int, h: Int): Image {
        val surface = Surface(w, h)
        surface.canvas {
            val cx = w / 2f
            val cy = h / 2f
            drawRect(Rect(0f, 0f, w.toFloat(), h.toFloat()), Paint(color = Color.TRANSPARENT))
            val path = org.graphiks.kanvas.geometry.Path { }.apply { addCircle(cx, cy, cx) }
            drawPath(path, Paint(shader = makeShader1(w, h), antiAlias = true))
        }
        return surface.makeImageSnapshot()
    }

    private fun makeShader1(w: Int, h: Int): Shader {
        val cx = w / 2f
        val cy = h / 2f
        return Shader.RadialGradient(
            center = Point(cx, cy),
            radius = cx,
            stops = listOf(
                GradientStop(0f, Color.RED),
                GradientStop(0.5f, Color.fromRGBA(0f, 1f, 0f, 1f)),
                GradientStop(1f, Color.BLUE),
            ),
        )
    }

    private companion object {
        fun makeTable0(): UByteArray {
            val t = UByteArray(256)
            for (i in 0 until 256) {
                val n = i shr 5
                t[i] = ((n shl 5) or (n shl 2) or (n shr 1)).toUByte()
            }
            return t
        }

        fun makeTable1(): UByteArray {
            val t = UByteArray(256)
            for (i in 0 until 256) {
                t[i] = (i * i / 255).toUByte()
            }
            return t
        }

        fun makeTable2(): UByteArray {
            val t = UByteArray(256)
            for (i in 0 until 256) {
                val fi = i / 255f
                t[i] = (sqrt(fi) * 255f).toInt().toUByte()
            }
            return t
        }

        fun makeCF3(): ColorFilter {
            val ar = makeTable0()
            val ag = makeTable1()
            val ab = makeTable2()
            val combined = UByteArray(256) { i ->
                val r = ar[i].toInt()
                val g = ag[i].toInt()
                val b = ab[i].toInt()
                ((r + g + b) / 3).coerceIn(0, 255).toUByte()
            }
            return ColorFilter.Table(combined)
        }
    }
}
