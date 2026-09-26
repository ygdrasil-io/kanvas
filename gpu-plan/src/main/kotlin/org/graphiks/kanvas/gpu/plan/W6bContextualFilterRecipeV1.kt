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

internal class W6bEvaluatedFilterRecipeV1(
    requiredInputDeviceI32: RectI32?,
    val output: W6bRecipeSourceV1,
    val terminalKey: W6bRecipeKeyV1,
    instructions: List<W6bRecipeInstructionV1>,
    sources: Map<W6bRecipeSymbolV1, W6bRecipeSourceV1>,
) {
    private val requiredInput = requiredInputDeviceI32?.copy()
    val instructions = immutableList(instructions)
    val sources: Map<W6bRecipeSymbolV1, W6bRecipeSourceV1> = java.util.Collections.unmodifiableMap(LinkedHashMap(sources))
    fun copyRequiredInputDeviceI32(): RectI32? = requiredInput?.copy()
    fun copyProducedOutputDeviceI32(): RectI32? = output.copyProducedOutputDeviceI32()
}

/** One bound topology supplies inverse demand and forward descriptors to physical lowering. */
internal class W6bContextualFilterRecipeV1 internal constructor(
    private val occurrence: W6bFilterGraphConstruction.PositiveOccurrence,
    desiredOutputDeviceI32: RectI32,
    mapping: LayerMappingF64,
) {
    private val demands = W6bFilterDemandsV1(occurrence.topology, occurrence.mask, desiredOutputDeviceI32, mapping)
    fun copyRequiredInputDeviceI32(): RectI32? = demands.copyRequiredSourceI32()
    fun evaluate(source: W6bFilterSourceFactsV1, runtimeCatalog: RuntimeEffectSemanticCatalogSnapshot): W6bEvaluatedFilterRecipeV1 =
        W6bFilterGraphConstruction.evaluateRecipe(occurrence, source, demands, runtimeCatalog)
}

internal fun W6bFilterGraphConstruction.bindOccurrenceRecipe(
    occurrence: W6bFilterGraphConstruction.PositiveOccurrence,
    desiredOutputDeviceI32: RectI32,
    mapping: LayerMappingF64,
): W6bContextualFilterRecipeV1 = W6bContextualFilterRecipeV1(occurrence, desiredOutputDeviceI32, mapping)
