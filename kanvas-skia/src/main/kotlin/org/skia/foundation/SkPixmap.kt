package org.skia.foundation


import org.graphiks.math.SkColor
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkIRect
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Mirrors Skia's [`SkPixmap`](https://github.com/google/skia/blob/main/include/core/SkPixmap.h)
 * — a non-owning view that pairs an [SkImageInfo] with a pixel buffer
 * and a row-byte stride. Where [SkBitmap] owns its backing storage and
 * exposes Kotlin-friendly typed arrays, [SkPixmap] is the byte-level
 * counterpart used by the more plumbing-oriented Skia APIs
 * ([SkImageGenerator.getPixels], `SkImage::readPixels`, …).
 *
 * The buffer is a [ByteBuffer] :
 *  - little-endian (matches the on-disk layout described by
 *    [SkColorType] — kanvas-skia's bitmap storage is also LE-host
 *    Pascal-Argb for the 32-bit colour types, so the byte order of
 *    in-buffer 32-bit pixels is `B, G, R, A` for `kRGBA_8888` on a
 *    little-endian JVM);
 *  - non-owning : when this pixmap is mutated via [reset] or goes out
 *    of scope the buffer's lifetime is the caller's responsibility, no
 *    different from upstream's `const void* fPixels` semantics.
 *
 * **Not a SkBitmap replacement** — the raster pipeline still operates
 * on [SkBitmap]. The pixmap is a *view* used to read / write raw bytes
 * across the API surface.
 */
public class SkPixmap {
    private var _info: SkImageInfo = SkImageInfo.Make(0, 0, SkColorType.kUnknown, SkAlphaType.kUnknown)
    private var _addr: ByteBuffer = EMPTY_BUFFER
    private var _rowBytes: Int = 0

    /** Empty constructor — matches upstream's default `SkPixmap()`. */
    public constructor()

    /**
     * Mirrors `SkPixmap(const SkImageInfo&, const void*, size_t rowBytes)`.
     * No defensive validation is performed beyond row-byte sanity (a
     * non-empty info requires `rowBytes >= info.minRowBytes()`).
     */
    public constructor(info: SkImageInfo, addr: ByteBuffer, rowBytes: Int) {
        reset(info, addr, rowBytes)
    }

    /**
     * Mirrors `SkPixmap::reset()` — clears info, addr, and rowBytes.
     * The previously-referenced bytes are unaffected.
     */
    public fun reset() {
        _info = SkImageInfo.Make(0, 0, SkColorType.kUnknown, SkAlphaType.kUnknown)
        _addr = EMPTY_BUFFER
        _rowBytes = 0
    }

    /**
     * Mirrors `SkPixmap::reset(const SkImageInfo&, const void*, size_t)`.
     * Validates the row-byte stride for non-empty infos.
     */
    public fun reset(info: SkImageInfo, addr: ByteBuffer, rowBytes: Int) {
        if (!info.isEmpty()) {
            require(rowBytes >= info.minRowBytes()) {
                "rowBytes=$rowBytes < minRowBytes=${info.minRowBytes()} for $info"
            }
        }
        _info = info
        // Always view the buffer in little-endian — kanvas-skia stores
        // 32-bit pixels in host-LE Pascal-Argb (see SkBitmap.pixelsBGRA8888
        // KDoc) and the upstream `void*` is opaque, so consumers expect
        // host byte order.
        _addr = addr.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        _rowBytes = rowBytes
    }

    public fun info(): SkImageInfo = _info
    public fun rowBytes(): Int = _rowBytes

    /**
     * Returns the underlying [ByteBuffer], positioned at 0. The returned
     * buffer is a *duplicate* (independent position / limit / mark) over
     * the same bytes — mutating the bytes mutates the pixmap, but moving
     * the buffer cursor does not affect any future [addr] call.
     */
    public fun addr(): ByteBuffer = _addr.duplicate().order(ByteOrder.LITTLE_ENDIAN).also { it.position(0) }

    public fun width(): Int = _info.width
    public fun height(): Int = _info.height
    public fun colorType(): SkColorType = _info.colorType
    public fun alphaType(): SkAlphaType = _info.alphaType
    public fun colorSpace(): SkColorSpace? = _info.colorSpace
    public fun bounds(): SkIRect = SkIRect.MakeWH(width(), height())

