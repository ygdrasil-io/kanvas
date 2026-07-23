package org.graphiks.kanvas.codec.jpegls

import java.io.ByteArrayOutputStream
import kotlin.math.abs

/** Clean-room LOCO-I regular/run mode implementation for the supported 8-bit JPEG-LS profile. */
internal object JpegLsEntropy {
    private val runJ: IntArray = intArrayOf(
        0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3,
        4, 4, 5, 5, 6, 6, 7, 7, 8, 9, 10, 11, 12, 13, 14, 15,
    )

    fun decode(source: ByteArray, scans: List<JpegLsScan>, frame: JpegLsFrame): IntArray {
        if (frame.components.size == 3 && frame.interleaveMode == 0) {
            return decodeRgbNonInterleavedScans(source, scans, frame)
        }
        val scan = scans.singleOrNull()
            ?: jpeglsFailure("jpeg-ls.scan.unsupported", scans.firstOrNull()?.markerOffset ?: 0, org.graphiks.kanvas.codec.Codec.Result.kUnimplemented)
        return when (frame.interleaveMode) {
            0 -> decodeMonochromeScan(source, scan.entropyOffset, scan.entropyEnd, frame)
            1 -> decodeRgbLineInterleavedScan(source, scan.entropyOffset, scan.entropyEnd, frame)
            2 -> decodeRgbSampleInterleavedScan(source, scan.entropyOffset, scan.entropyEnd, frame)
            else -> jpeglsFailure("jpeg-ls.scan.unsupported", scan.markerOffset, org.graphiks.kanvas.codec.Codec.Result.kUnimplemented)
        }
    }

    private fun decodeMonochromeScan(source: ByteArray, entropyOffset: Int, entropyEnd: Int, frame: JpegLsFrame): IntArray {
        val reader = JpegLsBitReader(source, entropyOffset, entropyEnd)
        val state = LocoState(frame)
        var previous = IntArray(frame.width + 2)
        var current = IntArray(frame.width + 2)
        val output = IntArray(frame.width * frame.height)

        for (y in 0 until frame.height) {
            previous[frame.width + 1] = previous[frame.width]
            current[0] = previous[1]
            var index = 1
            while (index <= frame.width) {
                val ra = current[index - 1]
                val rb = previous[index]
                val rc = previous[index - 1]
                val rd = previous[index + 1]
                val context = state.context(rd - rb, rb - rc, rc - ra)
                if (context.index != 0) {
                    current[index] = state.decodeRegular(reader, context, predict(ra, rb, rc))
                    index++
                } else {
                    val run = state.decodeRun(reader, frame.width - index + 1)
                    if (run > frame.width - index + 1) jpeglsFailure("jpeg-ls.run.invalid", reader.offset)
                    repeat(run) { offset -> current[index + offset] = ra }
                    index += run
                    if (index <= frame.width) {
                        current[index] = state.decodeRunInterruption(reader, ra, previous[index])
                        state.decrementRunIndex()
                        index++
                    }
                }
            }
            for (x in 0 until frame.width) output[y * frame.width + x] = current[x + 1]
            val swap = previous
            previous = current
            current = swap
        }
        reader.finishAtEntropyEnd()
        return output
    }

    /** Decode three independent ILV=0 planes and normalize them to interleaved RGB output. */
    private fun decodeRgbNonInterleavedScans(source: ByteArray, scans: List<JpegLsScan>, frame: JpegLsFrame): IntArray {
        check(scans.size == 3)
        val output = IntArray(frame.width * frame.height * 3)
        scans.forEachIndexed { component, scan ->
            val planeFrame = frame.copy(components = listOf(JpegLsComponent(component + 1)), interleaveMode = 0)
            val plane = decodeMonochromeScan(source, scan.entropyOffset, scan.entropyEnd, planeFrame)
            plane.forEachIndexed { index, sample -> output[index * 3 + component] = sample }
        }
        return output
    }

