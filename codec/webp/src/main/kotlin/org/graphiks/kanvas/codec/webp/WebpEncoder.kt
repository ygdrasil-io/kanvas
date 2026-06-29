package org.graphiks.kanvas.codec.webp

import org.skia.foundation.SkBitmap
import org.skia.foundation.SkData
import org.skia.foundation.SkImage
import java.io.ByteArrayOutputStream
import java.io.OutputStream

public object WebpEncoder {

    public enum class Compression {
        kLossy,
        kLossless,
    }

    public data class Options(
        val compression: Compression = Compression.kLossless,
        val quality: Float = 100.0f,
    ) {
        init {
            require(quality in 0.0f..100.0f) {
                "quality must be in [0.0, 100.0], got $quality"
            }
        }
    }

    private val defaultOptions = Options()

    @Volatile
    private var customEncoder: ((SkBitmap, Options) -> ByteArray?)? = null

    public fun custom(callback: ((SkBitmap, Options) -> ByteArray?)?) {
        customEncoder = callback
    }

    public fun encode(image: SkImage, options: Options = defaultOptions): ByteArray? {
        val argb = IntArray(image.width * image.height)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                argb[y * image.width + x] = image.peekPixel(x, y)
            }
        }
        return encodeArgb(argb, image.width, image.height, options)
    }

    public fun encode(bitmap: SkBitmap, options: Options = defaultOptions): ByteArray? {
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

    public fun encode(
        dst: OutputStream,
        bitmap: SkBitmap,
        options: Options = defaultOptions,
    ): Boolean {
        val bytes = encode(bitmap, options) ?: return false
        return try {
            dst.write(bytes)
            true
        } catch (_: Throwable) {
            false
        }
    }

    public fun encodeAsData(image: SkImage, options: Options = defaultOptions): SkData? =
        encode(image, options)?.let { SkData.MakeWithCopy(it) }

    private fun encodeArgb(
        argb: IntArray,
        width: Int,
        height: Int,
        options: Options,
    ): ByteArray? {
        val custom = customEncoder
        if (custom != null) {
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
        if (width > 16384 || height > 16384) return null
        return when (options.compression) {
            Compression.kLossless -> WebpLosslessEncoder.encode(argb, width, height)
            Compression.kLossy -> null
        }
    }

    public fun requireLossy(bitmap: SkBitmap, options: Options = defaultOptions): ByteArray {
        require(options.compression == Compression.kLossy) {
            "requireLossy() must be called with options.compression = kLossy, got ${options.compression}"
        }
        return encode(bitmap, options) ?: throw NotImplementedError(
            "STUB.WEBP_LOSSY: lossy WebP encode is not implemented; " +
                "register an encoder via WebpEncoder.custom(...) to override.",
        )
    }
}

// =====================================================================
// VP8L lossless encoder — pure-Kotlin, literal-only emitter.
// =====================================================================

internal object WebpLosslessEncoder {

    private const val SIGNATURE: Int = 0x2F

    private const val NUM_LITERAL_CODES: Int = 256
    private const val NUM_LENGTH_CODES: Int = 24
    private const val GREEN_ALPHABET: Int = NUM_LITERAL_CODES + NUM_LENGTH_CODES
    private const val RED_ALPHABET: Int = 256
    private const val BLUE_ALPHABET: Int = 256
    private const val ALPHA_ALPHABET: Int = 256
    private const val DISTANCE_ALPHABET: Int = 40

    private const val MAX_CODE_LENGTH: Int = 15
    private const val CODE_LENGTH_CODES: Int = 19

    private val CODE_LENGTH_CODE_ORDER: IntArray = intArrayOf(
        17, 18, 0, 1, 2, 3, 4, 5, 16, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
    )

    fun encode(argb: IntArray, width: Int, height: Int): ByteArray {
        val payload = BitWriter()

        payload.writeBits(SIGNATURE, 8)
        payload.writeBits(width - 1, 14)
        payload.writeBits(height - 1, 14)
        var anyAlpha = 0
        for (px in argb) {
            if (((px ushr 24) and 0xFF) != 0xFF) { anyAlpha = 1; break }
        }
        payload.writeBits(anyAlpha, 1)
        payload.writeBits(0, 3)

        payload.writeBits(0, 1)

        payload.writeBits(0, 1)
        payload.writeBits(0, 1)

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

        val greenLens = buildCanonicalLengths(greenHist)
        val redLens = buildCanonicalLengths(redHist)
        val blueLens = buildCanonicalLengths(blueHist)
        val alphaLens = buildCanonicalLengths(alphaHist)

        val greenCodes = buildCanonicalCodes(greenLens)
        val redCodes = buildCanonicalCodes(redLens)
        val blueCodes = buildCanonicalCodes(blueLens)
        val alphaCodes = buildCanonicalCodes(alphaLens)

        writeHuffmanCode(payload, greenLens, greenHist)
        writeHuffmanCode(payload, redLens, redHist)
        writeHuffmanCode(payload, blueLens, blueHist)
        writeHuffmanCode(payload, alphaLens, alphaHist)
        writeUnusedHuffmanCode(payload)

        for (px in argb) {
            val a = (px ushr 24) and 0xFF
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF
            payload.writeCode(greenCodes[g], greenLens[g])
            payload.writeCode(redCodes[r], redLens[r])
            payload.writeCode(blueCodes[b], blueLens[b])
            payload.writeCode(alphaCodes[a], alphaLens[a])
        }

        val payloadBytes = payload.finish()

        val padded = (payloadBytes.size + 1) and 1.inv()
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
        return out
    }

    @Suppress("UNUSED_PARAMETER")
    private fun writeHuffmanCode(out: BitWriter, codeLengths: IntArray, histogram: IntArray) {
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
            out.writeBits(1, 1)
            val numSymbols = if (used == 0) 1 else used
            out.writeBits(numSymbols - 1, 1)
            val sym0 = if (first == -1) 0 else first
            out.writeBits(1, 1)
            out.writeBits(sym0, 8)
            if (numSymbols == 2) {
                out.writeBits(second, 8)
            }
            return
        }
        out.writeBits(0, 1)

        val codes = buildCodeLengthSequence(codeLengths)

        val metaHist = IntArray(CODE_LENGTH_CODES)
        for (e in codes) metaHist[e.code]++
        val metaLens = buildCanonicalLengths(metaHist, maxLen = 7)
        val metaCodes = buildCanonicalCodes(metaLens)

        var trimmed = CODE_LENGTH_CODES
        while (trimmed > 4 && metaLens[CODE_LENGTH_CODE_ORDER[trimmed - 1]] == 0) {
            trimmed--
        }
        out.writeBits(trimmed - 4, 4)
        for (i in 0 until trimmed) {
            out.writeBits(metaLens[CODE_LENGTH_CODE_ORDER[i]], 3)
        }

        out.writeBits(0, 1)

        for (e in codes) {
            out.writeCode(metaCodes[e.code], metaLens[e.code])
            when (e.code) {
                16 -> out.writeBits(e.extra, 2)
                17 -> out.writeBits(e.extra, 3)
                18 -> out.writeBits(e.extra, 7)
            }
        }
    }

    private fun writeUnusedHuffmanCode(out: BitWriter) {
        out.writeBits(1, 1)
        out.writeBits(0, 1)
        out.writeBits(0, 1)
        out.writeBits(0, 1)
    }

    private data class LengthCode(val code: Int, val extra: Int)

    private fun buildCodeLengthSequence(codeLengths: IntArray): List<LengthCode> {
        val out = ArrayList<LengthCode>(codeLengths.size)
        var i = 0
        var prevNonZero = -1
        while (i < codeLengths.size) {
            val v = codeLengths[i]
            if (v == 0) {
                var run = 0
                while (i + run < codeLengths.size && codeLengths[i + run] == 0) run++
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
                out.add(LengthCode(v, 0))
                i++
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
        @Suppress("UNUSED_VARIABLE") val _kept = prevNonZero
        return out
    }

    private fun buildCanonicalLengths(hist: IntArray, maxLen: Int = MAX_CODE_LENGTH): IntArray {
        val n = hist.size
        val lengths = IntArray(n)
        val used = ArrayList<Int>()
        for (i in 0 until n) if (hist[i] > 0) used.add(i)
        if (used.isEmpty()) return lengths
        if (used.size == 1) {
            lengths[used[0]] = 1
            val sentinel = if (used[0] == 0) 1 else 0
            lengths[sentinel] = 1
            return lengths
        }

        val numLeaves = used.size
        val totalNodes = 2 * numLeaves - 1
        val leftChild = IntArray(totalNodes) { -1 }
        val rightChild = IntArray(totalNodes) { -1 }
        val nodeWeight = LongArray(totalNodes)
        for (k in 0 until numLeaves) nodeWeight[k] = hist[used[k]].toLong()

        val alive = ArrayList<Int>(totalNodes)
        for (k in 0 until numLeaves) alive.add(k)
        var nextId = numLeaves
        while (alive.size > 1) {
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

        while (true) {
            var maxIdx = -1
            for (k in 0 until numLeaves) {
                if (rawLens[k] > maxLen) {
                    if (maxIdx == -1 || rawLens[k] > rawLens[maxIdx]) maxIdx = k
                }
            }
            if (maxIdx == -1) break
            var donor = -1
            for (k in 0 until numLeaves) {
                if (rawLens[k] in 1 until maxLen) {
                    if (donor == -1 || rawLens[k] < rawLens[donor]) donor = k
                }
            }
            if (donor == -1) {
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

    private fun buildCanonicalCodes(codeLengths: IntArray): IntArray {
        val n = codeLengths.size
        val blCount = IntArray(MAX_CODE_LENGTH + 1)
        for (i in 0 until n) {
            val l = codeLengths[i]
            if (l > 0) blCount[l]++
        }
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

internal class BitWriter {
    private val out = ByteArrayOutputStream()
    private var buf: Long = 0L
    private var nbits: Int = 0

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

    fun writeCode(code: Int, len: Int) {
        writeBits(code, len)
    }

    fun finish(): ByteArray {
        if (nbits > 0) {
            out.write((buf and 0xFF).toInt())
            buf = 0L
            nbits = 0
        }
        return out.toByteArray()
    }
}
