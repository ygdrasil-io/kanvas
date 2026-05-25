package org.skia.core

import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkCubicResampler
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSurfaceProps
import org.graphiks.math.SkIRect
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Mirrors Skia's
 * [`SkSurface`](https://github.com/google/skia/blob/main/include/core/SkSurface.h)
 * — the host-owned raster destination on top of which a single [SkCanvas]
 * draws and from which immutable [SkImage] snapshots can be taken.
 *
 * Conceptually : a surface owns (or wraps) a backing pixel buffer and
 * vends exactly one [SkCanvas] that draws into it. `surface.canvas`
 * returns the same instance each call. `surface.makeImageSnapshot()`
 * returns an immutable [SkImage] whose pixels reflect the surface's
 * state at call time — subsequent draws into the surface don't leak
 * into the snapshot (Skia's "copy-on-write" semantics; here implemented
 * eagerly via [SkBitmap.pixels.copyOf] inside [SkImage.Make]).
 *
 * Used as :
 *
 *  - the destination for offscreen rendering passes (e.g. recording
 *    a GM into a surface, then composing the snapshot back onto a
 *    parent canvas).
 *  - the playback target for [org.skia.core.SkPicture] (the picture
 *    receives the surface's canvas).
 *  - the foundation primitive for the future DM (Skia DM) integration:
 *    DM reads pictures, plays them into surfaces, snapshots, compares.
 *
 * GPU / texture-backed variants (`MakeRenderTarget`, `MakeFromBackend*`)
 * are intentionally out of scope — kanvas-skia is raster-only.
 */
public abstract class SkSurface protected constructor(
    public val width: Int,
    public val height: Int,
    /**
     * Surface-level pixel-geometry / behaviour hints. Mirrors upstream's
     * `SkSurfaceProps* fProps` member ; `null` (the default) is the
     * upstream "no opinion" sentinel and collapses LCD-text rendering
     * to greyscale (see [SkSurfaceProps] for the bigger picture).
     *
     * Inherited by sub-surfaces created via
     * [org.skia.core.SkCanvas.makeSurface] when the caller doesn't
     * pass their own override.
     */
    private val _surfaceProps: SkSurfaceProps? = null,
) {

    /**
     * Mirrors Skia's `SkSurface::props()` — returns this surface's
     * pixel geometry / behaviour hints, or a default-constructed
     * [SkSurfaceProps] (`flags = 0`, `pixelGeometry = kUnknown`) when
     * the surface was created without an explicit props value. The
     * default matches upstream's `SkSurface::props()` zero-fill
     * fallback for surfaces that pre-date the props plumbing.
     */
    public fun props(): SkSurfaceProps = _surfaceProps ?: SkSurfaceProps()
    /**
     * The canvas that draws into this surface. Every call returns the
     * same [SkCanvas] instance (Skia's contract — clients may cache it
     * across draws). The canvas's CTM and clip stack persist between
     * snapshot calls; reset them explicitly via `canvas.restoreToCount(1)`
     * + `canvas.resetMatrix()` if needed.
     */
    public abstract val canvas: SkCanvas

    /**
     * Immutable snapshot of the current pixel state. The returned
     * [SkImage] owns its own buffer (eager copy) so mutating this
     * surface afterwards does not affect the snapshot.
     *
     * Mirrors Skia's `SkSurface::makeImageSnapshot()`.
     */
    public abstract fun makeImageSnapshot(): SkImage

    /**
     * Mirrors Skia's `SkSurface::makeImageSnapshot(const SkIRect&)` —
     * returns an immutable [SkImage] of the sub-rectangle [subset] of
     * this surface's current pixels.
     *
     * The requested rectangle is sanitized like upstream:
     *  - bounds extending beyond the surface are trimmed to the intersection.
     *  - non-intersecting bounds produce an empty image sentinel because this
     *    Kotlin surface API is non-nullable.
     */
    public open fun makeImageSnapshot(subset: SkIRect): SkImage {
        val bounds = SkIRect.MakeWH(width, height)
        val sanitized = SkIRect.MakeLTRB(bounds.left, bounds.top, bounds.right, bounds.bottom)
        if (!sanitized.intersect(subset)) {
            val info = imageInfo()
            return SkImage(0, 0, IntArray(0), info.colorType, info.colorSpace)
        }
        if (sanitized == bounds) return makeImageSnapshot()
        return makeImageSnapshot().makeSubset(sanitized)
            ?: SkImage(0, 0, IntArray(0), imageInfo().colorType, imageInfo().colorSpace)
    }

    /**
     * Draw this surface's current contents onto another canvas, with
     * the surface's `(0, 0)` landing at (`x`, `y`) in `target` space.
     * `paint` may carry alpha and a blend mode (filters/shaders are
     * ignored — they don't make sense for a surface composite).
     *
     * Mirrors Skia's `SkSurface::draw(SkCanvas*, SkScalar, SkScalar,
     * const SkPaint*)`.
     */
    public open fun draw(target: SkCanvas, x: Float, y: Float, paint: SkPaint? = null) {
        val image = makeImageSnapshot()
        target.drawImage(image, x, y, SkSamplingOptions.Default, paint)
    }

    /** Mirrors Skia's `SkSurface::imageInfo()`. */
    public abstract fun imageInfo(): SkImageInfo

    /**
     * Mirrors Skia's
     * [`SkSurface::makeTemporaryImage()`](https://github.com/google/skia/blob/main/include/core/SkSurface.h)
     * — return an [SkImage] backed by the **same** storage as this
     * surface's pixels, suitable for short-lived sampling within the
     * same draw operation that produced the surface contents.
     *
     * Upstream uses this on GPU configs to avoid an extra texture
     * allocation when the caller wants to feed the just-drawn surface
     * back into a follow-up draw (e.g. as a blur input). The contract
     * is **"the returned image is only valid until the next draw to
     * the surface"** — the caller must not retain it across mutations.
     *
     * **kanvas-skia raster behaviour** : with no GPU texture cache to
     * recycle, the lightest implementation is to delegate to
     * [makeImageSnapshot] which already produces a fresh raster
     * [SkImage] holding its own copy of the pixels (so the "same
     * storage" sharing degrades to "same pixel values at this point in
     * time", which is the strictly safer outcome). Callers porting GMs
     * that use `makeTemporaryImage` (`gm/hdr_pip_blur.cpp`) get the
     * expected visual result without having to special-case the raster
     * backend ; the only divergence is the cost (one extra IntArray
     * copy per call), which is benign for GM tests.
     */
    public open fun makeTemporaryImage(): SkImage = makeImageSnapshot()

    /**
     * Replay an [SkDeferredDisplayList] into this surface's canvas.
     * Returns `true` iff the DDL's characterization matches this
     * surface's [imageInfo] (and the playback ran). Returns `false`
     * — without modifying the surface — if the signature drifted
     * (different dimensions, colour type, alpha type, or colour
     * space).
     *
     * Mirrors Skia's
     * [`skgpu::ganesh::DrawDDL`](https://github.com/google/skia/blob/main/include/private/chromium/GrDeferredDisplayList.h#L113)
     * collapsed onto our raster pipeline (no GPU programs to compile,
     * no flush boundary, no thread-safe handoff — see
     * [SkDeferredDisplayList] KDoc for the raster-scope discussion).
     */
    public open fun draw(ddl: SkDeferredDisplayList): Boolean {
        if (!ddl.characterization.isCompatibleWith(this)) return false
        ddl.playbackInto(canvas)
        return true
    }

    // ── async-readback rescale family ──────────────────────────────────
    //
    // Mirrors Skia's
    // [`SkSurface::asyncRescaleAndReadPixels`](https://github.com/google/skia/blob/main/include/core/SkSurface.h)
    // surface — the GPU-side async readback pipeline that asynchronously
    // rescales the surface's content to a target [SkImageInfo] /
    // [SkISize] and delivers the resulting pixels to a [callback].
    // Skia's GPU path is genuinely asynchronous. The raster fallback here
    // follows Skia's CPU shape: synchronously snapshot, crop, resample, then
    // invoke the callback before returning.
    //
    // Used by `gm/asyncrescaleandread.cpp` (3 × 2 grid of rescaled
    // reads — nearest / repeated-linear / repeated-cubic × src-gamma /
    // linear-gamma) and any downstream consumer that needs surface →
    // bitmap with rescale (e.g. video pipelines, blur reference passes).

    /**
     * Async rescale + readback into RGBA pixels.
     *
     * CPU-raster fallback for the RGBA readback path. YUV/YUVA variants
     * remain dependency-gated separately.
     */
    public open fun asyncRescaleAndReadPixels(
        info: SkImageInfo,
        srcRect: org.graphiks.math.SkIRect,
        rescaleGamma: RescaleGamma = RescaleGamma.kSrc,
        rescaleMode: RescaleMode = RescaleMode.kNearest,
        callback: (AsyncReadResult?) -> Unit,
    ) {
        callback(readPixelsRescaled(info, srcRect, rescaleGamma, rescaleMode))
    }

    /**
     * Async rescale + readback into YUV-420 (3 planes, 4:2:0 chroma
     * subsampled). Used by video-encoder feed paths.
     *
     * **TODO: STUB.ASYNC_RESCALE_READ_YUV** — YUV/YUVA readback stays
     * dependency-gated outside the RGBA CPU fallback.
     */
    public open fun asyncRescaleAndReadPixelsYUV420(
        yuvColorSpace: SkYUVColorSpace,
        dstColorSpace: org.skia.foundation.SkColorSpace?,
        srcRect: org.graphiks.math.SkIRect,
        dstSize: org.graphiks.math.SkISize,
        rescaleGamma: RescaleGamma = RescaleGamma.kSrc,
        rescaleMode: RescaleMode = RescaleMode.kNearest,
        callback: (AsyncReadResult?) -> Unit,
    ): Unit = TODO("STUB.ASYNC_RESCALE_READ_YUV: SkSurface.asyncRescaleAndReadPixelsYUV420")

    /**
     * Async rescale + readback into YUVA-420 (3 planes + alpha plane,
     * 4:2:0 chroma subsampled).
     *
     * **TODO: STUB.ASYNC_RESCALE_READ_YUV** — same gated status as the
     * YUV420 variant.
     */
    public open fun asyncRescaleAndReadPixelsYUVA420(
        yuvColorSpace: SkYUVColorSpace,
        dstColorSpace: org.skia.foundation.SkColorSpace?,
        srcRect: org.graphiks.math.SkIRect,
        dstSize: org.graphiks.math.SkISize,
        rescaleGamma: RescaleGamma = RescaleGamma.kSrc,
        rescaleMode: RescaleMode = RescaleMode.kNearest,
        callback: (AsyncReadResult?) -> Unit,
    ): Unit = TODO("STUB.ASYNC_RESCALE_READ_YUV: SkSurface.asyncRescaleAndReadPixelsYUVA420")

    /** Linearisation hint for the async rescale step. Mirrors `SkImage::RescaleGamma`. */
    public enum class RescaleGamma { kSrc, kLinear }

    /** Sampling kernel for the async rescale step. Mirrors `SkImage::RescaleMode`. */
    public enum class RescaleMode { kNearest, kRepeatedLinear, kRepeatedCubic }

    /**
     * Async-readback result delivered to the
     * [asyncRescaleAndReadPixels] / `…YUV420` / `…YUVA420` callback.
     * Mirrors Skia's `SkSurface::AsyncReadResult` — a small handle that
     * exposes the per-plane pixel buffers and row-bytes.
     *
     * RGBA readbacks return a single plane. YUV/YUVA variants will add
     * multiple planes when those paths are implemented.
     */
    public abstract class AsyncReadResult internal constructor() {
        public abstract fun count(): Int
        public abstract fun data(planeIndex: Int): ByteArray
        public abstract fun rowBytes(planeIndex: Int): Int
    }

    /**
     * Identifier for the YUV transfer matrix used by the YUV420 / YUVA420
     * async readback variants. Mirrors Skia's `SkYUVColorSpace`.
     *
     * **TODO: STUB.ASYNC_RESCALE_READ** — the YUV pipeline itself is
     * not implemented ; the enum here is a flag-planting placeholder
     * sufficient to type the async-readback signatures.
     */
    public enum class SkYUVColorSpace {
        kJPEG_Full_YUV,
        kRec601_Limited_YUV,
        kRec709_Full_YUV,
        kRec709_Limited_YUV,
        kBT2020_8bit_Full_YUV,
        kBT2020_8bit_Limited_YUV,
        kBT2020_10bit_Full_YUV,
        kBT2020_10bit_Limited_YUV,
        kBT2020_12bit_Full_YUV,
        kBT2020_12bit_Limited_YUV,
        kFCC_Full_YUV,
        kFCC_Limited_YUV,
        kSMPTE240_Full_YUV,
        kSMPTE240_Limited_YUV,
        kYDZDX_Full_YUV,
        kYDZDX_Limited_YUV,
        kGBR_Full_YUV,
        kGBR_Limited_YUV,
        kYCgCo_8bit_Full_YUV,
        kYCgCo_8bit_Limited_YUV,
        kYCgCo_10bit_Full_YUV,
        kYCgCo_10bit_Limited_YUV,
        kYCgCo_12bit_Full_YUV,
        kYCgCo_12bit_Limited_YUV,
        kIdentity,
    }

    private fun readPixelsRescaled(
        info: SkImageInfo,
        srcRect: SkIRect,
        @Suppress("UNUSED_PARAMETER") rescaleGamma: RescaleGamma,
        rescaleMode: RescaleMode,
    ): AsyncReadResult? {
        if (info.isEmpty()) return null
        if (info.colorType !in rgbaAsyncColorTypes) return null
        if (srcRect.isEmpty) return null
        if (!SkIRect.MakeWH(width, height).contains(srcRect)) return null

        val srcInfo = info.makeWH(srcRect.width(), srcRect.height())
        val srcRowBytes = srcInfo.minRowBytes()
        val srcPixels = ByteBuffer.allocate(srcRowBytes * srcInfo.height).order(ByteOrder.LITTLE_ENDIAN)
        if (!makeImageSnapshot().readPixels(srcInfo, srcPixels, srcRowBytes, srcRect.left, srcRect.top)) {
            return null
        }

        val dstRowBytes = info.minRowBytes()
        val dstPixels = ByteBuffer.allocate(dstRowBytes * info.height).order(ByteOrder.LITTLE_ENDIAN)
        val srcPixmap = SkPixmap(srcInfo, srcPixels, srcRowBytes)
        val dstPixmap = SkPixmap(info, dstPixels, dstRowBytes)
        if (!srcPixmap.scalePixels(dstPixmap, rescaleMode.toSamplingOptions())) return null

        val bytes = ByteArray(dstPixels.capacity())
        dstPixels.duplicate().also {
            it.position(0)
            it.get(bytes)
        }
        return RasterAsyncReadResult(bytes, dstRowBytes)
    }

    private class RasterAsyncReadResult(
        private val bytes: ByteArray,
        private val stride: Int,
    ) : AsyncReadResult() {
        override fun count(): Int = 1

        override fun data(planeIndex: Int): ByteArray {
            require(planeIndex == 0) { "RGBA async readback has one plane, requested $planeIndex" }
            return bytes
        }

        override fun rowBytes(planeIndex: Int): Int {
            require(planeIndex == 0) { "RGBA async readback has one plane, requested $planeIndex" }
            return stride
        }
    }

    private fun RescaleMode.toSamplingOptions(): SkSamplingOptions = when (this) {
        RescaleMode.kNearest -> SkSamplingOptions.nearest()
        RescaleMode.kRepeatedLinear -> SkSamplingOptions.linear()
        RescaleMode.kRepeatedCubic -> SkSamplingOptions(SkCubicResampler.Mitchell)
    }

    public companion object {
        private val rgbaAsyncColorTypes: Set<SkColorType> = setOf(
            SkColorType.kAlpha_8,
            SkColorType.kARGB_4444,
            SkColorType.kRGBA_8888,
            SkColorType.kBGRA_8888,
        )

        /**
         * Allocate a raster surface with a freshly created backing
         * [SkBitmap] sized and configured per [info]. The bitmap is
         * zero-initialised. When [props] is non-null its
         * [SkSurfaceProps.pixelGeometry] / [SkSurfaceProps.flags]
         * round-trip via [SkSurface.props].
         *
         * Mirrors Skia's `SkSurface::MakeRaster(info, props?)`.
         */
        public fun MakeRaster(info: SkImageInfo, props: SkSurfaceProps? = null): SkSurface {
            require(!info.isEmpty()) { "MakeRaster: empty info $info" }
            val bitmap = SkBitmap(info.width, info.height, info.colorSpace, info.colorType)
            return RasterSurface(info, bitmap, props)
        }

        /**
         * Convenience for a sRGB / N32-premul raster surface of
         * `width × height`. Matches the most common DM sink config
         * (`8888` raster, `PremulRGB` colour space).
         */
        public fun MakeRasterN32Premul(
            width: Int,
            height: Int,
            colorSpace: SkColorSpace = SkColorSpace.makeSRGB(),
        ): SkSurface = MakeRaster(SkImageInfo.MakeN32Premul(width, height, colorSpace))

        /**
         * Wrap an externally owned [SkBitmap] as a surface. The surface
         * does **not** copy the bitmap — draws go directly into the
         * supplied buffer, and `makeImageSnapshot()` snapshots its
         * current state. Use this when the caller needs to reuse the
         * pixel buffer (e.g. test harness comparing rendered bitmaps).
         *
         * Mirrors Skia's `SkSurface::MakeRasterDirect(info, pixels,
         * rowBytes)` collapsed onto our [SkBitmap] type.
         */
        public fun MakeRasterDirect(bitmap: SkBitmap, props: SkSurfaceProps? = null): SkSurface {
            val info = SkImageInfo.Make(
                bitmap.width,
                bitmap.height,
                bitmap.colorType,
                inferAlphaType(bitmap.colorType),
                bitmap.colorSpace,
            )
            return RasterSurface(info, bitmap, props)
        }

        private fun inferAlphaType(ct: SkColorType): SkAlphaType = when (ct) {
            SkColorType.kRGBA_8888 -> SkAlphaType.kUnpremul
            SkColorType.kRGBA_F16Norm -> SkAlphaType.kPremul
            else -> SkAlphaType.kUnknown
        }
    }
}

/**
 * Raster-backed [SkSurface] — owns a single [SkBitmap] and a single
 * [SkCanvas] over it.
 */
private class RasterSurface(
    private val info: SkImageInfo,
    private val bitmap: SkBitmap,
    props: SkSurfaceProps? = null,
) : SkSurface(info.width, info.height, props) {
    private val cachedCanvas: SkCanvas = SkCanvas(bitmap, props)

    override val canvas: SkCanvas get() = cachedCanvas

    override fun makeImageSnapshot(): SkImage = SkImage.Make(bitmap)

    override fun imageInfo(): SkImageInfo = info
}
