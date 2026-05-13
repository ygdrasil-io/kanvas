package org.skia.foundation

import org.skia.codec.SkCodec
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
 * **Deferred items** (Phase R2 batch3-B) :
 *  - [RasterFromPixmap] / [RasterFromPixmapCopy] are stubbed against
 *    `SkPixmap`, which is being added by the parallel R2 batch3-A. The
 *    Kotlin signatures use `Any` to avoid a compile-time dependency on
 *    the unmerged type and throw a `TODO(R2-B)` until the batch lands.
 *  - [DeferredFromGenerator] is stubbed against `SkImageGenerator`,
 *    likewise unmerged — currently throws `TODO(R2-B)`.
 *  - `RasterFromCompressedTextureData` is **out of scope** (GPU-only
 *    compression formats) and is not exposed here.
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
     * **Stub** : `SkPixmap` is being added by Phase R2 batch3-A — see
     * the kdoc on [SkImages] for the full status. Once the type lands,
     * change the `pixmap` parameter type from `Any` to `SkPixmap` and
     * replace the body with the actual share-pixels implementation
     * (`releaseProc` is invoked when the [SkImage] is unreachable).
     */
    public fun RasterFromPixmap(pixmap: Any, releaseProc: (() -> Unit)? = null): SkImage? {
        TODO("R2-B: depends on SkPixmap (parallel R2 batch3-A); will share-pixels once SkPixmap lands")
    }

    /**
     * Mirrors Skia's
     * `SkImages::RasterFromPixmapCopy(const SkPixmap&)`.
     *
     * **Stub** : see [RasterFromPixmap] — the parallel R2 batch3-A
     * adds `SkPixmap`. Once merged, this method copies the pixmap's
     * pixels into a fresh [SkImage].
     */
    public fun RasterFromPixmapCopy(pixmap: Any): SkImage? {
        TODO("R2-B: depends on SkPixmap (parallel R2 batch3-A); will copy-pixels once SkPixmap lands")
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

    /**
     * Mirrors Skia's
     * `SkImages::DeferredFromEncodedData(sk_sp<const SkData>,
     * std::optional<SkAlphaType>)`.
     *
     * Decodes the encoded byte stream [encoded] (PNG / JPEG / GIF /
     * BMP / WBMP / WEBP — see [SkCodec.MakeFromData] for the registered
     * formats) into a fresh raster [SkImage]. Returns `null` when no
     * registered codec matches the leading bytes, or when the decode
     * itself fails. Despite the upstream name ("deferred"), the
     * `:kanvas-skia` raster backend eagerly decodes — there is no JIT
     * decode-on-draw path.
     *
     * The alpha-type parameter from upstream is omitted ; we use the
     * codec's natural alpha type (matches `std::nullopt` upstream).
     */
    public fun DeferredFromEncodedData(encoded: ByteBuffer): SkImage? {
        // Materialise the ByteBuffer to a ByteArray without mutating the
        // caller's read cursor.
        val view = encoded.duplicate()
        val bytes = ByteArray(view.remaining())
        view.get(bytes)
        val codec = SkCodec.MakeFromData(bytes) ?: return null
        val (bitmap, result) = codec.getImage()
        if (result != SkCodec.Result.kSuccess || bitmap == null) return null
        return SkImage.Make(bitmap)
    }

    /**
     * Mirrors Skia's
     * `SkImages::DeferredFromGenerator(std::unique_ptr<SkImageGenerator>)`.
     *
     * **Stub** : `SkImageGenerator` is not yet on master. Once the type
     * lands, change `generator` from `Any` to `SkImageGenerator` and
     * delegate to the generator's `getPixels` / equivalent to produce
     * an [SkImage] backed by the generator's decoded output.
     */
    public fun DeferredFromGenerator(generator: Any): SkImage? {
        TODO("R2-B: depends on SkImageGenerator (not yet on master); will delegate to generator.getPixels once it lands")
    }
}
