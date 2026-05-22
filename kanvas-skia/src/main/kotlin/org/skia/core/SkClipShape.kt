package org.skia.core

import org.skia.foundation.SkClipOp
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import kotlin.math.abs

/**
 * G2.x -- a `clipPath` whose source-space contour is one of a small set of
 * axis-aligned simple shapes (rect / oval / circle / uniform-corner rrect).
 *
 * Lives next to [SkCanvas]'s clip stack : when a caller does
 * `canvas.clipPath(path, kIntersect, doAntiAlias)` and `path` matches one
 * of the recognised contours via [SkPath.isRect] / [SkPath.isOval] /
 * [SkPath.isRRect], the canvas snapshots the **device-space** shape into
 * a [SkClipShape] and parks it next to the rasterised `aaClip` so a GPU
 * device can evaluate per-pixel coverage analytically (no path mask
 * texture needed).
 *
 * **M4 -- kDifference.** Each variant carries an [op] field so the same
 * shape can encode either an intersect-style clip (keep the inside ;
 * default) or a difference-style clip (keep the outside -- the shape
 * **cuts a hole** in the current clip). On the GPU side, intersect ships
 * `rrect_cov(p)` and difference ships `1 - rrect_cov(p)` ; the rest of
 * the pipeline is shared. Lets us unlock `clipRect(rect, kDifference)`
 * and `clipRRect(_, kDifference)` without going through the
 * arbitrary-clipPath stencil mask (out of scope for M4 ; only triggers
 * for non-axis-aligned shapes or non-canonical paths).
 *
 * **Scope (first slice).** Only the canonical contours produced by
 * `addRect` / `addOval` / `addCircle` / `addRRect` (uniform corner radii)
 * are recognised. Non-uniform `kNinePatch` / `kComplex` rrects fall
 * through to the existing bounding-rect approximation (current GPU
 * behaviour), then the [SkCanvas.bindClip] guard reports the
 * unsupported case to keep the GPU device from silently producing wrong
 * pixels.
 *
 * **Device coords.** Shapes are stored after the CTM has been applied, so
 * the device evaluates against [SkRect] / [SkRRect] / `(cx, cy, r)`
 * directly in fragment-position space. Only axis-aligned CTMs preserve
 * the shape (a rotated circle stays a circle, but a rotated rrect or
 * oval doesn't), so [tryDetect] bails on non-axis-aligned matrices.
 *
 * **Stacking.** A single shape clip is supported today : composing two
 * `clipPath` calls (e.g. circle + oval) is out of scope. The second call
 * widens the stored shape to `null` and the canvas falls back to its
 * existing alpha-mask path (which then triggers the GPU device's
 * fail-fast on aaClip + no shape). Future slices may carry a list.
 */
public sealed interface SkClipShape {

    /** Clip op carried by every shape : intersect (keep inside) or
     *  difference (keep outside, ie cut a hole). */
    public val op: SkClipOp

    /** Axis-aligned rect clip. Bounds are in **device** coords. */
    public data class Rect(
        val bounds: SkRect,
        override val op: SkClipOp = SkClipOp.kIntersect,
    ) : SkClipShape

    /** Axis-aligned ellipse inscribed in [bounds] (device coords). */
    public data class Oval(
        val bounds: SkRect,
        override val op: SkClipOp = SkClipOp.kIntersect,
    ) : SkClipShape

    /** Circle clip at device-space `(cx, cy)` with radius [r]. */
    public data class Circle(
        val cx: Float,
        val cy: Float,
        val r: Float,
        override val op: SkClipOp = SkClipOp.kIntersect,
    ) : SkClipShape

    /**
     * Rounded-rect clip. [bounds] is the outer bbox (device coords), [rx]
     * / [ry] are the **uniform** corner radii (kRect / kOval / kSimple
     * sub-types). Non-uniform rrects (kNinePatch / kComplex) are not
     * represented here -- the detector returns `null` for those.
     */
    public data class RRect(
        val bounds: SkRect,
        val rx: Float,
        val ry: Float,
        override val op: SkClipOp = SkClipOp.kIntersect,
    ) : SkClipShape

