package org.graphiks.kanvas.color.cicp

import org.graphiks.kanvas.color.ColorProfiles
import org.graphiks.kanvas.color.ColorTransform
import org.graphiks.kanvas.color.HdrTransferFunction
import org.graphiks.kanvas.color.AlphaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CicpColorInfoTest {

    @Test
    fun `png rgb cicp pq rec2020 builds hdr source profile`() {
        val profile = CicpColorInfo(primaries = 9, transfer = 16, matrix = 0, fullRange = true)
            .toColorProfile()
            .getOrThrow()

        assertTrue(profile.isHdr)
        assertEquals(ColorProfiles.rec2020().toXyzD50, profile.toXyzD50)
    }

    @Test
    fun `png display p3 cicp uses H273 code points 12 and 13`() {
        val profile = CicpColorInfo(primaries = 12, transfer = 13, matrix = 0, fullRange = true)
            .toColorProfile()
            .getOrThrow()

        assertFalse(profile.isHdr)
        assertEquals(ColorProfiles.displayP3().toXyzD50, profile.toXyzD50)
        assertEquals(ColorProfiles.sRGB().transferFunction, profile.transferFunction)
    }

    @Test
    fun `supported sdr transfer code points build transformable profiles`() {
        listOf(1, 8, 13, 14, 15).forEach { transfer ->
            val profile = CicpColorInfo(primaries = 1, transfer = transfer, matrix = 0, fullRange = false)
                .toColorProfile()
                .getOrThrow()

            assertFalse(profile.isHdr, "transfer=$transfer")
            assertTrue(profile.hasMatrixTrc, "transfer=$transfer")
            val compileFailure = ColorTransform.compile(
                profile,
                ColorProfiles.sRGB(),
                AlphaType.UNPREMULTIPLIED,
            ).failureOrNull()
            assertNull(compileFailure, "transfer=$transfer failure=$compileFailure")
        }
    }

    @Test
    fun `unsupported png matrix is a stable typed refusal`() {
        val failure = CicpColorInfo(primaries = 9, transfer = 16, matrix = 9, fullRange = true)
            .toColorProfile()
            .failureOrNull()

        assertEquals("cicp.matrix.unsupported", failure!!.code)
    }

    @Test
    fun `unsupported primaries are a stable typed refusal`() {
        val failure = CicpColorInfo(primaries = 2, transfer = 13, matrix = 0, fullRange = true)
            .toColorProfile()
            .failureOrNull()

        assertEquals("cicp.primaries.unsupported", failure!!.code)
    }

    @Test
    fun `unsupported transfer is a stable typed refusal`() {
        val failure = CicpColorInfo(primaries = 1, transfer = 2, matrix = 0, fullRange = true)
            .toColorProfile()
            .failureOrNull()

        assertEquals("cicp.transfer.unsupported", failure!!.code)
    }

    @Test
    fun `hlg refuses primaries outside bt2020 with a stable typed failure`() {
        listOf(1, 12).forEach { primaries ->
            val failure = CicpColorInfo(primaries, transfer = 18, matrix = 0, fullRange = true)
                .toColorProfile()
                .failureOrNull()

            assertEquals("cicp.transfer.unsupported", failure!!.code, "primaries=$primaries")
        }
    }

    @Test
    fun `pq transfer decodes absolute luminance and round trips`() {
        val decoded = FloatArray(3)
        HdrTransferFunction.PQ.decode(
            floatArrayOf(0.5080784f, 0.7518271f, 1f),
            inputOffset = 0,
            output = decoded,
        )

        assertEquals(100f, decoded[0], 0.02f)
        assertEquals(1000f, decoded[1], 0.2f)
        assertEquals(10_000f, decoded[2], 1f)

        val encoded = FloatArray(3)
        HdrTransferFunction.PQ.encode(decoded, encoded)
        assertEquals(0.5080784f, encoded[0], 2e-6f)
        assertEquals(0.7518271f, encoded[1], 2e-6f)
        assertEquals(1f, encoded[2], 2e-6f)
    }

    @Test
    fun `hlg transfer applies reference display ootf and round trips`() {
        val decoded = FloatArray(3)
        HdrTransferFunction.HLG.decode(floatArrayOf(0.5f, 0.5f, 0.5f), 0, decoded)

        assertEquals(50.69703f, decoded[0], 0.002f)
        assertEquals(decoded[0], decoded[1], 1e-5f)
        assertEquals(decoded[1], decoded[2], 1e-5f)

        val encoded = FloatArray(3)
        HdrTransferFunction.HLG.encode(decoded, encoded)
        encoded.forEach { assertEquals(0.5f, it, 2e-6f) }

        HdrTransferFunction.HLG.decode(floatArrayOf(1f, 1f, 1f), 0, decoded)
        decoded.forEach { assertEquals(1000f, it, 0.002f) }
    }

    @Test
    fun `hlg chromatic signal round trips through coupled bt2020 ootf`() {
        val signal = floatArrayOf(0.75f, 0.5f, 0.25f)
        val decoded = FloatArray(3)
        val encoded = FloatArray(3)

        HdrTransferFunction.HLG.decode(signal, 0, decoded)
        HdrTransferFunction.HLG.encode(decoded, encoded)

        repeat(3) { channel -> assertEquals(signal[channel], encoded[channel], 3e-6f) }
    }
}
