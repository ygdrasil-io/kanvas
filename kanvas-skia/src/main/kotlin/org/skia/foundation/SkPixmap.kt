package org.skia.foundation

import org.skia.math.SkIRect
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
        if (kotlin.math.abs(srcX) >= width() || kotlin.math.abs(srcY) >= height()) return false
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
     * Performs a nearest-neighbour resample from `this` to [dst]. The
     * upstream implementation honours [sampling] (linear, mipmap,
     * cubic) ; kanvas-skia exposes the same surface but falls back to
     * nearest for the moment — sufficient for the only consumer
     * ([SkImageGenerator.getPixels] when the destination info differs
     * in size) and easy to upgrade later without an API break.
     */
    public fun scalePixels(dst: SkPixmap, sampling: SkSamplingOptions): Boolean {
        if (colorType() == SkColorType.kUnknown || dst.colorType() == SkColorType.kUnknown) return false
        val sw = width(); val sh = height()
        val dw = dst.width(); val dh = dst.height()
        if (sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0) return false
        // Nearest-neighbour fallback (sampling parameter accepted but
        // ignored — see KDoc). Map dst-centre → src-centre, then round.
        for (dy in 0 until dh) {
            // sy = ((dy + 0.5) * sh / dh) - 0.5 ; clamp to source bounds.
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
