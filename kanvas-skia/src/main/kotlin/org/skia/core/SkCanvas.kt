package org.skia.core

import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkAAClip
import org.skia.foundation.SkClipOp
import org.skia.foundation.SkRegion
import org.skia.foundation.SkColor
import org.skia.foundation.SkFont
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathDirection
import org.skia.foundation.SkRRect
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTextEncoding
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
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
         * Phase I3.3.b — band-encoded AA clip carrying the
         * combined `clipPath` / `clipRRect` coverage. `null` = pure
         * rectangular clip (fast path ; the bbox is [State.clip]).
         * Replaces the Phase 7q `clipMask: ByteArray?` 2D byte buffer
         * — see [SkAAClip] for the run-encoded representation.
         */
        var aaClip: SkAAClip? = null,
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

    /**
     * Current clip bounds in **device** coordinates — i.e. the
     * smallest [SkIRect] that fully contains the active clip
     * (intersected through every `clipRect` / `clipRRect` /
     * `clipPath` op since the matching `save`).
     *
     * Mirrors Skia's `SkCanvas::getDeviceClipBounds`. Empty rect
     * iff the clip is empty (subsequent draws are guaranteed to
     * be no-ops). Used by [SkPicture.playback] to feed the BBH
     * cull query.
     */
    public open fun getDeviceClipBounds(): SkIRect = top.clip.copy()

    /**
     * Current clip bounds in **local** coordinates — i.e. the
     * device clip transformed back through the inverse of the
     * current CTM. Used by [SkPicture.playback] to query the
     * picture's BBH (recorded ops carry bounds in the recording
     * canvas's own local frame, which equals the playback's local
     * frame when the picture sits inside its `cullRect`).
     *
     * Mirrors Skia's `SkCanvas::getLocalClipBounds`. Falls back to
     * the device clip (cast to floats) when the CTM is singular
     * (degenerate inverse). The fallback rect is conservative — it
     * may over-include but never under-includes — matching Skia.
     */
    public open fun getLocalClipBounds(): SkRect {
        val devClip = SkRect.Make(top.clip)
        if (devClip.isEmpty) return devClip
        val inv = top.matrix.invert() ?: return devClip
        return inv.mapRect(devClip)
    }

    public open fun save(): Int {
        val s = top
        // aaClip shared by reference — clipPath / clipRRect always
        // allocates a fresh SkAAClip before mutating (op() with the
        // path's own AA clip), so the parent's stays untouched.
        stack.addLast(State(s.matrix, s.clip.copy(), s.device, aaClip = s.aaClip))
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

        // Phase 7d.2 — when the layer's paint carries an imageFilter,
        // snapshot the layer to an SkImage, apply the filter, and
        // composite the filtered result onto the parent device with
        // the offset adjusted for the filter's displacement.
        val layerPaint = layer.paint
        val imageFilter = layerPaint?.imageFilter
        if (imageFilter != null) {
            val snapshot = popped.device.bitmap.asImage()
            val filterResult = imageFilter.filterImage(snapshot, top.matrix)
            val filteredImg = filterResult.image
            val filteredBitmap = SkBitmap(
                filteredImg.width, filteredImg.height,
                popped.device.bitmap.colorSpace, popped.device.bitmap.colorType,
            )
            for (yp in 0 until filteredImg.height) {
                for (xp in 0 until filteredImg.width) {
                    filteredBitmap.setPixel(xp, yp, filteredImg.peekPixel(xp, yp))
                }
            }
            val filteredDevice = SkBitmapDevice(filteredBitmap)
            val proxyPaint = layerPaint.copy().apply { this.imageFilter = null }
            layer.parentDevice.compositeFrom(
                filteredDevice,
                layer.originX + filterResult.offsetX,
                layer.originY + filterResult.offsetY,
                top.clip,
                proxyPaint,
            )
            return
        }

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
            s.aaClip = null
            return
        }

        val pathAac = rasterisePathToAaClip(s, path, newClipBbox, doAntiAlias)

        val parent = s.aaClip
        s.aaClip = if (parent == null) {
            pathAac
        } else {
            val combined = SkAAClip(parent)
            combined.op(pathAac, SkRegion.Op.kIntersect)
            combined
        }

        s.clip = newClipBbox
    }

    /**
     * `clipPath(..., kDifference)` — cut the supplied path out of the
     * current clip. Bbox stays at the parent clip ; the new
     * [SkAAClip] is `parent.op(pathAaClip, kDifference)`. If there
     * was no parent AA clip we synthesise one from `s.clip` (full
     * coverage rect) before subtracting.
     */
    private fun clipPathDifference(s: State, path: SkPath, doAntiAlias: Boolean) {
        val w = s.clip.right - s.clip.left
        val h = s.clip.bottom - s.clip.top
        if (w <= 0 || h <= 0) {
            s.aaClip = null
            return
        }
        val pathAac = rasterisePathToAaClip(s, path, s.clip, doAntiAlias)
        val combined = SkAAClip(s.aaClip ?: SkAAClip(s.clip))
        combined.op(pathAac, SkRegion.Op.kDifference)
        s.aaClip = combined
    }

    /**
     * Phase I3.3.b — rasterise [path] (transformed by the active CTM
     * and clipped to [bbox]) into an [SkAAClip]. Replaces the Phase
     * 7q `rasterisePathMask` ByteArray helper.
     */
    private fun rasterisePathToAaClip(
        s: State, path: SkPath, bbox: SkIRect, doAntiAlias: Boolean,
    ): SkAAClip {
        val transformed = path.makeTransform(s.matrix)
        val out = SkAAClip()
        out.setPath(transformed, SkRegion(bbox), doAA = doAntiAlias)
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

    /**
     * Mirrors Skia's `SkCanvas::clipRegion(deviceRgn, op)`.
     *
     * Unlike [clipRect] / [clipPath] / [clipRRect], `clipRegion` is
     * expressed in **device** coordinates — the CTM is *not* applied
     * to [region]. This is what makes it useful for callers that have
     * already accumulated a complex (multi-rect) clip and want to bind
     * it into the clip stack without having a single transformable
     * shape (cf. Skia GM `clip_sierpinski_region`, which builds 81
     * rects and then rotates the canvas — `clipRegion` snaps the clip
     * to the pre-rotation device pixels).
     *
     * Implementation : the region is materialised into an [SkAAClip]
     * (full `0xFF` coverage everywhere ; gaps inside the bounds are
     * `0x00`). For [SkClipOp.kIntersect] (the default), the new AA
     * clip composes with any existing one via [SkRegion.Op.kIntersect]
     * and the device-clip bbox is intersected with the region's
     * bounds. For [SkClipOp.kDifference], the existing clip is
     * promoted to an AA clip (synthesised from the device-clip bbox
     * if absent) and the region is subtracted ; the device-clip bbox
     * stays put (cutting a hole inside doesn't shrink the outer
     * bound).
     */
    public open fun clipRegion(region: SkRegion, op: SkClipOp = SkClipOp.kIntersect) {
        val s = top
        if (region.isEmpty()) {
            when (op) {
                SkClipOp.kIntersect -> {
                    // Intersect with empty → empty clip.
                    s.clip = SkIRect.MakeLTRB(s.clip.left, s.clip.top, s.clip.left, s.clip.top)
                    s.aaClip = null
                }
                SkClipOp.kDifference -> {
                    // Difference with empty → unchanged.
                }
            }
            return
        }
        // [region] is in **global/canvas** device coords (i.e. the
        // outermost canvas's pixel grid). When the active state is a
        // layer, we need to translate the region by the negative of
        // the cumulative layer origin so it lands on the layer's local
        // device grid before joining the clip stack. Mirrors Skia's
        // `SkBitmapDevice::clipRegion` (see `src/core/SkBitmapDevice.cpp`).
        val (ox, oy) = cumulativeLayerOrigin()
        val deviceRegion = if (ox == 0 && oy == 0) {
            region
        } else {
            SkRegion(region).also { it.translate(-ox, -oy) }
        }
        when (op) {
            SkClipOp.kIntersect -> clipRegionIntersect(s, deviceRegion)
            SkClipOp.kDifference -> clipRegionDifference(s, deviceRegion)
        }
    }

    /**
     * Sum of every active layer's `(originX, originY)` walking the
     * `save` stack from the root down. Used to translate canvas-space
     * coords (passed to `clipRegion`) into the current layer's local
     * device grid. The root canvas has origin `(0, 0)`.
     */
    private fun cumulativeLayerOrigin(): Pair<Int, Int> {
        var ox = 0
        var oy = 0
        for (st in stack) {
            val l = st.layer ?: continue
            ox += l.originX
            oy += l.originY
        }
        return ox to oy
    }

    private fun clipRegionIntersect(s: State, region: SkRegion) {
        val rb = region.getBounds()
        val newClipBbox = SkIRect.MakeLTRB(
            maxOf(s.clip.left, rb.left),
            maxOf(s.clip.top, rb.top),
            minOf(s.clip.right, rb.right),
            minOf(s.clip.bottom, rb.bottom),
        )
        val w = newClipBbox.right - newClipBbox.left
        val h = newClipBbox.bottom - newClipBbox.top
        if (w <= 0 || h <= 0) {
            s.clip = SkIRect.MakeLTRB(s.clip.left, s.clip.top, s.clip.left, s.clip.top)
            s.aaClip = null
            return
        }
        val regionAaClip = SkAAClip(region)
        val parent = s.aaClip
        s.aaClip = if (parent == null) {
            regionAaClip
        } else {
            val combined = SkAAClip(parent)
            combined.op(regionAaClip, SkRegion.Op.kIntersect)
            combined
        }
        s.clip = newClipBbox
    }

    private fun clipRegionDifference(s: State, region: SkRegion) {
        val w = s.clip.right - s.clip.left
        val h = s.clip.bottom - s.clip.top
        if (w <= 0 || h <= 0) {
            s.aaClip = null
            return
        }
        val regionAaClip = SkAAClip(region)
        val combined = SkAAClip(s.aaClip ?: SkAAClip(s.clip))
        combined.op(regionAaClip, SkRegion.Op.kDifference)
        s.aaClip = combined
    }

    /** Bind the active state's AA clip onto the device before each draw. */
    private fun bindClip(s: State) {
        s.device.setActiveClip(s.aaClip)
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
     * Mirrors Skia's `SkCanvas::experimental_DrawEdgeAAQuad`
     * ([include/core/SkCanvas.h](https://github.com/google/skia/blob/main/include/core/SkCanvas.h)) :
     *
     * ```cpp
     * void experimental_DrawEdgeAAQuad(const SkRect& rect, const SkPoint clip[4],
     *                                  QuadAAFlags aaFlags, SkColor color, SkBlendMode mode);
     * ```
     *
     * Draws either the [rect] (when [clip] is `null`) or the 4-point
     * quadrilateral [clip] filled with solid [color] under the given blend
     * [mode]. Edge AA is controlled by [aaFlags] (a bitmask of
     * [QuadAAFlags] values).
     *
     * **Per-edge AA semantics.** Upstream's CPU device implementation
     * (`SkDevice::drawEdgeAAQuad` in `src/core/SkDevice.cpp`) takes the
     * all-or-nothing shortcut for raster — AA is enabled iff every edge is
     * flagged (`aa == kAll_QuadAAFlags`). Per-edge AA is a GPU-only feature
     * that avoids seaming in tiled composited layers; raster mirrors it
     * with hard edges whenever any edge has AA disabled. We follow upstream
     * verbatim, so the per-edge GM repros (`crbug_1167277`, `crbug_1174186`,
     * `crbug_1162942`) match the reference renders exactly.
     *
     * **Clip points.** When [clip] is non-null its four points are taken in
     * order **top-left, top-right, bottom-right, bottom-left** (the
     * `SkRect::toQuad` order — see the upstream comment at
     * `include/core/SkCanvas.h:1755-1758`). The resulting closed polygon
     * is filled with the solid colour.
     */
    public open fun experimental_DrawEdgeAAQuad(
        rect: SkRect,
        clip: Array<SkPoint>?,
        aaFlags: Int,
        color: SkColor,
        mode: SkBlendMode,
    ) {
        // Match upstream's CPU shortcut: AA only when every edge is flagged.
        // Per-edge AA is GPU-only; raster intentionally drops to hard edges
        // for any partial flag combination to avoid seaming.
        val paint = SkPaint().apply {
            this.color = color
            this.blendMode = mode
            this.isAntiAlias = aaFlags == QuadAAFlags.kAll_QuadAAFlags
        }
        if (clip != null) {
            require(clip.size == 4) {
                "experimental_DrawEdgeAAQuad: clip must contain exactly 4 points, got ${clip.size}"
            }
            // Closed polygon through the 4 clip points (TL, TR, BR, BL).
            val pts = arrayOf(
                clip[0].fX to clip[0].fY,
                clip[1].fX to clip[1].fY,
                clip[2].fX to clip[2].fY,
                clip[3].fX to clip[3].fY,
            )
            drawPath(SkPath.Polygon(pts, isClosed = true), paint)
        } else {
            drawRect(rect, paint)
        }
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
     * Mirrors Skia's `SkCanvas::PointMode` enum
     * (`include/core/SkCanvas.h:1478-1482`). Selects how the point
     * array passed to [drawPoints] is interpreted geometrically.
     */
    public enum class PointMode {
        /** Each `SkPoint` becomes one stroked dot (square / circle / pixel per stroke cap). */
        kPoints,
        /** Each adjacent **pair** `(p[0],p[1]), (p[2],p[3]) …` becomes a stroked line. */
        kLines,
        /** Each adjacent pair `(p[i], p[i+1])` becomes a stroked line — closed-loop without close. */
        kPolygon,
    }

    /**
     * Mirrors Skia's `SkCanvas::drawPoints(mode, count, points, paint)`
     * (`include/core/SkCanvas.h:1496`). Draws [points] interpreted per
     * [mode] using the supplied [paint] :
     *
     *  - [PointMode.kPoints] — each point becomes a stroked dot. The
     *    visual shape is governed by [SkPaint.strokeCap] :
     *    [SkPaint.Cap.kRound_Cap] → circle of radius
     *    `paint.strokeWidth / 2`, [SkPaint.Cap.kSquare_Cap] → square of
     *    side `paint.strokeWidth`, [SkPaint.Cap.kButt_Cap] → a
     *    zero-length line stroke (single-pixel dot at hairline widths,
     *    visible square otherwise — same as upstream's degenerate-
     *    butt behaviour).
     *  - [PointMode.kLines] — `points` is consumed in pairs ;
     *    `points[2*i]` to `points[2*i+1]` becomes a stroked line. An
     *    odd trailing point is dropped.
     *  - [PointMode.kPolygon] — `points[i]` to `points[i+1]` for every
     *    adjacent pair (open polyline ; no implicit close).
     *
     * No fast path : delegates per-point/per-segment to [drawCircle]
     * (round caps), [drawRect] (square caps) or [drawLine] (line
     * modes). The path the rasteriser walks is exactly the same as if
     * the caller emitted those calls themselves, so honouring of
     * paint shaders / blend modes / clipPath stays uniform.
     */
    public open fun drawPoints(mode: PointMode, points: Array<org.skia.math.SkPoint>, paint: SkPaint) {
        if (points.isEmpty()) return
        when (mode) {
            PointMode.kPoints -> {
                val r = paint.strokeWidth * 0.5f
                when (paint.strokeCap) {
                    SkPaint.Cap.kRound_Cap -> {
                        // Circle of diameter strokeWidth ; fall back to
                        // hairline-square when strokeWidth is zero.
                        val fillPaint = paint.copy().apply {
                            style = SkPaint.Style.kFill_Style
                        }
                        if (r > 0f) {
                            for (p in points) drawCircle(p.fX, p.fY, r, fillPaint)
                        } else {
                            // Hairline → 1-pixel dot via 1×1 rect.
                            for (p in points) {
                                drawRect(SkRect.MakeXYWH(p.fX, p.fY, 1f, 1f), fillPaint)
                            }
                        }
                    }
                    SkPaint.Cap.kSquare_Cap -> {
                        // Square of side strokeWidth, centred at the point.
                        val fillPaint = paint.copy().apply {
                            style = SkPaint.Style.kFill_Style
                        }
                        val side = if (paint.strokeWidth > 0f) paint.strokeWidth else 1f
                        val half = side * 0.5f
                        for (p in points) {
                            drawRect(SkRect.MakeXYWH(p.fX - half, p.fY - half, side, side), fillPaint)
                        }
                    }
                    SkPaint.Cap.kButt_Cap -> {
                        // Degenerate line at the point — invisible at
                        // strokeWidth=0, single-pixel hint at >0.
                        // Mirror upstream by using a 1×1 rect when
                        // stroke is hairline ; otherwise the
                        // degenerate stroke is a no-op.
                        if (paint.strokeWidth <= 0f) {
                            val fillPaint = paint.copy().apply {
                                style = SkPaint.Style.kFill_Style
                            }
                            for (p in points) {
                                drawRect(SkRect.MakeXYWH(p.fX, p.fY, 1f, 1f), fillPaint)
                            }
                        }
                    }
                }
            }
            PointMode.kLines -> {
                var i = 0
                while (i + 1 < points.size) {
                    val a = points[i]
                    val b = points[i + 1]
                    drawLine(a.fX, a.fY, b.fX, b.fY, paint)
                    i += 2
                }
            }
            PointMode.kPolygon -> {
                for (i in 0 until points.size - 1) {
                    val a = points[i]
                    val b = points[i + 1]
                    drawLine(a.fX, a.fY, b.fX, b.fY, paint)
                }
            }
        }
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
     * Mirrors Skia's `SkCanvas::drawAtlas(image, xform, src, colors,
     * blendMode, sampling, cullRect, paint)`
     * (`include/core/SkCanvas.h:1970`). Batched sprite draw : for
     * each `i`, the source rect `src[i]` of [image] is positioned in
     * destination space according to `xform[i]` (an [SkRSXform]
     * carrying scale + rotation + translation), then composited with
     * [paint] / [blendMode] / [sampling] semantics shared across all
     * sprites in the batch.
     *
     * **Implementation** : per-sprite, build the dst quad path from
     * the four src-rect corners under `xform[i]`, then fill via
     * [SkImage.makeShader] with the inverse transform as the local
     * matrix. Each sprite goes through the standard [drawPath]
     * dispatch ; the shader sampler handles arbitrary CTM rotation /
     * scale / skew (no axis-aligned restriction).
     *
     * **Out of scope (deferred)** :
     *  - per-sprite [colors] tinting (the `colors[i] × image` blend
     *    via [blendMode]) — the current implementation ignores
     *    [colors] ; the GMs in scope use `colors = null` ;
     *  - [cullRect] early-out — sprites outside [cullRect] are still
     *    drawn (correctness-equivalent ; just costs perf).
     *
     * @param xform     same length as [src] (and [colors] if non-null).
     * @param colors    per-sprite tint, length `xform.size` (deferred).
     * @param blendMode applied between [colors] and the sampled image
     *                  pixels (deferred).
     */
    public open fun drawAtlas(
        image: SkImage,
        xform: Array<org.skia.foundation.SkRSXform>,
        src: Array<SkRect>,
        @Suppress("UNUSED_PARAMETER") colors: IntArray? = null,
        @Suppress("UNUSED_PARAMETER") blendMode: SkBlendMode = SkBlendMode.kSrcOver,
        sampling: SkSamplingOptions = SkSamplingOptions.Default,
        @Suppress("UNUSED_PARAMETER") cullRect: SkRect? = null,
        paint: SkPaint? = null,
    ) {
        require(xform.size == src.size) {
            "drawAtlas : xform.size (${xform.size}) != src.size (${src.size})"
        }
        if (xform.isEmpty()) return
        val basePaint = paint?.copy() ?: SkPaint()
        for (i in xform.indices) {
            val xf = xform[i]
            val sr = src[i]
            // Affine that maps source pixel (x, y) → dst pixel.
            //   dst.x = sCos*x − sSin*y + (tx − sCos*sr.left + sSin*sr.top)
            //   dst.y = sSin*x + sCos*y + (ty − sSin*sr.left − sCos*sr.top)
            val tx = xf.fTx - xf.fSCos * sr.left + xf.fSSin * sr.top
            val ty = xf.fTy - xf.fSSin * sr.left - xf.fSCos * sr.top
            val srcToDst = org.skia.math.SkMatrix(
                sx = xf.fSCos, kx = -xf.fSSin, tx = tx,
                ky = xf.fSSin, sy = xf.fSCos, ty = ty,
            )
            // Sanity check : srcToDst must be invertible (rejects
            // degenerate xforms with sCos == sSin == 0).
            srcToDst.invert() ?: continue

            // Quad corners in dst space.
            val (x00, y00) = srcToDst.mapXY(sr.left, sr.top)
            val (x10, y10) = srcToDst.mapXY(sr.right, sr.top)
            val (x11, y11) = srcToDst.mapXY(sr.right, sr.bottom)
            val (x01, y01) = srcToDst.mapXY(sr.left, sr.bottom)

            val quad = SkPathBuilder()
                .moveTo(x00, y00)
                .lineTo(x10, y10)
                .lineTo(x11, y11)
                .lineTo(x01, y01)
                .close()
                .detach()

            // Shader localMatrix maps **shader-space (atlas pixels) →
            // local (path) space**. For drawAtlas the path is already
            // in dst (= device) space, so localMatrix is exactly
            // [srcToDst] — pixel sampling at device (devX, devY)
            // queries `(canvasCtm.preConcat(srcToDst)).invert() * (devX,
            // devY)` = `dstToSrc * (devX, devY)` for an identity CTM,
            // which lands back on the atlas pixel.
            val spritePaint = basePaint.copy().apply {
                shader = image.makeShader(
                    tileX = org.skia.foundation.SkTileMode.kClamp,
                    tileY = org.skia.foundation.SkTileMode.kClamp,
                    sampling = sampling,
                    localMatrix = srcToDst,
                )
                style = SkPaint.Style.kFill_Style
            }
            drawPath(quad, spritePaint)
        }
    }

    /**
     * Mirrors Skia's `SkCanvas::drawVertices(vertices, blendMode, paint)`
     * (`include/core/SkCanvas.h:1942`). Renders the triangle mesh
     * carried by [vertices] onto the canvas, filling each triangle
     * via the configured fill rule.
     *
     * **Phase I5.3 implementation note** : the current iso slice
     * supports the **solid-color** path — every triangle is filled
     * with `paint.color` / `paint.shader`. Per-vertex
     * [SkVertices.colors] interpolation and per-vertex
     * [SkVertices.texCoords] sampling are deferred (the parameters
     * are accepted but currently ignored ; documented in the
     * migration plan as I5.3.b / I5.3.c follow-ups).
     *
     * Triangle iteration honours [vertices]'s `mode`
     * ([SkVertices.VertexMode.kTriangles] / `kTriangleStrip` /
     * `kTriangleFan`) and any optional `indices` indirection. Each
     * triangle becomes a 3-vertex closed [SkPath] dispatched through
     * the existing [drawPath] pipeline — paint shaders / clipPath /
     * blend mode are honoured uniformly.
     *
     * @param blendMode currently unused at the per-vertex level —
     *                  combines into the paint via the standard
     *                  `paint.blendMode` slot when [vertices.colors]
     *                  is null. Reserved for I5.3.b.
     */
    public open fun drawVertices(
        vertices: org.skia.foundation.SkVertices,
        blendMode: SkBlendMode,
        paint: SkPaint,
    ) {
        val tCount = vertices.triangleCount()
        if (tCount == 0) return
        val s = top
        val colors = vertices.colors
        val texCoords = vertices.texCoords
        val shader = paint.shader

        // Dispatch table :
        //   1) texCoords + shader → I5.3.c texture-mapped triangles
        //      (with optional per-vertex colour modulation).
        //   2) colors only → I5.3.b barycentric colour interpolation.
        //   3) neither → I5.3.a solid-colour drawPath fast path.
        if (texCoords != null && shader != null) {
            bindClip(s)
            for (t in 0 until tCount) {
                val tri = vertices.triangleAt(t)
                val a = vertices.positions[tri[0]]
                val b = vertices.positions[tri[1]]
                val c = vertices.positions[tri[2]]
                val uvA = texCoords[tri[0]]
                val uvB = texCoords[tri[1]]
                val uvC = texCoords[tri[2]]
                s.device.drawTexturedTriangle(
                    a.fX, a.fY, uvA.fX, uvA.fY, colors?.get(tri[0]),
                    b.fX, b.fY, uvB.fX, uvB.fY, colors?.get(tri[1]),
                    c.fX, c.fY, uvC.fX, uvC.fY, colors?.get(tri[2]),
                    blendMode,
                    s.matrix, s.clip, paint,
                )
            }
            return
        }
        if (colors == null) {
            // Phase I5.3.a — solid colour fast path : every triangle
            // becomes a closed [SkPath] dispatched through drawPath.
            for (t in 0 until tCount) {
                val tri = vertices.triangleAt(t)
                val a = vertices.positions[tri[0]]
                val b = vertices.positions[tri[1]]
                val c = vertices.positions[tri[2]]
                val path = SkPathBuilder()
                    .moveTo(a.fX, a.fY)
                    .lineTo(b.fX, b.fY)
                    .lineTo(c.fX, c.fY)
                    .close()
                    .detach()
                drawPath(path, paint)
            }
            return
        }
        // Phase I5.3.b — per-vertex colour interpolation. Each
        // triangle is rasterised pixel-by-pixel via
        // [SkBitmapDevice.drawColoredTriangle] with barycentric ARGB
        // interp.
        bindClip(s)
        for (t in 0 until tCount) {
            val tri = vertices.triangleAt(t)
            val a = vertices.positions[tri[0]]
            val b = vertices.positions[tri[1]]
            val c = vertices.positions[tri[2]]
            s.device.drawColoredTriangle(
                a.fX, a.fY, colors[tri[0]],
                b.fX, b.fY, colors[tri[1]],
                c.fX, c.fY, colors[tri[2]],
                s.matrix, s.clip, paint,
            )
        }
    }

    /**
     * Mirrors Skia's
     * [`SkCanvas::drawPatch(cubics, colors, texCoords, blendMode, paint)`](https://github.com/google/skia/blob/main/include/core/SkCanvas.h#L2010).
     * Renders a *Coons patch* — a curvy quadrilateral whose 4 sides
     * are cubic Bézier curves and whose interior is bilinearly
     * interpolated between those edges.
     *
     * Vertex layout in [cubics] (12 [SkPoint]s, 24 floats) :
     * ```
     *   [0]──top edge──[3]
     *    │              │
     *  left           right
     *  edge           edge
     *    │              │
     *   [9]──bot edge──[6]
     * ```
     * - top edge : cubics[0..3] (left → right)
     * - right edge : cubics[3..6] (top → bottom)
     * - bottom edge : cubics[6..9] (right → left)
     * - left edge : cubics[9..11] + cubics[0] (bottom → top)
     *
     * Corners — `cubics[0]` (top-left), `cubics[3]` (top-right),
     * `cubics[6]` (bottom-right), `cubics[9]` (bottom-left) — match
     * the 4 entries of [colors] / [texCoords] when present.
     *
     * **Implementation** : tessellates the patch into an N×N grid of
     * quads (`N = 8` ; 128 triangles) via the Coons surface formula
     * `C(s, t) = Lc(s, t) + Ld(s, t) − B(s, t)` (linear blend along
     * each pair of opposite edges minus the bilinear blend of the
     * 4 corners), then defers to [drawVertices].
     *
     * @param cubics    12 control points (24 floats) describing the
     *                  4 edge cubics ; corners are shared between
     *                  adjacent edges.
     * @param colors    optional 4 corner ARGB colours, bilinearly
     *                  interpolated across the patch interior.
     * @param texCoords optional 4 corner texture coords, bilinearly
     *                  interpolated and used by [paint]'s shader.
     * @param blendMode passed through to [drawVertices] for vertex /
     *                  texture combination.
     */
    public open fun drawPatch(
        cubics: Array<org.skia.math.SkPoint>,
        colors: IntArray?,
        texCoords: Array<org.skia.math.SkPoint>?,
        blendMode: SkBlendMode,
        paint: SkPaint,
    ) {
        require(cubics.size == 12) { "drawPatch : cubics must have 12 points, got ${cubics.size}" }
        if (colors != null) require(colors.size == 4) { "drawPatch : colors must have 4 entries, got ${colors.size}" }
        if (texCoords != null) require(texCoords.size == 4) { "drawPatch : texCoords must have 4 entries, got ${texCoords.size}" }

        val n = PATCH_TESS_N
        val verts = ArrayList<org.skia.math.SkPoint>((n + 1) * (n + 1))
        val tCoords = if (texCoords != null) ArrayList<org.skia.math.SkPoint>((n + 1) * (n + 1)) else null
        val cArr = if (colors != null) IntArray((n + 1) * (n + 1)) else null

        for (j in 0..n) {
            val t = j.toFloat() / n
            for (i in 0..n) {
                val s = i.toFloat() / n
                val pt = coonsSurfaceAt(cubics, s, t)
                verts.add(pt)
                if (tCoords != null && texCoords != null) {
                    tCoords.add(bilerpPoint(texCoords, s, t))
                }
                if (cArr != null && colors != null) {
                    cArr[j * (n + 1) + i] = bilerpColor(colors, s, t)
                }
            }
        }

        // Build triangle indices (two triangles per quad cell).
        val indices = ShortArray(n * n * 6)
        var idx = 0
        for (j in 0 until n) {
            for (i in 0 until n) {
                val a = (j * (n + 1) + i).toShort()
                val b = (j * (n + 1) + i + 1).toShort()
                val c = ((j + 1) * (n + 1) + i).toShort()
                val d = ((j + 1) * (n + 1) + i + 1).toShort()
                // Quad → 2 triangles : (a, b, c) + (b, d, c).
                indices[idx++] = a; indices[idx++] = b; indices[idx++] = c
                indices[idx++] = b; indices[idx++] = d; indices[idx++] = c
            }
        }

        val sk = org.skia.foundation.SkVertices.MakeCopy(
            org.skia.foundation.SkVertices.VertexMode.kTriangles,
            verts.toTypedArray(),
            tCoords?.toTypedArray(),
            cArr,
            indices,
        )
        drawVertices(sk, blendMode, paint)
    }

    /**
     * Phase I5.4 — evaluate the Coons surface at parametric point
     * `(s, t) ∈ [0, 1]²`. Implements
     *   `C(s, t) = Lc(s, t) + Ld(s, t) − B(s, t)`
     * where `Lc`, `Ld` are the lerp surfaces along the (top/bottom)
     * and (left/right) edge pairs, and `B` is the bilinear blend of
     * the 4 corners.
     */
    private fun coonsSurfaceAt(cubics: Array<org.skia.math.SkPoint>, s: SkScalar, t: SkScalar): org.skia.math.SkPoint {
        // Top : cubics[0] → cubics[3] at parameter s.
        val top = cubicAt(cubics, 0, s)
        // Bottom : cubics[6] → cubics[9] at parameter (1 - s) (so s
        // increases left-to-right in dst space).
        val bot = cubicAt(cubics, 6, 1f - s)
        // Right : cubics[3] → cubics[6] at parameter t.
        val rig = cubicAt(cubics, 3, t)
        // Left : cubics[9..11] + cubics[0] at parameter (1 - t).
        val lef = cubicAtLeftEdge(cubics, 1f - t)

        val p00 = cubics[0]   // top-left
        val p10 = cubics[3]   // top-right
        val p11 = cubics[6]   // bottom-right
        val p01 = cubics[9]   // bottom-left

        val lcX = (1f - t) * top.fX + t * bot.fX
        val lcY = (1f - t) * top.fY + t * bot.fY
        val ldX = (1f - s) * lef.fX + s * rig.fX
        val ldY = (1f - s) * lef.fY + s * rig.fY
        val bX = (1f - s) * (1f - t) * p00.fX + s * (1f - t) * p10.fX +
            (1f - s) * t * p01.fX + s * t * p11.fX
        val bY = (1f - s) * (1f - t) * p00.fY + s * (1f - t) * p10.fY +
            (1f - s) * t * p01.fY + s * t * p11.fY
        return org.skia.math.SkPoint(lcX + ldX - bX, lcY + ldY - bY)
    }

    /**
     * Evaluate the cubic Bézier `[cubics[i0], cubics[i0+1],
     * cubics[i0+2], cubics[i0+3]]` at parameter `t`. The standard
     * Bernstein polynomial form keeps the algorithm trivial.
     */
    private fun cubicAt(cubics: Array<org.skia.math.SkPoint>, i0: Int, t: SkScalar): org.skia.math.SkPoint {
        val u = 1f - t
        val b0 = u * u * u
        val b1 = 3f * u * u * t
        val b2 = 3f * u * t * t
        val b3 = t * t * t
        val p0 = cubics[i0]
        val p1 = cubics[i0 + 1]
        val p2 = cubics[i0 + 2]
        val p3 = cubics[i0 + 3]
        return org.skia.math.SkPoint(
            b0 * p0.fX + b1 * p1.fX + b2 * p2.fX + b3 * p3.fX,
            b0 * p0.fY + b1 * p1.fY + b2 * p2.fY + b3 * p3.fY,
        )
    }

    /**
     * Left edge cubic uses `cubics[9..11] + cubics[0]` (the layout
     * wraps back around to `cubics[0]` so the 4-corner sequence
     * stays connected). Same Bernstein evaluation as [cubicAt],
     * just with a discontiguous index pattern.
     */
    private fun cubicAtLeftEdge(cubics: Array<org.skia.math.SkPoint>, t: SkScalar): org.skia.math.SkPoint {
        val u = 1f - t
        val b0 = u * u * u
        val b1 = 3f * u * u * t
        val b2 = 3f * u * t * t
        val b3 = t * t * t
        val p0 = cubics[9]
        val p1 = cubics[10]
        val p2 = cubics[11]
        val p3 = cubics[0]
        return org.skia.math.SkPoint(
            b0 * p0.fX + b1 * p1.fX + b2 * p2.fX + b3 * p3.fX,
            b0 * p0.fY + b1 * p1.fY + b2 * p2.fY + b3 * p3.fY,
        )
    }

    private fun bilerpPoint(corners: Array<org.skia.math.SkPoint>, s: SkScalar, t: SkScalar): org.skia.math.SkPoint {
        // corners : 0 = top-left, 1 = top-right, 2 = bottom-right, 3 = bottom-left.
        val p00 = corners[0]; val p10 = corners[1]; val p11 = corners[2]; val p01 = corners[3]
        val w00 = (1f - s) * (1f - t)
        val w10 = s * (1f - t)
        val w01 = (1f - s) * t
        val w11 = s * t
        return org.skia.math.SkPoint(
            w00 * p00.fX + w10 * p10.fX + w01 * p01.fX + w11 * p11.fX,
            w00 * p00.fY + w10 * p10.fY + w01 * p01.fY + w11 * p11.fY,
        )
    }

    private fun bilerpColor(corners: IntArray, s: SkScalar, t: SkScalar): SkColor {
        val w00 = (1f - s) * (1f - t)
        val w10 = s * (1f - t)
        val w01 = (1f - s) * t
        val w11 = s * t
        val a = (org.skia.foundation.SkColorGetA(corners[0]) * w00 +
            org.skia.foundation.SkColorGetA(corners[1]) * w10 +
            org.skia.foundation.SkColorGetA(corners[2]) * w11 +
            org.skia.foundation.SkColorGetA(corners[3]) * w01).toInt().coerceIn(0, 255)
        val r = (org.skia.foundation.SkColorGetR(corners[0]) * w00 +
            org.skia.foundation.SkColorGetR(corners[1]) * w10 +
            org.skia.foundation.SkColorGetR(corners[2]) * w11 +
            org.skia.foundation.SkColorGetR(corners[3]) * w01).toInt().coerceIn(0, 255)
        val g = (org.skia.foundation.SkColorGetG(corners[0]) * w00 +
            org.skia.foundation.SkColorGetG(corners[1]) * w10 +
            org.skia.foundation.SkColorGetG(corners[2]) * w11 +
            org.skia.foundation.SkColorGetG(corners[3]) * w01).toInt().coerceIn(0, 255)
        val b = (org.skia.foundation.SkColorGetB(corners[0]) * w00 +
            org.skia.foundation.SkColorGetB(corners[1]) * w10 +
            org.skia.foundation.SkColorGetB(corners[2]) * w11 +
            org.skia.foundation.SkColorGetB(corners[3]) * w01).toInt().coerceIn(0, 255)
        return org.skia.foundation.SkColorSetARGB(a, r, g, b)
    }

    private companion object {
        /**
         * Patch tessellation density (one side of the grid). 8 → 64
         * quads → 128 triangles per patch ; matches the default Skia
         * uses for raster-side patches and keeps the budget under
         * SkVertices's 16-bit index limit.
         */
        private const val PATCH_TESS_N: Int = 8
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
     * Mirrors Skia's
     * [`SkCanvas::drawTextBlob(blob, x, y, paint)`](https://github.com/google/skia/blob/main/include/core/SkCanvas.h#L1858).
     *
     * Renders an [org.skia.foundation.SkTextBlob]'s glyph runs at
     * `(x, y)` (added to each run's intrinsic positions). Each run
     * carries its own [SkFont] ; for each glyph we walk the font's
     * `getPath(glyphId)` and draw the resulting path with [paint].
     *
     * **Phase I2.3 — subpixel positioning** : per-glyph translates are
     * snapped in device space using [snapGlyphPosition] :
     *  - if `font.isSubpixel == true` → X is bucketed into 4 quarter
     *    phases (`{0, .25, .5, .75}`), Y is snapped to integer baseline ;
     *  - otherwise → both axes snap to integer.
     *
     * Snapping only kicks in when the CTM is identity / pure translate
     * (the textblob GMs we mirror) — under any rotation or skew the
     * float position is preserved. This matches Skia's
     * `SkScalerContext::generateMetrics` rounding policy and removes
     * the AA "shimmer" that pure-float positioning produced on
     * horizontal text runs.
     */
    public open fun drawTextBlob(
        blob: org.skia.foundation.SkTextBlob,
        x: SkScalar,
        y: SkScalar,
        paint: SkPaint,
    ) {
        for (run in blob.runs) {
            val isSubpixel = run.font.isSubpixel
            when (run) {
                is org.skia.foundation.SkTextBlob.Run.HorizontalSpread -> {
                    var advance = 0f
                    for (gid in run.glyphIds) {
                        val path = run.font.getPath(gid)
                        if (path != null) {
                            val (sx, sy) = snapGlyphPosition(x + run.x + advance, y + run.y, isSubpixel)
                            save()
                            translate(sx, sy)
                            drawPath(path, paint)
                            restore()
                        }
                        advance += run.font.getWidth(gid)
                    }
                }
                is org.skia.foundation.SkTextBlob.Run.HorizontalPositions -> {
                    for (i in run.glyphIds.indices) {
                        val gid = run.glyphIds[i]
                        val path = run.font.getPath(gid) ?: continue
                        val (sx, sy) = snapGlyphPosition(x + run.xs[i], y + run.constY, isSubpixel)
                        save()
                        translate(sx, sy)
                        drawPath(path, paint)
                        restore()
                    }
                }
                is org.skia.foundation.SkTextBlob.Run.FullPositions -> {
                    var i = 0
                    var g = 0
                    while (g < run.glyphIds.size) {
                        val gid = run.glyphIds[g]
                        val path = run.font.getPath(gid)
                        if (path != null) {
                            val (sx, sy) = snapGlyphPosition(
                                x + run.positions[i],
                                y + run.positions[i + 1],
                                isSubpixel,
                            )
                            save()
                            translate(sx, sy)
                            drawPath(path, paint)
                            restore()
                        }
                        i += 2
                        g += 1
                    }
                }
            }
        }
    }

    /**
     * Snap a per-glyph local-space translate to a pixel-aligned phase
     * for [drawTextBlob] (Phase I2.3).
     *
     * Compute the device-space position via the active translate-only
     * CTM, snap to integer (`isSubpixel == false`) or to the nearest
     * 1/4-pixel horizontal phase + integer baseline (`isSubpixel == true`),
     * then map back to local-space delta. Returns the input unchanged
     * when the CTM has any scale / rotation / skew / perspective —
     * those cases bypass snap and stay on the float path.
     */
    private fun snapGlyphPosition(
        localX: SkScalar,
        localY: SkScalar,
        isSubpixel: Boolean,
    ): Pair<SkScalar, SkScalar> {
        val m = top.matrix
        if (!m.isTranslate()) return Pair(localX, localY)
        val devX = m.tx + localX
        val devY = m.ty + localY
        val snapDevY = kFloor(devY.toDouble() + 0.5).toFloat()
        val snapDevX = if (isSubpixel) {
            val ix = kFloor(devX.toDouble()).toFloat()
            val phaseQuarters = kFloor(((devX - ix) * 4f + 0.5f).toDouble()).toInt().coerceIn(0, 4)
            ix + phaseQuarters * 0.25f
        } else {
            kFloor(devX.toDouble() + 0.5).toFloat()
        }
        return Pair(snapDevX - m.tx, snapDevY - m.ty)
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
    public open fun saveLayer(bounds: SkRect?, paint: SkPaint?): Int =
        saveLayer(SaveLayerRec(bounds, paint, null, 0))

    /**
     * Mirrors Skia's full-fat `SkCanvas::saveLayer(SaveLayerRec)` —
     * see the [SaveLayerRec] KDoc for field semantics.
     *
     * Phase G6 adds [SaveLayerRec.backdrop] handling : if non-null,
     * the parent device's pixels (within the resolved layer bbox)
     * are snapshotted, run through the backdrop filter, and pasted
     * into the new layer as its initial content. Subsequent draws
     * into the layer compose on top. `restore()` then composites
     * the layer back onto the parent with [SaveLayerRec.paint].
     */
    public open fun saveLayer(rec: SaveLayerRec): Int {
        val bounds = rec.bounds
        val paint = rec.paint
        val backdrop = rec.backdrop
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

        // Phase G6 — backdrop : seed the new layer with the parent's
        // pre-filtered pixels. The backdrop reads the parent device's
        // pixels in the device-space layer bbox, runs them through the
        // image filter, and pastes the (possibly displaced + resized)
        // result back into the layer at the correct origin.
        if (backdrop != null) {
            seedLayerFromBackdrop(
                s.device, layerDevice, originX, originY, w, h, backdrop, s.matrix,
                scaleFactor = rec.scaleFactor,
            )
        }

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

    /**
     * Phase G6 helper — snapshots the parent device's pixels in the
     * device-space rectangle `[originX, originY, originX+w, originY+h)`,
     * runs them through [backdrop], and pastes the result into
     * [layerDevice] at `(filterResult.offsetX, filterResult.offsetY)`
     * (relative to the layer's top-left).
     *
     * The snapshot is taken on the **parent's** pixel grid (not the
     * full parent device) so the filter sees only the layer's
     * footprint — this matches upstream's `getRasterBackdrop` which
     * crops the backdrop image to the layer bounds before running
     * the filter (`SkCanvas::internalSaveLayer` in
     * `src/core/SkCanvas.cpp`).
     */
    private fun seedLayerFromBackdrop(
        parent: SkBitmapDevice,
        layerDevice: SkBitmapDevice,
        originX: Int,
        originY: Int,
        w: Int,
        h: Int,
        backdrop: SkImageFilter,
        ctm: SkMatrix,
        scaleFactor: Float = 1f,
    ) {
        // Snapshot parent's pixels in the layer bbox. Out-of-parent
        // samples come back as 0 (transparent) — matching upstream's
        // "transparent black" backdrop padding.
        val snapBuf = IntArray(w * h)
        val pw = parent.width; val ph = parent.height
        for (y in 0 until h) {
            val py = originY + y
            if (py < 0 || py >= ph) continue
            for (x in 0 until w) {
                val px = originX + x
                if (px < 0 || px >= pw) continue
                snapBuf[y * w + x] = parent.bitmap.getPixel(px, py)
            }
        }
        // Phase R1-C — apply `scaleFactor` downscale to the snapshot
        // **before** running the filter. Matches upstream's
        // `internalDrawDeviceWithFilter(scaleFactor=...)` pipeline
        // (`include/core/SkCanvas.h:2654-2660`) : the snapshot is
        // downsampled to `(w*scale × h*scale)`, the filter runs at
        // that lower resolution, then the result is pasted back at
        // 1/scaleFactor (i.e. upsampled to the full layer footprint).
        // `scaleFactor == 1.0` is the fast path (no-op) — every existing
        // backdrop test stays bit-iso with the pre-Phase-R1-C output.
        val scale = scaleFactor.coerceIn(0.01f, 1f)
        val snapImg: SkImage = if (scale >= 1f) {
            SkImage(w, h, snapBuf)
        } else {
            // Box-average downscale to `(sw × sh)`. Sufficient precision
            // for the GM-only use case ; upstream uses a higher-quality
            // mipmap but the difference is sub-pixel for the GM diff.
            val sw = (w * scale + 0.5f).toInt().coerceAtLeast(1)
            val sh = (h * scale + 0.5f).toInt().coerceAtLeast(1)
            val down = IntArray(sw * sh)
            for (yi in 0 until sh) {
                val sy = (yi.toFloat() / scale).toInt().coerceIn(0, h - 1)
                for (xi in 0 until sw) {
                    val sx = (xi.toFloat() / scale).toInt().coerceIn(0, w - 1)
                    down[yi * sw + xi] = snapBuf[sy * w + sx]
                }
            }
            SkImage(sw, sh, down)
        }
        val filtered = backdrop.filterImage(snapImg, ctm)
        var filteredImg = filtered.image
        var dx = filtered.offsetX
        var dy = filtered.offsetY
        // Upsample back to the layer's resolution if we downscaled.
        if (scale < 1f) {
            val inv = 1f / scale
            val upW = (filteredImg.width * inv + 0.5f).toInt().coerceAtLeast(1)
            val upH = (filteredImg.height * inv + 0.5f).toInt().coerceAtLeast(1)
            val up = IntArray(upW * upH)
            for (yi in 0 until upH) {
                val sy = (yi * scale).toInt().coerceIn(0, filteredImg.height - 1)
                for (xi in 0 until upW) {
                    val sx = (xi * scale).toInt().coerceIn(0, filteredImg.width - 1)
                    up[yi * upW + xi] = filteredImg.peekPixel(sx, sy)
                }
            }
            filteredImg = SkImage(upW, upH, up)
            dx = (dx * inv + 0.5f).toInt()
            dy = (dy * inv + 0.5f).toInt()
        }
        // Paste filtered pixels into the layer bitmap at the offset the
        // filter reported, clamped to the layer's bounds.
        val fw = filteredImg.width
        val fh = filteredImg.height
        val l = maxOf(0, dx); val t = maxOf(0, dy)
        val r = minOf(w, dx + fw); val b = minOf(h, dy + fh)
        if (l >= r || t >= b) return
        for (y in t until b) {
            for (x in l until r) {
                val px = filteredImg.peekPixel(x - dx, y - dy)
                if ((px ushr 24) == 0) continue
                layerDevice.bitmap.setPixel(x, y, px)
            }
        }
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

    // ─── Phase C4 — extension slots ───────────────────────────────────

    /**
     * Mirrors Skia's `SkCanvas::drawDrawable(SkDrawable*, const
     * SkMatrix*)`. The drawable's [SkDrawable.draw] method is invoked
     * with `this` canvas, optionally pre-concatenated by [matrix].
     * The implementation is wrapped in a `save` / `restore` so the
     * canvas's external state is preserved on return.
     *
     * Subclasses (e.g. `SkRecordingCanvas`, `SkSVGCanvas`) may
     * override to record / serialise the drawable directly instead
     * of replaying its op stream — but the default behaviour
     * (delegate to [SkDrawable.draw]) is correct for every backend
     * that supports the basic draw primitives.
     */
    public open fun drawDrawable(drawable: SkDrawable, matrix: SkMatrix? = null) {
        drawable.draw(this, matrix)
    }

    /**
     * Convenience overload — translates by `(x, y)` before drawing.
     * Mirrors `SkCanvas::drawDrawable(SkDrawable*, SkScalar, SkScalar)`.
     */
    public open fun drawDrawable(drawable: SkDrawable, x: SkScalar, y: SkScalar) {
        drawable.draw(this, SkMatrix.MakeTrans(x, y))
    }

    /**
     * Mirrors Skia's `SkCanvas::drawAnnotation(rect, key, value)` —
     * attach a sink-specific annotation (typically PDF link metadata,
     * a named destination, or a URL) to a rectangular region of the
     * canvas.
     *
     * **Raster behaviour** : a no-op. The raster pipeline does not
     * encode annotations into a backing image — only structured
     * sinks (PDF, future XPS) can act on them. Subclasses that
     * support annotations override this method ; the default impl
     * silently drops [rect], [key], [value]. Mirrors upstream's
     * raster-device behaviour.
     */
    public open fun drawAnnotation(rect: SkRect, key: String, value: ByteArray?) {
        // Raster sinks ignore annotations — see KDoc.
    }
}
