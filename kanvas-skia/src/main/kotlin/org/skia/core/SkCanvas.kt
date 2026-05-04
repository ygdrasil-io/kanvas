package org.skia.core

import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColor
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkRRect
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkIRect
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.math.SkScalarRoundToInt
import kotlin.math.ceil as kCeil
import kotlin.math.floor as kFloor

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
public open class SkCanvas(rootDevice: SkBitmapDevice) {

    public constructor(bitmap: SkBitmap) : this(SkBitmapDevice(bitmap))

    /** The root (backing) device. Layers push their own devices on the stack. */
    public val device: SkBitmapDevice = rootDevice

    public val bitmap: SkBitmap get() = device.bitmap

    /**
     * One stack entry per `save` / `saveLayer`. Carries the active CTM
     * (translate + scale) and clip in **current device coordinates**, plus
     * the device that draws land in. For a non-layer `save` the device is
     * the same as the parent state's; for `saveLayer` it's a fresh
     * offscreen device whose `(0, 0)` maps to `(layerOriginX, layerOriginY)`
     * in the parent state's device.
     */
    private data class State(
        var sx: SkScalar,
        var sy: SkScalar,
        var tx: SkScalar,
        var ty: SkScalar,
        var clip: SkIRect,
        var device: SkBitmapDevice,
        /** Non-null iff this state was opened by `saveLayer`. */
        var layer: Layer? = null,
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
        addLast(State(1f, 1f, 0f, 0f, rootDevice.deviceClipBounds(), rootDevice))
    }

    private val top: State get() = stack.last()

    public fun save(): Int {
        val s = top
        stack.addLast(State(s.sx, s.sy, s.tx, s.ty, s.clip.copy(), s.device))
        return stack.size - 2
    }

