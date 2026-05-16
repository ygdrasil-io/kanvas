package org.skia.tests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.graphiks.math.SkISize
import org.skia.testing.TestUtils

/**
 * Phase 0 smoke test: prove the module compiles, JUnit5 runs, a GM can be
 * instantiated, and the reference image bundle is reachable on the classpath.
 */
class BootstrapTest {

    private class NoopGM : GM() {
        override fun getName(): String = "bootstrap"
        override fun getISize(): SkISize = SkISize.Make(8, 8)
        override fun onDraw(canvas: SkCanvas?) { /* no-op for Phase 0 */ }
    }

    @Test
    fun `GM can be instantiated and exposes name and size`() {
        val gm = NoopGM()
        assertEquals("bootstrap", gm.name())
        assertEquals(SkISize.Make(8, 8), gm.size())
    }

    @Test
    fun `GM draw calls onDraw without throwing`() {
        val gm = NoopGM()
        gm.draw(null)
        gm.draw(null)
    }

    @Test
    fun `reference image bundle is reachable on the classpath`() {
        val image = TestUtils.loadReferenceImage("bigrect")
        assertNotNull(image, "Expected original-888/bigrect.png on the test classpath")
        assertTrue(image!!.width > 0 && image.height > 0)
    }
}
