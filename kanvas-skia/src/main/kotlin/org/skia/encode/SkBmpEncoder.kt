package org.skia.encode

import org.skia.foundation.SkBitmap
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.skia.foundation.SkData
import org.skia.foundation.SkImage
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * R-suivi.19 — BMP encoder for kanvas-skia.
 *
 * Skia upstream does **not** ship a BMP encoder (the BMP family in
 * `include/encode/` covers only PNG / JPEG / WebP / Rust-PNG) ;
 * kanvas-skia carries this object so call sites that expect a
 * symmetric encode/decode pair (the BMP **decoder** lives at
 * `codec/bmp/src/main/kotlin/org/graphiks/kanvas/codec/bmp/`) compile against
 * a real type rather than a `null`-returning stub.
 *
 * The BMP framing emitted here is the classical
 * **BITMAPFILEHEADER + BITMAPINFOHEADER** combination (14 + 40 bytes),
 * with pixel rows stored *top-down* (negative `biHeight`) so we don't
 * have to walk the bitmap in reverse. Two pixel formats are supported :
 *  - [BmpFormat.kBGRA_8888] — 32-bit per pixel, alpha in the high byte.
 *  - [BmpFormat.kBGR_888] — 24-bit per pixel, no alpha.
 *
 * Both honour the BMP-mandated 4-byte row alignment by zero-padding
 * each row up to the nearest multiple of 4. Java's bundled
 * the pure Kotlin BMP codec round-trips both layouts.
 *
 * Pure Kotlin — no JNI, no native libwebp-style dependency. The
 * encoder allocates one `ByteArrayOutputStream` sized to the exact
 * file length up front so the write loop is a single sequential pass.
 */
public object SkBmpEncoder {

    /** Pixel layout selector. Mirrors the spirit of `SkColorType` for BMP. */
    public enum class BmpFormat {
        /** 32-bit BGRA — alpha preserved in the high byte. Default. */
        kBGRA_8888,

        /** 24-bit BGR — alpha is dropped. */
        kBGR_888,
    }

    /**
     * Encoder options. Only [format] is honoured today ; future fields
     * (DPI, compression mode RLE-4 / RLE-8, palette table) would slot
     * in here without changing call sites.
     */
    public data class Options(
        /** Target pixel layout. Default is [BmpFormat.kBGRA_8888]. */
        val format: BmpFormat = BmpFormat.kBGRA_8888,
    )

    private val defaultOptions = Options()

    /**
     * Encode [image]'s pixels into a BMP byte stream wrapped in
     * [SkData], or `null` on failure. Convenience entry point that
     * snapshots the image as an [SkBitmap] and dispatches to the
     * bitmap overload.
     */
    public fun Encode(image: SkImage, options: Options = defaultOptions): SkData? {
        val bitmap = SkBitmap(image.width, image.height)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                bitmap.pixels[y * image.width + x] = image.peekPixel(x, y)
            }
        }
        return Encode(bitmap, options)
    }

    /**
     * Encode [bitmap]'s pixels into a BMP byte stream wrapped in
     * [SkData], or `null` on failure (degenerate dimensions).
     *
     * The framing layout :
     *  - bytes 0–13 : `BITMAPFILEHEADER` (`'BM'`, file size, reserved, data offset).
     *  - bytes 14–53 : `BITMAPINFOHEADER` (header size, width, **negative** height,
     *    planes, bpp, compression, image size, x/y dpi, palette counts).
     *  - bytes 54+ : pixel data, BGRA or BGR per [Options.format], rows
     *    padded to 4 bytes.
     */
    public fun Encode(bitmap: SkBitmap, options: Options = defaultOptions): SkData? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null

        val bpp = if (options.format == BmpFormat.kBGRA_8888) 4 else 3
        val rowSize = (w * bpp + 3) and 3.inv() // align row to multiple of 4
        val pixelDataSize = rowSize * h
        val fileSize = FILE_HEADER_SIZE + DIB_HEADER_SIZE + pixelDataSize

        val out = ByteArrayOutputStream(fileSize)
        // BITMAPFILEHEADER (14 bytes).
        out.write('B'.code)
        out.write('M'.code)
        writeU32LE(out, fileSize)
        writeU16LE(out, 0); writeU16LE(out, 0) // reserved
        writeU32LE(out, FILE_HEADER_SIZE + DIB_HEADER_SIZE) // pixel data offset

        // BITMAPINFOHEADER (40 bytes).
        writeU32LE(out, DIB_HEADER_SIZE)
        writeU32LE(out, w)
        writeU32LE(out, -h) // negative → top-down rows (no flip)
        writeU16LE(out, 1) // planes
        writeU16LE(out, bpp * 8) // bits per pixel
        writeU32LE(out, 0) // compression = BI_RGB (no compression)
        writeU32LE(out, pixelDataSize)
        writeU32LE(out, 2835) // x DPI ≈ 72 dpi in pixels-per-metre
        writeU32LE(out, 2835) // y DPI ≈ 72 dpi in pixels-per-metre
        writeU32LE(out, 0) // colours used (0 = full range)
        writeU32LE(out, 0) // important colours (0 = all)

        // Pixel data — top-down (because of negative height) BGRA / BGR rows.
        val pad = rowSize - w * bpp
        for (y in 0 until h) {
            for (x in 0 until w) {
                val argb = bitmap.getPixel(x, y)
                out.write(SkColorGetB(argb))
                out.write(SkColorGetG(argb))
                out.write(SkColorGetR(argb))
                if (bpp == 4) out.write(SkColorGetA(argb))
            }
            for (i in 0 until pad) out.write(0)
        }

        return SkData.MakeWithCopy(out.toByteArray())
    }

    /**
     * Encode [bitmap] into [dst] directly — caller retains [dst] ownership.
     * Returns `true` on success.
     */
    public fun Encode(
        dst: OutputStream,
        bitmap: SkBitmap,
        options: Options = defaultOptions,
    ): Boolean {
        val data = Encode(bitmap, options) ?: return false
        return try {
            dst.write(data.toByteArray())
            true
        } catch (_: Throwable) {
            false
        }
    }

    private const val FILE_HEADER_SIZE = 14
    private const val DIB_HEADER_SIZE = 40

    private fun writeU32LE(out: OutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v ushr 8) and 0xFF)
        out.write((v ushr 16) and 0xFF)
        out.write((v ushr 24) and 0xFF)
    }

    private fun writeU16LE(out: OutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v ushr 8) and 0xFF)
    }
}
