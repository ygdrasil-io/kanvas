package org.graphiks.kanvas.codec.jpeg2000

import org.graphiks.kanvas.codec.Codec
import org.skia.foundation.SkBitmap

/** Packet range retained by the bounded raw-J2K parser for the narrow Tier-2 path. */
internal data class J2kEntropyInput(
    val packetOffset: Int,
    val packetLength: Int,
    val decompositions: Int,
)

private class J2kEntropyFailure(
    val diagnostic: Jpeg2000Diagnostic,
) : RuntimeException(diagnostic.code)

private fun entropyFailure(
    code: String,
    offset: Int,
    result: Codec.Result = Codec.Result.kErrorInInput,
): Nothing = throw J2kEntropyFailure(Jpeg2000Diagnostic(code, offset.toLong(), result))

/**
 * Decodes the intentionally small raw Part-1 profile validated by
 * [J2kCodestreamParser]: one 8-bit unsigned component, one or two horizontal
 * 64x64 codeblocks, a single LRCP packet, reversible quantization and no DWT
 * levels.
 *
 * The packet header, MQ arithmetic stream and EBCOT passes are all decoded
 * from the encoded bytes. This is deliberately not a JP2, tiled, DWT, or
 * general multi-codeblock implementation.
 */
internal fun decodeNarrowRawJ2k(
    source: ByteArray,
    frame: Jpeg2000FrameInfo,
    entropy: J2kEntropyInput,
): Jpeg2000DecodeResult = try {
    if (
        frame.components != 1 || frame.precision != 8 ||
        frame.width !in 1..NARROW_RAW_J2K_MAX_WIDTH ||
        frame.height !in 1..NARROW_RAW_J2K_MAX_HEIGHT
    ) {
        entropyFailure("jpeg2000.entropy.profile.unsupported", entropy.packetOffset, Codec.Result.kUnimplemented)
    }
    val packetEnd = entropy.packetOffset.toLong() + entropy.packetLength.toLong()
    if (entropy.packetOffset < 0 || entropy.packetLength <= 0 || packetEnd > source.size) {
        entropyFailure("jpeg2000.packet.bounds", entropy.packetOffset)
    }
    if (entropy.decompositions == 1) {
        decodeNdecompOneRawJ2k(source, frame, entropy)
    } else {
        if (entropy.decompositions != 0) {
            entropyFailure("jpeg2000.entropy.profile.unsupported", entropy.packetOffset, Codec.Result.kUnimplemented)
        }
        val packet = source.copyOfRange(entropy.packetOffset, packetEnd.toInt())
        val codeblockWidths = narrowRawJ2kCodeblockWidths(frame.width)
        val header = J2kPacketHeader.read(packet, entropy.packetOffset, codeblockWidths.size)
        var bodyOffset = header.bodyOffset
        val coefficients = header.codeblocks.mapIndexed { index, codeblock ->
            val bodyEnd = bodyOffset + codeblock.bodyLength
            val decoded = J2kTier1Decoder(
                width = codeblockWidths[index],
                height = frame.height,
                numBitPlanes = codeblock.numBitPlanes,
                passes = codeblock.passes,
                codeblock = packet.copyOfRange(bodyOffset, bodyEnd),
                codeblockOffset = entropy.packetOffset + bodyOffset,
            ).decode()
            bodyOffset = bodyEnd
            decoded
        }

        val bitmap = SkBitmap(frame.width, frame.height)
        for (y in 0 until frame.height) {
            for (x in 0 until frame.width) {
                val codeblockIndex = if (x < RAW_J2K_CODEBLOCK_WIDTH) 0 else 1
                val codeblockX = if (codeblockIndex == 0) x else x - RAW_J2K_CODEBLOCK_WIDTH
                val sample = (coefficients[codeblockIndex][y * codeblockWidths[codeblockIndex] + codeblockX] + 128)
                    .coerceIn(0, 255)
                bitmap.setPixel(x, y, 0xFF000000.toInt() or (sample shl 16) or (sample shl 8) or sample)
            }
        }
        Jpeg2000DecodeResult(bitmap, null)
    }
} catch (failure: J2kEntropyFailure) {
    Jpeg2000DecodeResult(null, failure.diagnostic)
}

/**
 * Decodes one reversible 5/3 decomposition with precisely one codeblock in
 * each of LL, HL, LH and HH. The raw profile caps the source frame at 128x64,
 * therefore every subband is at most a 64x32 codeblock.
 */
