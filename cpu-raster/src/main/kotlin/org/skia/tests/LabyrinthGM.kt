package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/labyrinth.cpp`. Repro for
 * [crbug.com/913223](https://bugs.chromium.org/p/chromium/issues/detail?id=913223).
 *
 * Builds a hard-coded maze out of `0/1` row and column masks, emits each
 * `1` as a unit-length horizontal or vertical sub-segment, scales the
 * whole stream by 40×, and strokes with a 0.1-px stroke width. Three
 * variants (`labyrinth_square`, `labyrinth_round`, `labyrinth_butt`) only
 * differ in the [SkPaint.Cap] applied to the same path.
 *
 * Original bug was filed against **square** caps, where coverage counting
 * over-counts overlapping segments. **Round** caps share that
 * over-coverage; **butt** caps expose under-coverage on abutted strokes
 * with a `max()`-style coverage function.
 *
 * C++ original (helper):
 * ```cpp
 * static void draw_labyrinth(SkCanvas* canvas, SkPaint::Cap cap) {
 *     constexpr static bool kRows[11][12] = {...};
 *     constexpr static bool kCols[13][10] = {...};
 *     SkPathBuilder maze;
 *     for (size_t y = 0; y < std::size(kRows); ++y) {
 *         for (size_t x = 0; x < std::size(kRows[0]); ++x) {
 *             if (kRows[y][x]) {
 *                 maze.moveTo(x, y);
 *                 maze.lineTo(x+1, y);
 *             }
 *         }
 *     }
 *     for (size_t x = 0; x < std::size(kCols); ++x) {
 *         for (size_t y = 0; y < std::size(kCols[0]); ++y) {
 *             if (kCols[x][y]) {
 *                 maze.moveTo(x, y);
 *                 maze.lineTo(x, y+1);
 *             }
 *         }
 *     }
 *
 *     SkPaint paint;
 *     paint.setStyle(SkPaint::kStroke_Style);
 *     paint.setStrokeWidth(.1f);
 *     paint.setColor(0xff406060);
 *     paint.setAntiAlias(true);
 *     paint.setStrokeCap(cap);
 *
 *     canvas->translate(10.5, 10.5);
 *     canvas->scale(40, 40);
 *     canvas->drawPath(maze.detach(), paint);
 * }
 * ```
 */
internal object LabyrinthHelper {
    internal const val WIDTH: Int = 500
    internal const val HEIGHT: Int = 420

    // Mirrors `constexpr static bool kRows[11][12]` from upstream.
    private val kRows: Array<IntArray> = arrayOf(
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

    // Mirrors `constexpr static bool kCols[13][10]` from upstream.
    private val kCols: Array<IntArray> = arrayOf(
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

    internal fun drawLabyrinth(canvas: SkCanvas, cap: SkPaint.Cap) {
        val maze = SkPathBuilder()
        for (y in kRows.indices) {
            for (x in kRows[0].indices) {
                if (kRows[y][x] == 1) {
                    maze.moveTo(x.toFloat(), y.toFloat())
                    maze.lineTo((x + 1).toFloat(), y.toFloat())
                }
            }
        }
        for (x in kCols.indices) {
            for (y in kCols[0].indices) {
                if (kCols[x][y] == 1) {
                    maze.moveTo(x.toFloat(), y.toFloat())
                    maze.lineTo(x.toFloat(), (y + 1).toFloat())
                }
            }
        }

        val paint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 0.1f
            color = 0xff406060.toInt()
            isAntiAlias = true
            strokeCap = cap
        }

        canvas.translate(10.5f, 10.5f)
        canvas.scale(40f, 40f)
        canvas.drawPath(maze.detach(), paint)
    }
}

/** Port of `DEF_SIMPLE_GM(labyrinth_square, canvas, kWidth, kHeight)`. */
public class LabyrinthSquareGM : GM() {
    override fun getName(): String = "labyrinth_square"
    override fun getISize(): SkISize = SkISize.Make(LabyrinthHelper.WIDTH, LabyrinthHelper.HEIGHT)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        LabyrinthHelper.drawLabyrinth(c, SkPaint.Cap.kSquare_Cap)
    }
}

/** Port of `DEF_SIMPLE_GM(labyrinth_round, canvas, kWidth, kHeight)`. */
public class LabyrinthRoundGM : GM() {
    override fun getName(): String = "labyrinth_round"
    override fun getISize(): SkISize = SkISize.Make(LabyrinthHelper.WIDTH, LabyrinthHelper.HEIGHT)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        LabyrinthHelper.drawLabyrinth(c, SkPaint.Cap.kRound_Cap)
    }
}

/** Port of `DEF_SIMPLE_GM(labyrinth_butt, canvas, kWidth, kHeight)`. */
public class LabyrinthButtGM : GM() {
    override fun getName(): String = "labyrinth_butt"
    override fun getISize(): SkISize = SkISize.Make(LabyrinthHelper.WIDTH, LabyrinthHelper.HEIGHT)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        LabyrinthHelper.drawLabyrinth(c, SkPaint.Cap.kButt_Cap)
    }
}
