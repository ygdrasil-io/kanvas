package org.graphiks.kanvas.codec.jpeg

import java.io.OutputStream

/** Stable failure reported when an arithmetic JPEG scan is malformed. */
internal class ArithmeticJpegException(
    val diagnosticCode: String,
) : IllegalArgumentException(diagnosticCode)

internal fun arithmeticFailure(diagnosticCode: String): Nothing =
    throw ArithmeticJpegException(diagnosticCode)

/**
 * JPEG's QM binary arithmetic decoder (ITU-T T.81, annex D).
 *
 * Each entropy scan owns an instance.  The statistics bins are initialized at
 * scan start and reset at restart intervals according to the scan type.
 */
internal class ArithmeticDecoder(private val bytes: ByteArray) {
    private var offset = 0
    private var c = 0L
    private var a = 0L
    private var ct = -16
    private var unreadMarker = 0
    private var nextRestartMarker = 0

    private val dcStats = Array(16) { ByteArray(64) }
    private val acStats = Array(16) { ByteArray(256) }
    private val fixedBin = ByteArray(1) { FIXED_PROBABILITY_STATE.toByte() }

    fun decodeDcDifference(table: Int, context: Int, lower: Int, upper: Int): ArithmeticDcDifference {
        if (table !in dcStats.indices || context !in 0 until 64) arithmeticFailure("jpeg.arithmetic.scan.table")
        val stats = dcStats[table]
        var statIndex = context
        if (decode(stats, statIndex) == 0) return ArithmeticDcDifference(0, 0)

        val sign = decode(stats, statIndex + 1)
        statIndex += 2 + sign
        var magnitude = decode(stats, statIndex)
        if (magnitude != 0) {
            statIndex = 20
            while (decode(stats, statIndex) != 0) {
                magnitude = magnitude shl 1
                if (magnitude == 0x8000 || statIndex + 1 >= stats.size) {
                    arithmeticFailure("jpeg.arithmetic.dc.magnitude")
                }
                statIndex++
            }
        }
        val nextContext = when {
            magnitude < ((1 shl lower) shr 1) -> 0
            magnitude > ((1 shl upper) shr 1) -> 12 + sign * 4
            else -> 4 + sign * 4
        }
        var value = magnitude
        statIndex += 14
        while ((magnitude shr 1).also { magnitude = it } != 0) {
            if (decode(stats, statIndex) != 0) value = value or magnitude
        }
        value++
        return ArithmeticDcDifference(if (sign == 0) value else -value, nextContext)
    }

    fun decodeAcValue(table: Int, coefficient: Int, conditioningK: Int): Int {
        if (table !in acStats.indices || coefficient !in 1..63) arithmeticFailure("jpeg.arithmetic.scan.table")
        val stats = acStats[table]
        var statIndex = 3 * (coefficient - 1) + 2
        val sign = decode(fixedBin, 0)
        var magnitude = decode(stats, statIndex)
        if (magnitude != 0) {
            if (decode(stats, statIndex) != 0) {
                magnitude = magnitude shl 1
                statIndex = if (coefficient <= conditioningK) 189 else 217
                while (decode(stats, statIndex) != 0) {
                    magnitude = magnitude shl 1
                    if (magnitude == 0x8000 || statIndex + 1 >= stats.size) {
                        arithmeticFailure("jpeg.arithmetic.ac.magnitude")
                    }
                    statIndex++
                }
            }
        }
        var value = magnitude
        statIndex += 14
        while ((magnitude shr 1).also { magnitude = it } != 0) {
            if (decode(stats, statIndex) != 0) value = value or magnitude
        }
        value++
        return if (sign == 0) value else -value
    }

    fun decodeAcInitial(
        table: Int,
        startCoefficient: Int,
        endCoefficient: Int,
        conditioningK: Int,
        onValue: (coefficient: Int, value: Int) -> Unit,
    ) {
        if (table !in acStats.indices || startCoefficient !in 1..63 || endCoefficient !in startCoefficient..63) {
            arithmeticFailure("jpeg.arithmetic.scan.table")
        }
        val stats = acStats[table]
        var coefficient = startCoefficient
        while (coefficient <= endCoefficient) {
            var statIndex = 3 * (coefficient - 1)
            if (decode(stats, statIndex) != 0) return
            while (decode(stats, statIndex + 1) == 0) {
                coefficient++
                if (coefficient > endCoefficient) arithmeticFailure("jpeg.arithmetic.ac.spectral")
                statIndex += 3
            }
            val value = decodeAcValue(table, coefficient, conditioningK)
            onValue(coefficient, value)
            coefficient++
        }
    }

