package org.graphiks.kanvas.gpu.renderer.wgsl

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.color.GPUColorWgslValidation
import org.graphiks.kanvas.gpu.renderer.color.validateColorWgsl
import org.graphiks.wgsl.parser.parseWgslResult

class ColorMatrixWgslTest {
    @Test
    fun `color matrix applies four row-major linear rows and translation`() {
        assertContains(ColorMatrixWgsl, "dot(uniforms.m0, linear)")
        assertContains(ColorMatrixWgsl, "dot(uniforms.m1, linear)")
        assertContains(ColorMatrixWgsl, "dot(uniforms.m2, linear)")
        assertContains(ColorMatrixWgsl, "dot(uniforms.m3, linear)")
        assertContains(ColorMatrixWgsl, "+ uniforms.m4")
    }

    @Test
    fun `color matrix explicitly converts sRGB then returns premultiplied rgba`() {
        assertContains(ColorMatrixWgsl, "fn srgb_to_linear")
        assertContains(ColorMatrixWgsl, "fn linear_to_srgb")
        assertContains(ColorMatrixWgsl, "srgb_to_linear(c.r)")
        assertContains(ColorMatrixWgsl, "linear_to_srgb(filtered.r)")
        assertContains(ColorMatrixWgsl, "clamp(")
        assertContains(ColorMatrixWgsl, "encoded * filtered.a")
    }

    @Test
    fun `color matrix WGSL parses and reflects through wgsl4k`() {
        val result = validateColorWgsl("srgb-colorfilter-matrix-v1", ColorMatrixWgsl)

        val validated = assertIs<GPUColorWgslValidation.Validated>(result)
        val reflection = requireNotNull(validated.reflection)
        assertTrue(reflection.validated)
        assertTrue(reflection.report.entryPoints.any { it.name == "fs_main" })
        val uniforms = reflection.report.layouts.single { it.structName == "ColorMatrixUniforms" }
        assertEquals("uniform", uniforms.addressSpace)
        assertEquals(96, uniforms.size)
        assertEquals(
            listOf(
                "color" to (0 to 16),
                "m0" to (16 to 16),
                "m1" to (32 to 16),
                "m2" to (48 to 16),
                "m3" to (64 to 16),
                "m4" to (80 to 16),
            ),
            uniforms.members.map { it.name to (it.offset to it.size) },
        )
        val parsed = parseWgslResult(ColorMatrixWgsl)
        assertTrue(parsed.isSuccess, "wgsl4k rejected ColorMatrixWgsl: ${parsed.errors.joinToString { it.message }}")
    }
}
