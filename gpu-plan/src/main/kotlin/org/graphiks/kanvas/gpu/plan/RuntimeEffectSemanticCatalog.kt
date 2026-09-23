package org.graphiks.kanvas.gpu.plan

import java.util.Collections
import org.graphiks.kanvas.render.ir.*

public enum class RuntimeEffectColorContractV1 { LINEAR_PREMUL, SRGB_STRAIGHT }
public enum class RuntimeEffectSemanticKindV1 { SHADER_CHILD_OPACITY, IMAGE_OPACITY }

public class RuntimeEffectSemanticEntryV1 internal constructor(
    public val descriptor: RuntimeEffectDescriptor,
    public val semanticKind: RuntimeEffectSemanticKindV1,
    public val numericContractId: String,
    public val numericGraph: NumericOperationGraphV1,
    public val cpuEvaluator: RuntimeEffectCpuEvaluatorV1,
    public val graphLimits: GraphLimits = GraphLimits(),
    public val frameLimits: MaterialFrameLimits = MaterialFrameLimits(),
) {
    public val inputColorContract: RuntimeEffectColorContractV1 = RuntimeEffectColorContractV1.LINEAR_PREMUL
    public val outputColorContract: RuntimeEffectColorContractV1 = RuntimeEffectColorContractV1.LINEAR_PREMUL
    init {
        require(descriptor.semanticVersionI32 > 0 && descriptor.legacyV0 == null) { W5hPlanDiagnostics.Descriptor }
        require(descriptor.abiHash.matches(Regex("[0-9a-f]{64}")) && descriptor.abiHash == descriptor.recomputeAbiHash()) { W5hPlanDiagnostics.AbiMismatch }
        require(numericGraph is NumericOperationGraphV1.RuntimeChildOpacity && numericGraph.contractId == "WgslFloatEnvelopeV1") {
            W5hPlanDiagnostics.CpuNumeric
        }
        when (semanticKind) {
            RuntimeEffectSemanticKindV1.SHADER_CHILD_OPACITY -> {
                require(numericContractId == "kanvas.runtime.child-opacity.numeric-v1" && cpuEvaluator === ChildOpacityCpuEvaluatorV1 &&
                    cpuEvaluator.id == "kanvas.runtime.child-opacity.cpu-v1" && cpuEvaluator.evaluatorVersionI32 == 1)
                require(descriptor.id.value == "kanvas.runtime.child-opacity" && descriptor.semanticVersionI32 == 1 && descriptor.abi == RuntimeEffectAbi.SHADER)
                require(descriptor.childSlots == listOf(RuntimeChildSlotV2("child", RuntimeChildType.SHADER, false)))
            }
            RuntimeEffectSemanticKindV1.IMAGE_OPACITY -> {
                require(numericContractId == "kanvas.runtime.image-opacity.numeric-v1" && cpuEvaluator === ImageOpacityCpuEvaluatorV1 &&
                    cpuEvaluator.id == "kanvas.runtime.image-opacity.cpu-v1" && cpuEvaluator.evaluatorVersionI32 == 1)
                require(descriptor.id.value == "kanvas.runtime.image-opacity" && descriptor.semanticVersionI32 == 1 && descriptor.abi == RuntimeEffectAbi.IMAGE_FILTER)
                require(descriptor.childSlots == listOf(RuntimeChildSlotV2("input", RuntimeChildType.IMAGE_FILTER, true)))
            }
        }
        require(descriptor.uniformBlock.slots == listOf(RuntimeUniformSlotV2("alpha", RuntimeUniformType.FLOAT, 0, 4, 4, 1, 0)) &&
            descriptor.uniformBlock.sizeBytesI32 == 16 && descriptor.logicalResources.isEmpty())
        require(graphLimits.maxDepth >= 2 && graphLimits.maxNodes >= 2)
    }
}

