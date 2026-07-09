package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import kotlin.test.Test
import kotlin.test.assertContains

class GradientWgslShaderProviderTest {
    @Test
    fun `conical gradient wgsl solves positive-radius quadratic root`() {
        val descriptor = GPUMaterialDescriptor.ConicalGradient(
            startX = 0f,
            startY = 0f,
            endX = 0f,
            endY = 0f,
            startRadius = 0f,
            endRadius = 10f,
            startR = 1f,
            startG = 0f,
            startB = 0f,
            startA = 1f,
            endR = 0f,
            endG = 0f,
            endB = 1f,
            endA = 1f,
            allStopPositions = floatArrayOf(0f, 1f),
            allStopColors = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f, 1f, 1f),
        )

        val wgsl = GradientWgslShaderProvider.shaderFor(descriptor)!!.wgslSource

        assertContains(wgsl, "let B = -2.0 * (dx*fx + dy*fy + gradient.r1*dr);")
        assertContains(wgsl, "let t0 = (-B - s) / (2.0 * A);")
        assertContains(wgsl, "let t1 = (-B + s) / (2.0 * A);")
        assertContains(wgsl, "let t0Valid = r0 >= 0.0;")
        assertContains(wgsl, "let t1Valid = r1 >= 0.0;")
    }
}
