package org.graphiks.kanvas.gpu.renderer.product

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProductFlagConfigTest {

    @Test
    fun `default config has all m13 flags enabled`() {
        val config = GpuProductFlagConfig()

        assertTrue(config.fillRRectEnabled)
        assertTrue(config.linearGradientEnabled)
        assertTrue(config.scissorEnabled)
    }

    @Test
    fun `default config builds capabilities with all m13 facts`() {
        val config = GpuProductFlagConfig()
        val capabilities = config.buildCapabilities()

        val factsByName = capabilities.facts.associateBy { it.name }
        assertTrue(factsByName.containsKey("first_slice.fill_rrect.native"))
        assertTrue(factsByName.containsKey("first_slice.linear_gradient.native"))
        assertTrue(factsByName.containsKey("first_slice.scissor.native"))
        assertEquals("product-flags", capabilities.snapshotId)
    }

    @Test
    fun `disable property overrides fill rrect flag`() {
        val config = GpuProductFlagConfig.fromSystemProperties(
            propertyReader = { key ->
                when (key) {
                    GpuProductFlagConfig.FillRRectDisableProperty -> "true"
                    else -> null
                }
            },
        )

        assertFalse(config.fillRRectEnabled)
        assertTrue(config.linearGradientEnabled)
        assertTrue(config.scissorEnabled)
    }

    @Test
    fun `disable property overrides linear gradient flag`() {
        val config = GpuProductFlagConfig.fromSystemProperties(
            propertyReader = { key ->
                when (key) {
                    GpuProductFlagConfig.LinearGradientDisableProperty -> "true"
                    else -> null
                }
            },
        )

        assertTrue(config.fillRRectEnabled)
        assertFalse(config.linearGradientEnabled)
        assertTrue(config.scissorEnabled)
    }

    @Test
    fun `disable property overrides scissor flag`() {
        val config = GpuProductFlagConfig.fromSystemProperties(
            propertyReader = { key ->
                when (key) {
                    GpuProductFlagConfig.ScissorDisableProperty -> "true"
                    else -> null
                }
            },
        )

        assertTrue(config.fillRRectEnabled)
        assertTrue(config.linearGradientEnabled)
        assertFalse(config.scissorEnabled)
    }

    @Test
    fun `disabled flag does not produce capability fact`() {
        val config = GpuProductFlagConfig(fillRRectEnabled = false)
        val capabilities = config.buildCapabilities()

        val factNames = capabilities.facts.map { it.name }
        assertFalse("first_slice.fill_rrect.native" in factNames)
        assertTrue("first_slice.linear_gradient.native" in factNames)
        assertTrue("first_slice.scissor.native" in factNames)
    }

    @Test
    fun `empty property reader leaves all flags enabled`() {
        val config = GpuProductFlagConfig.fromSystemProperties(
            propertyReader = { null },
        )

        assertTrue(config.fillRRectEnabled)
        assertTrue(config.linearGradientEnabled)
        assertTrue(config.scissorEnabled)
    }
}
