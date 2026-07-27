package org.graphiks.kanvas.surface.gpu

import kotlin.math.abs
import org.graphiks.kanvas.image.ColorType
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GPUPreparedImageTestFixturesTest {

    @Test
    fun `rgba premul 2x2 has correct dimensions and stride`() {
        assertEquals(2, GPUPreparedImageTestFixtures.rgbaPremul2x2Width)
        assertEquals(2, GPUPreparedImageTestFixtures.rgbaPremul2x2Height)
        assertEquals(ColorType.RGBA_8888, GPUPreparedImageTestFixtures.rgbaPremul2x2ColorType)
        assertEquals(16, GPUPreparedImageTestFixtures.rgbaPremul2x2Bytes.size)
    }

    @Test
    fun `rgba premul 2x2 has expected premultiplied red pixel`() {
        val p = GPUPreparedImageTestFixtures.rgbaPremul2x2Bytes
        assertEquals(128.toByte(), p[0]) // R premultiplied by alpha 128
        assertEquals(0.toByte(), p[1]) // G
        assertEquals(0.toByte(), p[2]) // B
        assertEquals(128.toByte().toInt().and(0xFF), p[3].toInt().and(0xFF)) // A
    }

    @Test
    fun `rgba premul 2x2 has expected premultiplied green pixel`() {
        val p = GPUPreparedImageTestFixtures.rgbaPremul2x2Bytes
        assertEquals(0.toByte(), p[4]) // R
        assertEquals(128.toByte(), p[5]) // G premultiplied by alpha 128
        assertEquals(0.toByte(), p[6]) // B
        assertEquals(128.toByte().toInt().and(0xFF), p[7].toInt().and(0xFF)) // A
    }

    @Test
    fun `rgba premul 2x2 has expected premultiplied blue pixel`() {
        val p = GPUPreparedImageTestFixtures.rgbaPremul2x2Bytes
        assertEquals(0.toByte(), p[8]) // R
        assertEquals(0.toByte(), p[9]) // G
        assertEquals(128.toByte(), p[10]) // B premultiplied by alpha 128
        assertEquals(128.toByte().toInt().and(0xFF), p[11].toInt().and(0xFF)) // A
    }

    @Test
    fun `rgba premul 2x2 has expected premultiplied white pixel`() {
        val p = GPUPreparedImageTestFixtures.rgbaPremul2x2Bytes
        assertEquals(128.toByte(), p[12]) // R premultiplied
        assertEquals(128.toByte(), p[13]) // G premultiplied
        assertEquals(128.toByte(), p[14]) // B premultiplied
        assertEquals(128.toByte().toInt().and(0xFF), p[15].toInt().and(0xFF)) // A
    }

    @Test
    fun `rgba premul 2x2 premultiplication invariant rgb le alpha`() {
        val p = GPUPreparedImageTestFixtures.rgbaPremul2x2Bytes
        for (i in 0 until 4) {
            val offset = i * 4
            val r = p[offset].toInt().and(0xFF)
            val g = p[offset + 1].toInt().and(0xFF)
            val b = p[offset + 2].toInt().and(0xFF)
            val a = p[offset + 3].toInt().and(0xFF)
            assertTrue(r <= a, "R=$r must be <= alpha=$a at pixel $i")
            assertTrue(g <= a, "G=$g must be <= alpha=$a at pixel $i")
            assertTrue(b <= a, "B=$b must be <= alpha=$a at pixel $i")
        }
    }

    @Test
    fun `bgra opaque 2x2 has correct dimensions and color type`() {
        assertEquals(2, GPUPreparedImageTestFixtures.bgraOpaque2x2Width)
        assertEquals(2, GPUPreparedImageTestFixtures.bgraOpaque2x2Height)
        assertEquals(ColorType.BGRA_8888, GPUPreparedImageTestFixtures.bgraOpaque2x2ColorType)
        assertEquals(16, GPUPreparedImageTestFixtures.bgraOpaque2x2Bytes.size)
    }

    @Test
    fun `bgra opaque 2x2 has expected BGRA channel order for red`() {
        val p = GPUPreparedImageTestFixtures.bgraOpaque2x2Bytes
        assertEquals(0.toByte(), p[0]) // B
        assertEquals(0.toByte(), p[1]) // G
        assertEquals(255.toByte().toInt().and(0xFF), p[2].toInt().and(0xFF)) // R
        assertEquals(255.toByte().toInt().and(0xFF), p[3].toInt().and(0xFF)) // A opaque
    }

    @Test
    fun `bgra opaque 2x2 has expected BGRA channel order for green`() {
        val p = GPUPreparedImageTestFixtures.bgraOpaque2x2Bytes
        assertEquals(0.toByte(), p[4]) // B
        assertEquals(255.toByte().toInt().and(0xFF), p[5].toInt().and(0xFF)) // G
        assertEquals(0.toByte(), p[6]) // R
        assertEquals(255.toByte().toInt().and(0xFF), p[7].toInt().and(0xFF)) // A
    }

    @Test
    fun `bgra opaque 2x2 has expected BGRA channel order for blue`() {
        val p = GPUPreparedImageTestFixtures.bgraOpaque2x2Bytes
        assertEquals(255.toByte().toInt().and(0xFF), p[8].toInt().and(0xFF)) // B
        assertEquals(0.toByte(), p[9]) // G
        assertEquals(0.toByte(), p[10]) // R
        assertEquals(255.toByte().toInt().and(0xFF), p[11].toInt().and(0xFF)) // A
    }

    @Test
    fun `bgra opaque 2x2 has expected BGRA channel order for white`() {
        val p = GPUPreparedImageTestFixtures.bgraOpaque2x2Bytes
        assertEquals(255.toByte().toInt().and(0xFF), p[12].toInt().and(0xFF)) // B
        assertEquals(255.toByte().toInt().and(0xFF), p[13].toInt().and(0xFF)) // G
        assertEquals(255.toByte().toInt().and(0xFF), p[14].toInt().and(0xFF)) // R
        assertEquals(255.toByte().toInt().and(0xFF), p[15].toInt().and(0xFF)) // A
    }

    @Test
    fun `a8 3x1 has correct dimensions and color type`() {
        assertEquals(3, GPUPreparedImageTestFixtures.a8_3x1Width)
        assertEquals(1, GPUPreparedImageTestFixtures.a8_3x1Height)
        assertEquals(ColorType.ALPHA_8, GPUPreparedImageTestFixtures.a8_3x1ColorType)
        assertEquals(3, GPUPreparedImageTestFixtures.a8_3x1Bytes.size)
    }

    @Test
    fun `a8 3x1 has expected alpha values`() {
        val p = GPUPreparedImageTestFixtures.a8_3x1Bytes
        assertEquals(0.toByte(), p[0])
        assertEquals(128.toByte().toInt().and(0xFF), p[1].toInt().and(0xFF))
        assertEquals(255.toByte().toInt().and(0xFF), p[2].toInt().and(0xFF))
    }

    @Test
    fun `image nine 6x6 has correct dimensions and color type`() {
        assertEquals(6, GPUPreparedImageTestFixtures.imageNine6x6Width)
        assertEquals(6, GPUPreparedImageTestFixtures.imageNine6x6Height)
        assertEquals(ColorType.RGBA_8888, GPUPreparedImageTestFixtures.imageNine6x6ColorType)
        assertEquals(144, GPUPreparedImageTestFixtures.imageNine6x6Bytes.size)
    }

    @Test
    fun `image nine 6x6 corner regions are distinct from center`() {
        val p = GPUPreparedImageTestFixtures.imageNine6x6Bytes
        val cornerPixel = pixelAt(p, 6, 0, 0)
        val centerPixel = pixelAt(p, 6, 3, 3)
        assertTrue(
            !cornerPixel.contentEquals(centerPixel),
            "corner (0,0) must differ from center (3,3)",
        )
    }

    @Test
    fun `image nine 6x6 edge regions are distinct from corners and center`() {
        val p = GPUPreparedImageTestFixtures.imageNine6x6Bytes
        val cornerPixel = pixelAt(p, 6, 0, 0)
        val edgeTopPixel = pixelAt(p, 6, 2, 0)
        val edgeLeftPixel = pixelAt(p, 6, 0, 2)
        val centerPixel = pixelAt(p, 6, 3, 3)
        assertTrue(!edgeTopPixel.contentEquals(cornerPixel), "edge top must differ from corner")
        assertTrue(!edgeLeftPixel.contentEquals(cornerPixel), "edge left must differ from corner")
        assertTrue(!edgeTopPixel.contentEquals(centerPixel), "edge top must differ from center")
    }

    @Test
    fun `image nine 6x6 all nine regions are uniformly filled`() {
        val p = GPUPreparedImageTestFixtures.imageNine6x6Bytes
        for (regionY in 0 until 3) {
            for (regionX in 0 until 3) {
                val reference = pixelAt(p, 6, regionX * 2, regionY * 2)
                for (dy in 0 until 2) {
                    for (dx in 0 until 2) {
                        val actual = pixelAt(p, 6, regionX * 2 + dx, regionY * 2 + dy)
                        assertArrayEquals(reference, actual, "region ($regionX,$regionY) uniform")
                    }
                }
            }
        }
    }

    @Test
    fun `atlas 4x4 has correct dimensions and color type`() {
        assertEquals(4, GPUPreparedImageTestFixtures.atlas4x4Width)
        assertEquals(4, GPUPreparedImageTestFixtures.atlas4x4Height)
        assertEquals(ColorType.RGBA_8888, GPUPreparedImageTestFixtures.atlas4x4ColorType)
        assertEquals(64, GPUPreparedImageTestFixtures.atlas4x4Bytes.size)
    }

    @Test
    fun `atlas 4x4 quadrant regions are distinct`() {
        val p = GPUPreparedImageTestFixtures.atlas4x4Bytes
        val q0 = pixelAt(p, 4, 0, 0)
        val q1 = pixelAt(p, 4, 2, 0)
        val q2 = pixelAt(p, 4, 0, 2)
        val q3 = pixelAt(p, 4, 2, 2)
        assertTrue(!q0.contentEquals(q1), "quadrant 0 must differ from 1")
        assertTrue(!q0.contentEquals(q2), "quadrant 0 must differ from 2")
        assertTrue(!q0.contentEquals(q3), "quadrant 0 must differ from 3")
        assertTrue(!q1.contentEquals(q2), "quadrant 1 must differ from 2")
    }

    @Test
    fun `atlas 4x4 quadrants are uniformly filled`() {
        val p = GPUPreparedImageTestFixtures.atlas4x4Bytes
        for (qy in 0 until 2) {
            for (qx in 0 until 2) {
                val ref = pixelAt(p, 4, qx * 2, qy * 2)
                for (dy in 0 until 2) {
                    for (dx in 0 until 2) {
                        assertArrayEquals(ref, pixelAt(p, 4, qx * 2 + dx, qy * 2 + dy),
                            "quadrant ($qx,$qy) uniform")
                    }
                }
            }
        }
    }

    @Test
    fun `fixtures hash is stable`() {
        assertEquals(
            GPUPreparedImageTestFixtures.rgbaPremul2x2Bytes.contentHashCode(),
            GPUPreparedImageTestFixtures.rgbaPremul2x2Bytes.contentHashCode(),
            "rgba premul 2x2 hash must be stable",
        )
        assertEquals(
            GPUPreparedImageTestFixtures.bgraOpaque2x2Bytes.contentHashCode(),
            GPUPreparedImageTestFixtures.bgraOpaque2x2Bytes.contentHashCode(),
            "bgra opaque 2x2 hash must be stable",
        )
        assertEquals(
            GPUPreparedImageTestFixtures.a8_3x1Bytes.contentHashCode(),
            GPUPreparedImageTestFixtures.a8_3x1Bytes.contentHashCode(),
            "a8 3x1 hash must be stable",
        )
        assertEquals(
            GPUPreparedImageTestFixtures.imageNine6x6Bytes.contentHashCode(),
            GPUPreparedImageTestFixtures.imageNine6x6Bytes.contentHashCode(),
            "image nine 6x6 hash must be stable",
        )
        assertEquals(
            GPUPreparedImageTestFixtures.atlas4x4Bytes.contentHashCode(),
            GPUPreparedImageTestFixtures.atlas4x4Bytes.contentHashCode(),
            "atlas 4x4 hash must be stable",
        )
    }

    @Test
    fun `nearest sample oracle returns exact texel for integer UV`() {
        val oracle = GPUPreparedImagePixelOracle
        val bytes = GPUPreparedImageTestFixtures.rgbaPremul2x2Bytes
        val width = GPUPreparedImageTestFixtures.rgbaPremul2x2Width
        val height = GPUPreparedImageTestFixtures.rgbaPremul2x2Height

        val result = oracle.nearestSample(bytes, width, height, 0f, 0f)
        assertArrayEquals(ia(128, 0, 0, 128), result)

        val result2 = oracle.nearestSample(bytes, width, height, 1f, 0f)
        assertArrayEquals(ia(0, 128, 0, 128), result2)

        val result3 = oracle.nearestSample(bytes, width, height, 0f, 1f)
        assertArrayEquals(ia(0, 0, 128, 128), result3)

        val result4 = oracle.nearestSample(bytes, width, height, 1f, 1f)
        assertArrayEquals(ia(128, 128, 128, 128), result4)
    }

    @Test
    fun `nearest sample clamps UV to texture bounds`() {
        val oracle = GPUPreparedImagePixelOracle
        val bytes = ia(10, 20, 30, 40)
        val clamped = oracle.nearestSample(bytes, 1, 1, -1f, 0f)
        assertArrayEquals(ia(10, 20, 30, 40), clamped)
        val clamped2 = oracle.nearestSample(bytes, 1, 1, 0f, 2f)
        assertArrayEquals(ia(10, 20, 30, 40), clamped2)
    }

    @Test
    fun `linear sample oracle returns corner texel for integer UV`() {
        val oracle = GPUPreparedImagePixelOracle
        val bytes = GPUPreparedImageTestFixtures.rgbaPremul2x2Bytes
        val result = oracle.linearSample(bytes, 2, 2, 0f, 0f)
        assertArrayEquals(ia(128, 0, 0, 128), result)
    }

    @Test
    fun `linear sample oracle interpolates between two texels horizontally`() {
        val oracle = GPUPreparedImagePixelOracle
        val bytes = ia(0, 0, 0, 255, 100, 100, 100, 255)
        val result = oracle.linearSample(bytes, 2, 1, 0.5f, 0f)
        val diff = abs(result[0].toInt().and(0xFF) - 50)
        assertTrue(diff <= 1, "expected ~50, got ${result[0].toInt().and(0xFF)}")
    }

    @Test
    fun `apply tint and paint alpha multiplies correctly`() {
        val oracle = GPUPreparedImagePixelOracle
        val src = ia(100, 100, 100, 100)
        val tint = floatArrayOf(1f, 0.5f, 0f, 1f)
        val paintAlpha = 0.5f
        val result = oracle.applyTint(src, tint, paintAlpha)
        assertEquals(100, result[0].toInt().and(0xFF))
        assertEquals(50, result[1].toInt().and(0xFF))
        assertEquals(0, result[2].toInt().and(0xFF))
        assertEquals(50, result[3].toInt().and(0xFF))
    }

    @Test
    fun `apply tint does not apply tint or paint alpha more than once`() {
        val oracle = GPUPreparedImagePixelOracle
        val src = ia(100, 100, 100, 100)
        val tint = floatArrayOf(0.5f, 0.5f, 0.5f, 1f)
        val once = oracle.applyTint(src, tint, 0.8f)
        val twice = oracle.applyTint(once, tint, 0.8f)
        assertTrue(
            !once.contentEquals(twice),
            "twice-applied tint should differ from once-applied",
        )
    }

    @Test
    fun `exact comparison returns true for identical pixels`() {
        val oracle = GPUPreparedImagePixelOracle
        val a = ia(10, 20, 30, 40)
        val b = ia(10, 20, 30, 40)
        assertTrue(oracle.exactMatch(a, b))
    }

    @Test
    fun `exact comparison returns false for different pixels`() {
        val oracle = GPUPreparedImagePixelOracle
        val a = ia(10, 20, 30, 40)
        val b = ia(10, 20, 30, 41)
        assertTrue(!oracle.exactMatch(a, b))
    }

    @Test
    fun `linear comparison passes with delta le 1`() {
        val oracle = GPUPreparedImagePixelOracle
        val a = ia(10, 20, 30, 40)
        val b = ia(11, 19, 31, 41)
        assertTrue(oracle.linearMatch(a, b))
    }

    @Test
    fun `linear comparison fails with delta gt 1`() {
        val oracle = GPUPreparedImagePixelOracle
        val a = ia(10, 20, 30, 40)
        val b = ia(12, 20, 30, 40)
        assertTrue(!oracle.linearMatch(a, b))
    }

    @Test
    fun `source rect and UV clamp produces expected mapping`() {
        val oracle = GPUPreparedImagePixelOracle
        val bytes = GPUPreparedImageTestFixtures.rgbaPremul2x2Bytes
        val result = oracle.sourceRectSample(
            bytes, 2, 2,
            srcL = 0f, srcT = 0f, srcR = 1f, srcB = 1f,
            u = 0.25f, v = 0.25f,
            sample = GPUPreparedImagePixelOracle.SampleKind.NEAREST,
        )
        assertArrayEquals(ia(128, 0, 0, 128), result)
    }

    @Test
    fun `UV clamp restricts to source rect bounds`() {
        val oracle = GPUPreparedImagePixelOracle
        val bytes = GPUPreparedImageTestFixtures.rgbaPremul2x2Bytes
        val uInside = oracle.sourceRectSample(
            bytes, 2, 2,
            srcL = 0.25f, srcT = 0.25f, srcR = 0.75f, srcB = 0.75f,
            u = 0f, v = 0f,
            sample = GPUPreparedImagePixelOracle.SampleKind.NEAREST,
        )
        val uClamped = oracle.sourceRectSample(
            bytes, 2, 2,
            srcL = 0.25f, srcT = 0.25f, srcR = 0.75f, srcB = 0.75f,
            u = -1f, v = -1f,
            sample = GPUPreparedImagePixelOracle.SampleKind.NEAREST,
        )
        assertArrayEquals(uInside, uClamped, "UV clamp must fold out-of-range UV into source rect")
    }

    private fun pixelAt(bytes: ByteArray, stride: Int, x: Int, y: Int): ByteArray {
        val offset = (y * stride + x) * 4
        return bytes.copyOfRange(offset, offset + 4)
    }

    companion object {
        private fun ia(vararg values: Int): ByteArray =
            ByteArray(values.size) { values[it].toByte() }
    }
}