public class RuntimeEffectSemanticCatalogSnapshot private constructor(entries: Collection<RuntimeEffectSemanticEntryV1>) {
    private data class Key(val id: RuntimeEffectId, val semanticVersionI32: Int, val abiHash: String)
    private val values: Map<Key, RuntimeEffectSemanticEntryV1>
    init {
        val exact = LinkedHashMap<Key, RuntimeEffectSemanticEntryV1>()
        val versions = HashMap<Pair<RuntimeEffectId, Int>, String>()
        entries.forEach { entry ->
            val d = entry.descriptor
            require(d.recomputeAbiHash() == d.abiHash) { W5hPlanDiagnostics.AbiMismatch }
            val previousHash = versions.putIfAbsent(d.id to d.semanticVersionI32, d.abiHash)
            require(previousHash == null || previousHash == d.abiHash) { W5hPlanDiagnostics.AbiMismatch }
            val previous = exact.putIfAbsent(Key(d.id, d.semanticVersionI32, d.abiHash), entry)
            require(previous == null || previous.descriptor == d && previous.semanticKind == entry.semanticKind && previous.numericContractId == entry.numericContractId &&
                previous.cpuEvaluator.id == entry.cpuEvaluator.id && previous.cpuEvaluator.evaluatorVersionI32 == entry.cpuEvaluator.evaluatorVersionI32 &&
                previous.graphLimits == entry.graphLimits && previous.frameLimits == entry.frameLimits &&
                (previous.numericGraph as NumericOperationGraphV1.RuntimeChildOpacity).colorGraph.canonicalIdentity ==
                (entry.numericGraph as NumericOperationGraphV1.RuntimeChildOpacity).colorGraph.canonicalIdentity) { W5hPlanDiagnostics.Descriptor }
        }
        values = Collections.unmodifiableMap(exact)
    }
    public fun find(id: RuntimeEffectId, semanticVersionI32: Int, abiHash: String): RuntimeEffectSemanticEntryV1? {
        if (semanticVersionI32 <= 0 || !abiHash.matches(Regex("[0-9a-f]{64}"))) return null
        return values[Key(id, semanticVersionI32, abiHash)]?.takeIf { it.descriptor.recomputeAbiHash() == abiHash }
    }
    public fun builtinDescriptor(id: String, semanticVersionI32: Int): RuntimeEffectDescriptor? =
        values.values.firstOrNull { it.descriptor.id.value == id && it.descriptor.semanticVersionI32 == semanticVersionI32 }?.descriptor
    internal companion object {
        /** Compatibility compiler scope: no registered semantic entry can be admitted. */
        val Unbound: RuntimeEffectSemanticCatalogSnapshot = RuntimeEffectSemanticCatalogSnapshot(emptyList())
        fun of(entries: Collection<RuntimeEffectSemanticEntryV1>): RuntimeEffectSemanticCatalogSnapshot = RuntimeEffectSemanticCatalogSnapshot(entries)
    }
}

public object RuntimeEffectSemanticCatalog {
    private val builtin = RuntimeEffectSemanticCatalogSnapshot.of(listOf(RuntimeEffectSemanticEntryV1(
        RuntimeEffectDescriptor.of(RuntimeEffectId("kanvas.runtime.child-opacity"), RuntimeEffectAbi.SHADER, 1,
            RuntimeUniformBlockV1.of(listOf(RuntimeUniformSlotV2("alpha", RuntimeUniformType.FLOAT, 0, 4, 4, 1, 0)), 16),
            listOf(RuntimeChildSlotV2("child", RuntimeChildType.SHADER, false))),
        RuntimeEffectSemanticKindV1.SHADER_CHILD_OPACITY, "kanvas.runtime.child-opacity.numeric-v1",
        NumericOperationGraphV1.RuntimeChildOpacity(), ChildOpacityCpuEvaluatorV1,
    ), RuntimeEffectSemanticEntryV1(
        RuntimeEffectDescriptor.of(RuntimeEffectId("kanvas.runtime.image-opacity"), RuntimeEffectAbi.IMAGE_FILTER, 1,
            RuntimeUniformBlockV1.of(listOf(RuntimeUniformSlotV2("alpha", RuntimeUniformType.FLOAT, 0, 4, 4, 1, 0)), 16),
            listOf(RuntimeChildSlotV2("input", RuntimeChildType.IMAGE_FILTER, true))),
        RuntimeEffectSemanticKindV1.IMAGE_OPACITY, "kanvas.runtime.image-opacity.numeric-v1",
        NumericOperationGraphV1.RuntimeChildOpacity(), ImageOpacityCpuEvaluatorV1,
    )))
    public fun builtinSnapshot(): RuntimeEffectSemanticCatalogSnapshot = builtin
}