    /** Mirrors `SkPixmap::computeByteSize()`. */
    public fun computeByteSize(): Long {
        if (height() == 0 || width() == 0) return 0L
        // Last row uses `width * bpp` bytes; preceding rows use full
        // `rowBytes`. Matches Skia's `SkImageInfo::computeByteSize`.
        val bpp = _info.bytesPerPixel()
        return (height() - 1).toLong() * _rowBytes + width().toLong() * bpp
    }

    /**
     * Mirrors `SkPixmap::extractSubset(SkPixmap* subset, const SkIRect& area)`.
     *
     * Sets `subset`'s info / addr / rowBytes to the intersection of `area`
     * with this pixmap's bounds. Returns `false` if the intersection is
     * empty.
     */
    public fun extractSubset(subset: SkPixmap, area: SkIRect): Boolean {
        val bounds = this.bounds()
        val isect = SkIRect.MakeLTRB(bounds.left, bounds.top, bounds.right, bounds.bottom)
        if (!isect.intersect(area)) return false
        // Slice into the byte buffer at the (isect.left, isect.top) offset.
        val bpp = _info.bytesPerPixel()
        val offset = isect.top * _rowBytes + isect.left * bpp
        val newInfo = _info.makeWH(isect.width(), isect.height())
        val sliced = _addr.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        sliced.position(offset)
        // The subset shares the parent rowBytes — successive rows still
        // step by rowBytes in the parent buffer.
        subset.reset(newInfo, sliced.slice().order(ByteOrder.LITTLE_ENDIAN), _rowBytes)
        return true
    }

    /**
     * Mirrors `SkPixmap::erase(SkColor, const SkIRect&)`. Writes the
     * non-premultiplied sRGB [color] into every pixel covered by [subset]
     * (defaults to [bounds] when `null`). Returns `false` if the colour
     * type is [SkColorType.kUnknown] or the subset doesn't intersect
     * [bounds].
     */
    public fun erase(color: SkColor, subset: SkIRect? = null): Boolean {
        if (colorType() == SkColorType.kUnknown) return false
        val target = if (subset == null) bounds() else {
            val s = SkIRect.MakeLTRB(subset.left, subset.top, subset.right, subset.bottom)
            if (!s.intersect(bounds())) return false
            s
        }
        if (target.isEmpty) return false
        for (y in target.top until target.bottom) {
            for (x in target.left until target.right) {
                writePixel(x, y, color)
            }
        }
        return true
    }

    /**
     * Mirrors `SkPixmap::readPixels(const SkPixmap& dst, int srcX, int srcY)`.
     *
     * Copies a `dst.width() × dst.height()` block from `(srcX, srcY)` of
     * `this` into [dst]. Colour conversion across [SkColorType] is
     * supported for the colour types kanvas-skia rasterises (`kAlpha_8`,
     * `kARGB_4444`, `kRGBA_8888`, `kBGRA_8888`). Source / destination
     * pixels are exchanged as non-premultiplied 8-bit ARGB via the
     * shared [readPixel] / [writePixel] path; colour-space matching is
     * the caller's responsibility (matches upstream — `readPixels` doesn't
     * cross-convert spaces here).
     */
    public fun readPixels(dst: SkPixmap, srcX: Int = 0, srcY: Int = 0): Boolean {
        if (colorType() == SkColorType.kUnknown || dst.colorType() == SkColorType.kUnknown) return false
        if (width() <= 0 || height() <= 0 || dst.width() <= 0 || dst.height() <= 0) return false
        if (srcX >= width() || srcY >= height()) return false
        if (srcX + dst.width() <= 0 || srcY + dst.height() <= 0) return false
        // Compute the overlap rect in source coords.
        val srcL = maxOf(srcX, 0)
        val srcT = maxOf(srcY, 0)
        val srcR = minOf(width(), srcX + dst.width())
        val srcB = minOf(height(), srcY + dst.height())
        if (srcL >= srcR || srcT >= srcB) return false
        for (sy in srcT until srcB) {
            for (sx in srcL until srcR) {
                val c = readPixel(sx, sy)
                dst.writePixel(sx - srcX, sy - srcY, c)
            }
        }
        return true
    }