    fun decodeAcRefinement(
        table: Int,
        startCoefficient: Int,
        endCoefficient: Int,
        previousLastNonZero: Int,
        onExisting: (coefficient: Int) -> Boolean,
        onNew: (coefficient: Int, negative: Boolean) -> Unit,
    ) {
        if (table !in acStats.indices || startCoefficient !in 1..63 || endCoefficient !in startCoefficient..63) {
            arithmeticFailure("jpeg.arithmetic.scan.table")
        }
        val stats = acStats[table]
        var coefficient = startCoefficient
        while (coefficient <= endCoefficient) {
            var statIndex = 3 * (coefficient - 1)
            if (coefficient > previousLastNonZero && decode(stats, statIndex) != 0) return
            while (true) {
                if (onExisting(coefficient)) {
                    if (decode(stats, statIndex + 2) != 0) onNew(coefficient, false)
                    break
                }
                if (decode(stats, statIndex + 1) != 0) {
                    onNew(coefficient, decode(fixedBin, 0) != 0)
                    break
                }
                coefficient++
                if (coefficient > endCoefficient) arithmeticFailure("jpeg.arithmetic.ac.spectral")
                statIndex += 3
            }
            coefficient++
        }
    }

    fun resetForRestart(components: List<Component>, resetDc: Boolean, resetAc: Boolean) {
        if (unreadMarker == 0) unreadMarker = nextMarker()
        if (unreadMarker != 0xD0 + nextRestartMarker) arithmeticFailure("jpeg.arithmetic.restart.marker")
        unreadMarker = 0
        nextRestartMarker = (nextRestartMarker + 1) and 7
        for (component in components) {
            if (resetDc) dcStats[component.dcTable].fill(0)
            if (resetAc) acStats[component.acTable].fill(0)
        }
        c = 0
        a = 0
        ct = -16
    }

    fun fixedBit(): Int = decode(fixedBin, 0)

    /**
     * Decodes a QM bit using caller-owned contexts.
     *
     * Lossless arithmetic coding has Annex D's separate S0/SS/SP/SN and
     * magnitude context sets, so it must not reuse the DCT DC statistics.
     */
    internal fun decodeContext(contexts: ByteArray, index: Int): Int = decode(contexts, index)

    private fun decode(stats: ByteArray, index: Int): Int {
        if (index !in stats.indices) arithmeticFailure("jpeg.arithmetic.entropy.invalid")
        while (a < 0x8000) {
            if (--ct < 0) {
                val data = if (unreadMarker != 0) 0 else nextDataByte()
                c = (c shl 8) or data.toLong()
                ct += 8
                if (ct < 0) {
                    ct++
                    if (ct == 0) a = 0x8000
                }
            }
            a = a shl 1
        }

        var state = stats[index].toInt() and 0xFF
        val stateIndex = state and 0x7F
        val qe = Qe[stateIndex].toLong()
        val mpsState = NEXT_MPS[stateIndex]
        val lpsState = NEXT_LPS[stateIndex] or if (SWITCH_MPS[stateIndex] != 0) 0x80 else 0
        var interval = a - qe
        a = interval
        interval = interval shl ct
        if (c >= interval) {
            c -= interval
            if (a < qe) {
                a = qe
                stats[index] = ((state and 0x80) xor mpsState).toByte()
            } else {
                a = qe
                stats[index] = ((state and 0x80) xor lpsState).toByte()
                state = state xor 0x80
            }
        } else if (a < 0x8000) {
            if (a < qe) {
                stats[index] = ((state and 0x80) xor lpsState).toByte()
                state = state xor 0x80
            } else {
                stats[index] = ((state and 0x80) xor mpsState).toByte()
            }
        }
        return state ushr 7
    }

