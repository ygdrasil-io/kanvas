package org.skia.codec.jpeg

import org.skia.codec.SkCodec
import org.skia.codec.SkEncodedImageFormat
import org.skia.codec.SkEncodedOrigin
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImageInfo
import org.skia.skcms.SkcmsICCProfile
import org.skia.skcms.skcmsParse
import org.skia.utils.SkPixmapUtils
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
    private val origin: SkEncodedOrigin,
) : SkCodec() {

    /**
     * Logical (post-orientation) dimensions. For an EXIF orientation
     * that includes a 90° rotation ([SkEncodedOrigin.swapsWidthHeight])
     * the decoded grid's `(w, h)` ends up swapped relative to the
     * stored JPEG `(w, h)` ; [getInfo] / [getPixels] surface this
     * post-orientation view so callers see the visually-correct image.
     */
    private val logicalWidth: Int =
        if (origin.swapsWidthHeight()) image.height else image.width
    private val logicalHeight: Int =
        if (origin.swapsWidthHeight()) image.width else image.height

    private val cachedInfo: SkImageInfo by lazy {
        val cs = iccProfile?.let { SkColorSpace.make(it) } ?: SkColorSpace.makeSRGB()
        SkImageInfo.Make(
            width = logicalWidth,
            height = logicalHeight,
            colorType = SkColorType.kRGBA_8888,
            alphaType = SkAlphaType.kUnpremul,
            colorSpace = cs,
        )
    }

    override fun getInfo(): SkImageInfo = cachedInfo

    override fun getEncodedFormat(): SkEncodedImageFormat = SkEncodedImageFormat.kJPEG

    override fun getICCProfile(): SkcmsICCProfile? = iccProfile

    /**
     * Mirrors `SkCodec::getOrigin()`. Returns the EXIF Orientation tag
     * parsed out of the JPEG's APP1 segment at decode time, or
     * [SkEncodedOrigin.kTopLeft] if the file carried no EXIF or the
     * Orientation tag was absent / out of range.
     */
    override fun getOrigin(): SkEncodedOrigin = origin

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
        if (origin == SkEncodedOrigin.kTopLeft) {
            image.getRGB(0, 0, image.width, image.height, dst.pixels, 0, image.width)
            return Result.kSuccess
        }
        // Non-trivial EXIF orientation : decode into an intermediate
        // bitmap matching the stored grid, then route through the
        // existing SkPixmapUtils.Orient transform to re-orient onto
        // `dst` (whose width/height are the post-orientation logical
        // dimensions). This mirrors upstream's "pixels are rotated /
        // mirrored to match the EXIF tag" contract for `getPixels`.
        val raw = SkBitmap(
            width = image.width,
            height = image.height,
            colorSpace = info.colorSpace,
            colorType = SkColorType.kRGBA_8888,
        )
        image.getRGB(0, 0, image.width, image.height, raw.pixels, 0, image.width)
        return if (SkPixmapUtils.Orient(dst, raw, origin)) {
            Result.kSuccess
        } else {
            Result.kInvalidParameters
        }
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
            val origin = extractExifOrigin(data) ?: SkEncodedOrigin.kTopLeft
            return SkJpegCodec(image, profile, origin)
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

        private const val APP1 = 0xE1.toByte()
        private const val APP2 = 0xE2.toByte()
        private const val SOS = 0xDA.toByte()

        // ─── EXIF orientation parsing (R-final.8) ─────────────────────

        /**
         * 6-byte `Exif\0\0` signature opening every APP1 EXIF segment
         * per the JEITA Exif 2.32 spec (`Exif` ASCII + two NUL pad
         * bytes). Followed by a standard TIFF header.
         */
        private val EXIF_SIG: ByteArray = byteArrayOf(
            0x45, 0x78, 0x69, 0x66, 0x00, 0x00,
        )

        /** TIFF tag 0x0112 — `Orientation` (Exif 2.32 §4.6.4 A). */
        private const val EXIF_ORIENTATION_TAG: Int = 0x0112

        /** TIFF type 3 — `SHORT` (16-bit unsigned). */
        private const val TIFF_TYPE_SHORT: Int = 3

        /**
         * Walk the JPEG marker stream looking for an APP1 segment whose
         * payload starts with the [EXIF_SIG] signature, then parse the
         * embedded TIFF header to extract the `Orientation` tag (0x0112)
         * from the first Image File Directory.
         *
         * Returns `null` when the file carries no EXIF, the EXIF block
         * is malformed, the Orientation tag is absent, or the parsed
         * value is outside `1..8`. The caller (Decoder.make) treats a
         * `null` return as "default top-left".
         *
         * The parser is intentionally conservative — it bails on any
         * unexpected layout rather than guessing, matching upstream
         * `SkExif::Parse`'s defensive behaviour for the Origin field.
         */
        private fun extractExifOrigin(bytes: ByteArray): SkEncodedOrigin? {
            if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) {
                return null
            }
            var p = 2 // past SOI
            while (p + 1 < bytes.size) {
                if (bytes[p] != 0xFF.toByte()) return null
                val marker = bytes[p + 1]
                p += 2
                if (marker == 0xD8.toByte() || marker == 0xD9.toByte() ||
                    marker == 0x01.toByte() ||
                    (marker.toInt() and 0xFF) in 0xD0..0xD7
                ) {
                    if (marker == 0xD9.toByte()) break
                    continue
                }
                if (marker == SOS) break
                if (p + 2 > bytes.size) return null
                val segLen = ((bytes[p].toInt() and 0xFF) shl 8) or
                    (bytes[p + 1].toInt() and 0xFF)
                if (segLen < 2 || p + segLen > bytes.size) return null
                val payloadStart = p + 2
                val payloadEnd = p + segLen
                if (marker == APP1 && payloadEnd - payloadStart >= EXIF_SIG.size + 8 &&
                    matchesAt(bytes, payloadStart, EXIF_SIG)
                ) {
                    val origin = parseExifTiffForOrigin(bytes, payloadStart + EXIF_SIG.size, payloadEnd)
                    if (origin != null) return origin
                    // Some encoders emit multiple APP1 segments (XMP,
                    // etc.) ; keep walking in case a later one is the
                    // EXIF block. Spec-compliant streams put EXIF first.
                }
                p = payloadEnd
            }
            return null
        }

        /**
         * Parse the TIFF block embedded in an EXIF APP1 payload (offsets
         * `[tiffStart, end)`). Returns the `Orientation` tag (0x0112)
         * value as an [SkEncodedOrigin], or `null` if the TIFF is
         * malformed / the tag is absent / the value is out of range.
         *
         * The TIFF header is :
         *  - 2 bytes : byte-order mark — `MM` (big-endian) or `II`
         *    (little-endian).
         *  - 2 bytes : magic `0x002A` in the active endianness.
         *  - 4 bytes : offset (from `tiffStart`) of the first IFD.
         *
         * Each IFD entry is 12 bytes : tag (2) + type (2) + count (4) +
         * value/offset (4). For `count*sizeof(type) <= 4`, the value
         * is stored inline ; otherwise the 4-byte field is an offset
         * into the same TIFF block. The Orientation tag is `count=1`,
         * `type=SHORT`, so the value lives in the first 2 bytes of
         * the inline field (in active endianness).
         */
        private fun parseExifTiffForOrigin(
            bytes: ByteArray,
            tiffStart: Int,
            end: Int,
        ): SkEncodedOrigin? {
            if (tiffStart + 8 > end) return null
            val b0 = bytes[tiffStart].toInt() and 0xFF
            val b1 = bytes[tiffStart + 1].toInt() and 0xFF
            val littleEndian = when {
                b0 == 0x49 && b1 == 0x49 -> true   // 'II'
                b0 == 0x4D && b1 == 0x4D -> false  // 'MM'
                else -> return null
            }
            val magic = readU16(bytes, tiffStart + 2, littleEndian)
            if (magic != 0x002A) return null
            val ifdOffset = readU32(bytes, tiffStart + 4, littleEndian)
            val ifdAt = tiffStart + ifdOffset
            if (ifdAt + 2 > end) return null
            val count = readU16(bytes, ifdAt, littleEndian)
            val entriesStart = ifdAt + 2
            if (entriesStart + 12 * count > end) return null
            for (i in 0 until count) {
                val entry = entriesStart + 12 * i
                val tag = readU16(bytes, entry, littleEndian)
                if (tag != EXIF_ORIENTATION_TAG) continue
                val type = readU16(bytes, entry + 2, littleEndian)
                val cnt = readU32(bytes, entry + 4, littleEndian)
                if (type != TIFF_TYPE_SHORT || cnt != 1) return null
                // SHORT count=1 — value is in the first 2 bytes of the
                // 4-byte value field, in active endianness.
                val raw = readU16(bytes, entry + 8, littleEndian)
                if (raw < 1 || raw > 8) return null
                return SkEncodedOrigin.fromExifValue(raw)
            }
            return null
        }

        private fun readU16(bytes: ByteArray, at: Int, littleEndian: Boolean): Int {
            val b0 = bytes[at].toInt() and 0xFF
            val b1 = bytes[at + 1].toInt() and 0xFF
            return if (littleEndian) (b1 shl 8) or b0 else (b0 shl 8) or b1
        }

        private fun readU32(bytes: ByteArray, at: Int, littleEndian: Boolean): Int {
            val b0 = bytes[at].toInt() and 0xFF
            val b1 = bytes[at + 1].toInt() and 0xFF
            val b2 = bytes[at + 2].toInt() and 0xFF
            val b3 = bytes[at + 3].toInt() and 0xFF
            return if (littleEndian) {
                (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
            } else {
                (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
            }
        }

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
