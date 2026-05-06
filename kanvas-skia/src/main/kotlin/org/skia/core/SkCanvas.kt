package org.skia.core

import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkClipOp
import org.skia.foundation.SkColor
import org.skia.foundation.SkFont
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathDirection
import org.skia.foundation.SkRRect
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTextEncoding
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.math.SkScalar
import kotlin.math.ceil as kCeil
import kotlin.math.floor as kFloor

/**
 * The CTM is now a full 2 × 3 [SkMatrix] (Phase 4b — `rotate` / `skew` /
 * `concat` / `setMatrix` are real, not stubs). A source point `(x, y)` lands
 * at device coordinates `M.mapXY(x, y)`. Phase 1–3's `translate` and `scale`
 * helpers continue to work; under the hood they call [SkMatrix.preTranslate]
 * / [SkMatrix.preScale] on the active state's matrix.
 *
 * **Clip semantics under non-axis-aligned matrices** (i.e. `kx ≠ 0` or
 * `ky ≠ 0`): the device clip is the *axis-aligned bounding box* of the
 * rotated `clipRect` projected through the matrix. This is conservative —
 * pixels just outside the rotated quad but inside its bbox aren't masked
 * out — but matches all upstream GMs in our scope which never combine a
 * rotated CTM with `clipRect`. A true rotated AA clip is deferred to a
 * later phase.
 *
 * **`drawRect` under non-axis-aligned matrices**: re-routed through
 * [drawPath] (4-vertex polygon). The fast path through [SkBitmapDevice.drawRect]
 * stays for axis-aligned matrices, which covers every Phase 0–3 GM.
 */
public open class SkCanvas(rootDevice: SkBitmapDevice) {

    public constructor(bitmap: SkBitmap) : this(SkBitmapDevice(bitmap))

    /** The root (backing) device. Layers push their own devices on the stack. */
    public val device: SkBitmapDevice = rootDevice

    public val bitmap: SkBitmap get() = device.bitmap

    /**
     * One stack entry per `save` / `saveLayer`. Carries the active CTM
     * matrix and the clip in **current device coordinates**, plus the device
     * that draws land in.
     */
    private data class State(
        var matrix: SkMatrix,
        var clip: SkIRect,
        var device: SkBitmapDevice,
        /** Non-null iff this state was opened by `saveLayer`. */
        var layer: Layer? = null,
        /**
         * Phase 7q `clipPath` / `clipRRect` alpha mask, sized exactly
         * `clip.width() × clip.height()`. `0xFF` = fully inside the clip
         * region ; `0` = fully outside ; intermediate values = AA partial
         * coverage. `null` = pure rectangular clip.
         */
        var clipMask: ByteArray? = null,
    )

    /**
     * Bookkeeping for an active offscreen layer. On `restore` of the
     * matching state, the layer's bitmap is composited back into
     * [parentDevice] at `(originX, originY)` using [paint] (alpha and
     * SrcOver — full blend modes are out of scope).
     */
    private data class Layer(
        val parentDevice: SkBitmapDevice,
        val originX: Int,
        val originY: Int,
        val paint: SkPaint?,
    )

    private val stack: ArrayDeque<State> = ArrayDeque<State>().apply {
        addLast(State(SkMatrix.Identity, rootDevice.deviceClipBounds(), rootDevice))
    }

    private val top: State get() = stack.last()

    /** Read-only access to the current CTM. */
    public open fun getTotalMatrix(): SkMatrix = top.matrix

    public open fun save(): Int {
        val s = top
        // ClipMask shared by reference — clipPath/RRect always allocates a
        // fresh mask before AND-ing in, so the parent's stays untouched.
        stack.addLast(State(s.matrix, s.clip.copy(), s.device, clipMask = s.clipMask))
        return stack.size - 2
    }

    /**
     * Mirrors Skia's `SkCanvas::getSaveCount()` — returns the depth of the
     * save stack, where 1 = the implicit root state (empty CTM, full clip).
     * Each [save] / [saveLayer] increments the count by 1; each [restore]
     * decrements it (down to 1 minimum).
     */
    public open fun getSaveCount(): Int = stack.size

    /**
     * Mirrors Skia's `SkCanvas::restoreToCount(int)`. Pops save-stack frames
     * until [getSaveCount] == [saveCount]. A value `<= 1` collapses every
     * pending [save] / [saveLayer] (root state is preserved).
     */
    public open fun restoreToCount(saveCount: Int) {
        val target = saveCount.coerceAtLeast(1)
        while (stack.size > target) restore()
    }