    /**
     * T.87 line-interleaved RGB keeps one adaptive regular/run context set for
     * the scan, while each component owns its line history and run index. The
     * coded order is R-line, G-line, B-line for every image row.
     */
    private fun decodeRgbLineInterleavedScan(source: ByteArray, entropyOffset: Int, entropyEnd: Int, frame: JpegLsFrame): IntArray {
        val reader = JpegLsBitReader(source, entropyOffset, entropyEnd)
        val state = LocoState(frame)
        val components = frame.components.size
        check(components == 3)
        var previous = Array(components) { IntArray(frame.width + 2) }
        var current = Array(components) { IntArray(frame.width + 2) }
        val runIndices = IntArray(components)
        val output = IntArray(frame.width * frame.height * components)

        for (y in 0 until frame.height) {
            for (component in 0 until components) {
                previous[component][frame.width + 1] = previous[component][frame.width]
                current[component][0] = previous[component][1]
            }
            for (component in 0 until components) {
                state.runIndex = runIndices[component]
                decodeLine(reader, state, previous[component], current[component], frame.width)
                runIndices[component] = state.runIndex
                for (x in 0 until frame.width) {
                    output[(y * frame.width + x) * components + component] = current[component][x + 1]
                }
            }
            val swap = previous
            previous = current
            current = swap
        }
        reader.finishAtEntropyEnd()
        return output
    }

    /**
     * T.87 sample-interleaved RGB processes one triplet per pixel. Regular
     * contexts are shared by all three components; run mode is selected only
     * when all three gradients quantify to zero and its interruption uses run
     * context 0 independently for each member of the triplet.
     */
    private fun decodeRgbSampleInterleavedScan(source: ByteArray, entropyOffset: Int, entropyEnd: Int, frame: JpegLsFrame): IntArray {
        val reader = JpegLsBitReader(source, entropyOffset, entropyEnd)
        val state = LocoState(frame)
        val components = frame.components.size
        check(components == 3)
        var previous = Array(components) { IntArray(frame.width + 2) }
        var current = Array(components) { IntArray(frame.width + 2) }
        val output = IntArray(frame.width * frame.height * components)

        for (y in 0 until frame.height) {
            for (component in 0 until components) {
                previous[component][frame.width + 1] = previous[component][frame.width]
                current[component][0] = previous[component][1]
            }
            var index = 1
            while (index <= frame.width) {
                val contexts = Array(components) { component ->
                    val ra = current[component][index - 1]
                    val rb = previous[component][index]
                    val rc = previous[component][index - 1]
                    val rd = previous[component][index + 1]
                    state.context(rd - rb, rb - rc, rc - ra)
                }
                if (contexts.all { it.index == 0 }) {
                    val run = state.decodeRun(reader, frame.width - index + 1)
                    if (run > frame.width - index + 1) jpeglsFailure("jpeg-ls.run.invalid", reader.offset)
                    repeat(run) { offset ->
                        for (component in 0 until components) {
                            current[component][index + offset] = current[component][index - 1]
                        }
                    }
                    index += run
                    if (index <= frame.width) {
                        for (component in 0 until components) {
                            current[component][index] = state.decodeSampleInterleavedRunInterruption(
                                reader,
                                current[component][index - 1],
                                previous[component][index],
                            )
                        }
                        state.decrementRunIndex()
                        index++
                    }
                } else {
                    for (component in 0 until components) {
                        val ra = current[component][index - 1]
                        val rb = previous[component][index]
                        val rc = previous[component][index - 1]
                        current[component][index] = state.decodeRegular(reader, contexts[component], predict(ra, rb, rc))
                    }
                    index++
                }
            }
            for (x in 0 until frame.width) {
                for (component in 0 until components) {
                    output[(y * frame.width + x) * components + component] = current[component][x + 1]
                }
            }
            val swap = previous
            previous = current
            current = swap
        }
        reader.finishAtEntropyEnd()
        return output
    }