private fun decodeNdecompOneRawJ2k(
    source: ByteArray,
    frame: Jpeg2000FrameInfo,
    entropy: J2kEntropyInput,
): Jpeg2000DecodeResult {
    if (frame.width < 2 || frame.height < 2) {
        entropyFailure("jpeg2000.ndecomp1.geometry.unsupported", entropy.packetOffset, Codec.Result.kUnimplemented)
    }
    val packetEnd = entropy.packetOffset + entropy.packetLength
    val packet = source.copyOfRange(entropy.packetOffset, packetEnd)
    val spans = readNdecompOnePacketSpans(packet, entropy.packetOffset)
    val llEntry = spans[0].header.codeblocks.single()
    val detailEntries = spans[1].header.codeblocks
    if (detailEntries.size != 3) entropyFailure("jpeg2000.ndecomp1.packet.codeblocks", spans[1].packetOffset)

    val lowWidth = (frame.width + 1) ushr 1
    val highWidth = frame.width ushr 1
    val lowHeight = (frame.height + 1) ushr 1
    val highHeight = frame.height ushr 1
    val ll = decodeNdecompOneCodeblock(
        source = source,
        entry = llEntry,
        bodyOffset = spans[0].bodyOffset,
        width = lowWidth,
        height = lowHeight,
        orientation = J2kSubbandOrientation.LL,
    )
    var detailBodyOffset = spans[1].bodyOffset
    fun decodeDetail(index: Int, width: Int, height: Int, orientation: J2kSubbandOrientation): IntArray {
        val entry = detailEntries[index]
        val decoded = decodeNdecompOneCodeblock(
            source = source,
            entry = entry,
            bodyOffset = detailBodyOffset,
            width = width,
            height = height,
            orientation = orientation,
        )
        detailBodyOffset += entry.bodyLength
        return decoded
    }
    val hl = decodeDetail(0, highWidth, lowHeight, J2kSubbandOrientation.HL)
    val lh = decodeDetail(1, lowWidth, highHeight, J2kSubbandOrientation.LH)
    val hh = decodeDetail(2, highWidth, highHeight, J2kSubbandOrientation.HH)
    if (detailBodyOffset != spans[1].bodyEnd) {
        entropyFailure("jpeg2000.ndecomp1.packet.trailing", detailBodyOffset)
    }

    val lowRows = IntArray(frame.width * lowHeight)
    val highRows = IntArray(frame.width * highHeight)
    for (y in 0 until lowHeight) {
        inverseReversible53(
            low = ll.copyOfRange(y * lowWidth, (y + 1) * lowWidth),
            high = hl.copyOfRange(y * highWidth, (y + 1) * highWidth),
            length = frame.width,
        ).copyInto(lowRows, y * frame.width)
    }
    for (y in 0 until highHeight) {
        inverseReversible53(
            low = lh.copyOfRange(y * lowWidth, (y + 1) * lowWidth),
            high = hh.copyOfRange(y * highWidth, (y + 1) * highWidth),
            length = frame.width,
        ).copyInto(highRows, y * frame.width)
    }

    val bitmap = SkBitmap(frame.width, frame.height)
    val lowColumn = IntArray(lowHeight)
    val highColumn = IntArray(highHeight)
    for (x in 0 until frame.width) {
        for (y in 0 until lowHeight) lowColumn[y] = lowRows[y * frame.width + x]
        for (y in 0 until highHeight) highColumn[y] = highRows[y * frame.width + x]
        val samples = inverseReversible53(lowColumn, highColumn, frame.height)
        for (y in samples.indices) {
            val sample = (samples[y] + 128).coerceIn(0, 255)
            bitmap.setPixel(x, y, 0xFF000000.toInt() or (sample shl 16) or (sample shl 8) or sample)
        }
    }
    return Jpeg2000DecodeResult(bitmap, null)
}

private fun decodeNdecompOneCodeblock(
    source: ByteArray,
    entry: J2kPacketCodeblock,
    bodyOffset: Int,
    width: Int,
    height: Int,
    orientation: J2kSubbandOrientation,
): IntArray {
    val bodyEnd = bodyOffset + entry.bodyLength
    if (width !in 1..RAW_J2K_CODEBLOCK_WIDTH || height !in 1..RAW_J2K_CODEBLOCK_WIDTH || bodyEnd > source.size) {
        entropyFailure("jpeg2000.ndecomp1.codeblock.bounds", bodyOffset)
    }
    return J2kTier1Decoder(
        width = width,
        height = height,
        numBitPlanes = entry.numBitPlanes,
        passes = entry.passes,
        codeblock = source.copyOfRange(bodyOffset, bodyEnd),
        codeblockOffset = bodyOffset,
        orientation = orientation,
    ).decode()
}

internal data class J2kPacketCodeblock(
    val numBitPlanes: Int,
    val passes: Int,
    val bodyLength: Int,
)

/** A bounded packet span in the Ndecomp=1 two-resolution lossless profile. */
internal data class J2kPacketSpan(
    val packetOffset: Int,
    val header: J2kPacketHeader,
    val bodyOffset: Int,
    val bodyEnd: Int,
)

