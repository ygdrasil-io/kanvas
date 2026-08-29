package org.graphiks.kanvas.gpu.renderer.product

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUFirstSliceCapabilityName

class GPUProductFlagCapabilityTruthTest {
    @Test
    fun `product flags do not invent unconditional fill rect support`() {
        val capabilities = GPUProductFlagConfig().buildCapabilities()

        assertFalse(capabilities.facts.any { it.name == "first_slice.fill_rect.native" })
    }

    @Test
    fun `path hairline capability follows path and stroke product flags`() {
        assertTrue(
            GPUProductFlagConfig().buildCapabilities().facts.any {
                it.name == GPUFirstSliceCapabilityName.PATH_HAIRLINE_DIRECT_NATIVE
            },
        )
        assertFalse(
            GPUProductFlagConfig(strokeEnabled = false).buildCapabilities().facts.any {
                it.name == GPUFirstSliceCapabilityName.PATH_HAIRLINE_DIRECT_NATIVE
            },
        )
        assertFalse(
            GPUProductFlagConfig(pathFillEnabled = false).buildCapabilities().facts.any {
                it.name == GPUFirstSliceCapabilityName.PATH_HAIRLINE_DIRECT_NATIVE
            },
        )
    }
}