    public open fun restore() {
        if (stack.size <= 1) return
        val popped = stack.removeLast()
        val layer = popped.layer ?: return
        // Composite the layer's bitmap back into the parent device using the
        // layer's paint (color modulates alpha, SrcOver is the only blend
        // mode in scope). The parent state is now `top`.
        layer.parentDevice.compositeFrom(
            popped.device,
            layer.originX,
            layer.originY,
            top.clip,
            layer.paint,
        )
    }

    public open fun translate(dx: SkScalar, dy: SkScalar) {
        val s = top
        s.matrix = s.matrix.preTranslate(dx, dy)
    }

    public open fun scale(sx: SkScalar, sy: SkScalar) {
        val s = top
        s.matrix = s.matrix.preScale(sx, sy)
    }

    /** Mirrors Skia's `SkCanvas::rotate(deg)` — pre-concat with a rotation around the origin. */
    public open fun rotate(deg: SkScalar) {
        val s = top
        s.matrix = s.matrix.preRotate(deg)
    }

    /**
     * Mirrors Skia's `SkCanvas::rotate(deg, px, py)` — pre-concat with a
     * rotation around an arbitrary pivot point.
     */
    public open fun rotate(deg: SkScalar, px: SkScalar, py: SkScalar) {
        val s = top
        s.matrix = s.matrix.preRotate(deg, px, py)
    }

    /** Mirrors Skia's `SkCanvas::skew(sx, sy)` — pre-concat with a skew. */
    public open fun skew(sx: SkScalar, sy: SkScalar) {
        val s = top
        s.matrix = s.matrix.preSkew(sx, sy)
    }

    /** Mirrors Skia's `SkCanvas::concat(SkMatrix)` — pre-concat with `mat`. */
    public open fun concat(mat: SkMatrix) {
        val s = top
        s.matrix = s.matrix.preConcat(mat)
    }

    /** Mirrors Skia's `SkCanvas::setMatrix(SkMatrix)` — replaces the CTM wholesale. */
    public open fun setMatrix(mat: SkMatrix) {
        val s = top
        s.matrix = mat
    }

    /** Mirrors Skia's `SkCanvas::resetMatrix()`. */
    public open fun resetMatrix() {
        val s = top
        s.matrix = SkMatrix.Identity
    }

    /**
     * Mirrors Skia's `SkCanvas::clipRect(rect)` (default non-AA).
     *
     * Non-AA clipping snaps the device-space rect to integer bounds via
     * `SkScalarRoundToInt` per component (round-half-up = `floor(c + 0.5)`),
     * matching Skia's `SkRasterClip::op(rect.round(), ...)`. This makes
     * the clip pixel-aligned with the non-AA `drawRect` rasterizer (see
     * `SkBitmapDevice.pixelEdge`) — a sub-pixel-edge `rect` consumed by
     * both `clipRect` and `drawRect` lands on the same integer pixel rows
     * and columns, so a `clipRect(r) ; drawRect(bigRect) ; drawRect(r)`
     * pattern leaves no 1-px remnants (cf. `ClipDrawDrawGM`,
     * `crbug.com/423834`).
     *
     * For non-axis-aligned matrices the rotated clip becomes a quad ; we
     * approximate with its axis-aligned bbox (conservative) using the
     * same rounding.
     */
    public open fun clipRect(rect: SkRect) {
        clipRect(rect, doAntiAlias = false)
    }