    private fun nextDataByte(): Int {
        if (offset >= bytes.size) {
            unreadMarker = 0xD9
            return 0
        }
        val value = bytes[offset++].toInt() and 0xFF
        if (value != 0xFF) return value
        while (offset < bytes.size && bytes[offset] == 0xFF.toByte()) offset++
        if (offset >= bytes.size) {
            unreadMarker = 0xD9
            return 0
        }
        val next = bytes[offset++].toInt() and 0xFF
        if (next == 0) return 0xFF
        unreadMarker = next
        return 0
    }

    private fun nextMarker(): Int {
        while (offset < bytes.size) {
            if (bytes[offset++].toInt() and 0xFF != 0xFF) continue
            while (offset < bytes.size && bytes[offset] == 0xFF.toByte()) offset++
            if (offset >= bytes.size) return 0xD9
            val marker = bytes[offset++].toInt() and 0xFF
            if (marker != 0) return marker
        }
        return 0xD9
    }

    internal data class ArithmeticDcDifference(val value: Int, val nextContext: Int)

    private companion object {
        const val FIXED_PROBABILITY_STATE = 113
    }
}

/**
 * JPEG's QM binary arithmetic encoder (ITU-T T.81, Annex D).
 *
 * The encoder keeps entropy state separate from the image writer so the
 * writer can select SOF9 without sharing any Huffman path.  It emits one
 * complete arithmetic scan at a time; [writeRestart] terminates the current
 * sub-scan before writing an unstuffed RST marker and resetting the contexts
 * required by sequential DCT coding.
 */
