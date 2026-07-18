package org.graphiks.kanvas.gpu.renderer.product

import kotlin.test.Test
import kotlin.test.assertFalse

class GPUProductFlagCapabilityTruthTest {
    @Test
    fun `product flags do not invent unconditional fill rect support`() {
        val capabilities = GPUProductFlagConfig().buildCapabilities()

        assertFalse(capabilities.facts.any { it.name == "first_slice.fill_rect.native" })
    }
}
