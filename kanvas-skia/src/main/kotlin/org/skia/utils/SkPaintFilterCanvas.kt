package org.skia.utils

import org.skia.core.SkCanvas
import org.skia.core.SrcRectConstraint
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkClipOp
import org.skia.foundation.SkColor
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * Proxy [SkCanvas] that intercepts every paint-bearing draw,
 * lets a subclass mutate / inspect the paint, then forwards (or
 * drops) the draw to a wrapped target canvas.
 *
 * Mirrors Skia's
 * [`SkPaintFilterCanvas`](https://github.com/google/skia/blob/main/include/utils/SkPaintFilterCanvas.h).
 *
 * **Use cases** :
 *  - Force-disable AA across an entire GM ("`paint.isAntiAlias = false`")
 *    for performance comparison.
 *  - Force a specific blend mode for diagnostic capture.
 *  - Drop draws that match a predicate (e.g. skip every text draw to
 *    isolate vector regressions).
 *
 * **Mechanics** :
 *  - The wrapper extends [SkCanvas] with a `1 × 1` dummy bitmap so its
 *    own state stack ([getTotalMatrix], [getSaveCount]) stays accurate
 *    for analysis. The dummy bitmap is never drawn into.
 *  - State changes (save / restore / translate / scale / clip…) are
 *    forwarded to **both** `super` (the local stack) and the target
 *    canvas (so the target's CTM / clip stays in lockstep).
 *  - Every draw method clones the supplied [SkPaint], invokes
 *    [onFilter] on the copy, and — if `onFilter` returns `true` —
 *    forwards the draw to the target with the (possibly mutated)
 *    paint. `onFilter == false` skips the draw entirely.
 *  - Image draws receive a synthesised paint when the caller passed
 *    `null` (so subclasses always see a non-null paint to inspect or
 *    mutate). The synthesised paint is `SkPaint()` with default values.
 *
 * Subclass example :
 * ```kotlin
 * val noAa = object : SkPaintFilterCanvas(target) {
 *     override fun onFilter(paint: SkPaint): Boolean {
 *         paint.isAntiAlias = false
 *         return true
 *     }
 * }
 * gm.draw(noAa)  // every draw goes to `target` with AA forced off
 * ```
 *
 * For one-shot use, the convenience [Companion.invoke] factory takes a
 * lambda and returns a fresh anonymous subclass :
 * ```kotlin
 * val noAa = SkPaintFilterCanvas(target) { paint ->
 *     paint.isAntiAlias = false ; true
 * }
 * ```
 */
public abstract class SkPaintFilterCanvas(
    private val target: SkCanvas,
) : SkCanvas(SkBitmap(1, 1)) {

    /**
     * Subclasses override : called with a *copy* of every draw's
     * paint before the draw is forwarded to the target. Mutate the
     * paint in place to alter the draw ; return `true` to forward
     * the draw, `false` to skip it entirely.
     *
     * The supplied [paint] is **never null** — image draws with no
     * caller-supplied paint receive a fresh default `SkPaint()` so
     * subclasses always have a value to work with.
     */
    protected abstract fun onFilter(paint: SkPaint): Boolean

    override val width: Int get() = target.width
    override val height: Int get() = target.height

    // ─── State ops — forward to super (local stack) AND target ────────

    override fun save(): Int {
        target.save()
        return super.save()
    }

    override fun restore() {
        super.restore()
        target.restore()
    }

    override fun restoreToCount(saveCount: Int) {
        // Both stacks are kept in lockstep, so popping by depth in
        // either order yields the same final depth — but we route
        // through the per-call restore() to honour any subclass
        // that overrides it.
        while (getSaveCount() > saveCount.coerceAtLeast(1)) restore()
    }

    override fun saveLayer(bounds: SkRect?, paint: SkPaint?): Int {
        // Layer paints aren't filtered (they're a special-case used
        // by the saveLayer machinery, not by drawing primitives).
        target.saveLayer(bounds, paint)
        return super.saveLayer(bounds, paint)
    }

    override fun translate(dx: SkScalar, dy: SkScalar) {
        super.translate(dx, dy)
        target.translate(dx, dy)
    }

    override fun scale(sx: SkScalar, sy: SkScalar) {
        super.scale(sx, sy)
        target.scale(sx, sy)
    }

    override fun rotate(deg: SkScalar) {
        super.rotate(deg)
        target.rotate(deg)
    }

    override fun rotate(deg: SkScalar, px: SkScalar, py: SkScalar) {
        super.rotate(deg, px, py)
        target.rotate(deg, px, py)
    }

    override fun skew(sx: SkScalar, sy: SkScalar) {
        super.skew(sx, sy)
        target.skew(sx, sy)
    }

    override fun concat(mat: SkMatrix) {
        super.concat(mat)
        target.concat(mat)
    }

    override fun setMatrix(mat: SkMatrix) {
        super.setMatrix(mat)
        target.setMatrix(mat)
    }

    override fun resetMatrix() {
        super.resetMatrix()
        target.resetMatrix()
    }

    override fun clipRect(rect: SkRect) {
        super.clipRect(rect)
        target.clipRect(rect)
    }

    override fun clipRect(rect: SkRect, doAntiAlias: Boolean) {
        super.clipRect(rect, doAntiAlias)
        target.clipRect(rect, doAntiAlias)
    }

    override fun clipRect(rect: SkRect, op: SkClipOp, doAntiAlias: Boolean) {
        super.clipRect(rect, op, doAntiAlias)
        target.clipRect(rect, op, doAntiAlias)
    }

    override fun clipRRect(rrect: SkRRect, doAntiAlias: Boolean) {
        super.clipRRect(rrect, doAntiAlias)
        target.clipRRect(rrect, doAntiAlias)
    }

    override fun clipRRect(rrect: SkRRect, op: SkClipOp, doAntiAlias: Boolean) {
        super.clipRRect(rrect, op, doAntiAlias)
        target.clipRRect(rrect, op, doAntiAlias)
    }

    override fun clipPath(path: SkPath, doAntiAlias: Boolean) {
        super.clipPath(path, doAntiAlias)
        target.clipPath(path, doAntiAlias)
    }

    override fun clipPath(path: SkPath, op: SkClipOp, doAntiAlias: Boolean) {
        super.clipPath(path, op, doAntiAlias)
        target.clipPath(path, op, doAntiAlias)
    }

    // ─── Draw ops — clone paint, filter, forward to target ────────────

    override fun drawRect(rect: SkRect, paint: SkPaint) {
        val p = paint.copy()
        if (onFilter(p)) target.drawRect(rect, p)
    }

    override fun drawOval(oval: SkRect, paint: SkPaint) {
        val p = paint.copy()
        if (onFilter(p)) target.drawOval(oval, p)
    }

    override fun drawCircle(cx: SkScalar, cy: SkScalar, radius: SkScalar, paint: SkPaint) {
        val p = paint.copy()
        if (onFilter(p)) target.drawCircle(cx, cy, radius, p)
    }

    override fun drawLine(x0: SkScalar, y0: SkScalar, x1: SkScalar, y1: SkScalar, paint: SkPaint) {
        val p = paint.copy()
        if (onFilter(p)) target.drawLine(x0, y0, x1, y1, p)
    }

    override fun drawPath(path: SkPath, paint: SkPaint) {
        val p = paint.copy()
        if (onFilter(p)) target.drawPath(path, p)
    }

    override fun drawRRect(rrect: SkRRect, paint: SkPaint) {
        val p = paint.copy()
        if (onFilter(p)) target.drawRRect(rrect, p)
    }

    override fun drawRoundRect(rect: SkRect, rx: SkScalar, ry: SkScalar, paint: SkPaint) {
        val p = paint.copy()
        if (onFilter(p)) target.drawRoundRect(rect, rx, ry, p)
    }

    override fun drawDRRect(outer: SkRRect, inner: SkRRect, paint: SkPaint) {
        val p = paint.copy()
        if (onFilter(p)) target.drawDRRect(outer, inner, p)
    }

    override fun drawArc(
        oval: SkRect,
        startAngle: SkScalar,
        sweepAngle: SkScalar,
        useCenter: Boolean,
        paint: SkPaint,
    ) {
        val p = paint.copy()
        if (onFilter(p)) target.drawArc(oval, startAngle, sweepAngle, useCenter, p)
    }

    override fun drawPoints(
        mode: PointMode,
        points: Array<org.skia.math.SkPoint>,
        paint: SkPaint,
    ) {
        val p = paint.copy()
        if (onFilter(p)) target.drawPoints(mode, points, p)
    }

    override fun drawImage(
        image: SkImage,
        x: SkScalar,
        y: SkScalar,
        sampling: SkSamplingOptions,
        paint: SkPaint?,
    ) {
        // Synthesise a default paint for the filter when the caller
        // passed null — subclasses always see something to inspect.
        val p = (paint ?: SkPaint()).copy()
        if (onFilter(p)) target.drawImage(image, x, y, sampling, p)
    }

    override fun drawImageRect(
        image: SkImage,
        src: SkRect,
        dst: SkRect,
        sampling: SkSamplingOptions,
        paint: SkPaint?,
        constraint: SrcRectConstraint,
    ) {
        val p = (paint ?: SkPaint()).copy()
        if (onFilter(p)) target.drawImageRect(image, src, dst, sampling, p, constraint)
    }

    override fun drawColor(color: SkColor, mode: SkBlendMode) {
        val p = SkPaint(color).apply { blendMode = mode }
        if (onFilter(p)) target.drawColor(p.color, p.blendMode)
    }

    override fun drawPaint(paint: SkPaint) {
        val p = paint.copy()
        if (onFilter(p)) target.drawPaint(p)
    }

    override fun drawPatch(
        cubics: Array<org.skia.math.SkPoint>,
        colors: IntArray?,
        texCoords: Array<org.skia.math.SkPoint>?,
        blendMode: SkBlendMode,
        paint: SkPaint,
    ) {
        val p = paint.copy()
        if (onFilter(p)) target.drawPatch(cubics, colors, texCoords, blendMode, p)
    }

    public companion object {
        /**
         * Convenience factory for one-shot filter use. Builds an
         * anonymous [SkPaintFilterCanvas] subclass whose
         * [onFilter] delegates to [filter].
         *
         * Usage :
         * ```kotlin
         * val noAa = SkPaintFilterCanvas(target) { paint ->
         *     paint.isAntiAlias = false
         *     true
         * }
         * ```
         */
        public operator fun invoke(
            target: SkCanvas,
            filter: (SkPaint) -> Boolean,
        ): SkPaintFilterCanvas = object : SkPaintFilterCanvas(target) {
            override fun onFilter(paint: SkPaint): Boolean = filter(paint)
        }
    }
}