    /**
     * Mirrors `SkPixmap::scalePixels(const SkPixmap& dst, const SkSamplingOptions&)`.
     *
     * Resamples `this` into [dst] honouring [sampling] :
     *  - `sampling.cubic != null` → bicubic Mitchell-Netravali kernel
     *    using [SkCubicBC] with the `(B, C)` from
     *    [SkSamplingOptions.cubic]. Matches upstream's `useCubic`
     *    branch (cubic takes precedence over filter/mipmap).
     *  - `sampling.filter == SkFilterMode.kLinear` → bilinear blend
     *    of the four texels surrounding the sub-pixel sample.
     *  - otherwise → nearest-neighbour (Skia's default sampling).
     *
     * The [SkSamplingOptions.mipmap] and [SkSamplingOptions.maxAniso]
     * fields are ignored — pixmaps are single-level, mipmaps and
     * anisotropy are a higher-level texture concept.
     *
     * Sub-pixel sampling uses Skia's "half-pixel centre" convention :
     * destination texel centre `(dx + 0.5)` maps to source coordinate
     * `((dx + 0.5) * sw / dw)`, then re-centred by subtracting `0.5`
     * to land on a texel grid. Out-of-bounds samples clamp to the
     * edge (matches upstream's `SkTileMode::kClamp` default for
     * `scalePixels`).
     */
    public fun scalePixels(dst: SkPixmap, sampling: SkSamplingOptions): Boolean {
        if (colorType() == SkColorType.kUnknown || dst.colorType() == SkColorType.kUnknown) return false
        val sw = width(); val sh = height()
        val dw = dst.width(); val dh = dst.height()
        if (sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0) return false
        return when {
            sampling.cubic != null -> scaleCubic(dst, sampling.cubic)
            sampling.filter == SkFilterMode.kLinear -> scaleBilinear(dst)
            else -> scaleNearest(dst)
        }
    }

    /**
     * Nearest-neighbour resample — Skia's default when
     * `SkSamplingOptions()` is used with no arguments. Map
     * destination texel-centre to source coords and round.
     */
    private fun scaleNearest(dst: SkPixmap): Boolean {
        val sw = width(); val sh = height()
        val dw = dst.width(); val dh = dst.height()
        for (dy in 0 until dh) {
            val syF = ((dy + 0.5) * sh / dh) - 0.5
            val sy = syF.toInt().coerceIn(0, sh - 1)
            for (dx in 0 until dw) {
                val sxF = ((dx + 0.5) * sw / dw) - 0.5
                val sx = sxF.toInt().coerceIn(0, sw - 1)
                dst.writePixel(dx, dy, readPixel(sx, sy))
            }
        }
        return true
    }

    /**
     * Bilinear resample — 4-tap blend of the texels surrounding the
     * sub-pixel source coordinate. The sub-pixel position uses the
     * half-pixel centre convention (matches Skia's
     * `SkBitmapProcState_matrix.h` bilinear path).
     */
    private fun scaleBilinear(dst: SkPixmap): Boolean {
        val sw = width(); val sh = height()
        val dw = dst.width(); val dh = dst.height()
        for (dy in 0 until dh) {
            // Source y-coordinate of destination centre, re-centred.
            val syF = ((dy + 0.5) * sh / dh) - 0.5
            val syFloor = kotlin.math.floor(syF)
            val sy0Raw = syFloor.toInt()
            val sy1Raw = sy0Raw + 1
            val sy0 = sy0Raw.coerceIn(0, sh - 1)
            val sy1 = sy1Raw.coerceIn(0, sh - 1)
            // When the sample falls outside [0, sh-1], both texels
            // clamp to the same edge — ty becomes irrelevant. Keep
            // the raw fractional offset for in-range samples.
            val ty = (syF - syFloor).toFloat().coerceIn(0f, 1f)
            for (dx in 0 until dw) {
                val sxF = ((dx + 0.5) * sw / dw) - 0.5
                val sxFloor = kotlin.math.floor(sxF)
                val sx0Raw = sxFloor.toInt()
                val sx1Raw = sx0Raw + 1
                val sx0 = sx0Raw.coerceIn(0, sw - 1)
                val sx1 = sx1Raw.coerceIn(0, sw - 1)
                val tx = (sxF - sxFloor).toFloat().coerceIn(0f, 1f)
                val c00 = readPixel(sx0, sy0)
                val c10 = readPixel(sx1, sy0)
                val c01 = readPixel(sx0, sy1)
                val c11 = readPixel(sx1, sy1)
                dst.writePixel(dx, dy, lerp2D(c00, c10, c01, c11, tx, ty))
            }
        }
        return true
    }

