@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.math.abs
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point3F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.vector.Vector3F32
import org.junit.jupiter.api.Test

/** Public Render+Readback witnesses for the first executable W6d lighting slice. */
class W6dLightingSurfacePixelTest {
    @Test
    fun `point diffuse matches independent oracle`() = assertRemainingFamily(
        W6dLightingCpuOracle.Family.POINT_DIFFUSE,
        ImageFilter.PointLitDiffuse(Point3F32(1f, 0f, 1f), ColorARGB.White, 1f, 1f),
    )

    @Test
    fun `point diffuse preserves Z when XY is unchanged`() {
        val alpha = alphaFixture()
        val near = W6dLightingCpuOracle.remainingFamilyRgba8(W6dLightingCpuOracle.Family.POINT_DIFFUSE, 3, 3, alpha,
            1f, 0f, 1f, surfaceDepth = 1f, coefficient = 1f)
        val far = W6dLightingCpuOracle.remainingFamilyRgba8(W6dLightingCpuOracle.Family.POINT_DIFFUSE, 3, 3, alpha,
            1f, 0f, 100f, surfaceDepth = 1f, coefficient = 1f)
        assertFalse(near.contentEquals(far), "The independent point-light oracle must distinguish Z at fixed XY.")
        assertPointFamilyNear(near, Point3F32(1f, 0f, 1f))
        assertPointFamilyNear(far, Point3F32(1f, 0f, 100f))
    }

    @Test
    fun `spot diffuse uses Skia falloff before cone edge ramp`() = assertRemainingFamily(
        W6dLightingCpuOracle.Family.SPOT_DIFFUSE,
        ImageFilter.SpotLitDiffuse(Point3F32(1f, 0f, 1f), Point3F32(1f, 0f, 0f), 1f, 90f, ColorARGB.White, 1f, 1f),
    )

    @Test
    fun `distant specular uses distant direction and linear alpha`() = assertRemainingFamily(
        W6dLightingCpuOracle.Family.DISTANT_SPECULAR,
        ImageFilter.DistantLitSpecular(Vector3F32(1f, 0f, 1f), ColorARGB.White, 1f, 1f, 2f),
        expectedTopLeft = ubyteArrayOf(121u, 121u, 121u, 49u),
    )

    @Test
    fun `point specular matches independent oracle`() = assertRemainingFamily(
        W6dLightingCpuOracle.Family.POINT_SPECULAR,
        ImageFilter.PointLitSpecular(Point3F32(1f, 0f, 1f), ColorARGB.White, 1f, 1f, 2f),
    )

    @Test
    fun `spot specular matches independent oracle`() = assertRemainingFamily(
        W6dLightingCpuOracle.Family.SPOT_SPECULAR,
        ImageFilter.SpotLitSpecular(Point3F32(1f, 0f, 1f), Point3F32(1f, 0f, 0f), 1f, 90f, ColorARGB.White, 1f, 1f, 2f),
    )

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
    fun `distant diffuse retains consumer demand outside a cropped child`() {
        val alpha = FloatArray(25).also { it[2 * 5 + 2] = 1f }
        val expected = W6dLightingCpuOracle.distantDiffuseRgba8(5, 5, alpha, 2, 2, 3, 3,
            directionX = 1f, directionY = 0f, directionZ = 1f, surfaceDepth = 1f, kd = 1f)
        val filter = ImageFilter.DistantLitDiffuse(
            Vector3F32(1f, 0f, 1f), ColorARGB.White, 1f, 1f,
            ImageFilter.Crop(RectF32.ofLTRB(2f, 2f, 3f, 3f)),
        )
        val surface = Surface(5, 5)
        surface.canvas {
            drawRect(RectF32.ofLTRB(2f, 2f, 3f, 3f), Paint(ColorARGB.White, imageFilter = filter, antiAlias = false))
        }

        assertTrue(expected[0].toInt() > 0, "The cropped-child fixture must light transparent black outside the crop.")
        assertFamilyNear(expected, surface.render(), maxChannelDelta = 2)
    }

    @Test
    fun `compose outer distant diffuse retains consumer demand outside cropped inner`() {
        val alpha = FloatArray(25).also { it[2 * 5 + 2] = 1f }
        val expected = W6dLightingCpuOracle.distantDiffuseRgba8(5, 5, alpha, 2, 2, 3, 3,
            directionX = 1f, directionY = 0f, directionZ = 1f, surfaceDepth = 1f, kd = 1f)
        val filter = ImageFilter.Compose(
            ImageFilter.DistantLitDiffuse(Vector3F32(1f, 0f, 1f), ColorARGB.White, 1f, 1f),
            ImageFilter.Crop(RectF32.ofLTRB(2f, 2f, 3f, 3f)),
        )
        val surface = Surface(5, 5)
        surface.canvas {
            drawRect(RectF32.ofLTRB(2f, 2f, 3f, 3f), Paint(ColorARGB.White, imageFilter = filter, antiAlias = false))
        }

        assertTrue(expected[0].toInt() > 0, "The Compose fixture must light transparent black outside the crop.")
        assertFamilyNear(expected, surface.render(), maxChannelDelta = 2)
    }

