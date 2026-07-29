package org.graphiks.kanvas.gpu.renderer.layers

import org.graphiks.kanvas.gpu.renderer.filters.GPUPreparedFilterNodeId
import org.graphiks.kanvas.gpu.renderer.filters.GPUPreparedFilterGraph
import org.graphiks.kanvas.gpu.renderer.filters.GPUPreparedFilterInputRef
import org.graphiks.kanvas.gpu.renderer.filters.GPUPreparedFilterNormalization

/** Stable composite scope identity. */
@JvmInline
value class GPUPreparedCompositeScopeId(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUPreparedCompositeScopeId.value must not be blank" }
    }
}

/** Scope origin kind. */
enum class GPUPreparedCompositeScopeKind {
    Root,
    SaveLayer,
    PaintedPicture,
    FilterPictureSource,
}

/** Ordered entry in a composite scope. */
sealed interface GPUPreparedCompositeEntry {
    data class Draw(val operationIndex: Int) : GPUPreparedCompositeEntry
    data class Scope(val id: GPUPreparedCompositeScopeId) : GPUPreparedCompositeEntry
}

/** Immutable composite scope. */
data class GPUPreparedCompositeScope(
    val id: GPUPreparedCompositeScopeId,
    val parentId: GPUPreparedCompositeScopeId?,
    val saveOperationIndex: Int?,
    val restoreOperationIndex: Int?,
    val entries: List<GPUPreparedCompositeEntry>,
    val sourceKind: GPUPreparedCompositeScopeKind,
    val provenance: String,
)

sealed interface GPUPreparedImageFilterLowering {
    data class Ready(val graph: GPUPreparedFilterGraph) :
        GPUPreparedImageFilterLowering

    data class Refused(
        val code: String,
        val facts: Map<String, String>,
    ) : GPUPreparedImageFilterLowering
}

data class GPUPreparedCompositePlan(
    val captureIdentity: String,
    val rootScopeId: GPUPreparedCompositeScopeId,
    val layers: List<GPULayerPlan>,
    val normalizedFilters: Map<GPUPreparedCompositeScopeId, GPUPreparedFilterNormalization>,
    val identity: String,
)

sealed interface GPUPreparedCompositeLowering {
    data class Ready(val plan: GPUPreparedCompositePlan) :
        GPUPreparedCompositeLowering

    data class Refused(
        val code: String,
        val operationIndex: Int?,
        val facts: Map<String, String>,
    ) : GPUPreparedCompositeLowering
}

data class GPUPreparedMaskFilterPlan(
    val kind: String,
    val coverageFormat: String,
    val executionIdentity: String,
    val tableEntries: List<Int> = emptyList(),
)

sealed interface GPUPreparedMaskFilterLowering {
    data class Ready(val plan: GPUPreparedMaskFilterPlan) :
        GPUPreparedMaskFilterLowering

    data class Refused(
        val code: String,
        val facts: Map<String, String>,
    ) : GPUPreparedMaskFilterLowering
}
