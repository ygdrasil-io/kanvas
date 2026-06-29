package org.graphiks.kanvas.gpu.renderer.compute

import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GPUComputeTessellationTest {

    @Test
    fun `GPU compute tessellation plan produces valid dispatch grid`() {
        val plan = GPUComputeTessellationPlan.forPathFill(
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
        val plan = GPUComputeTessellationPlan.forPathStroke(
            vertexCount = 128,
            workgroupSize = 64,
        )
        assertEquals(2, plan.dispatchGrid.x)
        assertTrue { plan.pipelineKey.value.startsWith("compute-tessellation-stroke") }
    }

    @Test
    fun `forPathFill and forPathStroke produce distinct pipeline keys for same inputs`() {
        val fill = GPUComputeTessellationPlan.forPathFill(256, 64)
        val stroke = GPUComputeTessellationPlan.forPathStroke(256, 64)
        assertTrue { fill.pipelineKey.value != stroke.pipelineKey.value }
    }

    @Test
    fun `GPU compute tessellation route accepted when compute supported and within budget`() {
        val plan = GPUComputeTessellationPlan.forPathFill(256, 64)
        val route = plan.analyze(capabilities = GPUCapabilities(computeSupported = true))
        assertIs<GPUComputeTessellationRoute.Accepted>(route)
        assertEquals(256, route.artifact.vertexCount)
    }

    @Test
    fun `GPU compute tessellation route capability unavailable when compute unsupported`() {
        val plan = GPUComputeTessellationPlan.forPathFill(256, 64)
        val route = plan.analyze(capabilities = GPUCapabilities(computeSupported = false))
        assertIs<GPUComputeTessellationRoute.CapabilityUnavailable>(route)
        assertEquals("unsupported.tessellation.compute_capability_absent", route.reason)
    }

    @Test
    fun `GPU compute tessellation route refused when vertex budget exceeded`() {
        val overBudget = GPUComputeTessellationPlan.MAX_VERTEX_BUDGET + 1
        val plan = GPUComputeTessellationPlan.forPathFill(overBudget, 64)
        val route = plan.analyze(capabilities = GPUCapabilities(computeSupported = true))
        assertIs<GPUComputeTessellationRoute.Refused>(route)
        assertEquals("unsupported.tessellation.vertex_budget_exceeded", route.diagnostic.code)
    }

    @Test
    fun `dispatch grid calculation rounds up for partial workgroups`() {
        val plan = GPUComputeTessellationPlan.forPathFill(vertexCount = 65, workgroupSize = 64)
        assertEquals(2, plan.dispatchGrid.x)
    }

    @Test
    fun `plan key is deterministic for same inputs`() {
        val a = GPUComputeTessellationPlan.forPathFill(256, 64)
        val b = GPUComputeTessellationPlan.forPathFill(256, 64)
        assertEquals(a.pipelineKey.value, b.pipelineKey.value)
    }

    @Test
    fun `WGSL source loads and is non-empty`() {
        val plan = GPUComputeTessellationPlan.forPathFill(256, 64)
        val source = plan.wgslSource()
        assertTrue { source.isNotBlank() }
        assertTrue { source.contains("compute_main") }
        assertTrue { source.contains("VertexOutput") }
    }

    @Test
    fun `WGSL source uses positional constructors not named fields`() {
        val plan = GPUComputeTessellationPlan.forPathFill(256, 64)
        val source = plan.wgslSource()
        assertTrue { source.contains("VertexOutput(pos, 1.0)") }
        assertTrue { !source.contains("(position:") }
    }

    @Test
    fun `WGSL source has no unused VertexInput struct`() {
        val plan = GPUComputeTessellationPlan.forPathFill(256, 64)
        val source = plan.wgslSource()
        assertTrue { !source.contains("VertexInput") }
    }

    @Test
    fun `valid WGSL passes wgsl4k validation`() {
        val plan = GPUComputeTessellationPlan.forPathFill(256, 64)
        val errors = GPUComputeTessellationPlan.validateWgsl(plan.wgslSource())
        assertTrue { errors.isEmpty() }
    }

    @Test
    fun `invalid WGSL is refused with wgsl_validation code`() {
        val badWgsl = "struct Bad { @frag ~~~~ }"
        val errors = GPUComputeTessellationPlan.validateWgsl(badWgsl)
        assertTrue { errors.isNotEmpty() }
    }

    @Test
    fun `refusal diagnostic uses correct terminal flag`() {
        val overBudget = GPUComputeTessellationPlan.MAX_VERTEX_BUDGET + 1
        val plan = GPUComputeTessellationPlan.forPathFill(overBudget, 64)
        val route = plan.analyze(capabilities = GPUCapabilities(computeSupported = true))
        assertIs<GPUComputeTessellationRoute.Refused>(route)
        assertEquals(true, route.diagnostic.terminal)
    }

    @Test
    fun `CPU oracle generates circle vertices of requested count`() {
        val n = 8
        val vertices = GPUComputeTessellationPlan.CpuOracle.circleVertices(n, radius = 2.0f)
        assertEquals(n, vertices.size)
    }

    @Test
    fun `CPU oracle circle vertices lie on expected radius`() {
        val vertices = GPUComputeTessellationPlan.CpuOracle.circleVertices(64, radius = 3.0f)
        vertices.forEach { (x, y) ->
            val r = kotlin.math.sqrt(x * x + y * y)
            assertTrue { abs(r - 3.0f) < 0.01f }
        }
    }

    @Test
    fun `CPU oracle circle first vertex is at angle 0 on positive x-axis`() {
        val vertices = GPUComputeTessellationPlan.CpuOracle.circleVertices(4, radius = 5.0f)
        val first = vertices[0]
        assertEquals(5.0f, first.first)
        assertTrue { abs(first.second - 0.0f) < 0.01f }
    }

    @Test
    fun `CPU oracle computeTessellation mirrors WGSL shader logic`() {
        val input = listOf(Pair(1.0f, 2.0f), Pair(3.0f, 4.0f), Pair(5.0f, 6.0f))
        val output = GPUComputeTessellationPlan.CpuOracle.computeTessellation(input)
        assertEquals(3, output.size)
        assertEquals(Pair(1.0f, 2.0f), output[0].position)
        assertEquals(1.0f, output[0].coverage)
        assertEquals(Pair(5.0f, 6.0f), output[2].position)
    }

    @Test
    fun `CPU oracle computeTessellation coverage is always 1 for all vertices`() {
        val vertices = GPUComputeTessellationPlan.CpuOracle.circleVertices(16)
        val output = GPUComputeTessellationPlan.CpuOracle.computeTessellation(vertices)
        output.forEach { output ->
            assertEquals(1.0f, output.coverage)
        }
    }

    @Test
    fun `CPU oracle squareVertices produces 4 axis-aligned corners`() {
        val square = GPUComputeTessellationPlan.CpuOracle.squareVertices()
        assertEquals(4, square.size)
        assertEquals(Pair(-1f, -1f), square[0])
        assertEquals(Pair(1f, 1f), square[2])
    }

    @Test
    fun `wgslModule is populated with module hash and entry point`() {
        val plan = GPUComputeTessellationPlan.forPathFill(256, 64)
        assertEquals("compute_main", plan.wgslModule.entryPoint)
        assertTrue { plan.wgslModule.moduleHash.value.startsWith("compute-tessellation:") }
    }

    @Test
    fun `wgslModule bindings include vertices and outputs`() {
        val plan = GPUComputeTessellationPlan.forPathFill(256, 64)
        val bindingRoles = plan.wgslModule.bindings.map { it.layoutRole }.toSet()
        assertTrue { bindingRoles.contains("vertices") }
        assertTrue { bindingRoles.contains("outputs") }
    }

    @Test
    fun `forPathStroke produces accepted route for supported compute`() {
        val plan = GPUComputeTessellationPlan.forPathStroke(128, 64)
        val route = plan.analyze(capabilities = GPUCapabilities(computeSupported = true))
        assertIs<GPUComputeTessellationRoute.Accepted>(route)
    }

    @Test
    fun `forPathStroke produces CapabilityUnavailable for unsupported compute`() {
        val plan = GPUComputeTessellationPlan.forPathStroke(128, 64)
        val route = plan.analyze(capabilities = GPUCapabilities(computeSupported = false))
        assertIs<GPUComputeTessellationRoute.CapabilityUnavailable>(route)
        assertEquals("unsupported.tessellation.compute_capability_absent", route.reason)
    }

    @Test
    fun `artifact descriptor is deterministic`() {
        val key = org.graphiks.kanvas.gpu.renderer.routing.CPUPreparedGPUArtifactKey("test-key")
        val a = GPUComputeTessellationArtifact.descriptor(key)
        val b = GPUComputeTessellationArtifact.descriptor(key)
        assertEquals(a.descriptorHash, b.descriptorHash)
        assertEquals("GPUComputeTessellationArtifact", a.artifactType)
    }

    @Test
    fun `reflectComputeModule resolves a compute entry point`() {
        val plan = GPUComputeTessellationPlan.forPathFill(256, 64)
        val report = GPUComputeTessellationPlan.reflectComputeModule(plan.wgslSource())
        assertTrue { report != null }
        assertEquals("compute", report!!.entryPoints.firstOrNull()?.stage)
        assertEquals("compute_main", report.entryPoints.first().name)
    }

    @Test
    fun `reflectComputeModule reports storage buffer bindings`() {
        val plan = GPUComputeTessellationPlan.forPathFill(256, 64)
        val report = GPUComputeTessellationPlan.reflectComputeModule(plan.wgslSource())
        assertTrue { report != null }
        val storageBindings = report!!.bindings.filter { it.resourceKind == "storageBuffer" }
        assertEquals(2, storageBindings.size)
    }

    @Test
    fun `reflectComputeModule discriminates non-compute stage`() {
        val vertexShader = """
            @vertex
            fn vs_main() -> @builtin(position) vec4<f32> {
                return vec4<f32>(0.0, 0.0, 0.0, 1.0);
            }
        """.trimIndent()
        val report = GPUComputeTessellationPlan.reflectComputeModule(vertexShader)
        assertTrue { report != null }
        assertEquals("vertex", report!!.entryPoints.firstOrNull()?.stage)
    }

    @Test
    fun `override workgroup size is reported as unresolved by wgsl4k`() {
        val plan = GPUComputeTessellationPlan.forPathFill(256, 64)
        val report = GPUComputeTessellationPlan.reflectComputeModule(plan.wgslSource())
        assertTrue { report != null }
        val workgroupSize = report!!.entryPoints.first().workgroupSize
        assertTrue { workgroupSize == null || workgroupSize.all { it == 1 } }
    }

    @Test
    fun `reflection-validated route accepts the real compute tessellation shader`() {
        val plan = GPUComputeTessellationPlan.forPathFill(256, 64)
        val route = plan.analyze(capabilities = GPUCapabilities(computeSupported = true))
        assertIs<GPUComputeTessellationRoute.Accepted>(route)
    }
}
