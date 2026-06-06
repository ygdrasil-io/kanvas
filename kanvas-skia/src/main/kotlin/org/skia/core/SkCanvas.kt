package org.skia.core

import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlender
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkAAClip
import org.skia.foundation.SkClipOp
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkRegion
import org.graphiks.math.SkColor
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkFont
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkShader
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathDirection
import org.skia.foundation.SkRRect
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkSurfaceProps
import org.skia.foundation.SkTextEncoding
import org.skia.foundation.SkTileMode
import org.skia.foundation.SkVertices
import org.skia.foundation.opentype.OpenTypeTypeface
import org.graphiks.math.SkIRect
import org.graphiks.math.SkM44
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.graphiks.math.SkScalar
import java.nio.ByteBuffer
import java.util.zip.Inflater
import kotlin.math.ceil as kCeil
import kotlin.math.floor as kFloor

public object SkAAClipDifferenceTrace {
    public data class MatrixSnapshot(
        public val sx: Float,
        public val kx: Float,
        public val tx: Float,
        public val ky: Float,
        public val sy: Float,
        public val ty: Float,
        public val persp0: Float,
        public val persp1: Float,
        public val persp2: Float,
    )

    public data class Event(
        public val index: Int,
        public val op: String,
        public val doAntiAlias: Boolean,
        public val stateClipBounds: SkIRect,
        public val matrix: MatrixSnapshot,
        public val parent: SkAAClip.DebugSnapshot,
        public val path: SkAAClip.DebugSnapshot,
        public val result: SkAAClip.DebugSnapshot,
    )

    private val lock = Any()
    @Volatile
    private var enabled: Boolean = false
    private var probeRows: Set<Int> = emptySet()
    private var probePoints: Set<SkAAClip.DebugProbePoint> = emptySet()
    private val events = mutableListOf<Event>()

    public fun configure(
        probeRows: Set<Int> = emptySet(),
        probePoints: Set<SkAAClip.DebugProbePoint> = emptySet(),
    ) {
        synchronized(lock) {
            this.probeRows = probeRows
            this.probePoints = probePoints
            events.clear()
            enabled = true
        }
    }

    public fun reset() {
        synchronized(lock) {
            enabled = false
            probeRows = emptySet()
            probePoints = emptySet()
            events.clear()
        }
    }

    public fun snapshot(): List<Event> = synchronized(lock) { events.toList() }

    internal fun recordClipPathDifference(
        stateClipBounds: SkIRect,
        matrix: SkMatrix,
        doAntiAlias: Boolean,
        parent: SkAAClip,
        path: SkAAClip,
        result: SkAAClip,
    ) {
        if (!enabled) return
        synchronized(lock) {
            if (!enabled) return
            events += Event(
                index = events.size,
                op = "kDifference",
                doAntiAlias = doAntiAlias,
                stateClipBounds = stateClipBounds.copy(),
                matrix = MatrixSnapshot(
                    sx = matrix.sx,
                    kx = matrix.kx,
                    tx = matrix.tx,
                    ky = matrix.ky,
                    sy = matrix.sy,
                    ty = matrix.ty,
                    persp0 = matrix.persp0,
                    persp1 = matrix.persp1,
                    persp2 = matrix.persp2,
                ),
                parent = parent.debugSnapshot(probeRows, probePoints),
                path = path.debugSnapshot(probeRows, probePoints),
                result = result.debugSnapshot(probeRows, probePoints),
            )
        }
    }
}

/**
 * Cast a [SkDevice] down to [SkBitmapDevice] for paths that still need the
 * raster-only surface (layer composite-back, image-filter snapshots,
 * `setActiveClip*`, the triangle helpers). Will fail fast with a useful
 * message once [SkCanvas] is wired to a non-raster device (G1.2+) that
 * accidentally exercises one of these paths. Generalising any of them
 * across backends is deferred to the relevant later G-phase.
 */
private fun SkDevice.requireBitmap(op: String): SkBitmapDevice =
    this as? SkBitmapDevice
        ?: error(
            "SkCanvas.$op currently requires SkBitmapDevice (CPU raster); " +
                "current device is ${this::class.simpleName}. " +
                "See MIGRATION_PLAN_GPU_WEBGPU.md Phase G1.1 — the SkDevice " +
                "abstraction is intentionally incomplete for layer / filter / " +
                "triangle paths until a later G-phase generalises them.",
        )

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
public open class SkCanvas(rootDevice: SkDevice, surfaceProps: SkSurfaceProps? = null) {

    public constructor(bitmap: SkBitmap) : this(SkBitmapDevice(bitmap), null)
    public constructor(bitmap: SkBitmap, surfaceProps: SkSurfaceProps?) : this(SkBitmapDevice(bitmap), surfaceProps)

    /**
     * Captured at construction time — the [SkSurface]'s pixel-geometry /
     * behaviour hints, inherited by sub-surfaces created via
     * [makeSurface] when the caller doesn't pass their own override.
     * `null` here means "no opinion" and matches upstream's
     * `nullptr fProps` sentinel ; [surfaceProps] returns a
     * default-constructed [SkSurfaceProps] in that case.
     */
    private val _surfaceProps: SkSurfaceProps? = surfaceProps

    /**
     * Mirrors Skia's `SkCanvas::getProps()` — returns this canvas's
     * (parent surface's) pixel-geometry / behaviour hints, defaulted
     * to a zero-fill [SkSurfaceProps] when the canvas was constructed
     * without an explicit value (matches upstream's
     * `if (!fProps) return SkSurfaceProps()` fallback).
     */
    public open fun surfaceProps(): SkSurfaceProps = _surfaceProps ?: SkSurfaceProps()

    /**
     * Mirrors Skia's
     * [`SkCanvas::makeSurface`](https://github.com/google/skia/blob/main/include/core/SkCanvas.h#L295)
     * — return a new raster surface "compatible with this canvas"
     * (matching colour type / colour space, inheriting the canvas's
     * [SkSurfaceProps] when [props] is `null`). Used by GMs like
     * `Xfermodes3GM` and `hdr_pip_blur` to spin up an off-screen pad
     * the same shape and colour profile as their main canvas, blit a
     * filtered draw into it, then composite back.
     *
     * Returns `null` when [info] is empty — matches Skia's validity
     * contract on `SkSurface::makeSurface`.
     */
    public open fun makeSurface(info: org.skia.foundation.SkImageInfo, props: SkSurfaceProps? = null): SkSurface? {
        if (info.isEmpty()) return null
        return org.skia.foundation.SkSurfaces.Raster(info, rowBytes = 0, props = props ?: this.surfaceProps())
    }

    /**
     * Mirrors Skia's `SkCanvas::imageInfo()` — returns the [org.skia.foundation.SkImageInfo]
     * of this canvas's root (backing) device. The colour space, colour type,
     * alpha type, and dimensions reflect the bitmap that was passed to the
     * canvas's constructor (or the surface that created this canvas).
     *
     * GMs use this to build a new surface in a *different* colour space
     * (see `colorspace2.cpp` — `canvas->makeSurface(canvas->imageInfo().makeColorSpace(midCS))`),
     * preserving the original dimensions and pixel format while swapping
     * the working space.
     *
     * Mirrors upstream `SkCanvas::imageInfo()` → `rootDevice()->imageInfo()`.
     * Requires a raster ([SkBitmapDevice]) root device; throws for GPU devices
     * (deferred to a G-phase where [SkDevice] grows `imageInfo()`).
     */
    public open fun imageInfo(): org.skia.foundation.SkImageInfo {
        val bm = device.requireBitmap("imageInfo").bitmap
        val alphaType = when (bm.colorType) {
            org.skia.foundation.SkColorType.kRGBA_F16Norm -> org.skia.foundation.SkAlphaType.kPremul
            else -> org.skia.foundation.SkAlphaType.kUnpremul
        }
        return org.skia.foundation.SkImageInfo.Make(
            width = bm.width,
            height = bm.height,
            colorType = bm.colorType,
            alphaType = alphaType,
            colorSpace = bm.colorSpace,
        )
    }

    /**
     * Mirrors Skia's `SkCanvas::readPixels(const SkPixmap&, int, int)` for
     * raster canvases. Coordinates are root-device coordinates, independent
     * from the current CTM, matching upstream's readback contract.
     */
    public open fun readPixels(dst: SkPixmap, srcX: Int = 0, srcY: Int = 0): Boolean =
        SkImage.Make(device.requireBitmap("readPixels").bitmap).readPixels(dst, srcX, srcY)

    /**
     * Mirrors Skia's `SkCanvas::readPixels(const SkImageInfo&, void*, size_t, int, int)`.
     */
    public open fun readPixels(
        dstInfo: SkImageInfo,
        dstPixels: ByteBuffer,
        dstRowBytes: Int,
        srcX: Int = 0,
        srcY: Int = 0,
    ): Boolean =
        readPixels(SkPixmap(dstInfo, dstPixels, dstRowBytes), srcX, srcY)

    /**
     * Mirrors Skia's `SkCanvas::readPixels(const SkBitmap&, int, int)`.
     * `SkBitmap.peekPixels` is not required here because kanvas-skia bitmaps
     * always expose typed storage through [SkBitmap.getPixel] / [SkBitmap.setPixel].
     */
    public open fun readPixels(dst: SkBitmap, srcX: Int = 0, srcY: Int = 0): Boolean {
        if (dst.width <= 0 || dst.height <= 0) return false
        val src = device.requireBitmap("readPixels").bitmap
        if (src.width <= 0 || src.height <= 0) return false
        if (srcX >= src.width || srcY >= src.height) return false
        if (srcX + dst.width <= 0 || srcY + dst.height <= 0) return false

        val srcL = maxOf(srcX, 0)
        val srcT = maxOf(srcY, 0)
        val srcR = minOf(src.width, srcX + dst.width)
        val srcB = minOf(src.height, srcY + dst.height)
        if (srcL >= srcR || srcT >= srcB) return false

        for (sy in srcT until srcB) {
            for (sx in srcL until srcR) {
                dst.setPixel(sx - srcX, sy - srcY, src.getPixel(sx, sy))
            }
        }
        return true
    }

    /** Mirrors Skia's `SkCanvas::writePixels(const SkPixmap&, int, int)`. */
    public open fun writePixels(src: SkPixmap, dstX: Int = 0, dstY: Int = 0): Boolean =
        device.requireBitmap("writePixels").bitmap.writePixels(src, dstX, dstY)

    /**
     * Mirrors Skia's `SkCanvas::writePixels(const SkBitmap&, int, int)`.
     */
    public open fun writePixels(src: SkBitmap, dstX: Int = 0, dstY: Int = 0): Boolean {
        if (src.width <= 0 || src.height <= 0) return false
        val dst = device.requireBitmap("writePixels").bitmap
        if (dst.width <= 0 || dst.height <= 0) return false
        if (dstX >= dst.width || dstY >= dst.height) return false
        if (dstX + src.width <= 0 || dstY + src.height <= 0) return false

        for (sy in 0 until src.height) {
            val dy = dstY + sy
            if (dy < 0 || dy >= dst.height) continue
            for (sx in 0 until src.width) {
                val dx = dstX + sx
                if (dx < 0 || dx >= dst.width) continue
                dst.setPixel(dx, dy, src.getPixel(sx, sy))
            }
        }
        return true
    }

    /** The root (backing) device. Layers push their own devices on the stack. */
    public val device: SkDevice = rootDevice

    /**
     * Convenience accessor for the root device's bitmap — only meaningful
     * when the root is a [SkBitmapDevice] (the common case). Throws when
     * the canvas is backed by a non-raster device (e.g. `SkWebGpuDevice`
     * arriving in G1.2).
     */
    public val bitmap: SkBitmap get() = device.requireBitmap("bitmap").bitmap