internal data class J2kPacketHeader(
    val codeblocks: List<J2kPacketCodeblock>,
    val bodyOffset: Int,
) {
    val numBitPlanes: Int get() = codeblocks.single().numBitPlanes
    val passes: Int get() = codeblocks.single().passes
    val bodyLength: Int get() = codeblocks.sumOf(J2kPacketCodeblock::bodyLength)

    companion object {
        /** Decodes the LL codeblock entries of the strict one-packet profile. */
        fun read(
            packet: ByteArray,
            absoluteOffset: Int,
            codeblockCount: Int = 1,
            bandNumBitPlanes: IntArray? = null,
        ): J2kPacketHeader {
            val header = readPrefix(packet, absoluteOffset, codeblockCount, bandNumBitPlanes = bandNumBitPlanes)
            if (header.bodyLength != packet.size - header.bodyOffset) {
                entropyFailure("jpeg2000.packet.trailing", absoluteOffset + header.bodyOffset)
            }
            return header
        }

        /** Reads one packet header and body lengths from the start of a larger packet stream. */
        internal fun readPrefix(
            packet: ByteArray,
            absoluteOffset: Int,
            codeblockCount: Int,
            independentSubbandTrees: Boolean = false,
            bandNumBitPlanes: IntArray? = null,
        ): J2kPacketHeader {
            if (codeblockCount !in 1..3) entropyFailure("jpeg2000.packet.codeblocks", absoluteOffset)
            val baseBitPlanes = bandNumBitPlanes ?: IntArray(codeblockCount) { DEFAULT_BAND_NUM_BIT_PLANES }
            if (baseBitPlanes.size != codeblockCount || baseBitPlanes.any { it !in 1..MAX_CODEBLOCK_BIT_PLANES }) {
                entropyFailure("jpeg2000.packet.bitplanes", absoluteOffset)
            }
            val bits = J2kPacketBits(packet, absoluteOffset)
            if (bits.readBit() == 0) entropyFailure("jpeg2000.packet.empty", absoluteOffset)

            val inclusion = J2kTagTree(codeblockCount)
            val insignificantMsbs = J2kTagTree(codeblockCount)
            val perSubbandInclusion = if (independentSubbandTrees) {
                List(codeblockCount) { J2kTagTree(1) }
            } else {
                emptyList()
            }
            val perSubbandInsignificantMsbs = if (independentSubbandTrees) {
                List(codeblockCount) { J2kTagTree(1) }
            } else {
                emptyList()
            }
            val codeblocks = ArrayList<J2kPacketCodeblock>(codeblockCount)
            repeat(codeblockCount) { index ->
                val inclusionTree = if (independentSubbandTrees) perSubbandInclusion[index] else inclusion
                val insignificantMsbTree = if (independentSubbandTrees) perSubbandInsignificantMsbs[index] else insignificantMsbs
                val treeLeaf = if (independentSubbandTrees) 0 else index
                if (!inclusionTree.decode(bits, treeLeaf, threshold = 1)) {
                    entropyFailure("jpeg2000.packet.inclusion", bits.offset)
                }

                var zeroBitPlanes = 0
                while (!insignificantMsbTree.decode(bits, treeLeaf, threshold = zeroBitPlanes)) {
                    zeroBitPlanes++
                    if (zeroBitPlanes > MAX_ZERO_BIT_PLANES) {
                        entropyFailure("jpeg2000.packet.zero-bitplanes", bits.offset)
                    }
                }
                // SQcd=0x40/SPqcd=0x40 maps exponent 8 to a base of 9.
                // Ndecomp=1 passes the distinct base for each detail subband.
                val numBitPlanes = baseBitPlanes[index] + 1 - zeroBitPlanes
                if (numBitPlanes !in 1..MAX_CODEBLOCK_BIT_PLANES) {
                    entropyFailure("jpeg2000.packet.zero-bitplanes", bits.offset)
                }

                val passes = bits.readNumPasses()
                if (passes !in 1..MAX_PASSES) entropyFailure("jpeg2000.packet.passes", bits.offset)
                val lengthIncrement = bits.readCommaCode()
                if (lengthIncrement > MAX_LENGTH_INCREMENT) entropyFailure("jpeg2000.packet.length-bits", bits.offset)
                val lengthBits = INITIAL_LENGTH_BITS + lengthIncrement + floorLog2(passes)
                if (lengthBits !in 1..30) entropyFailure("jpeg2000.packet.length-bits", bits.offset)
                val bodyLength = bits.readBits(lengthBits)
                if (bodyLength <= 0) entropyFailure("jpeg2000.packet.length", bits.offset)
                codeblocks += J2kPacketCodeblock(numBitPlanes, passes, bodyLength)
            }
            bits.alignToPacketBoundary()
            val bodyOffset = bits.bytesRead
            val bodiesLength = codeblocks.sumOf(J2kPacketCodeblock::bodyLength)
            if (bodiesLength > packet.size - bodyOffset) entropyFailure("jpeg2000.packet.truncated", bits.offset)
            return J2kPacketHeader(codeblocks, bodyOffset)
        }

        private const val DEFAULT_BAND_NUM_BIT_PLANES = 9
        private const val MAX_ZERO_BIT_PLANES = 9
        private const val MAX_CODEBLOCK_BIT_PLANES = 11
        private const val MAX_PASSES = 22
        private const val MAX_LENGTH_INCREMENT = 24
        private const val INITIAL_LENGTH_BITS = 3
    }
}