    private fun decodeLine(
        reader: JpegLsBitReader,
        state: LocoState,
        previous: IntArray,
        current: IntArray,
        width: Int,
    ) {
        var index = 1
        while (index <= width) {
            val ra = current[index - 1]
            val rb = previous[index]
            val rc = previous[index - 1]
            val rd = previous[index + 1]
            val context = state.context(rd - rb, rb - rc, rc - ra)
            if (context.index != 0) {
                current[index] = state.decodeRegular(reader, context, predict(ra, rb, rc))
                index++
            } else {
                val run = state.decodeRun(reader, width - index + 1)
                if (run > width - index + 1) jpeglsFailure("jpeg-ls.run.invalid", reader.offset)
                repeat(run) { offset -> current[index + offset] = ra }
                index += run
                if (index <= width) {
                    current[index] = state.decodeRunInterruption(reader, ra, previous[index])
                    state.decrementRunIndex()
                    index++
                }
            }
        }
    }

    fun encode(width: Int, height: Int, samples: IntArray, frame: JpegLsFrame): ByteArray {
        require(samples.size == width * height * frame.components.size)
        return when (frame.interleaveMode) {
            0 -> encodeMonochrome(width, height, samples, frame)
            1 -> encodeRgbLineInterleaved(width, height, samples, frame)
            2 -> encodeRgbSampleInterleaved(width, height, samples, frame)
            else -> error("unsupported JPEG-LS interleave mode ${frame.interleaveMode}")
        }
    }

    /** Encode one entropy segment per JPEG-LS scan in the validated frame layout. */
    fun encodeScans(width: Int, height: Int, samples: IntArray, frame: JpegLsFrame): List<ByteArray> {
        require(samples.size == width * height * frame.components.size)
        if (frame.components.size == 3 && frame.interleaveMode == 0) {
            return List(3) { component ->
                val plane = IntArray(width * height) { index -> samples[index * 3 + component] }
                val planeFrame = frame.copy(components = listOf(JpegLsComponent(component + 1)), interleaveMode = 0)
                encodeMonochrome(width, height, plane, planeFrame)
            }
        }
        return listOf(encode(width, height, samples, frame))
    }

    private fun encodeMonochrome(width: Int, height: Int, samples: IntArray, frame: JpegLsFrame): ByteArray {
        val writer = JpegLsBitWriter()
        val state = LocoState(frame)
        var previous = IntArray(width + 2)
        var current = IntArray(width + 2)

        for (y in 0 until height) {
            previous[width + 1] = previous[width]
            current[0] = previous[1]
            var index = 1
            while (index <= width) {
                val ra = current[index - 1]
                val rb = previous[index]
                val rc = previous[index - 1]
                val rd = previous[index + 1]
                val context = state.context(rd - rb, rb - rc, rc - ra)
                if (context.index != 0) {
                    val sample = samples[y * width + index - 1]
                    current[index] = state.encodeRegular(writer, context, predict(ra, rb, rc), sample)
                    index++
                } else {
                    var run = 0
                    while (
                        run < width - index + 1 &&
                        state.isNear(samples[y * width + index - 1 + run], ra)
                    ) {
                        run++
                    }
                    state.encodeRun(writer, run, run == width - index + 1)
                    repeat(run) { offset -> current[index + offset] = ra }
                    index += run
                    if (index <= width) {
                        val sample = samples[y * width + index - 1]
                        current[index] = state.encodeRunInterruption(writer, sample, ra, previous[index])
                        state.decrementRunIndex()
                        index++
                    }
                }
            }
            val swap = previous
            previous = current
            current = swap
        }
        return writer.finish()
    }

    private fun encodeRgbLineInterleaved(
        width: Int,
        height: Int,
        samples: IntArray,
        frame: JpegLsFrame,
    ): ByteArray {
        val writer = JpegLsBitWriter()
        val state = LocoState(frame)
        val components = frame.components.size
        check(components == 3)
        var previous = Array(components) { IntArray(width + 2) }
        var current = Array(components) { IntArray(width + 2) }
        val runIndices = IntArray(components)

        for (y in 0 until height) {
            for (component in 0 until components) {
                previous[component][width + 1] = previous[component][width]
                current[component][0] = previous[component][1]
                for (x in 0 until width) {
                    current[component][x + 1] = samples[(y * width + x) * components + component]
                }
            }
            for (component in 0 until components) {
                state.runIndex = runIndices[component]
                encodeLine(writer, state, previous[component], current[component], width)
                runIndices[component] = state.runIndex
            }
            val swap = previous
            previous = current
            current = swap
        }
        return writer.finish()
    }

