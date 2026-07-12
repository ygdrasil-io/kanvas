package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendStencilFillRule
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
    fun `even odd hole and inverse cover use their own stencil states`() {
        assertEquals(GPUBackendStencilFillRule.EvenOdd, stencilConfig(FillType.EVEN_ODD).fillRule)
        assertTrue(stencilConfig(FillType.INVERSE_WINDING).inverse)
    }
}
