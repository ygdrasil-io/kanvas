package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect
import kotlin.random.Random

enum class Clip { kRect_Clip, kRRect_Clip, kPath_Clip }

/**
 * Port of Skia's `gm/complexclip2.cpp::ComplexClip2GM`.
 *
 * 5 × 5 grid of clip-stack experiments. Each cell stacks five clips
 * (rect / rrect / path variant, with random [ClipOp.DIFFERENCE] /
 * [ClipOp.INTERSECT] op per slot), then fills with light green.
 *
 * 6 variants: (rect|rrect|path) × (bw|aa).
 * @see https://github.com/google/skia/blob/main/gm/complexclip2.cpp
 */
abstract class ComplexClip2Gm(
    override val name: String,
    private val clip: Clip,
    private val antiAlias: Boolean,
) : SkiaGm {
    override val renderFamily = RenderFamily.CLIP
    override val minSimilarity = 0.0
    override val width = kWidth
    override val height = kHeight

    private val rects = Array(5) { Rect.fromLTRB(0f, 0f, 0f, 0f) }
    private val rrects = Array(5) { RRect(Rect.EMPTY) }
    private val paths = arrayOfNulls<Path>(5)
    private val rectColors = IntArray(5)
    private val ops = Array(kRows * kCols) { Array(5) { ClipOp.INTERSECT } }

    init {
        val xA = 0.65f; val xB = 10.65f; val xC = 20.65f
        val xD = 30.65f; val xE = 40.65f; val xF = 50.65f
        val yA = 0.65f; val yB = 10.65f; val yC = 20.65f
        val yD = 30.65f; val yE = 40.65f; val yF = 50.65f

        rects[0] = Rect.fromLTRB(xB, yB, xE, yE)
        rrects[0] = RRect(rects[0], CornerRadii(7f, 7f), CornerRadii(7f, 7f), CornerRadii(7f, 7f), CornerRadii(7f, 7f))
        paths[0] = Path { }.apply { addRRect(RRect(rects[0], CornerRadii(5f, 5f), CornerRadii(5f, 5f), CornerRadii(5f, 5f), CornerRadii(5f, 5f))) }
        rectColors[0] = 0xFFFF0000.toInt()

        rects[1] = Rect.fromLTRB(xA, yA, xD, yD)
        rrects[1] = RRect(rects[1], CornerRadii(7f, 7f), CornerRadii(7f, 7f), CornerRadii(7f, 7f), CornerRadii(7f, 7f))
        paths[1] = Path { }.apply { addRRect(RRect(rects[1], CornerRadii(5f, 5f), CornerRadii(5f, 5f), CornerRadii(5f, 5f), CornerRadii(5f, 5f))) }
        rectColors[1] = 0xFF00FF00.toInt()

        rects[2] = Rect.fromLTRB(xC, yA, xF, yD)
        rrects[2] = RRect(rects[2], CornerRadii(7f, 7f), CornerRadii(7f, 7f), CornerRadii(7f, 7f), CornerRadii(7f, 7f))
        paths[2] = Path { }.apply { addRRect(RRect(rects[2], CornerRadii(5f, 5f), CornerRadii(5f, 5f), CornerRadii(5f, 5f), CornerRadii(5f, 5f))) }
        rectColors[2] = 0xFF0000FF.toInt()

        rects[3] = Rect.fromLTRB(xA, yC, xD, yF)
        rrects[3] = RRect(rects[3], CornerRadii(7f, 7f), CornerRadii(7f, 7f), CornerRadii(7f, 7f), CornerRadii(7f, 7f))
        paths[3] = Path { }.apply { addRRect(RRect(rects[3], CornerRadii(5f, 5f), CornerRadii(5f, 5f), CornerRadii(5f, 5f), CornerRadii(5f, 5f))) }
        rectColors[3] = 0xFFFFFF00.toInt()

        rects[4] = Rect.fromLTRB(xC, yC, xF, yF)
        rrects[4] = RRect(rects[4], CornerRadii(7f, 7f), CornerRadii(7f, 7f), CornerRadii(7f, 7f), CornerRadii(7f, 7f))
        paths[4] = Path { }.apply { addRRect(RRect(rects[4], CornerRadii(5f, 5f), CornerRadii(5f, 5f), CornerRadii(5f, 5f), CornerRadii(5f, 5f))) }
        rectColors[4] = 0xFF00FFFF.toInt()

        val opChoices = arrayOf(ClipOp.DIFFERENCE, ClipOp.INTERSECT)
        val r = Random(0)
        for (i in 0 until kRows) {
            for (j in 0 until kCols) {
                for (k in 0 until 5) {
                    val idx = r.nextInt(opChoices.size)
                    ops[j * kRows + i][k] = opChoices[idx]
                }
            }
        }
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0xDD / 255f, 0xA0 / 255f, 0xDD / 255f)

        var rectPaint = Paint(style = PaintStyle.STROKE, strokeWidth = 0f)
        val fillPaint = Paint(color = Color.fromRGBA(0xA0 / 255f, 0xDD / 255f, 0xA0 / 255f))

        for (i in 0 until kRows) {
            for (j in 0 until kCols) {
                canvas.save()
                canvas.translate(
                    kPadX + (kCellW + kPadX) * j,
                    kPadY + (kCellH + kPadY) * i,
                )

                for (k in 0 until 5) {
                    rectPaint = rectPaint.copy(color = Color(rectColors[k].toUInt()))
                    when (clip) {
                        Clip.kRect_Clip -> canvas.drawRect(rects[k], rectPaint)
                        Clip.kRRect_Clip -> canvas.drawRRect(rrects[k], rectPaint)
                        Clip.kPath_Clip -> canvas.drawPath(paths[k]!!, rectPaint)
                    }
                }

                for (k in 0 until 5) {
                    val op = ops[j * kRows + i][k]
                    when (clip) {
                        Clip.kRect_Clip -> {
                            val rectPath = Path { }.apply { addRect(rects[k]) }
                            canvas.clipPath(rectPath, op, antiAlias)
                        }
                        Clip.kRRect_Clip -> canvas.clipRRect(rrects[k], op, antiAlias)
                        Clip.kPath_Clip -> canvas.clipPath(paths[k]!!, op, antiAlias)
                    }
                }
                canvas.drawRect(Rect.fromXYWH(0f, 0f, kCellW, kCellH), fillPaint)
                canvas.restore()
            }
        }
    }

    private companion object {
        const val kRows = 5
        const val kCols = 5
        const val kPadX = 20f
        const val kPadY = 20f
        val kCellW = 50f
        val kCellH = 50f
        val kWidth = (kCols * kCellW + (kCols + 1) * kPadX).toInt()
        val kHeight = (kRows * kCellH + (kRows + 1) * kPadY).toInt()
    }
}

class ComplexClip2RectGm : ComplexClip2Gm("complexclip2", Clip.kRect_Clip, false)
class ComplexClip2RectAaGm : ComplexClip2Gm("complexclip2_rect_aa", Clip.kRect_Clip, true)
class ComplexClip2RRectGm : ComplexClip2Gm("complexclip2_rrect_bw", Clip.kRRect_Clip, false)
class ComplexClip2RRectAaGm : ComplexClip2Gm("complexclip2_rrect_aa", Clip.kRRect_Clip, true)
class ComplexClip2PathGm : ComplexClip2Gm("complexclip2_path_bw", Clip.kPath_Clip, false)
class ComplexClip2PathAaGm : ComplexClip2Gm("complexclip2_path_aa", Clip.kPath_Clip, true)