    public fun restore() {
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
        s.device.drawRect(devRect, s.clip, paint)
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
        s.device.drawPath(path, s.sx, s.sy, s.tx, s.ty, s.clip, paint)
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
     * Mirrors Skia's `SkCanvas::drawRoundRect(rect, rx, ry, paint)`. Builds a
     * uniform-corner [SkRRect] via [SkRRect.MakeRectXY] and routes through
     * [drawRRect]. Convenience wrapper — the stand-alone rrect can be reused
     * if the same shape is drawn many times.
     */
    public fun drawRoundRect(rect: SkRect, rx: SkScalar, ry: SkScalar, paint: SkPaint) {
        drawRRect(SkRRect.MakeRectXY(rect, rx, ry), paint)
    }

    /**
     * Mirrors Skia's `SkCanvas::drawLine(x0, y0, x1, y1, paint)`. Emits a
     * 2-point open path (`moveTo` + `lineTo`) and routes through [drawPath].
     * The paint's stroke style is taken at face value — `kFill_Style` produces
     * a degenerate (zero-area) fill that rasterizes to nothing, matching
     * Skia's behaviour. `kStroke_Style` exercises [SkStroker] with caps but
     * no joins (single segment, two endpoints).
     */
    public fun drawLine(x0: SkScalar, y0: SkScalar, x1: SkScalar, y1: SkScalar, paint: SkPaint) {
        val path = SkPathBuilder().moveTo(x0, y0).lineTo(x1, y1).detach()
        drawPath(path, paint)
    }

    /**
     * Mirrors Skia's `SkCanvas::drawArc(oval, startAngleDeg, sweepAngleDeg, useCenter, paint)`.
     *
     * - When `useCenter = false`, an open arc curve is drawn — `paint`'s
     *   stroke caps are visible at the two ends.
     * - When `useCenter = true`, a closed pie-slice contour is drawn:
     *   `arc + lineTo(centre) + close`. Filled or stroked the same as any
     *   other closed path.
     *
     * Sweep = 0 is a no-op (Skia degenerates similarly).
     */
    public fun drawArc(
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
        s.device.drawImageRect(image, src, devDst, sampling, paint, constraint, s.clip)
    }

    public fun drawColor(color: SkColor) {
        bitmap.eraseColor(color)
    }

    /**
     * Mirrors Skia's `SkCanvas::drawPaint`. Fills the **current clip** with
     * `paint.color` via SrcOver. The CTM is irrelevant — `drawPaint` is
     * "infinite rect" semantics, so the only bound is the clip.
     *
     * Phase-2-2026 scope: only honours `paint.color` (sRGB-encoded ARGB).
     * `paint.isAntiAlias` is ignored — the clip rect is integer-aligned in
     * device coordinates, so analytic edge coverage would emit zero
     * fractional contribution and we can take the cheap solid-fill path
     * straight to [SkBitmapDevice.drawPaint].
     */
    public fun drawPaint(paint: SkPaint) {
        val s = top
        s.device.drawPaint(s.clip, paint)
    }

    /**
     * Mirrors Skia's `SkCanvas::saveLayer(bounds, paint)`. Allocates an
     * offscreen bitmap-backed device matching the device-space projection of
     * [bounds] (intersected with the current clip), then redirects all
     * subsequent draws into it until the matching [restore] composites the
     * layer back onto the parent device using [paint] (alpha modulation +
     * SrcOver — no full blend mode dispatch in this slice).
     *
     * When [bounds] is null, the layer matches the entire current clip.
     * The CTM and clip on the new state are translated so source-space
     * coordinates land in the same place in the offscreen device that they
     * would have in the parent device, except shifted by `(-originX, -originY)`.
     */
    public fun saveLayer(bounds: SkRect?, paint: SkPaint?): Int {
        val s = top
        // Project `bounds` into current device space, then intersect with the
        // current clip. A null `bounds` ⇒ "whole clip".
        val layerBounds: SkIRect = if (bounds == null) {
            s.clip
        } else {
            val l = bounds.left * s.sx + s.tx
            val t = bounds.top * s.sy + s.ty
            val r = bounds.right * s.sx + s.tx
            val b = bounds.bottom * s.sy + s.ty
            SkIRect.MakeLTRB(
                maxOf(s.clip.left, kFloor(minOf(l, r).toDouble()).toInt()),
                maxOf(s.clip.top, kFloor(minOf(t, b).toDouble()).toInt()),
                minOf(s.clip.right, kCeil(maxOf(l, r).toDouble()).toInt()),
                minOf(s.clip.bottom, kCeil(maxOf(t, b).toDouble()).toInt()),
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
                s.sx, s.sy, s.tx, s.ty,
                SkIRect.MakeLTRB(s.clip.left, s.clip.top, s.clip.left, s.clip.top),
                s.device,
                layer = null,
            ))
            return stack.size - 2
        }

        val layerBitmap = SkBitmap(w, h, s.device.bitmap.colorSpace).also { it.eraseColor(0) }
        val layerDevice = SkBitmapDevice(layerBitmap)
        val originX = layerBounds.left
        val originY = layerBounds.top

        // Layer-local CTM: translate by `-origin` so a source point that used
        // to land at parent device `(px, py)` now lands at layer coords
        // `(px - originX, py - originY)`.
        val newState = State(
            sx = s.sx,
            sy = s.sy,
            tx = s.tx - originX,
            ty = s.ty - originY,
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
    public fun saveLayer(): Int = saveLayer(null, null)

    /**
     * Mirrors Skia's `SkCanvas::saveLayer(bounds, paint, flags)`. The
     * [flags] field is accepted for API compatibility but ignored —
     * `kInitWithPrevious_SaveLayerFlag` and friends are out of scope for the
     * current slice, since they require backdrop sampling against the parent
     * device which the raster path doesn't yet expose.
     */
    public fun saveLayer(bounds: SkRect?, paint: SkPaint?, flags: SaveLayerFlags): Int =
        saveLayer(bounds, paint)

    public val width: Int get() = device.width
    public val height: Int get() = device.height
}
