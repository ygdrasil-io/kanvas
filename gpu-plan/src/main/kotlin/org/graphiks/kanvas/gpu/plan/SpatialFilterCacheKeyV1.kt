package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.RectI32

/**
 * Immutable identity for one already-frozen spatial filter result.  This is a planner value:
 * native materialization receives a selected binding, never a key to reconstruct.
 */
public class SpatialFilterInputGenerationV1 internal constructor(
    public val sourceIdentity: String,
    public val generationI64: Long,
    subsetDeviceI32: RectI32,
) {
    private val subsetSnapshot = subsetDeviceI32.copy()
    init { require(sourceIdentity.isNotBlank() && generationI64 >= 0L && !subsetSnapshot.isEmpty) }
    public fun copySubsetDeviceI32(): RectI32 = subsetSnapshot.copy()
    override fun equals(other: Any?): Boolean = other is SpatialFilterInputGenerationV1 &&
        sourceIdentity == other.sourceIdentity && generationI64 == other.generationI64 &&
        subsetSnapshot == other.subsetSnapshot
    override fun hashCode(): Int = 31 * (31 * sourceIdentity.hashCode() + generationI64.hashCode()) + subsetSnapshot.hashCode()
}

/** Complete cache identity: occurrence, four bounds, target ABI, ordered input generations and backend epochs. */
public class SpatialFilterCacheKeyV1 internal constructor(
    /** Pass occurrence identity; two operations from one node evaluation are distinct entries. */
    public val passIdentity: String,
    public val evaluationKey: FilterEvaluationKeyV1,
    bounds: FilterBoundsPlanV1,
    public val format: PlanLogicalColorFormat,
    public val colorSpaceIdentity: String,
    public val sampleCountI32: Int,
    public val capabilityGenerationI64: Long,
    public val backendGenerationI64: Long,
    public val semanticVersionI32: Int,
    inputGenerations: List<SpatialFilterInputGenerationV1>,
) {
    private val known = bounds.copyKnownContentDeviceI32()
    private val desired = bounds.copyDesiredOutputDeviceI32()
    private val required = bounds.copyRequiredInputDeviceI32()
    private val produced = bounds.copyProducedOutputDeviceI32()
    private val origin = bounds.copyTargetOriginDeviceI32()
    private val inputs = immutableList(inputGenerations)
    init {
        require(passIdentity.isNotBlank() && sampleCountI32 == 1 && capabilityGenerationI64 >= 0L && backendGenerationI64 >= 0L &&
            semanticVersionI32 > 0 && colorSpaceIdentity.isNotBlank() && inputs.isNotEmpty())
    }
    public fun inputGenerations(): List<SpatialFilterInputGenerationV1> = inputs
    public fun copyKnownContentDeviceI32(): RectI32? = known?.copy()
    public fun copyDesiredOutputDeviceI32(): RectI32 = desired.copy()
    public fun copyRequiredInputDeviceI32(): RectI32 = required.copy()
    public fun copyProducedOutputDeviceI32(): RectI32? = produced?.copy()
    override fun equals(other: Any?): Boolean = other is SpatialFilterCacheKeyV1 &&
        passIdentity == other.passIdentity && evaluationIdentity(evaluationKey) == evaluationIdentity(other.evaluationKey) && known == other.known &&
        desired == other.desired && required == other.required && produced == other.produced && origin == other.origin &&
        format == other.format && colorSpaceIdentity == other.colorSpaceIdentity && sampleCountI32 == other.sampleCountI32 &&
        capabilityGenerationI64 == other.capabilityGenerationI64 && backendGenerationI64 == other.backendGenerationI64 &&
        semanticVersionI32 == other.semanticVersionI32 && inputs == other.inputs
    override fun hashCode(): Int = listOf(passIdentity, evaluationIdentity(evaluationKey), known, desired, required, produced, origin, format,
        colorSpaceIdentity, sampleCountI32, capabilityGenerationI64, backendGenerationI64, semanticVersionI32, inputs).hashCode()
    private fun evaluationIdentity(key: FilterEvaluationKeyV1): List<Any?> = listOf(
        key.capturedNodeId?.valueI32, key.maskOccurrenceI32, key.boundSourceId.value,
        key.sourceRevisionIdentity,
        key.copyDesiredOutputDeviceI32(), key.mapping.copyLocalToDeviceF64(),
        key.mapping.copyDeviceToLayerF64(), key.mapping.copyLocalToLayerF64(),
        key.mapping.copyLayerOriginDeviceI32(),
    )
}
