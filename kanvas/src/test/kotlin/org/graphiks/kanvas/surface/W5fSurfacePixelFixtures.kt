@file:OptIn(ExperimentalUnsignedTypes::class)
package org.graphiks.kanvas.surface

import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal object W5fSurfacePixelFixtures {
    fun requireBounded(expected: WgslFloatEnvelopeV1Oracle.DrawResult) {
        require(expected is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) { expected.toString() }
        require(expected.channels.all { it.size in 1..2 && it.max() - it.min() <= 1 })
    }
    fun assertNativePixels(result: RenderResult, expected: List<WgslFloatEnvelopeV1Oracle.DrawResult>) {
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            result.nativeEvidenceScopeKinds.toString())
        assertEquals(expected.size * 4, result.pixels.size)
        expected.forEachIndexed { index, value ->
            WgslFloatEnvelopeV1Oracle.assertAdmits(value, result.pixels.copyOfRange(index * 4, index * 4 + 4))
        }
    }
}
