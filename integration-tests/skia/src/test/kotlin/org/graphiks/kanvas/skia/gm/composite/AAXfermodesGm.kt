package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.a
import org.graphiks.kanvas.types.r
import org.graphiks.kanvas.types.g
import org.graphiks.kanvas.types.b
import kotlin.math.max

/**
 * Port of Skia's gm/aaxfermodes.cpp.
 * Verifies AA on every Porter-Duff coefficient mode and every "Advanced"
 * (separable + HSL) mode.
 * @see https://github.com/google/skia/blob/main/gm/aaxfermodes.cpp
 */
class AAXfermodesGm : SkiaGm {
    override val name = "aaxfermodes"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.SLOW
    override val minSimilarity = 14.0
    override val width = 984
    override val height = 625

    private val ovalPath = Path {
        val radius = -1.4f * kShapeSize / 2f
        moveTo(-radius, 0f)
        quadTo(0f, -1.33f * radius, radius, 0f)
        quadTo(0f, 1.33f * radius, -radius, 0f)
        close()
    }

    private val concavePath = Path {
        val radius = -1.4f * kShapeSize / 2f
        moveTo(-radius, 0f)
        quadTo(0f, 0f, 0f, -radius)
        quadTo(0f, 0f, radius, 0f)
        quadTo(0f, 0f, 0f, radius)
        quadTo(0f, 0f, -radius, 0f)
        close()
    }

    private val diamondPath = Path {
        val s = kShapeSize / 2f
        moveTo(0f, -s)
        lineTo(s, 0f)
        lineTo(0f, s)
        lineTo(-s, 0f)
        close()
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawPass(canvas, Pass.Checkerboard)

        canvas.save()
        canvas.translate(kMargin.toFloat(), kMargin.toFloat())
        drawPass(canvas, Pass.Background)
        drawPass(canvas, Pass.Shape)
        canvas.restore()
    }

    private fun drawPass(canvas: GmCanvas, pass: Pass) {
        val cellClip = Rect(
            -kShapeSize * 11f / 16f, -kShapeSize * 11f / 16f,
            kShapeSize * 11f / 16f, kShapeSize * 11f / 16f,
        )

        canvas.save()
        if (pass == Pass.Checkerboard) {
            canvas.translate(kMargin.toFloat(), kMargin.toFloat())
        }
        canvas.translate(0f, kTitleSpacing.toFloat())

        for (xfermodeSet in 0..1) {
            val firstMode = (BlendMode.entries.size - 1) * xfermodeSet
            canvas.save()

            canvas.translate(0f, kSubtitleSpacing + kShapeSpacing / 2f)

            var m = 0
            while (m < BlendMode.entries.size) {
                val modeIndex = firstMode + m
                if (modeIndex >= BlendMode.entries.size) break
                val mode = BlendMode.entries[modeIndex]
                canvas.save()

                canvas.translate(kLabelSpacing + kShapeSpacing / 2f, 0f)

                for (colorIdx in kShapeColors.indices) {
                    val paint = setupShapePaint(canvas, kShapeColors[colorIdx], mode)
                    canvas.save()

                    for (shapeIdx in 0..kLast_Shape) {
                        if (pass != Pass.Shape) {
                            canvas.save()
                            canvas.clipRect(cellClip)
                            if (pass == Pass.Checkerboard) {
                                drawCheckerboard(canvas, 0xFFFFFFFF.toInt(), 0xFFC6C3C6.toInt(), 10)
                            } else {
                                canvas.drawColor(kBGColor.r, kBGColor.g, kBGColor.b, kBGColor.a)
                            }
                            canvas.restore()
                        } else {
                            drawShape(canvas, shapeIdx, paint, mode)
                        }
                        canvas.translate(kShapeTypeSpacing.toFloat(), 0f)
                    }

                    canvas.restore()
                    canvas.translate(kPaintSpacing.toFloat(), 0f)
                }

                canvas.restore()
                canvas.translate(0f, kShapeSpacing.toFloat())
                m++
            }

            canvas.restore()
            canvas.translate(kXfermodeTypeSpacing.toFloat(), 0f)
        }
        canvas.restore()
    }