internal class ArithmeticEncoder(
    private val output: OutputStream,
    componentCount: Int,
) {
    private var c = 0L
    private var a = 0x10000L
    private var stackedFf = 0
    private var pendingZeros = 0
    private var bitCount = 11
    private var buffer = -1
    private var nextRestartMarker = 0

    private val previousDc = IntArray(componentCount)
    private val dcContexts = IntArray(componentCount)
    private val dcStats = Array(16) { ByteArray(64) }
    private val acStats = Array(16) { ByteArray(256) }
    private val fixedBin = ByteArray(1) { FIXED_PROBABILITY_STATE.toByte() }

    fun encodeSequentialBlock(
        coefficients: IntArray,
        component: Int,
        dcTable: Int,
        acTable: Int,
        dcLower: Int,
        dcUpper: Int,
        acK: Int,
        differential: Boolean = false,
    ) {
        require(coefficients.size == 64)
        require(component in previousDc.indices)
        require(dcTable in dcStats.indices && acTable in acStats.indices)
        require(dcLower in 0..15 && dcUpper in dcLower..15 && acK in 0..63)

        encodeDcDifference(component, dcTable, coefficients[0], dcLower, dcUpper, differential)
        encodeAcCoefficients(acTable, coefficients, acK)
    }

    /** Encodes one initial progressive DC coefficient (`Ah = Al = 0`). */
    fun encodeProgressiveDcInitial(
        component: Int,
        dcTable: Int,
        coefficient: Int,
        dcLower: Int,
        dcUpper: Int,
    ) {
        require(component in previousDc.indices)
        require(dcTable in dcStats.indices)
        require(dcLower in 0..15 && dcUpper in dcLower..15)
        encodeDcDifference(component, dcTable, coefficient, dcLower, dcUpper)
    }

    /**
     * Encodes one initial progressive AC band (`Ah = Al = 0`).  The EOB and
     * zero-run decisions deliberately follow Annex D's progressive grammar,
     * which differs from the sequential arithmetic AC scan.
     */
    fun encodeProgressiveAcInitial(
        acTable: Int,
        coefficients: IntArray,
        startCoefficient: Int,
        endCoefficient: Int,
        conditioningK: Int,
    ) {
        require(acTable in acStats.indices)
        require(coefficients.size == 64)
        require(startCoefficient in 1..63 && endCoefficient in startCoefficient..63)
        require(conditioningK in 0..63)

        val stats = acStats[acTable]
        var last = endCoefficient
        while (last >= startCoefficient && coefficients[last] == 0) last--

        var coefficient = startCoefficient
        while (coefficient <= last) {
            var statIndex = 3 * (coefficient - 1)
            encode(stats, statIndex, 0)
            var value = coefficients[coefficient]
            while (value == 0) {
                encode(stats, statIndex + 1, 0)
                coefficient++
                statIndex += 3
                value = coefficients[coefficient]
            }
            encode(stats, statIndex + 1, 1)
            encode(fixedBin, 0, if (value > 0) 0 else 1)
            encodeAcMagnitude(
                stats = stats,
                initialIndex = statIndex + 2,
                absolute = if (value > 0) value else -value,
                magnitudeStart = if (coefficient <= conditioningK) 189 else 217,
            )
            coefficient++
        }
        if (coefficient <= endCoefficient) encode(stats, 3 * (coefficient - 1), 1)
    }

    fun writeRestart(dcTables: IntArray, acTables: IntArray) {
        finish()
        output.write(0xFF)
        output.write(0xD0 + nextRestartMarker)
        nextRestartMarker = (nextRestartMarker + 1) and 7
        dcTables.forEach { table ->
            require(table in dcStats.indices)
            dcStats[table].fill(0)
        }
        acTables.forEach { table ->
            require(table in acStats.indices)
            acStats[table].fill(0)
        }
        previousDc.fill(0)
        dcContexts.fill(0)
        resetCodingInterval()
    }

    /** Terminates the current scan or restart interval according to Annex D. */
    fun finish() {
        val terminal = (a - 1 + c) and 0xFFFF0000L
        c = if (terminal < c) terminal + 0x8000L else terminal
        c = c shl bitCount
        if ((c and 0xF8000000L) != 0L) {
            if (buffer >= 0) {
                emitPendingZeros()
                emitDataByte(buffer + 1)
            }
            pendingZeros += stackedFf
            stackedFf = 0
        } else {
            if (buffer == 0) {
                pendingZeros++
            } else if (buffer >= 0) {
                emitPendingZeros()
                emitDataByte(buffer)
            }
            if (stackedFf > 0) {
                emitPendingZeros()
                repeat(stackedFf) { emitDataByte(0xFF) }
                stackedFf = 0
            }
        }
        if ((c and 0x07FFF800L) != 0L) {
            emitPendingZeros()
            val first = ((c ushr 19) and 0xFF).toInt()
            emitDataByte(first)
            if ((c and 0x0007F800L) != 0L) {
                emitDataByte(((c ushr 11) and 0xFF).toInt())
            }
        }
    }

    private fun encodeDcDifference(
        component: Int,
        table: Int,
        current: Int,
        lower: Int,
        upper: Int,
        differential: Boolean = false,
    ) {
        val stats = dcStats[table]
        var index = dcContexts[component]
        // Differential DCT frames transmit every DC coefficient relative to
        // the inter-frame prediction, not to the preceding block. The
        // arithmetic contexts still evolve across blocks exactly as Annex D
        // specifies, so only the within-frame DC predictor is bypassed.
        val difference = if (differential) current else current - previousDc[component]
        if (difference == 0) {
            encode(stats, index, 0)
            dcContexts[component] = 0
            return
        }

        if (!differential) previousDc[component] = current
        encode(stats, index, 1)
        val positive = difference > 0
        val absolute = if (positive) difference else -difference
        if (positive) {
            encode(stats, index + 1, 0)
            index += 2
            dcContexts[component] = 4
        } else {
            encode(stats, index + 1, 1)
            index += 3
            dcContexts[component] = 8
        }
        val magnitudeMask = encodeDcMagnitude(stats, index, absolute)
        if (magnitudeMask < ((1 shl lower) ushr 1)) {
            dcContexts[component] = 0
        } else if (magnitudeMask > ((1 shl upper) ushr 1)) {
            dcContexts[component] += 8
        }
    }

    private fun encodeAcCoefficients(table: Int, coefficients: IntArray, conditioningK: Int) {
        val stats = acStats[table]
        var last = 63
        while (last > 0 && coefficients[last] == 0) last--

        var coefficient = 1
        while (coefficient <= last) {
            var index = 3 * (coefficient - 1)
            encode(stats, index, 0)
            var value = coefficients[coefficient]
            while (value == 0) {
                encode(stats, index + 1, 0)
                index += 3
                coefficient++
                value = coefficients[coefficient]
            }
            encode(stats, index + 1, 1)
            val positive = value > 0
            encode(fixedBin, 0, if (positive) 0 else 1)
            val absolute = if (positive) value else -value
            encodeAcMagnitude(
                stats,
                index + 2,
                absolute,
                magnitudeStart = if (coefficient <= conditioningK) 189 else 217,
            )
            coefficient++
        }
        if (coefficient <= 63) {
            encode(stats, 3 * (coefficient - 1), 1)
        }
    }

    /** Returns the highest DC magnitude-category bit used for conditioning. */
    private fun encodeDcMagnitude(stats: ByteArray, initialIndex: Int, absolute: Int): Int {
        require(absolute > 0)
        var index = initialIndex
        val value = absolute - 1
        var magnitudeMask = 0
        if (value != 0) {
            encode(stats, index, 1)
            magnitudeMask = 1
            var probe = value
            index = 20
            while (true) {
                probe = probe ushr 1
                if (probe == 0) break
                encode(stats, index, 1)
                magnitudeMask = magnitudeMask shl 1
                index++
            }
        }
        encode(stats, index, 0)
        index += 14
        var patternMask = magnitudeMask
        while (patternMask > 1) {
            patternMask = patternMask ushr 1
            encode(stats, index, if ((patternMask and value) == 0) 0 else 1)
        }
        return magnitudeMask
    }

    private fun encodeAcMagnitude(stats: ByteArray, initialIndex: Int, absolute: Int, magnitudeStart: Int): Int {
        require(absolute > 0)
        var index = initialIndex
        var value = absolute - 1
        var magnitudeMask = 0
        if (value != 0) {
            encode(stats, index, 1)
            magnitudeMask = 1
            var probe = value ushr 1
            if (probe != 0) {
                encode(stats, index, 1)
                magnitudeMask = magnitudeMask shl 1
                index = magnitudeStart
                while (true) {
                    probe = probe ushr 1
                    if (probe == 0) break
                    encode(stats, index, 1)
                    magnitudeMask = magnitudeMask shl 1
                    index++
                }
            }
        }
        encode(stats, index, 0)
        index += 14
        var patternMask = magnitudeMask
        while (patternMask > 1) {
            patternMask = patternMask ushr 1
            encode(stats, index, if ((patternMask and value) == 0) 0 else 1)
        }
        return magnitudeMask
    }

    private fun encode(stats: ByteArray, index: Int, value: Int) {
        require(index in stats.indices && value in 0..1)
        val state = stats[index].toInt() and 0xFF
        val stateIndex = state and 0x7F
        val qe = Qe[stateIndex].toLong()
        val mps = state ushr 7
        a -= qe
        if (value != mps) {
            if (a >= qe) {
                c += a
                a = qe
            }
            stats[index] = ((state and 0x80) xor nextLpsState(stateIndex)).toByte()
        } else {
            if (a >= 0x8000L) return
            if (a < qe) {
                c += a
                a = qe
            }
            stats[index] = ((state and 0x80) xor NEXT_MPS[stateIndex]).toByte()
        }
        do {
            a = a shl 1
            c = c shl 1
            bitCount--
            if (bitCount == 0) emitReadyByte()
        } while (a < 0x8000L)
    }

    private fun nextLpsState(stateIndex: Int): Int =
        NEXT_LPS[stateIndex] or if (SWITCH_MPS[stateIndex] != 0) 0x80 else 0

    private fun emitReadyByte() {
        val value = c ushr 19
        when {
            value > 0xFF -> {
                if (buffer >= 0) {
                    emitPendingZeros()
                    emitDataByte(buffer + 1)
                }
                pendingZeros += stackedFf
                stackedFf = 0
                buffer = (value and 0xFF).toInt()
            }
            value == 0xFFL -> stackedFf++
            else -> {
                if (buffer == 0) {
                    pendingZeros++
                } else if (buffer >= 0) {
                    emitPendingZeros()
                    emitDataByte(buffer)
                }
                if (stackedFf > 0) {
                    emitPendingZeros()
                    repeat(stackedFf) { emitDataByte(0xFF) }
                    stackedFf = 0
                }
                buffer = value.toInt()
            }
        }
        c = c and 0x7FFFFL
        bitCount += 8
    }

    private fun emitPendingZeros() {
        repeat(pendingZeros) { emitDataByte(0) }
        pendingZeros = 0
    }

    private fun emitDataByte(value: Int) {
        output.write(value and 0xFF)
        if ((value and 0xFF) == 0xFF) output.write(0)
    }

    private fun resetCodingInterval() {
        c = 0L
        a = 0x10000L
        stackedFf = 0
        pendingZeros = 0
        bitCount = 11
        buffer = -1
    }

    private companion object {
        const val FIXED_PROBABILITY_STATE = 113
    }
}

