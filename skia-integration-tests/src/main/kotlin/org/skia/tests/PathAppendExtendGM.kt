package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix

/**
 * Port of upstream Skia's
 * [`gm/patharcto.cpp`](https://github.com/google/skia/blob/main/gm/patharcto.cpp)
 * `DEF_SIMPLE_GM(path_append_extend, …, 400, 400)`.
 *
 * Exercises [SkPathBuilder.addPath] with both [SkPath.AddPathMode.kAppend] and
 * [SkPath.AddPathMode.kExtend], using open and closed polygons built with
 * both `SkPathBuilder.addPolygon` (old_school) and [SkPath.Companion.Polygon]
 * (new_school).
 *
 * Each row draws 5 variants of `path0 ++ path1`:
 *  1. `path0` alone + `path1` alone (side by side, conceptually).
 *  2. `addPath(path1, kAppend)` — separate sub-paths.
 *  3. `addPath(path1, perspective, kAppend)` — same, with tiny perspective.
 *  4. `addPath(path1, kExtend)` — extends the last contour.
 *  5. `addPath(path1, perspective, kExtend)` — same, with tiny perspective.
 *
 * The grid is 2 (isClosed) × 2 (polygon-factory) = 4 rows of 5 columns
 * (each column 80 px wide), so 400 × 400.
 *
 * The perspective matrix `[1,0,0, 0,1,0, x,0,1]` (with `x=0.0001`) verifies
 * that [SkPathBuilder.addPath] applies the homogeneous divide for perspective
 * point mapping and does not collapse the two perspective columns to the
 * identity columns.
 *
 * Reference image: `path_append_extend.png`, 400 × 400, white background.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(path_append_extend, canvas, 400, 400) {
 *     const SkPoint p0[] = { { 10, 30 }, {30, 10}, {50, 30} };
 *     const SkPoint p1[] = { { 10, 50 }, {30, 70}, {50, 50} };
 *
 *     const SkPath path1 = SkPath::Polygon(p1, false);
 *
 *     SkPaint paint;
 *     paint.setStroke(true);
 *     paint.setStrokeWidth(9);
 *     paint.setAntiAlias(true);
 *
 *     const SkScalar x = 0.0001f;
 *     const SkMatrix perspective = SkMatrix::MakeAll(1, 0, 0,
 *                                                    0, 1, 0,
 *                                                    x, 0, 1);
 *     for (bool isClosed : {false, true}) {
 *         for (auto proc : {old_school_polygon, new_school_polygon}) {
 *             canvas->save();
 *             SkPath path0 = proc({p0, std::size(p0)}, isClosed);
 *             canvas->drawPath(path0, paint);
 *             canvas->drawPath(path1, paint);
 *             canvas->translate(80, 0);
 *             { SkPath path = SkPathBuilder(path0).addPath(path1, SkPath::kAppend_AddPathMode).detach(); canvas->drawPath(path, paint); }
 *             canvas->translate(80, 0);
 *             { SkPath path = SkPathBuilder(path0).addPath(path1, perspective, SkPath::kAppend_AddPathMode).detach(); canvas->drawPath(path, paint); }
 *             canvas->translate(80, 0);
 *             { SkPath path = SkPathBuilder(path0).addPath(path1, SkPath::kExtend_AddPathMode).detach(); canvas->drawPath(path, paint); }
 *             canvas->translate(80, 0);
 *             { SkPath path = SkPathBuilder(path0).addPath(path1, perspective, SkPath::kExtend_AddPathMode).detach(); canvas->drawPath(path, paint); }
 *             canvas->restore();
 *             canvas->translate(0, 100);
 *         }
 *     }
 * }
 * ```
 */
public class PathAppendExtendGM : GM() {

    override fun getName(): String = "path_append_extend"

    override fun getISize(): SkISize = SkISize.Make(400, 400)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val p0 = arrayOf(10f to 30f, 30f to 10f, 50f to 30f)
        val p1 = arrayOf(10f to 50f, 30f to 70f, 50f to 50f)

        val path1 = SkPath.Polygon(p1, isClosed = false)

        val paint = SkPaint().apply {
            setStroke(true)
            strokeWidth = 9f
            isAntiAlias = true
        }

        // Tiny perspective: upstream uses this to verify addPath handles
        // perspective input instead of treating it as affine identity.
        val x = 0.0001f
        val perspective = SkMatrix.MakeAll(
            1f, 0f, 0f,
            0f, 1f, 0f,
            x, 0f, 1f,
        )

        val procs: List<(Array<Pair<Float, Float>>, Boolean) -> SkPath> = listOf(
            // old_school: SkPathBuilder().addPolygon(pts, isClosed)
            { pts, isClosed -> SkPathBuilder().addPolygon(pts, isClosed).detach() },
            // new_school: SkPath.Polygon(pts, isClosed)
            { pts, isClosed -> SkPath.Polygon(pts, isClosed) },
        )

        for (isClosed in listOf(false, true)) {
            for (proc in procs) {
                c.save()

                val path0 = proc(p0, isClosed)

                // Column 1: path0 alone + path1 alone (drawn separately)
                c.drawPath(path0, paint)
                c.drawPath(path1, paint)

                c.translate(80f, 0f)
                // Column 2: kAppend — path1 appended as a separate sub-path
                val appendPath = SkPathBuilder(path0)
                    .addPath(path1, mode = SkPath.AddPathMode.kAppend)
                    .detach()
                c.drawPath(appendPath, paint)

                c.translate(80f, 0f)
                // Column 3: kAppend with perspective matrix
                val appendPerspPath = SkPathBuilder(path0)
                    .addPath(path1, perspective, SkPath.AddPathMode.kAppend)
                    .detach()
                c.drawPath(appendPerspPath, paint)

                c.translate(80f, 0f)
                // Column 4: kExtend — path1 extends the last contour of path0
                val extendPath = SkPathBuilder(path0)
                    .addPath(path1, mode = SkPath.AddPathMode.kExtend)
                    .detach()
                c.drawPath(extendPath, paint)

                c.translate(80f, 0f)
                // Column 5: kExtend with perspective matrix
                val extendPerspPath = SkPathBuilder(path0)
                    .addPath(path1, perspective, SkPath.AddPathMode.kExtend)
                    .detach()
                c.drawPath(extendPerspPath, paint)

                c.restore()
                c.translate(0f, 100f)
            }
        }
    }
}
