package org.graphiks.kanvas.gpu.renderer.materials.contracts

import org.graphiks.kanvas.gpu.renderer.collections.ExactUtf16CanonicalIdentityDigestEncoder

/** Material-owned exact identity used by frame inventories. */
class GPUPreparedMaterialFrameIdentity internal constructor(
    val bucketKey: String,
)

/** Authenticated immutable material plus the sole frame/semantic identity derived from it. */
class GPUPreparedMaterialFrameSnapshot internal constructor(
    val program: GPUPreparedMaterialProgram,
    val identity: GPUPreparedMaterialFrameIdentity,
)

object GPUPreparedMaterialFrameIdentityAuthority {
    fun identity(program: GPUPreparedMaterialProgram): GPUPreparedMaterialFrameIdentity =
        authenticate(program).identity

    fun authenticate(program: GPUPreparedMaterialProgram): GPUPreparedMaterialFrameSnapshot {
        val snapshot = program.authenticatedSnapshot()
        return GPUPreparedMaterialFrameSnapshot(
            program = snapshot,
            identity = GPUPreparedMaterialFrameIdentity(snapshot.exactFrameBucketKey()),
        )
    }

    @JvmSynthetic
    internal fun identity(
        program: GPUPreparedMaterialProgram,
        bucketKeyOverride: String?,
    ): GPUPreparedMaterialFrameIdentity = if (bucketKeyOverride != null) {
        GPUPreparedMaterialFrameIdentity(bucketKeyOverride)
    } else {
        identity(program)
    }

    fun exactlyMatches(
        left: GPUPreparedMaterialProgram,
        right: GPUPreparedMaterialProgram,
    ): Boolean = left.exactFrameMatches(right)

    fun authenticates(snapshot: GPUPreparedMaterialFrameSnapshot): Boolean {
        val expected = authenticate(snapshot.program)
        return snapshot.identity.bucketKey == expected.identity.bucketKey &&
            exactlyMatches(snapshot.program, expected.program)
    }
}

private fun GPUPreparedMaterialProgram.exactFrameBucketKey(): String {
    val fragment = composableFragment
    val encoder = ExactUtf16CanonicalIdentityDigestEncoder(
        "prepared-material-frame-identity-v2-utf16-code-units",
    )
        .text("materialKey", materialKey)
        .text("wgslSource", wgslSource)
        .text("entryPoint", entryPoint)
        .text("fragment.declarationsWgsl", fragment.declarationsWgsl)
        .text("fragment.evaluationFunctionWgsl", fragment.evaluationFunctionWgsl)
        .text("fragment.evaluationFunction", fragment.evaluationFunction)
        .boolean("fragment.uniformBinding.present", fragment.uniformBinding != null)
        .text("fragment.colorContract", fragment.colorContract.name)
        .text("fragment.coordinateContract", fragment.coordinateContract.name)
        .text("fragment.fragmentHash", fragment.fragmentHash)
        .text("fragment.abiHash", fragment.abiHash)
        .bytes("uniformBytes", uniformBytes.map(Int::toByte).toByteArray())
        .floatBits("paintAlpha", paintAlpha)
        .text("sourceKind", sourceKind.name)
        .text("preCoverageSourceAlpha", preCoverageSourceAlpha.name)
        .text("abiHash", abiHash)
    fragment.uniformBinding?.let { binding ->
        encoder
            .int("fragment.uniformBinding.group", binding.group)
            .int("fragment.uniformBinding.binding", binding.binding)
            .int("fragment.uniformBinding.minBindingSizeBytes", binding.minBindingSizeBytes)
    }
    encoder.int("fragment.sampledBindings.count", fragment.sampledBindings.size)
    fragment.sampledBindings.forEachIndexed { index, binding ->
        encoder
            .int("fragment.sampledBinding[$index].resourceIndex", binding.resourceIndex)
            .int("fragment.sampledBinding[$index].textureGroup", binding.textureGroup)
            .int("fragment.sampledBinding[$index].textureBinding", binding.textureBinding)
            .int("fragment.sampledBinding[$index].samplerGroup", binding.samplerGroup)
            .int("fragment.sampledBinding[$index].samplerBinding", binding.samplerBinding)
    }
    encoder.int("sampledResources.count", sampledResources.size)
    sampledResources.forEachIndexed { index, resource ->
        encoder
            .text("sampledResource[$index].resourceKey", resource.resourceKey)
            .int("sampledResource[$index].width", resource.width)
            .int("sampledResource[$index].height", resource.height)
            .text("sampledResource[$index].samplingFilterMode", resource.samplingFilterMode)
            .boolean("sampledResource[$index].alphaOnly", resource.alphaOnly)
            .text("sampledResource[$index].contentHash", resource.contentHash)
            .bytes("sampledResource[$index].rgba8Bytes", resource.rgba8Bytes())
    }
    encoder.int("childPrograms.count", childPrograms.size)
    childPrograms.forEachIndexed { index, child ->
        val prefix = "childProgram[$index]"
        encoder
            .text("$prefix.name", child.name)
            .text("$prefix.role", child.role.name)
            .text("$prefix.programKey", child.programKey)
            .text("$prefix.abiHash", child.abiHash)
            .bytes("$prefix.uniformBytes", child.uniformBytes.map(Int::toByte).toByteArray())
            .texts("$prefix.resourceFacts", child.resourceFacts)
            .text("$prefix.wgslSource", child.wgslSource)
            .text("$prefix.evaluationFunction", child.evaluationFunction)
        child.cpuProgram.appendCanonicalIdentity(encoder, "$prefix.cpuProgram")
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

private fun GPUPreparedRuntimeEffectChildCpuProgram.appendCanonicalIdentity(
    encoder: ExactUtf16CanonicalIdentityDigestEncoder,
    prefix: String,
) {
    when (this) {
        is GPUPreparedRuntimeEffectChildCpuProgram.Shader -> encoder
            .text("$prefix.kind", "shader")
            .text("$prefix.materialKey", materialKey)
        is GPUPreparedRuntimeEffectChildCpuProgram.Matrix -> {
            encoder.text("$prefix.kind", "matrix").int("$prefix.values.count", values.size)
            values.forEachIndexed { index, value -> encoder.floatBits("$prefix.values[$index]", value) }
        }
        is GPUPreparedRuntimeEffectChildCpuProgram.BlendConstant -> {
            encoder
                .text("$prefix.kind", "blend-constant")
                .text("$prefix.modeLabel", modeLabel)
                .int("$prefix.sourcePremul.count", sourcePremul.size)
            sourcePremul.forEachIndexed { index, value ->
                encoder.floatBits("$prefix.sourcePremul[$index]", value)
            }
        }
        is GPUPreparedRuntimeEffectChildCpuProgram.Compose -> {
            encoder.text("$prefix.kind", "compose")
            inner.appendCanonicalIdentity(encoder, "$prefix.inner")
            outer.appendCanonicalIdentity(encoder, "$prefix.outer")
        }
        is GPUPreparedRuntimeEffectChildCpuProgram.ModeBlender -> encoder
            .text("$prefix.kind", "mode-blender")
            .text("$prefix.modeLabel", modeLabel)
    }
}