private val Qe = intArrayOf(
    0x5a1d, 0x2586, 0x1114, 0x080b, 0x03d8, 0x01da, 0x00e5, 0x006f, 0x0036, 0x001a, 0x000d, 0x0006,
    0x0003, 0x0001, 0x5a7f, 0x3f25, 0x2cf2, 0x207c, 0x17b9, 0x1182, 0x0cef, 0x09a1, 0x072f, 0x055c,
    0x0406, 0x0303, 0x0240, 0x01b1, 0x0144, 0x00f5, 0x00b7, 0x008a, 0x0068, 0x004e, 0x003b, 0x002c,
    0x5ae1, 0x484c, 0x3a0d, 0x2ef1, 0x261f, 0x1f33, 0x19a8, 0x1518, 0x1177, 0x0e74, 0x0bfb, 0x09f8,
    0x0861, 0x0706, 0x05cd, 0x04de, 0x040f, 0x0363, 0x02d4, 0x025c, 0x01f8, 0x01a4, 0x0160, 0x0125,
    0x00f6, 0x00cb, 0x00ab, 0x008f, 0x5b12, 0x4d04, 0x412c, 0x37d8, 0x2fe8, 0x293c, 0x2379, 0x1edf,
    0x1aa9, 0x174e, 0x1424, 0x119c, 0x0f6b, 0x0d51, 0x0bb6, 0x0a40, 0x5832, 0x4d1c, 0x438e, 0x3bdd,
    0x34ee, 0x2eae, 0x299a, 0x2516, 0x5570, 0x4ca9, 0x44d9, 0x3e22, 0x3824, 0x32b4, 0x2e17, 0x56a8,
    0x4f46, 0x47e5, 0x41cf, 0x3c3d, 0x375e, 0x5231, 0x4c0f, 0x4639, 0x415e, 0x5627, 0x50e7, 0x4b85,
    0x5597, 0x504f, 0x5a10, 0x5522, 0x59eb, 0x5a1d,
)

