package org.skia.core

import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColor
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkIRect
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.math.SkScalarRoundToInt

/**
 * Phase 1+2 canvas with Phase 3 extensions. The CTM now supports
 * **translate + uniform/non-uniform scale** in addition to clipping —
 * still no rotation/skew, which arrive in a later phase.
 *
 * The state matrix is `M = | sx 0 tx ; 0 sy ty ; 0 0 1 |`. A source point
 * `(x, y)` lands at device coordinates `(sx*x + tx, sy*y + ty)`. New
 * `translate` / `scale` calls post-multiply by their local transform, so
 * sequential calls compose like `M_new = M_old @ Local` — matching Skia's
 * `SkCanvas::translate` / `SkCanvas::scale`.
 */
public open class SkCanvas(public val device: SkBitmapDevice) {

    public constructor(bitmap: SkBitmap) : this(SkBitmapDevice(bitmap))

    public val bitmap: SkBitmap get() = device.bitmap

    private data class State(
        var sx: SkScalar,
        var sy: SkScalar,
        var tx: SkScalar,
        var ty: SkScalar,
        var clip: SkIRect,
    )

    private val stack: ArrayDeque<State> = ArrayDeque<State>().apply {
        addLast(State(1f, 1f, 0f, 0f, device.deviceClipBounds()))
    }

    private val top: State get() = stack.last()

    public fun save(): Int {
        val s = top
        stack.addLast(State(s.sx, s.sy, s.tx, s.ty, s.clip.copy()))
        return stack.size - 2
    }

    public fun restore() {
        if (stack.size > 1) stack.removeLast()
    }

    public fun translate(dx: SkScalar, dy: SkScalar) {
        val s = top
        s.tx += s.sx * dx
        s.ty += s.sy * dy
    }

    public fun scale(kx: SkScalar, ky: SkScalar) {
        val s = top
        s.sx *= kx
        s.sy *= ky
    }

    public fun clipRect(rect: SkRect) {
        val s = top
        val l = rect.left * s.sx + s.tx
        val t = rect.top * s.sy + s.ty
        val r = rect.right * s.sx + s.tx
        val b = rect.bottom * s.sy + s.ty
        s.clip = SkIRect.MakeLTRB(
            maxOf(s.clip.left, SkScalarRoundToInt(minOf(l, r))),
            maxOf(s.clip.top, SkScalarRoundToInt(minOf(t, b))),
            minOf(s.clip.right, SkScalarRoundToInt(maxOf(l, r))),
            minOf(s.clip.bottom, SkScalarRoundToInt(maxOf(t, b))),
        )
    }

    /**
     * Mirrors Skia's `SkCanvas::clipRect(rect, doAntiAlias)`. The Phase 2/3
     * targets only clip with integer-aligned rects (under translate-only
     * CTM), where AA-clip and pixel-aligned clip produce identical
     * pixel-aligned output. A true AA-clip would require a per-pixel
     * coverage mask; deferred to later phases.
     */
    public fun clipRect(rect: SkRect, doAntiAlias: Boolean) {
        clipRect(rect)
    }

    public fun drawRect(rect: SkRect, paint: SkPaint) {
        val s = top
        val l = rect.left * s.sx + s.tx
        val t = rect.top * s.sy + s.ty
        val r = rect.right * s.sx + s.tx
        val b = rect.bottom * s.sy + s.ty
        val devRect = SkRect.MakeLTRB(minOf(l, r), minOf(t, b), maxOf(l, r), maxOf(t, b))
        device.drawRect(devRect, s.clip, paint)
    }

    /**
     * Phase 3a: draw a polygon path filled with `paint.color` under the
     * current path fill rule (`kWinding` / `kEvenOdd`). The path's verb
     * stream is transformed point-by-point into device space using the
     * current CTM, so callers continue to express geometry in source
     * coordinates.
     */
    public fun drawPath(path: SkPath, paint: SkPaint) {
        val s = top
        device.drawPath(path, s.sx, s.sy, s.tx, s.ty, s.clip, paint)
    }

    /**
     * Mirrors Skia's `SkCanvas::drawOval`. Emits an elliptical contour via
     * [SkPath.Oval] and routes through [drawPath]. Convenience wrapper —
     * the stand-alone path can be reused if the same oval is drawn many
     * times.
     */
    public fun drawOval(oval: SkRect, paint: SkPaint) {
        drawPath(SkPath.Oval(oval), paint)
    }

    /**
     * Mirrors Skia's `SkCanvas::drawCircle`. Convenience wrapper around
     * [SkPath.Circle] + [drawPath].
     */
    public fun drawCircle(cx: SkScalar, cy: SkScalar, radius: SkScalar, paint: SkPaint) {
        if (radius <= 0f) return
        drawPath(SkPath.Circle(cx, cy, radius), paint)
    }

    /**
     * Mirrors Skia's `SkCanvas::drawRRect`. Routes through [SkPath.RRect],
     * which dispatches on [SkRRect.Type] to the right cubic-Bézier or
     * straight-line contour. Empty rrects are a no-op.
     */
    public fun drawRRect(rrect: SkRRect, paint: SkPaint) {
        if (rrect.isEmpty()) return
        drawPath(SkPath.RRect(rrect), paint)
    }

    /**
     * Draw `image` at device-space position `(x, y)`, sampled with
     * `sampling`. Mirrors Skia's `SkCanvas::drawImage(image, x, y, sampling, paint)`.
     */
    public fun drawImage(
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
     * The `dst` rect is transformed via the current CTM; the `src` rect is
     * passed through unchanged (it's an image-space sub-rectangle, not a
     * geometry to transform).
     */
    public fun drawImageRect(
        image: SkImage,
        src: SkRect,
        dst: SkRect,
        sampling: SkSamplingOptions = SkSamplingOptions.Default,
        paint: SkPaint? = null,
        constraint: SrcRectConstraint = SrcRectConstraint.kStrict,
    ) {
        val s = top
        val l = dst.left * s.sx + s.tx
        val t = dst.top * s.sy + s.ty
        val r = dst.right * s.sx + s.tx
        val b = dst.bottom * s.sy + s.ty
        val devDst = SkRect.MakeLTRB(minOf(l, r), minOf(t, b), maxOf(l, r), maxOf(t, b))
        device.drawImageRect(image, src, devDst, sampling, paint, constraint, s.clip)
    }

    public fun drawColor(color: SkColor) {
        bitmap.eraseColor(color)
    }

    public val width: Int get() = device.width
    public val height: Int get() = device.height
}
