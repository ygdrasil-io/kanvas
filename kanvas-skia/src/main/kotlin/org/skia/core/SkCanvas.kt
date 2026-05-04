package org.skia.core

import org.skia.foundation.SkBitmap
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
    public fun getTotalMatrix(): SkMatrix = top.matrix

    public fun save(): Int {
        val s = top
        stack.addLast(State(s.matrix, s.clip.copy(), s.device))
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
        s.matrix = s.matrix.preTranslate(dx, dy)
    }

    public fun scale(kx: SkScalar, ky: SkScalar) {
        val s = top
        s.matrix = s.matrix.preScale(kx, ky)
    }

    /** Mirrors Skia's `SkCanvas::rotate(deg)` — pre-concat with a rotation around the origin. */
    public fun rotate(deg: SkScalar) {
        val s = top
        s.matrix = s.matrix.preRotate(deg)
    }

    /**
     * Mirrors Skia's `SkCanvas::rotate(deg, px, py)` — pre-concat with a
     * rotation around an arbitrary pivot point.
     */
    public fun rotate(deg: SkScalar, px: SkScalar, py: SkScalar) {
        val s = top
        s.matrix = s.matrix.preRotate(deg, px, py)
    }

    /** Mirrors Skia's `SkCanvas::skew(sx, sy)` — pre-concat with a skew. */
    public fun skew(sx: SkScalar, sy: SkScalar) {
        val s = top
        s.matrix = s.matrix.preSkew(sx, sy)
    }

    /** Mirrors Skia's `SkCanvas::concat(SkMatrix)` — pre-concat with `mat`. */
    public fun concat(mat: SkMatrix) {
        val s = top
        s.matrix = s.matrix.preConcat(mat)
    }

    /** Mirrors Skia's `SkCanvas::setMatrix(SkMatrix)` — replaces the CTM wholesale. */
    public fun setMatrix(mat: SkMatrix) {
        val s = top
        s.matrix = mat
    }

    /** Mirrors Skia's `SkCanvas::resetMatrix()`. */
    public fun resetMatrix() {
        val s = top
        s.matrix = SkMatrix.Identity
    }

    public fun clipRect(rect: SkRect) {
        val s = top
        // Under non-axis-aligned matrices the rotated clip becomes a quad —
        // we approximate with its axis-aligned bbox (conservative).
        val devRect = s.matrix.mapRect(rect)
        s.clip = SkIRect.MakeLTRB(
            maxOf(s.clip.left, kFloor(devRect.left.toDouble()).toInt()),
            maxOf(s.clip.top, kFloor(devRect.top.toDouble()).toInt()),
            minOf(s.clip.right, kCeil(devRect.right.toDouble()).toInt()),
            minOf(s.clip.bottom, kCeil(devRect.bottom.toDouble()).toInt()),
        )
    }

    /**
     * Mirrors Skia's `SkCanvas::clipRect(rect, doAntiAlias)`. AA-clip
     * support is deferred to a later phase — for now both call paths emit
     * pixel-aligned clips.
     */
    public fun clipRect(rect: SkRect, doAntiAlias: Boolean) {
        clipRect(rect)
    }

    public fun drawRect(rect: SkRect, paint: SkPaint) {
        val s = top
        if (s.matrix.isAxisAligned && paint.shader == null) {
            // Fast path: solid colour, axis-aligned CTM. Pre-compute the
            // device rect and route through SkBitmapDevice's hard-edge /
            // analytic-AA rect rasterizer.
            val (x0, y0) = s.matrix.mapXY(rect.left, rect.top)
            val (x1, y1) = s.matrix.mapXY(rect.right, rect.bottom)
            val devRect = SkRect.MakeLTRB(minOf(x0, x1), minOf(y0, y1), maxOf(x0, x1), maxOf(y0, y1))
            s.device.drawRect(devRect, s.clip, paint)
        } else {
            // Either rotated/skewed CTM (4-vertex polygon) or shader-driven
            // colour (per-pixel scanline path). Both go through drawPath.
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
    public fun drawPath(path: SkPath, paint: SkPaint) {
        val s = top
        s.device.drawPath(path, s.matrix, s.clip, paint)
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
     * Mirrors Skia's `SkCanvas::drawDRRect(outer, inner, paint)` — fills the
     * "donut" between the [outer] and [inner] rounded rectangles. Built as a
     * single path with the outer ring in [SkPathDirection.kCW] and the inner
     * ring in [SkPathDirection.kCCW], which the default `kWinding` fill rule
     * paints as the band between them.
     */
    public fun drawDRRect(outer: SkRRect, inner: SkRRect, paint: SkPaint) {
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
    public fun drawLine(x0: SkScalar, y0: SkScalar, x1: SkScalar, y1: SkScalar, paint: SkPaint) {
        val path = SkPathBuilder().moveTo(x0, y0).lineTo(x1, y1).detach()
        drawPath(path, paint)
    }

    /**
     * Mirrors Skia's `SkCanvas::drawArc(oval, startAngleDeg, sweepAngleDeg, useCenter, paint)`.
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
     * Under axis-aligned matrices the dst rect is mapped through the CTM to
     * an axis-aligned device rect (current code path). Under non-axis-aligned
     * matrices the call is dropped — true rotated `drawImageRect` would need
     * texture-space sampling with an inverse matrix (deferred).
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
        if (!s.matrix.isAxisAligned) {
            // TODO(phase 4b+) rotated drawImageRect — needs sampler with inverse CTM.
            return
        }
        val (x0, y0) = s.matrix.mapXY(dst.left, dst.top)
        val (x1, y1) = s.matrix.mapXY(dst.right, dst.bottom)
        val devDst = SkRect.MakeLTRB(minOf(x0, x1), minOf(y0, y1), maxOf(x0, x1), maxOf(y0, y1))
        s.device.drawImageRect(image, src, devDst, sampling, paint, constraint, s.clip)
    }

    public fun drawColor(color: SkColor) {
        bitmap.eraseColor(color)
    }

    /**
     * Mirrors Skia's `SkCanvas::drawPaint`. Fills the current clip with
     * `paint.color` (or `paint.shader`, if set) via the paint's blend mode.
     * `drawPaint` has "infinite rect" semantics, so the only spatial bound
     * is the clip; the CTM affects the shader's local-to-device mapping
     * only (and is a no-op for solid-colour paints).
     */
    public fun drawPaint(paint: SkPaint) {
        val s = top
        s.device.drawPaint(s.matrix, s.clip, paint)
    }

    /**
     * Mirrors Skia's `SkCanvas::drawString(const char[], SkScalar, SkScalar,
     * const SkFont&, const SkPaint&)` (SkCanvas.h:1861).
     *
     * **T1/T2 status — no-op stub.** The text is **not rendered** at this
     * stage of the migration. The method exists so that GMs which call
     * `canvas->drawString(...)` for cell labels compile and run without
     * crashing; the corresponding pixels stay at the background colour.
     *
     * T3 will replace this body with a real path:
     *  1. resolve glyph outlines via `AwtGlyphRasterizer` (`org.skia.foundation.awt`),
     *  2. transform by the font's baseline `(x, y)` + the active CTM,
     *  3. route through [drawPath] using the existing scanline-fill +
     *     blend-mode pipeline.
     *
     * Until then, see `MIGRATION_PLAN_TEXT.md` §T1 / §T3.
     */
    public open fun drawString(
        str: String,
        @Suppress("UNUSED_PARAMETER") x: SkScalar,
        @Suppress("UNUSED_PARAMETER") y: SkScalar,
        @Suppress("UNUSED_PARAMETER") font: SkFont,
        @Suppress("UNUSED_PARAMETER") paint: SkPaint,
    ) {
        // Intentional no-op (T1/T2). See KDoc.
        @Suppress("UNUSED_EXPRESSION") str
    }

    /**
     * Mirrors Skia's `SkCanvas::drawSimpleText(const void*, size_t,
     * SkTextEncoding, SkScalar, SkScalar, const SkFont&, const SkPaint&)`
     * (SkCanvas.h:1834). Same no-op semantics as [drawString] until T3.
     *
     * @param byteLength number of bytes (UTF-8) or code units (UTF-16/32)
     *                   to consider in [text].
     */
    public open fun drawSimpleText(
        text: String,
        @Suppress("UNUSED_PARAMETER") byteLength: Int,
        @Suppress("UNUSED_PARAMETER") encoding: SkTextEncoding,
        @Suppress("UNUSED_PARAMETER") x: SkScalar,
        @Suppress("UNUSED_PARAMETER") y: SkScalar,
        @Suppress("UNUSED_PARAMETER") font: SkFont,
        @Suppress("UNUSED_PARAMETER") paint: SkPaint,
    ) {
        // Intentional no-op (T1/T2). See [drawString] KDoc.
        @Suppress("UNUSED_EXPRESSION") text
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
    public fun saveLayer(bounds: SkRect?, paint: SkPaint?): Int {
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
    public fun saveLayer(): Int = saveLayer(null, null)

    /**
     * Mirrors Skia's `SkCanvas::saveLayer(bounds, paint, flags)`. The
     * [flags] field is accepted for API compatibility but ignored.
     */
    public fun saveLayer(bounds: SkRect?, paint: SkPaint?, flags: SaveLayerFlags): Int =
        saveLayer(bounds, paint)

    public val width: Int get() = device.width
    public val height: Int get() = device.height
}