    /**
     * Bicubic resample — 16-tap (4×4) Mitchell-Netravali kernel using
     * [SkCubicBC.weight]. The `(B, C)` parameters select a member of
     * the cubic family (Mitchell, Catmull-Rom, …) ; see
     * [SkCubicResampler]. Out-of-grid taps clamp to the nearest edge
     * texel (Skia's default tile mode for `scalePixels`).
     */
    private fun scaleCubic(dst: SkPixmap, cubic: SkCubicResampler): Boolean {
        val sw = width(); val sh = height()
        val dw = dst.width(); val dh = dst.height()
        val B = cubic.B; val C = cubic.C
        for (dy in 0 until dh) {
            val syF = ((dy + 0.5) * sh / dh) - 0.5
            val syBase = kotlin.math.floor(syF).toInt()
            val fy = (syF - syBase).toFloat()
            // Weights for the 4 rows at offsets -1, 0, 1, 2 from syBase.
            val wy0 = SkCubicBC.weight(1f + fy, B, C)
            val wy1 = SkCubicBC.weight(fy, B, C)
            val wy2 = SkCubicBC.weight(1f - fy, B, C)
            val wy3 = SkCubicBC.weight(2f - fy, B, C)
            for (dx in 0 until dw) {
                val sxF = ((dx + 0.5) * sw / dw) - 0.5
                val sxBase = kotlin.math.floor(sxF).toInt()
                val fx = (sxF - sxBase).toFloat()
                val wx0 = SkCubicBC.weight(1f + fx, B, C)
                val wx1 = SkCubicBC.weight(fx, B, C)
                val wx2 = SkCubicBC.weight(1f - fx, B, C)
                val wx3 = SkCubicBC.weight(2f - fx, B, C)
                // Accumulate as floats in non-premul ARGB. Cubic
                // weights can overshoot [0, 1] (Catmull-Rom), so
                // clamp on write-back.
                var a = 0f; var r = 0f; var g = 0f; var b = 0f
                for (j in 0..3) {
                    val sy = (syBase + j - 1).coerceIn(0, sh - 1)
                    val wy = when (j) { 0 -> wy0; 1 -> wy1; 2 -> wy2; else -> wy3 }
                    for (i in 0..3) {
                        val sx = (sxBase + i - 1).coerceIn(0, sw - 1)
                        val wx = when (i) { 0 -> wx0; 1 -> wx1; 2 -> wx2; else -> wx3 }
                        val w = wx * wy
                        val c = readPixel(sx, sy)
                        a += SkColorGetA(c) * w
                        r += SkColorGetR(c) * w
                        g += SkColorGetG(c) * w
                        b += SkColorGetB(c) * w
                    }
                }
                // Round-half-up before truncation so 254.985 lands
                // on 255, not 254 (Mitchell weights sum to 1.0 only
                // within float precision — a constant-colour source
                // must round-trip exactly).
                val ai = (a + 0.5f).toInt().coerceIn(0, 255)
                val ri = (r + 0.5f).toInt().coerceIn(0, 255)
                val gi = (g + 0.5f).toInt().coerceIn(0, 255)
                val bi = (b + 0.5f).toInt().coerceIn(0, 255)
                dst.writePixel(dx, dy, SkColorSetARGB(ai, ri, gi, bi))
            }
        }
        return true
    }