/** Reads the two LRCP packets of the deliberately bounded Ndecomp=1 profile. */
internal fun readNdecompOnePacketSpans(packet: ByteArray, absoluteOffset: Int): List<J2kPacketSpan> {
    val ll = J2kPacketHeader.readPrefix(
        packet,
        absoluteOffset,
        codeblockCount = 1,
        bandNumBitPlanes = intArrayOf(9),
    )
    val firstBodyEnd = ll.bodyOffset + ll.bodyLength
    if (firstBodyEnd >= packet.size) entropyFailure("jpeg2000.ndecomp1.packet.missing", absoluteOffset + firstBodyEnd)

    val resolutionOne = J2kPacketHeader.readPrefix(
        packet = packet.copyOfRange(firstBodyEnd, packet.size),
        absoluteOffset = absoluteOffset + firstBodyEnd,
        codeblockCount = 3,
        independentSubbandTrees = true,
        bandNumBitPlanes = intArrayOf(10, 10, 11),
    )
    val secondBodyOffset = firstBodyEnd + resolutionOne.bodyOffset
    val secondBodyEnd = secondBodyOffset + resolutionOne.bodyLength
    if (secondBodyEnd != packet.size) entropyFailure("jpeg2000.ndecomp1.packet.trailing", absoluteOffset + secondBodyEnd)

    return listOf(
        J2kPacketSpan(
            packetOffset = absoluteOffset,
            header = ll,
            bodyOffset = absoluteOffset + ll.bodyOffset,
            bodyEnd = absoluteOffset + firstBodyEnd,
        ),
        J2kPacketSpan(
            packetOffset = absoluteOffset + firstBodyEnd,
            header = resolutionOne,
            bodyOffset = absoluteOffset + secondBodyOffset,
            bodyEnd = absoluteOffset + secondBodyEnd,
        ),
    )
}

/** JPEG 2000 packet-header BIO reader, including the 0xFF seven-bit rule. */
private class J2kPacketBits(
    private val bytes: ByteArray,
    private val absoluteStart: Int,
) {
    private var position = 0
    private var current = -1
    private var remaining = 0

    val offset: Int get() = absoluteStart + position
    val bytesRead: Int get() = position

    fun readBit(): Int {
        if (remaining == 0) loadByte()
        remaining--
        return (current ushr remaining) and 1
    }

    fun readBits(count: Int): Int {
        var result = 0
        repeat(count) { result = (result shl 1) or readBit() }
        return result
    }

    fun readNumPasses(): Int {
        if (readBit() == 0) return 1
        if (readBit() == 0) return 2
        val twoBits = readBits(2)
        if (twoBits != 3) return 3 + twoBits
        val fiveBits = readBits(5)
        if (fiveBits != 31) return 6 + fiveBits
        return 37 + readBits(7)
    }

    fun readCommaCode(): Int {
        var count = 0
        while (readBit() != 0) {
            count++
            if (count > 31) entropyFailure("jpeg2000.packet.comma-code", offset)
        }
        return count
    }

    fun alignToPacketBoundary() {
        // BIO's inalign reads one stuffed continuation byte after an FF
        // header byte before establishing the next byte boundary.
        if (current == 0xFF) loadByte()
        remaining = 0
    }

    private fun loadByte() {
        if (position >= bytes.size) entropyFailure("jpeg2000.packet.truncated", absoluteStart + position)
        val previousWasFf = current == 0xFF
        current = bytes[position++].toInt() and 0xFF
        remaining = if (previousWasFf) 7 else 8
    }
}

/** Persistent packet-header tag tree for one or two horizontal codeblocks. */
private class J2kTagTree(
    private val leaves: Int,
) {
    private data class Node(var value: Int = Int.MAX_VALUE, var low: Int = 0)

    private val nodes: Array<Node> = Array(if (leaves == 1) 1 else 3) { Node() }

    fun decode(bits: J2kPacketBits, leaf: Int, threshold: Int): Boolean {
        if (leaf !in 0 until leaves || threshold < 0) entropyFailure("jpeg2000.packet.tag-tree", bits.offset)
        val path = if (leaves == 1) intArrayOf(0) else intArrayOf(2, leaf)
        var low = 0
        for (index in path) {
            val node = nodes[index]
            low = maxOf(low, node.low)
            while (low < threshold && low < node.value) {
                if (bits.readBit() != 0) node.value = low else low++
            }
            node.low = low
        }
        return nodes[leaf].value < threshold
    }
}

