package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkCubicResampler
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkImages
import org.skia.foundation.SkMipmapMode
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkSamplingOptions
import org.graphiks.math.SkISize
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Port of Skia's `gm/image.cpp::scalepixels_unpremul`
 * (`DEF_SIMPLE_GM(scalepixels_unpremul, canvas, 1080, 280)`).
 *
 * Builds a 16×16 unpremultiplied N32 pixmap whose pixels encode
 * `(A=0, R=yy, G=xx, B=0xFF)` — i.e. each pixel carries a unique
 * (x, y) identity in its G/R channels and fully-opaque blue, with
 * alpha forced to zero (unpremul convention: transparent pixel with
 * meaningful colour channels). For each of the 4 sampling modes the
 * 16×16 source is scaled to 256×256 via [SkPixmap.scalePixels], then
 * "slam_ff" forces every pixel's alpha to 0xFF (making the result
 * opaque so the reference PNG is fully visible), and the result is
 * drawn at the current canvas offset.
 *
 * The 4 samplings match [gSamplings] in the C++ file:
 *  - `kNearest`
 *  - `kLinear`
 *  - `kLinear / kLinear` (mip-mapped linear — treated as bilinear on
 *    a single-level pixmap)
 *  - `Mitchell` cubic
 */
public class ScalepixelsUnpremulGM : GM() {
    override fun getName(): String = "scalepixels_unpremul"
    override fun getISize(): SkISize = SkISize.Make(1080, 280)

    /** Mirrors `gSamplings[]` from the C++ source. */
    private val samplings = arrayOf(
        SkSamplingOptions(SkFilterMode.kNearest),
        SkSamplingOptions(SkFilterMode.kLinear),
        SkSamplingOptions(SkFilterMode.kLinear, SkMipmapMode.kLinear),
        SkSamplingOptions(SkCubicResampler.Mitchell),
    )

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // ── Build the 16×16 source pixmap (kN32 kUnpremul). ────────────
        // C++: SkImageInfo::MakeN32(16, 16, kUnpremul_SkAlphaType)
        val srcInfo = SkImageInfo.MakeN32(16, 16, SkAlphaType.kUnpremul)
        val srcRowBytes = srcInfo.minRowBytes()
        val srcBuf = ByteBuffer.allocate(srcRowBytes * 16).order(ByteOrder.LITTLE_ENDIAN)
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                // C++: SkPackARGB32(0, (y<<4)|y, (x<<4)|x, 0xFF)
                // In kanvas-skia SkColorSetARGB(a, r, g, b) = 0xAARRGGBB.
                // SkPixmap kRGBA_8888 stores [R, G, B, A] LE in the buffer.
                val argb = SkColorSetARGB(0, (y shl 4) or y, (x shl 4) or x, 0xFF)
                val offset = y * srcRowBytes + x * 4
                // RGBA_8888 LE layout: byte[0]=R, byte[1]=G, byte[2]=B, byte[3]=A
                val r = (argb ushr 16) and 0xFF
                val g = (argb ushr 8) and 0xFF
                val b = argb and 0xFF
                val a = (argb ushr 24) and 0xFF
                srcBuf.put(offset, r.toByte())
                srcBuf.put(offset + 1, g.toByte())
                srcBuf.put(offset + 2, b.toByte())
                srcBuf.put(offset + 3, a.toByte())
            }
        }
        val srcPixmap = SkPixmap(srcInfo, srcBuf, srcRowBytes)

        // ── Allocate 256×256 destination pixmap (kN32 kUnpremul). ──────
        val dstInfo = SkImageInfo.MakeN32(256, 256, SkAlphaType.kUnpremul)
        val dstRowBytes = dstInfo.minRowBytes()
        val dstBuf = ByteBuffer.allocate(dstRowBytes * 256).order(ByteOrder.LITTLE_ENDIAN)
        val dstPixmap = SkPixmap(dstInfo, dstBuf, dstRowBytes)

        for (sampling in samplings) {
            // Scale src → dst.
            srcPixmap.scalePixels(dstPixmap, sampling)

            // ── slam_ff: force alpha to 0xFF on every pixel ─────────────
            // C++: *pm.writable_addr32(x,y) = *pm.addr32(x,y) | SkPackARGB32(0xFF,0,0,0)
            // In RGBA_8888 LE the alpha byte is at offset +3 in each pixel.
            for (off in 3 until dstBuf.limit() step 4) {
                dstBuf.put(off, 0xFF.toByte())
            }

            // ── draw_pixmap: RasterFromPixmapCopy → drawImage ───────────
            val img = SkImages.RasterFromPixmapCopy(dstPixmap)
            if (img != null) {
                c.drawImage(img, 10f, 10f)
            }
            c.translate(dstInfo.width + 10f, 0f)
        }
    }
}
