@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Independent CPU equations for the six public W6 lighting families.
 *
 * The fixture is deliberately small and hand-owned by this shard: a cross-shaped alpha height
 * field exposes both Sobel derivatives without depending on the renderer's captured filter data.
 */
object W6eLightingCpuOracle {
    enum class Family {
        DISTANT_DIFFUSE,
        POINT_DIFFUSE,
        SPOT_DIFFUSE,
        DISTANT_SPECULAR,
        POINT_SPECULAR,
        SPOT_SPECULAR,
    }

    private const val width = 3
    private const val height = 3
    private val alpha = floatArrayOf(
        0f, 1f, 0f,
        1f, 1f, 0f,
        0f, 1f, 0f,
    )

    fun distantDiffuse(): UByteArray = render(Family.DISTANT_DIFFUSE)
    fun pointDiffuse(): UByteArray = render(Family.POINT_DIFFUSE)
    fun spotDiffuse(): UByteArray = render(Family.SPOT_DIFFUSE)
    fun distantSpecular(): UByteArray = render(Family.DISTANT_SPECULAR)
    fun pointSpecular(): UByteArray = render(Family.POINT_SPECULAR)
    fun spotSpecular(): UByteArray = render(Family.SPOT_SPECULAR)

    private fun render(family: Family): UByteArray = UByteArray(width * height * 4).also { output ->
        for (y in 0 until height) for (x in 0 until width) {
            val normal = normalAt(x, y)
            val light = when (family) {
                Family.DISTANT_DIFFUSE, Family.DISTANT_SPECULAR -> unit(1f, 0f, 1f)
                else -> unit(1f - (x + .5f), -(y + .5f), 1f - alpha[y * width + x])
            }
            val cone = when (family) {
                Family.SPOT_DIFFUSE, Family.SPOT_SPECULAR -> spotCone(light)
                else -> 1f
            }
            val diffuse = if (normal == null || light == null) 0f else max(0f, dot(normal, light))
            val contribution = when (family) {
                Family.DISTANT_DIFFUSE, Family.POINT_DIFFUSE, Family.SPOT_DIFFUSE -> diffuse * cone
                else -> specular(normal, light) * cone
            }
            val linear = contribution.coerceIn(0f, 1f)
            val srgb = if (linear <= .0031308f) 12.92f * linear
            else 1.055f * linear.toDouble().pow(1.0 / 2.4).toFloat() - .055f
            val channel = (srgb * 255f).roundToInt().toUByte()
            val offset = (y * width + x) * 4
            output[offset] = channel
            output[offset + 1] = channel
            output[offset + 2] = channel
            output[offset + 3] = when (family) {
                Family.DISTANT_DIFFUSE, Family.POINT_DIFFUSE, Family.SPOT_DIFFUSE -> 255u
                else -> (linear * 255f).roundToInt().toUByte()
            }
        }
    }

    private fun normalAt(x: Int, y: Int): Triple<Float, Float, Float>? {
        fun sample(sampleX: Int, sampleY: Int): Float = alpha[
            sampleY.coerceIn(0, height - 1) * width + sampleX.coerceIn(0, width - 1)
        ]
        val dx = .25f * ((sample(x + 1, y - 1) + 2f * sample(x + 1, y) + sample(x + 1, y + 1)) -
            (sample(x - 1, y - 1) + 2f * sample(x - 1, y) + sample(x - 1, y + 1)))
        val dy = .25f * ((sample(x - 1, y + 1) + 2f * sample(x, y + 1) + sample(x + 1, y + 1)) -
            (sample(x - 1, y - 1) + 2f * sample(x, y - 1) + sample(x + 1, y - 1)))
        return unit(-dx, -dy, 1f)
    }

    private fun spotCone(light: Triple<Float, Float, Float>?): Float {
        val axis = unit(0f, 0f, -1f) ?: return 0f
        val value = light ?: return 0f
        val cosine = -dot(value, axis)
        val cutoff = kotlin.math.cos(Math.toRadians(90.0)).toFloat()
        if (cosine < cutoff || cosine < 0f) return 0f
        return cosine * min(1f, (cosine - cutoff) / .016f)
    }

    private fun specular(normal: Triple<Float, Float, Float>?, light: Triple<Float, Float, Float>?): Float {
        if (normal == null || light == null) return 0f
        val half = unit(light.first, light.second, light.third + 1f) ?: return 0f
        val base = dot(normal, half)
        return if (base <= 0f) 0f else base.pow(2f)
    }

    private fun unit(x: Float, y: Float, z: Float): Triple<Float, Float, Float>? {
        val dx = x.toDouble()
        val dy = y.toDouble()
        val dz = z.toDouble()
        val scale = max(abs(dx), max(abs(dy), abs(dz)))
        if (!scale.isFinite() || scale == 0.0) return null
        val length = sqrt((dx / scale) * (dx / scale) + (dy / scale) * (dy / scale) + (dz / scale) * (dz / scale))
        if (!length.isFinite()) return null
        return Triple((dx / scale / length).toFloat(), (dy / scale / length).toFloat(), (dz / scale / length).toFloat())
    }

    private fun dot(a: Triple<Float, Float, Float>, b: Triple<Float, Float, Float>): Float =
        a.first * b.first + a.second * b.second + a.third * b.third
}
