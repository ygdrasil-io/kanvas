package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class SmallPathsGm : SkiaGm {
    override val name = "smallpaths"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 88.0
    override val width = 640
    override val height = 512

    private val paths: Array<Path>
    private val dy: FloatArray
    private val widths = floatArrayOf(2f, 3f, 4f, 5f, 6f, 7f, 7f, 14f, 0f, 0f, 0f)
    private val xTranslate = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -220.625f, 0f, 0f)

    init {
        val pairs = listOf(
            ::makeTriangle, ::makeRect, ::makeOval,
            { makeStar(5) }, { makeStar(13) },
            ::makeThreeLine, ::makeArrow, ::makeCurve,
            ::makeBattery, ::makeBattery2, ::makeRing,
        ).map { it() }
        paths = Array(pairs.size) { pairs[it].first }
        dy = FloatArray(pairs.size) { pairs[it].second }
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        var paint = Paint()

        canvas.save()
        for (i in paths.indices) {
            canvas.drawPath(paths[i], paint)
            canvas.translate(xTranslate[i], dy[i])
        }
        canvas.restore()
        canvas.translate(120f, 0f)

        canvas.save()
        paint = paint.copy(style = PaintStyle.STROKE, strokeCap = StrokeCap.BUTT)
        for (i in paths.indices) {
            paint = paint.copy(strokeWidth = widths[i])
            canvas.drawPath(paths[i], paint)
            canvas.translate(xTranslate[i], dy[i])
        }
        canvas.restore()
        canvas.translate(120f, 0f)

        canvas.save()
        paint = paint.copy(style = PaintStyle.STROKE, strokeCap = StrokeCap.BUTT)
        for (i in paths.indices) {
            paint = paint.copy(strokeWidth = widths[i] + 2f)
            canvas.drawPath(paths[i], paint)
            canvas.translate(xTranslate[i], dy[i])
        }
        canvas.restore()
        canvas.translate(120f, 0f)

        paint = paint.copy(style = PaintStyle.STROKE, strokeCap = StrokeCap.BUTT)
        for (i in paths.indices) {
            paint = paint.copy(strokeWidth = widths[i])
            canvas.drawPath(paths[i], paint)
            canvas.translate(xTranslate[i], dy[i])
        }
    }

    private fun makeTriangle(): Pair<Path, Float> {
        val pts = intArrayOf(10, 20, 15, 5, 30, 30)
        val p = Path {
            moveTo(pts[0].toFloat(), pts[1].toFloat())
            lineTo(pts[2].toFloat(), pts[3].toFloat())
            lineTo(pts[4].toFloat(), pts[5].toFloat())
            close()
        }
        return p.transform(10f, 0f, 1f, 1f) to 30f
    }

    private fun makeRect(): Pair<Path, Float> {
        val r = Rect(20f, 10f, 40f, 30f)
        return Path {
            moveTo(r.left, r.top)
            lineTo(r.right, r.top)
            lineTo(r.right, r.bottom)
            lineTo(r.left, r.bottom)
            close()
        } to 30f
    }

    private fun makeOval(): Pair<Path, Float> {
        val r = Rect(20f, 10f, 40f, 30f)
        return Path { }.also { it.addOval(r) } to 30f
    }

    private fun makeStar(n: Int): Pair<Path, Float> {
        val cx = 45f
        val r = 20f
        var rad = -PI.toFloat() / 2f
        val drad = (n shr 1) * PI.toFloat() * 2f / n
        val p = Path {
            moveTo(cx, cx - r)
            for (i in 1 until n) {
                rad += drad
                lineTo(cx + cos(rad) * r, cx + sin(rad) * r)
            }
            close()
        }
        return p to (r * 2 * 6 / 5)
    }

    private fun makeThreeLine(): Pair<Path, Float> {
        val xOffset = 34f
        val yOffset = 50f
        return Path {
            moveTo(-32.5f + xOffset, 0f + yOffset)
            lineTo(32.5f + xOffset, 0f + yOffset)

            moveTo(-32.5f + xOffset, 19f + yOffset)
            lineTo(32.5f + xOffset, 19f + yOffset)

            moveTo(-32.5f + xOffset, -19f + yOffset)
            lineTo(32.5f + xOffset, -19f + yOffset)
            lineTo(-32.5f + xOffset, -19f + yOffset)

            close()
        } to 70f
    }

    private fun makeArrow(): Pair<Path, Float> {
        val xOffset = 34f
        val yOffset = 40f
        return Path {
            moveTo(-26f + xOffset, 0f + yOffset)
            lineTo(26f + xOffset, 0f + yOffset)

            moveTo(-28f + xOffset, -2.4748745f + yOffset)
            lineTo(0f + xOffset, 25.525126f + yOffset)

            moveTo(-28f + xOffset, 2.4748745f + yOffset)
            lineTo(0f + xOffset, -25.525126f + yOffset)
            lineTo(-28f + xOffset, 2.4748745f + yOffset)

            close()
        } to 70f
    }

    private fun makeCurve(): Pair<Path, Float> {
        val xOffset = -382f
        val yOffset = -50f
        return Path {
            moveTo(491f + xOffset, 56f + yOffset)
            quadTo(
                435.93292f + xOffset, 56.000031f + yOffset,
                382.61078f + xOffset, 69.752716f + yOffset,
            )
        } to 40f
    }

    private fun makeBattery(): Pair<Path, Float> {
        val xOffset = 5f
        return Path {
            moveTo(24.67f + xOffset, 0.33000004f)
            lineTo(8.3299999f + xOffset, 0.33000004f)
            lineTo(8.3299999f + xOffset, 5.3299999f)
            lineTo(0.33000004f + xOffset, 5.3299999f)
            lineTo(0.33000004f + xOffset, 50.669998f)
            lineTo(32.669998f + xOffset, 50.669998f)
            lineTo(32.669998f + xOffset, 5.3299999f)
            lineTo(24.67f + xOffset, 5.3299999f)
            lineTo(24.67f + xOffset, 0.33000004f)
            close()

            moveTo(25.727224f + xOffset, 12.886665f)
            lineTo(10.907918f + xOffset, 12.886665f)
            lineTo(7.5166659f + xOffset, 28.683645f)
            lineTo(14.810181f + xOffset, 28.683645f)
            lineTo(7.7024879f + xOffset, 46.135998f)
            lineTo(28.049999f + xOffset, 25.136419f)
            lineTo(16.854223f + xOffset, 25.136419f)
            lineTo(25.727224f + xOffset, 12.886665f)
            close()
        } to 50f
    }

    private fun makeBattery2(): Pair<Path, Float> {
        val xOffset = 225.625f
        return Path {
            moveTo(32.669998f + xOffset, 9.8640003f)
            lineTo(0.33000004f + xOffset, 9.8640003f)
            lineTo(0.33000004f + xOffset, 50.669998f)
            lineTo(32.669998f + xOffset, 50.669998f)
            lineTo(32.669998f + xOffset, 9.8640003f)
            close()

            moveTo(10.907918f + xOffset, 12.886665f)
            lineTo(25.727224f + xOffset, 12.886665f)
            lineTo(16.854223f + xOffset, 25.136419f)
            lineTo(28.049999f + xOffset, 25.136419f)
            lineTo(7.7024879f + xOffset, 46.135998f)
            lineTo(14.810181f + xOffset, 28.683645f)
            lineTo(7.5166659f + xOffset, 28.683645f)
            lineTo(10.907918f + xOffset, 12.886665f)
            close()
        } to 60f
    }

    private fun makeRing(): Pair<Path, Float> {
        val xOffset = 120f
        val yOffset = -270f
        return Path {
            moveTo(xOffset + 144.859f, yOffset + 285.172f)
            lineTo(xOffset + 144.859f, yOffset + 285.172f)
            lineTo(xOffset + 144.859f, yOffset + 285.172f)
            lineTo(xOffset + 143.132f, yOffset + 284.617f)
            lineTo(xOffset + 144.859f, yOffset + 285.172f)
            close()

            moveTo(xOffset + 135.922f, yOffset + 286.844f)
            lineTo(xOffset + 135.922f, yOffset + 286.844f)
            lineTo(xOffset + 135.922f, yOffset + 286.844f)
            lineTo(xOffset + 135.367f, yOffset + 288.571f)
            lineTo(xOffset + 135.922f, yOffset + 286.844f)
            close()

            moveTo(xOffset + 135.922f, yOffset + 286.844f)
            cubicTo(
                xOffset + 137.07f, yOffset + 287.219f,
                xOffset + 138.242f, yOffset + 287.086f,
                xOffset + 139.242f, yOffset + 286.578f,
            )
            cubicTo(
                xOffset + 140.234f, yOffset + 286.078f,
                xOffset + 141.031f, yOffset + 285.203f,
                xOffset + 141.406f, yOffset + 284.055f,
            )
            lineTo(xOffset + 144.859f, yOffset + 285.172f)
            cubicTo(
                xOffset + 143.492f, yOffset + 289.375f,
                xOffset + 138.992f, yOffset + 291.656f,
                xOffset + 134.797f, yOffset + 290.297f,
            )
            lineTo(xOffset + 135.922f, yOffset + 286.844f)
            close()

            moveTo(xOffset + 129.68f, yOffset + 280.242f)
            lineTo(xOffset + 129.68f, yOffset + 280.242f)
            lineTo(xOffset + 129.68f, yOffset + 280.242f)
            lineTo(xOffset + 131.407f, yOffset + 280.804f)
            lineTo(xOffset + 129.68f, yOffset + 280.242f)
            close()

            moveTo(xOffset + 133.133f, yOffset + 281.367f)
            cubicTo(
                xOffset + 132.758f, yOffset + 282.508f,
                xOffset + 132.883f, yOffset + 283.687f,
                xOffset + 133.391f, yOffset + 284.679f,
            )
            cubicTo(
                xOffset + 133.907f, yOffset + 285.679f,
                xOffset + 134.774f, yOffset + 286.468f,
                xOffset + 135.922f, yOffset + 286.843f,
            )
            lineTo(xOffset + 134.797f, yOffset + 290.296f)
            cubicTo(
                xOffset + 130.602f, yOffset + 288.929f,
                xOffset + 128.313f, yOffset + 284.437f,
                xOffset + 129.68f, yOffset + 280.241f,
            )
            lineTo(xOffset + 133.133f, yOffset + 281.367f)
            close()

            moveTo(xOffset + 139.742f, yOffset + 275.117f)
            lineTo(xOffset + 139.742f, yOffset + 275.117f)
            lineTo(xOffset + 139.18f, yOffset + 276.844f)
            lineTo(xOffset + 139.742f, yOffset + 275.117f)
            close()

            moveTo(xOffset + 138.609f, yOffset + 278.57f)
            cubicTo(
                xOffset + 137.461f, yOffset + 278.203f,
                xOffset + 136.297f, yOffset + 278.328f,
                xOffset + 135.297f, yOffset + 278.836f,
            )
            cubicTo(
                xOffset + 134.297f, yOffset + 279.344f,
                xOffset + 133.508f, yOffset + 280.219f,
                xOffset + 133.133f, yOffset + 281.367f,
            )
            lineTo(xOffset + 129.68f, yOffset + 280.242f)
            cubicTo(
                xOffset + 131.047f, yOffset + 276.039f,
                xOffset + 135.539f, yOffset + 273.758f,
                xOffset + 139.742f, yOffset + 275.117f,
            )
            lineTo(xOffset + 138.609f, yOffset + 278.57f)
            close()

            moveTo(xOffset + 141.406f, yOffset + 284.055f)
            cubicTo(
                xOffset + 141.773f, yOffset + 282.907f,
                xOffset + 141.648f, yOffset + 281.735f,
                xOffset + 141.148f, yOffset + 280.735f,
            )
            cubicTo(
                xOffset + 140.625f, yOffset + 279.735f,
                xOffset + 139.757f, yOffset + 278.946f,
                xOffset + 138.609f, yOffset + 278.571f,
            )
            lineTo(xOffset + 139.742f, yOffset + 275.118f)
            cubicTo(
                xOffset + 143.937f, yOffset + 276.493f,
                xOffset + 146.219f, yOffset + 280.977f,
                xOffset + 144.859f, yOffset + 285.173f,
            )
            lineTo(xOffset + 141.406f, yOffset + 284.055f)
            close()
        }.also { it.fillType = FillType.WINDING } to 15f
    }
}
