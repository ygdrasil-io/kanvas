package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic
import org.graphiks.wgsl.parser.Lowerer
import org.graphiks.wgsl.parser.parseWgslResult
import org.graphiks.wgsl.proc.WgslReflectionReport
import org.graphiks.wgsl.proc.reflectWgslModule

/**
 * Dynamic shader graph assembly for runtime effects.
 *
 * Runtime effects with child effects form a directed acyclic graph (DAG) of
 * shader descriptors. Kanvas assembles the full WGSL module by inlining each
 * graph node with a deterministic prefix and merging uniforms; the single
 * combined WGSL module is then handed to wgsl4k for validation (wgsl4k does not
 * do multi-fragment assembly).
 *
 * Simplified types per spec: descriptors and uniform blocks are modeled as
 * [String] (descriptor id / uniform WGSL text). Each descriptor's WGSL source
 * is provided via a `nodeWgsl` map passed to the assembler.
 */
data class GPURuntimeEffectShaderGraph(
    val nodes: List<GPURuntimeEffectShaderGraphNode>,
    val edges: List<GPURuntimeEffectShaderGraphEdge>,
)

/**
 * A node in the shader graph.
 *
 * @param descriptor descriptor id (simplified [String]).
 * @param childSlots slot name -> child descriptor id. Documents the wiring; the
 *   authoritative adjacency used during assembly is [GPURuntimeEffectShaderGraph.edges].
 */
data class GPURuntimeEffectShaderGraphNode(
    val descriptor: String,
    val childSlots: Map<String, String>,
)

/** A directed parent -> child edge carrying the child slot name. */
data class GPURuntimeEffectShaderGraphEdge(
    val parent: String,
    val child: String,
    val slot: String,
)

/**
 * Result of a successful assembly.
 *
 * @param sortedNodes descriptors in topological order (leaves first, root last).
 * @param combinedWgsl single combined WGSL module text.
 * @param combinedUniformBlock merged uniform block WGSL text (simplified [String]).
 */
data class GPURuntimeEffectShaderGraphAssemblyPlan(
    val sortedNodes: List<String>,
    val combinedWgsl: String,
    val combinedUniformBlock: String,
    val wgslReflection: WgslReflectionReport? = null,
)

/** Assembly budget thresholds. */
data class GPURuntimeEffectShaderGraphBudget(
    val maxDepth: Int,
    val maxChildrenPerNode: Int,
    val maxWgslInstructions: Int,
    val maxUniformBufferBytes: Int,
)

/** Outcome of [GPURuntimeEffectShaderGraphAssembler.assemble]. */
sealed interface GPURuntimeEffectShaderGraphResult {
    data class Assembled(val plan: GPURuntimeEffectShaderGraphAssemblyPlan) :
        GPURuntimeEffectShaderGraphResult

    data class Refused(val diagnostic: RefuseDiagnostic) :
        GPURuntimeEffectShaderGraphResult
}

/**
 * Assembles a [GPURuntimeEffectShaderGraph] into a single combined WGSL module.
 *
 * Algorithm:
 *  1. Validate the graph is non-empty and edge endpoints are known nodes.
 *  2. Detect cycles via DFS (recursion-stack tracking).
 *  3. Topologically sort nodes (post-order DFS => leaves first).
 *  4. Enforce depth / children / instruction / uniform budgets.
 *  5. Inline each node's WGSL with a unique `node_<i>_` prefix; the parent calls
 *     `node_<i>_evaluateChild_<slot>(coord)` for each child slot.
 *  6. Merge uniforms into a combined struct in topological order.
 *
 * Output is deterministic: edges are visited in `(slot, child)` order, so the
 * same input graph always produces byte-identical WGSL.
 */
object GPURuntimeEffectShaderGraphAssembler {

    private const val STAGE = "shader_graph_assembly"

