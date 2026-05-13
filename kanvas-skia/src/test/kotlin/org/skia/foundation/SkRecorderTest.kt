package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * R3.9 stub-coverage tests for the [SkRecorder] base class and its
 * raster concrete subtype [SkCPURecorder] — verifies the marker
 * shape (type / resourceCache) and the open / overridable contract.
 */
class SkRecorderTest {

    @Test
    fun `SkCPURecorder reports kRaster as its type`() {
        val rec = SkCPURecorder()
        assertEquals(SkRecorder.Type.kRaster, rec.type())
    }

    @Test
    fun `SkRecorder default resourceCache returns null`() {
        val rec = SkCPURecorder()
        assertNull(rec.resourceCache())
    }

    @Test
    fun `subclasses can override resourceCache`() {
        val payload = Any()
        val rec = object : SkRecorder() {
            override fun type(): Type = Type.kRaster
            override fun resourceCache(): Any? = payload
        }
        assertEquals(payload, rec.resourceCache())
    }

    @Test
    fun `Recorder Type enum exposes kRaster and kGpu`() {
        // Exhaustiveness check — both variants must remain present so
        // upstream call sites that pattern-match keep compiling.
        val values = SkRecorder.Type.entries
        assertTrue(values.contains(SkRecorder.Type.kRaster))
        assertTrue(values.contains(SkRecorder.Type.kGpu))
        assertEquals(2, values.size)
    }

    @Test
    fun `multiple recorders are independent instances`() {
        val r1 = SkCPURecorder()
        val r2 = SkCPURecorder()
        assertNotNull(r1)
        assertNotNull(r2)
        assertTrue(r1 !== r2)
    }
}
