package org.skia.foundation

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import java.nio.ByteBuffer

/**
 * Mirrors Skia's
 * [`SkSurfaces`](https://github.com/google/skia/blob/main/include/core/SkSurface.h#L43)
 * namespace — the collection of free static factories that produce an
 * [SkSurface] from various sources (fresh raster allocation, pixel-buffer
 * wrap, null sink, …).
 *
 * Upstream these live as free functions inside `namespace SkSurfaces { … }`.
 * Kotlin has no free-function namespaces, so we collapse them onto a
 * sibling singleton [object] whose member functions match the upstream
 * static signatures one-for-one. `SkSurfaces::Raster(info)` in C++ ↔
 * `SkSurfaces.Raster(info)` in Kotlin.
 *
 * **Scope** : raster-only. Skia's GPU-backed factories (`RenderTarget`,
 * `WrapBackendRenderTarget`, `WrapBackendTexture`, `WrapAndroidHardwareBuffer`,
 * `WrapMetalLayer`, …) are intentionally out of scope — `:kanvas-skia`
 * is a raster facade.
 *
 * **Deferred items** (Phase R2 batch3-B) :
 *  - [WrapPixels] with the `SkPixmap` overload uses `Any` to side-step
 *    the unmerged-type compile dependency, and throws `TODO(R2-B)` until
 *    the parallel R2 batch3-A lands `SkPixmap`.
 */
public object SkSurfaces {

    /**
     * Mirrors Skia's
     * `SkSurfaces::Raster(const SkImageInfo&, size_t rowBytes,
     * const SkSurfaceProps*)`.
     *
     * Allocates a fresh raster [SkSurface] sized per [info]. The
     * backing [SkBitmap] is zero-initialised. `rowBytes` is accepted
     * for source-compatibility with upstream but currently ignored
     * (the raster backend allocates tightly packed storage — Skia's
     * `rowBytes = 0` default). Pass `rowBytes = 0` to match upstream
     * idiom.
     *
     * Returns `null` when [info] is empty (width or height ≤ 0) —
     * matches Skia's validity contract.
     */
    public fun Raster(info: SkImageInfo, rowBytes: Int = 0): SkSurface? {
        if (info.isEmpty()) return null
        if (rowBytes != 0 && rowBytes < info.minRowBytes()) return null
        return SkSurface.MakeRaster(info)
    }

