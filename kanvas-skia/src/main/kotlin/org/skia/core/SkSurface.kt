package org.skia.core

import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSurfaceProps

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

    public companion object {
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
