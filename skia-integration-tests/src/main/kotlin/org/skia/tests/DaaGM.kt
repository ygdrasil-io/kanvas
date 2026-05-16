package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/daa.cpp::DEF_SIMPLE_GM(daa, ..., K+350, 5*K)`,
 * with `K = 49` ⇒ canvas size 399 × 245.
 *
 * Five "should be all green / no red" sub-tests that probe how a
 * delta-based AA rasteriser handles adjacent / wound polygons :
 *  1. Two triangles sharing the (0,0)→(K,K) diagonal.
 *  2. Two adjacent rectangles drawn in separate `drawPath`s.
 *  3. Two adjacent rectangles in a single multi-contour `drawPath`,
 *     same winding.
 *  4. Two adjacent rectangles in a single multi-contour `drawPath`,
 *     opposite winding.
 *  5. One long polyline weaving the two adjacent rectangles into one
 *     closed contour with reversed winding at the shared edge.
 *
 * In all cases the underlying red rect should be fully covered by the
 * green / blue draws. If the AA rasteriser drops shared-edge coverage,
 * red pixels show through and the test "fails" visually.
 */
public class DaaGM : GM() {

    private val k = 49f
    private val K = 49

    override fun getName(): String = "daa"
    override fun getISize(): SkISize = SkISize.Make(K + 350, 5 * K)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val paint = SkPaint().apply { isAntiAlias = true }
        val font = ToolUtils.DefaultPortableFont()

        // 1) two triangles share the (0,0)-(K,K) diagonal.
        run {
            paint.color = SK_ColorBLACK
            c.drawString(
                "Should be a green square with no red showing through.",
                k * 1.5f, k * 0.5f, font, paint,
            )

            paint.color = SK_ColorRED
            c.drawRect(SkRect.MakeLTRB(0f, 0f, k, k), paint)

            val tri1 = arrayOf(0f to 0f, k to k, 0f to k, 0f to 0f)
            val tri2 = arrayOf(0f to 0f, k to k, k to 0f, 0f to 0f)
            val path: SkPath = SkPathBuilder()
                .addPolygon(tri1, isClosed = false)
                .addPolygon(tri2, isClosed = false)
                .detach()

            paint.color = SK_ColorGREEN
            c.drawPath(path, paint)
        }

        // 2) Adjacent rects, two separate draws (blue then green).
        c.translate(0f, k)
        run {
            paint.color = SK_ColorBLACK
            c.drawString(
                "Adjacent rects, two draws.  Blue then green, no red?",
                k * 1.5f, k * 0.5f, font, paint,
            )

            paint.color = SK_ColorRED
            c.drawRect(SkRect.MakeLTRB(0f, 0f, k, k), paint)

            run {
                val path = SkPath.Polygon(
                    arrayOf(0f to 0f, 0f to k, k * 0.5f to k, k * 0.5f to 0f),
                    isClosed = false,
                )
                paint.color = SK_ColorBLUE
                c.drawPath(path, paint)
            }
            run {
                val path = SkPath.Polygon(
                    arrayOf(k * 0.5f to 0f, k * 0.5f to k, k to k, k to 0f),
                    isClosed = false,
                )
                paint.color = SK_ColorGREEN
                c.drawPath(path, paint)
            }
        }

        // 3) Two adjacent rects, same winding, single path.
        c.translate(0f, k)
        run {
            paint.color = SK_ColorBLACK
            c.drawString(
                "Adjacent rects, wound together.  All green?",
                k * 1.5f, k * 0.5f, font, paint,
            )

            paint.color = SK_ColorRED
            c.drawRect(SkRect.MakeLTRB(0f, 0f, k, k), paint)

            val path = SkPathBuilder()
                .addPolygon(
                    arrayOf(0f to 0f, 0f to k, k * 0.5f to k, k * 0.5f to 0f),
                    isClosed = false,
                )
                .addPolygon(
                    arrayOf(k * 0.5f to 0f, k * 0.5f to k, k to k, k to 0f),
                    isClosed = false,
                )
                .detach()

            paint.color = SK_ColorGREEN
            c.drawPath(path, paint)
        }

        // 4) Two adjacent rects, opposite winding, single path.
        c.translate(0f, k)
        run {
            paint.color = SK_ColorBLACK
            c.drawString(
                "Adjacent rects, wound opposite.  All green?",
                k * 1.5f, k * 0.5f, font, paint,
            )

            paint.color = SK_ColorRED
            c.drawRect(SkRect.MakeLTRB(0f, 0f, k, k), paint)

            val path = SkPathBuilder()
                .addPolygon(
                    arrayOf(0f to 0f, 0f to k, k * 0.5f to k, k * 0.5f to 0f),
                    isClosed = false,
                )
                .addPolygon(
                    arrayOf(k * 0.5f to 0f, k to 0f, k to k, k * 0.5f to k),
                    isClosed = false,
                )
                .detach()

            paint.color = SK_ColorGREEN
            c.drawPath(path, paint)
        }

        // 5) Single multi-vertex polygon weaving both halves.
        c.translate(0f, k)
        run {
            paint.color = SK_ColorBLACK
            c.drawString(
                "One poly, wound opposite.  All green?",
                k * 1.5f, k * 0.5f, font, paint,
            )

            paint.color = SK_ColorRED
            c.drawRect(SkRect.MakeLTRB(0f, 0f, k, k), paint)

            val path = SkPath.Polygon(
                arrayOf(
                    k * 0.5f to 0f, 0f to 0f, 0f to k, k * 0.5f to k,
                    k * 0.5f to 0f, k to 0f, k to k, k * 0.5f to k,
                ),
                isClosed = false,
            )

            paint.color = SK_ColorGREEN
            c.drawPath(path, paint)
        }
    }
}