/** ISO/IEC 15444-1 C.3 MQ arithmetic decoder for Tier-1's 19 contexts. */
private class J2kMqDecoder(
    private val bytes: ByteArray,
    private val absoluteStart: Int,
) {
    private val contexts = IntArray(CONTEXT_COUNT)
    private var position = 0
    private var c: Long
    private var a = 0x8000
    private var ct = 0

    val offset: Int get() = absoluteStart + position

    init {
        contexts[CTX_ZC] = 8 // state 4, MPS=0
        contexts[CTX_AGG] = 6 // state 3, MPS=0
        contexts[CTX_UNI] = 92 // state 46, MPS=0
        c = (byteAt(0).toLong() shl 16) and UINT32_MASK
        byteIn()
        c = (c shl 7) and UINT32_MASK
        ct -= 7
    }

    fun decode(context: Int): Int {
        if (context !in contexts.indices) entropyFailure("jpeg2000.mq.context", absoluteStart + position)
        var state = contexts[context]
        val stateIndex = state ushr 1
        val mps = state and 1
        val qe = MQ_QE[stateIndex]
        a -= qe
        val decision: Int
        if ((c ushr 16) < qe.toLong()) {
            if (a < qe) {
                a = qe
                decision = mps
                state = mpsTransition(stateIndex, mps)
            } else {
                a = qe
                decision = mps xor 1
                state = lpsTransition(stateIndex, mps)
            }
            renormalize()
        } else {
            c = (c - (qe.toLong() shl 16)) and UINT32_MASK
            if (a < 0x8000) {
                if (a < qe) {
                    decision = mps xor 1
                    state = lpsTransition(stateIndex, mps)
                } else {
                    decision = mps
                    state = mpsTransition(stateIndex, mps)
                }
                renormalize()
            } else {
                decision = mps
            }
        }
        contexts[context] = state
        return decision
    }

    private fun renormalize() {
        while (a < 0x8000) {
            if (ct == 0) byteIn()
            a = a shl 1
            c = (c shl 1) and UINT32_MASK
            ct--
        }
    }

    private fun byteIn() {
        val current = byteAt(position)
        val next = byteAt(position + 1)
        if (current == 0xFF) {
            if (next > 0x8F) {
                c = (c + 0xFF00L) and UINT32_MASK
                ct = 8
            } else {
                position++
                c = (c + (next.toLong() shl 9)) and UINT32_MASK
                ct = 7
            }
        } else {
            position++
            c = (c + (next.toLong() shl 8)) and UINT32_MASK
            ct = 8
        }
    }

    private fun byteAt(index: Int): Int = if (index < bytes.size) bytes[index].toInt() and 0xFF else 0xFF

    private fun mpsTransition(index: Int, mps: Int): Int = J2kMqTransitions.afterMps((index shl 1) or mps)

    private fun lpsTransition(index: Int, mps: Int): Int = J2kMqTransitions.afterLps((index shl 1) or mps)

    private companion object {
        const val CTX_ZC = 0
        const val CTX_AGG = 17
        const val CTX_UNI = 18
        const val CONTEXT_COUNT = 19
        const val UINT32_MASK = 0xFFFF_FFFFL

        // Annex C's 47 probability states. Context values store the paired
        // state index (probability index << 1 | MPS), as in OpenJPEG's MQC.
        val MQ_QE = intArrayOf(
            0x5601, 0x3401, 0x1801, 0x0AC1, 0x0521, 0x0221, 0x5601, 0x5401,
            0x4801, 0x3801, 0x3001, 0x2401, 0x1C01, 0x1601, 0x5601, 0x5401,
            0x5101, 0x4801, 0x3801, 0x3401, 0x3001, 0x2801, 0x2401, 0x2201,
            0x1C01, 0x1801, 0x1601, 0x1401, 0x1201, 0x1101, 0x0AC1, 0x09C1,
            0x08A1, 0x0521, 0x0441, 0x02A1, 0x0221, 0x0141, 0x0111, 0x0085,
            0x0049, 0x0025, 0x0015, 0x0009, 0x0005, 0x0001, 0x5601,
        )
    }
}

/** Annex C's packed MQ state transitions, shared by production decode and regression tests. */
internal object J2kMqTransitions {
    fun afterMps(state: Int): Int {
        val index = state ushr 1
        return (MQ_NMPS[index] shl 1) or (state and 1)
    }

    fun afterLps(state: Int): Int {
        val index = state ushr 1
        val mps = state and 1
        return (MQ_NLPS[index] shl 1) or (mps xor if (MQ_SWITCH[index]) 1 else 0)
    }

    private val MQ_NMPS = intArrayOf(
        1, 2, 3, 4, 5, 38, 7, 8, 9, 10, 11, 12, 13, 29, 15, 16,
        17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31,
        32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 45, 46,
    )
    private val MQ_NLPS = intArrayOf(
        1, 6, 9, 12, 29, 33, 6, 14, 14, 14, 17, 18, 20, 21, 14, 14,
        15, 16, 17, 18, 19, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28,
        29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 46,
    )
    private val MQ_SWITCH = booleanArrayOf(
        true, false, false, false, false, false, true, false, false, false, false, false,
        false, false, true, false, false, false, false, false, false, false, false, false,
        false, false, false, false, false, false, false, false, false, false, false, false,
        false, false, false, false, false, false, false, false, false, false, false,
    )
}