    /**
     * One stack entry per `save` / `saveLayer`. Carries the active CTM
     * matrix and the clip in **current device coordinates**, plus the device
     * that draws land in.
     */
    private data class State(
        var matrix: SkMatrix,
        var clip: SkIRect,
        var device: SkDevice,
        /**
         * R3.1-bis — full 4×4 CTM, **only** populated when an actual
         * 3D/perspective component has been introduced via
         * [concat] / [setMatrix] overloads taking an [SkM44]. When
         * `null`, the 3×3 [matrix] field is the authoritative CTM and
         * every 2D fast path keeps using it as before. Any 2D matrix
         * mutator ([translate], [scale], [rotate], [skew], the
         * `concat(SkMatrix)` / `setMatrix(SkMatrix)` overloads,
         * [resetMatrix]) clears this slot — the 4×4 representation is
         * derived on demand from [matrix] in [getLocalToDevice] when
         * the slot is empty.
         *
         * Wiring the 4×4 into the rasteriser is out of scope for
         * R3.1-bis ; this field is purely an API-surface store so
         * `getLocalToDevice()` round-trips a previously-set
         * perspective matrix.
        */
        var m44: SkM44? = null,
        /** Non-null iff this state was opened by `saveLayer`. */
        var layer: Layer? = null,
        /** Non-null iff this state was opened by `saveBehind`. */
        var saveBehind: SaveBehind? = null,
        /**
         * Phase I3.3.b — band-encoded AA clip carrying the
         * combined `clipPath` / `clipRRect` coverage. `null` = pure
         * rectangular clip (fast path ; the bbox is [State.clip]).
         * Replaces the Phase 7q `clipMask: ByteArray?` 2D byte buffer
         * — see [SkAAClip] for the run-encoded representation.
         */
        var aaClip: SkAAClip? = null,
        /**
         * Phase R2.14 — per-pixel shader-driven clip. When non-null,
         * a draw's per-pixel coverage is additionally multiplied by
         * `clipShader.shadeRow(devX, devY).alpha / 255`. Captured
         * together with the CTM at `clipShader()` call time so the
         * shader's local-to-device mapping is frozen at that point
         * (matches Skia's "clip is taken in current CTM" semantics).
         * Save / saveLayer inherit by reference ; `clipShader()` always
         * replaces the slot, so parent states stay untouched.
         */
        var clipShader: SkShader? = null,
        /** CTM snapshot captured when [clipShader] was set. */
        var clipShaderCtm: SkMatrix = SkMatrix.Identity,
        /** Clip-op for the bound [clipShader]. */
        var clipShaderOp: SkClipOp = SkClipOp.kIntersect,
        /**
         * G2.x -- analytical clip shape captured by [clipPath] when the
         * path is one of the canonical simple shapes (rect / oval /
         * circle / uniform-corner rrect) and the CTM is axis-aligned.
         * Stored in **device coords**. Used by non-raster devices that
         * can evaluate the shape's coverage analytically in a shader,
         * sparing them the rasterised [aaClip] mask. The raster
         * [SkBitmapDevice] ignores this slot and keeps using [aaClip].
         *
         * Set by intersect-style `clipPath` / `clipRRect` ops via
         * [SkClipShape.tryDetect]. Single-shape only : a second simple-
         * shape `clipPath` clears the slot back to `null` (the canvas
         * falls back to the AA-mask path, which then triggers the GPU
         * device's fail-fast when no simple shape can carry the
         * combined clip). Difference ops also clear the slot.
         */
        var simpleShapeClip: SkClipShape? = null,
    )

    /**
     * Bookkeeping for an active offscreen layer. On `restore` of the
     * matching state, the layer's device is composited back into
     * [parentDevice] at `(originX, originY)` using [paint] (alpha +
     * blendMode honoured ; colour filter / image filter are CPU-only
     * for now — see Phase G-saveLayer).
     */
    private data class Layer(
        val parentDevice: SkDevice,
        val originX: Int,
        val originY: Int,
        val paint: SkPaint?,
        val filters: List<SkImageFilter?>? = null,
    )

    private data class SaveBehind(
        val snapshot: SkBitmapDevice,
        val originX: Int,
        val originY: Int,
        val bounds: SkIRect,
    )

    private val stack: ArrayDeque<State> = ArrayDeque<State>().apply {
        addLast(State(SkMatrix.Identity, rootDevice.deviceClipBounds(), rootDevice))
    }

    private val top: State get() = stack.last()

