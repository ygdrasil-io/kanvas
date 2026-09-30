@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.skia

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.skia.gm.gradient.HardstopGradientShaderGm
import org.graphiks.kanvas.skia.gm.gradient.HardstopGradientsManyGm
import org.graphiks.kanvas.test.GpuAvailability
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

class HardstopGradientSurfacePixelTest {
    @AfterEach
    fun disposeSharedBackend() {
        GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun gridMatchesIndependentFiveHundredLayout() {
        val expected = independentFiveHundredGridRgba()
        assertEquals(512 * 512 * 4, expected.size)
        assertPixel(expected, 512, 164, 10, 255, 255, 255, 255)
        assertPixel(expected, 512, 10, 60, 255, 255, 255, 255)
        assertPixel(expected, 512, 495, 10, 255, 255, 255, 255)
        assertPixel(expected, 512, 10, 493, 255, 255, 255, 255)
        assertPixel(expected, 512, 83, 10, 126, 129, 0, 255)
        assertPixel(expected, 512, 150, 10, 0, 255, 0, 255)

        GpuAvailability.requireWebGpu()
        val actual = SkiaGmRenderer.render(HardstopGradientShaderGm())
        assertRenderShape(actual, 512, 512)
        assertPixel(actual.rgba, actual.width, 164, 10, 255, 255, 255, 255)
        assertPixel(actual.rgba, actual.width, 10, 60, 255, 255, 255, 255)
        assertPixel(actual.rgba, actual.width, 495, 10, 255, 255, 255, 255)
        assertPixel(actual.rgba, actual.width, 10, 493, 255, 255, 255, 255)
        assertPixelRgbNear(actual.rgba, actual.width, 83, 10, 126, 129, 0, 2)
        assertPixelRgbNear(actual.rgba, actual.width, 150, 10, 0, 255, 0, 2)
        assertGridPixels(expected, actual.rgba)
        assertCleanNativeRender(actual)

        val repeated = SkiaGmRenderer.render(HardstopGradientShaderGm())
        assertRenderShape(repeated, 512, 512)
        assertTrue(actual.rgba.contentEquals(repeated.rgba), "repeated hardstop grid bytes differ")
        assertCleanNativeRender(repeated)
    }

    @Test
    fun manyMatchesIndependentBlueWhiteRamps() {
        val expected = independentBlueWhiteRampsRgba()
        assertEquals(1000 * 2000 * 4, expected.size)
        assertPixel(expected, 1000, 0, 1, 0, 0, 255, 255)
        assertPixel(expected, 1000, 499, 1, 127, 127, 255, 255)
        assertPixel(expected, 1000, 500, 1, 128, 128, 255, 255)
        assertPixel(expected, 1000, 500, 18, 128, 128, 255, 255)
        assertPixel(expected, 1000, 500, 0, 255, 255, 255, 255)
        assertPixel(expected, 1000, 500, 19, 255, 255, 255, 255)
        assertPixel(expected, 1000, 499, 21, 255, 255, 255, 255)
        assertPixel(expected, 1000, 500, 21, 0, 0, 255, 255)

        GpuAvailability.requireWebGpu()
        val actual = SkiaGmRenderer.render(HardstopGradientsManyGm())
        assertRenderShape(actual, 1000, 2000)
        assertPixelRgbNear(actual.rgba, actual.width, 0, 1, 0, 0, 255, 2)
        assertPixelRgbNear(actual.rgba, actual.width, 499, 1, 127, 127, 255, 2)
        assertPixelRgbNear(actual.rgba, actual.width, 500, 1, 128, 128, 255, 2)
        assertPixelRgbNear(actual.rgba, actual.width, 500, 18, 128, 128, 255, 2)
        assertPixel(actual.rgba, actual.width, 500, 0, 255, 255, 255, 255)
        assertPixel(actual.rgba, actual.width, 500, 19, 255, 255, 255, 255)
        assertPixelRgbNear(actual.rgba, actual.width, 499, 21, 255, 255, 255, 2)
        assertPixelRgbNear(actual.rgba, actual.width, 500, 21, 0, 0, 255, 2)
        assertManyPixels(expected, actual.rgba)
        assertCleanNativeRender(actual)

        val repeated = SkiaGmRenderer.render(HardstopGradientsManyGm())
        assertRenderShape(repeated, 1000, 2000)
        assertTrue(actual.rgba.contentEquals(repeated.rgba), "repeated hardstop-many bytes differ")
        assertCleanNativeRender(repeated)
    }

    private fun assertRenderShape(actual: SkiaRenderResult, width: Int, height: Int) {
        assertEquals(width, actual.width)
        assertEquals(height, actual.height)
        assertEquals(width * height * 4, actual.rgba.size)
    }

    private fun assertCleanNativeRender(actual: SkiaRenderResult) {
        assertEquals(0, actual.refusedCount, actual.diagnostics.toString())
        assertTrue(actual.diagnostics.isEmpty(), actual.diagnostics.toString())
        assertTrue(actual.dispatchedCount > 0, "expected native dispatch")
    }
}

private data class OracleRgb(val red: Int, val green: Int, val blue: Int)

private fun independentFiveHundredGridRgba(): ByteArray {
    val width = 512
    val height = 512
    val expected = opaqueWhiteRgba(width, height)
    val colors = listOf(
        OracleRgb(255, 0, 0),
        OracleRgb(0, 255, 0),
        OracleRgb(0, 0, 255),
        OracleRgb(255, 255, 0),
        OracleRgb(255, 0, 255),
    )
    val counts = intArrayOf(2, 3, 3, 5, 4, 3, 3, 4)
    val positions = arrayOf(
        floatArrayOf(0f, 1f),
        floatArrayOf(0f, 0.5f, 1f),
        floatArrayOf(0f, 0.25f, 1f),
        floatArrayOf(0f, 0.25f, 0.5f, 0.5f, 1f),
        floatArrayOf(0f, 0.5f, 0.5f, 1f),
        floatArrayOf(0f, 0f, 1f),
        floatArrayOf(0f, 1f, 1f),
        floatArrayOf(0f, 0.3f, 0.3f, 1f),
    )

    for (row in 0 until 8) {
        for (column in 0 until 3) {
            val left = 166 * column + 3
            val top = 62 * row + 3
            val rowColors = colors.take(counts[row])
            val rowPositions = positions[row]
            for (y in top until top + 56) {
                for (x in left until left + 160) {
                    val tRaw = ((x.toFloat() + 0.5f - (166 * column + 33).toFloat()) * 100f) / 10000f
                    putOpaquePixel(expected, width, x, y, sampleGridGradient(rowColors, rowPositions, tRaw, column))
                }
            }
        }
    }
    return expected
}

private fun independentBlueWhiteRampsRgba(): ByteArray {
    val width = 1000
    val height = 2000
    val expected = opaqueWhiteRgba(width, height)
    for (count in 1..100) {
        val rowPixels = blueWhiteRampRow(count)
        val top = (count - 1) * 20 + 1
        repeat(18) { line ->
            rowPixels.copyInto(expected, destinationOffset = (top + line) * width * 4)
        }
    }
    return expected
}

private fun blueWhiteRampRow(count: Int): ByteArray {
    val pixels = ByteArray(1000 * 4)
    val stops = ArrayList<Pair<Float, OracleRgb>>(2 * count)
    stops += 0f to OracleRgb(0, 0, 255)
    for (k in 1 until count) {
        val position = k.toFloat() / count.toFloat()
        stops += position to OracleRgb(255, 255, 255)
        stops += position to OracleRgb(0, 0, 255)
    }
    stops += 1f to OracleRgb(255, 255, 255)
    for (x in 0 until 1000) {
        val tRaw = ((x.toFloat() + 0.5f) * 1000f) / 1000000f
        putOpaquePixel(pixels, 1000, x, 0, sampleClampGradient(stops, tRaw))
    }
    return pixels
}

private fun sampleGridGradient(
    colors: List<OracleRgb>,
    positions: FloatArray,
    tRaw: Float,
    tileColumn: Int,
): OracleRgb {
    if (tileColumn == 0 && tRaw < 0f) return colors.first()
    if (tileColumn == 0 && tRaw > 1f) return colors.last()
    val t = when (tileColumn) {
        0 -> tRaw.coerceIn(0f, 1f)
        1 -> tRaw - floor(tRaw.toDouble()).toFloat()
        else -> {
            val period = tRaw - floor((tRaw / 2f).toDouble()).toFloat() * 2f
            if (period <= 1f) period else 2f - period
        }
    }
    return sampleStops(colors.indices.map { positions[it] to colors[it] }, t)
}

private fun sampleClampGradient(stops: List<Pair<Float, OracleRgb>>, tRaw: Float): OracleRgb {
    if (tRaw < 0f) return stops.first().second
    if (tRaw > 1f) return stops.last().second
    return sampleStops(stops, tRaw.coerceIn(0f, 1f))
}

private fun sampleStops(stops: List<Pair<Float, OracleRgb>>, t: Float): OracleRgb {
    var lower = 0
    for (index in stops.indices) {
        if (stops[index].first <= t) lower = index
    }
    if (lower == stops.lastIndex) return stops[lower].second
    val (startPosition, startColor) = stops[lower]
    val (endPosition, endColor) = stops[lower + 1]
    val fraction = (t.toDouble() - startPosition.toDouble()) / (endPosition.toDouble() - startPosition.toDouble())
    return OracleRgb(
        (startColor.red + (endColor.red - startColor.red) * fraction).roundToInt(),
        (startColor.green + (endColor.green - startColor.green) * fraction).roundToInt(),
        (startColor.blue + (endColor.blue - startColor.blue) * fraction).roundToInt(),
    )
}

private fun opaqueWhiteRgba(width: Int, height: Int): ByteArray = ByteArray(width * height * 4).also { pixels ->
    for (offset in pixels.indices step 4) {
        pixels[offset] = 255.toByte()
        pixels[offset + 1] = 255.toByte()
        pixels[offset + 2] = 255.toByte()
        pixels[offset + 3] = 255.toByte()
    }
}

private fun putOpaquePixel(pixels: ByteArray, width: Int, x: Int, y: Int, color: OracleRgb) {
    val offset = (y * width + x) * 4
    pixels[offset] = color.red.toByte()
    pixels[offset + 1] = color.green.toByte()
    pixels[offset + 2] = color.blue.toByte()
    pixels[offset + 3] = 255.toByte()
}

private fun assertGridPixels(expected: ByteArray, actual: ByteArray) {
    assertEquals(expected.size, actual.size)
    for (offset in expected.indices step 4) {
        val x = (offset / 4) % 512
        val y = (offset / 4) / 512
        val painted = (0 until 3).any { column -> (0 until 8).any { row ->
            x in (166 * column + 3) until (166 * column + 163) && y in (62 * row + 3) until (62 * row + 59)
        } }
        assertExpectedPixel(expected, actual, offset, x, y, painted)
    }
}

private fun assertManyPixels(expected: ByteArray, actual: ByteArray) {
    assertEquals(expected.size, actual.size)
    for (offset in expected.indices step 4) {
        val x = (offset / 4) % 1000
        val y = (offset / 4) / 1000
        val painted = (y % 20) in 1..18
        assertExpectedPixel(expected, actual, offset, x, y, painted)
    }
}

private fun assertExpectedPixel(expected: ByteArray, actual: ByteArray, offset: Int, x: Int, y: Int, painted: Boolean) {
    for (channel in 0..3) {
        val expectedChannel = expected[offset + channel].toInt() and 255
        val actualChannel = actual[offset + channel].toInt() and 255
        val delta = abs(expectedChannel - actualChannel)
        assertTrue(if (painted && channel < 3) delta <= 2 else delta == 0,
            "pixel ($x,$y) channel=$channel expected=$expectedChannel actual=$actualChannel")
    }
}

private fun assertPixel(pixels: ByteArray, width: Int, x: Int, y: Int, red: Int, green: Int, blue: Int, alpha: Int) {
    val offset = (y * width + x) * 4
    assertEquals(red, pixels[offset].toInt() and 255, "red ($x,$y)")
    assertEquals(green, pixels[offset + 1].toInt() and 255, "green ($x,$y)")
    assertEquals(blue, pixels[offset + 2].toInt() and 255, "blue ($x,$y)")
    assertEquals(alpha, pixels[offset + 3].toInt() and 255, "alpha ($x,$y)")
}

private fun assertPixelRgbNear(
    pixels: ByteArray, width: Int, x: Int, y: Int, red: Int, green: Int, blue: Int, tolerance: Int,
) {
    val offset = (y * width + x) * 4
    listOf(red, green, blue).forEachIndexed { channel, expected ->
        val actual = pixels[offset + channel].toInt() and 255
        assertTrue(abs(actual - expected) <= tolerance,
            "pixel ($x,$y) channel=$channel expected=$expected±$tolerance actual=$actual")
    }
    assertEquals(255, pixels[offset + 3].toInt() and 255, "alpha ($x,$y)")
}