    private fun setupShapePaint(canvas: GmCanvas, color: Color, mode: BlendMode): Paint {
        var paint = Paint(color = color)
        if (mode != BlendMode.PLUS) return paint

        // Detect overflow on any channel
        val maxSum = maxOf(
            (kBGColor.a * 255).toInt() + (color.a * 255).toInt(),
            (kBGColor.r * 255).toInt() + (color.r * 255).toInt(),
            (kBGColor.g * 255).toInt() + (color.g * 255).toInt(),
            (kBGColor.b * 255).toInt() + (color.b * 255).toInt(),
        )
        if (maxSum <= 255) return paint

        if (color.a < 1f) {
            val dimPaint = Paint(
                color = Color.fromRGBA(0f, 0f, 0f, 255f * 255f / maxSum / 255f),
                antiAlias = false,
                blendMode = BlendMode.DST_IN,
            )
            paint = paint.copy(
                color = Color.fromRGBA(
                    color.r,
                    color.g,
                    color.b,
                    255f * color.a / maxSum / 255f,
                )
            )
            canvas.drawRect(
                Rect(
                    -kShapeSpacing / 2f, -kShapeSpacing / 2f,
                    kShapeSpacing / 2f + 3f * kShapeTypeSpacing, kShapeSpacing / 2f,
                ),
                dimPaint,
            )
        } else {
            val dimPaint = Paint(
                color = Color.TRANSPARENT,
                antiAlias = false,
                blendMode = BlendMode.DST_IN,
            )
            canvas.drawRect(
                Rect(
                    -kShapeSpacing / 2f, -kShapeSpacing / 2f,
                    kShapeSpacing / 2f + 3f * kShapeTypeSpacing, kShapeSpacing / 2f,
                ),
                dimPaint,
            )
        }
        return paint
    }

    private fun drawShape(canvas: GmCanvas, shapeIdx: Int, paint: Paint, mode: BlendMode) {
        val shapePaint = paint.copy(
            antiAlias = shapeIdx != kSquare_Shape,
            blendMode = mode,
        )
        val s = kShapeSize / 2f
        val rect = Rect(-s, -s, s, s)
        when (shapeIdx) {
            kSquare_Shape -> canvas.drawRect(rect, shapePaint)
            kDiamond_Shape -> canvas.drawPath(diamondPath, shapePaint)
            kOval_Shape -> canvas.drawPath(ovalPath, shapePaint)
            kConcave_Shape -> canvas.drawPath(concavePath, shapePaint)
        }
    }

    private fun drawCheckerboard(canvas: GmCanvas, c1: Int, c2: Int, size: Int) {
        val span = kShapeSize
        val xMin = -span - size
        val xMax = span + size
        val yMin = -span - size
        val yMax = span + size
        var y = Math.floorDiv(yMin, size) * size
        while (y < yMax) {
            var x = Math.floorDiv(xMin, size) * size
            while (x < xMax) {
                val cx = Math.floorDiv(x, size)
                val cy = Math.floorDiv(y, size)
                val solid = Paint(
                    color = if (((cx + cy) and 1) == 0) Companion.intToColor(c2) else Companion.intToColor(c1),
                    antiAlias = false,
                )
                canvas.drawRect(
                    Rect(x.toFloat(), y.toFloat(), (x + size).toFloat(), (y + size).toFloat()),
                    solid,
                )
                x += size
            }
            y += size
        }
    }

    private enum class Pass { Checkerboard, Background, Shape }

    companion object {
        internal val upstreamBlendModes: List<BlendMode> = BlendMode.entries.toList()
        internal val upstreamCoeffSplit: Int
            get() = BlendMode.entries.size - 1

        fun intToColor(value: Int): Color {
            val a = (value ushr 24) and 0xFF
            val r = (value ushr 16) and 0xFF
            val g = (value ushr 8) and 0xFF
            val b = value and 0xFF
            return Color.fromRGBA(r / 255f, g / 255f, b / 255f, a / 255f)
        }

        internal const val kShapeSize: Int = 22
        internal const val kShapeSpacing: Int = 36
        internal const val kShapeTypeSpacing: Int = 4 * kShapeSpacing / 3
        internal const val kPaintSpacing: Int = 4 * kShapeTypeSpacing
        internal const val kLabelSpacing: Int = 3 * kShapeSize
        internal const val kMargin: Int = kShapeSpacing / 2
        internal const val kXfermodeTypeSpacing: Int =
            kLabelSpacing + 2 * kPaintSpacing + kShapeTypeSpacing
        internal const val kTitleSpacing: Int = 3 * kShapeSpacing / 4
        internal const val kSubtitleSpacing: Int = 5 * kShapeSpacing / 8

        internal val kBGColor = intToColor(0xC8D2B887.toInt())

        internal val kShapeColors = listOf(
            intToColor(0x82FF0080.toInt()),
            intToColor(0xFF00FFFF.toInt()),
        )

        internal const val kSquare_Shape: Int = 0
        internal const val kDiamond_Shape: Int = 1
        internal const val kOval_Shape: Int = 2
        internal const val kConcave_Shape: Int = 3
        internal const val kLast_Shape: Int = kConcave_Shape
    }
}
