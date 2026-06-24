package org.graphiks.kanvas.gpu.renderer.filters

/** A single node in the filter DAG. */
data class GPUFilterDAGNode(
    val nodeId: GPUFilterNodeID,
    val nodeKind: String,
    val inputLabels: List<String>,
)

/** An edge connecting two filter DAG nodes. */
data class GPUFilterDAGEdge(
    val fromNodeId: GPUFilterNodeID,
    val toNodeId: GPUFilterNodeID,
)

/** A full filter DAG composed of nodes and edges. */
data class GPUFilterDAG(
    val graphId: String,
    val nodes: List<GPUFilterDAGNode>,
    val edges: List<GPUFilterDAGEdge>,
) {
    val nodeCount: Int get() = nodes.size
    val edgeCount: Int get() = edges.size
}

/** Result of executing a filter DAG. */
data class GPUFilterDAGExecutionResult(
    val passCount: Int,
    val intermediateCount: Int,
    val accepted: Boolean,
    val diagnostics: List<String> = emptyList(),
)

/** Executes a filter DAG and produces execution stats. */
class GPUFilterDAGExecutor {
    /** Validates and executes the filter DAG, returning pass/intermediate counts. */
    fun execute(dag: GPUFilterDAG): GPUFilterDAGExecutionResult {
        val diagnostics = mutableListOf<String>()

        if (dag.nodeCount == 0) {
            diagnostics.add("unsupported.filter.dag_empty")
            return GPUFilterDAGExecutionResult(
                passCount = 0,
                intermediateCount = 0,
                accepted = false,
                diagnostics = diagnostics,
            )
        }

        if (dag.nodeCount > 2) {
            diagnostics.add("unsupported.filter.dag_too_many_nodes")
            return GPUFilterDAGExecutionResult(
                passCount = 0,
                intermediateCount = 0,
                accepted = false,
                diagnostics = diagnostics,
            )
        }

        val intermediateCount = (dag.nodeCount - 1).coerceAtLeast(0)
        val passCount = dag.nodeCount

        return GPUFilterDAGExecutionResult(
            passCount = passCount,
            intermediateCount = intermediateCount,
            accepted = true,
            diagnostics = diagnostics,
        )
    }
}