    public companion object {
        /**
         * Try to detect an axis-aligned simple shape inside [path]
         * transformed by [ctm]. Returns `null` if :
         *  - [path] is not one of the canonical canonical-contour
         *    detector matches ([SkPath.isRect] / [SkPath.isOval] /
         *    [SkPath.isRRect]),
         *  - [ctm] is not axis-aligned (rotation / skew),
         *  - the rrect has non-uniform corner radii (kNinePatch / kComplex).
         *
         * The returned shape is in device coordinates -- callers can pass
         * it directly to a GPU pipeline keyed on fragment position. The
         * rect path also routes through this detector so `clipRect`-style
         * paths get the fast path through the same machinery.
         *
         * The [op] argument selects between intersect (default) and
         * difference flavour ; it is stored verbatim on the returned
         * shape so downstream consumers (GPU shader) pick the right
         * coverage formula.
         */
        public fun tryDetect(
            path: SkPath,
            ctm: SkMatrix,
            op: SkClipOp = SkClipOp.kIntersect,
        ): SkClipShape? {
            if (!ctm.isAxisAligned) return null

            // Rect ?
            path.isRect()?.let { srcRect ->
                val (x0, y0) = ctm.mapXY(srcRect.left, srcRect.top)
                val (x1, y1) = ctm.mapXY(srcRect.right, srcRect.bottom)
                val devRect = SkRect.MakeLTRB(
                    minOf(x0, x1), minOf(y0, y1),
                    maxOf(x0, x1), maxOf(y0, y1),
                )
                return Rect(devRect, op)
            }

            // Oval ? Map source-space bounds via the axis-aligned CTM ;
            // the oval inscribed in the new bbox is the image of the
            // original oval iff the CTM scales/translates only. If the
            // mapped bounds happen to be square the result is a circle,
            // collapse to [Circle] so the shader gets the cheaper distance
            // test.
            path.isOval()?.let { srcBounds ->
                val (x0, y0) = ctm.mapXY(srcBounds.left, srcBounds.top)
                val (x1, y1) = ctm.mapXY(srcBounds.right, srcBounds.bottom)
                val devBounds = SkRect.MakeLTRB(
                    minOf(x0, x1), minOf(y0, y1),
                    maxOf(x0, x1), maxOf(y0, y1),
                )
                val rx = devBounds.width() * 0.5f
                val ry = devBounds.height() * 0.5f
                return if (abs(rx - ry) < CIRCLE_EPS) {
                    Circle(
                        cx = (devBounds.left + devBounds.right) * 0.5f,
                        cy = (devBounds.top + devBounds.bottom) * 0.5f,
                        r = rx,
                        op = op,
                    )
                } else {
                    Oval(devBounds, op)
                }
            }

            // RRect ? Only uniform-corner sub-types map cleanly under an
            // axis-aligned scale ; the per-corner radii pair would have
            // to be scaled by `(|sx|, |sy|)` and stored individually for
            // kNinePatch / kComplex. Defer the non-uniform variants.
            path.isRRect()?.let { rrect ->
                when (rrect.getType()) {
                    SkRRect.Type.kRect_Type -> {
                        val (x0, y0) = ctm.mapXY(rrect.rect().left, rrect.rect().top)
                        val (x1, y1) = ctm.mapXY(rrect.rect().right, rrect.rect().bottom)
                        return Rect(
                            SkRect.MakeLTRB(
                                minOf(x0, x1), minOf(y0, y1),
                                maxOf(x0, x1), maxOf(y0, y1),
                            ),
                            op,
                        )
                    }
                    SkRRect.Type.kOval_Type -> {
                        val (x0, y0) = ctm.mapXY(rrect.rect().left, rrect.rect().top)
                        val (x1, y1) = ctm.mapXY(rrect.rect().right, rrect.rect().bottom)
                        val devBounds = SkRect.MakeLTRB(
                            minOf(x0, x1), minOf(y0, y1),
                            maxOf(x0, x1), maxOf(y0, y1),
                        )
                        val rx = devBounds.width() * 0.5f
                        val ry = devBounds.height() * 0.5f
                        return if (abs(rx - ry) < CIRCLE_EPS) {
                            Circle(
                                cx = (devBounds.left + devBounds.right) * 0.5f,
                                cy = (devBounds.top + devBounds.bottom) * 0.5f,
                                r = rx,
                                op = op,
                            )
                        } else {
                            Oval(devBounds, op)
                        }
                    }
                    SkRRect.Type.kSimple_Type -> {
                        val (x0, y0) = ctm.mapXY(rrect.rect().left, rrect.rect().top)
                        val (x1, y1) = ctm.mapXY(rrect.rect().right, rrect.rect().bottom)
                        val devBounds = SkRect.MakeLTRB(
                            minOf(x0, x1), minOf(y0, y1),
                            maxOf(x0, x1), maxOf(y0, y1),
                        )
                        // Uniform corner : the per-corner radii get scaled
                        // by the same |sx| / |sy| as the rect bounds.
                        val src = rrect.getSimpleRadii()
                        val sx = abs(ctm.getScaleX())
                        val sy = abs(ctm.getScaleY())
                        return RRect(devBounds, src.x() * sx, src.y() * sy, op)
                    }
                    SkRRect.Type.kNinePatch_Type -> {
                        // M4 -- a kSimple rrect that round-trips through
                        // `SkPath.RRect(...)` + `path.isRRect()` re-emerges
                        // with sub-ULP jitter on the per-corner radii
                        // (e.g. 0.10000038 vs 0.099998474), which trips
                        // [SkRRect.computeType]'s strict equality check
                        // and pushes the type to kNinePatch. The shape is
                        // still uniform in any practical sense ; coerce
                        // back to a single (rx, ry) pair when the spread
                        // is below the jitter threshold so we keep the
                        // analytic fast path. Beyond the threshold (true
                        // nine-patch corner profile) we fall back to null
                        // and the caller takes the alpha-mask path.
                        val r0 = rrect.radii(SkRRect.Corner.kUpperLeft_Corner)
                        val r1 = rrect.radii(SkRRect.Corner.kUpperRight_Corner)
                        val r2 = rrect.radii(SkRRect.Corner.kLowerRight_Corner)
                        val r3 = rrect.radii(SkRRect.Corner.kLowerLeft_Corner)
                        val rxMin = minOf(r0.x(), r1.x(), r2.x(), r3.x())
                        val rxMax = maxOf(r0.x(), r1.x(), r2.x(), r3.x())
                        val ryMin = minOf(r0.y(), r1.y(), r2.y(), r3.y())
                        val ryMax = maxOf(r0.y(), r1.y(), r2.y(), r3.y())
                        // 0.5% spread tolerance -- well above the ULP
                        // jitter from the path round-trip (which is on
                        // the order of 1e-6 relative) but tight enough
                        // that a real per-corner-radii rrect drops out.
                        val uniformX = (rxMax - rxMin) <= NEAR_UNIFORM_EPS * (rxMax + 1f)
                        val uniformY = (ryMax - ryMin) <= NEAR_UNIFORM_EPS * (ryMax + 1f)
                        if (!uniformX || !uniformY) return null
                        val (x0, y0) = ctm.mapXY(rrect.rect().left, rrect.rect().top)
                        val (x1, y1) = ctm.mapXY(rrect.rect().right, rrect.rect().bottom)
                        val devBounds = SkRect.MakeLTRB(
                            minOf(x0, x1), minOf(y0, y1),
                            maxOf(x0, x1), maxOf(y0, y1),
                        )
                        val rx = 0.5f * (rxMin + rxMax)
                        val ry = 0.5f * (ryMin + ryMax)
                        val sx = abs(ctm.getScaleX())
                        val sy = abs(ctm.getScaleY())
                        return RRect(devBounds, rx * sx, ry * sy, op)
                    }
                    else -> return null
                }
            }

            return null
        }

        /** Epsilon to fold near-square ovals into circles. Device pixels. */
        private const val CIRCLE_EPS: Float = 1.0f / 1024f

        /** Relative tolerance for coercing a kNinePatch rrect with sub-ULP
         *  per-corner jitter (from the `SkPath` ↔ `SkRRect` round-trip)
         *  back to a uniform-corner simple rrect. 0.5 % is well above the
         *  observed jitter (~1e-6 relative) and tight enough that a true
         *  per-corner-radii rrect falls through. */
        private const val NEAR_UNIFORM_EPS: Float = 0.005f
    }
}