    /**
     * Mirrors Skia's
     * `SkSurfaces::WrapPixels(const SkImageInfo&, void*, size_t,
     * PixelsReleaseProc, void*, const SkSurfaceProps*)`.
     *
     * Reads [info]`.width × `[info]`.height` pixels out of [pixels]
     * (using [rowBytes] for the stride between rows) into a fresh
     * raster [SkSurface]. The pixels are copied — `:kanvas-skia`
     * does not share buffers with the caller (no zero-copy
     * ByteBuffer path on the JVM raster backend). [releaseProc] is
     * invoked immediately after the copy completes (semantically
     * equivalent to upstream's "called when the surface is deleted,
     * meaning the caller can reclaim the buffer", since our copy
     * means the caller can reclaim the buffer right away).
     *
     * Returns `null` when [info] is empty or [rowBytes] is too small
     * to hold one row. Currently supports the 32-bit colour types
     * ([SkColorType.kRGBA_8888], [SkColorType.kBGRA_8888]) — other
     * colour types return `null`.
     */
    public fun WrapPixels(
        info: SkImageInfo,
        pixels: ByteBuffer,
        rowBytes: Int,
        releaseProc: (() -> Unit)? = null,
    ): SkSurface? {
        if (info.isEmpty()) return null
        if (rowBytes < info.minRowBytes()) return null
        val bpp = info.bytesPerPixel()
        val needed = (info.height - 1) * rowBytes + info.width * bpp
        if (pixels.remaining() < needed) return null

        val bitmap = SkBitmap(info.width, info.height, info.colorSpace, info.colorType)
        val buf = pixels.duplicate().order(pixels.order())

        when (info.colorType) {
            SkColorType.kRGBA_8888, SkColorType.kBGRA_8888 -> {
                val dst = if (info.colorType == SkColorType.kRGBA_8888) bitmap.pixels8888 else bitmap.pixelsBGRA8888
                val width = info.width
                val height = info.height
                for (y in 0 until height) {
                    val rowStart = buf.position() + y * rowBytes
                    for (x in 0 until width) {
                        val off = rowStart + x * 4
                        val b0 = buf.get(off).toInt() and 0xFF
                        val b1 = buf.get(off + 1).toInt() and 0xFF
                        val b2 = buf.get(off + 2).toInt() and 0xFF
                        val b3 = buf.get(off + 3).toInt() and 0xFF
                        val argb = if (info.colorType == SkColorType.kRGBA_8888) {
                            (b3 shl 24) or (b0 shl 16) or (b1 shl 8) or b2
                        } else {
                            (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
                        }
                        dst[y * width + x] = argb
                    }
                }
            }
            SkColorType.kAlpha_8 -> {
                val width = info.width
                for (y in 0 until info.height) {
                    val rowStart = buf.position() + y * rowBytes
                    for (x in 0 until width) {
                        bitmap.pixelsA8[y * width + x] = buf.get(rowStart + x)
                    }
                }
            }
            else -> return null
        }
        releaseProc?.invoke()
        return SkSurface.MakeRasterDirect(bitmap)
    }

    /**
     * Mirrors Skia's inline overload
     * `SkSurfaces::WrapPixels(const SkPixmap&, const SkSurfaceProps*)`.
     *
     * **Stub** : `SkPixmap` is being added by Phase R2 batch3-A — see
     * the kdoc on [SkSurfaces]. Once the type lands, change `pixmap`
     * from `Any` to `SkPixmap` and delegate to the byte-buffer
     * [WrapPixels] using `pixmap.info()`, `pixmap.writable_addr()`,
     * `pixmap.rowBytes()`.
     */
    public fun WrapPixels(pixmap: Any, releaseProc: (() -> Unit)? = null): SkSurface? {
        TODO("R2-B: depends on SkPixmap (parallel R2 batch3-A); will delegate once SkPixmap lands")
    }

    /**
     * Mirrors Skia's `SkSurfaces::Null(int width, int height)`.
     *
     * Returns a surface whose canvas accepts draw calls but discards
     * them — `makeImageSnapshot()` returns an all-zero (transparent)
     * [SkImage]. Useful as a benchmark sink or a dry-run target where
     * the caller wants to exercise the canvas-call pipeline without
     * paying for raster work.
     *
     * Upstream returns `nullptr` for non-positive dimensions ; we
     * throw `IllegalArgumentException` to surface the misuse loudly
     * at the call site (Kotlin idiom — null isn't a graceful fallback
     * for a programming error here).
     */
    public fun Null(width: Int, height: Int): SkSurface {
        require(width > 0 && height > 0) { "Null surface dimensions must be positive ; got ${width}x$height" }
        return NullSurface(width, height)
    }
}

/**
 * No-op [SkSurface] used by [SkSurfaces.Null]. The vended [SkCanvas]
 * draws into a discard bitmap that the surface never exposes ; the
 * snapshot is always a fresh all-zero [SkImage] sized to this surface.
 *
 * Skia's `MakeNull(width, height)` returns a surface whose canvas
 * "accepts draws but produces no rendered output" and whose
 * `makeImageSnapshot()` "returns nullptr". Returning a Kotlin null
 * from `makeImageSnapshot()` would force callers into nullable hand-
 * holding for every surface ; instead we produce an empty image
 * (transparent black) matching the semantic intent — "no useful pixels"
 * — while keeping the API non-nullable.
 */
private class NullSurface(width: Int, height: Int) : SkSurface(width, height) {

    private val discardBitmap: SkBitmap = SkBitmap(width, height)
    private val cachedCanvas: SkCanvas = SkCanvas(discardBitmap)

    private val info: SkImageInfo = SkImageInfo.MakeN32(width, height)

    override val canvas: SkCanvas get() = cachedCanvas

    /**
     * Returns a fresh all-zero [SkImage]. Mirrors upstream's null
     * snapshot (collapsed onto a non-null sentinel image — see the
     * class kdoc for the rationale).
     */
    override fun makeImageSnapshot(): SkImage =
        SkImage(width, height, IntArray(width * height), SkColorType.kRGBA_8888)

    override fun imageInfo(): SkImageInfo = info
}
