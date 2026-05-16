package org.skia.foundation


import org.graphiks.math.SK_ColorTRANSPARENT
import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.graphiks.math.SkIPoint
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import java.nio.ByteBuffer

/**
 * Mirrors Skia's
 * [`SkImages`](https://github.com/google/skia/blob/main/include/core/SkImage.h#L45)
 * namespace — the collection of free static factories that produce an
 * immutable [SkImage] from various sources (bitmap snapshots, pixmap
 * shares, encoded streams, …).
 *
 * Upstream these live as free functions inside `namespace SkImages { … }`.
 * Kotlin has no free-function namespaces, so we collapse them onto a
 * sibling singleton [object] whose member functions match the upstream
 * static signatures one-for-one. This keeps the porting story trivial :
 * `SkImages::RasterFromBitmap(bm)` in C++ ↔ `SkImages.RasterFromBitmap(bm)`
 * in Kotlin.
 *
 * **Scope** : raster-only. Skia's GPU-backed factories (`Texture*`,
 * `RenderTarget*`, `CrossContext*`, `PromiseTexture*`) are intentionally
 * out of scope — `:kanvas-skia` is a raster facade.
 *
 * **R-suivi.12 cleanup** — the [RasterFromPixmap] / [RasterFromPixmapCopy]
 * / [DeferredFromGenerator] factories landed alongside their
 * dependencies ([SkPixmap], [SkImageGenerator]). `RasterFromCompressedTextureData`
 * remains **out of scope** (GPU-only compression formats) and is not
 * exposed here.
 */
public object SkImages {

