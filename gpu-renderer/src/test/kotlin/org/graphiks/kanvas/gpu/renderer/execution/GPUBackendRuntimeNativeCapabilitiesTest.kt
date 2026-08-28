package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUFirstSliceCapabilityName

class GPUBackendRuntimeNativeCapabilitiesTest {
    @Test
    fun `native core primitive capability facts publish bounded linear radial and sweep`() {
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

        val linear = assertNotNull(facts["first_slice.linear_gradient.native"])
        assertEquals("runtime", linear.source)
        assertEquals("supported", linear.value)
        assertEquals(true, linear.affectsValidity)
        assertEquals("core-primitive-gradient-linear-native", linear.evidenceLabel)

        val threeStopStroke = assertNotNull(
            facts[GPUFirstSliceCapabilityName.STROKE_RECT_LINEAR_GRADIENT_THREE_STOP_NATIVE],
        )
        assertEquals("runtime", threeStopStroke.source)
        assertEquals("supported", threeStopStroke.value)
        assertEquals(true, threeStopStroke.affectsValidity)
        assertEquals("core-primitive-gradient-linear-stroke-3stop-native", threeStopStroke.evidenceLabel)

        listOf(
            GPUFirstSliceCapabilityName.STROKE_RECT_LINEAR_GRADIENT_THREE_STOP_TRANSLATE_NATIVE to
                "core-primitive-gradient-linear-stroke-3stop-translate-native",
            GPUFirstSliceCapabilityName.STROKE_RECT_LINEAR_GRADIENT_TRANSLATE_NATIVE to
                "core-primitive-gradient-linear-stroke-translate-native",
            GPUFirstSliceCapabilityName.STROKE_RECT_RADIAL_GRADIENT_TWO_STOP_NATIVE to
                "core-primitive-gradient-radial-stroke-2stop-native",
            GPUFirstSliceCapabilityName.STROKE_RECT_RADIAL_GRADIENT_THREE_STOP_NATIVE to
                "core-primitive-gradient-radial-stroke-3stop-native",
            GPUFirstSliceCapabilityName.STROKE_RECT_SWEEP_GRADIENT_TWO_STOP_NATIVE to
                "core-primitive-gradient-sweep-stroke-2stop-native",
            GPUFirstSliceCapabilityName.STROKE_RECT_SWEEP_GRADIENT_THREE_STOP_NATIVE to
                "core-primitive-gradient-sweep-stroke-3stop-native",
        ).forEach { (name, label) ->
            val fact = assertNotNull(facts[name])
            assertEquals("runtime", fact.source)
            assertEquals("supported", fact.value)
            assertEquals(true, fact.affectsValidity)
            assertEquals(label, fact.evidenceLabel)
        }
    }
}