/** EBCOT Tier-1 decoder for style 0, one LL codeblock, and no raw bypass mode. */
internal data class J2kEbcotDecision(
    val pass: String,
    val bitPlane: Int,
    val x: Int,
    val y: Int,
    val context: Int,
    val bit: Int,
    val mqOffset: Int,
    val signPrediction: Int? = null,
)

/** JPEG 2000 subband orientations used by Tier-1's directional ZC contexts. */
internal enum class J2kSubbandOrientation {
    LL,
    HL,
    LH,
    HH,
}

/**
 * Annex C zero-coding context number for one coefficient neighbourhood.
 *
 * `HL`, `LH`, and `HH` must not reuse LL's isotropic-looking table: the
 * high-pass direction changes how horizontal, vertical and diagonal evidence
 * is ranked. The values mirror the four 512-entry orientation tables used by
 * OpenJPEG's Part-1 Tier-1 implementation.
 */
internal fun j2kZeroCodingContext(
    horizontal: Int,
    vertical: Int,
    diagonal: Int,
    orientation: J2kSubbandOrientation,
): Int {
    require(horizontal in 0..2)
    require(vertical in 0..2)
    require(diagonal in 0..4)
    return when (orientation) {
        J2kSubbandOrientation.LL,
        J2kSubbandOrientation.LH,
        -> when {
            horizontal == 0 && vertical == 0 && diagonal == 0 -> 0
            horizontal == 0 && vertical == 0 && diagonal == 1 -> 1
            horizontal == 0 && vertical == 0 -> 2
            horizontal == 0 && vertical == 1 -> 3
            horizontal == 0 -> 4
            horizontal == 1 && vertical == 0 && diagonal == 0 -> 5
            horizontal == 1 && vertical == 0 -> 6
            horizontal == 1 -> 7
            else -> 8
        }

        J2kSubbandOrientation.HL -> when {
            horizontal == 0 && vertical == 0 && diagonal == 0 -> 0
            horizontal == 0 && vertical == 0 && diagonal == 1 -> 1
            horizontal == 0 && vertical == 0 -> 2
            horizontal == 0 && vertical == 1 && diagonal == 0 -> 5
            horizontal == 0 && vertical == 1 -> 6
            horizontal == 0 -> 8
            horizontal == 1 && vertical == 0 -> 3
            horizontal == 1 -> 7
            vertical == 0 -> 4
            vertical == 1 -> 7
            else -> 8
        }

        J2kSubbandOrientation.HH -> when {
            diagonal == 0 && horizontal == 0 && vertical == 0 -> 0
            diagonal == 0 && horizontal + vertical == 1 -> 1
            diagonal == 0 -> 2
            diagonal == 1 && horizontal == 0 && vertical == 0 -> 3
            diagonal == 1 && horizontal + vertical == 1 -> 4
            diagonal == 1 -> 5
            diagonal == 2 && horizontal == 0 && vertical == 0 -> 6
            diagonal == 2 -> 7
            else -> 8
        }
    }
}

internal fun traceNarrowJ2kPacket(
    packet: ByteArray,
    packetOffset: Int,
    width: Int,
    height: Int,
): List<J2kEbcotDecision> {
    val header = J2kPacketHeader.read(packet, packetOffset)
    val trace = ArrayList<J2kEbcotDecision>()
    J2kTier1Decoder(
        width = width,
        height = height,
        numBitPlanes = header.numBitPlanes,
        passes = header.passes,
        codeblock = packet.copyOfRange(header.bodyOffset, packet.size),
        codeblockOffset = packetOffset + header.bodyOffset,
        trace = trace::add,
    ).decode()
    return trace
}

