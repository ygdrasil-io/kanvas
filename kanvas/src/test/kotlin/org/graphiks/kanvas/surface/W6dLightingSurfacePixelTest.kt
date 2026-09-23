@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.math.abs
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.vector.Vector3F32
import org.junit.jupiter.api.Test

/** Public Render+Readback witnesses for the first executable W6d lighting slice. */
class W6dLightingSurfacePixelTest {
    @Test
    fun `distant diffuse matches independent alpha-normal oracle and preserves Z`() {
        val alpha = floatArrayOf(
            0f, 1f, 0f,
            1f, 1f, 0f,
            0f, 1f, 0f,
        )
        val lowZ = W6dLightingCpuOracle.distantDiffuseRgba8(3, 3, alpha, 0, 0, 3, 3,
            directionX = 1f, directionY = 0f, directionZ = 1f, surfaceDepth = 1f, kd = 1f)
        val highZ = W6dLightingCpuOracle.distantDiffuseRgba8(3, 3, alpha, 0, 0, 3, 3,
            directionX = 1f, directionY = 0f, directionZ = 100f, surfaceDepth = 1f, kd = 1f)
        assertFalse(lowZ.contentEquals(highZ), "The independent fixture must distinguish Z at fixed XY.")

        assertFamilyNear(lowZ, renderLayerAlphaFixture(Vector3F32(1f, 0f, 1f)), maxChannelDelta = 2)
        assertFamilyNear(highZ, renderLayerAlphaFixture(Vector3F32(1f, 0f, 100f)), maxChannelDelta = 2)
    }

    @Test
    fun `distant diffuse uses decal at an interior child edge and lights transparent black`() {
        val alpha = FloatArray(25).also { it[2 * 5 + 2] = 1f }
        val expected = W6dLightingCpuOracle.distantDiffuseRgba8(5, 5, alpha, 2, 2, 3, 3,
            directionX = 1f, directionY = 0f, directionZ = 1f, surfaceDepth = 1f, kd = 1f)
        val surface = Surface(5, 5)
        surface.canvas {
            drawRect(RectF32.ofLTRB(2f, 2f, 3f, 3f), Paint(ColorARGB.White,
                imageFilter = ImageFilter.DistantLitDiffuse(Vector3F32(1f, 0f, 1f), ColorARGB.White, 1f, 1f),
                antiAlias = false))
        }

        assertTrue(expected[0].toInt() > 0, "The fixture must make lighting visible on transparent black.")
        assertFamilyNear(expected, surface.render(), maxChannelDelta = 2)
    }

    @Test
    fun `zero distant direction produces opaque black`() {
        val alpha = FloatArray(9) { 1f }
        val expected = W6dLightingCpuOracle.distantDiffuseRgba8(3, 3, alpha, 0, 0, 3, 3,
            directionX = 0f, directionY = 0f, directionZ = 0f, surfaceDepth = 1f, kd = 1f)
        assertTrue(expected.filterIndexed { index, _ -> index % 4 != 3 }.all { it == 0.toUByte() })
        val surface = Surface(3, 3)
        surface.canvas {
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = ImageFilter.DistantLitDiffuse(
                Vector3F32(0f, 0f, 0f), ColorARGB.White, 1f, 1f), antiAlias = false)))
            drawRect(RectF32.ofLTRB(0f, 0f, 3f, 3f), Paint(ColorARGB.White, antiAlias = false))
            restore()
        }

        assertFamilyNear(expected, surface.render(), maxChannelDelta = 0)
    }

    private fun renderLayerAlphaFixture(direction: Vector3F32): RenderResult {
        val surface = Surface(3, 3)
        surface.canvas {
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = ImageFilter.DistantLitDiffuse(
                direction, ColorARGB.White, 1f, 1f), antiAlias = false)))
            listOf(1 to 0, 0 to 1, 1 to 1, 1 to 2).forEach { (x, y) ->
                drawRect(RectF32.ofLTRB(x.toFloat(), y.toFloat(), x + 1f, y + 1f), Paint(ColorARGB.White, antiAlias = false))
            }
            restore()
        }
        return surface.render()
    }

    private fun assertFamilyNear(expected: UByteArray, actual: RenderResult, maxChannelDelta: Int) {
        assertTrue(actual.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), actual.nativeEvidenceScopeKinds.toString())
        assertTrue(expected.size == actual.pixels.size)
        expected.indices.forEach { index ->
            assertTrue(abs(expected[index].toInt() - actual.pixels[index].toInt()) <= maxChannelDelta,
                "channel $index expected=${expected[index]} actual=${actual.pixels[index]}")
        }
    }
}
