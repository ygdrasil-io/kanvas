package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * R3.9 stub-coverage tests for [SkCPUContext] — verifies that
 * [SkCPUContext.Make] yields a context and that [SkCPUContext.makeRecorder]
 * hands out raster recorders.
 */
class SkCPUContextTest {

    @Test
    fun `Make returns a non-null context`() {
        val ctx = SkCPUContext.Make()
        assertNotNull(ctx)
    }

    @Test
    fun `makeRecorder returns a CPU recorder reporting kRaster`() {
        val ctx = SkCPUContext.Make()
        val rec = ctx.makeRecorder()
        assertEquals(SkRecorder.Type.kRaster, rec.type())
    }

    @Test
    fun `makeRecorder hands out a fresh recorder on every call`() {
        val ctx = SkCPUContext.Make()
        val r1 = ctx.makeRecorder()
        val r2 = ctx.makeRecorder()
        assertTrue(r1 !== r2, "Each makeRecorder() call should return a new SkCPURecorder")
    }

    @Test
    fun `successive Make calls return independent contexts`() {
        val c1 = SkCPUContext.Make()
        val c2 = SkCPUContext.Make()
        assertTrue(c1 !== c2)
    }
}
