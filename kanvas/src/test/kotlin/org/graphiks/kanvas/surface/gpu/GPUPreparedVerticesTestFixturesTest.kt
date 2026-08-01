package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GPUPreparedVerticesTestFixturesTest {

    private fun fullFixture(): GPUPreparedVerticesTestFixture =
        GPUPreparedVerticesTestFixture.create(
            positions = floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f),
            colorsRgba8 = byteArrayOf(
                255.toByte(), 0, 0, 255.toByte(),
                0, 255.toByte(), 0, 255.toByte(),
                0, 0, 255.toByte(), 255.toByte(),
            ),
            texCoords = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f),
            indices = intArrayOf(0, 1, 2),
            topology = GPUPreparedVerticesTopology.TRIANGLES,
            transform = GPUPreparedVerticesAffineTransform.translate(1f, 1f),
            clip = GPUPreparedVerticesRectClip(0, 0, 4, 4),
            blendMode = GPUPreparedVerticesBlendMode.SRC_OVER,
            paintAlpha = 0.75f,
            image = GPUPreparedVerticesImage.create(
                pixels = byteArrayOf(
                    255.toByte(), 255.toByte(), 255.toByte(), 255.toByte(),
                    255.toByte(), 255.toByte(), 255.toByte(), 255.toByte(),
                    255.toByte(), 255.toByte(), 255.toByte(), 255.toByte(),
                    255.toByte(), 255.toByte(), 255.toByte(), 255.toByte(),
                ),
                width = 2,
                height = 2,
                filterMode = GPUPreparedVerticesFilterMode.NEAREST,
            ),
            pixelWidth = 4,
            pixelHeight = 4,
        )

    @Test
    fun `positions accessor returns a fresh copy every time`() {
        val fixture = fullFixture()
        val first = fixture.positionsCopy
        first[0] = -999f
        val second = fixture.positionsCopy
        assertNotSame(first, second)
        assertContentEquals(floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f), second)
    }

    @Test
    fun `colors accessor returns a fresh copy every time`() {
        val fixture = fullFixture()
        val first = fixture.colorsRgba8Copy!!
        first[0] = 7
        val second = fixture.colorsRgba8Copy!!
        assertNotSame(first, second)
        assertContentEquals(
            byteArrayOf(
                255.toByte(), 0, 0, 255.toByte(),
                0, 255.toByte(), 0, 255.toByte(),
                0, 0, 255.toByte(), 255.toByte(),
            ),
            second,
        )
    }

    @Test
    fun `tex coords accessor returns a fresh copy every time`() {
        val fixture = fullFixture()
        val first = fixture.texCoordsCopy!!
        first[0] = -1f
        val second = fixture.texCoordsCopy!!
        assertNotSame(first, second)
        assertContentEquals(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f), second)
    }

    @Test
    fun `indices accessor returns a fresh copy every time`() {
        val fixture = fullFixture()
        val first = fixture.indicesCopy!!
        first[0] = 99
        val second = fixture.indicesCopy!!
        assertNotSame(first, second)
        assertContentEquals(intArrayOf(0, 1, 2), second)
    }

    @Test
    fun `image pixel bytes accessor returns a fresh copy every time`() {
        val fixture = fullFixture()
        val first = fixture.imageCopy!!.pixelsCopy
        first[0] = 0
        val second = fixture.imageCopy!!.pixelsCopy
        assertNotSame(first, second)
        assertTrue(second.all { it.toInt() and 0xff == 255 })
    }

    @Test
    fun `image accessor returns a fresh instance every time`() {
        val fixture = fullFixture()
        val a = fixture.imageCopy!!
        val b = fixture.imageCopy!!
        assertNotSame(a, b)
        a.pixelsCopy[0] = 0
        assertTrue(b.pixelsCopy.all { it.toInt() and 0xff == 255 })
    }

    @Test
    fun `null optional attribute accessors return null`() {
        val fixture = GPUPreparedVerticesTestFixture.create(
            positions = floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f),
            topology = GPUPreparedVerticesTopology.TRIANGLES,
            pixelWidth = 2,
            pixelHeight = 2,
        )
        assertNull(fixture.colorsRgba8Copy)
        assertNull(fixture.texCoordsCopy)
        assertNull(fixture.indicesCopy)
        assertNull(fixture.imageCopy)
        assertNull(fixture.clip)
    }

    @Test
    fun `create rejects odd position count`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedVerticesTestFixture.create(
                positions = floatArrayOf(0f, 0f, 2f, 0f, 0f),
                topology = GPUPreparedVerticesTopology.TRIANGLES,
                pixelWidth = 2,
                pixelHeight = 2,
            )
        }
    }

    @Test
    fun `create rejects too few positions for one triangle`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedVerticesTestFixture.create(
                positions = floatArrayOf(0f, 0f, 2f, 0f),
                topology = GPUPreparedVerticesTopology.TRIANGLES,
                pixelWidth = 2,
                pixelHeight = 2,
            )
        }
    }

    @Test
    fun `create rejects non-finite positions`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedVerticesTestFixture.create(
                positions = floatArrayOf(0f, 0f, Float.NaN, 0f, 0f, 2f),
                topology = GPUPreparedVerticesTopology.TRIANGLES,
                pixelWidth = 2,
                pixelHeight = 2,
            )
        }
    }

    @Test
    fun `create rejects colors with wrong size`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedVerticesTestFixture.create(
                positions = floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f),
                colorsRgba8 = byteArrayOf(255.toByte(), 0, 0, 255.toByte()),
                topology = GPUPreparedVerticesTopology.TRIANGLES,
                pixelWidth = 2,
                pixelHeight = 2,
            )
        }
    }

    @Test
    fun `create rejects colors violating the premultiplied invariant`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedVerticesTestFixture.create(
                positions = floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f),
                colorsRgba8 = byteArrayOf(
                    255.toByte(), 0, 0, 128.toByte(),
                    0, 255.toByte(), 0, 255.toByte(),
                    0, 0, 255.toByte(), 255.toByte(),
                ),
                topology = GPUPreparedVerticesTopology.TRIANGLES,
                pixelWidth = 2,
                pixelHeight = 2,
            )
        }
    }

    @Test
    fun `create rejects tex coords with wrong size`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedVerticesTestFixture.create(
                positions = floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f),
                texCoords = floatArrayOf(0f, 0f, 1f, 0f),
                topology = GPUPreparedVerticesTopology.TRIANGLES,
                pixelWidth = 2,
                pixelHeight = 2,
            )
        }
    }

    @Test
    fun `create rejects indices out of range`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedVerticesTestFixture.create(
                positions = floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f),
                indices = intArrayOf(0, 1, 3),
                topology = GPUPreparedVerticesTopology.TRIANGLES,
                pixelWidth = 2,
                pixelHeight = 2,
            )
        }
    }

    @Test
    fun `create rejects triangle topology with non-multiple-of-three indices`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedVerticesTestFixture.create(
                positions = floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f, 2f, 2f),
                indices = intArrayOf(0, 1, 2, 3),
                topology = GPUPreparedVerticesTopology.TRIANGLES,
                pixelWidth = 2,
                pixelHeight = 2,
            )
        }
    }

    @Test
    fun `create rejects paint alpha outside the closed unit interval`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedVerticesTestFixture.create(
                positions = floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f),
                topology = GPUPreparedVerticesTopology.TRIANGLES,
                paintAlpha = 1.25f,
                pixelWidth = 2,
                pixelHeight = 2,
            )
        }
    }

    @Test
    fun `create rejects non-finite paint alpha`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedVerticesTestFixture.create(
                positions = floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f),
                topology = GPUPreparedVerticesTopology.TRIANGLES,
                paintAlpha = Float.POSITIVE_INFINITY,
                pixelWidth = 2,
                pixelHeight = 2,
            )
        }
    }

    @Test
    fun `create rejects image with wrong pixel byte size`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedVerticesImage.create(
                pixels = byteArrayOf(255.toByte(), 0, 0, 255.toByte()),
                width = 2,
                height = 2,
                filterMode = GPUPreparedVerticesFilterMode.NEAREST,
            )
        }
    }

    @Test
    fun `create rejects image violating the premultiplied invariant`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedVerticesImage.create(
                pixels = byteArrayOf(255.toByte(), 0, 0, 128.toByte()),
                width = 1,
                height = 1,
                filterMode = GPUPreparedVerticesFilterMode.NEAREST,
            )
        }
    }

    @Test
    fun `create rejects image when tex coords are absent`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedVerticesTestFixture.create(
                positions = floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f),
                topology = GPUPreparedVerticesTopology.TRIANGLES,
                image = GPUPreparedVerticesImage.create(
                    pixels = byteArrayOf(255.toByte(), 255.toByte(), 255.toByte(), 255.toByte()),
                    width = 1,
                    height = 1,
                    filterMode = GPUPreparedVerticesFilterMode.NEAREST,
                ),
                pixelWidth = 2,
                pixelHeight = 2,
            )
        }
    }

    @Test
    fun `create rejects non-positive pixel dimensions`() {
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedVerticesTestFixture.create(
                positions = floatArrayOf(0f, 0f, 2f, 0f, 0f, 2f),
                topology = GPUPreparedVerticesTopology.TRIANGLES,
                pixelWidth = 0,
                pixelHeight = 2,
            )
        }
    }
}
