package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/labyrinth.cpp`. Repro for
 * [crbug.com/913223](https://bugs.chromium.org/p/chromium/issues/detail?id=913223).
 *
 * Builds a hard-coded maze out of `0/1` row and column masks, emits each
 * `1` as a unit-length horizontal or vertical sub-segment, scales the
 * whole stream by 40x, and strokes with a 0.1-px stroke width. Three
 * variants (`labyrinth_square`, `labyrinth_round`, `labyrinth_butt`) only
 * differ in the [StrokeCap] applied to the same path.
 *
 * Original bug was filed against **square** caps, where coverage counting
 * over-counts overlapping segments. **Round** caps share that
 * over-coverage; **butt** caps expose under-coverage on abutted strokes
 * with a `max()`-style coverage function.
 * @see https://github.com/google/skia/blob/main/gm/labyrinth.cpp
 */
private object LabyrinthHelper {
    const val WIDTH = 500
    const val HEIGHT = 420

    private val kRows = arrayOf(
        intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
        intArrayOf(0, 1, 0, 1, 0, 1, 0, 0, 0, 0, 1, 1),
        intArrayOf(0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 1, 1),
        intArrayOf(1, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0),
        intArrayOf(0, 1, 1, 0, 0, 0, 0, 0, 0, 1, 1, 1),
        intArrayOf(1, 0, 0, 1, 0, 0, 0, 0, 1, 1, 1, 0),
        intArrayOf(0, 1, 0, 1, 1, 1, 0, 0, 1, 1, 1, 0),
        intArrayOf(1, 0, 1, 0, 1, 1, 1, 1, 0, 1, 1, 1),
        intArrayOf(0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 1),
        intArrayOf(0, 1, 1, 1, 0, 0, 1, 1, 1, 1, 0, 0),
        intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
    )

    private val kCols = arrayOf(
        intArrayOf(1, 1, 1, 1, 0, 1, 1, 1, 1, 1),
        intArrayOf(0, 0, 1, 0, 0, 0, 1, 1, 1, 0),
        intArrayOf(0, 1, 1, 0, 1, 1, 1, 0, 0, 1),
        intArrayOf(1, 1, 0, 0, 0, 0, 1, 0, 1, 0),
        intArrayOf(0, 0, 1, 0, 1, 0, 0, 0, 0, 1),
        intArrayOf(0, 0, 1, 1, 1, 0, 0, 0, 1, 0),
        intArrayOf(0, 1, 0, 1, 1, 1, 0, 0, 0, 0),
        intArrayOf(1, 1, 1, 0, 1, 1, 1, 0, 1, 0),
        intArrayOf(1, 1, 0, 1, 1, 0, 0, 0, 1, 0),
        intArrayOf(0, 0, 1, 0, 0, 0, 0, 0, 0, 1),
        intArrayOf(0, 0, 1, 1, 0, 0, 0, 0, 1, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 1, 0, 0, 1),
        intArrayOf(1, 1, 1, 1, 1, 1, 0, 1, 1, 1),
    )

    fun buildPath(): Path = Path {
        for (y in kRows.indices) {
            for (x in kRows[0].indices) {
                if (kRows[y][x] == 1) {
                    moveTo(x.toFloat(), y.toFloat())
                    lineTo((x + 1).toFloat(), y.toFloat())
                }
            }
        }
        for (x in kCols.indices) {
            for (y in kCols[0].indices) {
                if (kCols[x][y] == 1) {
                    moveTo(x.toFloat(), y.toFloat())
                    lineTo(x.toFloat(), (y + 1).toFloat())
                }
            }
        }
    }

    fun draw(canvas: GmCanvas, cap: StrokeCap) {
        val path = buildPath()
        val paint = Paint(
            style = PaintStyle.STROKE,
            strokeWidth = 0.1f,
            color = Color(0xFF406060u),
            antiAlias = true,
            strokeCap = cap,
        )
        canvas.translate(10.5f, 10.5f)
        canvas.scale(40f, 40f)
        canvas.drawPath(path, paint)
    }
}

class LabyrinthSquareGm : SkiaGm {
    override val name = "labyrinth_square"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 39.5
    override val width = LabyrinthHelper.WIDTH
    override val height = LabyrinthHelper.HEIGHT

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        LabyrinthHelper.draw(canvas, StrokeCap.SQUARE)
    }
}

class LabyrinthRoundGm : SkiaGm {
    override val name = "labyrinth_round"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = LabyrinthHelper.WIDTH
    override val height = LabyrinthHelper.HEIGHT

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        LabyrinthHelper.draw(canvas, StrokeCap.ROUND)
    }
}

class LabyrinthButtGm : SkiaGm {
    override val name = "labyrinth_butt"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = LabyrinthHelper.WIDTH
    override val height = LabyrinthHelper.HEIGHT

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        LabyrinthHelper.draw(canvas, StrokeCap.BUTT)
    }
}