    /**
     * Mirrors Skia's
     * `SkImages::RasterFromBitmap(const SkBitmap&)`. Snapshots
     * [bitmap] into a fresh immutable [SkImage] sharing no storage
     * with the source. Returns `null` when [bitmap] has zero width
     * or height (Skia's contract — see `SkImage.h`).
     */
    public fun RasterFromBitmap(bitmap: SkBitmap): SkImage? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        return SkImage.Make(bitmap)
    }

    /**
     * Mirrors Skia's
     * `SkImages::RasterFromPixmap(const SkPixmap&, RasterReleaseProc,
     * ReleaseContext)`.
     *
     * Wraps [pixmap]'s pixels into a fresh raster [SkImage]. Upstream's
     * contract is "no copy ; the caller keeps the buffer alive until
     * `releaseProc` is invoked when the image is no longer referenced".
     * The kanvas-skia raster backend has no zero-copy ByteBuffer path
     * ([SkImage]'s storage is always an `IntArray` of Pascal-Argb pixels
     * — see [SkImage.pixels] KDoc), so we eagerly snapshot the pixmap
     * into the image's internal buffer and invoke [releaseProc]
     * immediately after the snapshot completes. This matches
     * [RasterFromData]'s already-documented divergence from upstream's
     * zero-copy share. Returns `null` if [pixmap] is empty
     * (`width == 0 || height == 0`) or has [SkColorType.kUnknown].
     */
    public fun RasterFromPixmap(pixmap: SkPixmap, releaseProc: (() -> Unit)? = null): SkImage? {
        val image = snapshotPixmap(pixmap) ?: return null
        releaseProc?.invoke()
        return image
    }

    /**
     * Mirrors Skia's `SkImages::RasterFromPixmapCopy(const SkPixmap&)`.
     *
     * Explicitly copies [pixmap]'s pixels into a fresh raster [SkImage].
     * Functionally identical to [RasterFromPixmap] in kanvas-skia (we
     * always copy — see [RasterFromPixmap] KDoc for the rationale) but
     * doesn't accept a release proc since there's no shared buffer to
     * release. Returns `null` for empty or unknown-colour-type pixmaps.
     */
    public fun RasterFromPixmapCopy(pixmap: SkPixmap): SkImage? = snapshotPixmap(pixmap)

    /**
     * Walk the pixmap's bytes once and produce a Pascal-Argb [IntArray]
     * suitable for the [SkImage] constructor. Shared between
     * [RasterFromPixmap] and [RasterFromPixmapCopy] — both routes copy
     * eagerly (see [RasterFromPixmap] KDoc).
     */
    private fun snapshotPixmap(pixmap: SkPixmap): SkImage? {
        val info = pixmap.info()
        if (info.isEmpty()) return null
        if (info.colorType == SkColorType.kUnknown) return null
        val w = info.width
        val h = info.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                pixels[y * w + x] = pixmap.getColor(x, y)
            }
        }
        return SkImage(
            width = w,
            height = h,
            pixels = pixels,
            colorType = info.colorType,
            colorSpace = info.colorSpace,
        )
    }

    /**
     * Mirrors Skia's
     * `SkImages::RasterFromData(const SkImageInfo&, sk_sp<SkData>,
     * size_t rowBytes)`.
     *
     * Reads [info]`.width × `[info]`.height` pixels out of [data]
     * (using [rowBytes] for the stride between successive rows) into
     * a fresh [SkImage]. The pixels are copied — `:kanvas-skia` does
     * not share buffers with the caller (Skia's contract documents
     * "pixels data will not be copied", but the raster backend on
     * the JVM has no zero-copy ByteBuffer path, so we copy eagerly
     * and document the divergence).
     *
     * Currently supports the 32-bit colour types
     * ([SkColorType.kRGBA_8888], [SkColorType.kBGRA_8888]) — every
     * other colour type returns `null` (matches Skia's validity
     * check : the colour type must be supported by the raster backend).
     */
    public fun RasterFromData(info: SkImageInfo, data: ByteBuffer, rowBytes: Int): SkImage? {
        if (info.isEmpty()) return null
        if (rowBytes < info.minRowBytes()) return null
        val bpp = info.bytesPerPixel()
        // Total bytes the buffer must contain : `(height - 1) * rowBytes + width * bpp`
        // (the last row need not have trailing stride padding).
        val needed = (info.height - 1) * rowBytes + info.width * bpp
        if (data.remaining() < needed) return null

        val bitmap = SkBitmap(info.width, info.height, info.colorSpace, info.colorType)
        // Snapshot the ByteBuffer's read position so we don't mutate the
        // caller's cursor — we duplicate the buffer instead of advancing.
        val buf = data.duplicate().order(data.order())

        when (info.colorType) {
            SkColorType.kRGBA_8888, SkColorType.kBGRA_8888 -> {
                val dst = if (info.colorType == SkColorType.kRGBA_8888) bitmap.pixels8888 else bitmap.pixelsBGRA8888
                val width = info.width
                val height = info.height
                for (y in 0 until height) {
                    val rowStart = buf.position() + y * rowBytes
                    for (x in 0 until width) {
                        val off = rowStart + x * 4
                        // Read 4 bytes as a little-endian word matching the
                        // `:kanvas-skia` Pascal-Argb layout (`0xAARRGGBB`).
                        // SkColorType.kRGBA_8888 docs : low byte is R, high
                        // is A. SkColorType.kBGRA_8888 docs : low byte is B,
                        // high is A. Both share the in-memory ARGB Int
                        // representation (see SkBitmap.pixelsBGRA8888 kdoc).
                        val b0 = buf.get(off).toInt() and 0xFF
                        val b1 = buf.get(off + 1).toInt() and 0xFF
                        val b2 = buf.get(off + 2).toInt() and 0xFF
                        val b3 = buf.get(off + 3).toInt() and 0xFF
                        val argb = if (info.colorType == SkColorType.kRGBA_8888) {
                            // RGBA layout : (R, G, B, A) → 0xAARRGGBB
                            (b3 shl 24) or (b0 shl 16) or (b1 shl 8) or b2
                        } else {
                            // BGRA layout : (B, G, R, A) → 0xAARRGGBB
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
        return SkImage.Make(bitmap)
    }

    // `DeferredFromEncodedData(ByteBuffer): SkImage?` factory moved to
    // [org.skia.codec.SkImageCodecs] (cycle break preparing the
    // :cpu-raster Gradle module extraction — foundation no longer
    // imports from codec).

    // `DeferredFromGenerator` factory moved to
    // [org.skia.codec.SkImageCodecs.DeferredFromGenerator] (cycle break
    // preparing the :cpu-raster Gradle module extraction — foundation
    // no longer imports from codec).

    /**
     * Mirrors Skia's
     * `SkImages::TextureFromYUVAPixmaps` / `SkImages::RasterFromYUVAPixmaps`
     * landing point — a single entry that materialises a YUV(A) multi-
     * plane source into an immutable raster [SkImage].
     *
     * The raster backend has no separate YUV draw path, so we eagerly
     * convert via [SkYUVAPixmaps.toRGBA8888] and wrap the resulting 8888
     * bitmap with [RasterFromBitmap]. Returns `null` when the supplied
     * [pixmaps] reports `isValid() == false`. Throws
     * [IllegalStateException] (propagated from
     * [SkYUVAPixmaps.toRGBA8888]) for plane configs we don't yet
     * support (interleaved / alpha-bearing).
     *
     * R-suivi.41 lands the bitmap-bridge path ; a future R-suivi entry
     * can wire a direct `SkBitmapDevice.drawImageYUVA(...)` for
     * intermediate-buffer-free draws.
     */
    public fun YUVA(pixmaps: SkYUVAPixmaps): SkImage? {
        if (!pixmaps.isValid()) return null
        val rgba = pixmaps.toRGBA8888()
        return RasterFromBitmap(rgba)
    }

    /**
     * Mirrors Skia's `SkImage::BitDepth` enum
     * ([include/core/SkImage.h](https://github.com/google/skia/blob/main/include/core/SkImage.h)).
     * Selects the per-channel storage of [DeferredFromPicture]'s
     * backing surface — 8-bit unsigned ([kU8]) or 16-bit half-float
     * ([kF16]).
     */
    public enum class BitDepth { kU8, kF16 }

    /**
     * Mirrors Skia's
     * `SkImages::DeferredFromPicture(picture, dimensions, matrix,
     * paint, bitDepth, colorSpace, props)`
     * ([include/core/SkImage.h](https://github.com/google/skia/blob/main/include/core/SkImage.h)).
     *
     * Materialises [picture] into a fresh raster [SkImage] sized to
     * [dimensions], with optional [matrix] / [paint] applied to the
     * playback canvas. The bitmap is allocated in the colour type
     * implied by [bitDepth] ([SkColorType.kRGBA_8888] for [BitDepth.kU8]
     * or [SkColorType.kRGBA_F16Norm] for [BitDepth.kF16]) and tagged
     * with [colorSpace] (defaulting to sRGB when omitted, matching
     * upstream's `colorSpace == nullptr` fall-back).
     *
     * **Deferred → eager** : despite the name, the kanvas-skia raster
     * backend rasterises immediately into the backing bitmap — there
     * is no on-demand-render-on-draw path. The trade-off matches the
     * other `Deferred*` factories ([DeferredFromEncodedData],
     * [DeferredFromGenerator]).
     *
     * **Surface-properties divergence** : Skia upstream takes an
     * `SkSurfaceProps` arg controlling LCD-text geometry / pixel
     * geometry. The kanvas-skia raster backend has no equivalent
     * surface-prop knob (LCD subpixel rendering downgrades to plain
     * AA — see [`org.skia.foundation.SkFont`]), so the parameter is
     * not exposed here. A future R-suivi entry can wire it up if a
     * GM ever needs distinct LCD geometry per deferred-picture image.
     *
     * Returns `null` when [dimensions] is empty (`width <= 0` or
     * `height <= 0`).
     */
    /**
     * Mirrors Skia's
     * [`SkImages::MakeWithFilter(image, filter, subset, clipBounds, outSubset, outOffset)`](https://github.com/google/skia/blob/main/src/image/SkImage_Lazy.cpp).
     *
     * Applies [filter] to [image] over the source rectangle [subset]
     * (relative to [image]) and returns a fresh raster [SkImage]
     * containing the portion of the filter's output that intersects
     * [clipBounds] (also in [image]-local coords). The filter's output
     * may grow beyond [subset] (e.g. blur expands by `±radius`) — the
     * intersection with [clipBounds] is the final raster's footprint.
     *
     * **Out parameters** :
     *  - [outSubset]  — when non-null, set to the rectangle in
     *                  [image]-local coords that the returned raster
     *                  represents. For a blur this is `subset` inflated
     *                  by `±sigma*3` then intersected with [clipBounds].
     *  - [outOffset]  — when non-null, set to the offset (in
     *                  [image]-local coords) of the returned raster's
     *                  top-left corner. The caller composites the
     *                  returned image at `(outOffset.x, outOffset.y)`.
     *
     * **Pipeline** : we route through the existing [SkImageFilter] /
     * [SkPaint] machinery — the canonical raster code path is
     * `Surface(clipBounds-sized) → drawImage(image - subset, paint{filter})
     * → snapshot()`. That funnels every filter (blur, drop-shadow,
     * colour-matrix, …) through the same code as a draw-time filter
     * application, keeping the visual result bit-identical to a
     * `canvas.drawImage(image, paint{filter})` call.
     *
     * Returns `null` when [filter] is `null` (mirrors upstream's
     * "filter == nullptr → nullptr" early-out — the caller is supposed
     * to skip the call when there's no filter), or when [clipBounds]
     * intersects [subset]'s filter output to an empty rect.
     */
    public fun MakeWithFilter(
        image: SkImage,
        filter: SkImageFilter?,
        subset: SkIRect,
        clipBounds: SkIRect,
        outSubset: SkIRect? = null,
        outOffset: SkIPoint? = null,
    ): SkImage? {
        if (filter == null) return null
        if (subset.isEmpty || clipBounds.isEmpty) return null

        // 1) Apply the filter to the requested subset of the input.
        //    We feed a `subset`-cropped view of the source as the filter's
        //    input image so the filter sees only the relevant region.
        val srcSubset = image.makeSubset(subset) ?: return null
        val result = filter.filterImage(srcSubset, SkMatrix.I())

        // 2) Compute the filter's output rect in image-local coords :
        //    starts at `subset.topLeft + result.offset`, sized by the
        //    filtered image's dimensions.
        val filteredW = result.image.width
        val filteredH = result.image.height
        val outputRect = SkIRect(
            left = subset.left + result.offsetX,
            top = subset.top + result.offsetY,
            right = subset.left + result.offsetX + filteredW,
            bottom = subset.top + result.offsetY + filteredH,
        )

        // 3) Intersect with clipBounds.
        val cl = maxOf(outputRect.left, clipBounds.left)
        val ct = maxOf(outputRect.top, clipBounds.top)
        val cr = minOf(outputRect.right, clipBounds.right)
        val cb = minOf(outputRect.bottom, clipBounds.bottom)
        if (cl >= cr || ct >= cb) return null
        val intersect = SkIRect(cl, ct, cr, cb)

        // 4) Slice the filtered image to the intersected region (in
        //    filtered-image-local coords : subtract the output rect's
        //    top-left).
        val sliceLeft = intersect.left - outputRect.left
        val sliceTop = intersect.top - outputRect.top
        val sliceRight = intersect.right - outputRect.left
        val sliceBottom = intersect.bottom - outputRect.top
        val sliced = result.image.makeSubset(
            SkIRect(sliceLeft, sliceTop, sliceRight, sliceBottom)
        ) ?: return null

        // 5) Fill the out-params.
        outSubset?.setLTRB(0, 0, intersect.width(), intersect.height())
        outOffset?.let { it.fX = intersect.left; it.fY = intersect.top }

        return sliced
    }

    public fun DeferredFromPicture(
        picture: SkPicture,
        dimensions: SkISize,
        matrix: SkMatrix? = null,
        paint: SkPaint? = null,
        bitDepth: BitDepth = BitDepth.kU8,
        colorSpace: SkColorSpace = SkColorSpace.makeSRGB(),
    ): SkImage? {
        if (dimensions.width <= 0 || dimensions.height <= 0) return null
        val colorType = when (bitDepth) {
            BitDepth.kU8 -> SkColorType.kRGBA_8888
            BitDepth.kF16 -> SkColorType.kRGBA_F16Norm
        }
        val bitmap = SkBitmap(
            width = dimensions.width,
            height = dimensions.height,
            colorSpace = colorSpace,
            colorType = colorType,
        )
        val canvas = SkCanvas(bitmap)
        // Wipe the snapshot to fully transparent so the picture's own
        // alpha is preserved (matches `SkPicture.makeShader`).
        canvas.clear(SK_ColorTRANSPARENT)
        // Apply the optional [matrix] before [paint] : upstream uses
        // an `SkPaint` saveLayer for the [paint] arg (so its filters /
        // colour filter wrap the playback) and prepends [matrix] to
        // the canvas. Without a real saveLayer-with-paint hook into
        // the canvas surface we emulate via `saveLayer(null, paint)`
        // — this matches the upstream contract for the common case
        // where [paint] only carries a colour-filter / image-filter
        // (the GMs that would call DeferredFromPicture today, e.g.
        // PictureImageFilterGM, do not pass a paint).
        if (matrix != null) canvas.concat(matrix)
        if (paint != null) {
            canvas.saveLayer(null, paint)
            picture.playback(canvas)
            canvas.restore()
        } else {
            picture.playback(canvas)
        }
        return SkImage.Make(bitmap)
    }
}
