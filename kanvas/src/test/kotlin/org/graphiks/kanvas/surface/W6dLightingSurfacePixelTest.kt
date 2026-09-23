@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.math.abs
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point3F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
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
    fun `point diffuse maps location Z and surface scale through affine canvas matrix`() {
        // The affine maps the parameter point to device (7,7,2.5). The source layer starts
        // at device (5,7), so the frozen layer-space oracle location is rebased to (2,0,2.5).
        val alpha = FloatArray(15 * 16)
        listOf(7 to 7, 5 to 10, 7 to 10, 7 to 13).forEach { (left, top) ->
            for (y in top until top + 3) for (x in left until left + 2) alpha[y * 15 + x] = 1f
        }
        val expected = W6dLightingCpuOracle.remainingFamilyRgba8(
            W6dLightingCpuOracle.Family.POINT_DIFFUSE, 15, 16, alpha,
            locationX = 2f, locationY = 0f, locationZ = 2.5f,
            surfaceDepth = 2.5f, coefficient = 1f,
        )
        val unmapped = W6dLightingCpuOracle.remainingFamilyRgba8(
            W6dLightingCpuOracle.Family.POINT_DIFFUSE, 15, 16, alpha,
            locationX = 1f, locationY = 0f, locationZ = 1f,
            surfaceDepth = 1f, coefficient = 1f,
        )
        val withoutMappedZ = W6dLightingCpuOracle.remainingFamilyRgba8(
            W6dLightingCpuOracle.Family.POINT_DIFFUSE, 15, 16, alpha,
            locationX = 2f, locationY = 0f, locationZ = 1f,
            surfaceDepth = 1f, coefficient = 1f,
        )
        val withoutMappedXY = W6dLightingCpuOracle.remainingFamilyRgba8(
            W6dLightingCpuOracle.Family.POINT_DIFFUSE, 15, 16, alpha,
            locationX = 1f, locationY = 0f, locationZ = 2.5f,
            surfaceDepth = 2.5f, coefficient = 1f,
        )
        assertFalse(expected.contentEquals(unmapped), "The fixture must distinguish mapped parameters and surface depth.")
        assertFalse(expected.contentEquals(withoutMappedZ), "The fixture must distinguish mapped light Z and surfaceScale.")
        assertFalse(expected.contentEquals(withoutMappedXY), "The fixture must distinguish mapped light XY.")

        val surface = Surface(15, 16)
        surface.canvas {
            setMatrix(Matrix3x3F32(sx = 2f, sy = 3f, tx = 5f, ty = 7f))
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = ImageFilter.PointLitDiffuse(
                Point3F32(1f, 0f, 1f), ColorARGB.White, 1f, 1f), antiAlias = false)))
            listOf(1 to 0, 0 to 1, 1 to 1, 1 to 2).forEach { (x, y) ->
                drawRect(RectF32.ofLTRB(x.toFloat(), y.toFloat(), x + 1f, y + 1f), Paint(ColorARGB.White, antiAlias = false))
            }
            restore()
        }
        assertFamilyNear(expected, surface.render(), maxChannelDelta = 2)
    }

    @Test
    fun `spot diffuse uses Skia falloff before cone edge ramp`() = assertRemainingFamily(
        W6dLightingCpuOracle.Family.SPOT_DIFFUSE,
        ImageFilter.SpotLitDiffuse(Point3F32(1f, 0f, 1f), Point3F32(1f, 0f, 0f), 1f, 90f, ColorARGB.White, 1f, 1f),
        expectedTopLeft = ubyteArrayOf(180u, 180u, 180u, 255u),
    )

    @Test
    fun `spot diffuse multiplies exponentiated cosine by a nontrivial edge ramp`() = assertRemainingFamily(
        W6dLightingCpuOracle.Family.SPOT_DIFFUSE,
        ImageFilter.SpotLitDiffuse(Point3F32(1f, 0f, 1f), Point3F32(1f, 0f, 0f), 2f, 36f,
            ColorARGB.White, 1f, 1f),
        expectedTopLeft = ubyteArrayOf(116u, 116u, 116u, 255u),
        specularExponent = 2f,
        cutoffDegrees = 36f,
    )

    @Test
    fun `spot diffuse treats negative fractional-power bases as zero contribution`() = assertRemainingFamily(
        W6dLightingCpuOracle.Family.SPOT_DIFFUSE,
        ImageFilter.SpotLitDiffuse(Point3F32(1f, 0f, 1f), Point3F32(1f, 0f, 2f), .5f, 180f,
            ColorARGB.White, 1f, 1f),
        expectedTopLeft = ubyteArrayOf(0u, 0u, 0u, 255u),
        specularExponent = .5f,
        cutoffDegrees = 180f,
        location = Point3F32(1f, 0f, 1f),
        target = Point3F32(1f, 0f, 2f),
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
    fun `distant diffuse child touching output edge clamps its Sobel source`() {
        val alpha = alphaFixture()
        val expected = W6dLightingCpuOracle.distantDiffuseRgba8(3, 3, alpha, 0, 0, 3, 3,
            directionX = 1f, directionY = 0f, directionZ = 1f, surfaceDepth = 1f, kd = 1f)
        assertTrue(expected[0].toInt() > 0, "The edge-clamp fixture must have a visible independent witness.")
        assertFamilyNear(expected, renderLayerAlphaFixture(Vector3F32(1f, 0f, 1f)), maxChannelDelta = 2)
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
    fun `color filter around distant diffuse retains consumer demand outside cropped child`() {
        val alpha = FloatArray(25).also { it[2 * 5 + 2] = 1f }
        val expected = W6dLightingCpuOracle.distantDiffuseRgba8(5, 5, alpha, 2, 2, 3, 3,
            directionX = 1f, directionY = 0f, directionZ = 1f, surfaceDepth = 1f, kd = 1f)
        val filter = ImageFilter.ColorFilter(
            ColorFilter.Matrix(org.graphiks.math.color.ColorMatrixF32.ofIdentity()),
            ImageFilter.DistantLitDiffuse(
                Vector3F32(1f, 0f, 1f), ColorARGB.White, 1f, 1f,
                ImageFilter.Crop(RectF32.ofLTRB(2f, 2f, 3f, 3f)),
            ),
        )
        val surface = Surface(5, 5)
        surface.canvas {
            drawRect(RectF32.ofLTRB(2f, 2f, 3f, 3f), Paint(ColorARGB.White, imageFilter = filter, antiAlias = false))
        }

        assertTrue(expected[0].toInt() > 0, "The ColorFilter wrapper must not re-bound transparent-black lighting.")
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

    @Test
    fun `zero spot direction produces opaque black`() = assertRemainingFamily(
        W6dLightingCpuOracle.Family.SPOT_DIFFUSE,
        ImageFilter.SpotLitDiffuse(Point3F32(1f, 0f, 1f), Point3F32(1f, 0f, 1f), 1f, 90f,
            ColorARGB.White, 1f, 1f),
        expectedTopLeft = ubyteArrayOf(0u, 0u, 0u, 255u),
        location = Point3F32(1f, 0f, 1f),
        target = Point3F32(1f, 0f, 1f),
    )

    @Test
    fun `coincident point light and surface produces opaque black`() = assertRemainingFamily(
        W6dLightingCpuOracle.Family.POINT_DIFFUSE,
        ImageFilter.PointLitDiffuse(Point3F32(.5f, .5f, 0f), ColorARGB.White, 1f, 1f),
        expectedTopLeft = ubyteArrayOf(0u, 0u, 0u, 255u),
        location = Point3F32(.5f, .5f, 0f),
    )

    @Test
    fun `zero specular half vector produces transparent black`() = assertRemainingFamily(
        W6dLightingCpuOracle.Family.DISTANT_SPECULAR,
        ImageFilter.DistantLitSpecular(Vector3F32(0f, 0f, -1f), ColorARGB.White, 1f, 1f, 2f),
        expectedTopLeft = ubyteArrayOf(0u, 0u, 0u, 0u),
        location = Point3F32(0f, 0f, -1f),
    )

    @Test
    fun `spot pow zero zero follows the Kanvas one convention`() {
        val alpha = FloatArray(1) { 1f }
        val expected = W6dLightingCpuOracle.remainingFamilyRgba8(
            W6dLightingCpuOracle.Family.SPOT_DIFFUSE, 1, 1, alpha,
            locationX = .5f, locationY = .5f, locationZ = 2f,
            targetX = 1.5f, targetY = .5f, targetZ = 2f,
            surfaceDepth = 1f, coefficient = 1f, specularExponent = 0f, cutoffDegrees = 91f,
        )
        assertContentEquals(ubyteArrayOf(255u, 255u, 255u, 255u), expected)
        val surface = Surface(1, 1)
        surface.canvas {
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = ImageFilter.SpotLitDiffuse(
                Point3F32(.5f, .5f, 2f), Point3F32(1.5f, .5f, 2f), 0f, 91f,
                ColorARGB.White, 1f, 1f), antiAlias = false)))
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.White, antiAlias = false))
            restore()
        }
        assertFamilyNear(expected, surface.render(), maxChannelDelta = 0)
    }

    @Test
    fun `finite extreme spot coordinates retain their normalized diffuse contribution`() = assertRemainingFamily(
        W6dLightingCpuOracle.Family.SPOT_DIFFUSE,
        ImageFilter.SpotLitDiffuse(
            Point3F32(-1.8e38f, -1.8e38f, 1f), Point3F32(1.8e38f, 1.8e38f, 1f),
            1f, 90f, ColorARGB.White, 1f, 1f,
        ),
        expectedTopLeft = ubyteArrayOf(222u, 222u, 222u, 255u),
        location = Point3F32(-1.8e38f, -1.8e38f, 1f),
        target = Point3F32(1.8e38f, 1.8e38f, 1f),
    )

    @Test
    fun `distant diffuse accepts signed surface scale minus one`() {
        val alpha = alphaFixture()
        val expected = W6dLightingCpuOracle.distantDiffuseRgba8(3, 3, alpha, 0, 0, 3, 3,
            directionX = 1f, directionY = 0f, directionZ = 1f, surfaceDepth = -1f, kd = 1f)
        assertFalse(expected.contentEquals(W6dLightingCpuOracle.distantDiffuseRgba8(3, 3, alpha, 0, 0, 3, 3,
            directionX = 1f, directionY = 0f, directionZ = 1f, surfaceDepth = 1f, kd = 1f)))
        assertDistantDiffuseNear(expected, -1f)
    }

    @Test
    fun `spot diffuse accepts finite exponents outside legacy range`() {
        listOf(.5f, 129f).forEach { exponent ->
            val expected = W6dLightingCpuOracle.remainingFamilyRgba8(
                W6dLightingCpuOracle.Family.SPOT_DIFFUSE, 3, 3, alphaFixture(),
                locationX = 1f, locationY = 0f, locationZ = 1f,
                targetX = 1f, targetY = 0f, targetZ = 0f,
                surfaceDepth = 1f, coefficient = 1f, specularExponent = exponent, cutoffDegrees = 90f,
            )
            val surface = Surface(3, 3)
            surface.canvas {
                saveLayer(SaveLayerRec(paint = Paint(imageFilter = ImageFilter.SpotLitDiffuse(
                    Point3F32(1f, 0f, 1f), Point3F32(1f, 0f, 0f), exponent, 90f,
                    ColorARGB.White, 1f, 1f), antiAlias = false)))
                listOf(1 to 0, 0 to 1, 1 to 1, 1 to 2).forEach { (x, y) ->
                    drawRect(RectF32.ofLTRB(x.toFloat(), y.toFloat(), x + 1f, y + 1f), Paint(ColorARGB.White, antiAlias = false))
                }
                restore()
            }
            assertFamilyNear(expected, surface.render(), maxChannelDelta = 2)
        }
    }

    @Test
    fun `negative lighting coefficients and non finite parameters refuse without readback mutation then recover`() {
        listOf(
            ImageFilter.DistantLitDiffuse(Vector3F32(1f, 0f, 1f), ColorARGB.White, 1f, -.1f) to "w6b.filter.invalid_bounds:",
            ImageFilter.PointLitSpecular(Point3F32(1f, 0f, 1f), ColorARGB.White, 1f, -.1f, 2f) to "w6b.filter.invalid_bounds:",
            ImageFilter.DistantLitDiffuse(Vector3F32(Float.NaN, 0f, 1f), ColorARGB.White, 1f, 1f) to "non-finite-value:",
            ImageFilter.SpotLitDiffuse(Point3F32(1f, 0f, 1f), Point3F32(1f, 0f, 0f), Float.POSITIVE_INFINITY, 90f,
                ColorARGB.White, 1f, 1f) to "non-finite-value:",
        ).forEach { (filter, prefix) -> assertLightingRefusesAndSurfaceRecovers(filter, prefix) }
    }

    @Test
    fun `perspective lighting mapping refuses without readback mutation then recovers`() {
        val expectedRecovery = recoveryRed2x2()
        val surface = Surface(2, 2)
        surface.canvas {
            setMatrix(Matrix3x3F32(persp0 = .25f))
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = ImageFilter.DistantLitDiffuse(
                Vector3F32(1f, 0f, 1f), ColorARGB.White, 1f, 1f), antiAlias = false)))
            resetMatrix()
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.White, antiAlias = false))
            restore()
        }
        assertLightingRefusalAndRecovery(surface, "w6a.layer.unsupported_lighting_mapping:", expectedRecovery)
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

    private fun assertDistantDiffuseNear(expected: UByteArray, surfaceScale: Float) {
        val surface = Surface(3, 3)
        surface.canvas {
            saveLayer(SaveLayerRec(paint = Paint(imageFilter = ImageFilter.DistantLitDiffuse(
                Vector3F32(1f, 0f, 1f), ColorARGB.White, surfaceScale, 1f), antiAlias = false)))
            listOf(1 to 0, 0 to 1, 1 to 1, 1 to 2).forEach { (x, y) ->
                drawRect(RectF32.ofLTRB(x.toFloat(), y.toFloat(), x + 1f, y + 1f), Paint(ColorARGB.White, antiAlias = false))
            }
            restore()
        }
        assertFamilyNear(expected, surface.render(), maxChannelDelta = 2)
    }

    private fun assertRemainingFamily(
        family: W6dLightingCpuOracle.Family,
        filter: ImageFilter,
        expectedTopLeft: UByteArray? = null,
        specularExponent: Float = 1f,
        cutoffDegrees: Float = 90f,
        location: Point3F32 = Point3F32(1f, 0f, 1f),
        target: Point3F32 = Point3F32(1f, 0f, 0f),
    ) {
        val alpha = alphaFixture()
        val expected = W6dLightingCpuOracle.remainingFamilyRgba8(family, 3, 3, alpha,
            locationX = location.x, locationY = location.y, locationZ = location.z,
            targetX = target.x, targetY = target.y, targetZ = target.z,
            surfaceDepth = 1f, coefficient = 1f, shininess = 2f, specularExponent = specularExponent, cutoffDegrees = cutoffDegrees)
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

    private fun assertLightingRefusesAndSurfaceRecovers(filter: ImageFilter, diagnosticPrefix: String) {
        val expectedRecovery = recoveryRed2x2()
        val surface = Surface(2, 2)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.White, imageFilter = filter, antiAlias = false))
        }
        assertLightingRefusalAndRecovery(surface, diagnosticPrefix, expectedRecovery)
    }

    private fun assertLightingRefusalAndRecovery(surface: Surface, diagnosticPrefix: String, expectedRecovery: UByteArray) {
        val sentinel = UByteArray(16) { 0x5au }
        val before = sentinel.copyOf()
        val failure = assertFailsWith<IllegalStateException> {
            surface.readPixels(RectF32.ofLTRB(0f, 0f, 2f, 2f), sentinel)
        }
        assertTrue(failure.message?.startsWith(diagnosticPrefix) == true, failure.message ?: "missing diagnostic")
        assertContentEquals(before, sentinel)
        surface.discardRecordedOperations()
        surface.canvas {
            resetMatrix()
            drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(ColorARGB.Red, antiAlias = false))
        }
        val recovery = surface.render()
        assertTrue(recovery.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")), recovery.nativeEvidenceScopeKinds.toString())
        assertContentEquals(expectedRecovery, recovery.pixels)
    }

    private fun recoveryRed2x2(): UByteArray = ubyteArrayOf(
        255u, 0u, 0u, 255u,
        0u, 0u, 0u, 0u,
        0u, 0u, 0u, 0u,
        0u, 0u, 0u, 0u,
    )
}
