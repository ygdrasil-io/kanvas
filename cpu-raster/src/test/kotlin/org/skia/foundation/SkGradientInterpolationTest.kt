package org.skia.foundation

import org.graphiks.math.SkColorGetG
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
}