private val NEXT_LPS = intArrayOf(
    1, 14, 16, 18, 20, 23, 25, 28, 30, 33, 35, 9, 10, 12, 15, 36, 38, 39, 40, 42, 43, 45, 46, 48,
    49, 51, 52, 54, 56, 57, 59, 60, 62, 63, 32, 33, 37, 64, 65, 67, 68, 69, 70, 72, 73, 74, 75, 77,
    78, 79, 48, 50, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 61, 61, 65, 80, 81, 82, 83, 84, 86, 87,
    87, 72, 72, 74, 74, 75, 77, 77, 80, 88, 89, 90, 91, 92, 93, 86, 88, 95, 96, 97, 99, 99, 93, 95,
    101, 102, 103, 104, 99, 105, 106, 107, 103, 105, 108, 109, 110, 111, 110, 112, 112, 113,
)

private val NEXT_MPS = intArrayOf(
    1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 13, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24,
    25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 9, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48,
    49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 32, 65, 66, 67, 68, 69, 70, 71, 72,
    73, 74, 75, 76, 77, 78, 79, 48, 81, 82, 83, 84, 85, 86, 87, 71, 89, 90, 91, 92, 93, 94, 86, 96,
    97, 98, 99, 100, 93, 102, 103, 104, 99, 106, 107, 103, 109, 107, 111, 109, 111, 113,
)

private val SWITCH_MPS = intArrayOf(
    1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 1, 0,
)
