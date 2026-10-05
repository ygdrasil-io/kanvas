package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.*

internal class CapturedRuntimeResourceV1(val ownerNodeIndexI32: Int, val descriptor: RuntimeEffectDescriptor,
    val slot: RuntimeLogicalResourceSlotV1, val binding: RuntimeEffectResourceBindingV1,
    val resource: ComposedBindingLayoutV1.Resource)

/** Identity-based reservation: equal contents are not evidence of a shared captured owner. */
internal class RuntimeResourceOwnerIndexV1<T> {
    private val owners=java.util.IdentityHashMap<Any,MutableMap<String,T>>()
    fun getOrPut(captured: CapturedRuntimeResourceV1,create: ()->T): T {
        val owner=when(val binding=captured.binding) {
            is RuntimeEffectResourceBindingV1.StorageRead -> binding.bytes
            is RuntimeEffectResourceBindingV1.SampledTexture -> binding.image
            is RuntimeEffectResourceBindingV1.Sampler -> binding
        }
        val family=if(captured.binding is RuntimeEffectResourceBindingV1.StorageRead)
            "${captured.descriptor.abiHash}:${captured.slot.logicalSlotI32}" else when(captured.binding) {
                is RuntimeEffectResourceBindingV1.StorageRead -> error("handled above")
                is RuntimeEffectResourceBindingV1.SampledTexture -> "texture"
                is RuntimeEffectResourceBindingV1.Sampler -> "sampler"
            }
        return owners.getOrPut(owner) { mutableMapOf() }.getOrPut(family,create)
    }
}

/** Exact logical value, physical row and frame-owned cache request remain one authority. */
public class RuntimeEffectResourceReferenceV1 internal constructor(
    internal val captured: CapturedRuntimeResourceV1,
    public val cacheRequest: PlanCacheResourceRequest,
    public val imageUpload: ImageUploadPlanV1?,
) {
    public val ownerNodeIndexI32: Int get() = captured.ownerNodeIndexI32
    public val descriptorSlot: RuntimeLogicalResourceSlotV1 get() = captured.slot
    public val capturedBinding: RuntimeEffectResourceBindingV1 get() = captured.binding
    public val resource: ComposedBindingLayoutV1.Resource get() = captured.resource
    public val groupI32: Int get() = resource.groupI32
    public val bindingI32: Int get() = resource.bindingI32
    init {
        require(resource.ownerNodeIndexI32 == ownerNodeIndexI32 && resource.logicalSlotI32 == descriptorSlot.logicalSlotI32)
        require(when (val binding = capturedBinding) {
            is RuntimeEffectResourceBindingV1.StorageRead -> cacheRequest is PlanCacheResourceRequest.Storage &&
                cacheRequest.captured === binding.bytes && cacheRequest.abiHash == captured.descriptor.abiHash &&
                cacheRequest.logicalSlotI32 == descriptorSlot.logicalSlotI32 && resource.buffer?.storageKind == ComposedBindingLayoutV1.StorageKind.RUNTIME_READ
            is RuntimeEffectResourceBindingV1.SampledTexture -> imageUpload?.pixelsOwner === binding.image &&
                imageUpload.cacheRequest === cacheRequest && resource.texture != null
            is RuntimeEffectResourceBindingV1.Sampler -> cacheRequest is PlanCacheResourceRequest.Sampler &&
                cacheRequest.type == binding.type && resource.sampler != null
        }) { W5hPlanDiagnostics.Descriptor }
    }
}

