package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/pathfill.cpp::PathFillGM` (640 × 480).
 *
 * Renders a vertical stack of distinct filled paths.
 *
 * @see https://github.com/google/skia/blob/main/gm/pathfill.cpp
 */
class PathFillGm : SkiaGm {
    override val name = "pathfill"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 480

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(antiAlias = true)

        // Frame
        val frameRect = Rect(10f, 10f, 630f, 470f)
        val frameRRect = org.graphiks.kanvas.types.RRect(
            frameRect, 
            CornerRadii(15f, 15f),
            CornerRadii(15f, 15f),
            CornerRadii(15f, 15f),
            CornerRadii(15f, 15f)
        )
        val framePath = Path { }.also { it.addRRect(frameRRect) }
        canvas.drawPath(framePath, paint)
        canvas.translate(0f, 15f)

        // Triangle
        val triangle = Path {
            moveTo(10f, 20f)
            lineTo(15f, 5f)
            lineTo(30f, 30f)
            close()
        }.transform(Matrix33.translate(10f, 0f))
        canvas.drawPath(triangle, paint)
        canvas.translate(0f, 30f)

        // Rect
        val rectPath = Path { }.also { it.addRect(Rect(10f, 10f, 30f, 30f)) }.transform(Matrix33.translate(10f, 0f))
        canvas.drawPath(rectPath, paint)
        canvas.translate(0f, 30f)

        // Oval
        val ovalPath = Path { }.also { it.addOval(Rect(10f, 10f, 30f, 30f)) }.transform(Matrix33.translate(10f, 0f))
        canvas.drawPath(ovalPath, paint)
        canvas.translate(0f, 30f)

        // Sawtooth 32
        canvas.drawPath(makeSawtooth(32), paint)
        canvas.translate(0f, 30f)

        // Star 5
        canvas.drawPath(makeStar(5), paint)
        canvas.translate(0f, 30f * 5 / 4)

        // Star 13
        canvas.drawPath(makeStar(13), paint)
        canvas.translate(0f, 30f * 5 / 4)

        // Line
        canvas.drawPath(Path {
            moveTo(30f, 30f)
            lineTo(120f, 40f)
            close()
            moveTo(150f, 30f)
            lineTo(150f, 30f)
            lineTo(300f, 40f)
            close()
        }, paint)
        canvas.translate(0f, 40f)

        // House
        canvas.drawPath(makeHouse(), paint)
        canvas.translate(0f, 30f)

        // Sawtooth 3
        canvas.drawPath(makeSawtooth(3), paint)

        // Scaled paths
        canvas.save()
        canvas.scale(0.300000011920929f, 0.300000011920929f)
        canvas.translate(50f, 50f)
        canvas.drawPath(makeInfo(), paint)
        canvas.restore()

        canvas.scale(2f, 2f)
        canvas.translate(5f, 15f)
        canvas.drawPath(makeAccessibility(), paint)

        canvas.scale(0.5f, 0.5f)
        canvas.translate(5f, 50f)
        canvas.drawPath(makeVisualizer(), paint)
    }

    private fun makeSawtooth(teeth: Int): Path {
        var x = 20f
        val y = 20f
        val x0 = x
        val dx = 5f
        val dy = 10f
        return Path {
            moveTo(x, y)
            for (i in 0 until teeth) {
                x += dx
                lineTo(x, y - dy)
                x += dx
                lineTo(x, y + dy)
            }
            lineTo(x, y + 2f * dy)
            lineTo(x0, y + 2f * dy)
            close()
        }
    }

    private fun makeStar(n: Int): Path {
        val c = 45f
        val r = 20f
        var rad = -PI.toFloat() / 2f
        val drad = (n shr 1).toFloat() * PI.toFloat() * 2f / n
        return Path {
            moveTo(c, c - r)
            for (i in 1 until n) {
                rad += drad
                lineTo(c + cos(rad) * r, c + sin(rad) * r)
            }
            close()
        }
    }

    private fun makeHouse(): Path {
        return Path {
            moveTo(21f, 23f)
            lineTo(21f, 11.534f)
            lineTo(22.327f, 12.741f)
            lineTo(23.673f, 11.261f)
            lineTo(12f, 0.648f)
            lineTo(8f, 4.285f)
            lineTo(8f, 2f)
            lineTo(4f, 2f)
            lineTo(4f, 7.921f)
            lineTo(0.327f, 11.26f)
            lineTo(1.673f, 12.74f)
            lineTo(3f, 11.534f)
            lineTo(3f, 23f)
            lineTo(11f, 23f)
            lineTo(11f, 18f)
            lineTo(13f, 18f)
            lineTo(13f, 23f)
            lineTo(21f, 23f)
            close()
            moveTo(9f, 16f)
            lineTo(9f, 21f)
            lineTo(5f, 21f)
            lineTo(5f, 9.715f)
            lineTo(12f, 3.351f)
            lineTo(19f, 9.715f)
            lineTo(19f, 21f)
            lineTo(15f, 21f)
            lineTo(15f, 16f)
            lineTo(9f, 16f)
            close()
        }.transform(Matrix33.translate(20f, 0f))
    }

    private fun makeInfo(): Path {
        return Path {
            moveTo(24f, 4f)
            cubicTo(12.9499998f, 4f, 4f, 12.9499998f, 4f, 24f)
            cubicTo(4f, 35.0499992f, 12.9499998f, 44f, 24f, 44f)
            cubicTo(35.0499992f, 44f, 44f, 35.0499992f, 44f, 24f)
            cubicTo(44f, 12.9500008f, 35.0499992f, 4f, 24f, 4f)
            close()
            moveTo(26f, 34f)
            lineTo(22f, 34f)
            lineTo(22f, 22f)
            lineTo(26f, 22f)
            lineTo(26f, 34f)
            close()
            moveTo(26f, 18f)
            lineTo(22f, 18f)
            lineTo(22f, 14f)
            lineTo(26f, 14f)
            lineTo(26f, 18f)
            close()
        }
    }

    private fun makeAccessibility(): Path {
        return Path {
            moveTo(12f, 2f)
            cubicTo(13.10000038f, 2f, 14f, 2.900000095f, 14f, 4f)
            cubicTo(14f, 5.099999904f, 13.10000038f, 6f, 12f, 6f)
            cubicTo(10.89999961f, 6f, 10f, 5.099999904f, 10f, 4f)
            cubicTo(10f, 2.900000095f, 10.89999961f, 2f, 12f, 2f)
            close()
            moveTo(21f, 9f)
            lineTo(15f, 9f)
            lineTo(15f, 22f)
            lineTo(13f, 22f)
            lineTo(13f, 16f)
            lineTo(11f, 16f)
            lineTo(11f, 22f)
            lineTo(9f, 22f)
            lineTo(9f, 9f)
            lineTo(3f, 9f)
            lineTo(3f, 7f)
            lineTo(21f, 7f)
            lineTo(21f, 9f)
            close()
        }
    }

    private fun makeVisualizer(): Path {
        // Simplified version without conicTo
        return Path {
            moveTo(1.9520f, 2.0000f)
            lineTo(1.0000f, 5.4300f)
            lineTo(17.0000f, 5.4300f)
            lineTo(17.0000f, 2.9520f)
            lineTo(1.9520f, 2.0000f)
            close()
            for (cx in floatArrayOf(2.7140f, 5.0000f, 7.2860f)) {
                moveTo(cx, 3.1430f)
                lineTo(cx, 4.2860f)
                lineTo(cx, 3.1430f)
                close()
            }
            moveTo(1.0000f, 6.1900f)
            lineTo(1.0000f, 14.3810f)
            lineTo(16.0480f, 15.3330f)
            lineTo(17.0000f, 6.1910f)
            lineTo(1.0000f, 6.1910f)
            lineTo(1.0000f, 6.1900f)
            close()
        }
    }
}