internal class J2kTier1Decoder(
    private val width: Int,
    private val height: Int,
    private val numBitPlanes: Int,
    private val passes: Int,
    codeblock: ByteArray,
    codeblockOffset: Int,
    private val orientation: J2kSubbandOrientation = J2kSubbandOrientation.LL,
    private val trace: ((J2kEbcotDecision) -> Unit)? = null,
) {
    private val coefficients = IntArray(width * height)
    private val significant = BooleanArray(coefficients.size)
    private val visitedInSignificancePass = BooleanArray(coefficients.size)
    private val refined = BooleanArray(coefficients.size)
    private val mq = J2kMqDecoder(codeblock, codeblockOffset)
    private var activePass = ""
    private var activeBitPlane = 0

    fun decode(): IntArray {
        // Tier-1's `bpno_plus_one` naming is historical: its pass routines
        // receive this value directly. With eight decoded bit-planes the
        // first cleanup pass is therefore at bit position eight, then the
        // seven sig/ref/cleanup triplets run at positions seven through one.
        var bitPlane = numBitPlanes
        var passType = CLEANUP_PASS
        repeat(passes) {
            if (bitPlane < 1) entropyFailure("jpeg2000.tier1.passes", 0)
            activeBitPlane = bitPlane
            activePass = when (passType) {
                SIGNIFICANCE_PASS -> "significance"
                REFINEMENT_PASS -> "refinement"
                else -> "cleanup"
            }
            when (passType) {
                SIGNIFICANCE_PASS -> decodeSignificancePass(bitPlane)
                REFINEMENT_PASS -> decodeRefinementPass(bitPlane)
                CLEANUP_PASS -> decodeCleanupPass(bitPlane)
            }
            passType++
            if (passType == 3) {
                passType = SIGNIFICANCE_PASS
                bitPlane--
            }
        }
        return IntArray(coefficients.size) { index -> coefficients[index] / 2 }
    }

    private fun decodeSignificancePass(bitPlane: Int) {
        forEachEbcotStripe(width, height) { x, stripeStart, stripeEnd ->
            for (y in stripeStart until stripeEnd) {
                val index = indexOf(x, y)
                if (!significant[index] && !visitedInSignificancePass[index] && hasSignificantNeighbour(x, y)) {
                    if (decodeDecision(zeroCodingContext(x, y), x, y) != 0) markSignificant(x, y, bitPlane)
                    visitedInSignificancePass[index] = true
                }
            }
        }
    }

    private fun decodeRefinementPass(bitPlane: Int) {
        val delta = 1 shl (bitPlane - 1)
        forEachEbcotStripe(width, height) { x, stripeStart, stripeEnd ->
            for (y in stripeStart until stripeEnd) {
                val index = indexOf(x, y)
                if (significant[index] && !visitedInSignificancePass[index]) {
                    val bit = decodeDecision(magnitudeContext(x, y, index), x, y)
                    coefficients[index] += if ((bit != 0) xor (coefficients[index] < 0)) delta else -delta
                    refined[index] = true
                }
            }
        }
    }

    private fun decodeCleanupPass(bitPlane: Int) {
        forEachEbcotStripe(width, height) { x, stripeStart, stripeEnd ->
            if (stripeEnd - stripeStart == 4 && canUseRunMode(x, stripeStart)) {
                if (decodeDecision(17, x, stripeStart) != 0) {
                    val runLength = (decodeDecision(18, x, stripeStart) shl 1) or decodeDecision(18, x, stripeStart)
                    markSignificant(x, stripeStart + runLength, bitPlane)
                    for (y in stripeStart + runLength + 1 until stripeEnd) decodeCleanupSample(x, y, bitPlane)
                }
            } else {
                for (y in stripeStart until stripeEnd) decodeCleanupSample(x, y, bitPlane)
            }
        }
        visitedInSignificancePass.fill(false)
    }

    private fun canUseRunMode(x: Int, stripeStart: Int): Boolean =
        (0 until 4).all { offset ->
            val index = indexOf(x, stripeStart + offset)
            !significant[index] && !visitedInSignificancePass[index] && !hasSignificantNeighbour(x, stripeStart + offset)
        }

    private fun decodeCleanupSample(x: Int, y: Int, bitPlane: Int) {
        val index = indexOf(x, y)
        if (!significant[index] && !visitedInSignificancePass[index] && decodeDecision(zeroCodingContext(x, y), x, y) != 0) {
            markSignificant(x, y, bitPlane)
        }
    }

    private fun markSignificant(x: Int, y: Int, bitPlane: Int) {
        val (context, prediction) = signCodingContext(x, y)
        val negative = (decodeDecision(context, x, y, prediction) xor prediction) != 0
        val one = 1 shl bitPlane
        val initialMagnitude = one or (one ushr 1)
        val index = indexOf(x, y)
        coefficients[index] = if (negative) -initialMagnitude else initialMagnitude
        significant[index] = true
    }

    private fun zeroCodingContext(x: Int, y: Int): Int {
        val horizontal = isSignificant(x - 1, y).toInt() + isSignificant(x + 1, y).toInt()
        val vertical = isSignificant(x, y - 1).toInt() + isSignificant(x, y + 1).toInt()
        val diagonal = isSignificant(x - 1, y - 1).toInt() + isSignificant(x + 1, y - 1).toInt() +
            isSignificant(x - 1, y + 1).toInt() + isSignificant(x + 1, y + 1).toInt()
        return j2kZeroCodingContext(horizontal, vertical, diagonal, orientation)
    }

    private fun signCodingContext(x: Int, y: Int): Pair<Int, Int> {
        val rawHorizontal = axisSignContribution(x - 1, y, x + 1, y)
        val rawVertical = axisSignContribution(x, y - 1, x, y + 1)
        var horizontal = rawHorizontal
        var vertical = rawVertical
        if (horizontal < 0) {
            horizontal = -horizontal
            vertical = -vertical
        }
        val context = when (horizontal) {
            0 -> if (vertical == 0) 9 else 10
            else -> when (vertical) {
                -1 -> 11
                0 -> 12
                else -> 13
            }
        }
        // ISO/IEC 15444-1's SPB uses the *un-normalized* horizontal and
        // vertical contributions. In particular h=0,v<0 predicts a negative
        // sign, although the sign-context index remains 10.
        val prediction = if (
            rawHorizontal == 0 && rawVertical == 0 ||
            rawHorizontal > 0 || (rawHorizontal == 0 && rawVertical > 0)
        ) {
            0
        } else {
            1
        }
        return context to prediction
    }

    private fun axisSignContribution(firstX: Int, firstY: Int, secondX: Int, secondY: Int): Int {
        val first = signedNeighbourContribution(firstX, firstY)
        val second = signedNeighbourContribution(secondX, secondY)
        // Annex C clips positive and negative evidence independently before
        // subtracting it. `coerceIn(first + second)` is not equivalent when
        // both polarities are present on the same axis.
        return (if (first > 0 || second > 0) 1 else 0) - (if (first < 0 || second < 0) 1 else 0)
    }

    private fun magnitudeContext(x: Int, y: Int, index: Int): Int = when {
        refined[index] -> 16
        hasSignificantNeighbour(x, y) -> 15
        else -> 14
    }

    private fun signedNeighbourContribution(x: Int, y: Int): Int {
        if (x !in 0 until width || y !in 0 until height) return 0
        val coefficient = coefficients[indexOf(x, y)]
        return when {
            coefficient > 0 -> 1
            coefficient < 0 -> -1
            else -> 0
        }
    }

    private fun hasSignificantNeighbour(x: Int, y: Int): Boolean =
        isSignificant(x - 1, y - 1) || isSignificant(x, y - 1) || isSignificant(x + 1, y - 1) ||
            isSignificant(x - 1, y) || isSignificant(x + 1, y) ||
            isSignificant(x - 1, y + 1) || isSignificant(x, y + 1) || isSignificant(x + 1, y + 1)

    private fun isSignificant(x: Int, y: Int): Boolean =
        x in 0 until width && y in 0 until height && significant[indexOf(x, y)]

    private fun indexOf(x: Int, y: Int): Int = y * width + x

    private fun decodeDecision(context: Int, x: Int, y: Int, signPrediction: Int? = null): Int {
        val bit = mq.decode(context)
        trace?.invoke(
            J2kEbcotDecision(
                activePass,
                activeBitPlane,
                x,
                y,
                context,
                bit,
                mq.offset,
                signPrediction,
            ),
        )
        return bit
    }

    private fun Boolean.toInt(): Int = if (this) 1 else 0

    private companion object {
        const val SIGNIFICANCE_PASS = 0
        const val REFINEMENT_PASS = 1
        const val CLEANUP_PASS = 2
    }
}