    /**
     * Bilinear interpolation of four ARGB pixels arranged on a unit
     * square at corners `(0,0)`, `(1,0)`, `(0,1)`, `(1,1)`. Each
     * channel is interpolated independently in non-premultiplied
     * space.
     */
    private fun lerp2D(c00: SkColor, c10: SkColor, c01: SkColor, c11: SkColor, tx: Float, ty: Float): SkColor {
        fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t + 0.5f).toInt().coerceIn(0, 255)
        fun blend(get: (SkColor) -> Int): Int {
            val top = lerp(get(c00), get(c10), tx).toFloat()
            val bot = lerp(get(c01), get(c11), tx).toFloat()
            return (top + (bot - top) * ty + 0.5f).toInt().coerceIn(0, 255)
        }
        val a = blend(::SkColorGetA)
        val r = blend(::SkColorGetR)
        val g = blend(::SkColorGetG)
        val b = blend(::SkColorGetB)
        return SkColorSetARGB(a, r, g, b)
    }

    /**
     * Mirrors `SkPixmap::getColor(int x, int y)`. Returns the pixel as a
     * non-premultiplied 8-bit ARGB [SkColor]. Out-of-bounds coords throw
     * — Skia's upstream behaviour is "assert in debug, undefined in
     * release"; we lift the assert to a hard check so the JVM-side
     * contract is well-defined.
     */
    public fun getColor(x: Int, y: Int): SkColor {
        require(x in 0 until width() && y in 0 until height()) {
            "($x, $y) outside ${width()}x${height()}"
        }
        return readPixel(x, y)
    }

    /**
     * Mirrors `SkPixmap::getAlphaf(int x, int y)`. Returns the pixel's
     * alpha as a normalised `[0, 1]` float.
     */
    public fun getAlphaf(x: Int, y: Int): Float {
        require(x in 0 until width() && y in 0 until height()) {
            "($x, $y) outside ${width()}x${height()}"
        }
        return SkColorGetA(readPixel(x, y)) / 255f
    }

    // ─── internal pixel I/O ───────────────────────────────────────────

    /**
     * Read a single pixel as a non-premultiplied 8-bit ARGB [SkColor].
     * Supports the colour types kanvas-skia rasterises; throws for any
     * other type (matches upstream's "unknown → undefined" contract).
     */
    private fun readPixel(x: Int, y: Int): SkColor {
        val bpp = _info.bytesPerPixel()
        val offset = y * _rowBytes + x * bpp
        return when (colorType()) {
            SkColorType.kAlpha_8 -> {
                val a = _addr.get(offset).toInt() and 0xFF
                SkColorSetARGB(a, 0, 0, 0)
            }
            SkColorType.kARGB_4444 -> {
                val s = _addr.getShort(offset).toInt() and 0xFFFF
                SkBitmap.unpackARGB4444Premul(s.toShort())
            }
            SkColorType.kRGBA_8888 -> {
                // In-buffer byte order (little-endian) : R, G, B, A.
                val r = _addr.get(offset).toInt() and 0xFF
                val g = _addr.get(offset + 1).toInt() and 0xFF
                val b = _addr.get(offset + 2).toInt() and 0xFF
                val a = _addr.get(offset + 3).toInt() and 0xFF
                SkColorSetARGB(a, r, g, b)
            }
            SkColorType.kBGRA_8888 -> {
                // In-buffer byte order : B, G, R, A.
                val b = _addr.get(offset).toInt() and 0xFF
                val g = _addr.get(offset + 1).toInt() and 0xFF
                val r = _addr.get(offset + 2).toInt() and 0xFF
                val a = _addr.get(offset + 3).toInt() and 0xFF
                SkColorSetARGB(a, r, g, b)
            }
            else -> error("SkPixmap.readPixel unsupported for colorType=${colorType()}")
        }
    }

    /** Write a non-premultiplied 8-bit ARGB [SkColor] at `(x, y)`. */
    private fun writePixel(x: Int, y: Int, c: SkColor) {
        val bpp = _info.bytesPerPixel()
        val offset = y * _rowBytes + x * bpp
        val a = SkColorGetA(c)
        val r = SkColorGetR(c)
        val g = SkColorGetG(c)
        val b = SkColorGetB(c)
        when (colorType()) {
            SkColorType.kAlpha_8 -> _addr.put(offset, a.toByte())
            SkColorType.kARGB_4444 -> {
                val packed = SkBitmap.packARGB4444Premul(
                    a / 255f, r / 255f, g / 255f, b / 255f,
                )
                _addr.putShort(offset, packed)
            }
            SkColorType.kRGBA_8888 -> {
                _addr.put(offset, r.toByte())
                _addr.put(offset + 1, g.toByte())
                _addr.put(offset + 2, b.toByte())
                _addr.put(offset + 3, a.toByte())
            }
            SkColorType.kBGRA_8888 -> {
                _addr.put(offset, b.toByte())
                _addr.put(offset + 1, g.toByte())
                _addr.put(offset + 2, r.toByte())
                _addr.put(offset + 3, a.toByte())
            }
            else -> error("SkPixmap.writePixel unsupported for colorType=${colorType()}")
        }
    }

    public companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN)
    }
}
