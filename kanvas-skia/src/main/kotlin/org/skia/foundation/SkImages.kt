package org.skia.foundation


import org.graphiks.math.SK_ColorTRANSPARENT
import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.graphiks.math.SkIPoint
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkColorSetARGB
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

    /**
     * **STUB.COMPRESSED_TEXTURES** — mirrors Skia's
     * `SkImages::RasterFromCompressedTextureData(data, width, height,
     * compressionType)`.
     *
     * Upstream decodes a block-compressed payload (ETC1/ETC2, BC1/DXT1, …)
     * into a raster [SkImage] without going through the GPU. In
     * `:kanvas-skia` the decode pipeline for these compressed formats is
     * not yet implemented — this entry-point is a compile-contract stub
     * so that GMs that reference the factory (e.g. `ExoticFormatsGM`)
     * can call it and the `@Disabled` test records the gap.
     *
     * Always throws [NotImplementedError] with the `STUB.COMPRESSED_TEXTURES`
     * tag.
     */
    @Suppress("UNUSED_PARAMETER", "FunctionName")
    public fun RasterFromCompressedTextureData(
        data: ByteArray,
        width: Int,
        height: Int,
        compressionType: SkTextureCompressionType,
    ): SkImage? = RasterFromCompressedTextureData(
        SkData.MakeWithCopy(data),
        width,
        height,
        compressionType,
    )

    // `DeferredFromEncodedData(ByteBuffer): SkImage?` factory moved to
    // [org.graphiks.kanvas.codec.ImageCodecs] (cycle break preparing the
    // :cpu-raster Gradle module extraction — foundation no longer
    // imports from codec).

    // `DeferredFromGenerator` factory moved to
    // [org.graphiks.kanvas.codec.ImageCodecs.DeferredFromGenerator] (cycle break
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

    /**
     * Mirrors Skia's
     * [`SkImages::RasterFromCompressedTextureData(data, width, height, compression)`](https://github.com/google/skia/blob/main/include/core/SkImage.h)
     * — decodes a block-compressed payload ([data]) into a fresh raster
     * [SkImage] of the requested logical [width] × [height] (the
     * underlying block grid is rounded up to the next 4×4 multiple but
     * the caller-visible image is cropped back to the logical extent).
     *
     * [compression] picks the block format ; the entry currently
     * targets BC1 RGB / BC1 RGBA / ETC2 RGB8 (everything in the
     * [SkTextureCompressionType] enum bar [SkTextureCompressionType.kNone]).
     *
     * **Status.** Flag-planting STUB — throws
     * `NotImplementedError("STUB.COMPRESSED_TEXTURES")`. The kanvas-skia
     * raster backend has no block-decompression routine yet ; this
     * factory exists so the upstream `bc1_transparency` /
     * `compressed_textures` GM ports compile against the live surface
     * and stay `@Disabled` with the precise reason.
     *
     * **Originally documented as out-of-scope** in this class's header
     * KDoc ; reintroduced as a TODO entry-point to gate the upstream
     * GM ports (see PR for the BC1TransparencyGM body port).
     */
    public fun RasterFromCompressedTextureData(
        data: SkData,
        width: Int,
        height: Int,
        compression: SkTextureCompressionType,
    ): SkImage? {
        if (width <= 0 || height <= 0) return null
        return when (compression) {
            SkTextureCompressionType.kBC1_RGB8_UNORM -> decodeBC1(data, width, height, honorAlpha = false)
            SkTextureCompressionType.kBC1_RGBA8_UNORM -> decodeBC1(data, width, height, honorAlpha = true)
            SkTextureCompressionType.kETC2_RGB8_UNORM -> decodeETC2(data, width, height)
            SkTextureCompressionType.kNone -> null
        }
    }

    private fun decodeBC1(data: SkData, width: Int, height: Int, honorAlpha: Boolean): SkImage? {
        val blockW = (width + 3) / 4
        val blockH = (height + 3) / 4
        val needed = blockW * blockH * 8
        if (data.size < needed) return null
        val src = data.bytesUnsafe()
        val out = IntArray(width * height)
        var off = 0
        for (by in 0 until blockH) {
            for (bx in 0 until blockW) {
                val c0 = u16(src, off)
                val c1 = u16(src, off + 2)
                val idxBits = u32(src, off + 4)
                val pal = bc1Palette(c0, c1, honorAlpha)
                var bits = idxBits
                for (ly in 0 until 4) {
                    val py = by * 4 + ly
                    if (py >= height) {
                        bits = bits ushr 8
                        continue
                    }
                    for (lx in 0 until 4) {
                        val px = bx * 4 + lx
                        val idx = bits and 0x3
                        bits = bits ushr 2
                        if (px < width) out[py * width + px] = pal[idx]
                    }
                }
                off += 8
            }
        }
        return SkImage(width, height, out, SkColorType.kRGBA_8888)
    }

    private fun bc1Palette(c0: Int, c1: Int, honorAlpha: Boolean): IntArray {
        val p0 = rgb565ToColor(c0)
        val p1 = rgb565ToColor(c1)
        val r0 = (p0 ushr 16) and 0xFF
        val g0 = (p0 ushr 8) and 0xFF
        val b0 = p0 and 0xFF
        val r1 = (p1 ushr 16) and 0xFF
        val g1 = (p1 ushr 8) and 0xFF
        val b1 = p1 and 0xFF
        val p2: Int
        val p3: Int
        if (c0 > c1) {
            p2 = SkColorSetARGB(255, (2 * r0 + r1) / 3, (2 * g0 + g1) / 3, (2 * b0 + b1) / 3)
            p3 = SkColorSetARGB(255, (r0 + 2 * r1) / 3, (g0 + 2 * g1) / 3, (b0 + 2 * b1) / 3)
        } else {
            p2 = SkColorSetARGB(255, (r0 + r1) / 2, (g0 + g1) / 2, (b0 + b1) / 2)
            p3 = if (honorAlpha) 0 else SkColorSetARGB(255, 0, 0, 0)
        }
        return intArrayOf(p0, p1, p2, p3)
    }

    private fun rgb565ToColor(v: Int): Int {
        val r5 = (v ushr 11) and 0x1F
        val g6 = (v ushr 5) and 0x3F
        val b5 = v and 0x1F
        val r = (r5 shl 3) or (r5 ushr 2)
        val g = (g6 shl 2) or (g6 ushr 4)
        val b = (b5 shl 3) or (b5 ushr 2)
        return SkColorSetARGB(255, r, g, b)
    }

    private fun decodeETC2(data: SkData, width: Int, height: Int): SkImage? {
        val blockW = (width + 3) / 4
        val blockH = (height + 3) / 4
        val needed = blockW * blockH * 8
        if (data.size < needed) return null
        val src = data.bytesUnsafe()
        val out = IntArray(width * height)
        var off = 0
        for (by in 0 until blockH) {
            for (bx in 0 until blockW) {
                etc2DecodeBlock(src, off, width, height, bx, by, out)
                off += 8
            }
        }
        return SkImage(width, height, out, SkColorType.kRGBA_8888)
    }

    private fun etc2DecodeBlock(src: ByteArray, off: Int, w: Int, h: Int, bx: Int, by: Int, out: IntArray) {
        val hi = ((src[off].toInt() and 0xFF) shl 24) or
                ((src[off + 1].toInt() and 0xFF) shl 16) or
                ((src[off + 2].toInt() and 0xFF) shl 8) or
                (src[off + 3].toInt() and 0xFF)
        val lo = ((src[off + 4].toInt() and 0xFF) shl 24) or
                ((src[off + 5].toInt() and 0xFF) shl 16) or
                ((src[off + 6].toInt() and 0xFF) shl 8) or
                (src[off + 7].toInt() and 0xFF)

        val flipped = (hi shr 31) and 1
        val diff = (hi shr 30) and 1

        val baseR1: Int; val baseG1: Int; val baseB1: Int
        val baseR2: Int; val baseG2: Int; val baseB2: Int
        val table1: Int; val table2: Int

        if (diff == 0) {
            val r1_4 = (hi shr 15) and 0xF
            val g1_4 = (hi shr 10) and 0xF
            val b1_4 = (hi shr 5) and 0xF
            val r2_4 = (hi shr 0) and 0xF
            baseR1 = r1_4 * 17; baseG1 = g1_4 * 17; baseB1 = b1_4 * 17
            baseR2 = r2_4 * 17; baseG2 = baseG1; baseB2 = baseB1
            table1 = (hi shr 1) and 0x7
            table2 = (hi shr 4) and 0x7
        } else {
            val r1_5 = (hi shr 25) and 0x1F
            val g1_5 = (hi shr 20) and 0x1F
            val b1_5 = (hi shr 15) and 0x1F
            val dr = ((hi shr 12) and 0x7).let { if (it >= 4) it - 8 else it }
            val dg = ((hi shr 9) and 0x7).let { if (it >= 4) it - 8 else it }
            val db = ((hi shr 6) and 0x7).let { if (it >= 4) it - 8 else it }
            baseR1 = (r1_5 shl 3) or (r1_5 ushr 2)
            baseG1 = (g1_5 shl 3) or (g1_5 ushr 2)
            baseB1 = (b1_5 shl 3) or (b1_5 ushr 2)
            val r2_5 = (r1_5 + dr).coerceIn(0, 31)
            val g2_5 = (g1_5 + dg).coerceIn(0, 31)
            val b2_5 = (b1_5 + db).coerceIn(0, 31)
            baseR2 = (r2_5 shl 3) or (r2_5 ushr 2)
            baseG2 = (g2_5 shl 3) or (g2_5 ushr 2)
            baseB2 = (b2_5 shl 3) or (b2_5 ushr 2)
            table1 = (hi shr 3) and 0x7
            table2 = (hi shr 0) and 0x7
        }

        var indexBits = lo
        val pixelIndices = IntArray(16) { (indexBits ushr (it * 2)) and 3 }

        for (i in 0 until 16) {
            val lx: Int; val ly: Int
            if (flipped == 0) {
                lx = i % 4; ly = i / 4
            } else {
                lx = (i % 8) % 2 + (i / 8) * 2
                ly = (i % 8) / 2
            }
            val px = bx * 4 + lx
            val py = by * 4 + ly
            if (px >= w || py >= h) continue
            val sb = if (flipped == 0) { if (ly < 2) 0 else 1 } else { if (lx < 2) 0 else 1 }
            val table = if (sb == 0) table1 else table2
            val baseR = if (sb == 0) baseR1 else baseR2
            val baseG = if (sb == 0) baseG1 else baseG2
            val baseB = if (sb == 0) baseB1 else baseB2
            val idx = pixelIndices[i]
            val mod = ETC2_MODIFIER_TABLES[table][idx]
            val r = (baseR + mod).coerceIn(0, 255)
            val g = (baseG + mod).coerceIn(0, 255)
            val b = (baseB + mod).coerceIn(0, 255)
            out[py * w + px] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    private val ETC2_MODIFIER_TABLES = arrayOf(
        intArrayOf(2, 8, -8, -2),
        intArrayOf(5, 17, -17, -5),
        intArrayOf(9, 29, -29, -9),
        intArrayOf(13, 42, -42, -13),
        intArrayOf(18, 60, -60, -18),
        intArrayOf(24, 80, -80, -24),
        intArrayOf(33, 106, -106, -33),
        intArrayOf(47, 183, -183, -47),
    )

    private fun u16(src: ByteArray, off: Int): Int =
        (src[off].toInt() and 0xFF) or ((src[off + 1].toInt() and 0xFF) shl 8)

    private fun u32(src: ByteArray, off: Int): Int =
        (src[off].toInt() and 0xFF) or
            ((src[off + 1].toInt() and 0xFF) shl 8) or
            ((src[off + 2].toInt() and 0xFF) shl 16) or
            ((src[off + 3].toInt() and 0xFF) shl 24)

    /**
     * Mirrors Skia's
     * `SkImages::CrossContextTextureFromPixmap(GrDirectContext*, const SkPixmap&, bool buildMips)`
     * ([include/gpu/ganesh/SkImageGanesh.h](https://github.com/google/skia/blob/main/include/gpu/ganesh/SkImageGanesh.h)).
     *
     * Uploads [pixmap]'s pixels into a GPU cross-context texture and returns
     * a GPU-backed [SkImage] that can be consumed on a different GPU context
     * from the one used to create it. [buildMips] requests mip-map generation
     * on upload. The returned image holds a semaphore / sync primitive so the
     * producing context can signal ownership transfer to a consuming context.
     *
     * **Status : STUB.CROSS_CONTEXT_IMAGE** — `:kanvas-skia` is a raster-only
     * backend. There is no `GrDirectContext` and no GPU texture pipeline. This
     * factory exists solely so [CrossContextImageGM] compiles against the live
     * [SkImages] surface and remains `@Disabled` with a precise reason.
     * Throws [NotImplementedError] unconditionally at runtime.
     */
    public fun CrossContextTextureFromPixmap(
        pixmap: SkPixmap,
        buildMips: Boolean,
    ): SkImage? {
        TODO(
            "STUB.CROSS_CONTEXT_IMAGE: SkImages.CrossContextTextureFromPixmap(" +
                "pixmap=${pixmap.info().width}x${pixmap.info().height}, buildMips=$buildMips) " +
                "requires GrDirectContext — kanvas-skia is raster-only " +
                "(see CrossContextImageGM)."
        )
    }

    /**
     * Mirrors Skia's
     * `SkImages::TextureFromYUVAImages(Recorder*, const SkYUVAInfo&,
     * const sk_sp<SkImage>[kMaxPlanes], sk_sp<SkColorSpace>)`.
     *
     * Graphite (GPU) factory that assembles a YUVA multi-plane image from
     * pre-rendered per-plane GPU textures ([planes]) and a YUVA layout
     * descriptor ([yuvaInfo]). The resulting image is GPU-resident and
     * performs YUV→RGB conversion on the fly during drawing.
     *
     * This factory has no raster equivalent in `:kanvas-skia` — the raster
     * backend has no GPU recorder or texture concept. Any caller must guard
     * against `null` and/or be `@Disabled` with this stub tag.
     *
     * **TODO: STUB.YUVA_PIXMAPS** — `SkImages::TextureFromYUVAImages` is
     * Graphite-only; the raster backend cannot materialise GPU-resident
     * YUVA images.
     */
    public fun TextureFromYUVAImages(
        recorder: Any?,
        yuvaInfo: SkYUVAInfo,
        planes: Array<SkImage?>,
        imageColorSpace: SkColorSpace?,
    ): SkImage? {
        TODO(
            "STUB.YUVA_PIXMAPS: SkImages.TextureFromYUVAImages — " +
                "Graphite GPU-only factory; no raster equivalent in kanvas-skia."
        )
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
