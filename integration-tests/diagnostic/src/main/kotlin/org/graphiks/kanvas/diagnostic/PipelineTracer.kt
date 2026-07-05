package org.graphiks.kanvas.diagnostic

import org.graphiks.kanvas.surface.RenderOpListener

data class PipelineTrace(
    val summary: PipelineSummary,
    val ops: List<PipelineOpEntry>,
)

data class PipelineSummary(
    val dispatched: Int,
    val refused: Int,
)

data class PipelineOpEntry(
    val opIndex: Int,
    val route: String,
    val status: String,
    val shaders: List<String>?,
    val vertexCount: Int?,
    val blendMode: String?,
    val reason: RefusalReason?,
)

data class RefusalReason(
    val code: String,
    val message: String,
)

class PipelineTracer : RenderOpListener {
    private val entries = mutableListOf<PipelineOpEntry>()
    private var dispatched = 0
    private var refused = 0

    override fun onOpDispatched(
        index: Int,
        opType: String,
        route: String,
        shaders: List<String>,
        vertexCount: Int,
        blendMode: String,
    ) {
        dispatched++
        entries.add(PipelineOpEntry(
            opIndex = index,
            route = route,
            status = "dispatched",
            shaders = shaders,
            vertexCount = vertexCount,
            blendMode = blendMode,
            reason = null,
        ))
    }

    override fun onOpRefused(index: Int, opType: String, code: String, reason: String) {
        refused++
        entries.add(PipelineOpEntry(
            opIndex = index,
            route = "Refused",
            status = "refused",
            shaders = null,
            vertexCount = null,
            blendMode = null,
            reason = RefusalReason(code, reason),
        ))
    }

    fun buildTrace(): PipelineTrace =
        PipelineTrace(PipelineSummary(dispatched, refused), entries.toList())

    fun reset() {
        entries.clear()
        dispatched = 0
        refused = 0
    }
}