    /**
     * Read-only access to the current CTM as a 3×3 [SkMatrix].
     *
     * **Deprecated** — mirrors Skia's
     * [`SkCanvas::getTotalMatrix`](https://github.com/google/skia/blob/main/include/core/SkCanvas.h#L2288)
     * (gated by `SK_SUPPORT_LEGACY_GETTOTALMATRIX` upstream). New
     * code should call [getLocalToDevice] (full 4×4) or
     * [getLocalToDeviceAsMatrix] (3×3 affine, null on perspective).
     *
     * Returns the 3×3 drop of [getLocalToDevice], falling back to the
     * identity matrix if the 4×4 has no defined 3×3 image. Retained
     * for upstream API parity and existing internal call sites.
     */
    @Deprecated(
        message = "Use getLocalToDevice() (SkM44) or getLocalToDeviceAsMatrix() (SkMatrix?).",
        replaceWith = ReplaceWith("getLocalToDevice()"),
    )
    public open fun getTotalMatrix(): SkMatrix = getLocalToDevice().asM33() ?: SkMatrix.Identity

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
        stack.addLast(State(
            s.matrix, s.clip.copy(), s.device,
            m44 = s.m44?.let { SkM44(it) },
            aaClip = s.aaClip,
            clipShader = s.clipShader,
            clipShaderCtm = s.clipShaderCtm,
            clipShaderOp = s.clipShaderOp,
            simpleShapeClip = s.simpleShapeClip,
        ))
        return stack.size - 2
    }

    /**
     * Raster slice of Skia's private `SkCanvasPriv::SaveBehind`.
     *
     * The call pushes a regular save frame, snapshots [bounds] in device
     * space, clears that saved region, then [restore] composites the
     * snapshot back with `kDstOver`. This is intentionally public in the
     * Kotlin port so GM code can cover the private upstream behavior.
     */
    public open fun saveBehind(bounds: SkRect?): Int {
        val s = top
        if (bounds != null && !getLocalClipBounds().intersects(bounds)) {
            return save()
        }

        val saveCountBefore = save()
        val saved = top
        val raster = s.device as? SkBitmapDevice ?: return saveCountBefore
        val devBounds = if (bounds == null) {
            s.clip.copy()
        } else {
            s.matrix.mapRect(bounds).round().also {
                if (!it.intersect(s.clip)) it.setEmpty()
            }
        }
        if (devBounds.isEmpty) return saveCountBefore

        val w = devBounds.width()
        val h = devBounds.height()
        val snapshotBitmap = SkBitmap(w, h, raster.bitmap.colorSpace, raster.bitmap.colorType)
        for (y in 0 until h) {
            for (x in 0 until w) {
                snapshotBitmap.setPixel(x, y, raster.bitmap.getPixel(devBounds.left + x, devBounds.top + y))
            }
        }
        saved.saveBehind = SaveBehind(SkBitmapDevice(snapshotBitmap), devBounds.left, devBounds.top, devBounds)

        drawBehind(SkPaint().apply { blendMode = SkBlendMode.kClear })
        return saveCountBefore
    }

    /**
     * Raster slice of Skia's private `SkCanvasPriv::DrawBehind`.
     * Draws [paint] as a full-device paint clipped to the active save-behind
     * bounds, using the current CTM for shader evaluation.
     */
    public open fun drawBehind(paint: SkPaint) {
        val saveBehind = stack.asReversed().firstOrNull { it.saveBehind != null }?.saveBehind ?: return
        val clip = top.clip.copy()
        if (!clip.intersect(saveBehind.bounds)) return
        top.device.drawPaint(top.matrix, clip, paint)
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
        val saveBehind = popped.saveBehind
        if (saveBehind != null) {
            top.device.compositeFrom(
                saveBehind.snapshot,
                saveBehind.originX,
                saveBehind.originY,
                top.clip,
                SkPaint().apply { blendMode = SkBlendMode.kDstOver },
            )
        }
        val layer = popped.layer ?: return

        val layerFilters = layer.filters
        val poppedBitmap = (popped.device as? SkBitmapDevice)?.bitmap
        val parentBitmap = (layer.parentDevice as? SkBitmapDevice)
        if (layerFilters != null && poppedBitmap != null && parentBitmap != null) {
            val snapshot = poppedBitmap.asImage()
            val filtersOrNull = if (layerFilters.isEmpty()) listOf<SkImageFilter?>(null) else layerFilters
            val proxyPaint = layer.paint?.copy()?.apply { this.imageFilter = null }
            for (filter in filtersOrNull) {
                val filterResult = filter?.filterImage(snapshot, popped.matrix)
                    ?: SkImageFilter.FilterResult(snapshot, 0, 0)
                val filteredImg = filterResult.image
                val filteredBitmap = SkBitmap(
                    filteredImg.width, filteredImg.height,
                    poppedBitmap.colorSpace, poppedBitmap.colorType,
                )
                for (yp in 0 until filteredImg.height) {
                    for (xp in 0 until filteredImg.width) {
                        filteredBitmap.setPixel(xp, yp, filteredImg.peekPixel(xp, yp))
                    }
                }
                parentBitmap.compositeFrom(
                    SkBitmapDevice(filteredBitmap),
                    layer.originX + filterResult.offsetX,
                    layer.originY + filterResult.offsetY,
                    top.clip,
                    proxyPaint,
                )
            }
            return
        }

        // Phase 7d.2 — when the layer's paint carries an imageFilter,
        // snapshot the layer to an SkImage, apply the filter, and
        // composite the filtered result onto the parent device with
        // the offset adjusted for the filter's displacement.
        //
        // Image-filter restore is raster-only ; the snapshot + filter
        // re-upload pipeline runs in CPU. GPU backends fall through
        // to the generic `compositeFrom` path below (the layer's
        // paint loses its imageFilter — visual diff documented as a
        // deferred Phase G-imageFilter follow-up).
        val layerPaint = layer.paint
        val imageFilter = layerPaint?.imageFilter
        if (imageFilter != null && poppedBitmap != null && parentBitmap != null) {
            val snapshot = poppedBitmap.asImage()
            val filterResult = imageFilter.filterImage(snapshot, popped.matrix)
            val filteredImg = filterResult.image
            val filteredBitmap = SkBitmap(
                filteredImg.width, filteredImg.height,
                poppedBitmap.colorSpace, poppedBitmap.colorType,
            )
            for (yp in 0 until filteredImg.height) {
                for (xp in 0 until filteredImg.width) {
                    filteredBitmap.setPixel(xp, yp, filteredImg.peekPixel(xp, yp))
                }
            }
            val filteredDevice = SkBitmapDevice(filteredBitmap)
            val proxyPaint = layerPaint.copy().apply { this.imageFilter = null }
            parentBitmap.compositeFrom(
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
        s.m44 = null
    }

    public open fun scale(sx: SkScalar, sy: SkScalar) {
        val s = top
        s.matrix = s.matrix.preScale(sx, sy)
        s.m44 = null
    }

    /** Mirrors Skia's `SkCanvas::rotate(deg)` — pre-concat with a rotation around the origin. */
    public open fun rotate(deg: SkScalar) {
        val s = top
        s.matrix = s.matrix.preRotate(deg)
        s.m44 = null
    }

    /**
     * Mirrors Skia's `SkCanvas::rotate(deg, px, py)` — pre-concat with a
     * rotation around an arbitrary pivot point.
     */
    public open fun rotate(deg: SkScalar, px: SkScalar, py: SkScalar) {
        val s = top
        s.matrix = s.matrix.preRotate(deg, px, py)
        s.m44 = null
    }

    /** Mirrors Skia's `SkCanvas::skew(sx, sy)` — pre-concat with a skew. */
    public open fun skew(sx: SkScalar, sy: SkScalar) {
        val s = top
        s.matrix = s.matrix.preSkew(sx, sy)
        s.m44 = null
    }

    /** Mirrors Skia's `SkCanvas::concat(SkMatrix)` — pre-concat with `mat`. */
    public open fun concat(mat: SkMatrix) {
        val s = top
        s.matrix = s.matrix.preConcat(mat)
        s.m44 = null
    }

    /**
     * Mirrors Skia's `SkCanvas::concat(const SkM44&)` — post-concat the
     * current 4×4 CTM with [m44].
     *
     * If the canvas's CTM is still purely affine (no prior
     * `concat(SkM44)` / `setMatrix(SkM44)` with perspective), the
     * existing 3×3 [SkMatrix] is promoted to an [SkM44] first via
     * [SkM44.setM33], then `this · m44` is stored in the per-state
     * slot. Skia's `concat` is a *pre-multiply* (the new transform
     * applies *before* the existing CTM in mapping order) — see
     * `SkCanvas::concat44` in `src/core/SkCanvas.cpp`.
     *
     * The 3×3 [State.matrix] field is also refreshed (via
     * [SkM44.asM33]) so existing 2D draw paths keep working for the
     * affine subset.
     */
    public open fun concat(m44: SkM44) {
        val s = top
        val current = s.m44 ?: SkM44(s.matrix)
        // Pre-multiply: this = current · m44 (i.e. m44 applies first).
        current.preConcat(m44)
        s.m44 = current
        // Keep the legacy 3×3 in sync — the rasteriser still reads it
        // for the affine fast paths. Perspective info that would not
        // round-trip is intentionally dropped here (mapped via asM33)
        // but the full 4×4 is preserved in `m44`.
        s.matrix = current.asM33() ?: SkMatrix.Identity
    }

    /** Mirrors Skia's `SkCanvas::setMatrix(SkMatrix)` — replaces the CTM wholesale. */
    public open fun setMatrix(mat: SkMatrix) {
        val s = top
        s.matrix = mat
        s.m44 = null
    }

    /**
     * Mirrors Skia's `SkCanvas::setMatrix(const SkM44&)` — replaces the
     * CTM with [m44]. Any prior matrix state (including 4×4
     * perspective) is overwritten.
     */
    public open fun setMatrix(m44: SkM44) {
        val s = top
        s.m44 = SkM44(m44)
        s.matrix = m44.asM33() ?: SkMatrix.Identity
    }

    /** Mirrors Skia's `SkCanvas::resetMatrix()`. */
    public open fun resetMatrix() {
        val s = top
        s.matrix = SkMatrix.Identity
        s.m44 = null
    }

    /**
     * Mirrors Skia's `SkCanvas::getLocalToDevice()` — return the full
     * 4×4 CTM, including any perspective / Z component installed via
     * the [SkM44] overloads. When no [SkM44] is active in the current
     * state, the 3×3 [SkMatrix] is promoted on the fly.
     */
    public open fun getLocalToDevice(): SkM44 {
        val s = top
        return s.m44?.let { SkM44(it) } ?: SkM44(s.matrix)
    }

    /**
     * Return the 3×3 affine drop of the current CTM, or `null` if the
     * CTM carries true 3D / perspective content that would not survive
     * the projection. Mirrors Skia's
     * `SkCanvas::getLocalToDeviceAs3x3()`, but Kotlin-nullable so the
     * caller can detect perspective without inspecting the matrix
     * itself.
     *
     * The CTM is treated as "perspective" when any of the following
     * 4×4 entries diverge from their identity values:
     *  * column 2 = `(0, 0, 1, 0)` (z-output stays at z);
     *  * row 2    = `(0, 0, 1, 0)` (z-input is ignored);
     *  * the bottom row's z entry (`fMat[11]`) is non-zero (z
     *    contributes to the homogeneous divide).
     *
     * When only [SkMatrix]-shaped perspective is present (`persp0`,
     * `persp1`, `persp2`), the M44 still round-trips through 3×3 so
     * this method returns the affine version.
     */
    public open fun getLocalToDeviceAsMatrix(): SkMatrix? {
        val s = top
        val m = s.m44 ?: return s.matrix
        // True 3D / perspective sentinel: any of the M44 entries that
        // wouldn't survive `asM33()` round-tripping for a (x, y, 0, 1)
        // input are non-default.
        val r = m.fMat
        val isFlat =
            r[2]  == 0f && r[6]  == 0f && r[10] == 1f && r[14] == 0f &&
            r[8]  == 0f && r[9]  == 0f && r[11] == 0f
        return if (isFlat) m.asM33() else null
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
            s.simpleShapeClip = null
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

        // G2.x -- simple-shape capture for non-raster (GPU) devices.
        // Only kIntersect, only non-inverse fill types ; inverse fills
        // mean "outside the shape" which we don't model in the
        // [SkClipShape] sealed type.
        if (!path.fillType.isInverse()) {
            val detected = SkClipShape.tryDetect(path, s.matrix)
            when (detected) {
                null -> {
                    // Unrecognised path : leave the existing slot alone if
                    // it was already null ; otherwise clear it (can't
                    // intersect a recorded shape with an arbitrary path).
                    if (s.simpleShapeClip != null) s.simpleShapeClip = null
                }
                is SkClipShape.Rect -> {
                    // Pure rect intersection : the new clip rect is
                    // already encoded in [s.clip] (a tighter [SkIRect]).
                    // Don't disturb a previously recorded curved shape
                    // -- the canvas still represents (existing shape) ∩
                    // (new rect) correctly because [s.clip] is the
                    // outer bbox and the curved shape sits inside it.
                }
                else -> {
                    // Curved shape (oval / circle / rrect) :
                    //  - first such shape : record it.
                    //  - second curved shape : we don't compose two
                    //    curved shapes yet -- drop to null so [bindClip]
                    //    falls through to the AA-mask path. On non-
                    //    raster devices the existing fail-fast then
                    //    triggers, which is honest about the gap.
                    s.simpleShapeClip = if (s.simpleShapeClip == null) detected else null
                }
            }
        } else {
            // Inverse fill : clear -- can't represent "outside shape" in
            // the analytic clip slot today.
            s.simpleShapeClip = null
        }
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
            s.simpleShapeClip = null
            return
        }
        val parent = s.aaClip ?: SkAAClip(s.clip)
        val pathAac = rasterisePathToAaClip(s, path, s.clip, doAntiAlias)
        val combined = SkAAClip(parent)
        combined.op(pathAac, SkRegion.Op.kDifference)
        SkAAClipDifferenceTrace.recordClipPathDifference(
            stateClipBounds = s.clip,
            matrix = s.matrix,
            doAntiAlias = doAntiAlias,
            parent = parent,
            path = pathAac,
            result = combined,
        )
        s.aaClip = combined
        // M4 -- analytic difference clip. If the path is a canonical simple
        // shape (rect / oval / circle / uniform-corner rrect) under an
        // axis-aligned CTM, we can let the GPU device evaluate `1 -
        // rrect_cov(p)` per pixel instead of rasterising the difference
        // into an alpha mask. Same scope guard as `clipPathIntersect` :
        // inverse fill types and unrecognised paths drop to null (the
        // GPU device then takes the existing arbitrary-clipPath fallback,
        // which still throws at `bindClip` for non-shader pipelines).
        if (!path.fillType.isInverse() && s.simpleShapeClip == null) {
            val detected = SkClipShape.tryDetect(path, s.matrix, SkClipOp.kDifference)
            // Only first-difference is honoured today : composing an
            // existing intersect shape with a difference would need a
            // pipeline with two clip uniforms. Single-difference covers
            // every clipRect(rect, kDifference) / clipRRect(_, kDifference)
            // call site from a freshly-saved state.
            s.simpleShapeClip = detected
        } else {
            s.simpleShapeClip = null
        }
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
            s.simpleShapeClip = null
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
        // Region intersection can't tighten an analytic simple shape ;
        // drop it (the band-encoded region needs the AA-mask path).
        s.simpleShapeClip = null
    }

    private fun clipRegionDifference(s: State, region: SkRegion) {
        val w = s.clip.right - s.clip.left
        val h = s.clip.bottom - s.clip.top
        if (w <= 0 || h <= 0) {
            s.aaClip = null
            s.simpleShapeClip = null
            return
        }
        val regionAaClip = SkAAClip(region)
        val combined = SkAAClip(s.aaClip ?: SkAAClip(s.clip))
        combined.op(regionAaClip, SkRegion.Op.kDifference)
        s.aaClip = combined
        s.simpleShapeClip = null
    }

    /**
     * Bind the active state's AA clip + clip-shader onto the device
     * before each draw. R-suivi.20 — extended to push the clip-shader
     * (frozen CTM + op) so every draw entry point (drawRect, drawPath,
     * drawImage, …) honours the shader, not just [drawPaint].
     */
    private fun bindClip(s: State) {
        // G1.2 — `setActiveClip` / `setActiveClipShader` are raster-only
        // (SkAAClip is band-encoded raster coverage; clipShader is a
        // per-pixel shader sampled at draw time). Non-raster devices
        // (e.g. SkWebGpuDevice) have no concept of either yet ; the
        // integer clip rect (`s.clip`) is still propagated to every draw
        // call as a parameter, so basic `clipRect` still works across
        // backends. AA clip + clip-shader support on GPU lands when
        // SkWebGpuDevice grows shader-side clip evaluation (G2+).
        //
        // **G2.x simple-shape clip.** When the active state carries a
        // [State.simpleShapeClip] (set by [clipPath] / [clipRRect] when
        // the path matches one of the canonical simple-shape contours
        // and the CTM is axis-aligned), the canvas pushes it onto every
        // device via [SkDevice.setActiveClipShape]. The raster device
        // ignores it (it already has the rasterised aaClip). GPU
        // devices read it and evaluate per-pixel coverage analytically
        // in their fragment pipelines, sparing them an alpha mask.
        //
        // **Fail-fast on actual misuse.** Silently skipping when an
        // AA-clip or clip-shader is actually bound on a non-raster
        // device would let `clipPath` / `clipRRect` / `clipShader`
        // "succeed" and return wrong pixels. Throw with a useful
        // pointer instead so the caller can either fall back to
        // SkBitmapDevice or wait for the relevant G-phase. The
        // simple-shape clip carves out an honest fast path : if it's
        // set, the GPU device can render the clip analytically and we
        // skip the throw -- the rasterised aaClip is still present (as
        // an over-approximation), but the GPU shader's coverage is the
        // authoritative answer.
        val raster = s.device as? SkBitmapDevice
        if (raster == null) {
            s.device.setActiveClipShape(s.simpleShapeClip)
            // Allow aaClip to be present iff we also have a matching
            // simple-shape clip the GPU device can evaluate. Clip-shader
            // still throws (per-pixel shader sampling deferred to a
            // later slice).
            check(s.clipShader == null) {
                "${s.device::class.simpleName} does not support " +
                    "clipShader yet (G2+). Use clipRect() / clipPath() instead, " +
                    "or back the canvas with SkBitmapDevice. " +
                    "See MIGRATION_PLAN_GPU_WEBGPU.md."
            }
            check(s.aaClip == null || s.simpleShapeClip != null) {
                "${s.device::class.simpleName} does not support arbitrary " +
                    "clipPath (only axis-aligned rect / oval / circle / " +
                    "uniform-corner rrect under an axis-aligned CTM). The " +
                    "current clip needs a rasterised alpha mask. Use " +
                    "clipRect() or back the canvas with SkBitmapDevice. " +
                    "See MIGRATION_PLAN_GPU_WEBGPU.md."
            }
            return
        }
        raster.setActiveClip(s.aaClip)
        raster.setActiveClipShader(s.clipShader, s.clipShaderCtm, s.clipShaderOp)
    }

    // ─── Phase R2.14 — clipShader ─────────────────────────────────────────
    //
    // Mirrors Skia's `SkCanvas::clipShader(sk_sp<SkShader>, SkClipOp)`
    // (`include/core/SkCanvas.h:1140`). Pixels where the bound shader's
    // alpha is zero are clipped out ; intermediate alpha values modulate
    // per-pixel coverage. The shader is frozen against the current CTM
    // at call time (Skia semantics : the clip's local-to-device mapping
    // doesn't follow subsequent CTM mutations).
    //
    // R-suivi.20 — full rasterizer wiring : the clip shader is now
    // applied per-pixel inside the device's blend funnels (see
    // `SkBitmapDevice.setActiveClipShader` + the `activeClipShader`
    // check inside [blend] / [blendCustom] / [blendF16*]). Every draw
    // entry point (drawRect / drawPath / drawImage / drawOval /
    // drawRRect / drawArc / drawLine / drawPoints / drawPaint /
    // drawImageRect / drawString …) routes through `bindClip` which
    // pushes the shader onto the device. No per-entry-point opt-in
    // required.

    /**
     * Mirrors Skia's `SkCanvas::clipShader(shader, op)`. Adds a per-pixel
     * shader to the active clip stack. With [SkClipOp.kIntersect] (the
     * default), the new clip equals the existing clip intersected with
     * the shader's alpha channel ; with [SkClipOp.kDifference], it
     * equals the existing clip minus the shader's alpha (i.e. cut out
     * pixels where the shader is opaque).
     *
     * **Scope (R-suivi.20)** : every draw entry point honours the clip
     * shader — `drawRect`, `drawPath`, `drawImage`, `drawOval`,
     * `drawRRect`, `drawArc`, `drawLine`, `drawPoints`, `drawPaint`,
     * `drawImageRect`, `drawString`, … Per-pixel modulation is applied
     * inside the device's blend funnels, so all rasteriser flavours
     * (8-bit / F16 / custom blender) carry the coverage uniformly.
     */
    public open fun clipShader(shader: SkShader, op: SkClipOp = SkClipOp.kIntersect) {
        val s = top
        // R2 simplification : multiple clipShader() calls inside the
        // same save() frame replace each other ; Skia would stack them
        // with SkComposeShader / kSrcIn. Save/restore inheritance still
        // works because the slot is snapshotted into each new State.
        // R-suivi : honour stacking when callers need it.
        s.clipShader = shader
        s.clipShaderCtm = s.matrix
        s.clipShaderOp = op
    }

    /** Internal read-only accessor for tests / device wiring. */
    internal fun activeClipShader(): SkShader? = top.clipShader

    /** Internal accessor for the CTM that was active when the clipShader was set. */
    internal fun activeClipShaderCtm(): SkMatrix = top.clipShaderCtm

    /** Internal accessor for the active clipShader's [SkClipOp]. */
    internal fun activeClipShaderOp(): SkClipOp = top.clipShaderOp

    public open fun drawRect(rect: SkRect, paint: SkPaint) {
        if (paint.imageFilter != null) {
            val restoreCount = getSaveCount()
            val layerPaint = paint.copy()
            val innerPaint = paint.copy().apply {
                imageFilter = null
                colorFilter = null
                blendMode = SkBlendMode.kSrc
                alpha = 0xFF
            }
            val sourceBounds = innerPaint.computeFastBounds(
                rect,
                SkRect.MakeLTRB(rect.left, rect.top, rect.right, rect.bottom),
            )
            val filteredBounds = paint.computeFastBounds(
                rect,
                SkRect.MakeLTRB(rect.left, rect.top, rect.right, rect.bottom),
            )
            val layerBounds = SkRect.MakeLTRB(
                sourceBounds.left,
                sourceBounds.top,
                sourceBounds.right,
                sourceBounds.bottom,
            )
            layerBounds.join(filteredBounds)
            saveLayerForImageFilterSourceCapture(layerBounds, layerPaint)
            drawRect(rect, innerPaint)
            restoreToCount(restoreCount)
            return
        }

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
     * Mirrors Skia's `SkCanvas::ImageSetEntry`
     * ([include/core/SkCanvas.h](https://github.com/google/skia/blob/main/include/core/SkCanvas.h)) :
     *
     * ```cpp
     * struct ImageSetEntry {
     *     sk_sp<const SkImage> fImage;
     *     SkRect  fSrcRect;
     *     SkRect  fDstRect;
     *     int     fMatrixIndex;  // -1 ⇒ no preview matrix
     *     float   fAlpha;
     *     unsigned fAAFlags;     // bitmask of QuadAAFlags
     *     bool    fHasClip;      // dstClips index validity
     * };
     * ```
     *
     * One entry in the batch consumed by [experimental_DrawEdgeAAImageSet].
     * Each carries its own image, source sub-rect, destination rect, AA-edge
     * bitmask and a per-entry alpha multiplier — the API lets a renderer fuse
     * N image draws (typically tiled in compositor layers) into a single
     * operation that can share state and (on GPU) coalesce vertices.
     */
    public data class ImageSetEntry(
        public val image: SkImage,
        public val srcRect: SkRect,
        public val dstRect: SkRect,
        public val matrixIndex: Int = -1,
        public val alpha: SkScalar = 1f,
        public val aaFlags: Int = QuadAAFlags.kNone_QuadAAFlags,
        public val hasClip: Boolean = false,
    )

    /**
     * Mirrors Skia's `SkCanvas::experimental_DrawEdgeAAImageSet`
     * ([include/core/SkCanvas.h](https://github.com/google/skia/blob/main/include/core/SkCanvas.h)) :
     *
     * ```cpp
     * void experimental_DrawEdgeAAImageSet(const ImageSetEntry[], int count,
     *                                      const SkPoint dstClips[],
     *                                      const SkMatrix preViewMatrices[],
     *                                      const SkSamplingOptions&,
     *                                      const SkPaint*, SrcRectConstraint);
     * ```
     *
     * Batched per-image draw with per-entry edge-AA flags, per-entry alpha
     * multiplier and (optionally) per-entry destination clip-quad / preview
     * matrix indices. Each [ImageSetEntry] in [set] is conceptually rendered
     * as if by [drawImageRect] with the entry's `srcRect → dstRect`
     * mapping, the entry's `alpha` folded into [paint]'s alpha, and the
     * entry's AA flags driving per-edge anti-aliasing (raster's
     * all-or-nothing approximation per [experimental_DrawEdgeAAQuad]).
     *
     * This raster implementation is deliberately a correctness fallback:
     * it walks entries and replays them through [drawImageRect], folding the
     * per-entry alpha into a copied paint and applying optional preview
     * matrices / destination clip quads around the individual draw. GPU-style
     * vertex coalescing remains an optimization for future backends.
     */
    public open fun experimental_DrawEdgeAAImageSet(
        set: Array<ImageSetEntry>,
        count: Int,
        dstClips: Array<SkPoint>?,
        preViewMatrices: Array<SkMatrix>?,
        sampling: SkSamplingOptions,
        paint: SkPaint?,
        constraint: SrcRectConstraint,
    ) {
        val n = count.coerceIn(0, set.size)
        for (i in 0 until n) {
            val entry = set[i]
            if (entry.alpha <= 0f) continue
            if (entry.srcRect.isEmpty || entry.dstRect.isEmpty) continue

            val entryPaint = (paint?.copy() ?: SkPaint()).apply {
                alphaf *= entry.alpha.coerceIn(0f, 1f)
                if (entry.aaFlags == QuadAAFlags.kAll_QuadAAFlags) {
                    isAntiAlias = true
                }
            }

            save()
            try {
                if (entry.matrixIndex >= 0) {
                    val matrices = requireNotNull(preViewMatrices) {
                        "experimental_DrawEdgeAAImageSet: entry $i references matrix ${entry.matrixIndex} but preViewMatrices is null"
                    }
                    require(entry.matrixIndex < matrices.size) {
                        "experimental_DrawEdgeAAImageSet: entry $i references matrix ${entry.matrixIndex}, only ${matrices.size} provided"
                    }
                    concat(matrices[entry.matrixIndex])
                }

                if (entry.hasClip) {
                    val clips = requireNotNull(dstClips) {
                        "experimental_DrawEdgeAAImageSet: entry $i hasClip=true but dstClips is null"
                    }
                    val base = i * 4
                    require(base + 3 < clips.size) {
                        "experimental_DrawEdgeAAImageSet: entry $i needs dstClips[$base..${base + 3}], only ${clips.size} points provided"
                    }
                    val clipPath = SkPath.Polygon(
                        arrayOf(
                            clips[base + 0].fX to clips[base + 0].fY,
                            clips[base + 1].fX to clips[base + 1].fY,
                            clips[base + 2].fX to clips[base + 2].fY,
                            clips[base + 3].fX to clips[base + 3].fY,
                        ),
                        isClosed = true,
                    )
                    clipPath(clipPath, doAntiAlias = entry.aaFlags == QuadAAFlags.kAll_QuadAAFlags)
                }

                drawImageRect(entry.image, entry.srcRect, entry.dstRect, sampling, entryPaint, constraint)
            } finally {
                restore()
            }
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
     *
     * Per upstream's `SkCanvas::onDrawPoints`
     * (`src/core/SkCanvas.cpp:1947-1949`), the line modes
     * ([PointMode.kLines] and [PointMode.kPolygon]) force
     * `paint.style = kStroke_Style` before dispatch — a caller-supplied
     * kFill paint would otherwise produce a no-op fill of an open
     * polyline. [PointMode.kPoints] keeps the caller-supplied style
     * for the fill on each disk/square it stamps.
     */
    public open fun drawPoints(mode: PointMode, points: Array<org.graphiks.math.SkPoint>, paint: SkPaint) {
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
                // Upstream `SkCanvas::onDrawPoints` enforces
                // `strokePaint.setStyle(kStroke_Style)` before dispatch ;
                // a kFill paint would otherwise produce a no-op fill of
                // an open polyline. Force-stroke the local copy so the
                // [drawLine] → [drawPath] chain honours the upstream
                // contract regardless of caller-supplied paint.style.
                val strokePaint = if (paint.style == SkPaint.Style.kStroke_Style) {
                    paint
                } else {
                    paint.copy().apply { style = SkPaint.Style.kStroke_Style }
                }
                var i = 0
                while (i + 1 < points.size) {
                    val a = points[i]
                    val b = points[i + 1]
                    drawLine(a.fX, a.fY, b.fX, b.fY, strokePaint)
                    i += 2
                }
            }
            PointMode.kPolygon -> {
                // Same upstream enforcement as kLines : force kStroke so
                // a kFill paint still produces a visible polyline.
                val strokePaint = if (paint.style == SkPaint.Style.kStroke_Style) {
                    paint
                } else {
                    paint.copy().apply { style = SkPaint.Style.kStroke_Style }
                }
                for (i in 0 until points.size - 1) {
                    val a = points[i]
                    val b = points[i + 1]
                    drawLine(a.fX, a.fY, b.fX, b.fY, strokePaint)
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
     *
     * Three CTM regimes :
     *  1. **Axis-aligned** — `dst` is mapped through the CTM to an axis-aligned
     *     device rect and routed through the device's bulk `drawImageRect`
     *     (fast path).
     *  2. **Non-axis-aligned (rotated / skewed / perspective)** — R-final.7 :
     *     re-route through [drawPath] of `SkPath.Rect(dst)` filled by an
     *     image shader whose [SkShader] local-matrix maps `src → dst`. The
     *     existing path rasteriser already handles arbitrary CTMs (perspective
     *     included), and the shader pipeline samples the image at every
     *     covered device pixel via the device-to-local inverse — net effect
     *     is a projection-warp of the source rect into the destination
     *     polygon under the current CTM.
     *
     * The [paint] is honoured for everything that's not the colour /
     * shader (e.g. blend mode, alpha) — the shader assignment overrides
     * any user-supplied shader. [constraint] is currently ignored on the
     * perspective path (it only matters for the bulk-rect filter
     * over-sampling guard, which doesn't kick in for the per-pixel
     * shader path).
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
        val clipped = clipDrawImageRectSourceToImage(image, src, dst) ?: return
        val effectiveSrc = clipped.src
        val effectiveDst = clipped.dst
        // K2 — when `paint.maskFilter` is set we re-route through the
        // shader-rect path (same as upstream Skia's `USE_SHADER` branch
        // in `SkBitmapDevice::drawImageRect` when `CanApplyDstMatrixAsCTM`
        // returns false). The mask filter then operates on the boundary
        // of the dst rect via the offscreen-mask pipeline in
        // `SkBitmapDevice.drawPathWithMaskFilter` (J4 — shader + maskFilter
        // combo), producing the correct halo / inner-blur on the image
        // edge.
        //
        // We keep the bulk-rect fast path for the (overwhelmingly common)
        // axis-aligned no-maskFilter case ; it carries a fully-tuned
        // nearest / linear / cubic / aniso / mip ladder that the shader
        // path can't match for raw image draws.
        if (!s.matrix.isAxisAligned || paint?.maskFilter != null) {
            // R-final.7 — perspective / rotated path : route through drawRect
            // with an image-shader bound to the src→dst local matrix. The
            // shader maps every device pixel back through the CTM⁻¹ and the
            // localMatrix⁻¹ to its src-image sample.
            //
            // Local matrix : maps `dst` → `src` so that when the shader
            // samples at local-space point `p` (which is `dst` coords after
            // the canvas inverts the CTM), it reads from the corresponding
            // `src` pixel. SkBitmapShader composes `localMatrix⁻¹` with
            // `deviceToLocal` so we pass the src-to-dst mapping (the shader
            // inverts it internally as part of its sample pipeline).
            val srcToDst = SkMatrix.MakeRectToRect(
                effectiveSrc,
                effectiveDst,
                SkMatrix.ScaleToFit.kFill_ScaleToFit,
            )
            if (srcToDst == null) return
            // K2 — when the routing is driven by a maskFilter we tile the
            // shader with `kDecal` so that samples taken inside the
            // *blurred-mask halo* (i.e. outside the [dst] rect after the
            // path bounds are expanded by the filter margin) fall to
            // transparent black instead of clamping to the image edge.
            // That kills the radial "extruded edge" streaks the kClamp
            // path produces for opaque sprites under large mask sigmas,
            // matching the upstream reference where the blur attenuates
            // the image alpha to zero outside the dst rect.
            val maskTile = if (paint?.maskFilter != null) SkTileMode.kDecal else SkTileMode.kClamp
            val shader = image.makeShader(
                maskTile,
                maskTile,
                sampling,
                srcToDst,
            )
            val effective = (paint?.copy() ?: SkPaint()).apply {
                this.shader = shader
            }
            // Use drawPath directly to avoid drawRect's fast-path which
            // would re-enter and short-circuit on a null shader.
            drawPath(SkPath.Rect(effectiveDst), effective)
            return
        }
        val (x0, y0) = s.matrix.mapXY(effectiveDst.left, effectiveDst.top)
        val (x1, y1) = s.matrix.mapXY(effectiveDst.right, effectiveDst.bottom)
        val devDst = SkRect.MakeLTRB(minOf(x0, x1), minOf(y0, y1), maxOf(x0, x1), maxOf(y0, y1))
        bindClip(s)
        s.device.drawImageRect(image, effectiveSrc, devDst, sampling, paint, constraint, s.clip)
    }

    private data class DrawImageRectClip(val src: SkRect, val dst: SkRect)

    private fun clipDrawImageRectSourceToImage(
        image: SkImage,
        src: SkRect,
        dst: SkRect,
    ): DrawImageRectClip? {
        if (image.width <= 0 || image.height <= 0) return null
        val srcW = src.right - src.left
        val srcH = src.bottom - src.top
        val dstW = dst.right - dst.left
        val dstH = dst.bottom - dst.top
        if (srcW <= 0f || srcH <= 0f || dstW <= 0f || dstH <= 0f) return null

        val clippedLeft = maxOf(src.left, 0f)
        val clippedTop = maxOf(src.top, 0f)
        val clippedRight = minOf(src.right, image.width.toFloat())
        val clippedBottom = minOf(src.bottom, image.height.toFloat())
        if (clippedRight <= clippedLeft || clippedBottom <= clippedTop) return null
        if (clippedLeft == src.left && clippedTop == src.top &&
            clippedRight == src.right && clippedBottom == src.bottom
        ) {
            return DrawImageRectClip(src, dst)
        }

        fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

        val u0 = (clippedLeft - src.left) / srcW
        val u1 = (clippedRight - src.left) / srcW
        val v0 = (clippedTop - src.top) / srcH
        val v1 = (clippedBottom - src.top) / srcH
        return DrawImageRectClip(
            src = SkRect.MakeLTRB(clippedLeft, clippedTop, clippedRight, clippedBottom),
            dst = SkRect.MakeLTRB(
                lerp(dst.left, dst.right, u0),
                lerp(dst.top, dst.bottom, v0),
                lerp(dst.left, dst.right, u1),
                lerp(dst.top, dst.bottom, v1),
            ),
        )
    }

    // ─── Phase R2.13 — drawRegion / drawImageNine ─────────────────────────

    /**
     * Mirrors Skia's `SkCanvas::drawRegion(region, paint)`
     * (`include/core/SkCanvas.h:1427`). The region is expressed in
     * **canvas-local** coordinates (Skia's contract — same as the
     * shapes accepted by `drawRect` / `drawPath`). Implementation :
     * walk the region's rectangle decomposition via
     * [SkRegion.Iterator] and emit one [drawRect] per rect.
     *
     * The active CTM is honoured (each rect is mapped through it),
     * matching Skia's `SkDevice::drawRegion` which delegates to
     * per-rect drawing. Empty regions are a no-op.
     */
    public open fun drawRegion(region: SkRegion, paint: SkPaint) {
        if (region.isEmpty()) return
        val it = SkRegion.Iterator(region)
        while (!it.done()) {
            val r = it.rect()
            drawRect(
                SkRect.MakeLTRB(
                    r.left.toFloat(), r.top.toFloat(),
                    r.right.toFloat(), r.bottom.toFloat(),
                ),
                paint,
            )
            it.next()
        }
    }

    /**
     * Mirrors Skia's `SkCanvas::drawImageNine(image, center, dst, filter, paint)`
     * (`include/core/SkCanvas.h:1642`) — 9-patch raster.
     *
     * The [image] is divided by [center] into 9 quads :
     *  - 4 corners — drawn at their original (1:1) size into the
     *    matching corners of [dst] ;
     *  - 4 edges — stretched along exactly one axis to fill the gap
     *    between the corners ;
     *  - 1 middle — stretched along both axes to fill the inner
     *    rectangle.
     *
     * Implementation : compute the 9 source / destination sub-rects
     * and dispatch each through [drawImageRect] with the requested
     * [filterMode] (mapped to an `SkSamplingOptions`). Sub-rects with
     * zero width / height are skipped (matches Skia — if [center] is
     * flush against an edge, that side's strip just disappears).
     *
     * [center] is clamped to the image's bounds upstream (Skia's
     * `SkLatticeIter::Valid` rejects out-of-range centers). We
     * additionally guard by clamping if a misbehaving caller passes
     * an out-of-range rect, falling back to a plain `drawImageRect`.
     */
    public open fun drawImageNine(
        image: SkImage,
        center: SkIRect,
        dst: SkRect,
        filterMode: SkFilterMode,
        paint: SkPaint? = null,
    ) {
        val iw = image.width
        val ih = image.height
        // Clamp center to the image bounds (defensive — Skia upstream
        // asserts the caller already validated).
        val cl = center.left.coerceIn(0, iw)
        val ct = center.top.coerceIn(0, ih)
        val cr = center.right.coerceIn(cl, iw)
        val cb = center.bottom.coerceIn(ct, ih)
        // Degenerate center — fall back to plain drawImageRect.
        if (cl >= cr || ct >= cb) {
            drawImageRect(
                image,
                SkRect.MakeWH(iw.toFloat(), ih.toFloat()),
                dst,
                SkSamplingOptions(filterMode),
                paint,
                SrcRectConstraint.kStrict,
            )
            return
        }

        // Corner pixel widths from the source image.
        val leftSrcW = cl
        val rightSrcW = iw - cr
        val topSrcH = ct
        val bottomSrcH = ih - cb

        // Destination geometry. The corner strips keep their source
        // dimensions ; the middle stretches to fill what's left. Skia
        // proportionally shrinks corners that wouldn't otherwise fit
        // (combined corner > dst); we mirror that with a uniform scale.
        val dstW = dst.right - dst.left
        val dstH = dst.bottom - dst.top
        val horizCornerSrc = (leftSrcW + rightSrcW).toFloat()
        val vertCornerSrc = (topSrcH + bottomSrcH).toFloat()
        val sx = if (horizCornerSrc > dstW) dstW / horizCornerSrc else 1f
        val sy = if (vertCornerSrc > dstH) dstH / vertCornerSrc else 1f
        val leftDstW = leftSrcW * sx
        val rightDstW = rightSrcW * sx
        val topDstH = topSrcH * sy
        val bottomDstH = bottomSrcH * sy

        // Source x bands : [0, cl), [cl, cr), [cr, iw)
        // Source y bands : [0, ct), [ct, cb), [cb, ih)
        val sxs = floatArrayOf(0f, cl.toFloat(), cr.toFloat(), iw.toFloat())
        val sys = floatArrayOf(0f, ct.toFloat(), cb.toFloat(), ih.toFloat())
        // Destination x bands : align corners 1:1 (possibly uniformly
        // scaled down), the middle fills the gap.
        val dxs = floatArrayOf(
            dst.left,
            dst.left + leftDstW,
            dst.right - rightDstW,
            dst.right,
        )
        val dys = floatArrayOf(
            dst.top,
            dst.top + topDstH,
            dst.bottom - bottomDstH,
            dst.bottom,
        )

        val sampling = SkSamplingOptions(filterMode)
        for (row in 0..2) {
            for (col in 0..2) {
                val sLeft = sxs[col];   val sTop = sys[row]
                val sRight = sxs[col + 1]; val sBottom = sys[row + 1]
                val dLeft = dxs[col];   val dTop = dys[row]
                val dRight = dxs[col + 1]; val dBottom = dys[row + 1]
                if (sRight <= sLeft || sBottom <= sTop) continue
                if (dRight <= dLeft || dBottom <= dTop) continue
                drawImageRect(
                    image,
                    SkRect.MakeLTRB(sLeft, sTop, sRight, sBottom),
                    SkRect.MakeLTRB(dLeft, dTop, dRight, dBottom),
                    sampling,
                    paint,
                    SrcRectConstraint.kStrict,
                )
            }
        }
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
            val srcToDst = org.graphiks.math.SkMatrix(
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
            val raster = s.device.requireBitmap("drawVertices-textured")
            for (t in 0 until tCount) {
                val tri = vertices.triangleAt(t)
                val a = vertices.positions[tri[0]]
                val b = vertices.positions[tri[1]]
                val c = vertices.positions[tri[2]]
                val uvA = texCoords[tri[0]]
                val uvB = texCoords[tri[1]]
                val uvC = texCoords[tri[2]]
                raster.drawTexturedTriangle(
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
        val raster = s.device.requireBitmap("drawVertices-colored")
        for (t in 0 until tCount) {
            val tri = vertices.triangleAt(t)
            val a = vertices.positions[tri[0]]
            val b = vertices.positions[tri[1]]
            val c = vertices.positions[tri[2]]
            raster.drawColoredTriangle(
                a.fX, a.fY, colors[tri[0]],
                b.fX, b.fY, colors[tri[1]],
                c.fX, c.fY, colors[tri[2]],
                blendMode,
                s.matrix, s.clip, paint,
            )
        }
    }

    /**
     * Experimental CPU-only skeleton for Skia's `SkCanvas::drawMesh`.
     *
     * Supported subset: [mesh] must be valid, CPU-backed, and its
     * [SkMeshSpecification] must contain a `float2` attribute named
     * `position` and may contain one `ubyte4_unorm` attribute named `color`
     * encoded as RGBA bytes. The mesh is converted to [SkVertices] and drawn
     * with the supplied [paint]. CPU fragment-program execution is intentionally
     * bounded to deterministic uniforms-only colour outputs:
     * - `return uniformColor;`
     * - `return uniformColor * varyingColor;`
     * Child shaders remain out of scope.
     */
    public open fun drawMesh(
        mesh: SkMesh,
        @Suppress("UNUSED_PARAMETER") blender: SkBlender? = null,
        paint: SkPaint = SkPaint(),
    ) {
        if (!mesh.isValid()) return
        val s = top
        s.device.requireBitmap("drawMesh")

        val spec = mesh.spec() ?: return
        val position = spec.positionAttribute ?: return
        val color = spec.colorAttribute
        val vb = mesh.vertexBuffer() ?: return
        val vertexBytes = vb.bytesUnsafe()
        val positions = Array(mesh.vertexCount()) { i ->
            val base = mesh.vertexOffset() + i * spec.stride() + position.offset
            SkPoint(readFloatLE(vertexBytes, base), readFloatLE(vertexBytes, base + 4))
        }
        val meshToSrgb = SkColorSpaceXformSteps(
            src = spec.colorSpace ?: SkColorSpace.makeSRGB(),
            srcAT = spec.alphaType.toCoreAlphaType(),
            dst = SkColorSpace.makeSRGB(),
            dstAT = SkAlphaType.kUnpremul,
        )
        val colors = color?.let {
            IntArray(mesh.vertexCount()) { i ->
                val base = mesh.vertexOffset() + i * spec.stride() + it.offset
                readMeshColor(vertexBytes, base, it.type, meshToSrgb)
            }
        }
        val fragmentMode = detectCpuMeshFragmentMode(spec.fragmentProgram, spec.uniforms())
        val uniformColor =
            if (fragmentMode == CpuMeshFragmentMode.DEFAULT) null
            else decodeUniformColor(spec, mesh.uniforms())
        val effectiveColors = if (fragmentMode == CpuMeshFragmentMode.UNIFORM_SOLID) null else colors
        val vertexMode = when (mesh.mode()) {
            SkMesh.Mode.kTriangles -> SkVertices.VertexMode.kTriangles
            SkMesh.Mode.kTriangleStrip -> SkVertices.VertexMode.kTriangleStrip
        }
        val indices = mesh.indexBuffer()?.let { ib ->
            val bytes = ib.bytesUnsafe()
            ShortArray(mesh.indexCount()) { i ->
                readU16LE(bytes, mesh.indexOffset() + i * 2).toShort()
            }
        }
        val vertices = SkVertices.MakeCopy(
            mode = vertexMode,
            positions = positions,
            colors = effectiveColors,
            indices = indices,
        )
        val meshPaint = if (blender == null) paint else paint.copy().also { it.blender = blender }
        if (uniformColor != null) {
            meshPaint.shader = null
            meshPaint.color = uniformColor
        }
        drawVertices(vertices, SkBlendMode.kSrcOver, meshPaint)
    }

    public open fun drawMesh(mesh: SkMesh, paint: SkPaint) {
        drawMesh(mesh, null, paint)
    }

    private fun readFloatLE(bytes: ByteArray, offset: Int): Float =
        Float.fromBits(
            (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24),
        )

    private fun readU16LE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun readMeshColor(
        bytes: ByteArray,
        offset: Int,
        type: SkMeshSpecification.Attribute.Type,
        meshToSrgb: SkColorSpaceXformSteps,
    ): Int {
        val rgba = when (type) {
            SkMeshSpecification.Attribute.Type.kUByte4_unorm -> floatArrayOf(
                (bytes[offset].toInt() and 0xFF) / 255f,
                (bytes[offset + 1].toInt() and 0xFF) / 255f,
                (bytes[offset + 2].toInt() and 0xFF) / 255f,
                (bytes[offset + 3].toInt() and 0xFF) / 255f,
            )
            SkMeshSpecification.Attribute.Type.kFloat4 -> floatArrayOf(
                readFloatLE(bytes, offset + 0),
                readFloatLE(bytes, offset + 4),
                readFloatLE(bytes, offset + 8),
                readFloatLE(bytes, offset + 12),
            )
            else -> return 0xFFFFFFFF.toInt()
        }
        meshToSrgb.apply(rgba)
        return packUnpremulColor(rgba)
    }

    private enum class CpuMeshFragmentMode {
        DEFAULT,
        UNIFORM_SOLID,
        UNIFORM_TIMES_COLOR,
    }

    private fun detectCpuMeshFragmentMode(
        fragmentProgram: String,
        uniforms: List<SkMeshSpecification.Uniform>,
    ): CpuMeshFragmentMode {
        val colorUniform = uniforms.firstOrNull { it.type == SkMeshSpecification.Uniform.Type.kFloat4 || it.type == SkMeshSpecification.Uniform.Type.kHalf4 }
            ?: return CpuMeshFragmentMode.DEFAULT
        val uniformToken = colorUniform.name.lowercase()
        val returnExpr = extractReturnExpression(fragmentProgram) ?: return CpuMeshFragmentMode.DEFAULT
        return when {
            returnExpr == uniformToken -> CpuMeshFragmentMode.UNIFORM_SOLID
            returnExpr == "meshcolor*$uniformToken" || returnExpr == "$uniformToken*meshcolor" ->
                CpuMeshFragmentMode.UNIFORM_TIMES_COLOR
            Regex("""[a-z_][a-z0-9_]*=$uniformToken;""").containsMatchIn(
                fragmentProgram.replace(Regex("""\s+"""), "").lowercase(),
            ) -> CpuMeshFragmentMode.UNIFORM_SOLID
            else -> CpuMeshFragmentMode.DEFAULT
        }
    }

    private fun extractReturnExpression(fragmentProgram: String): String? {
        val returnMatch = Regex("""return\s+([^;]+);""", RegexOption.IGNORE_CASE).find(fragmentProgram) ?: return null
        return returnMatch.groupValues[1]
            .replace("(", "")
            .replace(")", "")
            .replace(Regex("""\s+"""), "")
            .lowercase()
    }

    private fun decodeUniformColor(
        spec: SkMeshSpecification,
        uniforms: org.skia.foundation.SkData?,
    ): Int? {
        if (spec.uniformSize() == 0 || uniforms == null) return null
        val colorUniform = spec.uniforms().firstOrNull {
            it.type == SkMeshSpecification.Uniform.Type.kFloat4 || it.type == SkMeshSpecification.Uniform.Type.kHalf4
        } ?: return null
        val bytes = uniforms.toByteArray()
        if (bytes.size < colorUniform.offset + 16) return null
        val rgba = floatArrayOf(
            readFloatLE(bytes, colorUniform.offset + 0),
            readFloatLE(bytes, colorUniform.offset + 4),
            readFloatLE(bytes, colorUniform.offset + 8),
            readFloatLE(bytes, colorUniform.offset + 12),
        )
        val meshCS = spec.colorSpace ?: SkColorSpace.makeSRGB()
        val meshAT = spec.alphaType.toCoreAlphaType()
        if (colorUniform.colorManaged) {
            SkColorSpaceXformSteps(
                src = SkColorSpace.makeSRGB(),
                srcAT = SkAlphaType.kUnpremul,
                dst = meshCS,
                dstAT = meshAT,
            ).apply(rgba)
        }
        SkColorSpaceXformSteps(
            src = meshCS,
            srcAT = meshAT,
            dst = SkColorSpace.makeSRGB(),
            dstAT = SkAlphaType.kUnpremul,
        ).apply(rgba)
        return packUnpremulColor(rgba)
    }

    private fun org.skia.foundation.SkAlphaType.toCoreAlphaType(): SkAlphaType =
        when (this) {
            org.skia.foundation.SkAlphaType.kOpaque -> SkAlphaType.kOpaque
            org.skia.foundation.SkAlphaType.kPremul -> SkAlphaType.kPremul
            org.skia.foundation.SkAlphaType.kUnpremul -> SkAlphaType.kUnpremul
            org.skia.foundation.SkAlphaType.kUnknown -> SkAlphaType.kUnknown
        }

    private fun packUnpremulColor(rgba: FloatArray): Int {
        val r = clamp01(rgba[0])
        val g = clamp01(rgba[1])
        val b = clamp01(rgba[2])
        val a = clamp01(rgba[3])
        return ((a * 255f + 0.5f).toInt() shl 24) or
            ((r * 255f + 0.5f).toInt() shl 16) or
            ((g * 255f + 0.5f).toInt() shl 8) or
            ((b * 255f + 0.5f).toInt())
    }

    private fun clamp01(v: Float): Float =
        when {
            v < 0f -> 0f
            v > 1f -> 1f
            else -> v
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
        cubics: Array<org.graphiks.math.SkPoint>,
        colors: IntArray?,
        texCoords: Array<org.graphiks.math.SkPoint>?,
        blendMode: SkBlendMode,
        paint: SkPaint,
    ) {
        require(cubics.size == 12) { "drawPatch : cubics must have 12 points, got ${cubics.size}" }
        if (colors != null) require(colors.size == 4) { "drawPatch : colors must have 4 entries, got ${colors.size}" }
        if (texCoords != null) require(texCoords.size == 4) { "drawPatch : texCoords must have 4 entries, got ${texCoords.size}" }

        val n = PATCH_TESS_N
        val verts = ArrayList<org.graphiks.math.SkPoint>((n + 1) * (n + 1))
        val tCoords = if (texCoords != null) ArrayList<org.graphiks.math.SkPoint>((n + 1) * (n + 1)) else null
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
    private fun coonsSurfaceAt(cubics: Array<org.graphiks.math.SkPoint>, s: SkScalar, t: SkScalar): org.graphiks.math.SkPoint {
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
        return org.graphiks.math.SkPoint(lcX + ldX - bX, lcY + ldY - bY)
    }

    /**
     * Evaluate the cubic Bézier `[cubics[i0], cubics[i0+1],
     * cubics[i0+2], cubics[i0+3]]` at parameter `t`. The standard
     * Bernstein polynomial form keeps the algorithm trivial.
     */
    private fun cubicAt(cubics: Array<org.graphiks.math.SkPoint>, i0: Int, t: SkScalar): org.graphiks.math.SkPoint {
        val u = 1f - t
        val b0 = u * u * u
        val b1 = 3f * u * u * t
        val b2 = 3f * u * t * t
        val b3 = t * t * t
        val p0 = cubics[i0]
        val p1 = cubics[i0 + 1]
        val p2 = cubics[i0 + 2]
        val p3 = cubics[i0 + 3]
        return org.graphiks.math.SkPoint(
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
    private fun cubicAtLeftEdge(cubics: Array<org.graphiks.math.SkPoint>, t: SkScalar): org.graphiks.math.SkPoint {
        val u = 1f - t
        val b0 = u * u * u
        val b1 = 3f * u * u * t
        val b2 = 3f * u * t * t
        val b3 = t * t * t
        val p0 = cubics[9]
        val p1 = cubics[10]
        val p2 = cubics[11]
        val p3 = cubics[0]
        return org.graphiks.math.SkPoint(
            b0 * p0.fX + b1 * p1.fX + b2 * p2.fX + b3 * p3.fX,
            b0 * p0.fY + b1 * p1.fY + b2 * p2.fY + b3 * p3.fY,
        )
    }

    private fun bilerpPoint(corners: Array<org.graphiks.math.SkPoint>, s: SkScalar, t: SkScalar): org.graphiks.math.SkPoint {
        // corners : 0 = top-left, 1 = top-right, 2 = bottom-right, 3 = bottom-left.
        val p00 = corners[0]; val p10 = corners[1]; val p11 = corners[2]; val p01 = corners[3]
        val w00 = (1f - s) * (1f - t)
        val w10 = s * (1f - t)
        val w01 = (1f - s) * t
        val w11 = s * t
        return org.graphiks.math.SkPoint(
            w00 * p00.fX + w10 * p10.fX + w01 * p01.fX + w11 * p11.fX,
            w00 * p00.fY + w10 * p10.fY + w01 * p01.fY + w11 * p11.fY,
        )
    }

    private fun bilerpColor(corners: IntArray, s: SkScalar, t: SkScalar): SkColor {
        val w00 = (1f - s) * (1f - t)
        val w10 = s * (1f - t)
        val w01 = (1f - s) * t
        val w11 = s * t
        val a = (org.graphiks.math.SkColorGetA(corners[0]) * w00 +
            org.graphiks.math.SkColorGetA(corners[1]) * w10 +
            org.graphiks.math.SkColorGetA(corners[2]) * w11 +
            org.graphiks.math.SkColorGetA(corners[3]) * w01).toInt().coerceIn(0, 255)
        val r = (org.graphiks.math.SkColorGetR(corners[0]) * w00 +
            org.graphiks.math.SkColorGetR(corners[1]) * w10 +
            org.graphiks.math.SkColorGetR(corners[2]) * w11 +
            org.graphiks.math.SkColorGetR(corners[3]) * w01).toInt().coerceIn(0, 255)
        val g = (org.graphiks.math.SkColorGetG(corners[0]) * w00 +
            org.graphiks.math.SkColorGetG(corners[1]) * w10 +
            org.graphiks.math.SkColorGetG(corners[2]) * w11 +
            org.graphiks.math.SkColorGetG(corners[3]) * w01).toInt().coerceIn(0, 255)
        val b = (org.graphiks.math.SkColorGetB(corners[0]) * w00 +
            org.graphiks.math.SkColorGetB(corners[1]) * w10 +
            org.graphiks.math.SkColorGetB(corners[2]) * w11 +
            org.graphiks.math.SkColorGetB(corners[3]) * w01).toInt().coerceIn(0, 255)
        return org.graphiks.math.SkColorSetARGB(a, r, g, b)
    }

    private companion object {
        /**
         * Patch tessellation density (one side of the grid). 8 → 64
         * quads → 128 triangles per patch ; matches the default Skia
         * uses for raster-side patches and keeps the budget under
         * SkVertices's 16-bit index limit.
         */
        private const val PATCH_TESS_N: Int = 8
        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

        init {
            // Eagerly load :cpu-raster's SkShadowUtils so its `init` block
            // can register the [drawShadowDispatcher] SPI used by
            // [SkCanvas.drawShadow]. If :cpu-raster is not on the
            // classpath the dispatcher stays null and drawShadow throws
            // a helpful diagnostic. See SkCanvasShadowSpi.kt.
            try {
                Class.forName("org.skia.utils.SkShadowUtils")
            } catch (_: ClassNotFoundException) {
                // :cpu-raster absent — drawShadow callers will get an
                // explicit error pointing at the missing dependency.
            }
        }
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
        if (mode == SkBlendMode.kSrc &&
            s.clip == s.device.deviceClipBounds() &&
            s.device is SkBitmapDevice
        ) {
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
     *  1. dispatch to `font.typeface.makeTextPath(...)` — portable
     *     OpenType typefaces build an [SkPath] from parsed glyph outlines
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
     *
     * Policy boundary for #976:
     *  - `drawString` remains a simple-text API routed through
     *    `SkFont.makeTextPath` (cmap + kern/limited GPOS in the
     *    current portable OpenType path);
     *  - complex shaping (`GSUB`, bidi reordering, script itemization,
     *    marks/cursive attachment, multi-font fallback splitting) must
     *    be routed through explicit `SkShaper`/text-layout entry
     *    points instead of being implicitly folded into `drawString`.
     */
    public open fun drawString(
        str: String,
        x: SkScalar,
        y: SkScalar,
        font: SkFont,
        paint: SkPaint,
    ) {
        if (str.isEmpty()) return
        val typeface = font.typeface
        if (paint.shader == null && typeface is OpenTypeTypeface) {
            val colorPaths = typeface.makeColorTextPaths(
                str,
                x,
                y,
                font.size,
                font.scaleX,
                font.skewX,
                font.isSubpixel,
            )
            if (colorPaths != null) {
                for (colorPath in colorPaths) {
                    drawOpenTypeColorPath(colorPath, paint)
                }
                return
            }
            val bitmapGlyphs = typeface.makeBitmapTextGlyphs(str, x, y, font.size, font.scaleX)
            if (bitmapGlyphs.isNotEmpty() && drawOpenTypeBitmapGlyphs(bitmapGlyphs, font.size, paint)) {
                return
            }
        }
        val path = font.makeTextPath(str, x, y) ?: return
        // Glyph fills are AA whenever the font asks for it. Skia's
        // `paint.isAntiAlias` is independent — we honour it by ANDing
        // with the font edging: if either says "alias", we go alias.
        // For T3 we keep paint.isAntiAlias as the source of truth and
        // let drawPath decide; future slices may refine.
        drawPath(path, paint)
    }

    private fun drawOpenTypeBitmapGlyphs(
        bitmapGlyphs: List<org.skia.foundation.opentype.OpenTypePositionedBitmapGlyph>,
        fontSize: SkScalar,
        paint: SkPaint,
    ): Boolean {
        val imageCache = HashMap<Int, SkImage>()
        var drewAny = false
        for (positioned in bitmapGlyphs) {
            val glyph = positioned.glyph
            val image = imageCache[glyph.glyphId] ?: decodePngGlyph(glyph.bytes)?.also {
                imageCache[glyph.glyphId] = it
            } ?: continue
            val ppem = glyph.ppemY.takeIf { it > 0 } ?: 1
            val scale = fontSize / ppem.toFloat()
            val left = positioned.x + glyph.originOffsetX * scale
            val top = positioned.y - image.height * scale + glyph.originOffsetY * scale
            val dst = SkRect.MakeXYWH(left, top, image.width * scale, image.height * scale)
            val src = SkRect.MakeWH(image.width.toFloat(), image.height.toFloat())
            drawImageRect(image, src, dst, SkSamplingOptions.Default, paint, SrcRectConstraint.kFast)
            drewAny = true
        }
        return drewAny
    }

    private fun decodePngGlyph(bytes: ByteArray): SkImage? {
        if (bytes.size < 8 || !bytes.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)) return null
        var offset = 8
        var width = 0
        var height = 0
        var bitDepth = 0
        var colorType = 0
        var interlace = 0
        val idat = ArrayList<Byte>()
        while (offset + 8 <= bytes.size) {
            val length = readU32(bytes, offset) ?: return null
            offset += 4
            val type = String(bytes, offset, 4, Charsets.US_ASCII)
            offset += 4
            if (offset + length + 4 > bytes.size) return null
            val chunkData = bytes.copyOfRange(offset, offset + length)
            offset += length + 4 // Skip CRC
            when (type) {
                "IHDR" -> {
                    if (length != 13) return null
                    width = readU32(chunkData, 0) ?: return null
                    height = readU32(chunkData, 4) ?: return null
                    bitDepth = chunkData[8].toInt() and 0xFF
                    colorType = chunkData[9].toInt() and 0xFF
                    interlace = chunkData[12].toInt() and 0xFF
                }
                "IDAT" -> idat.addAll(chunkData.toList())
                "IEND" -> break
            }
        }
        if (width <= 0 || height <= 0 || bitDepth != 8 || colorType != 6 || interlace != 0) return null
        val inflater = Inflater()
        inflater.setInput(idat.toByteArray())
        val rowBytes = width * 4
        val expected = height * (rowBytes + 1)
        val inflated = ByteArray(expected)
        val inflatedSize = try {
            inflater.inflate(inflated)
        } catch (_: Exception) {
            return null
        } finally {
            inflater.end()
        }
        if (inflatedSize != expected) return null
        val pixels = IntArray(width * height)
        val prev = ByteArray(rowBytes)
        val cur = ByteArray(rowBytes)
        var src = 0
        for (y in 0 until height) {
            val filter = inflated[src].toInt() and 0xFF
            src += 1
            for (i in 0 until rowBytes) cur[i] = inflated[src + i]
            src += rowBytes
            unfilterPngRow(cur, prev, filter, 4)
            for (x in 0 until width) {
                val i = x * 4
                val r = cur[i].toInt() and 0xFF
                val g = cur[i + 1].toInt() and 0xFF
                val b = cur[i + 2].toInt() and 0xFF
                val a = cur[i + 3].toInt() and 0xFF
                pixels[y * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
            System.arraycopy(cur, 0, prev, 0, rowBytes)
        }
        return SkImage(width, height, pixels)
    }

    private fun unfilterPngRow(cur: ByteArray, prev: ByteArray, filter: Int, bpp: Int) {
        when (filter) {
            0 -> return
            1 -> for (i in cur.indices) {
                val left = if (i >= bpp) cur[i - bpp].toInt() and 0xFF else 0
                cur[i] = ((cur[i].toInt() and 0xFF) + left).toByte()
            }
            2 -> for (i in cur.indices) {
                val up = prev[i].toInt() and 0xFF
                cur[i] = ((cur[i].toInt() and 0xFF) + up).toByte()
            }
            3 -> for (i in cur.indices) {
                val left = if (i >= bpp) cur[i - bpp].toInt() and 0xFF else 0
                val up = prev[i].toInt() and 0xFF
                cur[i] = ((cur[i].toInt() and 0xFF) + ((left + up) / 2)).toByte()
            }
            4 -> for (i in cur.indices) {
                val a = if (i >= bpp) cur[i - bpp].toInt() and 0xFF else 0
                val b = prev[i].toInt() and 0xFF
                val c = if (i >= bpp) prev[i - bpp].toInt() and 0xFF else 0
                cur[i] = ((cur[i].toInt() and 0xFF) + paeth(a, b, c)).toByte()
            }
            else -> return
        }
    }

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = kotlin.math.abs(p - a)
        val pb = kotlin.math.abs(p - b)
        val pc = kotlin.math.abs(p - c)
        return when {
            pa <= pb && pa <= pc -> a
            pb <= pc -> b
            else -> c
        }
    }

    private fun readU32(bytes: ByteArray, offset: Int): Int? {
        if (offset + 4 > bytes.size) return null
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun drawOpenTypeColorPath(
        colorPath: org.skia.foundation.opentype.OpenTypeColorPath,
        paint: SkPaint,
        internalBlendMode: org.skia.foundation.SkBlendMode? = null,
    ) {
        if (colorPath.children.isNotEmpty()) {
            val saveCount = getSaveCount()
            val layerPaint = paint.copy().also {
                it.shader = null
                it.colorFilter = null
                it.alphaf *= colorPath.alpha
                val groupBlendMode = colorPath.blendMode ?: internalBlendMode
                if (groupBlendMode != null) it.blendMode = groupBlendMode
            }
            saveLayer(null, layerPaint)
            for (child in colorPath.children) {
                drawOpenTypeColorPath(child, paint, org.skia.foundation.SkBlendMode.kSrcOver)
            }
            restoreToCount(saveCount)
            return
        }
        val layerPaint = paint.copy().also {
            if (colorPath.shader != null) {
                it.shader = colorPath.shader
                it.alphaf *= colorPath.alpha
            } else if (colorPath.color != null) {
                val color = colorPath.color
                it.color = color
                it.alphaf *= colorPath.alpha
            } else {
                it.alphaf *= colorPath.alpha
            }
            val pathBlendMode = colorPath.blendMode ?: internalBlendMode
            if (pathBlendMode != null) it.blendMode = pathBlendMode
        }
        if (colorPath.clipPaths.isEmpty()) {
            drawPath(colorPath.path, layerPaint)
        } else {
            val saveCount = getSaveCount()
            save()
            for (clip in colorPath.clipPaths) {
                clipPath(clip, doAntiAlias = paint.isAntiAlias)
            }
            drawPath(colorPath.path, layerPaint)
            restoreToCount(saveCount)
        }
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
                is org.skia.foundation.SkTextBlob.Run.RSXformPositions -> {
                    // Each glyph carries its own rotation + uniform-scale +
                    // translate in `xforms[i]`. The 2×2 affine maps the
                    // glyph's origin-relative outline into blob-local
                    // space :
                    //   blob.x = sCos·gx − sSin·gy + tx
                    //   blob.y = sSin·gx + sCos·gy + ty
                    // The blob-level `(x, y)` then translates the whole
                    // run. Sub-pixel snap is intentionally skipped — the
                    // RSX path is for rotated / scaled glyphs where
                    // quarter-phase snap would land off-axis pixels.
                    for (i in run.glyphIds.indices) {
                        val gid = run.glyphIds[i]
                        val path = run.font.getPath(gid) ?: continue
                        val xf = run.xforms[i]
                        // `m = T(x, y) · RSX(xf)` — pre-concat into the CTM
                        // so each glyph's outline ends up at the right
                        // rotated / scaled / translated location.
                        val m = org.graphiks.math.SkMatrix(
                            sx = xf.fSCos, kx = -xf.fSSin, tx = xf.fTx + x,
                            ky = xf.fSSin, sy = xf.fSCos, ty = xf.fTy + y,
                        )
                        save()
                        concat(m)
                        drawPath(path, paint)
                        restore()
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
        saveLayer(SaveLayerRec(bounds = bounds, paint = paint, backdrop = null, flags = 0))

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
    public open fun saveLayer(rec: SaveLayerRec): Int = saveLayer(rec, filters = null)

    private fun saveLayerForImageFilterSourceCapture(bounds: SkRect, paint: SkPaint): Int =
        saveLayer(
            SaveLayerRec(bounds = bounds, paint = paint, backdrop = null, flags = 0),
            filters = null,
            captureClipToCurrentClip = false,
        )

    private fun saveLayer(
        rec: SaveLayerRec,
        filters: List<SkImageFilter?>?,
        captureClipToCurrentClip: Boolean = true,
    ): Int {
        val bounds = rec.bounds
        val paint = rec.paint
        val backdrop = rec.backdrop
        val s = top
        val captureClip = if (captureClipToCurrentClip) s.clip else s.device.deviceClipBounds()
        val layerBounds: SkIRect = if (bounds == null) {
            captureClip
        } else {
            val devBounds = s.matrix.mapRect(bounds)
            SkIRect.MakeLTRB(
                maxOf(captureClip.left, kFloor(devBounds.left.toDouble()).toInt()),
                maxOf(captureClip.top, kFloor(devBounds.top.toDouble()).toInt()),
                minOf(captureClip.right, kCeil(devBounds.right.toDouble()).toInt()),
                minOf(captureClip.bottom, kCeil(devBounds.bottom.toDouble()).toInt()),
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

        // Phase G-saveLayer — delegate layer-device construction to the
        // backend so GPU canvases get a GPU-backed layer (raster stays
        // on `SkBitmapDevice`, GPU returns a child `SkWebGpuDevice`).
        // The backend is responsible for matching colour profile /
        // precision so the parent ↔ layer composite at `restore()`
        // doesn't introduce an extra colour-space conversion.
        val requestedLayerColorType =
            if ((rec.flags and F16_COLOR_TYPE_SAVE_LAYER_FLAG) != 0) SkColorType.kRGBA_F16Norm else null
        val layerDevice = s.device.makeLayerDevice(w, h, requestedLayerColorType)
        val originX = layerBounds.left
        val originY = layerBounds.top

        // Phase G6 — backdrop : seed the new layer with the parent's
        // pre-filtered pixels. The backdrop reads the parent device's
        // pixels in the device-space layer bbox, runs them through the
        // image filter, and pastes the (possibly displaced + resized)
        // result back into the layer at the correct origin.
        //
        // Backdrop seeding splits along the device backend :
        //   - Raster parents (SkBitmapDevice → SkBitmapDevice) : walk
        //     parent pixels via [SkBitmap.getPixel], run them through
        //     [backdrop], paste into the layer bitmap. Full filter
        //     application (Blur / ColorFilter / Offset / ...).
        //   - Non-raster parents (e.g. GPU device → GPU device) :
        //     delegate to [SkDevice.seedBackdropFrom], which the
        //     backend implements with whatever native primitive it
        //     has available. The GPU backend ships **copy-only**
        //     (Phase G-saveLayer-backdrop, #591) plus the J5
        //     filter-aware extension : the `backdrop` filter is
        //     forwarded so backends can apply it on-GPU. The WebGPU
        //     device honours `SkImageFilters.Blur(input = null)` and
        //     degrades the rest to copy-only.
        //
        // If neither branch matches (mixed backend / unknown device),
        // the layer is left transparent silently -- matches the
        // pre-Phase-G-saveLayer-backdrop behaviour on GPU.
        if (backdrop != null) {
            val parentBitmap = s.device as? SkBitmapDevice
            val layerBitmapDevice = layerDevice as? SkBitmapDevice
            if (parentBitmap != null && layerBitmapDevice != null) {
                seedLayerFromBackdrop(
                    parentBitmap, layerBitmapDevice,
                    originX, originY, w, h, backdrop, s.matrix,
                    scaleFactor = rec.scaleFactor,
                )
            } else {
                // Non-raster path : delegate to the device, which
                // either applies the filter natively (J5) or falls
                // back to copy-only / no-op. Returns `false` silently
                // when it can't seed -- we accept that as "layer
                // starts transparent". The filter-application
                // contract is per-backend (see [SkDevice
                // .seedBackdropFrom] kdoc).
                layerDevice.seedBackdropFrom(
                    s.device, originX, originY, w, h, backdrop,
                )
            }
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
                maxOf(0, captureClip.left - originX),
                maxOf(0, captureClip.top - originY),
                minOf(w, captureClip.right - originX),
                minOf(h, captureClip.bottom - originY),
            ),
            device = layerDevice,
            layer = Layer(s.device, originX, originY, paint, filters),
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
     * Mirrors Skia's `SkCanvas::saveLayer(bounds, paint, flags)`.
     */
    public open fun saveLayer(bounds: SkRect?, paint: SkPaint?, flags: SaveLayerFlags): Int =
        saveLayer(SaveLayerRec(bounds = bounds, paint = paint, backdrop = null, flags = flags))

    /**
     * Mirrors Skia's `SkCanvas::saveLayerAlphaf(const SkRect* bounds, float alpha)`.
     * Convenience wrapper that creates a layer paint with the given [alpha]
     * (a float in `[0, 1]`) and delegates to [saveLayer]. Matches upstream:
     * ```cpp
     * int SkCanvas::saveLayerAlphaf(const SkRect* bounds, float alpha) {
     *     SkPaint tmpPaint;
     *     tmpPaint.setAlphaf(alpha);
     *     return this->saveLayer(bounds, &tmpPaint);
     * }
     * ```
     */
    public open fun saveLayerAlphaf(bounds: SkRect?, alpha: Float): Int {
        val p = SkPaint().also { it.alphaf = alpha }
        return saveLayer(bounds, p)
    }

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

    // ─── R-suivi.50 — drawShadow / drawSlug / drawImageLattice / drawPicture ─

    /**
     * Mirrors Skia's `SkCanvas::private_draw_shadow_rec` /
     * `SkShadowUtils::DrawShadow` entry point — render the two-layer
     * (ambient + spot) shadow cast by [path] under the elevation
     * plane [zPlaneParams], lit by the source at [lightPos] with
     * disc radius [lightRadius]. The default delegates to the
     * functional [org.skia.utils.SkShadowUtils.DrawShadow]
     * implementation ; subclasses ([org.skia.utils.SkNWayCanvas],
     * [org.skia.utils.SkNoDrawCanvas], recording / SVG sinks)
     * override for forwarding, no-op or capture semantics.
     *
     * See [org.skia.utils.SkShadowUtils.DrawShadow] for the meaning
     * of [zPlaneParams] / [lightPos] / [lightRadius] /
     * [ambientColor] / [spotColor] / [flags].
     */
    public open fun drawShadow(
        path: SkPath,
        zPlaneParams: org.graphiks.math.SkPoint3,
        lightPos: org.graphiks.math.SkPoint3,
        lightRadius: SkScalar,
        ambientColor: SkColor,
        spotColor: SkColor,
        flags: Int = 0,
    ) {
        val dispatch = drawShadowDispatcher
            ?: error(
                "SkCanvas.drawShadow has no default implementation — " +
                "add :cpu-raster to the classpath (registers " +
                "SkShadowUtils.DrawShadow as the default at load-time), " +
                "or override drawShadow in your subclass."
            )
        dispatch(this, path, zPlaneParams, lightPos, lightRadius, ambientColor, spotColor, flags)
    }

    /**
     * Mirrors Skia's
     * [`SkCanvas::drawSlug`](https://github.com/google/skia/blob/main/include/core/SkCanvas.h)
     * — replay a pre-compiled glyph slug into this canvas at
     * [origin]. The default impl delegates to
     * [org.skia.foundation.SkTextSlug.replay], which re-issues the
     * captured `drawTextBlob`. Subclasses ([org.skia.utils.SkNWayCanvas],
     * [org.skia.utils.SkNoDrawCanvas], recording sinks) override
     * for forwarding, no-op or capture semantics.
     */
    public open fun drawSlug(
        slug: org.skia.foundation.SkTextSlug,
        origin: SkPoint = SkPoint(0f, 0f),
    ) {
        slug.replay(this, origin)
    }

    /**
     * Mirrors Skia's
     * [`SkCanvas::drawImageLattice`](https://github.com/google/skia/blob/main/include/core/SkCanvas.h)
     * — render [image] partitioned by [lattice] to fit inside [dst].
     *
     * **S7-C N × M tessellation** : reads `lattice.xDivs` /
     * `lattice.yDivs` to slice the source image into
     * `(N+1) × (M+1)` rectangles. Slices whose row + column indices are
     * both **even** are *fixed* (corner-like — they keep their source
     * pixel size). Every other slice **stretches** to fill the
     * remaining destination space — matches upstream
     * `src/core/SkCanvas.cpp::onDrawImageLattice` + `SkLatticeIter`.
     *
     * The total fixed width along x = sum of source widths of
     * fixed-x slices. The remaining `dst.width - totalFixedX` is
     * distributed between the stretchable x-slices, each receiving
     * `flexW × (slice.srcWidth / totalFlexX_src)`. Same calculation
     * along y. When `dst` is too small to fit the corner sum the
     * remainder collapses to zero (slices degenerate to zero-width)
     * — matches upstream's "shrink corners" behaviour.
     *
     * Per-slice [SkLattice.RectType] is honoured :
     *
     *  - `kDefault` → `drawImageRect(src, dst)` with [filterMode].
     *  - `kTransparent` → skip the slice (destination untouched).
     *  - `kFixedColor` → fill the destination rect with the matching
     *    [SkLattice.colors] entry under SrcOver.
     *
     * Falls back to a plain [drawImageRect] over the full destination
     * when the lattice is degenerate (no divs, mismatched arrays,
     * out-of-bounds divs) — matches upstream's
     * `SkLatticeIter::Valid` reject path.
     */
    public open fun drawImageLattice(
        image: SkImage,
        lattice: SkLattice,
        dst: SkRect,
        filterMode: SkFilterMode = SkFilterMode.kNearest,
        paint: SkPaint? = null,
    ) {
        val srcBounds = lattice.bounds ?: SkIRect.MakeWH(image.width, image.height)
        val srcL = srcBounds.left.toFloat()
        val srcT = srcBounds.top.toFloat()
        val srcR = srcBounds.right.toFloat()
        val srcB = srcBounds.bottom.toFloat()
        val srcW = srcR - srcL
        val srcH = srcB - srcT
        if (srcW <= 0f || srcH <= 0f || dst.isEmpty) return

        // Reject divs that fall outside the source rect — matches upstream's
        // SkLatticeIter::Valid rejection. Empty divs is fine — the lattice
        // collapses to a single 1×1 grid (the whole source).
        val xDivsValid = lattice.xDivs.all { it > srcL.toInt() && it < srcR.toInt() } &&
            isStrictlyIncreasing(lattice.xDivs)
        val yDivsValid = lattice.yDivs.all { it > srcT.toInt() && it < srcB.toInt() } &&
            isStrictlyIncreasing(lattice.yDivs)
        if (!xDivsValid || !yDivsValid) {
            drawImageRect(
                image,
                SkRect.MakeLTRB(srcL, srcT, srcR, srcB),
                dst,
                SkSamplingOptions(filterMode),
                paint,
                SrcRectConstraint.kStrict,
            )
            return
        }

        // Build (N+1) and (M+1) source-side slice positions.
        val xs = IntArray(lattice.xDivs.size + 2)
        xs[0] = srcL.toInt()
        for (i in lattice.xDivs.indices) xs[i + 1] = lattice.xDivs[i]
        xs[xs.size - 1] = srcR.toInt()
        val ys = IntArray(lattice.yDivs.size + 2)
        ys[0] = srcT.toInt()
        for (i in lattice.yDivs.indices) ys[i + 1] = lattice.yDivs[i]
        ys[ys.size - 1] = srcB.toInt()

        val nCols = xs.size - 1
        val nRows = ys.size - 1
        val expectedCells = nCols * nRows
        val rectTypes = lattice.rectTypes
        val colors = lattice.colors
        if (rectTypes != null && rectTypes.size != expectedCells) {
            // Mis-sized rectTypes — degenerate fallback (matches upstream
            // SkLatticeIter::Valid reject).
            drawImageRect(
                image,
                SkRect.MakeLTRB(srcL, srcT, srcR, srcB),
                dst,
                SkSamplingOptions(filterMode),
                paint,
                SrcRectConstraint.kStrict,
            )
            return
        }

        // Compute the destination slice positions.
        // Even-indexed slices are fixed (corner-like) ; odd-indexed
        // slices stretch. Per upstream : slice index `i` is fixed iff
        // `i` is even (0, 2, 4 …) for both axes.
        val dstXs = computeDstSlicePositions(xs, dst.left, dst.right)
        val dstYs = computeDstSlicePositions(ys, dst.top, dst.bottom)

        // Walk the (M+1) × (N+1) cells.
        for (row in 0 until nRows) {
            val sy0 = ys[row].toFloat()
            val sy1 = ys[row + 1].toFloat()
            val dy0 = dstYs[row]
            val dy1 = dstYs[row + 1]
            if (dy1 <= dy0) continue
            for (col in 0 until nCols) {
                val sx0 = xs[col].toFloat()
                val sx1 = xs[col + 1].toFloat()
                val dx0 = dstXs[col]
                val dx1 = dstXs[col + 1]
                if (dx1 <= dx0) continue
                val cellIdx = row * nCols + col
                val type = rectTypes?.get(cellIdx) ?: SkLattice.RectType.kDefault
                when (type) {
                    SkLattice.RectType.kTransparent -> {
                        // Skip — destination stays untouched.
                    }
                    SkLattice.RectType.kFixedColor -> {
                        val c = colors?.getOrNull(cellIdx) ?: 0
                        val fillPaint = SkPaint().apply { color = c }
                        drawRect(SkRect.MakeLTRB(dx0, dy0, dx1, dy1), fillPaint)
                    }
                    SkLattice.RectType.kDefault -> {
                        drawImageRect(
                            image,
                            SkRect.MakeLTRB(sx0, sy0, sx1, sy1),
                            SkRect.MakeLTRB(dx0, dy0, dx1, dy1),
                            SkSamplingOptions(filterMode),
                            paint,
                            SrcRectConstraint.kStrict,
                        )
                    }
                }
            }
        }
    }

    private fun isStrictlyIncreasing(divs: IntArray): Boolean {
        for (i in 1 until divs.size) if (divs[i] <= divs[i - 1]) return false
        return true
    }

    /**
     * Compute the destination-side slice positions for a 9-patch /
     * N-patch row or column. Even slices keep their source pixel
     * width (corners) ; odd slices share the remaining destination
     * proportional to their source widths. When destination is
     * narrower than the corner sum, fixed slices shrink uniformly to
     * fit and odd slices collapse to zero — matches upstream's
     * `SkLatticeIter::computeDstSlices`.
     */
    private fun computeDstSlicePositions(srcEdges: IntArray, dstStart: Float, dstEnd: Float): FloatArray {
        val n = srcEdges.size - 1 // slice count
        val widths = FloatArray(n)
        var totalFixed = 0f
        var totalFlex = 0f
        for (i in 0 until n) {
            widths[i] = (srcEdges[i + 1] - srcEdges[i]).toFloat()
            if (i % 2 == 0) totalFixed += widths[i] else totalFlex += widths[i]
        }
        val dstSpan = dstEnd - dstStart
        val flexSpan = (dstSpan - totalFixed).coerceAtLeast(0f)
        val out = FloatArray(srcEdges.size)
        out[0] = dstStart
        if (totalFixed > dstSpan && totalFixed > 0f) {
            // Shrink fixed slices uniformly to fit ; flex slices collapse.
            val shrink = dstSpan / totalFixed
            for (i in 0 until n) {
                val w = if (i % 2 == 0) widths[i] * shrink else 0f
                out[i + 1] = out[i] + w
            }
        } else {
            for (i in 0 until n) {
                val w = if (i % 2 == 0) widths[i]
                else if (totalFlex > 0f) flexSpan * (widths[i] / totalFlex)
                else 0f
                out[i + 1] = out[i] + w
            }
        }
        // Snap last edge to dstEnd to absorb fp drift.
        out[out.size - 1] = dstEnd
        return out
    }

    /**
     * Mirrors Skia's
     * [`SkCanvas::drawPicture`](https://github.com/google/skia/blob/main/include/core/SkCanvas.h)
     * — replay every recorded op in [picture] into this canvas,
     * optionally pre-multiplied by [matrix] and wrapped in a
     * [saveLayer] using [paint] (when non-null).
     *
     * The default impl wraps [SkPicture.playback] in a
     * `save / concat / playback / restore` sequence so the
     * picture's internal CTM mutations don't leak out. When
     * [paint] is non-null we open a `saveLayer` over
     * [SkPicture.cullRect] so blend / alpha / image-filter on
     * the paint applies uniformly to the picture's composite —
     * matches upstream `SkCanvasPriv::DrawPictureWithMatrixAndPaint`.
     *
     * Subclasses ([org.skia.utils.SkNWayCanvas],
     * [org.skia.utils.SkNoDrawCanvas], recording sinks) override
     * for forwarding, no-op or capture semantics.
     */
    /**
     * Mirrors Skia's `SkCanvasPriv::ScaledBackdropLayer` with a
     * `SkCanvas::FilterSpan` (array of `SkImageFilter`s applied to the
     * layer in parallel). In upstream the `FilterSpan` is a Canvas2D-
     * specific extension of `saveLayer` that allows multiple image
     * filters to be applied simultaneously (e.g. dilate + erode, or
     * drop-shadow + null) to the saved layer's content. The first filter
     * in the span and the rest are run independently and composited
     * together. The raster implementation stores the span on the layer
     * and applies each filter independently on restore before compositing
     * with [paint].
     */
    public open fun saveLayerWithMultipleFilters(
        bounds: SkRect?,
        paint: SkPaint?,
        filters: List<SkImageFilter?>,
    ): Int {
        if (paint?.imageFilter != null) return saveLayer(bounds, paint)
        return saveLayer(SaveLayerRec(bounds = bounds, paint = paint, backdrop = null, flags = 0), filters)
    }

    public open fun drawPicture(
        picture: SkPicture,
        matrix: SkMatrix? = null,
        paint: SkPaint? = null,
    ) {
        val saveCount = getSaveCount()
        save()
        try {
            if (matrix != null) concat(matrix)
            if (paint != null) {
                saveLayer(picture.cullRect, paint)
                picture.playback(this)
                restore()
            } else {
                picture.playback(this)
            }
        } finally {
            restoreToCount(saveCount)
        }
    }
}

private const val F16_COLOR_TYPE_SAVE_LAYER_FLAG: SaveLayerFlags = 1 shl 4