    /**
     * Mirrors Skia's `SkCanvas::clipRect(rect, doAntiAlias)`.
     *
     * - **`doAntiAlias = false`** (Skia default) — the clip snaps to integer
     *   bounds via round-half-up, matching the non-AA `drawRect` rasterizer.
     * - **`doAntiAlias = true`** — fractional-coverage AA clipping is not
     *   modelled yet ; we widen the clip outward via `floor(min)` / `ceil(max)`
     *   so paths that drew AA coverage flowing across the rect's logical
     *   boundary still get rasterized inside the device clip. This is the
     *   pre-edge-rounding-fix behaviour ; existing AA-path GMs that called
     *   `clipRect(rect, true)` (or `clipRect(rect)` from before the fix
     *   landed) keep their pixel output.
     */
    public open fun clipRect(rect: SkRect, doAntiAlias: Boolean) {
        val s = top
        val devRect = s.matrix.mapRect(rect)
        s.clip = if (doAntiAlias) {
            // AA clip — outward bbox preserves fractional edge coverage.
            SkIRect.MakeLTRB(
                maxOf(s.clip.left, kFloor(devRect.left.toDouble()).toInt()),
                maxOf(s.clip.top, kFloor(devRect.top.toDouble()).toInt()),
                minOf(s.clip.right, kCeil(devRect.right.toDouble()).toInt()),
                minOf(s.clip.bottom, kCeil(devRect.bottom.toDouble()).toInt()),
            )
        } else {
            // Non-AA clip — round-half-up, matches `pixelEdge` /
            // `SkScalarRoundToInt` upstream.
            SkIRect.MakeLTRB(
                maxOf(s.clip.left, kFloor(devRect.left.toDouble() + 0.5).toInt()),
                maxOf(s.clip.top, kFloor(devRect.top.toDouble() + 0.5).toInt()),
                minOf(s.clip.right, kFloor(devRect.right.toDouble() + 0.5).toInt()),
                minOf(s.clip.bottom, kFloor(devRect.bottom.toDouble() + 0.5).toInt()),
            )
        }
    }

    // ─── Phase 7q — clipPath / clipRRect (alpha-mask path clipping) ──────

    /**
     * Mirrors Skia's `SkCanvas::clipPath(path, doAntiAlias)`. Restricts
     * subsequent draws to the inside of [path] (or its complement when
     * `path.fillType` is `kInverse*`). Implementation : rasterise the path
     * into an 8-bit alpha coverage mask sized to the path's device-space
     * bounding box (intersected with the current clip), then bind it on
     * the active state's [State.clipMask]. The per-pixel write paths
     * (`blend`, `blendF16Premul`, `blendF16PremulMode`) modulate src.alpha
     * by the mask coverage. If a clip mask is already active, the new
     * path's mask is byte-wise AND-ed (multiplied) with the existing one
     * — clip stacks compose as path intersection.
     */
    public open fun clipPath(path: SkPath, doAntiAlias: Boolean = false) {
        clipPath(path, SkClipOp.kIntersect, doAntiAlias)
    }

    /**
     * Mirrors Skia's `SkCanvas::clipPath(path, op, doAntiAlias)`. The
     * [op] argument selects between
     * [SkClipOp.kIntersect] (the default — restrict draws to the inside
     * of [path]) and [SkClipOp.kDifference] (cut a hole — restrict draws
     * to the outside of [path]).
     *
     * Implementation notes :
     *  - **kIntersect** : the new clip bbox is the path's device-space
     *    bbox intersected with the parent clip ; the mask is `path AND
     *    parent_mask`.
     *  - **kDifference** : the new clip bbox is the parent clip
     *    unchanged (cutting a hole inside doesn't shrink the outer
     *    bound) ; the mask is `(255 - path) AND parent_mask`.
     *
     * Both ops AND-multiply with any pre-existing parent mask using
     * `(a*b + 127) / 255` rounding, so clip stacks compose correctly
     * across mixed intersect/difference sequences.
     */
    public open fun clipPath(path: SkPath, op: SkClipOp, doAntiAlias: Boolean = false) {
        val s = top
        when (op) {
            SkClipOp.kIntersect -> clipPathIntersect(s, path, doAntiAlias)
            SkClipOp.kDifference -> clipPathDifference(s, path, doAntiAlias)
        }
    }

