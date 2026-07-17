package org.graphiks.kanvas.gpu.renderer.wgsl

import kotlin.test.Test
import kotlin.test.assertContains

class ColorMatrixWgslTest {
    @Test
    fun `color matrix applies four row-major rows and translation`() {
        assertContains(ColorMatrixWgsl, "dot(uniforms.m0, c)")
        assertContains(ColorMatrixWgsl, "dot(uniforms.m1, c)")
        assertContains(ColorMatrixWgsl, "dot(uniforms.m2, c)")
        assertContains(ColorMatrixWgsl, "dot(uniforms.m3, c)")
        assertContains(ColorMatrixWgsl, "+ uniforms.m4")
    }

    @Test
    fun `color matrix clamps then returns premultiplied rgba`() {
        assertContains(ColorMatrixWgsl, "clamp(")
        assertContains(ColorMatrixWgsl, "filtered.rgb * filtered.a")
    }
}
