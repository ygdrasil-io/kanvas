package org.graphiks.kanvas.gpu.renderer.compute

import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic
import kotlin.math.abs
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
        assertTrue { plan.pipelineKey.value.startsWith("compute-tessellation-fill") }
    }

    @Test
    fun `GPU compute tessellation plan forPathStroke produces valid dispatch grid`() {
        val plan = GpuComputeTessellationPlan.forPathStroke(
            vertexCount = 128,
            workgroupSize = 64,
        )
        assertEquals(2, plan.dispatchGrid.x)
        assertTrue { plan.pipelineKey.value.startsWith("compute-tessellation-stroke") }
    }

    @Test
    fun `forPathFill and forPathStroke produce distinct pipeline keys for same inputs`() {
        val fill = GpuComputeTessellationPlan.forPathFill(256, 64)
        val stroke = GpuComputeTessellationPlan.forPathStroke(256, 64)
        assertTrue { fill.pipelineKey.value != stroke.pipelineKey.value }
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
        assertEquals("unsupported.tessellation.compute_capability_absent", route.reason)
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
        assertEquals(2, plan.dispatchGrid.x)
    }

    @Test
    fun `plan key is deterministic for same inputs`() {
        val a = GpuComputeTessellationPlan.forPathFill(256, 64)
        val b = GpuComputeTessellationPlan.forPathFill(256, 64)
        assertEquals(a.pipelineKey.value, b.pipelineKey.value)
    }

    @Test
    fun `WGSL source loads and is non-empty`() {
        val plan = GpuComputeTessellationPlan.forPathFill(256, 64)
        val source = plan.wgslSource()
        assertTrue { source.isNotBlank() }
        assertTrue { source.contains("compute_main") }
        assertTrue { source.contains("VertexOutput") }
    }

    @Test
    fun `WGSL source uses positional constructors not named fields`() {
        val plan = GpuComputeTessellationPlan.forPathFill(256, 64)
        val source = plan.wgslSource()
        assertTrue { source.contains("VertexOutput(pos, 1.0)") }
        assertTrue { !source.contains("(position:") }
    }

    @Test
    fun `WGSL source has no unused VertexInput struct`() {
        val plan = GpuComputeTessellationPlan.forPathFill(256, 64)
        val source = plan.wgslSource()
        assertTrue { !source.contains("VertexInput") }
    }

    @Test
    fun `valid WGSL passes wgsl4k validation`() {
        val plan = GpuComputeTessellationPlan.forPathFill(256, 64)
        val errors = GpuComputeTessellationPlan.validateWgsl(plan.wgslSource())
        assertTrue { errors.isEmpty() }
    }

    @Test
    fun `invalid WGSL is refused with wgsl_validation code`() {
        val badWgsl = "struct Bad { @frag ~~~~ }"
        val errors = GpuComputeTessellationPlan.validateWgsl(badWgsl)
        assertTrue { errors.isNotEmpty() }
    }

    @Test
    fun `refusal diagnostic uses correct terminal flag`() {
        val overBudget = GpuComputeTessellationPlan.MAX_VERTEX_BUDGET + 1
        val plan = GpuComputeTessellationPlan.forPathFill(overBudget, 64)
        val route = plan.analyze(capabilities = GpuCapabilities(computeSupported = true))
        assertIs<GpuComputeTessellationRoute.Refused>(route)
        assertEquals(true, route.diagnostic.terminal)
    }

    @Test
    fun `CPU oracle generates circle vertices of requested count`() {
        val n = 8
        val vertices = GpuComputeTessellationPlan.CpuOracle.circleVertices(n, radius = 2.0f)
        assertEquals(n, vertices.size)
    }

    @Test
    fun `CPU oracle circle vertices lie on expected radius`() {
        val vertices = GpuComputeTessellationPlan.CpuOracle.circleVertices(64, radius = 3.0f)
        vertices.forEach { (x, y) ->
            val r = kotlin.math.sqrt(x * x + y * y)
            assertTrue { abs(r - 3.0f) < 0.01f }
        }
    }

    @Test
    fun `CPU oracle circle first vertex is at angle 0 on positive x-axis`() {
        val vertices = GpuComputeTessellationPlan.CpuOracle.circleVertices(4, radius = 5.0f)
        val first = vertices[0]
        assertEquals(5.0f, first.first)
        assertTrue { abs(first.second - 0.0f) < 0.01f }
    }

    @Test
    fun `CPU oracle computeTessellation mirrors WGSL shader logic`() {
        val input = listOf(Pair(1.0f, 2.0f), Pair(3.0f, 4.0f), Pair(5.0f, 6.0f))
        val output = GpuComputeTessellationPlan.CpuOracle.computeTessellation(input)
        assertEquals(3, output.size)
        assertEquals(Pair(1.0f, 2.0f), output[0].position)
        assertEquals(1.0f, output[0].coverage)
        assertEquals(Pair(5.0f, 6.0f), output[2].position)
    }

    @Test
    fun `CPU oracle computeTessellation coverage is always 1 for all vertices`() {
        val vertices = GpuComputeTessellationPlan.CpuOracle.circleVertices(16)
        val output = GpuComputeTessellationPlan.CpuOracle.computeTessellation(vertices)
        output.forEach { output ->
            assertEquals(1.0f, output.coverage)
        }
    }

    @Test
    fun `CPU oracle squareVertices produces 4 axis-aligned corners`() {
        val square = GpuComputeTessellationPlan.CpuOracle.squareVertices()
        assertEquals(4, square.size)
        assertEquals(Pair(-1f, -1f), square[0])
        assertEquals(Pair(1f, 1f), square[2])
    }

    @Test
    fun `wgslModule is populated with module hash and entry point`() {
        val plan = GpuComputeTessellationPlan.forPathFill(256, 64)
        assertEquals("compute_main", plan.wgslModule.entryPoint)
        assertTrue { plan.wgslModule.moduleHash.value.startsWith("compute-tessellation:") }
    }

    @Test
    fun `wgslModule bindings include vertices and outputs`() {
        val plan = GpuComputeTessellationPlan.forPathFill(256, 64)
        val bindingRoles = plan.wgslModule.bindings.map { it.layoutRole }.toSet()
        assertTrue { bindingRoles.contains("vertices") }
        assertTrue { bindingRoles.contains("outputs") }
    }

    @Test
    fun `forPathStroke produces accepted route for supported compute`() {
        val plan = GpuComputeTessellationPlan.forPathStroke(128, 64)
        val route = plan.analyze(capabilities = GpuCapabilities(computeSupported = true))
        assertIs<GpuComputeTessellationRoute.Accepted>(route)
    }

    @Test
    fun `forPathStroke produces CapabilityUnavailable for unsupported compute`() {
        val plan = GpuComputeTessellationPlan.forPathStroke(128, 64)
        val route = plan.analyze(capabilities = GpuCapabilities(computeSupported = false))
        assertIs<GpuComputeTessellationRoute.CapabilityUnavailable>(route)
        assertEquals("unsupported.tessellation.compute_capability_absent", route.reason)
    }

    @Test
    fun `artifact descriptor is deterministic`() {
        val key = org.graphiks.kanvas.gpu.renderer.routing.CPUPreparedGPUArtifactKey("test-key")
        val a = GpuComputeTessellationArtifact.descriptor(key)
        val b = GpuComputeTessellationArtifact.descriptor(key)
        assertEquals(a.descriptorHash, b.descriptorHash)
        assertEquals("GpuComputeTessellationArtifact", a.artifactType)
    }
}