    /** Inverse of [decodeRgbSampleInterleavedScan], processing RGB triplets by pixel. */
    private fun encodeRgbSampleInterleaved(
        width: Int,
        height: Int,
        samples: IntArray,
        frame: JpegLsFrame,
    ): ByteArray {
        val writer = JpegLsBitWriter()
        val state = LocoState(frame)
        val components = frame.components.size
        check(components == 3)
        var previous = Array(components) { IntArray(width + 2) }
        var current = Array(components) { IntArray(width + 2) }

        for (y in 0 until height) {
            for (component in 0 until components) {
                previous[component][width + 1] = previous[component][width]
                current[component][0] = previous[component][1]
                for (x in 0 until width) {
                    current[component][x + 1] = samples[(y * width + x) * components + component]
                }
            }
            var index = 1
            while (index <= width) {
                val contexts = Array(components) { component ->
                    val ra = current[component][index - 1]
                    val rb = previous[component][index]
                    val rc = previous[component][index - 1]
                    val rd = previous[component][index + 1]
                    state.context(rd - rb, rb - rc, rc - ra)
                }
                if (contexts.all { it.index == 0 }) {
                    var run = 0
                    while (
                        run < width - index + 1 &&
                        (0 until components).all { component ->
                            state.isNear(current[component][index + run], current[component][index - 1])
                        }
                    ) {
                        run++
                    }
                    state.encodeRun(writer, run, run == width - index + 1)
                    repeat(run) { offset ->
                        for (component in 0 until components) {
                            current[component][index + offset] = current[component][index - 1]
                        }
                    }
                    index += run
                    if (index <= width) {
                        for (component in 0 until components) {
                            current[component][index] = state.encodeSampleInterleavedRunInterruption(
                                writer,
                                current[component][index],
                                current[component][index - 1],
                                previous[component][index],
                            )
                        }
                        state.decrementRunIndex()
                        index++
                    }
                } else {
                    for (component in 0 until components) {
                        val ra = current[component][index - 1]
                        val rb = previous[component][index]
                        val rc = previous[component][index - 1]
                        current[component][index] = state.encodeRegular(
                            writer,
                            contexts[component],
                            predict(ra, rb, rc),
                            current[component][index],
                        )
                    }
                    index++
                }
            }
            val swap = previous
            previous = current
            current = swap
        }
        return writer.finish()
    }

    private fun encodeLine(
        writer: JpegLsBitWriter,
        state: LocoState,
        previous: IntArray,
        current: IntArray,
        width: Int,
    ) {
        var index = 1
        while (index <= width) {
            val ra = current[index - 1]
            val rb = previous[index]
            val rc = previous[index - 1]
            val rd = previous[index + 1]
            val context = state.context(rd - rb, rb - rc, rc - ra)
            if (context.index != 0) {
                current[index] = state.encodeRegular(writer, context, predict(ra, rb, rc), current[index])
                index++
            } else {
                var run = 0
                while (run < width - index + 1 && state.isNear(current[index + run], ra)) {
                    run++
                }
                state.encodeRun(writer, run, run == width - index + 1)
                repeat(run) { offset -> current[index + offset] = ra }
                index += run
                if (index <= width) {
                    current[index] = state.encodeRunInterruption(writer, current[index], ra, previous[index])
                    state.decrementRunIndex()
                    index++
                }
            }
        }
    }

    private fun predict(ra: Int, rb: Int, rc: Int): Int = when {
        rc >= maxOf(ra, rb) -> minOf(ra, rb)
        rc <= minOf(ra, rb) -> maxOf(ra, rb)
        else -> ra + rb - rc
    }

