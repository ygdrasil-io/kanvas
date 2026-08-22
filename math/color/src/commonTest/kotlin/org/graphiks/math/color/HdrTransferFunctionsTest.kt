package org.graphiks.math.color

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HdrTransferFunctionsTest {
    @Test
    fun `normalized HDR kernels round trip`() {
        listOf(0.0, 0.009224571, 0.09833779, 1.0).forEach { linear ->
            assertEquals(linear, pqEotf(pqInverseEotf(linear)), 2e-12)
        }
        listOf(0.0, 1.0 / 12.0, 1.0).forEach { scene ->
            assertEquals(scene, hlgInverseOetf(hlgOetf(scene)), 2e-12)
        }
    }

    @Test
    fun `HDR kernels reject values outside their mathematical domains`() {
        assertFailsWith<IllegalArgumentException> { pqEotf(-0.001) }
        assertFailsWith<IllegalArgumentException> { pqInverseEotf(1.001) }
        assertFailsWith<IllegalArgumentException> { hlgInverseOetf(Double.NaN) }
        assertFailsWith<IllegalArgumentException> { hlgOetf(-0.001) }
        assertFailsWith<IllegalArgumentException> { hlgOetf(Double.POSITIVE_INFINITY) }
    }

    @Test
    fun `HLG OETF accepts scene linear values above normalized white`() {
        assertEquals(1.4156464012943346, hlgOetf(10.0), 2e-12)
    }
}
