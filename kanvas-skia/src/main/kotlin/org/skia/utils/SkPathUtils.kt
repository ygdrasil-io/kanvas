package org.skia.utils

import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkStroker
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * Mirrors Skia's
 * [`skpathutils`](https://github.com/google/skia/blob/main/include/core/SkPathUtils.h)
 * namespace (R1 port).
 *
 * `FillPathWithPaint` converts a stroked source path into the filled
 * outline that the rasterizer would actually paint when the supplied
 * [SkPaint] is in stroke (or stroke-and-fill) mode. The pipeline mirrors
 * upstream :
 *
 * ```
 *   src --pathEffect.filterPath(ctm)--> mid --stroker.stroke()--> dst
 * ```
 *
 * Each stage is skipped when the paint doesn't request it :
 *  - no [SkPaint.pathEffect] → `mid = src`
 *  - paint style is [SkPaint.Style.kFill_Style] → `dst = mid` (no stroke).
 *
 * R1 simplifications :
 *  - The optional [cullRect] argument is accepted for API parity but is
 *    not yet threaded through to [org.skia.foundation.SkPathEffect.filterPath]
 *    — kanvas-skia's [SkPathEffect][org.skia.foundation.SkPathEffect]
 *    interface doesn't expose a cull-rect knob yet (`filterPath` takes only
 *    the input + CTM).
 *  - The `ctm` parameter in upstream is replaced by a scalar [resScale]
 *    (the same hint [SkStroker.fromPaint] takes). Callers with a real CTM
 *    can pass `ctm.computeMaxScale()`. Identity ⇒ `1f`.
 *  - Return value : `true` when the result was successfully written to
 *    [dst]. Upstream's "hairline" return (false ⇒ caller should stroke as
 *    a hairline) maps to a degenerate stroke width of 0 — we still emit
 *    the source path unchanged in that case and return `true`, because
 *    kanvas-skia has no separate hairline raster path yet (see
 *    [SkStroker]'s comment block on hairline width ≤ 0).
 */
public object SkPathUtils {

    /**
     * Apply [paint]'s path effect + stroker to [srcPath], writing the
     * filled outline into [dstPath]. The destination is **reset** before
     * being filled. Returns `true` on success (always, in R1 — see class
     * doc).
     *
     * @param srcPath the source path (read-only).
     * @param paint   provides stroke parameters (width, cap, join, miter,
     *                style) and the optional path effect to pre-apply.
     * @param dstPath the destination builder — reset, then filled.
     * @param cullRect optional bounds passed through to the path effect.
     *                 R1 : accepted but currently ignored — see class doc.
     * @param resScale CTM-scale hint forwarded to [SkStroker.fromPaint].
     *                 Defaults to `1f` (identity CTM). Real callers pass
     *                 `ctm.computeMaxScale()`.
     */
    public fun FillPathWithPaint(
        srcPath: SkPath,
        paint: SkPaint,
        dstPath: SkPathBuilder,
        cullRect: SkRect? = null,
        resScale: SkScalar = 1f,
    ): Boolean {
        // R1: cullRect is part of the upstream signature but not yet
        // plumbed through SkPathEffect.filterPath. Accept-and-discard.
        @Suppress("UNUSED_PARAMETER") val _cull = cullRect

        // Stage 1 : path effect (optional).
        val effect = paint.pathEffect
        val ctm: SkMatrix = SkMatrix.I()
        val midPath: SkPath = if (effect != null) {
            effect.filterPath(srcPath, ctm) ?: srcPath
        } else {
            srcPath
        }

        // Stage 2 : stroke (only when the paint asks for it).
        val outline: SkPath = when (paint.style) {
            SkPaint.Style.kFill_Style -> midPath
            SkPaint.Style.kStroke_Style,
            SkPaint.Style.kStrokeAndFill_Style ->
                SkStroker.fromPaint(paint, resScale).stroke(midPath)
        }

        // Stage 3 : emit into dst.
        dstPath.reset()
        if (!outline.isEmpty()) {
            dstPath.addPath(outline)
        }
        dstPath.setFillType(outline.fillType)
        return true
    }
}