/** The renderer verifies this sealed expectation without receiving the semantic catalogue. */
public data class RuntimeEffectExpectationV1(
    val id: RuntimeEffectId,
    val semanticVersionI32: Int,
    val abiHash: String,
    val numericContractId: String,
    val cpuEvaluatorId: String,
    val cpuEvaluatorVersionI32: Int,
) {
    internal companion object {
        fun from(entry: RuntimeEffectSemanticEntryV1): RuntimeEffectExpectationV1 = RuntimeEffectExpectationV1(
            entry.descriptor.id,entry.descriptor.semanticVersionI32,entry.descriptor.abiHash,
            entry.numericContractId,entry.cpuEvaluator.id,entry.cpuEvaluator.evaluatorVersionI32)

        fun validate(node: MaterialNode.RuntimeEffect, catalog: RuntimeEffectSemanticCatalogSnapshot): RuntimeEffectSemanticEntryV1 {
            val descriptor = node.descriptor
            require(descriptor.semanticVersionI32 > 0) { W5hPlanDiagnostics.UnregisteredSemantics }
            val entry = requireNotNull(catalog.find(descriptor.id,descriptor.semanticVersionI32,descriptor.abiHash)) {
                W5hPlanDiagnostics.UnregisteredSemantics
            }
            require(descriptor.recomputeAbiHash() == descriptor.abiHash &&
                entry.descriptor.recomputeAbiHash() == descriptor.abiHash) { W5hPlanDiagnostics.AbiMismatch }
            require(descriptor == entry.descriptor && descriptor.abi == RuntimeEffectAbi.SHADER &&
                descriptor.legacyV0 == null) { W5hPlanDiagnostics.Descriptor }
            require(node.uniforms().keys.toList() == descriptor.uniformBlock.slots.map { it.name }) { W5hPlanDiagnostics.CpuUniforms }
            require(node.map { it.name } == descriptor.childSlots.map { it.name } &&
                descriptor.childSlots.all { it.type == RuntimeChildType.SHADER && !it.nullable }) { W5hPlanDiagnostics.CpuChildren }
            node.resources.requireMatches(descriptor)
            require(entry.inputColorContract == RuntimeEffectColorContractV1.LINEAR_PREMUL &&
                entry.outputColorContract == RuntimeEffectColorContractV1.LINEAR_PREMUL &&
                entry.numericGraph.contractId == "WgslFloatEnvelopeV1") { W5hPlanDiagnostics.CpuNumeric }
            val numeric = entry.numericGraph as? NumericOperationGraphV1.RuntimeChildOpacity
                ?: throw IllegalArgumentException(W5hPlanDiagnostics.CpuNumeric)
            val alpha = node.uniforms()["alpha"] as? RuntimeUniformValue.F1
                ?: throw IllegalArgumentException(W5hPlanDiagnostics.CpuUniforms)
            require(alpha.value.isFinite() && alpha.value in numeric.alphaMinF32..numeric.alphaMaxF32) { W5hPlanDiagnostics.CpuUniforms }
            return entry
        }
    }
}

/** V6 extends the same V5 DAG, owner table, numeric graph and source proof. */
public class ComposedMaterialProgramV6 internal constructor(
    internal val evaluation: MaterialEvaluationDagV5,
    operationGraph: ColorOperationGraphV1,
    public val layout: ComposedBindingLayoutV1,
    expectations: List<RuntimeNodeExpectation>,
) : ComposedMaterialProgramV5(MaterialProgramPlanId(identity(evaluation,operationGraph,layout,expectations)),operationGraph) {
    public data class RuntimeNodeExpectation(val nodeIndexI32: Int, val expectation: RuntimeEffectExpectationV1)
    public val runtimeExpectations: List<RuntimeNodeExpectation> = immutableList(expectations)
    public val programIdentity: String get() = structuralId.value
    override val versionI32: Int = 6

    private companion object {
        fun identity(dag: MaterialEvaluationDagV5, graph: ColorOperationGraphV1,
            layout: ComposedBindingLayoutV1, expectations: List<RuntimeNodeExpectation>): String =
            CanonicalHashBytesV1("kanvas-material-program-v6").apply {
                i32(6).i32(dag.root.indexI32)
                list(dag.entries.withIndex().toList()) { (index,entry) ->
                    i32(index).i32(entry.ownerNodeIndexI32)
                    list(entry.children) { i32(it.indexI32) }
                    text(entry.program.structuralId.value)
                }
                val owners = dag.entries.map { it.ownerNodeIndexI32 }.distinct().sorted()
                list(owners) { owner ->
                    i32(owner)
                    list(dag.entries.indices.filter { dag.entries[it].ownerNodeIndexI32 == owner }) { i32(it) }
                }
                text(graph.canonicalIdentity)
                list(expectations) { row ->
                    i32(row.nodeIndexI32)
                    with(row.expectation) {
                        text(id.value).i32(semanticVersionI32).text(abiHash).text(numericContractId)
                            .text(cpuEvaluatorId).i32(cpuEvaluatorVersionI32)
                    }
                }
                text(layout.composedBindingLayoutHash)
            }.sha256Hex()
    }
}

public class ComposedMaterialBindingV6 internal constructor(definition: PreparedComposedSourceV5, sourceProof: ColorSourceProofV1) :
    ComposedMaterialBindingV5(definition,sourceProof) {
    override val versionI32: Int = 6
}
