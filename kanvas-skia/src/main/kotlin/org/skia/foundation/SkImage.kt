package org.skia.foundation


import org.graphiks.math.SkColor
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkEncodedImageFormat
import org.graphiks.math.SkIRect
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Mirrors Skia's [`SkImage`](https://github.com/google/skia/blob/main/include/core/SkImage.h).
 *
 * The kanvas-skia implementation is a thin wrapper around an immutable
 * snapshot of an [SkBitmap]'s pixels. Skia's full surface/texture model
 * (GPU backing, codec-decoded streams, lazy mips) is intentionally out of
 * scope — we only need read-only access to a rectangular pixel buffer.
 *
 * Construct via [SkBitmap.asImage] or the [Make] factory.
 */
public class SkImage public constructor(
    public val width: Int,
    public val height: Int,
    public val pixels: IntArray,
    /**
     * Colour type of the bitmap this image was snapshotted from.
     * Internally the pixel buffer is always 8888 (so [SkBitmapShader] and
     * the raster device can read [pixels] uniformly), but callers that
     * need to know the originating type — e.g. an Alpha8 source image
     * fed into a colour-filter pipeline — can introspect this field.
     */
    public val colorType: SkColorType = SkColorType.kRGBA_8888,
    /**
     * Phase R2.12 — colour space of the snapshotted pixels. Defaults to
     * sRGB (matches upstream's "If SkImage colorSpace() returns nullptr,
     * SkImage SkColorSpace is assumed to be sRGB"). Set explicitly by the
     * [Make] factory when snapshotting from a non-sRGB bitmap, and by
     * [makeColorSpace] when re-tagging into a different working space.
     */
    public val colorSpace: SkColorSpace = SkColorSpace.makeSRGB(),
    /**
     * Phase G10 — optional pre-rendered mip pyramid. `null` until the
     * caller invokes [withDefaultMipmaps]. When non-null,
     * `mipLevels[0]` is the same `width × height × pixels` as this image
     * (level 0), and each subsequent entry halves the previous level
     * (rounding down, minimum 1) with 2×2 box-filter averaging. The
     * pyramid stops at 1×1, mirroring Skia's `SkMipmap::Build` levels.
     */
    public val mipLevels: List<MipLevel>? = null,
) {
    /** A single pre-rendered mip level — level 0 is the base image. */
    public class MipLevel(val width: Int, val height: Int, val pixels: IntArray)

    /** Direct read of pixel `(x, y)`; returns `0` if outside image bounds. */
    public fun peekPixel(x: Int, y: Int): SkColor =
        if (x in 0 until width && y in 0 until height) pixels[y * width + x] else 0

    /**
     * Mirrors Skia's `SkImage::makeShader(tmx, tmy, sampling, localMatrix)`.
     * Phase 5g — see [SkBitmapShader] for the sampling rules.
     */
    public fun makeShader(
        tileX: SkTileMode = SkTileMode.kClamp,
        tileY: SkTileMode = SkTileMode.kClamp,
        sampling: SkSamplingOptions = SkSamplingOptions.Default,
        localMatrix: org.graphiks.math.SkMatrix = org.graphiks.math.SkMatrix.Identity,
    ): SkShader = SkBitmapShader(this, tileX, tileY, sampling, localMatrix)

    /**
     * Mirrors Skia's `SkImage::makeShader(sampling, localMatrix)` —
     * defaults the tile modes to `kClamp`.
     */
    public fun makeShader(
        sampling: SkSamplingOptions,
        localMatrix: org.graphiks.math.SkMatrix = org.graphiks.math.SkMatrix.Identity,
    ): SkShader = SkBitmapShader(this, SkTileMode.kClamp, SkTileMode.kClamp, sampling, localMatrix)

    /**
     * Phase G10 — number of mip levels stored on this image, or `1`
     * when no mip pyramid has been pre-built. Matches the Skia
     * `SkMipmap::CountLevels` convention (`floor(log2(min(w, h))) + 1`
     * when the pyramid is built down to 1×1).
     */
    public fun levelCount(): Int = mipLevels?.size ?: 1

    /**
     * Mirrors Skia's `SkImage::imageInfo()` — returns a fresh
     * [SkImageInfo] describing this image's geometry / colour-type /
     * colour-space tagging. The alpha type is fixed to
     * [SkAlphaType.kUnpremul] (kanvas-skia's [SkImage] always stores
     * non-premultiplied 8888 pixels — see [SkImage] KDoc).
     */
    public fun imageInfo(): SkImageInfo = SkImageInfo.Make(
        width = width,
        height = height,
        colorType = colorType,
        alphaType = SkAlphaType.kUnpremul,
        colorSpace = colorSpace,
    )

    /**
     * Phase G10 — pre-build a box-filtered mip pyramid down to 1×1.
     * Each level halves the previous (round-down, minimum 1) and stores
     * a fresh `IntArray` so the pyramid is self-contained.
     *
     * Mirrors Skia's `SkImage::withDefaultMipmaps()`. Returns a *new*
     * [SkImage] sharing the level-0 pixel buffer with `this` — the
     * original image is left untouched.
     *
     * Box-filter is the same simplification Skia falls back to for
     * raster mip generation : per output texel, average the four
     * 2×2 source texels in unpremul ARGB space. For odd-sized levels
     * the rightmost column / bottom row is averaged with the duplicated
     * last-column / last-row texel so the dimensions still halve.
     */
    public fun withDefaultMipmaps(): SkImage {
        // Build levels until the smaller dimension reaches 1.
        val levels = ArrayList<MipLevel>()
        levels += MipLevel(width, height, pixels)
        var w = width
        var h = height
        var src = pixels
        while (w > 1 || h > 1) {
            val nw = maxOf(1, w / 2)
            val nh = maxOf(1, h / 2)
            val dst = IntArray(nw * nh)
            for (y in 0 until nh) {
                val sy0 = (2 * y).coerceAtMost(h - 1)
                val sy1 = (2 * y + 1).coerceAtMost(h - 1)
                for (x in 0 until nw) {
                    val sx0 = (2 * x).coerceAtMost(w - 1)
                    val sx1 = (2 * x + 1).coerceAtMost(w - 1)
                    val c00 = src[sy0 * w + sx0]
                    val c10 = src[sy0 * w + sx1]
                    val c01 = src[sy1 * w + sx0]
                    val c11 = src[sy1 * w + sx1]
                    // Average 4 texels in unpremul ARGB. Skia's
                    // raster mip uses premul-RGB for the colour
                    // channels but the difference is invisible for
                    // opaque images (the common case) and minor for
                    // mixed-alpha mip building.
                    val a = (SkColorGetA(c00) + SkColorGetA(c10) +
                             SkColorGetA(c01) + SkColorGetA(c11) + 2) shr 2
                    val r = (SkColorGetR(c00) + SkColorGetR(c10) +
                             SkColorGetR(c01) + SkColorGetR(c11) + 2) shr 2
                    val g = (SkColorGetG(c00) + SkColorGetG(c10) +
                             SkColorGetG(c01) + SkColorGetG(c11) + 2) shr 2
                    val b = (SkColorGetB(c00) + SkColorGetB(c10) +
                             SkColorGetB(c01) + SkColorGetB(c11) + 2) shr 2
                    dst[y * nw + x] = SkColorSetARGB(a, r, g, b)
                }
            }
            levels += MipLevel(nw, nh, dst)
            w = nw
            h = nh
            src = dst
        }
        return SkImage(width, height, pixels, colorType, colorSpace, levels)
    }

    // ─── Phase R2.12 — subset / colour-space / encode / readPixels ────

    /**
     * Mirrors Skia's `SkImage::makeSubset(SkRecorder*, const SkIRect&, RequiredProperties)`
     * — returns a new [SkImage] viewing the rectangular sub-region [subset]
     * of this image.
     *
     * Returns `null` if any of the upstream "bad subset" conditions are
     * hit (`SkImage.h:760-765`) :
     *  - [subset] is empty (`width <= 0 || height <= 0`).
     *  - [subset] is not contained within `[0, 0, width, height]`.
     *
     * The returned image owns a freshly-allocated [IntArray] containing
     * only the sub-rect's pixels (no aliasing with `this.pixels`) ; the
     * [colorType] and [colorSpace] of `this` carry through. Mip pyramids
     * are not propagated — the subset is a fresh raster image with no
     * pre-built levels (matches upstream's `RequiredProperties{false}`
     * default in the GM call sites).
     */
    public fun makeSubset(subset: SkIRect): SkImage? {
        if (subset.isEmpty) return null
        val bounds = SkIRect.MakeWH(width, height)
        if (!bounds.contains(subset)) return null
        val sw = subset.width()
        val sh = subset.height()
        val out = IntArray(sw * sh)
        for (y in 0 until sh) {
            val srcRow = (subset.top + y) * width + subset.left
            System.arraycopy(pixels, srcRow, out, y * sw, sw)
        }
        return SkImage(sw, sh, out, colorType, colorSpace)
    }

    /**
     * Mirrors Skia's `SkImage::makeScaled(SkRecorder*, const SkImageInfo&, const SkSamplingOptions&)`.
     *
     * Returns a rescaled version of this image with the geometry and colour
     * type described by [info], using [sampling] to control filter quality.
     * Returns `null` if the image cannot be rescaled (empty source / target).
     *
     * The raster implementation routes through [SkPixmap.scalePixels].
     * [SkImage] stores pixels as non-premultiplied ARGB ints, so the source
     * pixmap is materialised as a temporary RGBA_8888 byte buffer before
     * scaling into a destination pixmap described by [info].
     */
    public fun makeScaled(
        info: SkImageInfo,
        sampling: SkSamplingOptions,
    ): SkImage? {
        if (width <= 0 || height <= 0 || info.isEmpty()) return null
        if (info.colorType !in scalablePixmapColorTypes) return null

        val srcInfo = SkImageInfo.Make(
            width,
            height,
            SkColorType.kRGBA_8888,
            SkAlphaType.kUnpremul,
            colorSpace,
        )
        val srcPixels = ByteBuffer.allocate(srcInfo.minRowBytes() * height).order(ByteOrder.LITTLE_ENDIAN)
        for (pixel in pixels) {
            srcPixels.put((SkColorGetR(pixel) and 0xFF).toByte())
            srcPixels.put((SkColorGetG(pixel) and 0xFF).toByte())
            srcPixels.put((SkColorGetB(pixel) and 0xFF).toByte())
            srcPixels.put((SkColorGetA(pixel) and 0xFF).toByte())
        }
        srcPixels.rewind()

        val dstPixels = ByteBuffer.allocate(info.minRowBytes() * info.height).order(ByteOrder.LITTLE_ENDIAN)
        val srcPixmap = SkPixmap(srcInfo, srcPixels, srcInfo.minRowBytes())
        val dstPixmap = SkPixmap(info, dstPixels, info.minRowBytes())
        if (!srcPixmap.scalePixels(dstPixmap, sampling)) return null
        return SkImages.RasterFromPixmapCopy(dstPixmap)
    }

    private val scalablePixmapColorTypes: Set<SkColorType> = setOf(
        SkColorType.kAlpha_8,
        SkColorType.kARGB_4444,
        SkColorType.kRGBA_8888,
        SkColorType.kBGRA_8888,
    )

    /**
     * Mirrors Skia's `SkImage::makeColorSpace(SkRecorder*, sk_sp<SkColorSpace>, RequiredProperties)`
     * — returns a new image whose pixels have been converted from
     * `this.colorSpace` to [target].
     *
     * Returns the original image (no-op) when the source and target
     * colour spaces have the same hash (matches upstream's "Returns
     * original SkImage if it is in target SkColorSpace" contract,
     * `SkImage.h:862`).
     *
     * The conversion is performed in non-premultiplied linear space via
     * [org.skia.core.SkColorSpaceXformSteps]. For R2.12 the implementation
     * is exercised end-to-end against the three colour spaces the GM
     * harness needs : sRGB, Display P3, and Rec.2020 ; any other
     * combination still routes through the generic xform steps and
     * should behave correctly, but is uncovered by tests in this phase.
     *
     * Returns `null` only if [target] is structurally invalid (deferred
     * to the xform pipeline). The returned image carries the requested
     * [target] colour space.
     */
    public fun makeColorSpace(target: SkColorSpace): SkImage? {
        if (colorSpace.hash() == target.hash()) return this
        val steps = org.skia.core.SkColorSpaceXformSteps(
            colorSpace,
            org.skia.core.SkAlphaType.kUnpremul,
            target,
            org.skia.core.SkAlphaType.kUnpremul,
        )
        val out = IntArray(width * height)
        val rgba = FloatArray(4)
        for (i in pixels.indices) {
            val c = pixels[i]
            val a = SkColorGetA(c) / 255f
            val r = SkColorGetR(c) / 255f
            val g = SkColorGetG(c) / 255f
            val b = SkColorGetB(c) / 255f
            rgba[0] = r; rgba[1] = g; rgba[2] = b; rgba[3] = a
            steps.apply(rgba)
            val ai = (a * 255f + 0.5f).toInt().coerceIn(0, 255)
            val ri = (rgba[0] * 255f + 0.5f).toInt().coerceIn(0, 255)
            val gi = (rgba[1] * 255f + 0.5f).toInt().coerceIn(0, 255)
            val bi = (rgba[2] * 255f + 0.5f).toInt().coerceIn(0, 255)
            out[i] = SkColorSetARGB(ai, ri, gi, bi)
        }
        return SkImage(width, height, out, colorType, target)
    }

    /**
     * Mirrors Skia's `SkImage::reinterpretColorSpace(sk_sp<SkColorSpace>)`.
     *
     * Returns a new [SkImage] that shares the same underlying pixels as
     * `this` but is tagged with [newColorSpace] instead of the original
     * colour space. No pixel data is converted — the raw bytes are
     * reinterpreted as-is in the new space.
     *
     * This is a metadata-only operation. The returned image has a fresh
     * wrapper and the same immutable pixel buffer, so callers can compose
     * it with [makeColorSpace] without an extra conversion pass.
     */
    public fun reinterpretColorSpace(newColorSpace: SkColorSpace): SkImage =
        if (colorSpace.hash() == newColorSpace.hash()) this
        else SkImage(width, height, pixels, colorType, newColorSpace, mipLevels)

    /**
     * Mirrors Skia's `SkImage::makeColorTypeAndColorSpace(SkRecorder*,
     * SkColorType, sk_sp<SkColorSpace>, RequiredProperties)`.
     *
     * Returns a new [SkImage] with both the colour type and colour space
     * changed simultaneously. The internal storage remains 8888 for the
     * raster backend, but pixels are quantized through the requested
     * [newColorType] so drawing observes the same precision loss as the
     * source colour type. The conversion path is intentionally scoped to
     * the colour types the GM suite exercises today.
     */
    public fun makeColorTypeAndColorSpace(
        newColorType: SkColorType,
        newColorSpace: SkColorSpace,
    ): SkImage? {
        if (newColorType == SkColorType.kUnknown) return null
        val converted = makeColorSpace(newColorSpace) ?: return null
        if (converted.colorType == newColorType) return converted

        val out = IntArray(width * height)
        for (i in converted.pixels.indices) {
            out[i] = quantizeToColorType(converted.pixels[i], newColorType)
        }
        return SkImage(width, height, out, newColorType, newColorSpace)
    }

    /**
     * Mirrors Skia's `SkImage::makeRasterImage(SkRecorder*)` — on the GPU
     * path this reads back the texture into a CPU-backed raster image.
     *
     * On the kanvas-skia CPU raster backend every [SkImage] is already a
     * raster image, so this is a no-op returning `this`.
     */
    public fun makeRasterImage(): SkImage = this

    /**
     * Mirrors Skia's `SkImage::encodeToData()` convenience entry point
     * (defaults to PNG). Returns the encoded bytes wrapped in [SkData],
     * or `null` on encoder failure.
     *
     * PNG is the default lossless choice. For lossy encodings (JPEG)
     * use the [encodeToData] overload that takes an explicit format and
     * quality.
     */
    public fun encodeToData(): SkData? =
        encodeToData(SkEncodedImageFormat.kPNG, quality = 100)

    /**
     * Mirrors Skia's `SkImage::encodeToData(format, quality)`.
     *
     * Dispatch table :
     *  - [SkEncodedImageFormat.kPNG] : delegates to
     *    [org.graphiks.kanvas.codec.png.PngEncoder]. The [quality] argument is
     *    ignored (PNG is lossless).
     *  - [SkEncodedImageFormat.kJPEG] : delegates to
     *    [org.skia.encode.SkJpegEncoder] with the supplied [quality].
     *  - Every other format returns `null` — encoders for WEBP / GIF /
     *    BMP / WBMP / HEIF / AVIF / JPEGXL are out of scope. Use the
     *    per-format encoder directly when a richer surface is needed.
     *
     * The returned bytes encode a snapshot of this image's pixels
     * tagged sRGB ; non-sRGB working spaces lose their tag through the
     * encode (PNG `iCCP` chunk is not emitted by the underlying
     * pure Kotlin writer — tracked as a follow-up).
     */
    public fun encodeToData(format: SkEncodedImageFormat, quality: Int): SkData? {
        val bytes = org.skia.encode.encodeImageToBytes(this, format, quality) ?: return null
        return SkData.MakeWithCopy(bytes)
    }

    /**
     * Mirrors Skia's `SkImage::readPixels(const SkImageInfo&, void*, size_t, int, int)`.
     *
     * Copies a `dstInfo.width × dstInfo.height` block from `(srcX, srcY)`
     * of this image into [dstPixels], honouring the destination
     * [dstInfo]'s colour type and row stride. Negative `srcX` / `srcY`
     * skip the leading rows / columns (matches upstream — Skia uses
     * these to select a *sub-rect of the destination*).
     *
     * Returns `false` (and leaves [dstPixels] untouched) if any of the
     * upstream conditions are violated :
     *  - destination row-bytes are less than `dstInfo.minRowBytes()`.
     *  - destination colour type is `kUnknown`.
     *  - source image is empty.
     *  - the source and destination rectangles do not overlap.
     *
     * Colour-space conversion is performed when `this.colorSpace` and
     * `dstInfo.colorSpace` differ, via [SkColorSpaceXformSteps]. Pixel
     * format conversion (8888 → 4444 / Alpha8 / 565 / Gray8 / BGRA…) is
     * delegated to a temporary [SkPixmap] backed by [dstPixels].
     */
    public fun readPixels(
        dstInfo: SkImageInfo,
        dstPixels: ByteBuffer,
        dstRowBytes: Int,
        srcX: Int = 0,
        srcY: Int = 0,
    ): Boolean {
        if (dstInfo.colorType == SkColorType.kUnknown) return false
        if (dstInfo.isEmpty()) return false
        if (dstRowBytes < dstInfo.minRowBytes()) return false
        if (width <= 0 || height <= 0) return false
        if (srcX >= width || srcY >= height) return false
        if (srcX + dstInfo.width <= 0 || srcY + dstInfo.height <= 0) return false

        // Wrap the destination in a temporary pixmap so the per-pixel
        // format conversion logic lives in one place (SkPixmap.writePixel).
        val dstPixmap = SkPixmap(dstInfo, dstPixels, dstRowBytes)
        return readPixels(dstPixmap, srcX, srcY)
    }

    /**
     * Mirrors Skia's `SkImage::readPixels(const SkPixmap&, int, int)`.
     *
     * Pixmap overload — the destination's [SkPixmap.info] / [SkPixmap.addr] /
     * [SkPixmap.rowBytes] together describe the receiving buffer.
     * Colour-space conversion is honoured when `this.colorSpace` and
     * `dst.colorSpace()` differ ; format conversion is honoured for
     * every colour type [SkPixmap] supports (`kAlpha_8`, `kARGB_4444`,
     * `kRGBA_8888`, `kBGRA_8888`). [SkColorType.kRGB_565] and
     * [SkColorType.kGray_8] aren't wired up at the pixmap level yet —
     * callers needing them should use the [SkImageInfo] overload only
     * for the four core types.
     */
    public fun readPixels(dst: SkPixmap, srcX: Int = 0, srcY: Int = 0): Boolean {
        if (dst.colorType() == SkColorType.kUnknown) return false
        if (width <= 0 || height <= 0 || dst.width() <= 0 || dst.height() <= 0) return false
        if (srcX >= width || srcY >= height) return false
        if (srcX + dst.width() <= 0 || srcY + dst.height() <= 0) return false

        val srcL = maxOf(srcX, 0)
        val srcT = maxOf(srcY, 0)
        val srcR = minOf(width, srcX + dst.width())
        val srcB = minOf(height, srcY + dst.height())
        if (srcL >= srcR || srcT >= srcB) return false

        // Set up colour-space conversion when source and dst tags differ.
        // The destination's colour space is allowed to be `null` upstream
        // ("If SkImage SkColorSpace is nullptr, dst.colorSpace() must
        // match"), but kanvas-skia's [SkColorSpace] is never null — the
        // pixmap defaults to sRGB. Use the hash to short-circuit identity.
        val dstColorSpace = dst.colorSpace() ?: SkColorSpace.makeSRGB()
        val xform = if (colorSpace.hash() != dstColorSpace.hash()) {
            org.skia.core.SkColorSpaceXformSteps(
                colorSpace,
                org.skia.core.SkAlphaType.kUnpremul,
                dstColorSpace,
                when (dst.alphaType()) {
                    SkAlphaType.kPremul -> org.skia.core.SkAlphaType.kPremul
                    SkAlphaType.kOpaque -> org.skia.core.SkAlphaType.kOpaque
                    SkAlphaType.kUnpremul -> org.skia.core.SkAlphaType.kUnpremul
                    SkAlphaType.kUnknown -> org.skia.core.SkAlphaType.kUnknown
                },
            )
        } else {
            null
        }

        // We need write access — SkPixmap.writePixel is private, but its
        // public surface includes `erase`. We instead reach the writer
        // through a tiny ByteBuffer-poking helper that mirrors the
        // colour-type switch SkPixmap uses internally.
        for (sy in srcT until srcB) {
            for (sx in srcL until srcR) {
                var c = pixels[sy * width + sx]
                if (xform != null) {
                    val rgba = SCRATCH_RGBA.get()
                    rgba[0] = SkColorGetR(c) / 255f
                    rgba[1] = SkColorGetG(c) / 255f
                    rgba[2] = SkColorGetB(c) / 255f
                    rgba[3] = SkColorGetA(c) / 255f
                    xform.apply(rgba)
                    val ai = (rgba[3] * 255f + 0.5f).toInt().coerceIn(0, 255)
                    val ri = (rgba[0] * 255f + 0.5f).toInt().coerceIn(0, 255)
                    val gi = (rgba[1] * 255f + 0.5f).toInt().coerceIn(0, 255)
                    val bi = (rgba[2] * 255f + 0.5f).toInt().coerceIn(0, 255)
                    c = SkColorSetARGB(ai, ri, gi, bi)
                }
                writePixmapPixel(dst, sx - srcX, sy - srcY, c)
            }
        }
        return true
    }

    public companion object {
        /**
         * Snapshot the bitmap into a new immutable [SkImage]. The pixel
         * buffer is copied — subsequent edits to the source bitmap don't
         * leak into the image.
         *
         * The snapshot is always 8888 (Skia's `SkSurface::makeImageSnapshot()`
         * default contract — the snapshot's working precision is
         * implementation-defined; we pick 8888 to keep the [SkImage]
         * surface uniform and to match what every consumer downstream
         * ([SkBitmapShader] in particular) reads from `image.pixels`).
         * For F16 source bitmaps this means a per-pixel quantization
         * via [SkBitmap.getPixel] — same path the live `bitmap.getPixel`
         * accessor takes, so a snapshot stays bit-identical to the
         * live `bitmap.getPixel(x, y)` view.
         */
        public fun Make(bitmap: SkBitmap): SkImage {
            val w = bitmap.width
            val h = bitmap.height
            val out = when (bitmap.colorType) {
                SkColorType.kRGBA_8888 -> bitmap.pixels.copyOf()
                else -> {
                    // Walk via the colorType-aware accessor so F16 / Alpha8 /
                    // ARGB_4444 (and any future colorType) snapshots correctly
                    // through to 8888.
                    val a = IntArray(w * h)
                    for (y in 0 until h) {
                        for (x in 0 until w) a[y * w + x] = bitmap.getPixel(x, y)
                    }
                    a
                }
            }
            return SkImage(w, h, out, bitmap.colorType, bitmap.colorSpace)
        }

        /**
         * Mirrors Skia's `SkImage::MakeFromYUVAPixmaps`.
         *
         * Raster-only bridge: we materialize an RGBA image through
         * [SkImages.YUVA]. GPU texture/image-backed variants remain out of
         * scope and are covered by separate `SkImages.TextureFrom*` stubs.
         */
        @Suppress("FunctionName")
        public fun MakeFromYUVAPixmaps(pixmaps: SkYUVAPixmaps): SkImage? =
            SkImages.YUVA(pixmaps)

        // ─── Phase R2.12 — readPixels internals ───────────────────────

        /**
         * Per-thread scratch float vector used by [readPixels] when a
         * colour-space transform is active. ThreadLocal keeps the
         * read path allocation-free under repeated calls without
         * compromising thread-safety.
         */
        private val SCRATCH_RGBA: ThreadLocal<FloatArray> =
            ThreadLocal.withInitial { FloatArray(4) }

        private fun quantizeToColorType(c: SkColor, colorType: SkColorType): SkColor {
            val a = SkColorGetA(c)
            val r = SkColorGetR(c)
            val g = SkColorGetG(c)
            val b = SkColorGetB(c)
            return when (colorType) {
                SkColorType.kRGB_565 -> SkBitmap.unpackRGB565(
                    SkBitmap.packRGB565(r / 255f, g / 255f, b / 255f),
                )
                SkColorType.kGray_8 -> {
                    val y = (r * 0.299f + g * 0.587f + b * 0.114f + 0.5f)
                        .toInt()
                        .coerceIn(0, 255)
                    SkColorSetARGB(0xFF, y, y, y)
                }
                SkColorType.kAlpha_8 -> SkColorSetARGB(a, 0, 0, 0)
                SkColorType.kARGB_4444 -> SkBitmap.unpackARGB4444Premul(
                    SkBitmap.packARGB4444Premul(a / 255f, r / 255f, g / 255f, b / 255f),
                )
                SkColorType.kRGB_888x -> SkColorSetARGB(0xFF, r, g, b)
                SkColorType.kRGBA_8888,
                SkColorType.kBGRA_8888,
                SkColorType.kSRGBA_8888 -> c
                else -> c
            }
        }

        /**
         * Write a single non-premultiplied 8-bit ARGB [SkColor] at
         * `(x, y)` of [dst]. Mirrors the private
         * `SkPixmap.writePixel` switch — kept in sync with the
         * pixmap's supported colour types (`kAlpha_8`, `kARGB_4444`,
         * `kRGBA_8888`, `kBGRA_8888`). For [SkColorType.kRGB_565] and
         * [SkColorType.kGray_8] the call falls back to a no-op
         * (matches the upstream "incompatible colour-type pairing →
         * skip" behaviour for the R2.12-supported destinations) ;
         * future work can extend this when a GM needs 565 / Gray8
         * `readPixels` destinations.
         */
        private fun writePixmapPixel(dst: SkPixmap, x: Int, y: Int, c: SkColor) {
            val info = dst.info()
            val bpp = info.bytesPerPixel()
            val rowBytes = dst.rowBytes()
            val offset = y * rowBytes + x * bpp
            val buf: ByteBuffer = dst.addr()
            val a = SkColorGetA(c)
            val r = SkColorGetR(c)
            val g = SkColorGetG(c)
            val b = SkColorGetB(c)
            when (info.colorType) {
                SkColorType.kAlpha_8 -> buf.put(offset, a.toByte())
                SkColorType.kRGBA_8888 -> {
                    buf.put(offset, r.toByte())
                    buf.put(offset + 1, g.toByte())
                    buf.put(offset + 2, b.toByte())
                    buf.put(offset + 3, a.toByte())
                }
                SkColorType.kBGRA_8888 -> {
                    buf.put(offset, b.toByte())
                    buf.put(offset + 1, g.toByte())
                    buf.put(offset + 2, r.toByte())
                    buf.put(offset + 3, a.toByte())
                }
                SkColorType.kARGB_4444 -> {
                    val packed = SkBitmap.packARGB4444Premul(
                        a / 255f, r / 255f, g / 255f, b / 255f,
                    )
                    buf.putShort(offset, packed)
                }
                SkColorType.kRGB_565 -> {
                    val packed = SkBitmap.packRGB565(r / 255f, g / 255f, b / 255f)
                    buf.putShort(offset, packed)
                }
                SkColorType.kGray_8 -> {
                    // Rec.601 luma weights — mirrors SkBitmap.eraseColor
                    // for kGray_8 and Skia's `SkColorToLuminance`.
                    val ly = (r * 0.299f + g * 0.587f + b * 0.114f)
                        .coerceIn(0f, 255f)
                    buf.put(offset, (ly + 0.5f).toInt().coerceIn(0, 255).toByte())
                }
                else -> {
                    // Unsupported destination colour type — leave the
                    // buffer untouched. Matches the upstream contract
                    // for incompatible colour-type pairings on the
                    // legacy `readPixels` path.
                }
            }
        }
    }
}
