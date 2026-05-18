package org.skia.core

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

    /** Axis-aligned rect clip. Bounds are in **device** coords. */
    public data class Rect(val bounds: SkRect) : SkClipShape

    /** Axis-aligned ellipse inscribed in [bounds] (device coords). */
    public data class Oval(val bounds: SkRect) : SkClipShape

    /** Circle clip at device-space `(cx, cy)` with radius [r]. */
    public data class Circle(val cx: Float, val cy: Float, val r: Float) : SkClipShape

    /**
     * Rounded-rect clip. [bounds] is the outer bbox (device coords), [rx]
     * / [ry] are the **uniform** corner radii (kRect / kOval / kSimple
     * sub-types). Non-uniform rrects (kNinePatch / kComplex) are not
     * represented here -- the detector returns `null` for those.
     */
    public data class RRect(val bounds: SkRect, val rx: Float, val ry: Float) : SkClipShape

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
         */
        public fun tryDetect(path: SkPath, ctm: SkMatrix): SkClipShape? {
            if (!ctm.isAxisAligned) return null

            // Rect ?
            path.isRect()?.let { srcRect ->
                val (x0, y0) = ctm.mapXY(srcRect.left, srcRect.top)
                val (x1, y1) = ctm.mapXY(srcRect.right, srcRect.bottom)
                val devRect = SkRect.MakeLTRB(
                    minOf(x0, x1), minOf(y0, y1),
                    maxOf(x0, x1), maxOf(y0, y1),
                )
                return Rect(devRect)
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
                    )
                } else {
                    Oval(devBounds)
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
                            )
                        } else {
                            Oval(devBounds)
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
                        return RRect(devBounds, src.x() * sx, src.y() * sy)
                    }
                    else -> return null
                }
            }

            return null
        }

        /** Epsilon to fold near-square ovals into circles. Device pixels. */
        private const val CIRCLE_EPS: Float = 1.0f / 1024f
    }
}
