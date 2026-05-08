package org.skia.codec.jpeg

import org.skia.codec.SkCodec
import org.skia.codec.SkEncodedImageFormat
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImageInfo
import org.skia.skcms.SkcmsICCProfile
import org.skia.skcms.skcmsParse
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * JPEG decoder — the D3.2 implementation of the [SkCodec] facade.
 *
 * Mirrors Skia's
 * [`SkJpegDecoder`](https://github.com/google/skia/blob/main/include/codec/SkJpegDecoder.h).
 * Like [org.skia.codec.png.SkPngCodec], we delegate the bitstream
 * decode to the JVM's [javax.imageio.ImageIO] and own only the
 * Skia-shaped surface : signature sniffing, ICC profile recovery
 * (out of `APP2 / ICC_PROFILE`), and conversion to an
 * [SkColorType.kRGBA_8888] [SkBitmap].
 *
 * **Why not high-bit-depth ?** The JPEG 1992 baseline / extended
 * profiles top out at 12 bits per component, and ImageIO ships only
 * the 8-bit decoder ; in practice every JPEG in the wild that we'll
 * meet decodes to an 8-bit raster, so we never need the F16 branch
 * the PNG codec carries. **Alpha** is similarly absent — JPEG has no
 * native alpha channel, so the bitmap is tagged
 * [SkAlphaType.kUnpremul] for parity with the 8888 PNG path even
 * though the alpha byte is always `0xFF`.
 *
 * **ICC profiles in JPEG** are stored in `APP2` segments under the
 * 12-byte `ICC_PROFILE` + NUL signature, with a 2-byte
 * `[chunk_index, chunk_count]` header that lets the encoder split a
 * profile larger than the 64 KiB segment limit across multiple
 * markers. We walk every `APP2` segment in encounter order, gather
 * the chunks, sort them by index, concatenate, and hand the bytes
 * off to [skcmsParse]. Best-effort : a malformed / unknown chunk
 * gracefully returns a `null` profile rather than failing the decode.
 */
public class SkJpegCodec internal constructor(
    private val image: BufferedImage,
    private val iccProfile: SkcmsICCProfile?,
) : SkCodec() {

    private val cachedInfo: SkImageInfo by lazy {
        val cs = iccProfile?.let { SkColorSpace.make(it) } ?: SkColorSpace.makeSRGB()
        SkImageInfo.Make(
            width = image.width,
            height = image.height,
            colorType = SkColorType.kRGBA_8888,
            alphaType = SkAlphaType.kUnpremul,
            colorSpace = cs,
        )
    }

    override fun getInfo(): SkImageInfo = cachedInfo

    override fun getEncodedFormat(): SkEncodedImageFormat = SkEncodedImageFormat.kJPEG

    override fun getICCProfile(): SkcmsICCProfile? = iccProfile

    override fun getPixels(info: SkImageInfo, dst: SkBitmap): Result {
        if (dst.width != info.width || dst.height != info.height) {
            return Result.kInvalidParameters
        }
        if (dst.colorType != info.colorType) {
            return Result.kInvalidParameters
        }
        if (info.colorType != SkColorType.kRGBA_8888) {
            return Result.kInvalidConversion
        }
        // BufferedImage.getRGB is documented to return TYPE_INT_ARGB
        // pixels regardless of the underlying buffer, with the alpha
        // byte set to 0xFF for opaque sources. JPEG is always opaque,
        // so this lands as 0xFFRRGGBB — exactly what kRGBA_8888 wants.
        image.getRGB(0, 0, image.width, image.height, dst.pixels, 0, image.width)
        return Result.kSuccess
    }

    /**
     * D3.2 registration record. Sniffs the JPEG SOI marker (`FF D8 FF`)
     * before handing the bytes to ImageIO and the ICC walker. The
     * dispatcher in [SkCodec.MakeFromData] consults each registered
     * decoder in order ; PNG and JPEG signatures are non-overlapping
     * (`89 50 4E 47…` vs `FF D8 FF`), so order between them is moot.
     */
    internal companion object Decoder : SkCodec.Decoder {

        override val name: String = "jpeg"

        /**
         * The 3-byte JPEG `SOI` + first marker prefix used by upstream
         * Skia's `SkJpegCodec::IsJpeg` (see
         * `src/codec/SkJpegConstants.h:kJpegSig`). Any valid JPEG
         * starts with `FF D8` (Start Of Image) immediately followed by
         * `FF` opening the next marker — usually `FF E0` (`JFIF` APP0)
         * or `FF E1` (`Exif` APP1) but we don't pin which one, since
         * JPEG variants in the wild use any of the APPn slots.
         */
        private val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

        override fun matches(data: ByteArray): Boolean {
            if (data.size < JPEG_SIGNATURE.size) return false
            for (i in JPEG_SIGNATURE.indices) {
                if (data[i] != JPEG_SIGNATURE[i]) return false
            }
            return true
        }

        override fun make(data: ByteArray): SkCodec? {
            val image = try {
                ImageIO.read(ByteArrayInputStream(data))
            } catch (_: Throwable) {
                null
            } ?: return null
            val profile = extractIccProfile(data)?.let { skcmsParse(it) }
            return SkJpegCodec(image, profile)
        }

        // ─── ICC profile reconstruction ───────────────────────────────

        /**
         * 12-byte `ICC_PROFILE` signature opening every APP2 ICC chunk
         * (the bytes `49 43 43 5F 50 52 4F 46 49 4C 45 00`) per
         * [the ICC.1:2010 spec, Annex B](https://www.color.org/specification/ICC1v43_2010-12.pdf).
         */
        private val ICC_SIG: ByteArray = byteArrayOf(
            0x49, 0x43, 0x43, 0x5F, 0x50, 0x52, 0x4F, 0x46, 0x49, 0x4C, 0x45, 0x00,
        )

        private const val APP2 = 0xE2.toByte()
        private const val SOS = 0xDA.toByte()

        /**
         * Walk the JPEG marker stream and reconstruct the embedded ICC
         * profile, or `null` if there is none / a chunk is malformed.
         *
         * JPEG segments have the shape `FF <marker> <length:2 bytes
         * BE> <payload[length-2]>`. ICC chunks live in `APP2` segments
         * whose payload begins with the [ICC_SIG] 12-byte signature,
         * then a 1-byte chunk index (1-based), a 1-byte chunk count,
         * then the chunk's slice of the ICC profile. We gather every
         * chunk, sort by index, and concatenate ; libjpeg's encoder is
         * not required to emit them in order.
         */
        private fun extractIccProfile(bytes: ByteArray): ByteArray? {
            if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) {
                return null
            }
            // Each entry : (chunk_index, chunk_payload).
            val chunks = mutableListOf<Pair<Int, ByteArray>>()
            var expectedCount = -1
            var p = 2 // past SOI
            while (p + 1 < bytes.size) {
                if (bytes[p] != 0xFF.toByte()) return null
                val marker = bytes[p + 1]
                p += 2
                // Standalone markers (no length) : RST0..RST7 (0xD0..0xD7),
                // SOI (0xD8), EOI (0xD9), TEM (0x01). Skip them.
                if (marker == 0xD8.toByte() || marker == 0xD9.toByte() ||
                    marker == 0x01.toByte() ||
                    (marker.toInt() and 0xFF) in 0xD0..0xD7
                ) {
                    if (marker == 0xD9.toByte()) break // EOI : stop walking.
                    continue
                }
                // Past SOS, the rest of the file is entropy-coded image
                // data — an ICC profile is always before SOS by spec.
                if (marker == SOS) break
                // Read 2-byte big-endian segment length (includes itself).
                if (p + 2 > bytes.size) return null
                val segLen = ((bytes[p].toInt() and 0xFF) shl 8) or
                    (bytes[p + 1].toInt() and 0xFF)
                if (segLen < 2 || p + segLen > bytes.size) return null
                val payloadStart = p + 2
                val payloadEnd = p + segLen
                if (marker == APP2 && payloadEnd - payloadStart >= ICC_SIG.size + 2 &&
                    matchesAt(bytes, payloadStart, ICC_SIG)
                ) {
                    val idx = bytes[payloadStart + ICC_SIG.size].toInt() and 0xFF
                    val cnt = bytes[payloadStart + ICC_SIG.size + 1].toInt() and 0xFF
                    if (expectedCount == -1) {
                        expectedCount = cnt
                    } else if (cnt != expectedCount) {
                        return null
                    }
                    val chunkStart = payloadStart + ICC_SIG.size + 2
                    chunks += idx to bytes.copyOfRange(chunkStart, payloadEnd)
                }
                p = payloadEnd
            }
            if (chunks.isEmpty()) return null
            // Sort by chunk index, validate contiguous 1..N coverage,
            // concatenate. Any gap or duplicate aborts cleanly with a
            // null return — caller falls back to sRGB.
            chunks.sortBy { it.first }
            for ((i, entry) in chunks.withIndex()) {
                if (entry.first != i + 1) return null
            }
            val total = chunks.sumOf { it.second.size }
            val out = ByteArray(total)
            var offset = 0
            for ((_, chunk) in chunks) {
                chunk.copyInto(out, offset)
                offset += chunk.size
            }
            return out
        }

        private fun matchesAt(haystack: ByteArray, at: Int, needle: ByteArray): Boolean {
            if (at + needle.size > haystack.size) return false
            for (i in needle.indices) {
                if (haystack[at + i] != needle[i]) return false
            }
            return true
        }
    }
}
