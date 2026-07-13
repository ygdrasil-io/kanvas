package org.graphiks.kanvas.codec.jpeg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JpegTypesTest {

    @Test
    fun `classifies every non-reserved SOF marker`() {
        assertEquals(JpegSampleCoding.DCT_SEQUENTIAL, JpegFrameSpec.fromSof(0xC0)!!.sampleCoding)
        assertEquals(JpegEntropyCoding.ARITHMETIC, JpegFrameSpec.fromSof(0xCE)!!.entropyCoding)
        assertNull(JpegFrameSpec.fromSof(0xC8))
    }
}