    private fun clipPathIntersect(s: State, path: SkPath, doAntiAlias: Boolean) {
        val pathBoundsDev = if (path.fillType.isInverse()) {
            SkRect.MakeLTRB(
                s.clip.left.toFloat(), s.clip.top.toFloat(),
                s.clip.right.toFloat(), s.clip.bottom.toFloat(),
            )
        } else {
            s.matrix.mapRect(path.computeBounds())
        }

        val newClipBbox = SkIRect.MakeLTRB(
            maxOf(s.clip.left, kFloor(pathBoundsDev.left.toDouble()).toInt()),
            maxOf(s.clip.top, kFloor(pathBoundsDev.top.toDouble()).toInt()),
            minOf(s.clip.right, kCeil(pathBoundsDev.right.toDouble()).toInt()),
            minOf(s.clip.bottom, kCeil(pathBoundsDev.bottom.toDouble()).toInt()),
        )
        val w = newClipBbox.right - newClipBbox.left
        val h = newClipBbox.bottom - newClipBbox.top
        if (w <= 0 || h <= 0) {
            s.clip = SkIRect.MakeLTRB(s.clip.left, s.clip.top, s.clip.left, s.clip.top)
            s.clipMask = null
            return
        }

        val pathMask = rasterisePathMask(s, path, newClipBbox, doAntiAlias)

        // AND with the existing clipMask (if any) — clip stacks compose
        // via intersection.
        val parentMask = s.clipMask
        if (parentMask != null) {
            val parentL = s.clip.left
            val parentT = s.clip.top
            val parentW = s.clip.right - s.clip.left
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val absX = newClipBbox.left + x
                    val absY = newClipBbox.top + y
                    val pIdx = (absY - parentT) * parentW + (absX - parentL)
                    if (pIdx in 0 until parentMask.size) {
                        val pCov = parentMask[pIdx].toInt() and 0xFF
                        val newCov = pathMask[y * w + x].toInt() and 0xFF
                        pathMask[y * w + x] = ((pCov * newCov + 127) / 255).toByte()
                    } else {
                        pathMask[y * w + x] = 0
                    }
                }
            }
        }

        s.clip = newClipBbox
        s.clipMask = pathMask
    }

    /**
     * `clipPath(..., kDifference)` — cut the supplied path out of the
     * current clip. Bbox stays at the parent clip ; the new mask is
     * `(255 − pathCoverage) × parentCoverage`. If there was no parent
     * mask the result is just the inverted path coverage (255 outside,
     * 0 fully inside).
     */
    private fun clipPathDifference(s: State, path: SkPath, doAntiAlias: Boolean) {
        val w = s.clip.right - s.clip.left
        val h = s.clip.bottom - s.clip.top
        if (w <= 0 || h <= 0) {
            s.clipMask = null
            return
        }
        val pathMask = rasterisePathMask(s, path, s.clip, doAntiAlias)
        val parentMask = s.clipMask
        val newMask = ByteArray(w * h)
        for (i in 0 until w * h) {
            val pCov = pathMask[i].toInt() and 0xFF
            val invPath = 255 - pCov
            val parentCov = parentMask?.let { it[i].toInt() and 0xFF } ?: 255
            newMask[i] = ((parentCov * invPath + 127) / 255).toByte()
        }
        s.clipMask = newMask
    }

    /**
     * Helper : rasterise [path] into an 8-bit alpha coverage [ByteArray]
     * sized to the supplied device-space [bbox]. The path's source-space
     * coordinates are mapped through the active CTM, then translated so
     * `(bbox.left, bbox.top)` lands at mask origin `(0, 0)`. Returned
     * buffer is `0xFF` inside the path, `0` outside, AA values on the
     * edge.
     */
    private fun rasterisePathMask(
        s: State, path: SkPath, bbox: SkIRect, doAntiAlias: Boolean,
    ): ByteArray {
        val w = bbox.right - bbox.left
        val h = bbox.bottom - bbox.top
        val maskBitmap = SkBitmap(w, h).also { it.eraseColor(0) }
        val maskDevice = SkBitmapDevice(maskBitmap)
        val maskClip = SkIRect.MakeWH(w, h)
        val maskCtm = s.matrix.postTranslate(
            -bbox.left.toFloat(), -bbox.top.toFloat(),
        )
        val whitePaint = SkPaint().apply {
            color = org.skia.foundation.SK_ColorWHITE
            blendMode = SkBlendMode.kSrc
            isAntiAlias = doAntiAlias
        }
        maskDevice.drawPath(path, maskCtm, maskClip, whitePaint)
        val out = ByteArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                out[row + x] =
                    org.skia.foundation.SkColorGetA(maskBitmap.getPixel(x, y)).toByte()
            }
        }
        return out
    }

    /**
     * Mirrors Skia's `SkCanvas::clipRRect(rrect, doAntiAlias)`. Delegates
     * to [clipPath] via [SkPath.RRect].
     */
    public open fun clipRRect(rrect: SkRRect, doAntiAlias: Boolean = false) {
        clipPath(SkPath.RRect(rrect), SkClipOp.kIntersect, doAntiAlias)
    }

    /**
     * Mirrors Skia's `SkCanvas::clipRRect(rrect, op, doAntiAlias)`.
     */
    public open fun clipRRect(rrect: SkRRect, op: SkClipOp, doAntiAlias: Boolean = false) {
        clipPath(SkPath.RRect(rrect), op, doAntiAlias)
    }

    /**
     * Mirrors Skia's `SkCanvas::clipRect(rect, op, doAntiAlias)`. For
     * [SkClipOp.kIntersect] this is the existing fast path ; for
     * [SkClipOp.kDifference] we route through [clipPath] (a 4-vertex
     * rect path), which gives us the alpha-mask "cut a hole" semantics
     * the rasterizer needs.
     */
    public open fun clipRect(rect: SkRect, op: SkClipOp, doAntiAlias: Boolean = false) {
        when (op) {
            SkClipOp.kIntersect -> clipRect(rect, doAntiAlias)
            SkClipOp.kDifference -> clipPath(SkPath.Rect(rect), SkClipOp.kDifference, doAntiAlias)
        }
    }

    /** Bind the active state's clipMask onto the device before each draw. */
    private fun bindClip(s: State) {
        s.device.setActiveClip(s.clipMask, s.clip)
    }

    public open fun drawRect(rect: SkRect, paint: SkPaint) {
        val s = top
        // Fast path requires : axis-aligned CTM, no shader, no path
        // effect, no mask filter. The fast path goes directly into the
        // device's rect rasterizer and bypasses pathEffect/maskFilter
        // (Phase 7p / 7c) ; falling through to drawPath honours both.
        if (s.matrix.isAxisAligned &&
            paint.shader == null &&
            paint.pathEffect == null &&
            paint.maskFilter == null) {
            // Fast path: solid colour, axis-aligned CTM. Pre-compute the
            // device rect and route through SkBitmapDevice's hard-edge /
            // analytic-AA rect rasterizer.
            val (x0, y0) = s.matrix.mapXY(rect.left, rect.top)
            val (x1, y1) = s.matrix.mapXY(rect.right, rect.bottom)
            val devRect = SkRect.MakeLTRB(minOf(x0, x1), minOf(y0, y1), maxOf(x0, x1), maxOf(y0, y1))
            bindClip(s)
            s.device.drawRect(devRect, s.clip, paint)
        } else {
            // Either rotated/skewed CTM (4-vertex polygon), shader-driven
            // colour, dasher / mask filter — all routed through drawPath.
            drawPath(SkPath.Rect(rect), paint)
        }
    }

    /**
     * Phase 3a: draw a polygon path filled with `paint.color` under the
     * current path fill rule (`kWinding` / `kEvenOdd`). The path's verb
     * stream is transformed point-by-point into device space using the
     * current CTM, so callers continue to express geometry in source
     * coordinates.
     */
    public open fun drawPath(path: SkPath, paint: SkPaint) {
        val s = top
        bindClip(s)
        s.device.drawPath(path, s.matrix, s.clip, paint)
    }

    /**
     * Mirrors Skia's `SkCanvas::drawOval`. Emits an elliptical contour via
     * [SkPath.Oval] and routes through [drawPath]. Convenience wrapper —
     * the stand-alone path can be reused if the same oval is drawn many
     * times.
     */
    public open fun drawOval(oval: SkRect, paint: SkPaint) {
        drawPath(SkPath.Oval(oval), paint)
    }

    /**
     * Mirrors Skia's `SkCanvas::drawCircle`. Convenience wrapper around
     * [SkPath.Circle] + [drawPath].
     */
    public open fun drawCircle(cx: SkScalar, cy: SkScalar, radius: SkScalar, paint: SkPaint) {
        if (radius <= 0f) return
        drawPath(SkPath.Circle(cx, cy, radius), paint)
    }

    /**
     * Mirrors Skia's `SkCanvas::drawRRect`. Routes through [SkPath.RRect],
     * which dispatches on [SkRRect.Type] to the right cubic-Bézier or
     * straight-line contour. Empty rrects are a no-op.
     */
    public open fun drawRRect(rrect: SkRRect, paint: SkPaint) {
        if (rrect.isEmpty()) return
        drawPath(SkPath.RRect(rrect), paint)
    }

    /**
     * Mirrors Skia's `SkCanvas::drawRoundRect(rect, rx, ry, paint)`. Builds a
     * uniform-corner [SkRRect] via [SkRRect.MakeRectXY] and routes through
     * [drawRRect]. Convenience wrapper — the stand-alone rrect can be reused
     * if the same shape is drawn many times.
     */
    public open fun drawRoundRect(rect: SkRect, rx: SkScalar, ry: SkScalar, paint: SkPaint) {
        drawRRect(SkRRect.MakeRectXY(rect, rx, ry), paint)
    }

    /**
     * Mirrors Skia's `SkCanvas::drawDRRect(outer, inner, paint)` — fills the
     * "donut" between the [outer] and [inner] rounded rectangles. Built as a
     * single path with the outer ring in [SkPathDirection.kCW] and the inner
     * ring in [SkPathDirection.kCCW], which the default `kWinding` fill rule
     * paints as the band between them.
     */
    public open fun drawDRRect(outer: SkRRect, inner: SkRRect, paint: SkPaint) {
        if (outer.isEmpty()) return
        if (inner.isEmpty()) {
            drawRRect(outer, paint)
            return
        }
        val builder = SkPathBuilder()
            .addRRect(outer, SkPathDirection.kCW)
            .addRRect(inner, SkPathDirection.kCCW)
        drawPath(builder.detach(), paint)
    }

    /**
     * Mirrors Skia's `SkCanvas::drawLine(x0, y0, x1, y1, paint)`. Emits a
     * 2-point open path (`moveTo` + `lineTo`) and routes through [drawPath].
     */
    public open fun drawLine(x0: SkScalar, y0: SkScalar, x1: SkScalar, y1: SkScalar, paint: SkPaint) {
        val path = SkPathBuilder().moveTo(x0, y0).lineTo(x1, y1).detach()
        drawPath(path, paint)
    }

    /**
     * Mirrors Skia's `SkCanvas::drawArc(oval, startAngleDeg, sweepAngleDeg, useCenter, paint)`.
     */
    public open fun drawArc(
        oval: SkRect,
        startAngleDeg: SkScalar,
        sweepAngleDeg: SkScalar,
        useCenter: Boolean,
        paint: SkPaint,
    ) {
        if (sweepAngleDeg == 0f) return
        val builder = SkPathBuilder()
        if (useCenter) {
            // Pie slice: centre, then arc, then close.
            val cx = (oval.left + oval.right) * 0.5f
            val cy = (oval.top + oval.bottom) * 0.5f
            builder.moveTo(cx, cy)
            builder.arcTo(oval, startAngleDeg, sweepAngleDeg, forceMoveTo = false)
            builder.close()
        } else {
            // Open curve, no centre.
            builder.arcTo(oval, startAngleDeg, sweepAngleDeg, forceMoveTo = true)
        }
        drawPath(builder.detach(), paint)
    }

    /**
     * Draw `image` at device-space position `(x, y)`, sampled with
     * `sampling`. Mirrors Skia's `SkCanvas::drawImage(image, x, y, sampling, paint)`.
     */
    public open fun drawImage(
        image: SkImage,
        x: SkScalar,
        y: SkScalar,
        sampling: SkSamplingOptions = SkSamplingOptions.Default,
        paint: SkPaint? = null,
    ) {
        val w = image.width.toFloat()
        val h = image.height.toFloat()
        drawImageRect(
            image,
            SkRect.MakeWH(w, h),
            SkRect.MakeXYWH(x, y, w, h),
            sampling,
            paint,
            SrcRectConstraint.kFast,
        )
    }

    /**
     * Mirrors Skia's `SkCanvas::drawImageRect(image, src, dst, sampling, paint, constraint)`.
     * Under axis-aligned matrices the dst rect is mapped through the CTM to
     * an axis-aligned device rect (current code path). Under non-axis-aligned
     * matrices the call is dropped — true rotated `drawImageRect` would need
     * texture-space sampling with an inverse matrix (deferred).
     */
    public open fun drawImageRect(
        image: SkImage,
        src: SkRect,
        dst: SkRect,
        sampling: SkSamplingOptions = SkSamplingOptions.Default,
        paint: SkPaint? = null,
        constraint: SrcRectConstraint = SrcRectConstraint.kStrict,
    ) {
        val s = top
        if (!s.matrix.isAxisAligned) {
            // TODO(phase 4b+) rotated drawImageRect — needs sampler with inverse CTM.
            return
        }
        val (x0, y0) = s.matrix.mapXY(dst.left, dst.top)
        val (x1, y1) = s.matrix.mapXY(dst.right, dst.bottom)
        val devDst = SkRect.MakeLTRB(minOf(x0, x1), minOf(y0, y1), maxOf(x0, x1), maxOf(y0, y1))
        bindClip(s)
        s.device.drawImageRect(image, src, devDst, sampling, paint, constraint, s.clip)
    }

    /**
     * Mirrors Skia's `SkCanvas::drawColor(SkColor, SkBlendMode)`
     * (`SkCanvas.h:1235`). Fills the active clip with [color] under [mode]
     * — defaults to `kSrcOver` like upstream. `clear` is the `kSrc` flavour
     * (see [clear]).
     *
     * Whole-clip + `kSrc` is fast-pathed through `bitmap.eraseColor` (no
     * per-pixel scan); every other case routes through `drawPaint`.
     */
    public open fun drawColor(color: SkColor, mode: SkBlendMode = SkBlendMode.kSrcOver) {
        val s = top
        if (mode == SkBlendMode.kSrc && s.clip == s.device.deviceClipBounds()) {
            bitmap.eraseColor(color)
            return
        }
        val paint = SkPaint(color).apply { blendMode = mode }
        bindClip(s)
        s.device.drawPaint(s.matrix, s.clip, paint)
    }

    /**
     * Mirrors Skia's `SkCanvas::clear(SkColor)` (`SkCanvas.h:1263`) —
     * `drawColor` with [SkBlendMode.kSrc]. Wipes the clip to [color]
     * regardless of the existing destination.
     */
    public open fun clear(color: SkColor): Unit = drawColor(color, SkBlendMode.kSrc)

    /**
     * Mirrors Skia's `SkCanvas::drawPaint`. Fills the current clip with
     * `paint.color` (or `paint.shader`, if set) via the paint's blend mode.
     * `drawPaint` has "infinite rect" semantics, so the only spatial bound
     * is the clip; the CTM affects the shader's local-to-device mapping
     * only (and is a no-op for solid-colour paints).
     */
    public open fun drawPaint(paint: SkPaint) {
        val s = top
        bindClip(s)
        s.device.drawPaint(s.matrix, s.clip, paint)
    }

    /**
     * Mirrors Skia's `SkCanvas::drawString(const char[], SkScalar, SkScalar,
     * const SkFont&, const SkPaint&)` (SkCanvas.h:1861).
     *
     * **T3 status — real glyph rendering** via the existing path-fill
     * pipeline. Pipeline:
     *  1. dispatch to `font.typeface.makeTextPath(...)` — for the AWT
     *     backend this builds an [SkPath] from `GlyphVector.getOutline()`
     *     positioned so that the baseline lands at `(x, y)` in source
     *     coords (no CTM applied yet);
     *  2. delegate to [drawPath], which applies the current CTM and runs
     *     the standard scanline-fill (AA per `paint.isAntiAlias`) +
     *     `paint.shader` + `paint.blendMode` machinery.
     *
     * The base [SkTypeface] returns `null`, which we treat as a no-op
     * (matches the empty-typeface case from T1 plus protects callers
     * that pass `SkTypeface.MakeEmpty()`).
     *
     * Limitations (cf. `archives/MIGRATION_PLAN_TEXT.md` §T3):
     *  - `font.edging == kSubpixelAntiAlias` is downgraded to
     *    `kAntiAlias` silently — the path-fill rasteriser only does
     *    coverage AA, not LCD subpixel AA.
     *  - No glyph mask cache yet (T5).
     */
    public open fun drawString(
        str: String,
        x: SkScalar,
        y: SkScalar,
        font: SkFont,
        paint: SkPaint,
    ) {
        if (str.isEmpty()) return
        val path = font.makeTextPath(str, x, y) ?: return
        // Glyph fills are AA whenever the font asks for it. Skia's
        // `paint.isAntiAlias` is independent — we honour it by ANDing
        // with the font edging: if either says "alias", we go alias.
        // For T3 we keep paint.isAntiAlias as the source of truth and
        // let drawPath decide; future slices may refine.
        drawPath(path, paint)
    }

    /**
     * Mirrors Skia's `SkCanvas::drawSimpleText(const void*, size_t,
     * SkTextEncoding, SkScalar, SkScalar, const SkFont&, const SkPaint&)`
     * (SkCanvas.h:1834). Same rendering pipeline as [drawString]; the
     * `byteLength` and `encoding` parameters are honoured in the sense
     * that T3 only supports `SkTextEncoding.kUTF8` and bounded substring
     * lengths (kUTF16/32/GlyphID accept the call but treat the input as
     * UTF-8 — see plan §T1).
     *
     * @param byteLength number of bytes / code units to consider in [text].
     */
    public open fun drawSimpleText(
        text: String,
        byteLength: Int,
        @Suppress("UNUSED_PARAMETER") encoding: SkTextEncoding,
        x: SkScalar,
        y: SkScalar,
        font: SkFont,
        paint: SkPaint,
    ) {
        if (text.isEmpty() || byteLength == 0) return
        val sub = if (byteLength >= text.length) text else text.substring(0, byteLength)
        drawString(sub, x, y, font, paint)
    }

    /**
     * Mirrors Skia's `SkCanvas::saveLayer(bounds, paint)`. Allocates an
     * offscreen bitmap-backed device matching the device-space bbox of
     * [bounds] (intersected with the current clip), then redirects all
     * subsequent draws into it until the matching [restore] composites the
     * layer back onto the parent device using [paint] (alpha modulation +
     * SrcOver — no full blend mode dispatch in this slice).
     *
     * Under non-axis-aligned matrices the bounds bbox is the bounding box
     * of the rotated quad (conservative). Layer-local CTM is the parent
     * matrix post-translated by `(-originX, -originY)` so source-space
     * coordinates land in the same place as before, just shifted by the
     * layer origin.
     */
    public open fun saveLayer(bounds: SkRect?, paint: SkPaint?): Int {
        val s = top
        val layerBounds: SkIRect = if (bounds == null) {
            s.clip
        } else {
            val devBounds = s.matrix.mapRect(bounds)
            SkIRect.MakeLTRB(
                maxOf(s.clip.left, kFloor(devBounds.left.toDouble()).toInt()),
                maxOf(s.clip.top, kFloor(devBounds.top.toDouble()).toInt()),
                minOf(s.clip.right, kCeil(devBounds.right.toDouble()).toInt()),
                minOf(s.clip.bottom, kCeil(devBounds.bottom.toDouble()).toInt()),
            )
        }

        // Empty layer ⇒ degenerate to a plain `save` with an empty clip so
        // subsequent draws are silently dropped (matches Skia's `nothingToDraw`
        // bailout — we just intersect to (0,0,0,0) to avoid allocating a 0×0
        // bitmap, which `SkBitmap` doesn't accept).
        val w = layerBounds.right - layerBounds.left
        val h = layerBounds.bottom - layerBounds.top
        if (w <= 0 || h <= 0) {
            stack.addLast(State(
                s.matrix,
                SkIRect.MakeLTRB(s.clip.left, s.clip.top, s.clip.left, s.clip.top),
                s.device,
                layer = null,
            ))
            return stack.size - 2
        }

        // Inherit the parent's `colorType` so multi-layer composition stays
        // in the same precision regime — under an F16 root, layers are F16
        // too; otherwise both sides remain 8888.
        val layerBitmap = SkBitmap(w, h, s.device.bitmap.colorSpace, s.device.bitmap.colorType)
            .also { it.eraseColor(0) }
        val layerDevice = SkBitmapDevice(layerBitmap)
        val originX = layerBounds.left
        val originY = layerBounds.top

        // Layer-local CTM: the parent matrix post-translated by `-origin`,
        // so a source point that used to land at parent device `(px, py)`
        // now lands at layer coords `(px - originX, py - originY)`.
        val layerMatrix = s.matrix.copy(
            tx = s.matrix.tx - originX,
            ty = s.matrix.ty - originY,
        )
        val newState = State(
            matrix = layerMatrix,
            clip = SkIRect.MakeLTRB(
                maxOf(0, s.clip.left - originX),
                maxOf(0, s.clip.top - originY),
                minOf(w, s.clip.right - originX),
                minOf(h, s.clip.bottom - originY),
            ),
            device = layerDevice,
            layer = Layer(s.device, originX, originY, paint),
        )
        stack.addLast(newState)
        return stack.size - 2
    }

    /** Convenience overload mirroring `SkCanvas::saveLayer()`. */
    public open fun saveLayer(): Int = saveLayer(null, null)

    /**
     * Mirrors Skia's `SkCanvas::saveLayer(bounds, paint, flags)`. The
     * [flags] field is accepted for API compatibility but ignored.
     */
    public open fun saveLayer(bounds: SkRect?, paint: SkPaint?, flags: SaveLayerFlags): Int =
        saveLayer(bounds, paint)

    public open val width: Int get() = device.width
    public open val height: Int get() = device.height
}
