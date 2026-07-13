package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.surface.toImage
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

internal enum class ZeroLengthPathVerb(val hasVerb: Boolean) {
    MOVE(false),
    CLOSE(true),
    LINE(true),
    LINE_CLOSE(true),
    QUAD(true),
    QUAD_CLOSE(true),
    CUBIC(true),
    CUBIC_CLOSE(true),
    ARC(true),
    ARC_CLOSE(true),
}

internal object ZeroLengthPathLayout {
    const val cellWidth = 50
    const val cellHeight = 20
    const val cellPad = 2f
    val firstContourAnchor = Point(9.5f, 9.5f)
    val secondContourAnchor = Point(40.5f, 9.5f)

    fun expectedCaps(
        cap: StrokeCap,
        first: ZeroLengthPathVerb,
        second: ZeroLengthPathVerb? = null,
    ): Int {
        if (cap == StrokeCap.BUTT) return 0
        return listOfNotNull(first, second).count(ZeroLengthPathVerb::hasVerb)
    }
}

abstract class ZeroLengthPathsGm(
    final override val name: String,
    private val antiAlias: Boolean,
    private val doubleContour: Boolean,
) : SkiaGm {
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 80.0
    override val width = if (doubleContour) 1874 else 522
    override val height = 398

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(r = 0f, g = 0f, b = 0f)
        canvas.translate(ZeroLengthPathLayout.cellPad, ZeroLengthPathLayout.cellPad)
        val verbs = if (doubleContour) ZeroLengthPathVerb.entries.take(6) else ZeroLengthPathVerb.entries
        for (cap in listOf(StrokeCap.BUTT, StrokeCap.ROUND, StrokeCap.SQUARE)) {
            for (strokeWidth in listOf(0f, 0.9f, 1f, 1.1f, 15f, 25f)) {
                val paint = Paint(
                    color = Color.WHITE,
                    antiAlias = antiAlias,
                    style = PaintStyle.STROKE,
                    strokeCap = cap,
                    strokeWidth = strokeWidth,
                )
                canvas.save()
                for (first in verbs) {
                    val seconds = if (doubleContour) verbs else listOf<ZeroLengthPathVerb?>(null)
                    for (second in seconds) {
                        val path = if (second == null) {
                            zeroLengthPath(first, Point(24.5f, 9.5f))
                        } else {
                            Path { }.apply {
                                addPath(zeroLengthPath(first, ZeroLengthPathLayout.firstContourAnchor))
                                addPath(zeroLengthPath(second, ZeroLengthPathLayout.secondContourAnchor))
                            }
                        }
                        drawValidationCell(canvas, path, paint, ZeroLengthPathLayout.expectedCaps(cap, first, second))
                        canvas.translate(52f, 0f)
                    }
                }
                canvas.restore()
                canvas.translate(0f, 22f)
            }
        }
    }
}

private fun zeroLengthPath(verb: ZeroLengthPathVerb, anchor: Point): Path = Path {
    moveTo(anchor.x, anchor.y)
    when (verb) {
        ZeroLengthPathVerb.MOVE -> Unit
        ZeroLengthPathVerb.CLOSE -> close()
        ZeroLengthPathVerb.LINE -> lineTo(anchor.x, anchor.y)
        ZeroLengthPathVerb.LINE_CLOSE -> {
            lineTo(anchor.x, anchor.y)
            close()
        }
        ZeroLengthPathVerb.QUAD -> quadTo(anchor.x, anchor.y, anchor.x, anchor.y)
        ZeroLengthPathVerb.QUAD_CLOSE -> {
            quadTo(anchor.x, anchor.y, anchor.x, anchor.y)
            close()
        }
        ZeroLengthPathVerb.CUBIC -> cubicTo(anchor.x, anchor.y, anchor.x, anchor.y, anchor.x, anchor.y)
        ZeroLengthPathVerb.CUBIC_CLOSE -> {
            cubicTo(anchor.x, anchor.y, anchor.x, anchor.y, anchor.x, anchor.y)
            close()
        }
        ZeroLengthPathVerb.ARC -> arcTo(0f, 0f, 0f, false, false, anchor.x, anchor.y)
        ZeroLengthPathVerb.ARC_CLOSE -> {
            arcTo(0f, 0f, 0f, false, false, anchor.x, anchor.y)
            close()
        }
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
private fun countMiddleRowBlobs(result: RenderResult): Int {
    val y = (ZeroLengthPathLayout.cellHeight - 1) / 2
    var inBlob = false
    var blobs = 0
    for (x in 0 until ZeroLengthPathLayout.cellWidth) {
        val topRed = result.pixels[(y * ZeroLengthPathLayout.cellWidth + x) * 4].toInt()
        val bottomRed = result.pixels[((y + 1) * ZeroLengthPathLayout.cellWidth + x) * 4].toInt()
        val visible = topRed + bottomRed != 0
        if (visible && !inBlob) blobs++
        inBlob = visible
    }
    return blobs
}

private fun drawValidationCell(canvas: GmCanvas, path: Path, paint: Paint, expectedCaps: Int) {
    val surface = Surface(ZeroLengthPathLayout.cellWidth, ZeroLengthPathLayout.cellHeight)
    surface.canvas {
        clear(Color.TRANSPARENT)
        drawPath(path, paint)
    }
    val result = surface.render()
    val actualCaps = countMiddleRowBlobs(result)
    val overlay = when {
        actualCaps == expectedCaps -> Color(0x7F007F00u)
        actualCaps > expectedCaps -> Color(0x7F7F7F00u)
        else -> Color(0x7F7F0000u)
    }
    canvas.drawImage(
        result.toImage("zero-length-path-cell"),
        Rect.fromXYWH(0f, 0f, 50f, 20f),
    )
    canvas.drawRect(
        Rect.fromXYWH(0f, 0f, 50f, 20f),
        Paint(color = overlay),
    )
}