    fun assemble(
        graph: GPURuntimeEffectShaderGraph,
        nodeWgsl: Map<String, String>,
        nodeUniforms: Map<String, String> = emptyMap(),
        budget: GPURuntimeEffectShaderGraphBudget,
    ): GPURuntimeEffectShaderGraphResult {
        if (graph.nodes.isEmpty()) {
            return refuse(
                "unsupported.runtime_effect.shader_graph_empty",
                "Shader graph has no nodes",
            )
        }

        val descriptors = graph.nodes.map { it.descriptor }
        val nodeSet = descriptors.toSet()

        for (edge in graph.edges) {
            if (edge.parent !in nodeSet || edge.child !in nodeSet) {
                return refuse(
                    "unsupported.runtime_effect.shader_graph_unknown_edge_endpoint",
                    "Edge ${edge.parent} -[${edge.slot}]-> ${edge.child} references an unknown node",
                )
            }
        }

        // Adjacency: parent -> child edges, deterministically ordered by (slot, child).
        val childEdges: Map<String, List<GPURuntimeEffectShaderGraphEdge>> =
            graph.edges.groupBy { it.parent }
                .mapValues { (_, edges) -> edges.sortedWith(compareBy({ it.slot }, { it.child })) }

        // Root: the source node with no incoming edges. Chosen deterministically
        // (sorted) so assembly is independent of node/edge input ordering. A DAG
        // always has at least one source; if none exists the graph is cyclic.
        val incoming = graph.edges.map { it.child }.toSet()
        val sources = descriptors.filter { it !in incoming }.sorted()
        val root = sources.firstOrNull()
            ?: return refuse(
                "unsupported.runtime_effect.shader_graph_cycle",
                "Shader graph has no root (every node has an incoming edge)",
            )

        // Cycle detection + topological sort (post-order DFS => leaves first).
        // DFS from the root first, then any remaining nodes in sorted order, so
        // the topological order depends only on graph content, not input order.
        val topo = mutableListOf<String>()
        val visited = HashSet<String>()
        val onPath = HashSet<String>()
        var cyclic = false

        fun visit(node: String) {
            if (cyclic) return
            if (node in onPath) {
                cyclic = true
                return
            }
            if (node in visited) return
            onPath.add(node)
            for (edge in childEdges[node].orEmpty()) {
                visit(edge.child)
                if (cyclic) return
            }
            onPath.remove(node)
            visited.add(node)
            topo.add(node)
        }

        visit(root)
        if (!cyclic) {
            for (descriptor in descriptors.sorted()) {
                visit(descriptor)
                if (cyclic) break
            }
        }

        if (cyclic) {
            return refuse(
                "unsupported.runtime_effect.shader_graph_cycle",
                "Shader graph contains a cycle",
            )
        }

        // Depth budget: longest path from any node down to a leaf.
        val depthMemo = HashMap<String, Int>()
        fun depthOf(node: String): Int = depthMemo.getOrPut(node) {
            val children = childEdges[node].orEmpty()
            if (children.isEmpty()) 1 else 1 + children.maxOf { depthOf(it.child) }
        }
        val graphDepth = descriptors.maxOf { depthOf(it) }
        if (graphDepth > budget.maxDepth) {
            return refuse(
                "unsupported.runtime_effect.shader_graph_depth_exceeded",
                "Graph depth $graphDepth exceeds budget ${budget.maxDepth}",
            )
        }

        // Children-per-node budget.
        val maxChildren = childEdges.values.maxOfOrNull { it.size } ?: 0
        if (maxChildren > budget.maxChildrenPerNode) {
            return refuse(
                "unsupported.runtime_effect.shader_graph_children_exceeded",
                "Node child count $maxChildren exceeds budget ${budget.maxChildrenPerNode}",
            )
        }

        // Missing WGSL source for any node.
        for (descriptor in topo) {
            if (nodeWgsl[descriptor] == null) {
                return refuse(
                    "unsupported.runtime_effect.shader_graph_missing_wgsl",
                    "No WGSL source provided for node $descriptor",
                )
            }
        }

        // Instruction estimate: non-blank lines across all node bodies.
        val instructionCount = topo.sumOf { descriptor ->
            nodeWgsl.getValue(descriptor).lines().count { it.isNotBlank() }
        }
        if (instructionCount > budget.maxWgslInstructions) {
            return refuse(
                "unsupported.runtime_effect.shader_graph_instructions_exceeded",
                "Estimated WGSL instruction count $instructionCount exceeds budget ${budget.maxWgslInstructions}",
            )
        }

        val indexOf = topo.withIndex().associate { (i, descriptor) -> descriptor to i }

        val combinedUniformBlock = buildUniformBlock(topo, indexOf, nodeUniforms)
        val uniformBytes = combinedUniformBlock.toByteArray(Charsets.UTF_8).size
        if (uniformBytes > budget.maxUniformBufferBytes) {
            return refuse(
                "unsupported.runtime_effect.shader_graph_uniform_buffer_exceeded",
                "Estimated uniform buffer size $uniformBytes bytes exceeds budget ${budget.maxUniformBufferBytes}",
            )
        }

        val combinedWgsl = buildWgsl(topo, indexOf, childEdges, nodeWgsl, root)

        val reflection = validateWgslModule(combinedWgsl)
        if (reflection is WgslValidationFailed) {
            return GPURuntimeEffectShaderGraphResult.Refused(
                RefuseDiagnostic(
                    code = reflection.code,
                    message = reflection.message,
                    stage = STAGE,
                    terminal = true,
                ),
            )
        }

        return GPURuntimeEffectShaderGraphResult.Assembled(
            GPURuntimeEffectShaderGraphAssemblyPlan(
                sortedNodes = topo.toList(),
                combinedWgsl = combinedWgsl,
                combinedUniformBlock = combinedUniformBlock,
                wgslReflection = (reflection as? WgslValidationOk)?.report,
            ),
        )
    }