    private class LocoState(
        private val frame: JpegLsFrame,
    ) {
        private val parameters = frame.parameters
        private val nearLossless = frame.nearLossless
        private val range = (parameters.maximumSampleValue + 2 * nearLossless) / (2 * nearLossless + 1) + 1
        private val qbpp = ceilLog2(range)
        private val initialA = maxOf(2, (range + 32) / 64)
        private val regular = Array(365) { RegularContext(initialA) }
        private val run = arrayOf(RunContext(0, initialA), RunContext(1, initialA))
        private var runIndexValue: Int = 0

        var runIndex: Int
            get() = runIndexValue
            set(value) {
                require(value in 0..31)
                runIndexValue = value
            }

        fun context(d1: Int, d2: Int, d3: Int): Context {
            var q1 = quantizeGradient(d1)
            var q2 = quantizeGradient(d2)
            var q3 = quantizeGradient(d3)
            val negative = q1 < 0 || (q1 == 0 && q2 < 0) || (q1 == 0 && q2 == 0 && q3 < 0)
            if (negative) {
                q1 = -q1
                q2 = -q2
                q3 = -q3
            }
            return Context((q1 * 9 + q2) * 9 + q3, if (negative) -1 else 1)
        }

        fun decodeRegular(reader: JpegLsBitReader, context: Context, predicted: Int): Int {
            val stats = regular[context.index]
            val corrected = correctPrediction(predicted + context.sign * stats.c)
            val k = stats.golombK()
            var error = unmap(readMapped(reader, k, LIMIT, qbpp))
            if (k == 0) error = error xor stats.errorCorrection(nearLossless)
            stats.update(error, nearLossless, parameters.reset)
            return reconstruct(corrected, context.sign * error)
        }

        fun encodeRegular(writer: JpegLsBitWriter, context: Context, predicted: Int, sample: Int): Int {
            val stats = regular[context.index]
            val corrected = correctPrediction(predicted + context.sign * stats.c)
            val k = stats.golombK()
            val error = computeError(context.sign * (sample - corrected))
            val correctedError = if (k == 0) error xor stats.errorCorrection(nearLossless) else error
            writeMapped(writer, map(correctedError), k, LIMIT, qbpp)
            stats.update(error, nearLossless, parameters.reset)
            return reconstruct(corrected, context.sign * error)
        }

        fun decodeRun(reader: JpegLsBitReader, remaining: Int): Int {
            var length = 0
            while (reader.readBit() == 1) {
                val step = minOf(1 shl runJ[runIndexValue], remaining - length)
                length += step
                if (step == (1 shl runJ[runIndexValue])) incrementRunIndex()
                if (length == remaining) return length
            }
            if (runJ[runIndexValue] > 0) length += reader.readBits(runJ[runIndexValue])
            return length
        }

        fun encodeRun(writer: JpegLsBitWriter, length: Int, endOfLine: Boolean) {
            var remaining = length
            while (remaining >= (1 shl runJ[runIndexValue])) {
                writer.writeBit(1)
                remaining -= 1 shl runJ[runIndexValue]
                incrementRunIndex()
            }
            if (endOfLine) {
                if (remaining != 0) writer.writeBit(1)
            } else {
                writer.writeBit(0)
                if (runJ[runIndexValue] > 0) writer.writeBits(remaining, runJ[runIndexValue])
            }
        }

        fun decodeRunInterruption(reader: JpegLsBitReader, ra: Int, rb: Int): Int {
            val type = if (isNear(ra, rb)) 1 else 0
            val stats = run[type]
            val k = stats.golombK()
            val mapped = readMapped(reader, k, LIMIT - runJ[runIndexValue] - 1, qbpp)
            val error = stats.unmap(mapped + type, k)
            stats.update(error, mapped, parameters.reset)
            val prediction = if (type == 1) ra else rb
            val sign = if (type == 1) 1 else sign(rb - ra)
            return reconstruct(prediction, sign * error)
        }

        fun decodeSampleInterleavedRunInterruption(reader: JpegLsBitReader, ra: Int, rb: Int): Int {
            val stats = run[0]
            val k = stats.golombK()
            val mapped = readMapped(reader, k, LIMIT - runJ[runIndexValue] - 1, qbpp)
            val error = stats.unmap(mapped, k)
            stats.update(error, mapped, parameters.reset)
            return reconstruct(rb, sign(rb - ra) * error)
        }

        fun encodeRunInterruption(writer: JpegLsBitWriter, sample: Int, ra: Int, rb: Int): Int {
            val type = if (isNear(ra, rb)) 1 else 0
            val stats = run[type]
            val prediction = if (type == 1) ra else rb
            val direction = if (type == 1) 1 else sign(rb - ra)
            val error = computeError(direction * (sample - prediction))
            val k = stats.golombK()
            val mapped = stats.map(error, k)
            writeMapped(writer, mapped, k, LIMIT - runJ[runIndexValue] - 1, qbpp)
            stats.update(error, mapped, parameters.reset)
            return reconstruct(prediction, direction * error)
        }

        fun encodeSampleInterleavedRunInterruption(
            writer: JpegLsBitWriter,
            sample: Int,
            ra: Int,
            rb: Int,
        ): Int {
            val stats = run[0]
            val direction = sign(rb - ra)
            val error = computeError(direction * (sample - rb))
            val k = stats.golombK()
            val mapped = stats.map(error, k)
            writeMapped(writer, mapped, k, LIMIT - runJ[runIndexValue] - 1, qbpp)
            stats.update(error, mapped, parameters.reset)
            return reconstruct(rb, direction * error)
        }

        fun decrementRunIndex() {
            if (runIndexValue > 0) runIndexValue--
        }

        private fun incrementRunIndex() {
            if (runIndexValue < 31) runIndexValue++
        }

        fun isNear(first: Int, second: Int): Boolean = abs(first - second) <= nearLossless

        private fun quantizeGradient(delta: Int): Int = when {
            delta <= -parameters.threshold3 -> -4
            delta <= -parameters.threshold2 -> -3
            delta <= -parameters.threshold1 -> -2
            delta < -nearLossless -> -1
            delta <= nearLossless -> 0
            delta < parameters.threshold1 -> 1
            delta < parameters.threshold2 -> 2
            delta < parameters.threshold3 -> 3
            else -> 4
        }

        private fun computeError(error: Int): Int {
            val step = 2 * nearLossless + 1
            val quantized = if (error > 0) (error + nearLossless) / step else -(nearLossless - error) / step
            return moduloRange(quantized)
        }

        private fun moduloRange(error: Int): Int {
            var value = error
            if (value < 0) value += range
            if (value >= (range + 1) / 2) value -= range
            return value
        }

        private fun correctPrediction(predicted: Int): Int = predicted.coerceIn(0, parameters.maximumSampleValue)

        private fun reconstruct(predicted: Int, error: Int): Int {
            val step = 2 * nearLossless + 1
            var value = predicted + error * step
            if (value < -nearLossless) {
                value += range * step
            } else if (value > parameters.maximumSampleValue + nearLossless) {
                value -= range * step
            }
            return correctPrediction(value)
        }
    }

