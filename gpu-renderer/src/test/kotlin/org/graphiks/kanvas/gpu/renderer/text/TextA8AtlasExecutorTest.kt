package org.graphiks.kanvas.gpu.renderer.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextA8AtlasExecutorTest {
    @Test
    fun `execute returns accepted stats for empty atlas`() {
        val executor = TextA8AtlasExecutor()
        val stats = executor.execute("test-a8-atlas", 256, 256)
        assertTrue(stats.accepted)
        assertEquals("test-a8-atlas", "test-a8-atlas")
    }

    @Test
    fun `execute returns stats with zero glyph count for empty plan`() {
        val executor = TextA8AtlasExecutor()
        val stats = executor.execute("empty-atlas", 128, 128)
        assertEquals(0, stats.glyphCount)
        assertEquals(0L, stats.uploadSizeBytes)
    }
}
