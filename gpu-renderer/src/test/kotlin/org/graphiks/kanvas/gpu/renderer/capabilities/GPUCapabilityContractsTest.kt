package org.graphiks.kanvas.gpu.renderer.capabilities

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GPUCapabilityContractsTest {
    @Test
    fun `GPU limits validate positive values and nonblank source`() {
        val limits = GPULimits(
            maxTextureDimension2D = 8192L,
            copyBytesPerRowAlignment = 256L,
            minUniformBufferOffsetAlignment = 256L,
            source = "device.limits",
        )

        assertEquals(8192L, limits.maxTextureDimension2D)
        assertEquals(256L, limits.copyBytesPerRowAlignment)
        assertEquals(256L, limits.minUniformBufferOffsetAlignment)
        assertEquals("device.limits", limits.source)
        assertFailsWith<IllegalArgumentException> {
            GPULimits(
                maxTextureDimension2D = 0L,
                copyBytesPerRowAlignment = 256L,
                minUniformBufferOffsetAlignment = 256L,
                source = "device.limits",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPULimits(
                maxTextureDimension2D = 8192L,
                copyBytesPerRowAlignment = 0L,
                minUniformBufferOffsetAlignment = 256L,
                source = "device.limits",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPULimits(
                maxTextureDimension2D = 8192L,
                copyBytesPerRowAlignment = 256L,
                minUniformBufferOffsetAlignment = 0L,
                source = "device.limits",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPULimits(
                maxTextureDimension2D = 8192L,
                copyBytesPerRowAlignment = 256L,
                minUniformBufferOffsetAlignment = 256L,
                source = "",
            )
        }
    }

    @Test
    fun `GPU limits expose stable capability facts`() {
        val facts = GPULimits(
            maxTextureDimension2D = 8192L,
            copyBytesPerRowAlignment = 256L,
            minUniformBufferOffsetAlignment = 256L,
            source = "runtime.conservative",
        ).capabilityFacts(evidenceLabel = "runtime")

        assertEquals(
            listOf(
                "maxTextureDimension2D",
                "copyBytesPerRowAlignment",
                "minUniformBufferOffsetAlignment",
            ),
            facts.map { it.name },
        )
        assertEquals(listOf("8192", "256", "256"), facts.map { it.value })
        assertEquals(setOf("runtime.conservative"), facts.map { it.source }.toSet())
        assertTrue(facts.all { it.affectsValidity })
        assertTrue(facts.all { it.evidenceLabel == "runtime" })
        assertTrue(!facts.joinToString("\n").contains("@"))
    }

    @Test
    fun `GPU capabilities can carry limits without forcing existing facts`() {
        val limits = GPULimits.conservative(
            maxTextureDimension2D = 8192L,
            copyBytesPerRowAlignment = 256L,
            minUniformBufferOffsetAlignment = 256L,
        )
        val capabilities = GPUCapabilities(
            implementation = GPUImplementationIdentity(
                facadeName = "GPU",
                implementationName = "unit",
                adapterName = "unit-adapter",
                deviceName = "unit-device",
            ),
            facts = emptyList(),
            snapshotId = "unit-snapshot",
            limits = limits,
        )

        assertEquals(limits, capabilities.limits)
        assertEquals(emptyList(), capabilities.facts)
        assertEquals("runtime.conservative", capabilities.limits?.source)
    }
}