    @Test
    fun `direct terminal clip retains Sobel halo outside visible output`() {
        val alpha = floatArrayOf(1f, 0f, 0f)
        val expectedFull = W6dLightingCpuOracle.distantDiffuseRgba8(3, 1, alpha, 0, 0, 1, 1,
            directionX = 1f, directionY = 0f, directionZ = 0f, surfaceDepth = 1f, kd = 1f)
        val expected = UByteArray(3 * 4).also { pixels ->
            expectedFull.copyInto(pixels, destinationOffset = 4, startIndex = 4, endIndex = 8)
        }
        val surface = Surface(3, 1)
        surface.canvas {
            clipRect(RectF32.ofLTRB(1f, 0f, 2f, 1f), antiAlias = false)
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.White,
                imageFilter = ImageFilter.DistantLitDiffuse(Vector3F32(1f, 0f, 0f), ColorARGB.White, 1f, 1f),
                antiAlias = false))
        }

        assertTrue(expected[4].toInt() > 0, "The clipped pixel must retain the alpha halo at x=0.")
        assertFamilyNear(expected, surface.render(), maxChannelDelta = 2)
    }

    @Test
    fun `disjoint direct terminal clip is a no op and surface recovers`() {
        val surface = Surface(3, 1)
        surface.canvas {
            save()
            clipRect(RectF32.ofLTRB(4f, 0f, 5f, 1f), antiAlias = false)
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.White,
                imageFilter = ImageFilter.DistantLitDiffuse(Vector3F32(1f, 0f, 1f), ColorARGB.White, 1f, 1f),
                antiAlias = false))
            restore()
        }

        assertContentEquals(UByteArray(3 * 4), surface.render().pixels)
        surface.discardRecordedOperations()
        surface.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Red, antiAlias = false)) }
        assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u, 0u, 0u, 0u, 0u, 0u, 0u, 0u, 0u), surface.render().pixels)
    }

    @Test
    fun `direct distant diffuse intersects its unbounded demand with terminal clip before allocation`() {
        val center = W6dLightingCpuOracle.distantDiffuseRgba8(1, 1, floatArrayOf(1f), 0, 0, 1, 1,
            directionX = 1f, directionY = 0f, directionZ = 1f, surfaceDepth = 1f, kd = 1f)
        val expected = UByteArray(512 * 4).also { pixels ->
            center.copyInto(pixels, destinationOffset = 256 * 4)
        }
        val surface = Surface(512, 1, config = RenderConfig(frameLocalBudgetBytes = 5_000L))
        surface.canvas {
            clipRect(RectF32.ofLTRB(256f, 0f, 257f, 1f), antiAlias = false)
            drawRect(RectF32.ofLTRB(256f, 0f, 257f, 1f), Paint(ColorARGB.White,
                imageFilter = ImageFilter.DistantLitDiffuse(Vector3F32(1f, 0f, 1f), ColorARGB.White, 1f, 1f),
                antiAlias = false))
        }

        val actual = surface.render()
        assertTrue(actual.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), actual.nativeEvidenceScopeKinds.toString())
        assertContentEquals(expected, actual.pixels)
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

    private fun assertRemainingFamily(
        family: W6dLightingCpuOracle.Family,
        filter: ImageFilter,
        expectedTopLeft: UByteArray? = null,
    ) {
        val alpha = alphaFixture()
        val expected = W6dLightingCpuOracle.remainingFamilyRgba8(family, 3, 3, alpha,
            locationX = 1f, locationY = 0f, locationZ = 1f, targetX = 1f, targetY = 0f, targetZ = 0f,
            surfaceDepth = 1f, coefficient = 1f, shininess = 2f, specularExponent = 1f, cutoffDegrees = 90f)
        expectedTopLeft?.let { assertContentEquals(it, expected.copyOfRange(0, 4)) }
        val surface = Surface(3, 3)
        surface.canvas {
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = filter, antiAlias = false)))
            listOf(1 to 0, 0 to 1, 1 to 1, 1 to 2).forEach { (x, y) ->
                drawRect(RectF32.ofLTRB(x.toFloat(), y.toFloat(), x + 1f, y + 1f), Paint(ColorARGB.White, antiAlias = false))
            }
            restore()
        }
        assertFamilyNear(expected, surface.render(), maxChannelDelta = 2)
    }

    private fun alphaFixture(): FloatArray = floatArrayOf(0f, 1f, 0f, 1f, 1f, 0f, 0f, 1f, 0f)

    private fun assertPointFamilyNear(expected: UByteArray, location: Point3F32) {
        val surface = Surface(3, 3)
        surface.canvas {
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = ImageFilter.PointLitDiffuse(location, ColorARGB.White, 1f, 1f), antiAlias = false)))
            listOf(1 to 0, 0 to 1, 1 to 1, 1 to 2).forEach { (x, y) ->
                drawRect(RectF32.ofLTRB(x.toFloat(), y.toFloat(), x + 1f, y + 1f), Paint(ColorARGB.White, antiAlias = false))
            }
            restore()
        }
        assertFamilyNear(expected, surface.render(), maxChannelDelta = 2)
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
