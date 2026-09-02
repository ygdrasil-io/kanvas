package org.graphiks.kanvas.render.ir

import kotlin.test.Test
import kotlin.test.assertEquals

class RenderPathFanLimitsTest {
    @Test
    fun `public path fan limits define the complete geometry capacity`() {
        assertEquals(1_024u, RenderPathFanLimits.MAX_TRIANGLES)
        assertEquals(36u, RenderPathFanLimits.BYTES_PER_TRIANGLE)
        assertEquals(
            RenderPathFanLimits.MAX_TRIANGLES * RenderPathFanLimits.BYTES_PER_TRIANGLE,
            RenderPathFanLimits.MAX_GEOMETRY_BYTES,
        )
    }
}
