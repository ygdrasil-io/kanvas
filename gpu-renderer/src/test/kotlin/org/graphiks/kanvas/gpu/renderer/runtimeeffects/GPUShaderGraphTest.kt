package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class GPUShaderGraphTest {

    private fun generousBudget(): GPURuntimeEffectShaderGraphBudget =
        GPURuntimeEffectShaderGraphBudget(
            maxDepth = 32,
            maxChildrenPerNode = 16,
            maxWgslInstructions = 100_000,
            maxUniformBufferBytes = 1_000_000,
        )

    private fun assembled(result: GPURuntimeEffectShaderGraphResult): GPURuntimeEffectShaderGraphAssemblyPlan =
        when (result) {
            is GPURuntimeEffectShaderGraphResult.Assembled -> result.plan
            is GPURuntimeEffectShaderGraphResult.Refused ->
                fail("expected Assembled but was refused: ${result.diagnostic.code}")
        }

    private fun refused(result: GPURuntimeEffectShaderGraphResult): String =
        when (result) {
            is GPURuntimeEffectShaderGraphResult.Refused -> result.diagnostic.code
            is GPURuntimeEffectShaderGraphResult.Assembled ->
                fail("expected Refused but was assembled")
        }

    @Test
    fun `single node graph with no children assembles`() {
        val graph = GPURuntimeEffectShaderGraph(
            nodes = listOf(GPURuntimeEffectShaderGraphNode("a", emptyMap())),
            edges = emptyList(),
        )
        val plan = assembled(
            GPURuntimeEffectShaderGraphAssembler.assemble(
                graph = graph,
                nodeWgsl = mapOf("a" to "return vec4<f32>(1.0, 1.0, 1.0, 1.0);"),
                budget = generousBudget(),
            ),
        )

        assertEquals(listOf("a"), plan.sortedNodes)
        assertTrue(plan.combinedWgsl.contains("fn node_0_main"))
        assertTrue(plan.combinedWgsl.contains("fn assembled_main"))
        assertTrue(plan.combinedWgsl.contains("return node_0_main(coord);"))
    }

    @Test
    fun `two level graph assembles with wired child wrapper`() {
        val graph = GPURuntimeEffectShaderGraph(
            nodes = listOf(
                GPURuntimeEffectShaderGraphNode("parent", mapOf("fg" to "child")),
                GPURuntimeEffectShaderGraphNode("child", emptyMap()),
            ),
            edges = listOf(GPURuntimeEffectShaderGraphEdge("parent", "child", "fg")),
        )
        val plan = assembled(
            GPURuntimeEffectShaderGraphAssembler.assemble(
                graph = graph,
                nodeWgsl = mapOf(
                    "parent" to "return evaluateChild_fg(coord);",
                    "child" to "return vec4<f32>(1.0, 0.0, 0.0, 1.0);",
                ),
                budget = generousBudget(),
            ),
        )

        // children are emitted before parents (leaves first)
        assertEquals(listOf("child", "parent"), plan.sortedNodes)
        assertTrue(plan.combinedWgsl.contains("fn node_0_main"))
        assertTrue(plan.combinedWgsl.contains("fn node_1_main"))
        assertTrue(plan.combinedWgsl.contains("fn node_1_evaluateChild_fg(coord: vec2<f32>) -> vec4<f32>"))
        // wrapper delegates to child main
        assertTrue(plan.combinedWgsl.contains("return node_0_main(coord);"))
        // parent body rewritten to call the prefixed wrapper
        assertTrue(plan.combinedWgsl.contains("return node_1_evaluateChild_fg(coord);"))
        // entry calls root (parent)
        assertTrue(plan.combinedWgsl.contains("fn assembled_main"))
        assertTrue(plan.combinedWgsl.contains("return node_1_main(coord);"))
    }

    @Test
    fun `three level graph with multiple children assembles leaves first`() {
        val graph = GPURuntimeEffectShaderGraph(
            nodes = listOf(
                GPURuntimeEffectShaderGraphNode("root", mapOf("l" to "b", "r" to "c")),
                GPURuntimeEffectShaderGraphNode("b", mapOf("x" to "d")),
                GPURuntimeEffectShaderGraphNode("c", emptyMap()),
                GPURuntimeEffectShaderGraphNode("d", emptyMap()),
            ),
            edges = listOf(
                GPURuntimeEffectShaderGraphEdge("root", "b", "l"),
                GPURuntimeEffectShaderGraphEdge("root", "c", "r"),
                GPURuntimeEffectShaderGraphEdge("b", "d", "x"),
            ),
        )
        val plan = assembled(
            GPURuntimeEffectShaderGraphAssembler.assemble(
                graph = graph,
                nodeWgsl = mapOf(
                    "root" to "return evaluateChild_l(coord) + evaluateChild_r(coord);",
                    "b" to "return evaluateChild_x(coord);",
                    "c" to "return vec4<f32>(0.0);",
                    "d" to "return vec4<f32>(1.0);",
                ),
                budget = generousBudget(),
            ),
        )

        assertEquals(4, plan.sortedNodes.size)
        // every child appears before its parent
        assertTrue(plan.sortedNodes.indexOf("d") < plan.sortedNodes.indexOf("b"))
        assertTrue(plan.sortedNodes.indexOf("b") < plan.sortedNodes.indexOf("root"))
        assertTrue(plan.sortedNodes.indexOf("c") < plan.sortedNodes.indexOf("root"))
        assertEquals("root", plan.sortedNodes.last())
        for (i in 0 until 4) {
            assertTrue(plan.combinedWgsl.contains("fn node_${i}_main"))
        }
    }

    @Test
    fun `cycle is detected and refused`() {
        val graph = GPURuntimeEffectShaderGraph(
            nodes = listOf(
                GPURuntimeEffectShaderGraphNode("a", mapOf("x" to "b")),
                GPURuntimeEffectShaderGraphNode("b", mapOf("y" to "a")),
            ),
            edges = listOf(
                GPURuntimeEffectShaderGraphEdge("a", "b", "x"),
                GPURuntimeEffectShaderGraphEdge("b", "a", "y"),
            ),
        )
        val code = refused(
            GPURuntimeEffectShaderGraphAssembler.assemble(
                graph = graph,
                nodeWgsl = mapOf("a" to "return vec4<f32>(0.0);", "b" to "return vec4<f32>(0.0);"),
                budget = generousBudget(),
            ),
        )
        assertEquals("unsupported.runtime_effect.shader_graph_cycle", code)
    }

    @Test
    fun `depth budget exceeded is refused`() {
        val graph = GPURuntimeEffectShaderGraph(
            nodes = listOf(
                GPURuntimeEffectShaderGraphNode("a", mapOf("c" to "b")),
                GPURuntimeEffectShaderGraphNode("b", mapOf("c" to "c")),
                GPURuntimeEffectShaderGraphNode("c", mapOf("c" to "d")),
                GPURuntimeEffectShaderGraphNode("d", emptyMap()),
            ),
            edges = listOf(
                GPURuntimeEffectShaderGraphEdge("a", "b", "c"),
                GPURuntimeEffectShaderGraphEdge("b", "c", "c"),
                GPURuntimeEffectShaderGraphEdge("c", "d", "c"),
            ),
        )
        val code = refused(
            GPURuntimeEffectShaderGraphAssembler.assemble(
                graph = graph,
                nodeWgsl = mapOf(
                    "a" to "return evaluateChild_c(coord);",
                    "b" to "return evaluateChild_c(coord);",
                    "c" to "return evaluateChild_c(coord);",
                    "d" to "return vec4<f32>(1.0);",
                ),
                budget = generousBudget().copy(maxDepth = 2),
            ),
        )
        assertEquals("unsupported.runtime_effect.shader_graph_depth_exceeded", code)
    }

    @Test
    fun `children per node budget exceeded is refused`() {
        val graph = GPURuntimeEffectShaderGraph(
            nodes = listOf(
                GPURuntimeEffectShaderGraphNode("p", mapOf("a" to "x", "b" to "y", "c" to "z")),
                GPURuntimeEffectShaderGraphNode("x", emptyMap()),
                GPURuntimeEffectShaderGraphNode("y", emptyMap()),
                GPURuntimeEffectShaderGraphNode("z", emptyMap()),
            ),
            edges = listOf(
                GPURuntimeEffectShaderGraphEdge("p", "x", "a"),
                GPURuntimeEffectShaderGraphEdge("p", "y", "b"),
                GPURuntimeEffectShaderGraphEdge("p", "z", "c"),
            ),
        )
        val code = refused(
            GPURuntimeEffectShaderGraphAssembler.assemble(
                graph = graph,
                nodeWgsl = mapOf(
                    "p" to "return evaluateChild_a(coord);",
                    "x" to "return vec4<f32>(0.0);",
                    "y" to "return vec4<f32>(0.0);",
                    "z" to "return vec4<f32>(0.0);",
                ),
                budget = generousBudget().copy(maxChildrenPerNode = 2),
            ),
        )
        assertEquals("unsupported.runtime_effect.shader_graph_children_exceeded", code)
    }

    @Test
    fun `empty graph is refused`() {
        val graph = GPURuntimeEffectShaderGraph(nodes = emptyList(), edges = emptyList())
        val code = refused(
            GPURuntimeEffectShaderGraphAssembler.assemble(
                graph = graph,
                nodeWgsl = emptyMap(),
                budget = generousBudget(),
            ),
        )
        assertEquals("unsupported.runtime_effect.shader_graph_empty", code)
    }

    @Test
    fun `assembly is deterministic for the same input`() {
        val graph = GPURuntimeEffectShaderGraph(
            nodes = listOf(
                GPURuntimeEffectShaderGraphNode("root", mapOf("l" to "b", "r" to "c")),
                GPURuntimeEffectShaderGraphNode("b", emptyMap()),
                GPURuntimeEffectShaderGraphNode("c", emptyMap()),
            ),
            edges = listOf(
                GPURuntimeEffectShaderGraphEdge("root", "b", "l"),
                GPURuntimeEffectShaderGraphEdge("root", "c", "r"),
            ),
        )
        val wgsl = mapOf(
            "root" to "return evaluateChild_l(coord) + evaluateChild_r(coord);",
            "b" to "return vec4<f32>(1.0);",
            "c" to "return vec4<f32>(0.0);",
        )
        val first = assembled(
            GPURuntimeEffectShaderGraphAssembler.assemble(graph, wgsl, budget = generousBudget()),
        )
        val second = assembled(
            GPURuntimeEffectShaderGraphAssembler.assemble(graph, wgsl, budget = generousBudget()),
        )
        assertEquals(first.combinedWgsl, second.combinedWgsl)
        assertEquals(first.combinedUniformBlock, second.combinedUniformBlock)
        assertEquals(first.sortedNodes, second.sortedNodes)
    }

    @Test
    fun `assembly is deterministic regardless of input ordering`() {
        val nodes = listOf(
            GPURuntimeEffectShaderGraphNode("root", mapOf("l" to "b", "r" to "c")),
            GPURuntimeEffectShaderGraphNode("b", emptyMap()),
            GPURuntimeEffectShaderGraphNode("c", emptyMap()),
        )
        val wgsl = mapOf(
            "root" to "return evaluateChild_l(coord) + evaluateChild_r(coord);",
            "b" to "return vec4<f32>(1.0);",
            "c" to "return vec4<f32>(0.0);",
        )
        val ordered = GPURuntimeEffectShaderGraph(
            nodes = nodes,
            edges = listOf(
                GPURuntimeEffectShaderGraphEdge("root", "b", "l"),
                GPURuntimeEffectShaderGraphEdge("root", "c", "r"),
            ),
        )
        val shuffled = GPURuntimeEffectShaderGraph(
            nodes = nodes.reversed(),
            edges = listOf(
                GPURuntimeEffectShaderGraphEdge("root", "c", "r"),
                GPURuntimeEffectShaderGraphEdge("root", "b", "l"),
            ),
        )
        val a = assembled(GPURuntimeEffectShaderGraphAssembler.assemble(ordered, wgsl, budget = generousBudget()))
        val b = assembled(GPURuntimeEffectShaderGraphAssembler.assemble(shuffled, wgsl, budget = generousBudget()))
        assertEquals(a.combinedWgsl, b.combinedWgsl)
        assertEquals(a.sortedNodes, b.sortedNodes)
    }

    @Test
    fun `combined uniform block merges node uniforms deterministically`() {
        val graph = GPURuntimeEffectShaderGraph(
            nodes = listOf(
                GPURuntimeEffectShaderGraphNode("parent", mapOf("fg" to "child")),
                GPURuntimeEffectShaderGraphNode("child", emptyMap()),
            ),
            edges = listOf(GPURuntimeEffectShaderGraphEdge("parent", "child", "fg")),
        )
        val plan = assembled(
            GPURuntimeEffectShaderGraphAssembler.assemble(
                graph = graph,
                nodeWgsl = mapOf(
                    "parent" to "return evaluateChild_fg(coord);",
                    "child" to "return vec4<f32>(1.0);",
                ),
                nodeUniforms = mapOf(
                    "parent" to "parent_gain: f32,",
                    "child" to "child_tint: vec4<f32>,",
                ),
                budget = generousBudget(),
            ),
        )
        // child uniforms come first (leaves first), then parent
        val childIdx = plan.combinedUniformBlock.indexOf("child_tint")
        val parentIdx = plan.combinedUniformBlock.indexOf("parent_gain")
        assertTrue(childIdx >= 0 && parentIdx >= 0)
        assertTrue(childIdx < parentIdx)
    }

    @Test
    fun `instruction budget exceeded is refused`() {
        val body = (1..50).joinToString("\n") { "let v$it = vec4<f32>(0.0);" } + "\nreturn vec4<f32>(0.0);"
        val graph = GPURuntimeEffectShaderGraph(
            nodes = listOf(GPURuntimeEffectShaderGraphNode("a", emptyMap())),
            edges = emptyList(),
        )
        val code = refused(
            GPURuntimeEffectShaderGraphAssembler.assemble(
                graph = graph,
                nodeWgsl = mapOf("a" to body),
                budget = generousBudget().copy(maxWgslInstructions = 10),
            ),
        )
        assertEquals("unsupported.runtime_effect.shader_graph_instructions_exceeded", code)
    }

    @Test
    fun `uniform buffer budget exceeded is refused`() {
        val graph = GPURuntimeEffectShaderGraph(
            nodes = listOf(GPURuntimeEffectShaderGraphNode("a", emptyMap())),
            edges = emptyList(),
        )
        val code = refused(
            GPURuntimeEffectShaderGraphAssembler.assemble(
                graph = graph,
                nodeWgsl = mapOf("a" to "return vec4<f32>(0.0);"),
                nodeUniforms = mapOf("a" to "x".repeat(500) + ": f32,"),
                budget = generousBudget().copy(maxUniformBufferBytes = 16),
            ),
        )
        assertEquals("unsupported.runtime_effect.shader_graph_uniform_buffer_exceeded", code)
    }

    @Test
    fun `missing wgsl source is refused`() {
        val graph = GPURuntimeEffectShaderGraph(
            nodes = listOf(GPURuntimeEffectShaderGraphNode("a", emptyMap())),
            edges = emptyList(),
        )
        val code = refused(
            GPURuntimeEffectShaderGraphAssembler.assemble(
                graph = graph,
                nodeWgsl = emptyMap(),
                budget = generousBudget(),
            ),
        )
        assertEquals("unsupported.runtime_effect.shader_graph_missing_wgsl", code)
    }

    @Test
    fun `valid assembly produces WGSL reflection report with successful validation`() {
        val graph = GPURuntimeEffectShaderGraph(
            nodes = listOf(
                GPURuntimeEffectShaderGraphNode("root", mapOf("fg" to "child")),
                GPURuntimeEffectShaderGraphNode("child", emptyMap()),
            ),
            edges = listOf(GPURuntimeEffectShaderGraphEdge("root", "child", "fg")),
        )
        val result = GPURuntimeEffectShaderGraphAssembler.assemble(
            graph = graph,
            nodeWgsl = mapOf(
                "root" to "return evaluateChild_fg(coord);",
                "child" to "return vec4<f32>(1.0, 0.0, 0.0, 1.0);",
            ),
            budget = generousBudget(),
        )
        val plan = assembled(result)
        assertNotNull(plan.wgslReflection, "expected WGSL reflection report on assembled plan")
        assertTrue(
            plan.wgslReflection!!.validation.success,
            "expected validation to succeed, got: ${plan.wgslReflection!!.validation.diagnostics}",
        )
    }

    @Test
    fun `invalid wgsl node body causes refusal during parser-backed validation`() {
        val graph = GPURuntimeEffectShaderGraph(
            nodes = listOf(GPURuntimeEffectShaderGraphNode("a", emptyMap())),
            edges = emptyList(),
        )
        val code = refused(
            GPURuntimeEffectShaderGraphAssembler.assemble(
                graph = graph,
                nodeWgsl = mapOf("a" to "return vec4<f32>(1.0 NOTVALID;"),
                budget = generousBudget(),
            ),
        )
        assertEquals("unsupported.runtime_effect.shader_graph_wgsl_parse_error", code)
    }

    @Test
    fun `valid assembly reflection has no duplicate entry point names`() {
        val graph = GPURuntimeEffectShaderGraph(
            nodes = listOf(
                GPURuntimeEffectShaderGraphNode("root", mapOf("l" to "b", "r" to "c")),
                GPURuntimeEffectShaderGraphNode("b", emptyMap()),
                GPURuntimeEffectShaderGraphNode("c", emptyMap()),
            ),
            edges = listOf(
                GPURuntimeEffectShaderGraphEdge("root", "b", "l"),
                GPURuntimeEffectShaderGraphEdge("root", "c", "r"),
            ),
        )
        val result = GPURuntimeEffectShaderGraphAssembler.assemble(
            graph = graph,
            nodeWgsl = mapOf(
                "root" to "return evaluateChild_l(coord) + evaluateChild_r(coord);",
                "b" to "return vec4<f32>(1.0);",
                "c" to "return vec4<f32>(0.5);",
            ),
            budget = generousBudget(),
        )
        val plan = assembled(result)
        val report = plan.wgslReflection ?: fail("expected WGSL reflection report")
        val epNames = report.entryPoints.map { it.name }
        val uniqueEpNames = epNames.toSet()
        assertEquals(
            epNames.size, uniqueEpNames.size,
            "duplicate entry point names detected: $epNames",
        )
        val bindingKeys = report.bindings.map { "${it.group}/${it.binding}/${it.name}" }
        val uniqueBindingKeys = bindingKeys.toSet()
        assertEquals(
            bindingKeys.size, uniqueBindingKeys.size,
            "duplicate binding keys detected: $bindingKeys",
        )
    }

    @Test
    fun `determinism proof produces byte identical output across runs`() {
        val graph = GPURuntimeEffectShaderGraph(
            nodes = listOf(
                GPURuntimeEffectShaderGraphNode("root", mapOf("a" to "x", "b" to "y")),
                GPURuntimeEffectShaderGraphNode("x", emptyMap()),
                GPURuntimeEffectShaderGraphNode("y", emptyMap()),
            ),
            edges = listOf(
                GPURuntimeEffectShaderGraphEdge("root", "x", "a"),
                GPURuntimeEffectShaderGraphEdge("root", "y", "b"),
            ),
        )
        val wgsl = mapOf(
            "root" to "return evaluateChild_a(coord) + evaluateChild_b(coord);",
            "x" to "return vec4<f32>(1.0);",
            "y" to "return vec4<f32>(0.5);",
        )
        val bytesA = assembled(
            GPURuntimeEffectShaderGraphAssembler.assemble(graph, wgsl, budget = generousBudget()),
        ).combinedWgsl.toByteArray(Charsets.UTF_8)
        val bytesB = assembled(
            GPURuntimeEffectShaderGraphAssembler.assemble(graph, wgsl, budget = generousBudget()),
        ).combinedWgsl.toByteArray(Charsets.UTF_8)
        assertTrue(bytesA.contentEquals(bytesB))
        assertNotEquals(0, bytesA.size)
    }
}
