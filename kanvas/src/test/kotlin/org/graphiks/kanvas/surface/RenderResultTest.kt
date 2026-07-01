package org.graphiks.kanvas.surface

import org.graphiks.kanvas.types.ColorSpace
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
class RenderResultTest {
    @Test fun `clean result`() { val r = RenderResult(UByteArray(4){255u}, 2, 2, PixelFormat.RGBA8, ColorSpace.SRGB, Diagnostics(), RenderStats(1,0,1,1,1f)); assertTrue(r.isClean) }
    @Test fun `assertClean throws on fatal`() { val d = Diagnostics(); d.fatal("c","o","r"); try { RenderResult(UByteArray(4),2,2,PixelFormat.RGBA8, ColorSpace.SRGB, d, RenderStats(0,1,0,0,0f)).assertClean(); fail() } catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("FATAL=1")) } }
}
