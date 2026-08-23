package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class GPUBackendRuntimeNativeCapabilitiesTest {
    @Test
    fun `native core primitive capability facts publish bounded radial and sweep only`() {
        val facts = nativeCorePrimitiveCapabilityFacts().associateBy { it.name }

        val radial = assertNotNull(facts["first_slice.radial_gradient.native"])
        assertEquals("runtime", radial.source)
        assertEquals("supported", radial.value)
        assertEquals(true, radial.affectsValidity)
        assertEquals("core-primitive-gradient-radial-native", radial.evidenceLabel)

        val sweep = assertNotNull(facts["first_slice.sweep_gradient.native"])
        assertEquals("runtime", sweep.source)
        assertEquals("supported", sweep.value)
        assertEquals(true, sweep.affectsValidity)
        assertEquals("core-primitive-gradient-sweep-native", sweep.evidenceLabel)

        assertFalse(facts.containsKey("first_slice.linear_gradient.native"))
    }
}
