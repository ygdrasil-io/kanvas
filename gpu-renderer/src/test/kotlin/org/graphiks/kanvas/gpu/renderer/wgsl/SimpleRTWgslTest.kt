package org.graphiks.kanvas.gpu.renderer.wgsl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimpleRTWgslTest {

    @Test
    fun `SimpleRTWgsl contains simple_rt_source function`() {
        assertTrue(SimpleRTWgsl.contains("fn simple_rt_source"))
    }

    @Test
    fun `SimpleRTWgsl contains SimpleRTUniform struct`() {
        assertTrue(SimpleRTWgsl.contains("struct SimpleRTUniform"))
    }

    @Test
    fun `SimpleRTWgsl contains gColor field`() {
        assertTrue(SimpleRTWgsl.contains("gColor"))
    }

    @Test
    fun `SimpleRTWgsl contains uniform binding`() {
        assertTrue(SimpleRTWgsl.contains("@group(1) @binding(0) var<uniform>"))
    }

    @Test
    fun `SimpleRTSourceHash is fragment simple rt v1`() {
        assertEquals("fragment:simple_rt:v1", SimpleRTSourceHash)
    }

    @Test
    fun `SimpleRTEntryPoint is simple_rt_source`() {
        assertEquals("simple_rt_source", SimpleRTEntryPoint)
    }
}
