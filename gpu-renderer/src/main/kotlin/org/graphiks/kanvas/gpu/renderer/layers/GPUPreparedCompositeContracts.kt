package org.graphiks.kanvas.gpu.renderer.layers

import org.graphiks.kanvas.gpu.renderer.filters.GPUPreparedFilterNodeId
import org.graphiks.kanvas.gpu.renderer.filters.GPUPreparedFilterGraph
import org.graphiks.kanvas.gpu.renderer.filters.GPUPreparedFilterInputRef
import org.graphiks.kanvas.gpu.renderer.filters.GPUPreparedFilterNormalization
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import java.util.Collections

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

/** Exact float rectangle encoded with IEEE-754 raw bits. */
data class GPUPreparedRectSnapshot(
    val leftBits: Int,
    val topBits: Int,
    val rightBits: Int,
    val bottomBits: Int,
)

/** Exact 3x3 transform encoded with IEEE-754 raw bits. */
data class GPUPreparedMatrixSnapshot(
    val scaleXBits: Int,
    val skewXBits: Int,
    val transXBits: Int,
    val skewYBits: Int,
    val scaleYBits: Int,
    val transYBits: Int,
    val persp0Bits: Int,
    val persp1Bits: Int,
    val persp2Bits: Int,
)

/** Closed clip subset accepted by the prepared composite scaffold. */
sealed interface GPUPreparedClipSnapshot {
    data object WideOpen : GPUPreparedClipSnapshot

    data class DeviceRect(
        val rect: GPUPreparedRectSnapshot,
        val antiAlias: Boolean,
    ) : GPUPreparedClipSnapshot
}

enum class GPUPreparedPaintStyle {
    Fill,
    Stroke,
    StrokeAndFill,
}

enum class GPUPreparedStrokeCap {
    Butt,
    Round,
    Square,
}

enum class GPUPreparedStrokeJoin {
    Miter,
    Round,
    Bevel,
}

/**
 * Exact paint subset accepted at capture time.
 *
 * Shader/filter/path-effect/blender paints are refused before this snapshot is
 * created, so this type cannot silently represent only part of a Paint.
 */
data class GPUPreparedPaintSnapshot(
    val colorArgb: UInt,
    val blendMode: GPUBlendMode,
    val style: GPUPreparedPaintStyle,
    val strokeWidthBits: Int,
    val strokeCap: GPUPreparedStrokeCap,
    val strokeJoin: GPUPreparedStrokeJoin,
    val strokeMiterBits: Int,
    val antiAlias: Boolean,
)

/** Exact composite state attached to a saveLayer or picture scope. */
data class GPUPreparedCompositeScopeState(
    val bounds: GPUPreparedRectSnapshot?,
    val paint: GPUPreparedPaintSnapshot?,
    val transform: GPUPreparedMatrixSnapshot,
    val clip: GPUPreparedClipSnapshot,
    val backdropRequired: Boolean = false,
)

/** Ordered entry in a composite scope. */
sealed interface GPUPreparedCompositeEntry {
    data class Draw(val operationIndex: Int) : GPUPreparedCompositeEntry
    data class Scope(val id: GPUPreparedCompositeScopeId) : GPUPreparedCompositeEntry
}

/** Deeply immutable composite scope. */
class GPUPreparedCompositeScope(
    val id: GPUPreparedCompositeScopeId,
    val parentId: GPUPreparedCompositeScopeId?,
    val saveOperationIndex: Int?,
    val restoreOperationIndex: Int?,
    entries: List<GPUPreparedCompositeEntry>,
    val sourceKind: GPUPreparedCompositeScopeKind,
    val provenance: String,
    val state: GPUPreparedCompositeScopeState? = null,
) {
    val entries: List<GPUPreparedCompositeEntry> =
        Collections.unmodifiableList(entries.toList())
}

sealed interface GPUPreparedImageFilterLowering {
    data class Ready(val graph: GPUPreparedFilterGraph) :
        GPUPreparedImageFilterLowering

    class Refused(
        val code: String,
        facts: Map<String, String>,
    ) : GPUPreparedImageFilterLowering {
        val facts: Map<String, String> =
            Collections.unmodifiableMap(LinkedHashMap(facts))
    }
}

class GPUPreparedCompositePlan(
    val captureIdentity: String,
    val rootScopeId: GPUPreparedCompositeScopeId,
    layers: List<GPULayerPlan>,
    normalizedFilters: Map<GPUPreparedCompositeScopeId, GPUPreparedFilterNormalization>,
    val identity: String,
    gatePlans: Map<String, GPUSaveLayerIsolatedTargetGatePlan> = emptyMap(),
) {
    val layers: List<GPULayerPlan> =
        Collections.unmodifiableList(layers.toList())
    val normalizedFilters: Map<GPUPreparedCompositeScopeId, GPUPreparedFilterNormalization> =
        Collections.unmodifiableMap(LinkedHashMap(normalizedFilters))

    /** Per-layer gate plans keyed by [GPULayerSaveRecord.scopeId] value. */
    val gatePlans: Map<String, GPUSaveLayerIsolatedTargetGatePlan> =
        Collections.unmodifiableMap(LinkedHashMap(gatePlans))
}

sealed interface GPUPreparedCompositeLowering {
    data class Ready(val plan: GPUPreparedCompositePlan) :
        GPUPreparedCompositeLowering

    class Refused(
        val code: String,
        val operationIndex: Int?,
        facts: Map<String, String>,
    ) : GPUPreparedCompositeLowering {
        val facts: Map<String, String> =
            Collections.unmodifiableMap(LinkedHashMap(facts))
    }
}

enum class GPUPreparedMaskFilterKind {
    Blur,
    Shader,
    Table,
}

enum class GPUPreparedCoverageFormat {
    A8,
}

class GPUPreparedMaskFilterPlan(
    val kind: GPUPreparedMaskFilterKind,
    val coverageFormat: GPUPreparedCoverageFormat,
    val executionIdentity: String,
    tableEntries: List<Int> = emptyList(),
) {
    val tableEntries: List<Int> =
        Collections.unmodifiableList(tableEntries.toList())
}

sealed interface GPUPreparedMaskFilterLowering {
    data class Ready(val plan: GPUPreparedMaskFilterPlan) :
        GPUPreparedMaskFilterLowering

    class Refused(
        val code: String,
        facts: Map<String, String>,
    ) : GPUPreparedMaskFilterLowering {
        val facts: Map<String, String> =
            Collections.unmodifiableMap(LinkedHashMap(facts))
    }
}