    private data class Context(val index: Int, val sign: Int)

    private class RegularContext(initialA: Int) {
        var a: Int = initialA
        var b: Int = 0
        var c: Int = 0
        var n: Int = 1

        fun golombK(): Int {
            var k = 0
            while ((n shl k) < a) k++
            return k
        }

        fun errorCorrection(nearLossless: Int): Int =
            if (nearLossless == 0 && 2 * b + n - 1 < 0) -1 else 0

        fun update(error: Int, nearLossless: Int, reset: Int) {
            a += abs(error)
            b += error * (2 * nearLossless + 1)
            if (n == reset) {
                a = a shr 1
                b = b shr 1
                n = n shr 1
            }
            n++
            if (b + n <= 0) {
                b += n
                if (b <= -n) b = -n + 1
                if (c > -128) c--
            } else if (b > 0) {
                b -= n
                if (b > 0) b = 0
                if (c < 127) c++
            }
        }
    }

    private class RunContext(private val type: Int, initialA: Int) {
        private var a: Int = initialA
        private var n: Int = 1
        private var nn: Int = 0

        fun golombK(): Int {
            val target = a + (n shr 1) * type
            var k = 0
            while ((n shl k) < target) k++
            return k
        }

        fun map(error: Int, k: Int): Int {
            val map = (k == 0 && error > 0 && 2 * nn < n) ||
                (error < 0 && (2 * nn >= n || k != 0))
            return 2 * abs(error) - type - if (map) 1 else 0
        }

