package org.graphiks.kanvas.gpu.renderer.compute

import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GpuComputeTessellationTest {

    @Test
    fun `GPU compute tessellation plan produces valid dispatch grid`() {
        val plan = GpuComputeTessellationPlan.forPathFill(
            vertexCount = 256,
            workgroupSize = 64,
        )
        assertEquals(4, plan.dispatchGrid.x)
        assertEquals(1, plan.dispatchGrid.y)
        assertEquals(1, plan.dispatchGrid.z)
        assertTrue { plan.artifactKey.startsWith("compute-tessellation") }
    }

    @Test
    fun `GPU compute tessellation route accepted when compute supported and within budget`() {
        val plan = GpuComputeTessellationPlan.forPathFill(256, 64)
        val route = plan.analyze(capabilities = GpuCapabilities(computeSupported = true))
        assertIs<GpuComputeTessellationRoute.Accepted>(route)
        assertEquals(256, route.artifact.vertexCount)
    }

    @Test
    fun `GPU compute tessellation route capability unavailable when compute unsupported`() {
        val plan = GpuComputeTessellationPlan.forPathFill(256, 64)
        val route = plan.analyze(capabilities = GpuCapabilities(computeSupported = false))
        assertIs<GpuComputeTessellationRoute.CapabilityUnavailable>(route)
        assertEquals("compute_not_supported", route.reason)
    }

    @Test
    fun `GPU compute tessellation route refused when vertex budget exceeded`() {
        val overBudget = GpuComputeTessellationPlan.MAX_VERTEX_BUDGET + 1
        val plan = GpuComputeTessellationPlan.forPathFill(overBudget, 64)
        val route = plan.analyze(capabilities = GpuCapabilities(computeSupported = true))
        assertIs<GpuComputeTessellationRoute.Refused>(route)
        assertEquals("unsupported.tessellation.vertex_budget_exceeded", route.diagnostic.code)
    }

    @Test
    fun `dispatch grid calculation rounds up for partial workgroups`() {
        val plan = GpuComputeTessellationPlan.forPathFill(vertexCount = 65, workgroupSize = 64)
        assertEquals(2, plan.dispatchGrid.x) // ceil(65/64) = 2
    }

    @Test
    fun `plan key is deterministic for same inputs`() {
        val a = GpuComputeTessellationPlan.forPathFill(256, 64)
        val b = GpuComputeTessellationPlan.forPathFill(256, 64)
        assertEquals(a.artifactKey, b.artifactKey)
    }
}
