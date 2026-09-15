@file:OptIn(ExperimentalUnsignedTypes::class)
package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.ImmutableUBytes

internal fun normalizeNoiseSeedI32(seedI32: Int): Int = if (seedI32 <= 0)
    (-(seedI32.toLong() % 2147483646L) + 1L).toInt() else minOf(seedI32, 2147483646)

/** A table may be materialized only after its complete frame reservation was admitted. */
internal class NoiseTableV1 private constructor(
    val normalizedSeedI32: Int,
    val bytes: ImmutableUBytes,
) {
    companion object {
        const val BYTE_COUNT_I32: Int = 4352
        const val WORD_COUNT_I32: Int = 1088

        fun prepare(normalizedSeedI32: Int, owner: FrameSourceLayoutV4): NoiseTableV1 {
            require(normalizedSeedI32 in 1..2147483646 &&
                owner.noiseRange(normalizedSeedI32).normalizedSeedI32 == normalizedSeedI32) { W5gPlanDiagnostics.Schema }
            var state = normalizedSeedI32
            fun next(): Int {
                val candidate = 16807 * (state % 127773) - 2836 * (state / 127773)
                state = if (candidate <= 0) candidate + 2147483647 else candidate
                return state
            }
            val permutation = IntArray(256) { it }
            val raw = Array(4) { IntArray(512) { next() % 512 } }
            for (index in 255 downTo 1) {
                val other = next() % 256
                val saved = permutation[index]
                permutation[index] = permutation[other]
                permutation[other] = saved
            }
            val result = UByteArray(BYTE_COUNT_I32)
            permutation.forEachIndexed { index, value -> result[index] = value.toUByte() }
            for (channel in 0..3) for (index in 0..255) {
                val rawIndex = permutation[index] * 2
                val x = (raw[channel][rawIndex] - 256) / 256.0
                val y = (raw[channel][rawIndex + 1] - 256) / 256.0
                val length = StrictMath.sqrt(x * x + y * y)
                val reciprocal = if (length == 0.0) 0.0 else 1.0 / length
                for (axis in 0..1) {
                    val normalized = ((if (axis == 0) x else y) * reciprocal).toFloat()
                    val mappedF32 = (normalized + 1f) * 32767.5f
                    val code = kotlin.math.floor(mappedF32.toDouble() + .5).toInt()
                    require(code in 0..65535) { W5gPlanDiagnostics.Schema }
                    val offset = 256 + channel * 1024 + index * 4 + axis * 2
                    result[offset] = code.toUByte()
                    result[offset + 1] = (code ushr 8).toUByte()
                }
            }
            return NoiseTableV1(normalizedSeedI32, ImmutableUBytes.copyOf(result))
        }
    }
}

/** Exact range in the single physical frame slab; its seed is numerical binding data. */
public class NoiseTableRangeV1 internal constructor(
    public val normalizedSeedI32: Int,
    public val baseWordU32: UInt,
) {
    init {
        require(normalizedSeedI32 in 1..2147483646 && baseWordU32 % 4u == 0u) { W5gPlanDiagnostics.Schema }
    }
}

/** The same immutable bytes are used by the numeric proof and the native storage upload. */
public class NoiseTableSlabV1 private constructor(
    internal val owner: FrameSourceLayoutV4,
    public val bytes: ImmutableUBytes,
) {
    public val byteCountI64: Long get() = bytes.sizeI32.toLong()

    internal fun authenticates(range: NoiseTableRangeV1): Boolean =
        owner.noiseRange(range.normalizedSeedI32) === range &&
            range.baseWordU32.toLong() * 4L + NoiseTableV1.BYTE_COUNT_I32 <= byteCountI64

    companion object {
        internal fun prepare(owner: FrameSourceLayoutV4): NoiseTableSlabV1? {
            if (owner.noiseBytesI64 == 0L) return null
            val bytes = UByteArray(Math.toIntExact(owner.noiseBytesI64))
            owner.noiseRanges.forEach { range ->
                val table = NoiseTableV1.prepare(range.normalizedSeedI32, owner)
                val offset = Math.toIntExact(range.baseWordU32.toLong() * 4L)
                require(offset <= bytes.size - NoiseTableV1.BYTE_COUNT_I32) { W5gPlanDiagnostics.Schema }
                for (index in 0 until NoiseTableV1.BYTE_COUNT_I32) bytes[offset + index] = table.bytes[index]
            }
            return NoiseTableSlabV1(owner, ImmutableUBytes.copyOf(bytes))
        }
    }
}
