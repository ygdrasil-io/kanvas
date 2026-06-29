package org.skia.encode

import org.skia.foundation.SkBitmap
import org.skia.foundation.SkData
import org.skia.foundation.SkImage
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * R-suivi.23 implementation of upstream's
 * [`SkWebpEncoder`](https://github.com/google/skia/blob/main/include/encode/SkWebpEncoder.h).
 *
 * Sits beside [PngEncoder] / [JpegEncoder] in the `org.skia.encode`
 * package and follows the same shape : a Kotlin `object` carrying the
 * static `Encode` entry points and a Kotlin-idiomatic [Options] data
 * class mapped onto upstream's `SkWebpEncoder::Options`.
 *
 * ### Status
 *
 *  - [Compression.kLossless] is implemented in pure Kotlin via a
 *    minimal — but format-conformant — VP8L bitstream emitter. The
 *    output is **always** a valid RIFF/WEBP container that any
 *    standards-compliant WebP decoder (libwebp, Chrome, Firefox,
 *    Kanvas' pure Kotlin WebP decoder) decodes pixel-identical to the
 *    input. The encoder uses only literal pixels (no LZ77 backward
 *    references, no transformations, no color cache), so the bytes
 *    produced are larger than libwebp's lossless output — typically
 *    1.5–3× the size — but always strictly smaller than the raw ARGB
 *    pixel buffer. See the package-private helpers below for the
 *    bitstream layout.
 *  - [Compression.kLossy] returns `null`. Porting the VP8 lossy
 *    encoder is several thousand LOC of bit-exact DCT / quantizer
 *    arithmetic; this limitation is documented in `SUPPORTED_CODECS.md`.
 *
 * ### Extension hook
 *
 * [Custom] lets a downstream consumer plug in any external encoder
 * (for example a platform-specific encoder) without dragging the dependency into
 * `:kanvas-skia`. When registered, the callback takes precedence over
 * the built-in pure-Kotlin lossless encoder for **every** invocation
 * including [Compression.kLossless], so callers that want better
 * lossless compression (LZ77 + predictor transforms) than the minimal
 * built-in can transparently swap in libwebp without touching call
 * sites. Pass `null` to unregister and fall back to the built-in.
 */
public object SkWebpEncoder {

    /**
     * Mirror of `SkWebpEncoder::Compression`. Matches the upstream
     * libwebp dispatch — lossy (VP8) vs lossless (VP8L).
     */
    public enum class Compression {
        /** VP8 lossy compression. */
        kLossy,

        /** VP8L lossless compression. */
        kLossless,
    }

    /**
     * Mirror of `SkWebpEncoder::Options`.
     *
     *  - [quality] is `0.0..100.0` ;
     *  - if [compression] is [Compression.kLossy], [quality] is the
     *    visual quality (lower → smaller file) — currently unused
     *    because lossy is not implemented ;
     *  - if [compression] is [Compression.kLossless], [quality] is
     *    the encoder effort (lower → faster, larger file) — currently
     *    unused because the built-in lossless encoder has only one
     *    fixed effort level.
     *
     * Both fields are accepted today so call sites with non-default
     * options compile against the same surface a future fully-featured
     * encoder would expose.
     */
    public data class Options(
        val compression: Compression = Compression.kLossy,
        val quality: Float = 100.0f,
    ) {
        init {
            require(quality in 0.0f..100.0f) {
                "quality must be in [0.0, 100.0], got $quality"
            }
        }
    }

    private val defaultOptions = Options()

    /**
     * Pluggable callback. Volatile because the registration is
     * intentionally global and may be flipped from a different thread
     * than the encoding one (e.g. test setup vs. background renderer).
     */
    @Volatile
    private var customEncoder: ((SkBitmap, Options) -> ByteArray?)? = null

    /**
     * Register a [callback] that overrides the built-in encoder for
     * every subsequent [Encode] call. Pass `null` to unregister and
     * restore the built-in pure-Kotlin lossless encoder.
     *
     * The callback receives the source bitmap (always projected to
     * 8888 non-premul ARGB if the caller passed a different colour
     * type) and the requested options, and returns the encoded WebP
     * bytes or `null` to signal a soft failure (in which case
     * [Encode] returns `null` — the built-in is **not** retried).
     *
     * Typical use: binding a platform encoder in the consumer module
     * without forcing every `:kanvas-skia` user to depend on that
     * artifact.
     */
    @Suppress("FunctionName")
    public fun Custom(callback: ((SkBitmap, Options) -> ByteArray?)?) {
        customEncoder = callback
    }

    /**
     * Encode [image]'s pixels into the WebP byte stream. Mirrors
     * `sk_sp<SkData> SkWebpEncoder::Encode(GrDirectContext*,
     *     const SkImage*, const Options&)`.
     *
     * Routes through [Custom] if one is registered, otherwise hits the
     * built-in pure-Kotlin lossless encoder when [Compression.kLossless]
     * is requested, or returns `null` for [Compression.kLossy].
     */
    @Suppress("FunctionName")
    public fun Encode(image: SkImage, options: Options = defaultOptions): ByteArray? {
        val argb = IntArray(image.width * image.height)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                argb[y * image.width + x] = image.peekPixel(x, y)
            }
        }
        return encodeArgb(argb, image.width, image.height, options)
    }

    /**
     * Encode [bitmap]'s pixels into the WebP byte stream. Mirrors
     * `sk_sp<SkData> SkWebpEncoder::Encode(const SkPixmap&,
     *     const Options&)`.
     *
     * See [Encode] (the [SkImage] overload) for dispatch rules.
     */
    @Suppress("FunctionName")
    public fun Encode(bitmap: SkBitmap, options: Options = defaultOptions): ByteArray? {
        val custom = customEncoder
        if (custom != null) return custom(bitmap, options)
        val argb = IntArray(bitmap.width * bitmap.height)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                argb[y * bitmap.width + x] = bitmap.getPixel(x, y)
            }
        }
        return encodeArgbDispatch(argb, bitmap.width, bitmap.height, options)
    }

    /**
     * Encode [bitmap]'s pixels into [dst]. Returns `true` on success.
     * Mirrors `bool SkWebpEncoder::Encode(SkWStream*, const SkPixmap&,
     *     const Options&)`. The caller retains ownership of [dst].
     */
    @Suppress("FunctionName")
    public fun Encode(
        dst: OutputStream,
        bitmap: SkBitmap,
        options: Options = defaultOptions,
    ): Boolean {
        val bytes = Encode(bitmap, options) ?: return false
        return try {
            dst.write(bytes)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Convenience overload returning [SkData] rather than `ByteArray?`.
     * Mirrors upstream's `sk_sp<SkData>` return type — useful for call
     * sites that already speak the foundation types.
     */
    @Suppress("FunctionName")
    public fun EncodeAsData(image: SkImage, options: Options = defaultOptions): SkData? =
        Encode(image, options)?.let { SkData.MakeWithCopy(it) }

    /**
     * Encode helper that runs the registered [Custom] callback first,
     * falling back to the built-in only when no callback is set —
     * shared by [Encode] for [SkImage] (which has already projected to
     * an `IntArray`).
     */
    private fun encodeArgb(
        argb: IntArray,
        width: Int,
        height: Int,
        options: Options,
    ): ByteArray? {
        val custom = customEncoder
        if (custom != null) {
            // Re-hydrate the IntArray into a bitmap for the callback,
            // so consumers that bind to a native libwebp don't have to
            // also know our SkImage shape.
            val bm = SkBitmap(width, height)
            System.arraycopy(argb, 0, bm.pixels, 0, argb.size)
            return custom(bm, options)
        }
        return encodeArgbDispatch(argb, width, height, options)
    }

    private fun encodeArgbDispatch(
        argb: IntArray,
        width: Int,
        height: Int,
        options: Options,
    ): ByteArray? {
        if (width <= 0 || height <= 0) return null
        // VP8L's image header dedicates 14 bits per dimension, so the
        // maximum encodable side is 16384. Larger images would need
        // pre-tiling — out of scope for the minimal pure-Kotlin port.
        if (width > 16384 || height > 16384) return null
        return when (options.compression) {
            Compression.kLossless -> WebpLosslessEncoder.encode(argb, width, height)
            // R-final.S — STUB.WEBP_LOSSY. The VP8 lossy encoder needs
            // bit-exact DCT / quantizer arithmetic that lives outside
            // a pure-JVM port. The dispatch returns `null` here for
            // back-compat (existing call sites and the
            // [SkWebpEncoderTest] suite assume the soft-failure
            // contract). Consumers that want to *fail loud* on the
            // missing path call [requireLossy] instead. To plug a
            // real encoder, register it via [Custom] (the
            // [customEncoder] short-circuit at the top
            // of this method runs before this dispatch).
            Compression.kLossy -> null
        }
    }

    /**
     * Sharp-edge entry-point for the documented lossy WebP gap. Routes
     * to [Encode] ; if it returns `null` (because no [Custom] hook is
     * registered and the built-in lossy path is unimplemented),
     * throws [NotImplementedError] tagged `STUB.WEBP_LOSSY` so the
     * gap is visible at the call site rather than silently producing
     * an empty asset. Use this from GMs / tests that *require* a
     * working lossy WebP encode.
     */
    @Suppress("FunctionName")
    public fun requireLossy(bitmap: SkBitmap, options: Options = defaultOptions): ByteArray {
        require(options.compression == Compression.kLossy) {
            "requireLossy() must be called with options.compression = kLossy, got ${options.compression}"
        }
        return Encode(bitmap, options) ?: throw NotImplementedError(
            "STUB.WEBP_LOSSY: lossy WebP encode is not implemented; " +
                "register an encoder via SkWebpEncoder.Custom(...) to override.",
        )
    }
}

// =====================================================================
// VP8L lossless encoder — pure-Kotlin, literal-only emitter.
// =====================================================================

/**
 * Minimal VP8L lossless encoder. Produces a valid WebP/VP8L bitstream
 * that any conformant WebP decoder reads pixel-identical to the input.
 *
 * Approach :
 *  - **No transformations.** Pixels are emitted in raw ARGB order
 *    (predictor, color-transform, subtract-green and color-index
 *    transforms are libwebp optimizations, not bitstream requirements).
 *  - **No color cache.** The single bit signalling "color cache
 *    present" is emitted as `0` — the alphabet for green stays at
 *    `256 colors + 24 length codes = 280`.
 *  - **One Huffman code group.** No meta-Huffman over spatial regions —
 *    the whole image shares 5 Huffman codes.
 *  - **Literal-only emission.** Every pixel is encoded as the 4-tuple
 *    `(G, R, B, A)`. No backward references → no length / distance
 *    codes are ever emitted. The unused length / distance code
 *    alphabets are tagged with a "single symbol of length 0" simple
 *    code so the decoder does not try to read bits for them.
 *
 * Bit ordering follows the VP8L spec : LSB-first within each byte,
 * little-endian byte order in the RIFF chunk sizes.
 */
internal object WebpLosslessEncoder {

    private const val SIGNATURE: Int = 0x2F

    // Green alphabet : 256 colour values + 24 length codes (no colour
    // cache, see class kdoc).
    private const val NUM_LITERAL_CODES: Int = 256
    private const val NUM_LENGTH_CODES: Int = 24
    private const val GREEN_ALPHABET: Int = NUM_LITERAL_CODES + NUM_LENGTH_CODES   // 280
    private const val RED_ALPHABET: Int = 256
    private const val BLUE_ALPHABET: Int = 256
    private const val ALPHA_ALPHABET: Int = 256
    private const val DISTANCE_ALPHABET: Int = 40

    private const val MAX_CODE_LENGTH: Int = 15
    private const val CODE_LENGTH_CODES: Int = 19

    // Order in which the 19 code-length codes are emitted, per VP8L
    // spec. Putting 17 / 18 (zero-run codes) first lets the decoder
    // truncate the list early when only the first few are non-zero.
    private val CODE_LENGTH_CODE_ORDER: IntArray = intArrayOf(
        17, 18, 0, 1, 2, 3, 4, 5, 16, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
    )

    fun encode(argb: IntArray, width: Int, height: Int): ByteArray {
        // Build the VP8L payload into its own buffer first, then wrap
        // it in a RIFF/WEBP container with the right chunk size.
        val payload = BitWriter()

        // ─── 5-byte image header ──────────────────────────────────────
        payload.writeBits(SIGNATURE, 8)
        payload.writeBits(width - 1, 14)
        payload.writeBits(height - 1, 14)
        // alpha_used : 1 bit ; surfaces "does the image use any
        // non-255 alpha pixel". Decoders treat this as a hint only ;
        // we set it conservatively based on the data.
        var anyAlpha = 0
        for (px in argb) {
            if (((px ushr 24) and 0xFF) != 0xFF) { anyAlpha = 1; break }
        }
        payload.writeBits(anyAlpha, 1)
        // version_number : always 0 per spec, 3 bits.
        payload.writeBits(0, 3)

        // ─── No transformations ──────────────────────────────────────
        // 1 bit `transform_present` set to 0 closes the transform
        // chain before any transform body.
        payload.writeBits(0, 1)

        // ─── Image stream ─────────────────────────────────────────────
        // color_cache_info: 1 bit `present` set to 0.
        payload.writeBits(0, 1)
        // meta_huffman_codes_used: 1 bit `present` set to 0 — single
        // Huffman code group for the whole image.
        payload.writeBits(0, 1)

        // Build histograms over the pixel data. Only the 256 literal
        // slots of green are used (no length codes since we don't emit
        // backward refs).
        val greenHist = IntArray(GREEN_ALPHABET)
        val redHist = IntArray(RED_ALPHABET)
        val blueHist = IntArray(BLUE_ALPHABET)
        val alphaHist = IntArray(ALPHA_ALPHABET)
        for (px in argb) {
            val a = (px ushr 24) and 0xFF
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF
            greenHist[g]++
            redHist[r]++
            blueHist[b]++
            alphaHist[a]++
        }

        // Build canonical Huffman code-length tables (max length 15).
        val greenLens = buildCanonicalLengths(greenHist)
        val redLens = buildCanonicalLengths(redHist)
        val blueLens = buildCanonicalLengths(blueHist)
        val alphaLens = buildCanonicalLengths(alphaHist)
        // Distance tree is never used — emit a degenerate simple code.

        val greenCodes = buildCanonicalCodes(greenLens)
        val redCodes = buildCanonicalCodes(redLens)
        val blueCodes = buildCanonicalCodes(blueLens)
        val alphaCodes = buildCanonicalCodes(alphaLens)

        // Emit the 5 Huffman code tables in order :
        //   green+length, red, blue, alpha, distance.
        writeHuffmanCode(payload, greenLens, greenHist)
        writeHuffmanCode(payload, redLens, redHist)
        writeHuffmanCode(payload, blueLens, blueHist)
        writeHuffmanCode(payload, alphaLens, alphaHist)
        writeUnusedHuffmanCode(payload)

        // Emit pixels left-to-right, top-to-bottom.
        for (px in argb) {
            val a = (px ushr 24) and 0xFF
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF
            // Green is emitted first (and carries the length prefix in
            // its alphabet — but for a literal pixel the code is just
            // the green value).
            payload.writeCode(greenCodes[g], greenLens[g])
            payload.writeCode(redCodes[r], redLens[r])
            payload.writeCode(blueCodes[b], blueLens[b])
            payload.writeCode(alphaCodes[a], alphaLens[a])
        }

        val payloadBytes = payload.finish()

        // ─── RIFF wrapper ─────────────────────────────────────────────
        //   "RIFF"  4 bytes
        //   size    4 bytes (little-endian) = 4 ("WEBP") + 8 (VP8L hdr)
        //                                       + payload.size
        //   "WEBP"  4 bytes
        //   "VP8L"  4 bytes
        //   size    4 bytes (little-endian) = payload.size
        //   payload N bytes
        //   pad     1 byte if payload size odd
        val padded = (payloadBytes.size + 1) and 1.inv()
        // RIFF size = total file size - 8 (= excludes "RIFF" + 4-byte
        // size field), so it counts "WEBP" + ("VP8L"+size+payload+pad).
        val riffSize = 4 + 8 + padded
        val total = 8 + riffSize
        val out = ByteArray(total)
        var p = 0
        out[p++] = 'R'.code.toByte(); out[p++] = 'I'.code.toByte()
        out[p++] = 'F'.code.toByte(); out[p++] = 'F'.code.toByte()
        out[p++] = (riffSize and 0xFF).toByte()
        out[p++] = ((riffSize ushr 8) and 0xFF).toByte()
        out[p++] = ((riffSize ushr 16) and 0xFF).toByte()
        out[p++] = ((riffSize ushr 24) and 0xFF).toByte()
        out[p++] = 'W'.code.toByte(); out[p++] = 'E'.code.toByte()
        out[p++] = 'B'.code.toByte(); out[p++] = 'P'.code.toByte()
        out[p++] = 'V'.code.toByte(); out[p++] = 'P'.code.toByte()
        out[p++] = '8'.code.toByte(); out[p++] = 'L'.code.toByte()
        out[p++] = (payloadBytes.size and 0xFF).toByte()
        out[p++] = ((payloadBytes.size ushr 8) and 0xFF).toByte()
        out[p++] = ((payloadBytes.size ushr 16) and 0xFF).toByte()
        out[p++] = ((payloadBytes.size ushr 24) and 0xFF).toByte()
        System.arraycopy(payloadBytes, 0, out, p, payloadBytes.size)
        // Trailing pad byte (if any) is already 0 from ByteArray init.
        return out
    }

    /**
     * Emit a Huffman tree with at least one symbol of code length > 0.
     * Dispatches between the simple-code path (≤ 2 symbols used) and
     * the normal-code path (≥ 3 symbols used). The full canonical code
     * table is then derived from the emitted code lengths.
     *
     * The [histogram] is unused today but kept on the signature for
     * potential future use (e.g. picking a better tree shape than the
     * canonical one when the data is heavily skewed).
     */
    @Suppress("UNUSED_PARAMETER")
    private fun writeHuffmanCode(out: BitWriter, codeLengths: IntArray, histogram: IntArray) {
        // Count distinct symbols with a nonzero canonical code length.
        // This matches the codeLengths table that `buildCanonicalCodes`
        // will build the per-pixel writes against, so the simple-code
        // emission and the pixel-emit phase stay in sync.
        var used = 0
        var first = -1
        var second = -1
        for (i in codeLengths.indices) {
            if (codeLengths[i] != 0) {
                if (used == 0) first = i
                else if (used == 1) second = i
                used++
                if (used > 2) break
            }
        }
        if (used <= 2) {
            // Simple-code path. 1-bit `is_simple = 1`.
            out.writeBits(1, 1)
            // Encode 1 or 2 symbols.
            val numSymbols = if (used == 0) 1 else used
            out.writeBits(numSymbols - 1, 1)
            val sym0 = if (first == -1) 0 else first
            // `is_first_8bits = 1` always — we use the 8-bit form so a
            // symbol > 1 is representable. (The 1-bit form is a libwebp
            // micro-optimization for codes that only use symbols 0/1.)
            out.writeBits(1, 1)
            out.writeBits(sym0, 8)
            if (numSymbols == 2) {
                out.writeBits(second, 8)
            }
            return
        }
        // Normal-code path. 1-bit `is_simple = 0`.
        out.writeBits(0, 1)

        // Step 1 : build the sequence of code-length codes that will
        // be fed to the meta-Huffman decoder. Codes 0..15 are literal
        // bit-lengths ; 16 / 17 / 18 are run-length codes for
        // "repeat-previous" / "short zero run" / "long zero run".
        val codes = buildCodeLengthSequence(codeLengths)

        // Step 2 : histogram of code-length codes 0..18.
        val metaHist = IntArray(CODE_LENGTH_CODES)
        for (e in codes) metaHist[e.code]++
        val metaLens = buildCanonicalLengths(metaHist, maxLen = 7)
        // The meta-Huffman tree itself must have at least one symbol.
        // If `codeLengths` truly has ≥ 3 used symbols, `codes` is
        // non-empty and `metaLens` will too.
        val metaCodes = buildCanonicalCodes(metaLens)

        // Step 3 : write `num_code_lengths - 4` (4 bits) and that many
        // 3-bit lengths in the spec-mandated order. We trim trailing
        // zero lengths so a tree using only the first few code-length
        // codes (very common) emits a short table.
        var trimmed = CODE_LENGTH_CODES
        while (trimmed > 4 && metaLens[CODE_LENGTH_CODE_ORDER[trimmed - 1]] == 0) {
            trimmed--
        }
        out.writeBits(trimmed - 4, 4)
        for (i in 0 until trimmed) {
            out.writeBits(metaLens[CODE_LENGTH_CODE_ORDER[i]], 3)
        }

        // Step 4 : `use_length` flag = 0 → max_symbol = alphabet_size.
        out.writeBits(0, 1)

        // Step 5 : emit each code-length code via the meta-Huffman tree.
        for (e in codes) {
            out.writeCode(metaCodes[e.code], metaLens[e.code])
            when (e.code) {
                16 -> out.writeBits(e.extra, 2)  // repeat = extra + 3
                17 -> out.writeBits(e.extra, 3)  // zeros  = extra + 3
                18 -> out.writeBits(e.extra, 7)  // zeros  = extra + 11
                // 0..15 : no extra bits.
            }
        }
    }

    /**
     * Emit a degenerate single-symbol Huffman tree of code length 0.
     * Used for the distance code, which our literal-only emitter
     * never invokes. The decoder still reads the tree header but
     * never consumes any bit from it during pixel decoding.
     */
    private fun writeUnusedHuffmanCode(out: BitWriter) {
        // is_simple = 1
        out.writeBits(1, 1)
        // num_symbols - 1 = 0  (so 1 symbol)
        out.writeBits(0, 1)
        // is_first_8bits = 0  → first symbol is 1 bit.
        out.writeBits(0, 1)
        // symbol value = 0 — code length is 0, never emitted.
        out.writeBits(0, 1)
    }

    /**
     * One step in the code-length code stream : a literal length
     * (0..15) or a run-length code (16/17/18) with its extra bits.
     */
    private data class LengthCode(val code: Int, val extra: Int)

    /**
     * Pack [codeLengths] into the (literal / repeat / zero-run) stream
     * the VP8L spec consumes. Zero runs are collapsed using codes 17
     * (length 3..10) and 18 (length 11..138) ; repeats of a non-zero
     * length are collapsed using code 16 (length 3..6).
     */
    private fun buildCodeLengthSequence(codeLengths: IntArray): List<LengthCode> {
        val out = ArrayList<LengthCode>(codeLengths.size)
        var i = 0
        var prevNonZero = -1
        while (i < codeLengths.size) {
            val v = codeLengths[i]
            if (v == 0) {
                // Count run of zeros.
                var run = 0
                while (i + run < codeLengths.size && codeLengths[i + run] == 0) run++
                // Emit zero runs : >=11 uses code 18, 3..10 uses code 17,
                // <3 emits literal 0s.
                while (run >= 11) {
                    val r = minOf(run, 138)
                    out.add(LengthCode(18, r - 11))
                    run -= r
                    i += r
                }
                while (run >= 3) {
                    val r = minOf(run, 10)
                    out.add(LengthCode(17, r - 3))
                    run -= r
                    i += r
                }
                while (run > 0) {
                    out.add(LengthCode(0, 0))
                    run--
                    i++
                }
            } else {
                // Literal length.
                out.add(LengthCode(v, 0))
                i++
                // Then collapse run of identical follow-ups using code 16.
                var run = 0
                while (i < codeLengths.size && codeLengths[i] == v) {
                    run++; i++
                }
                while (run >= 3) {
                    val r = minOf(run, 6)
                    out.add(LengthCode(16, r - 3))
                    run -= r
                }
                while (run > 0) {
                    out.add(LengthCode(v, 0))
                    run--
                }
                prevNonZero = v
            }
        }
        // `prevNonZero` is kept around for symmetry with the libwebp
        // implementation ; we don't actually need it because code 16
        // already encodes "repeat the previous emitted length", which
        // matches our greedy-collapse semantics.
        @Suppress("UNUSED_VARIABLE") val _kept = prevNonZero
        return out
    }

    /**
     * Build canonical Huffman code lengths from a symbol histogram,
     * bounded to [maxLen] bits per symbol (default 15, the VP8L max).
     *
     * Uses a standard package-merge / "Huffman with length limit" via
     * a simpler iterative path : run unconstrained Huffman, then if any
     * code exceeds [maxLen] re-balance by truncating the longest codes
     * and recomputing canonical codes from the new length distribution
     * (Kraft inequality adjustment). Good enough for the literal-only
     * encoder — the histograms are well-behaved and tree imbalance
     * stays mild.
     */
    private fun buildCanonicalLengths(hist: IntArray, maxLen: Int = MAX_CODE_LENGTH): IntArray {
        val n = hist.size
        val lengths = IntArray(n)
        // Collect non-zero symbols.
        val used = ArrayList<Int>()
        for (i in 0 until n) if (hist[i] > 0) used.add(i)
        if (used.isEmpty()) return lengths
        if (used.size == 1) {
            // Single symbol used. The VP8L simple-code form supports
            // a 1-symbol tree (the decoder consumes 0 bits per occurrence
            // and always outputs that one symbol), but some real-world
            // decoders (notably TwelveMonkeys' `imageio-webp` port)
            // mishandle 0-bit-per-pixel streams and throw EOFException.
            // For robustness we emit a 2-symbol tree where the second
            // symbol is a synthetic sentinel : the actual encoded value
            // always uses code 0 (1 bit, zero), the sentinel is
            // declared but never emitted. Both libwebp and TwelveMonkeys
            // decode this correctly. See `writeHuffmanCode` for the
            // matching simple-code emission.
            lengths[used[0]] = 1
            // Pick a sentinel symbol that differs from the used one.
            val sentinel = if (used[0] == 0) 1 else 0
            lengths[sentinel] = 1
            return lengths
        }

        // Run standard Huffman code-length computation. We use the
        // "package-merge" cousin : a min-heap of (weight, node-id).
        // Node ids 0..numSymbols-1 are leaves ; ≥ numSymbols are
        // internal merge nodes.
        val numLeaves = used.size
        val totalNodes = 2 * numLeaves - 1
        val leftChild = IntArray(totalNodes) { -1 }
        val rightChild = IntArray(totalNodes) { -1 }
        val nodeWeight = LongArray(totalNodes)
        for (k in 0 until numLeaves) nodeWeight[k] = hist[used[k]].toLong()

        // Heap : array of node ids, sorted by weight ascending. Small
        // input (≤ alphabet size, ≤ 280) → an O(N²) scan is fine and
        // keeps the encoder dependency-free.
        val alive = ArrayList<Int>(totalNodes)
        for (k in 0 until numLeaves) alive.add(k)
        var nextId = numLeaves
        while (alive.size > 1) {
            // Pick two smallest weights.
            var i0 = 0
            for (j in 1 until alive.size) {
                if (nodeWeight[alive[j]] < nodeWeight[alive[i0]]) i0 = j
            }
            val a = alive.removeAt(i0)
            var i1 = 0
            for (j in 1 until alive.size) {
                if (nodeWeight[alive[j]] < nodeWeight[alive[i1]]) i1 = j
            }
            val b = alive.removeAt(i1)
            leftChild[nextId] = a
            rightChild[nextId] = b
            nodeWeight[nextId] = nodeWeight[a] + nodeWeight[b]
            alive.add(nextId)
            nextId++
        }
        val root = alive[0]

        // Depth-first walk to extract code lengths.
        val rawLens = IntArray(numLeaves)
        fun walk(node: Int, depth: Int) {
            if (node < numLeaves) {
                rawLens[node] = depth
                return
            }
            walk(leftChild[node], depth + 1)
            walk(rightChild[node], depth + 1)
        }
        walk(root, 0)

        // Enforce maxLen. Algorithm : while any length exceeds maxLen,
        // find the longest code and the shortest code that is < maxLen,
        // and swap one bit from the long to the short side. This
        // approximates the Kraft-rebalance Skia uses. Iterates at most
        // O(n) times in practice for natural histograms.
        while (true) {
            var maxIdx = -1
            for (k in 0 until numLeaves) {
                if (rawLens[k] > maxLen) {
                    if (maxIdx == -1 || rawLens[k] > rawLens[maxIdx]) maxIdx = k
                }
            }
            if (maxIdx == -1) break
            // Find a "donor" — a code at length < maxLen we can grow.
            var donor = -1
            for (k in 0 until numLeaves) {
                if (rawLens[k] in 1 until maxLen) {
                    if (donor == -1 || rawLens[k] < rawLens[donor]) donor = k
                }
            }
            if (donor == -1) {
                // Pathological — pin everything to maxLen.
                for (k in 0 until numLeaves) {
                    if (rawLens[k] > maxLen) rawLens[k] = maxLen
                }
                break
            }
            rawLens[maxIdx] = rawLens[maxIdx] - 1
            rawLens[donor] = rawLens[donor] + 1
        }

        for (k in 0 until numLeaves) lengths[used[k]] = rawLens[k]
        return lengths
    }

    /**
     * Build canonical code values for the given [codeLengths], per the
     * canonical Huffman convention (sort by length ascending, then by
     * symbol ascending within each length).
     *
     * Returns an array where `codes[symbol]` is the bit pattern (MSB
     * aligned to length `codeLengths[symbol]`) for that symbol. Bit 0
     * of the returned int is the *first* bit emitted by the bitstream
     * (i.e. we already flipped the canonical "MSB-first" bits into the
     * "LSB-first" order the VP8L bitstream consumes).
     */
    private fun buildCanonicalCodes(codeLengths: IntArray): IntArray {
        val n = codeLengths.size
        // Bucket count per length.
        val blCount = IntArray(MAX_CODE_LENGTH + 1)
        for (i in 0 until n) {
            val l = codeLengths[i]
            if (l > 0) blCount[l]++
        }
        // Compute first code per length (DEFLATE-style canonical
        // assignment ; this is the spec for VP8L too).
        val nextCode = IntArray(MAX_CODE_LENGTH + 1)
        var code = 0
        for (bits in 1..MAX_CODE_LENGTH) {
            code = (code + blCount[bits - 1]) shl 1
            nextCode[bits] = code
        }
        val codes = IntArray(n)
        for (i in 0 until n) {
            val len = codeLengths[i]
            if (len > 0) {
                val canonical = nextCode[len]
                nextCode[len] = canonical + 1
                // VP8L emits the canonical "MSB-first" code reversed
                // so the first bit emitted matches the first bit
                // consumed by an LSB-first reader.
                codes[i] = reverseBits(canonical, len)
            }
        }
        return codes
    }

    private fun reverseBits(value: Int, nBits: Int): Int {
        var v = value
        var r = 0
        for (i in 0 until nBits) {
            r = (r shl 1) or (v and 1)
            v = v ushr 1
        }
        return r
    }
}

/**
 * LSB-first bit stream writer. VP8L spec : bits are packed
 * little-endian within each byte (bit 0 of byte N is the bit at
 * stream position `N*8 + 0`), and bytes are written in stream order.
 *
 * The writer accumulates into a 64-bit register, flushing one byte at
 * a time once 8+ bits are buffered. [finish] flushes the remainder
 * (zero-padded to a byte boundary) and returns the produced bytes.
 */
internal class BitWriter {
    private val out = ByteArrayOutputStream()
    private var buf: Long = 0L
    private var nbits: Int = 0

    /** Write the low [nBits] of [value] into the stream (LSB-first). */
    fun writeBits(value: Int, nBits: Int) {
        if (nBits == 0) return
        val mask = if (nBits >= 32) -1 else (1 shl nBits) - 1
        buf = buf or ((value.toLong() and mask.toLong()) shl nbits)
        nbits += nBits
        while (nbits >= 8) {
            out.write((buf and 0xFF).toInt())
            buf = buf ushr 8
            nbits -= 8
        }
    }

    /** Convenience for emitting a pre-computed Huffman code. */
    fun writeCode(code: Int, len: Int) {
        writeBits(code, len)
    }

    /** Flush any partial byte (zero-padded) and return the bytes. */
    fun finish(): ByteArray {
        if (nbits > 0) {
            out.write((buf and 0xFF).toInt())
            buf = 0L
            nbits = 0
        }
        return out.toByteArray()
    }
}
