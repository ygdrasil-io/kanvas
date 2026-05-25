package org.skia.foundation

import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkPoint
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkAlphaType as CoreAlphaType
import org.skia.core.SkColorSpaceXformSteps

class SkGradientInterpolationTest {

    private val srgbXform = SkColorSpaceXformSteps(
        SkColorSpace.makeSRGB(), CoreAlphaType.kUnpremul,
        SkColorSpace.makeSRGB(), CoreAlphaType.kUnpremul,
    )

    @Test
    fun `linear gradient overload accepts RGB working spaces`() {
        val pts = arrayOf(SkPoint(0f, 0f), SkPoint(100f, 0f))
        val colors = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt())

        val shader = SkShaders.LinearGradient(
            pts,
            SkGradient(
                colors = colors,
                interpolation = SkGradient.Interpolation(
                    colorSpace = SkGradient.Interpolation.ColorSpace.kSRGBLinear,
                ),
            ),
        )

        shader.setupForDraw(org.graphiks.math.SkMatrix.Identity, srgbXform)
        val row = IntArray(101)
        shader.shadeRow(0, 0, row.size, row)

        assertTrue(
            SkColorGetG(row[50]) > 160,
            "linear-sRGB red-to-green midpoint should be bright, got 0x${row[50].toUInt().toString(16)}",
        )
    }

    @Test
    fun `linear gradient overload rejects perceptual color spaces explicitly`() {
        val pts = arrayOf(SkPoint(0f, 0f), SkPoint(10f, 0f))
        val error = assertThrows(UnsupportedOperationException::class.java) {
            SkShaders.LinearGradient(
                pts,
                SkGradient(
                    colors = intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt()),
                    interpolation = SkGradient.Interpolation(
                        colorSpace = SkGradient.Interpolation.ColorSpace.kOKLCH,
                    ),
                ),
            )
        }

        assertTrue(error.message!!.contains("STUB.GRADIENT_INTERPOLATION"))
    }

    @Test
    fun `linear gradient overload supports HSL shorter hue interpolation`() {
        val mid = sampleMidpoint(
            SkGradient.Interpolation(
                colorSpace = SkGradient.Interpolation.ColorSpace.kHSL,
                hueMethod = SkGradient.Interpolation.HueMethod.kShorter,
            ),
        )

        assertTrue(
            SkColorGetR(mid) > 200 && (mid and 0xFF) > 200 && SkColorGetG(mid) < 80,
            "shorter red-to-blue HSL midpoint should be magenta, got 0x${mid.toUInt().toString(16)}",
        )
    }

    @Test
    fun `linear gradient overload supports HSL increasing hue interpolation`() {
        val mid = sampleMidpoint(
            SkGradient.Interpolation(
                colorSpace = SkGradient.Interpolation.ColorSpace.kHSL,
                hueMethod = SkGradient.Interpolation.HueMethod.kIncreasing,
            ),
        )

        assertTrue(
            SkColorGetG(mid) > 200 && SkColorGetR(mid) < 80 && (mid and 0xFF) < 80,
            "increasing red-to-blue HSL midpoint should be green, got 0x${mid.toUInt().toString(16)}",
        )
    }

    @Test
    fun `linear gradient overload supports HSL longer hue interpolation`() {
        val mid = sampleMidpoint(
            SkGradient.Interpolation(
                colorSpace = SkGradient.Interpolation.ColorSpace.kHSL,
                hueMethod = SkGradient.Interpolation.HueMethod.kLonger,
            ),
        )

        assertTrue(
            SkColorGetG(mid) > 200 && SkColorGetR(mid) < 80 && (mid and 0xFF) < 80,
            "longer red-to-blue HSL midpoint should be green, got 0x${mid.toUInt().toString(16)}",
        )
    }

    @Test
    fun `linear gradient overload supports HSL decreasing hue interpolation`() {
        val mid = sampleMidpoint(
            SkGradient.Interpolation(
                colorSpace = SkGradient.Interpolation.ColorSpace.kHSL,
                hueMethod = SkGradient.Interpolation.HueMethod.kDecreasing,
            ),
        )

        assertTrue(
            SkColorGetR(mid) > 200 && (mid and 0xFF) > 200 && SkColorGetG(mid) < 80,
            "decreasing red-to-blue HSL midpoint should be magenta, got 0x${mid.toUInt().toString(16)}",
        )
    }

    @Test
    fun `linear gradient overload accepts premul interpolation flag for RGB spaces`() {
        val pts = arrayOf(SkPoint(0f, 0f), SkPoint(10f, 0f))
        val shader = SkShaders.LinearGradient(
            pts,
            SkGradient(
                colors = intArrayOf(0x80FF0000.toInt(), 0x800000FF.toInt()),
                interpolation = SkGradient.Interpolation(
                    colorSpace = SkGradient.Interpolation.ColorSpace.kSRGB,
                    inPremul = SkGradient.Interpolation.InPremul.kYes,
                ),
            ),
        )
        shader.setupForDraw(org.graphiks.math.SkMatrix.Identity, srgbXform)
        val row = IntArray(1)
        shader.shadeRow(5, 0, row.size, row)
        assertTrue(row[0] ushr 24 > 0, "premul RGB interpolation should render a non-empty sample")
    }

    @Test
    fun `HSL powerless hue borrows adjacent hue for white to blue`() {
        val powerless = sampleGradient(
            colors = intArrayOf(0xFFFFFFFF.toInt(), 0xFF0000FF.toInt()),
            x = 50,
        )

        assertTrue(
            (powerless and 0xFF) > SkColorGetR(powerless) &&
                (powerless and 0xFF) > SkColorGetG(powerless),
            "powerless white should borrow blue hue, got 0x${powerless.toUInt().toString(16)}",
        )
    }

    @Test
    fun `HSL powerless middle stop uses neighboring hues per side`() {
        val left = sampleGradient(
            colors = intArrayOf(0xFFFF0000.toInt(), 0xFFFFFFFF.toInt(), 0xFF0000FF.toInt()),
            x = 25,
        )
        val right = sampleGradient(
            colors = intArrayOf(0xFFFF0000.toInt(), 0xFFFFFFFF.toInt(), 0xFF0000FF.toInt()),
            x = 75,
        )

        assertTrue(
            SkColorGetR(left) > SkColorGetG(left) && SkColorGetR(left) > (left and 0xFF),
            "left side should borrow red hue, got 0x${left.toUInt().toString(16)}",
        )
        assertTrue(
            (right and 0xFF) > SkColorGetR(right) && (right and 0xFF) > SkColorGetG(right),
            "right side should borrow blue hue, got 0x${right.toUInt().toString(16)}",
        )
    }

    @Test
    fun `HSL accepts premul interpolation for powerless transparent stop`() {
        val sample = sampleGradient(
            colors = intArrayOf(0x00000000, 0xFF0000FF.toInt()),
            interpolation = SkGradient.Interpolation(
                colorSpace = SkGradient.Interpolation.ColorSpace.kHSL,
                inPremul = SkGradient.Interpolation.InPremul.kYes,
            ),
            x = 50,
        )

        assertTrue(
            sample ushr 24 in 0x7F..0x81,
            "premul HSL should interpolate alpha, got 0x${sample.toUInt().toString(16)}",
        )
    }

    @Test
    fun `HSL premul interpolation preserves transparent tinted hue`() {
        val sample = sampleGradient(
            colors = intArrayOf(0x000000FF, 0xFFFF0000.toInt()),
            interpolation = SkGradient.Interpolation(
                colorSpace = SkGradient.Interpolation.ColorSpace.kHSL,
                inPremul = SkGradient.Interpolation.InPremul.kYes,
            ),
            x = 50,
        )

        assertTrue(
            SkColorGetR(sample) > 200 && (sample and 0xFF) > 200 && SkColorGetG(sample) < 80,
            "transparent blue should keep its hue hint before premul interpolation, got 0x${sample.toUInt().toString(16)}",
        )
    }

    @Test
    fun `LCH powerless hue borrows adjacent hue for white to blue`() {
        val powerless = sampleGradient(
            colors = intArrayOf(0xFFFFFFFF.toInt(), 0xFF0000FF.toInt()),
            interpolation = SkGradient.Interpolation(
                colorSpace = SkGradient.Interpolation.ColorSpace.kLCH,
            ),
            x = 50,
        )

        assertTrue(
            (powerless and 0xFF) > SkColorGetR(powerless) &&
                (powerless and 0xFF) > SkColorGetG(powerless),
            "LCH powerless white should borrow blue hue, got 0x${powerless.toUInt().toString(16)}",
        )
    }

    @Test
    fun `LCH powerless middle stop uses neighboring hues per side`() {
        val left = sampleGradient(
            colors = intArrayOf(0xFFFF0000.toInt(), 0xFFFFFFFF.toInt(), 0xFF0000FF.toInt()),
            interpolation = SkGradient.Interpolation(
                colorSpace = SkGradient.Interpolation.ColorSpace.kLCH,
            ),
            x = 25,
        )
        val right = sampleGradient(
            colors = intArrayOf(0xFFFF0000.toInt(), 0xFFFFFFFF.toInt(), 0xFF0000FF.toInt()),
            interpolation = SkGradient.Interpolation(
                colorSpace = SkGradient.Interpolation.ColorSpace.kLCH,
            ),
            x = 75,
        )

        assertTrue(
            SkColorGetR(left) > SkColorGetG(left) && SkColorGetR(left) > (left and 0xFF),
            "LCH left side should borrow red hue, got 0x${left.toUInt().toString(16)}",
        )
        assertTrue(
            (right and 0xFF) > SkColorGetR(right) && (right and 0xFF) > SkColorGetG(right),
            "LCH right side should borrow blue hue, got 0x${right.toUInt().toString(16)}",
        )
    }

    @Test
    fun `linear gradient overload accepts bounded RGB color spaces`() {
        val pts = arrayOf(SkPoint(0f, 0f), SkPoint(10f, 0f))
        val rgbSpaces = listOf(
            SkGradient.Interpolation.ColorSpace.kSRGB,
            SkGradient.Interpolation.ColorSpace.kSRGBLinear,
            SkGradient.Interpolation.ColorSpace.kA98RGB,
            SkGradient.Interpolation.ColorSpace.kProPhotoRGB,
            SkGradient.Interpolation.ColorSpace.kDisplayP3,
            SkGradient.Interpolation.ColorSpace.kRec2020,
        )

        for (space in rgbSpaces) {
            val shader = SkShaders.LinearGradient(
                pts,
                SkGradient(
                    colors = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt()),
                    interpolation = SkGradient.Interpolation(colorSpace = space),
                ),
            )
            shader.setupForDraw(org.graphiks.math.SkMatrix.Identity, srgbXform)
            val row = IntArray(2)
            shader.shadeRow(0, 0, row.size, row)
            assertTrue(row.any { it != 0 }, "$space should render a non-empty row")
        }
    }

    private fun sampleMidpoint(interpolation: SkGradient.Interpolation): Int {
        return sampleGradient(
            colors = intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt()),
            interpolation = interpolation,
            x = 50,
        )
    }

    private fun sampleGradient(
        colors: IntArray,
        positions: FloatArray? = null,
        interpolation: SkGradient.Interpolation = SkGradient.Interpolation(
            colorSpace = SkGradient.Interpolation.ColorSpace.kHSL,
        ),
        x: Int,
    ): Int {
        val pts = arrayOf(SkPoint(0f, 0f), SkPoint(100f, 0f))
        val shader = SkShaders.LinearGradient(
            pts,
            SkGradient(
                colors = colors,
                positions = positions,
                interpolation = interpolation,
            ),
        )
        shader.setupForDraw(org.graphiks.math.SkMatrix.Identity, srgbXform)
        val row = IntArray(1)
        shader.shadeRow(x, 0, row.size, row)
        return row[0]
    }

}
