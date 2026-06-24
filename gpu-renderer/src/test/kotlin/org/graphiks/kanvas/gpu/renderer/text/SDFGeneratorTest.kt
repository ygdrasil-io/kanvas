package org.graphiks.kanvas.gpu.renderer.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SDFGeneratorTest {
    @Test
    fun `generateFromA8 produces accepted result for valid input`() {
        val a8 = ByteArray(4) { 0xFF.toByte() }
        val result = SDFGenerator().generateFromA8(a8, 2, 2)
        assertTrue(result.accepted)
        assertEquals(2, result.width)
        assertEquals(2, result.height)
        assertEquals(SDFGenerator.SDF_RADIUS, result.radius)
    }

    @Test
    fun `generateFromA8 refuses zero width input`() {
        val result = SDFGenerator().generateFromA8(ByteArray(0), 0, 0)
        assertFalse(result.accepted)
        assertEquals("SDF generation requires positive dimensions", result.diagnostic)
    }

    @Test
    fun `generateFromA8 refuses mismatched buffer size`() {
        val result = SDFGenerator().generateFromA8(ByteArray(3), 2, 2)
        assertFalse(result.accepted)
        assertEquals("A8 pixel buffer size mismatch", result.diagnostic)
    }

    @Test
    fun `generateFromA8 produces sdf bytes matching dimensions`() {
        val a8 = ByteArray(64) { 0x80.toByte() }
        val result = SDFGenerator().generateFromA8(a8, 8, 8)
        assertTrue(result.accepted)
        assertEquals(64, result.sdfBytes.size)
    }

    @Test
    fun `sdf constants are defined`() {
        assertEquals(8f, SDFGenerator.SDF_RADIUS)
        assertEquals(0.5f, SDFGenerator.SDF_THRESHOLD)
        assertEquals(0.125f, SDFGenerator.SDF_SMOOTHING)
    }
}
