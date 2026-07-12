package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendStencilCoverConfig
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendStencilFillRule
import org.graphiks.kanvas.gpu.renderer.wgsl.reflectWgslModule
import org.graphiks.wgsl.parser.Lowerer
import org.graphiks.wgsl.parser.parseWgslResult

class GPUClipCoverageWgslTest {
    @Test
    fun `stencil WGSL is static and parser validated`() {
        val parsed = parseWgslResult(CLIP_STENCIL_WRITE_WGSL)

        assertTrue(parsed.isSuccess)
        assertTrue(Lowerer().lower(parsed.translationUnit).entryPoints.isNotEmpty())
    }

    @Test
    fun `clip mask composite WGSL is static and parser validated`() {
        val parsed = parseWgslResult(CLIP_MASK_COMPOSITE_WGSL)

        assertTrue(parsed.isSuccess)
        assertTrue(Lowerer().lower(parsed.translationUnit).entryPoints.isNotEmpty())
    }

    @Test
    fun `clip mask cover WGSL is static and parser validated`() {
        val parsed = parseWgslResult(CLIP_MASK_COVER_WGSL)

        assertTrue(parsed.isSuccess)
        assertTrue(Lowerer().lower(parsed.translationUnit).entryPoints.isNotEmpty())
    }

    @Test
    fun `destination blend WGSL parses lowers and reflects all clip routes`() {
        val blend = lower(BLEND_FORMULA_WGSL)
        val clippedBlend = lower(CLIP_BLEND_FORMULA_WGSL)
        val coverageBlend = lower(CLIP_COVERAGE_BLEND_WGSL)
        val scissorBlend = lower(SCISSOR_CLIP_BLEND_FORMULA_WGSL)

        assertEquals(2, blend.reflectWgslModule("destination-blend").bindings.count { it.resourceKind == "sampledTexture" })
        assertEquals(3, clippedBlend.reflectWgslModule("destination-clip-blend").bindings.count { it.resourceKind == "sampledTexture" })
        assertEquals(3, coverageBlend.reflectWgslModule("coverage-clip-blend").bindings.count { it.resourceKind == "sampledTexture" })
        assertEquals(2, scissorBlend.reflectWgslModule("destination-scissor-blend").bindings.count { it.resourceKind == "sampledTexture" })
        assertTrue(CLIP_COVERAGE_BLEND_WGSL.contains("dst + clipAlpha * (blended - dst)"))
    }

    @Test
    fun `non AA shape shaders evaluate hard geometry rather than filling a conservative scissor`() {
        assertTrue(RECT_AA_WGSL.contains("let hardCov"))
        assertTrue(RRECT_WGSL.contains("let hardCov"))
    }

    @Test
    fun `even odd hole and inverse cover use their own stencil states`() {
        assertEquals(
            GPUBackendStencilCoverConfig(GPUBackendStencilFillRule.EvenOdd, inverse = false),
            stencilConfig(FillType.EVEN_ODD),
        )
        assertEquals(
            GPUBackendStencilCoverConfig(GPUBackendStencilFillRule.EvenOdd, inverse = true),
            stencilConfig(FillType.INVERSE_EVEN_ODD),
        )
    }

    private fun lower(wgsl: String) = parseWgslResult(wgsl).let { parsed ->
        assertTrue(parsed.isSuccess)
        Lowerer().lower(parsed.translationUnit).also { lowered ->
            assertTrue(lowered.entryPoints.isNotEmpty())
        }
    }
}
