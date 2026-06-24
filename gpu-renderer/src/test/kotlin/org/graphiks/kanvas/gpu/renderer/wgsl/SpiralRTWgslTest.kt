package org.graphiks.kanvas.gpu.renderer.wgsl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpiralRTWgslTest {

    @Test
    fun `SpiralRTWgsl contains spiral_rt_source function`() {
        assertTrue(SpiralRTWgsl.contains("fn spiral_rt_source"))
    }

    @Test
    fun `SpiralRTWgsl contains SpiralRTUniform struct`() {
        assertTrue(SpiralRTWgsl.contains("struct SpiralRTUniform"))
    }

    @Test
    fun `SpiralRTWgsl contains center color1 color2 params fields`() {
        assertTrue(SpiralRTWgsl.contains("center"))
        assertTrue(SpiralRTWgsl.contains("color1"))
        assertTrue(SpiralRTWgsl.contains("color2"))
        assertTrue(SpiralRTWgsl.contains("params"))
    }

    @Test
    fun `SpiralRTWgsl contains uniform binding`() {
        assertTrue(SpiralRTWgsl.contains("@group(1) @binding(0) var<uniform>"))
    }

    @Test
    fun `SpiralRTSourceHash is fragment spiral rt v1`() {
        assertEquals("fragment:spiral_rt:v1", SpiralRTSourceHash)
    }

    @Test
    fun `SpiralRTEntryPoint is spiral_rt_source`() {
        assertEquals("spiral_rt_source", SpiralRTEntryPoint)
    }

    @Test
    fun `SpiralRTWgsl returns mix of color1 and color2 based on spiral`() {
        assertTrue(SpiralRTWgsl.contains("mix("))
        assertTrue(SpiralRTWgsl.contains("uSpiralRT.color1"))
        assertTrue(SpiralRTWgsl.contains("uSpiralRT.color2"))
    }

    @Test
    fun `SpiralRTWgsl computes distance and angle`() {
        assertTrue(SpiralRTWgsl.contains("sqrt(dx * dx + dy * dy)"))
        assertTrue(SpiralRTWgsl.contains("atan2(dy, dx)"))
    }
}