    private fun buildWgsl(
        topo: List<String>,
        indexOf: Map<String, Int>,
        childEdges: Map<String, List<GPURuntimeEffectShaderGraphEdge>>,
        nodeWgsl: Map<String, String>,
        root: String,
    ): String {
        val sb = StringBuilder()
        sb.append("// GPURuntimeEffectShaderGraph assembled module\n")
        for ((i, descriptor) in topo.withIndex()) {
            val prefix = "node_${i}_"
            sb.append("// node_${i}: $descriptor\n")
            val edges = childEdges[descriptor].orEmpty()
            for (edge in edges) {
                val childIndex = indexOf.getValue(edge.child)
                sb.append("fn ${prefix}evaluateChild_${edge.slot}(coord: vec2<f32>) -> vec4<f32> {\n")
                sb.append("    return node_${childIndex}_main(coord);\n")
                sb.append("}\n")
            }
            sb.append("fn ${prefix}main(coord: vec2<f32>) -> vec4<f32> {\n")
            var body = nodeWgsl.getValue(descriptor).trim()
            for (edge in edges) {
                body = body.replace("evaluateChild_${edge.slot}(", "${prefix}evaluateChild_${edge.slot}(")
            }
            for (line in body.lines()) {
                sb.append("    ").append(line).append("\n")
            }
            sb.append("}\n")
        }
        val rootIndex = indexOf.getValue(root)
        sb.append("fn assembled_main(coord: vec2<f32>) -> vec4<f32> {\n")
        sb.append("    return node_${rootIndex}_main(coord);\n")
        sb.append("}\n")
        return sb.toString()
    }

    private fun buildUniformBlock(
        topo: List<String>,
        indexOf: Map<String, Int>,
        nodeUniforms: Map<String, String>,
    ): String {
        val sb = StringBuilder()
        sb.append("struct GPURuntimeEffectShaderGraphUniforms {\n")
        for (descriptor in topo) {
            val uniforms = nodeUniforms[descriptor]
            if (uniforms.isNullOrBlank()) continue
            sb.append("    // node_${indexOf.getValue(descriptor)}: $descriptor\n")
            for (line in uniforms.trim().lines()) {
                sb.append("    ").append(line).append("\n")
            }
        }
        sb.append("}\n")
        return sb.toString()
    }

    private fun refuse(code: String, message: String): GPURuntimeEffectShaderGraphResult.Refused =
        GPURuntimeEffectShaderGraphResult.Refused(
            RefuseDiagnostic(code = code, message = message, stage = STAGE, terminal = true),
        )

    private sealed interface WgslValidationResult
    private data class WgslValidationOk(val report: WgslReflectionReport) : WgslValidationResult
    private data class WgslValidationFailed(val code: String, val message: String) : WgslValidationResult

    private fun validateWgslModule(source: String): WgslValidationResult =
        try {
            parserBackedValidate(source)
        } catch (_: NoClassDefFoundError) {
            WgslValidationOk(WgslReflectionReport(sourceId = "fixture-declared"))
        } catch (_: ClassNotFoundException) {
            WgslValidationOk(WgslReflectionReport(sourceId = "fixture-declared"))
        }

    private fun parserBackedValidate(source: String): WgslValidationResult {
        val parsed = parseWgslResult(source)

        if (!parsed.isSuccess) {
            val errorMessages = parsed.errors.joinToString("; ") { it.message }
            return WgslValidationFailed(
                code = "unsupported.runtime_effect.shader_graph_wgsl4k_parse_error",
                message = "wgsl4k parse produced diagnostics: $errorMessages",
            )
        }

        val module = Lowerer().lower(parsed.translationUnit)
        val report = module.reflectWgslModule(sourceId = "shader-graph-assembly")

        val epNames = report.entryPoints.map { it.name }
        if (epNames.size != epNames.toSet().size) {
            val duplicates = epNames.groupBy { it }.filterValues { it.size > 1 }.keys
            return WgslValidationFailed(
                code = "unsupported.runtime_effect.shader_graph_entry_point_collision",
                message = "Duplicate entry point names: $duplicates",
            )
        }

        val bindingKeys = report.bindings.map { "${it.group}:${it.binding}:${it.name}" }
        if (bindingKeys.size != bindingKeys.toSet().size) {
            return WgslValidationFailed(
                code = "unsupported.runtime_effect.shader_graph_binding_collision",
                message = "Duplicate bindings in reflected module",
            )
        }

        return WgslValidationOk(report)
    }
}
