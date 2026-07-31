package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedRuntimeEffectChildCpuProgram

/** Material-owned exact identity used by frame inventories. */
class GPUPreparedMaterialFrameIdentity internal constructor(
    val bucketKey: String,
)

object GPUPreparedMaterialFrameIdentityAuthority {
    fun identity(program: GPUPreparedMaterialProgram): GPUPreparedMaterialFrameIdentity =
        identity(program, null)

    @JvmSynthetic
    internal fun identity(
        program: GPUPreparedMaterialProgram,
        bucketKeyOverride: String?,
    ): GPUPreparedMaterialFrameIdentity = GPUPreparedMaterialFrameIdentity(
        bucketKey = bucketKeyOverride ?: program.exactFrameBucketKey(),
    )

    fun exactlyMatches(
        left: GPUPreparedMaterialProgram,
        right: GPUPreparedMaterialProgram,
    ): Boolean = left.exactFrameMatches(right)
}

private fun GPUPreparedMaterialProgram.exactFrameBucketKey(): String {
    val fragment = composableFragment
    val encoder = CanonicalIdentityEncoder("prepared-material-frame-identity-v1")
        .text("materialKey", materialKey)
        .text("wgslSource", wgslSource)
        .text("entryPoint", entryPoint)
        .text("fragment.declarationsWgsl", fragment.declarationsWgsl)
        .text("fragment.evaluationFunctionWgsl", fragment.evaluationFunctionWgsl)
        .text("fragment.evaluationFunction", fragment.evaluationFunction)
        .text("fragment.uniformBinding", fragment.uniformBinding.toString())
        .texts("fragment.sampledBindings", fragment.sampledBindings.map(Any::toString))
        .text("fragment.colorContract", fragment.colorContract.name)
        .text("fragment.coordinateContract", fragment.coordinateContract.name)
        .text("fragment.fragmentHash", fragment.fragmentHash)
        .text("fragment.abiHash", fragment.abiHash)
        .bytes("uniformBytes", uniformBytes.map(Int::toByte).toByteArray())
        .texts("sampledResources", sampledResources.mapIndexed { index, resource ->
            "$index:${resource.resourceKey}:${resource.width}x${resource.height}:" +
                "${resource.samplingFilterMode}:${resource.alphaOnly}:${resource.contentHash}"
        })
        .texts("childPrograms", childPrograms.flatMapIndexed { index, child ->
            listOf(
                "$index:${child.name}:${child.role}:${child.programKey}:${child.abiHash}",
                "$index:${child.uniformBytes.joinToString(",")}",
                "$index:${child.resourceFacts.joinToString("\u0000")}",
                "$index:${child.wgslSource}:${child.evaluationFunction}",
                "$index:${child.cpuProgram.canonicalIdentityFacts().joinToString("\u0000")}",
            )
        })
        .floatBits("paintAlpha", paintAlpha)
        .text("sourceKind", sourceKind.name)
        .text("preCoverageSourceAlpha", preCoverageSourceAlpha.name)
        .text("abiHash", abiHash)
    sampledResources.forEachIndexed { index, resource ->
        encoder.bytes("sampledResource[$index].rgba8Bytes", resource.rgba8Bytes())
    }
    return encoder.digestIdentity()
}

private fun GPUPreparedMaterialProgram.exactFrameMatches(
    other: GPUPreparedMaterialProgram,
): Boolean {
    val leftFragment = composableFragment
    val rightFragment = other.composableFragment
    return materialKey == other.materialKey &&
        wgslSource == other.wgslSource &&
        entryPoint == other.entryPoint &&
        leftFragment.declarationsWgsl == rightFragment.declarationsWgsl &&
        leftFragment.evaluationFunctionWgsl == rightFragment.evaluationFunctionWgsl &&
        leftFragment.evaluationFunction == rightFragment.evaluationFunction &&
        leftFragment.uniformBinding == rightFragment.uniformBinding &&
        leftFragment.sampledBindings == rightFragment.sampledBindings &&
        leftFragment.colorContract == rightFragment.colorContract &&
        leftFragment.coordinateContract == rightFragment.coordinateContract &&
        leftFragment.fragmentHash == rightFragment.fragmentHash &&
        leftFragment.abiHash == rightFragment.abiHash &&
        uniformBytes == other.uniformBytes &&
        paintAlpha.toRawBits() == other.paintAlpha.toRawBits() &&
        sourceKind == other.sourceKind &&
        preCoverageSourceAlpha == other.preCoverageSourceAlpha &&
        abiHash == other.abiHash &&
        sampledResources.size == other.sampledResources.size &&
        sampledResources.zip(other.sampledResources).all { (left, right) ->
            left.resourceKey == right.resourceKey &&
                left.width == right.width && left.height == right.height &&
                left.samplingFilterMode == right.samplingFilterMode &&
                left.alphaOnly == right.alphaOnly &&
                left.contentHash == right.contentHash &&
                left.rgba8Bytes().contentEquals(right.rgba8Bytes())
        } &&
        childPrograms.size == other.childPrograms.size &&
        childPrograms.zip(other.childPrograms).all { (left, right) ->
            left.name == right.name && left.role == right.role &&
                left.programKey == right.programKey && left.abiHash == right.abiHash &&
                left.uniformBytes == right.uniformBytes && left.resourceFacts == right.resourceFacts &&
                left.wgslSource == right.wgslSource &&
                left.evaluationFunction == right.evaluationFunction &&
                left.cpuProgram == right.cpuProgram
        }
}

private fun GPUPreparedRuntimeEffectChildCpuProgram.canonicalIdentityFacts(): List<String> = when (this) {
    is GPUPreparedRuntimeEffectChildCpuProgram.Shader -> listOf("shader", materialKey)
    is GPUPreparedRuntimeEffectChildCpuProgram.Matrix ->
        listOf("matrix") + values.map { value -> value.toRawBits().toString() }
    is GPUPreparedRuntimeEffectChildCpuProgram.BlendConstant ->
        listOf("blend-constant", modeLabel) + sourcePremul.map { value -> value.toRawBits().toString() }
    is GPUPreparedRuntimeEffectChildCpuProgram.Compose ->
        listOf("compose", "inner") + inner.canonicalIdentityFacts() +
            listOf("outer") + outer.canonicalIdentityFacts()
    is GPUPreparedRuntimeEffectChildCpuProgram.ModeBlender -> listOf("mode-blender", modeLabel)
}