/** EBCOT's normative stripe-major sample order for every Tier-1 pass. */
internal inline fun forEachEbcotStripe(
    width: Int,
    height: Int,
    visit: (x: Int, startY: Int, endY: Int) -> Unit,
) {
    var stripeStart = 0
    while (stripeStart < height) {
        val stripeEnd = minOf(stripeStart + 4, height)
        for (x in 0 until width) visit(x, stripeStart, stripeEnd)
        stripeStart = stripeEnd
    }
}

private fun narrowRawJ2kCodeblockWidths(width: Int): IntArray = when (width) {
    in 1..RAW_J2K_CODEBLOCK_WIDTH -> intArrayOf(width)
    in (RAW_J2K_CODEBLOCK_WIDTH + 1)..NARROW_RAW_J2K_MAX_WIDTH ->
        intArrayOf(RAW_J2K_CODEBLOCK_WIDTH, width - RAW_J2K_CODEBLOCK_WIDTH)
    else -> entropyFailure("jpeg2000.entropy.profile.unsupported", 0, Codec.Result.kUnimplemented)
}

/** Reversible integer 5/3 synthesis for one row or column with symmetric extension. */
internal fun inverseReversible53(low: IntArray, high: IntArray, length: Int): IntArray {
    require(length > 0)
    val evenCount = (length + 1) ushr 1
    val oddCount = length ushr 1
    require(low.size == evenCount)
    require(high.size == oddCount)
    if (oddCount == 0) return low.copyOf()

    val even = low.copyOf()
    for (index in even.indices) {
        val previousHigh = high[if (index == 0) 0 else index - 1]
        val nextHigh = high[minOf(index, oddCount - 1)]
        even[index] -= Math.floorDiv(previousHigh + nextHigh + 2, 4)
    }

    return IntArray(length).also { samples ->
        for (index in even.indices) samples[index shl 1] = even[index]
        for (index in high.indices) {
            val rightEven = even[minOf(index + 1, even.lastIndex)]
            samples[(index shl 1) + 1] = high[index] + Math.floorDiv(even[index] + rightEven, 2)
        }
    }
}

private fun floorLog2(value: Int): Int = 31 - Integer.numberOfLeadingZeros(value)
