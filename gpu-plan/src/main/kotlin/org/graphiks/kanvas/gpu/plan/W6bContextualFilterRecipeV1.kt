package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.RectI32
import org.graphiks.math.matrix.LayerMappingF64

/** Immutable source facts sealed independently of the future parent target. */
internal class W6bFilterSourceFactsV1(
    sourceDomainDeviceI32: RectI32,
    knownContentDeviceI32: RectI32?,
    desiredOutputDeviceI32: RectI32,
    val mapping: LayerMappingF64,
    val preparePicture: ((W6bBoundFilterOperationV1, W6bSourceGeometryV1) -> W6bPreparedPictureSourceV1?)? = null,
) {
    private val sourceDomain = sourceDomainDeviceI32.copy()
    private val knownContent = knownContentDeviceI32?.copy()
    private val desiredOutput = desiredOutputDeviceI32.copy()
    fun copySourceDomainDeviceI32(): RectI32 = sourceDomain.copy()
    fun copyKnownContentDeviceI32(): RectI32? = knownContent?.copy()
    fun copyDesiredOutputDeviceI32(): RectI32 = desiredOutput.copy()
}

/** Semantic regions are independent of the retained physical sampling target of each phase. */
internal class W6bEvaluatedOperationFactsV1(
    val nodeId: org.graphiks.kanvas.render.ir.CapturedFilterNodeIdI32,
    knownContentDeviceI32: RectI32?,
    desiredOutputDeviceI32: RectI32?,
    requiredInputDeviceI32: RectI32?,
    producedOutputDeviceI32: RectI32?,
    inputDemandsDeviceI32: List<RectI32?>,
) {
    private val known = knownContentDeviceI32?.copy()
    private val desired = desiredOutputDeviceI32?.copy()
    private val required = requiredInputDeviceI32?.copy()
    private val produced = producedOutputDeviceI32?.copy()
    private val inputDemands = inputDemandsDeviceI32.map { it?.copy() }
    fun copyKnownContentDeviceI32(): RectI32? = known?.copy()
    fun copyDesiredOutputDeviceI32(): RectI32? = desired?.copy()
    fun copyRequiredInputDeviceI32(): RectI32? = required?.copy()
    fun copyProducedOutputDeviceI32(): RectI32? = produced?.copy()
    fun copyInputDemandsDeviceI32(): List<RectI32?> = inputDemands.map { it?.copy() }
}

internal class W6bEvaluatedFilterRecipeV1(
    requiredInputDeviceI32: RectI32?,
    val output: W6bRecipeSourceV1,
    val terminalKey: W6bRecipeKeyV1,
    instructions: List<W6bRecipeInstructionV1>,
    sources: Map<W6bRecipeSymbolV1, W6bRecipeSourceV1>,
    operationFacts: List<W6bEvaluatedOperationFactsV1> = emptyList(),
) {
    private val requiredInput = requiredInputDeviceI32?.copy()
    val instructions = immutableList(instructions)
    val operationFacts = immutableList(operationFacts)
    val sources: Map<W6bRecipeSymbolV1, W6bRecipeSourceV1> = java.util.Collections.unmodifiableMap(LinkedHashMap(sources))
    fun copyRequiredInputDeviceI32(): RectI32? = requiredInput?.copy()
    fun copyProducedOutputDeviceI32(): RectI32? = output.copyProducedOutputDeviceI32()
}

/** The mask footprint is independent of initialized texels; bind those after the single W4 lane. */
internal class W6bPreparedMaskRecipeV1(
    val outputGeometry: W6bRecipeSourceV1,
    private val bindInitializedContent: (RectI32?) -> W6bEvaluatedFilterRecipeV1,
) {
    fun evaluate(knownContentDeviceI32: RectI32?): W6bEvaluatedFilterRecipeV1 =
        bindInitializedContent(knownContentDeviceI32?.copy())
}

/** One bound topology supplies inverse demand and forward descriptors to physical lowering. */
internal class W6bContextualFilterRecipeV1 internal constructor(
    private val occurrence: W6bFilterGraphConstruction.PositiveOccurrence,
    desiredOutputDeviceI32: RectI32,
    mapping: LayerMappingF64,
) {
    private val demands = W6bFilterDemandsV1(occurrence.topology, occurrence.mask, desiredOutputDeviceI32, mapping)
    fun copyRequiredInputDeviceI32(): RectI32? = demands.copyRequiredSourceI32()
    fun evaluateMask(source: W6bFilterSourceFactsV1): W6bEvaluatedFilterRecipeV1 =
        W6bFilterGraphConstruction.evaluateMaskRecipe(occurrence, source)
    fun evaluate(source: W6bFilterSourceFactsV1, runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot): W6bEvaluatedFilterRecipeV1 =
        W6bFilterGraphConstruction.evaluateRecipe(occurrence, source, demands, runtimeCatalog)
}

internal fun W6bFilterGraphConstruction.bindOccurrenceRecipe(
    occurrence: W6bFilterGraphConstruction.PositiveOccurrence,
    desiredOutputDeviceI32: RectI32,
    mapping: LayerMappingF64,
): W6bContextualFilterRecipeV1 = W6bContextualFilterRecipeV1(occurrence, desiredOutputDeviceI32, mapping)