        fun unmap(value: Int, k: Int): Int {
            val map = (value and 1) != 0
            val magnitude = (value + if (map) 1 else 0) / 2
            return if ((k != 0 || 2 * nn >= n) == map) -magnitude else magnitude
        }

        fun update(error: Int, mapped: Int, reset: Int) {
            if (error < 0) nn++
            a += (mapped + 1 - type) shr 1
            if (n == reset) {
                a = a shr 1
                n = n shr 1
                nn = nn shr 1
            }
            n++
        }
    }

    private fun sign(value: Int): Int = if (value < 0) -1 else 1
    private fun map(error: Int): Int = if (error >= 0) error * 2 else -error * 2 - 1
    private fun unmap(value: Int): Int = if ((value and 1) == 0) value / 2 else -(value / 2) - 1

    private fun ceilLog2(value: Int): Int {
        var result = 0
        while (value > (1 shl result)) result++
        return result
    }

    private fun writeMapped(writer: JpegLsBitWriter, value: Int, k: Int, limit: Int, qbpp: Int) {
        val quotient = value shr k
        val escape = limit - qbpp - 1
        if (quotient < escape) {
            repeat(quotient) { writer.writeBit(0) }
            writer.writeBit(1)
            if (k > 0) writer.writeBits(value and ((1 shl k) - 1), k)
        } else {
            repeat(escape) { writer.writeBit(0) }
            writer.writeBit(1)
            writer.writeBits(value - 1, qbpp)
        }
    }

    private fun readMapped(reader: JpegLsBitReader, k: Int, limit: Int, qbpp: Int): Int {
        var unary = 0
        while (reader.readBit() == 0) {
            unary++
            if (unary > limit) jpeglsFailure("jpeg-ls.golomb.invalid", reader.offset)
        }
        val escape = limit - qbpp - 1
        return if (unary < escape) {
            (unary shl k) + if (k > 0) reader.readBits(k) else 0
        } else {
            reader.readBits(qbpp) + 1
        }
    }

    private class JpegLsBitReader(
        private val source: ByteArray,
        start: Int,
        private val end: Int,
    ) {
        private var position: Int = start
        private var current: Int = 0
        private var remaining: Int = 0
        private var previousWasFf: Boolean = false

        val offset: Int get() = position

        fun readBit(): Int {
            if (remaining == 0) loadByte()
            remaining--
            return (current ushr remaining) and 1
        }

        fun readBits(count: Int): Int {
            var value = 0
            repeat(count) { value = (value shl 1) or readBit() }
            return value
        }

        fun finishAtEntropyEnd() {
            // T.87 permits residual alignment bits after the final sample. The
            // parser has already located the next marker, so consume only the
            // bounded entropy span rather than inventing a padding bit value.
            while (remaining != 0 || position < end) readBit()
        }

        private fun loadByte() {
            if (position >= end) jpeglsFailure("jpeg-ls.entropy.truncated", position)
            val value = source[position++].u8()
            current = value
            remaining = if (previousWasFf) 7 else 8
            previousWasFf = value == MARKER_PREFIX
        }
    }

    private class JpegLsBitWriter {
        private val out = ByteArrayOutputStream()
        private var value: Int = 0
        private var count: Int = 0
        private var capacity: Int = 8
        private var previousWasFf: Boolean = false

        fun writeBit(bit: Int) {
            value = (value shl 1) or (bit and 1)
            count++
            if (count == capacity) flushByte()
        }

        fun writeBits(bits: Int, count: Int) {
            for (shift in count - 1 downTo 0) writeBit(bits ushr shift)
        }

        fun finish(): ByteArray {
            if (count != 0) {
                value = value shl (capacity - count)
                count = capacity
                flushByte()
            }
            if (previousWasFf) {
                writeBit(0)
                value = value shl (capacity - count)
                count = capacity
                flushByte()
            }
            return out.toByteArray()
        }

        private fun flushByte() {
            out.write(value)
            previousWasFf = value == MARKER_PREFIX
            capacity = if (previousWasFf) 7 else 8
            value = 0
            count = 0
        }
    }

    private const val LIMIT: Int = 32
}
