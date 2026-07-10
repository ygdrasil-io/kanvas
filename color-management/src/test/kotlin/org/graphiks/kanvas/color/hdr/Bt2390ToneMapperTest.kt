package org.graphiks.kanvas.color.hdr

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Bt2390ToneMapperTest {

    @Test
    fun `bt2390 full range pq to 100 nit sdr matches golden grays`() {
        val mapper = Bt2390ToneMapper(targetPeakNits = 100.0)
        val goldens = listOf(
            0f to 0f,
            1f to 0.01f,
            10f to 0.09763781f,
            100f to 0.47391912f,
            1000f to 0.91070396f,
            4000f to 0.99437356f,
            10_000f to 1f,
        )

        goldens.forEach { (inputNits, expected) ->
            val rgb = floatArrayOf(inputNits, inputNits, inputNits)
            mapper.map(rgb, 0)
            rgb.forEach { assertEquals(expected, it, 3e-5f, "input=$inputNits") }
        }
    }

    @Test
    fun `bt2390 output is finite and bounded by target white`() {
        val mapper = Bt2390ToneMapper(targetPeakNits = 100.0)
        val samples = listOf(
            floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY),
            floatArrayOf(-100f, 10_000f, 500f),
            floatArrayOf(10_000f, 0f, 0f),
            floatArrayOf(1000f, 500f, 100f),
        )

        samples.forEach { rgb ->
            mapper.map(rgb, 0)
            assertTrue(rgb.all(Float::isFinite), rgb.contentToString())
            assertTrue(rgb.all { it in 0f..1f }, rgb.contentToString())
        }
    }

    @Test
    fun `bt2390 gray mapping is monotonic across hdr range`() {
        val mapper = Bt2390ToneMapper(targetPeakNits = 100.0)
        var previous = -1f

        repeat(1001) { index ->
            val input = index * 10f
            val rgb = floatArrayOf(input, input, input)
            mapper.map(rgb, 0)
            assertTrue(rgb[0] + 2e-6f >= previous, "input=$input previous=$previous actual=${rgb[0]}")
            previous = rgb[0]
        }
    }

    @Test
    fun `bt2390 yRGB scaling preserves hue and normalized chroma`() {
        val mapper = Bt2390ToneMapper(targetPeakNits = 100.0)
        val input = floatArrayOf(1000f, 500f, 100f)
        val output = input.copyOf()

        mapper.map(output, 0)

        assertEquals(input[0] / input[1], output[0] / output[1], 2e-5f)
        assertEquals(input[1] / input[2], output[1] / output[2], 2e-5f)
        assertEquals(normalizedChroma(input), normalizedChroma(output), 2e-5f)
        assertTrue(output[0] > output[1] && output[1] > output[2])
    }

    @Test
    fun `bt2390 respects rgb offset and leaves surrounding storage unchanged`() {
        val mapper = Bt2390ToneMapper(targetPeakNits = 100.0)
        val storage = floatArrayOf(17f, 1000f, 1000f, 1000f, 23f)

        mapper.map(storage, 1)

        assertEquals(17f, storage[0], 0f)
        assertEquals(23f, storage[4], 0f)
        assertEquals(0.91070396f, storage[1], 3e-5f)
    }

    @Test
    fun `bt2390 rejects nonpositive or nonfinite target peaks`() {
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { peak ->
            assertFailsWith<IllegalArgumentException> { Bt2390ToneMapper(targetPeakNits = peak) }
        }
    }

    private fun normalizedChroma(rgb: FloatArray): Float {
        val maximum = rgb.max()
        val minimum = rgb.min()
        return if (abs(maximum) <= 1e-12f) 0f else (maximum - minimum) / maximum
    }
}
