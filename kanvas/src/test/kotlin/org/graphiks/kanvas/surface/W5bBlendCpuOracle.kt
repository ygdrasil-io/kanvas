@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.math.pow
import kotlin.test.assertTrue
import org.graphiks.math.color.ColorARGB

/** Independent public-test-only linear-premultiplied W5b fixed-function oracle. */
internal object W5bBlendCpuOracle {
    fun srgbBytes(color: ColorARGB): UByteArray = ubyteArrayOf(color.red.toUByte(), color.green.toUByte(), color.blue.toUByte(), color.alpha.toUByte())

    fun assertDstOver(background: ColorARGB, foreground: ColorARGB, opacityF32: Float, actual: UByteArray) {
        val destination = premul(background, 1f)
        val source = premul(foreground, opacityF32)
        val result = FloatArray(4) { index -> destination[index] + source[index] * (1f - destination[3]) }
        val expected = encode(result)
        actual.indices.forEach { index -> assertTrue(kotlin.math.abs(actual[index].toInt() - expected[index].toInt()) <= 1, "channel=$index expected=${expected.toList()} actual=${actual.toList()}") }
    }

    private fun premul(color: ColorARGB, opacityF32: Float): FloatArray {
        val alpha = color.alpha / 255f * opacityF32
        return floatArrayOf(linear(color.red / 255f) * alpha, linear(color.green / 255f) * alpha, linear(color.blue / 255f) * alpha, alpha)
    }
    private fun encode(value: FloatArray): UByteArray {
        val alpha = value[3].coerceIn(0f, 1f)
        fun channel(index: Int) = (value[index].coerceIn(0f, 1f).let(::srgb) * 255f + .5f).toInt().coerceIn(0, 255).toUByte()
        return ubyteArrayOf(channel(0), channel(1), channel(2), (alpha * 255f + .5f).toInt().toUByte())
    }
    private fun linear(value: Float): Float = if (value <= .04045f) value / 12.92f else ((value + .055f) / 1.055f).pow(2.4f)
    private fun srgb(value: Float): Float = if (value <= .0031308f) value * 12.92f else 1.055f * value.pow(1f / 2.4f) - .055f
}
