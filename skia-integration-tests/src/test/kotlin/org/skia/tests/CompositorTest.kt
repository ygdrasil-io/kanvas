package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("ALIAS: partial coverage in CompositorQuadsImageGM; full compositor_quads.cpp matrix TODO")
class CompositorTest {

    @Test
    fun `CompositorGM placeholder`() {
        val gm = CompositorGM()
        TestUtils.runGmTest(gm)
    }
}
