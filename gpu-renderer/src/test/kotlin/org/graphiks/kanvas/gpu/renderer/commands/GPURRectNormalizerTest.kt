package org.graphiks.kanvas.gpu.renderer.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GPURRectNormalizerTest {
    @Test
    fun `already fitting per corner radii remain unchanged`() {
        val source = rrect(
            rect = GPURect(1f, 2f, 41f, 32f),
            radii = listOf(3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f),
        )

        val normalized = assertIs<GPURRectNormalizationResult.Accepted>(
            GPURRectNormalizer.normalize(source),
        )

        assertEquals(source, normalized.rrect)
        assertEquals(1.0, normalized.scale)
        assertEquals(false, normalized.wasScaled)
    }

    @Test
    fun `overlapping sides scale every radius by the smallest Skia side ratio`() {
        val source = rrect(
            rect = GPURect(0f, 0f, 12f, 10f),
            radii = listOf(
                8f, 2f,
                8f, 6f,
                4f, 6f,
                2f, 2f,
            ),
        )

        val normalized = assertIs<GPURRectNormalizationResult.Accepted>(
            GPURRectNormalizer.normalize(source),
        )

        // Top requires 12 / (8 + 8) = 0.75; right requires 10 / (6 + 6) = 5 / 6.
        assertEquals(0.75, normalized.scale)
        assertEquals(true, normalized.wasScaled)
        assertRadii(
            normalized.rrect,
            listOf(6f, 1.5f, 6f, 4.5f, 3f, 4.5f, 1.5f, 1.5f),
        )
    }

    @Test
    fun `side sums use double precision like Skia instead of overflowing float`() {
        val source = rrect(
            rect = GPURect(0f, 0f, 100f, 80f),
            radii = listOf(
                Float.MAX_VALUE, 10f,
                Float.MAX_VALUE, 10f,
                1f, 10f,
                1f, 10f,
            ),
        )

        val normalized = assertIs<GPURRectNormalizationResult.Accepted>(
            GPURRectNormalizer.normalize(source),
        )

        assertTrue(normalized.scale > 0.0)
        assertEquals(50f, normalized.rrect.topLeft.x)
        assertEquals(50f, normalized.rrect.topRight.x)
        assertTrue(normalized.rrect.topLeft.y > 0f)
        assertSidesFit(normalized.rrect)
    }

    @Test
    fun `one zero radius component makes that corner square before scaling`() {
        val source = rrect(
            rect = GPURect(0f, 0f, 20f, 20f),
            radii = listOf(4f, 0f, 5f, 6f, 7f, 8f, 9f, 10f),
        )

        val normalized = assertIs<GPURRectNormalizationResult.Accepted>(
            GPURRectNormalizer.normalize(source),
        )

        assertEquals(GPURRectCornerRadii(0f, 0f), normalized.rrect.topLeft)
        assertEquals(GPURRectCornerRadii(5f, 6f), normalized.rrect.topRight)
    }

    @Test
    fun `negative zero is canonicalized to a positive square corner`() {
        val source = rrect(
            rect = GPURect(0f, 0f, 20f, 20f),
            radii = listOf(-0f, 4f, 5f, 6f, 7f, 8f, 9f, 10f),
        )

        val normalized = assertIs<GPURRectNormalizationResult.Accepted>(
            GPURRectNormalizer.normalize(source),
        )

        assertEquals(0f.toRawBits(), normalized.rrect.topLeft.x.toRawBits())
        assertEquals(0f.toRawBits(), normalized.rrect.topLeft.y.toRawBits())
    }

    @Test
    fun `ulp correction lowers a rounded radius until the side fits`() {
        val left = 55.80000305175781f
        val right = 556.576416015625f
        val radius = 260.0166320800781f
        val source = rrect(
            rect = GPURect(left, 0f, right, 1000f),
            radii = listOf(radius, 2f, radius, 2f, 2f, 2f, 2f, 2f),
        )

        val normalized = assertIs<GPURRectNormalizationResult.Accepted>(
            GPURRectNormalizer.normalize(source),
        )
        val idealRoundedRadius = (radius.toDouble() * normalized.scale).toFloat()
        val topLeft = normalized.rrect.topLeft.x
        val topRight = normalized.rrect.topRight.x

        assertTrue(topLeft.isFinite() && topRight.isFinite())
        assertTrue(topLeft >= 0f && topRight >= 0f)
        assertTrue(topLeft.toDouble() + topRight.toDouble() <= (right - left).toDouble())
        assertTrue(
            topLeft < idealRoundedRadius || topRight < idealRoundedRadius,
            "At least one rounded radius must be lowered by one ULP",
        )
    }

    @Test
    fun `malformed bounds non finite and negative radii refuse explicitly`() {
        val cases = listOf(
            rrect(GPURect(0f, 0f, 0f, 10f), validRadii) to
                "unsupported.geometry.rrect_bounds",
            rrect(GPURect(0f, 0f, Float.POSITIVE_INFINITY, 10f), validRadii) to
                "unsupported.geometry.rrect_bounds",
            rrect(GPURect(0f, 0f, 10f, 10f), validRadii.toMutableList().also {
                it[3] = Float.NaN
            }) to "unsupported.geometry.rrect_radii_non_finite",
            rrect(GPURect(0f, 0f, 10f, 10f), validRadii.toMutableList().also {
                it[6] = -1f
            }) to "unsupported.geometry.rrect_radii_negative",
        )

        cases.forEach { (source, expectedCode) ->
            assertEquals(
                expectedCode,
                assertIs<GPURRectNormalizationResult.Refused>(
                    GPURRectNormalizer.normalize(source),
                ).code,
            )
        }
    }

    private fun assertRadii(rrect: GPURRect, expected: List<Float>) {
        assertEquals(
            expected,
            listOf(
                rrect.topLeft.x,
                rrect.topLeft.y,
                rrect.topRight.x,
                rrect.topRight.y,
                rrect.bottomRight.x,
                rrect.bottomRight.y,
                rrect.bottomLeft.x,
                rrect.bottomLeft.y,
            ),
        )
    }

    private fun assertSidesFit(rrect: GPURRect) {
        val width = (rrect.rect.right - rrect.rect.left).toDouble()
        val height = (rrect.rect.bottom - rrect.rect.top).toDouble()
        assertTrue(rrect.topLeft.x.toDouble() + rrect.topRight.x.toDouble() <= width)
        assertTrue(rrect.topRight.y.toDouble() + rrect.bottomRight.y.toDouble() <= height)
        assertTrue(rrect.bottomRight.x.toDouble() + rrect.bottomLeft.x.toDouble() <= width)
        assertTrue(rrect.bottomLeft.y.toDouble() + rrect.topLeft.y.toDouble() <= height)
    }

    private fun rrect(rect: GPURect, radii: List<Float>): GPURRect {
        require(radii.size == 8)
        return GPURRect(
            rect = rect,
            topLeft = GPURRectCornerRadii(radii[0], radii[1]),
            topRight = GPURRectCornerRadii(radii[2], radii[3]),
            bottomRight = GPURRectCornerRadii(radii[4], radii[5]),
            bottomLeft = GPURRectCornerRadii(radii[6], radii[7]),
        )
    }

    private companion object {
        val validRadii = List(8) { 2f }
    }
}
