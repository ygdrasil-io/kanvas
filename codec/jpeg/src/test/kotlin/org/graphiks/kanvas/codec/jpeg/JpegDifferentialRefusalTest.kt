package org.graphiks.kanvas.codec.jpeg

import org.graphiks.kanvas.codec.Codec
import org.graphiks.kanvas.codec.test.CodecTestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.skia.foundation.SkColorType

/**
 * Structural probes only: the entropy bytes are deliberately not interpreted.
 *
 * A differential JPEG frame needs a validated hierarchical reference frame, which the
 * document-level hierarchy implementation owns. These probes establish that a standalone
 * document reports that missing prerequisite before any entropy or pixel path is selected.
 */
class JpegDifferentialRefusalTest {

    @Test
    fun `every differential SOF reports that its reference frame is required`() {
        for (marker in intArrayOf(0xC5, 0xC6, 0xC7, 0xCD, 0xCE, 0xCF)) {
            val data = differentialSofProbe(marker)
            val document = JpegDocument.open(data).document
            assertNotNull(document, "SOF${marker.toString(16)}")

            val result = document!!.decode(JpegDecodeRequest(SkColorType.kRGBA_8888, null))

            assertNull(result.bitmap, "SOF${marker.toString(16)}")
            assertEquals("jpeg.differential.reference.required", result.diagnostic?.code, "SOF${marker.toString(16)}")
            assertEquals(Codec.Result.kUnimplemented, result.diagnostic?.result, "SOF${marker.toString(16)}")
            assertEquals(
                document.segments.single { it.marker == marker }.offset,
                result.diagnostic?.offset,
                "SOF${marker.toString(16)}",
            )
        }
    }

    private fun differentialSofProbe(marker: Int): ByteArray =
        CodecTestFixtures.simpleGrayscaleJpeg().also { jpeg ->
            val sof = jpeg.indexOfSof0()
            jpeg[sof + 1] = marker.toByte()
        }

    private fun ByteArray.indexOfSof0(): Int =
        indices.firstOrNull { index ->
            index + 1 < size && this[index] == 0xFF.toByte() && this[index + 1] == 0xC0.toByte()
        } ?: error("SOF0 marker missing")
}
