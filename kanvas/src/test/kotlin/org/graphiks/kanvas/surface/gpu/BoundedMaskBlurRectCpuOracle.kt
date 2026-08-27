package org.graphiks.kanvas.surface.gpu

import java.security.MessageDigest
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor

/**
 * Fixed-fixture CPU oracle for `bounded-mask-blur-rect-v1`.
 *
 * This is deliberately not a general renderer and has no dependency on the
 * production planner, blur-kernel builder, payloads, or WebGPU. It spells out
 * the admitted sigma=2, scale=1 contract and quantizes each RGBA8 intermediate
 * exactly once, matching the five-pass native route.
 */
@OptIn(ExperimentalUnsignedTypes::class)
internal object BoundedMaskBlurRectCpuOracle {
    const val width = 32
    const val height = 32
    const val sigma = 2f
    const val activeTapCount = 5
    const val uniformWeightCapacity = 25
    const val command = "drawRect(8,8,17,17, MaskFilter.Blur(NORMAL, sigma=2))"

    private const val halo = 6
    private const val sourceLeft = 8
    private const val sourceTop = 8
    private const val sourceRight = 17
    private const val sourceBottom = 17
    private const val localLeft = sourceLeft - halo
    private const val localTop = sourceTop - halo
    private const val localRight = sourceRight + halo
    private const val localBottom = sourceBottom + halo
    private const val localWidth = localRight - localLeft
    private const val localHeight = localBottom - localTop

    fun render(): UByteArray {
        val mask = FloatArray(localWidth * localHeight) { index ->
            val x = index % localWidth
            val y = index / localWidth
            val deviceX = localLeft + x + 0.5f
            val deviceY = localTop + y + 0.5f
            if (deviceX >= sourceLeft && deviceX < sourceRight &&
                deviceY >= sourceTop && deviceY < sourceBottom
            ) 1f else 0f
        }
        val horizontal = blur(mask, horizontal = true)
        val vertical = blur(horizontal, horizontal = false)
        // NORMAL style copies the blurred coverage into a fourth RGBA8 target.
        val styled = vertical.map(::quantizeUnorm8).toFloatArray()

        return UByteArray(width * height * 4).also { out ->
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val px = x + 0.5f
                    val py = y + 0.5f
                    val coverage = if (px >= localLeft && px < localRight &&
                        py >= localTop && py < localBottom
                    ) {
                        val localX = (px - localLeft).toInt()
                        val localY = (py - localTop).toInt()
                        styled[localY * localWidth + localX]
                    } else {
                        0f
                    }
                    val index = (y * width + x) * 4
                    // Opaque black over transparent: RGB remains zero and the
                    // RGBA8 target quantizes the coverage alpha once more.
                    out[index + 3] = encodeByte(coverage)
                }
            }
        }
    }

    fun compare(cpu: UByteArray, gpu: UByteArray): Evidence {
        require(cpu.size == width * height * 4)
        require(gpu.size == cpu.size)
        var differentChannels = 0
        var maxDelta = 0
        var deltaSum = 0L
        cpu.indices.forEach { index ->
            val delta = kotlin.math.abs(cpu[index].toInt() - gpu[index].toInt())
            if (delta != 0) differentChannels += 1
            maxDelta = maxOf(maxDelta, delta)
            deltaSum += delta
        }
        return Evidence(
            cpuSha256 = sha256(cpu),
            gpuSha256 = sha256(gpu),
            differentChannels = differentChannels,
            maxDelta = maxDelta,
            meanDelta = deltaSum.toDouble() / cpu.size,
        )
    }

    data class Evidence(
        val cpuSha256: String,
        val gpuSha256: String,
        val differentChannels: Int,
        val maxDelta: Int,
        val meanDelta: Double,
    ) {
        fun canonicalString(): String =
            "cpuSha256=$cpuSha256,gpuSha256=$gpuSha256,differentChannels=$differentChannels," +
                "maxDelta=$maxDelta,meanDelta=$meanDelta,dimensions=${width}x${height},command=$command"
    }

    private fun blur(source: FloatArray, horizontal: Boolean): FloatArray {
        val weights = gaussianWeights()
        val half = weights.size / 2
        return FloatArray(source.size) { index ->
            val x = index % localWidth
            val y = index / localWidth
            var sum = 0f
            weights.forEachIndexed { tap, weight ->
                val offset = tap - half
                val sampleX = if (horizontal) x + offset else x
                val sampleY = if (horizontal) y else y + offset
                if (sampleX in 0 until localWidth && sampleY in 0 until localHeight) {
                    sum += weight * source[sampleY * localWidth + sampleX]
                }
            }
            quantizeUnorm8(sum)
        }
    }

    private fun gaussianWeights(): FloatArray {
        val taps = (ceil(sigma).toInt() * 2 + 1)
        check(taps == activeTapCount)
        val half = taps / 2
        val raw = FloatArray(taps) { index ->
            val x = (index - half).toFloat()
            exp(-(x * x) / (2f * sigma * sigma))
        }
        val total = raw.sum()
        return FloatArray(taps) { index -> raw[index] / total }
    }

    private fun quantizeUnorm8(value: Float): Float = encodeByte(value).toInt() / 255f

    private fun encodeByte(value: Float): UByte {
        val clamped = value.coerceIn(0f, 1f)
        return floor(clamped * 255f + 0.5f).toInt().coerceIn(0, 255).toUByte()
    }

    private fun sha256(bytes: UByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(ByteArray(bytes.size) { index -> bytes[index].toByte() })
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
