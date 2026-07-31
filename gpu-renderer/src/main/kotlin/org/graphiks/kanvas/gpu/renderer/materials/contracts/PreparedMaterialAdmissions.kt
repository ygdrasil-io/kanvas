package org.graphiks.kanvas.gpu.renderer.materials.contracts

import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.collections.CanonicalIdentityDigestEncoder
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.state.GPUSourceAlphaClassification
import org.graphiks.kanvas.gpu.renderer.wgsl.WgslReflectionReport

/**
 * Opaque preimage issued only after the composable WGSL gate has admitted its
 * typed reflection report.
 */
internal class GPUPreparedMaterialFragmentAdmission private constructor(
    val declarationsWgsl: String,
    val evaluationFunctionWgsl: String,
    val uniformBinding: GPUPreparedMaterialUniformBinding?,
    sampledBindings: List<GPUPreparedMaterialSampledBinding>,
    reflectedAbiFacts: List<String>,
) {
    val sampledBindings: List<GPUPreparedMaterialSampledBinding> =
        immutableList(sampledBindings)
    private val reflectedAbiFacts: List<String> = immutableList(reflectedAbiFacts)

    fun fragmentHash(): String =
        sha256Hex((declarationsWgsl + "\n\n" + evaluationFunctionWgsl).encodeToByteArray())

    fun fragmentAbiHash(): String =
        CanonicalIdentityDigestEncoder("prepared-material-fragment-abi-v1")
            .text("evaluationFunction", MATERIAL_EVALUATION_FUNCTION)
            .text(
                "colorContract",
                GPUPreparedMaterialColorContract.LinearPremultipliedRgba.name,
            )
            .text(
                "coordinateContract",
                GPUPreparedMaterialCoordinateContract.LocalPosition2D.name,
            )
            .texts(
                "uniformBinding",
                listOfNotNull(
                    uniformBinding?.let {
                        "${it.group}:${it.binding}:${it.minBindingSizeBytes}"
                    },
                ),
            )
            .texts(
                "sampledBindings",
                sampledBindings.map {
                    "${it.resourceIndex}:${it.textureGroup}:${it.textureBinding}:" +
                        "${it.samplerGroup}:${it.samplerBinding}"
                },
            )
            .texts("reflectedAbiFacts", reflectedAbiFacts)
            .digestIdentity()

    companion object {
        @JvmSynthetic
        internal fun issueValidated(
            declarationsWgsl: String,
            evaluationFunctionWgsl: String,
            uniformBinding: GPUPreparedMaterialUniformBinding?,
            sampledBindings: List<GPUPreparedMaterialSampledBinding>,
            reflectedAbi: WgslReflectionReport,
        ): GPUPreparedMaterialFragmentAdmission {
            val fragmentSource = declarationsWgsl + "\n\n" + evaluationFunctionWgsl
            require(reflectedAbi.sourceId == sha256Hex(fragmentSource.encodeToByteArray())) {
                "Prepared material fragment reflection must identify the admitted WGSL"
            }
            require(
                reflectedAbi.validation.success &&
                    reflectedAbi.unsupportedFeatures.isEmpty(),
            ) {
                "Prepared material fragment reflection must be admitted"
            }
            return GPUPreparedMaterialFragmentAdmission(
                declarationsWgsl = declarationsWgsl,
                evaluationFunctionWgsl = evaluationFunctionWgsl,
                uniformBinding = uniformBinding,
                sampledBindings = sampledBindings,
                reflectedAbiFacts = reflectedAbi.fragmentAbiFacts(),
            )
        }
    }
}

/**
 * Opaque program-ABI preimage issued only after final-module reflection has
 * passed the registered ABI gate.
 */
internal class GPUPreparedMaterialProgramAdmission private constructor(
    val fragmentAdmission: GPUPreparedMaterialFragmentAdmission,
    private val sourceKind: GPUMaterialSourceKind,
    private val sourceHash: String,
    private val entryPoint: String,
    private val uniformLayoutHash: String,
    private val uniformByteCount: Int,
    private val uniformBytesHash: String,
    private val sampledResourceCount: Int,
    sampledResourceFacts: List<String>,
    private val childProgramCount: Int,
    childProgramKeyFacts: List<String>,
    childProgramAbiFacts: List<String>,
    private val paintAlpha: Float,
    private val preCoverageSourceAlpha: GPUSourceAlphaClassification,
    private val capabilityClass: String,
    private val targetFormatClass: String,
    private val dictionaryVersion: String,
    keyFacts: List<String>,
    registeredAbiFacts: List<String>,
    reflectedAbiFacts: List<String>,
) {
    private val sampledResourceFacts: List<String> = immutableList(sampledResourceFacts)
    private val childProgramKeyFacts: List<String> = immutableList(childProgramKeyFacts)
    private val childProgramAbiFacts: List<String> = immutableList(childProgramAbiFacts)
    private val keyFacts: List<String> = immutableList(keyFacts)
    private val registeredAbiFacts: List<String> = immutableList(registeredAbiFacts)
    private val reflectedAbiFacts: List<String> = immutableList(reflectedAbiFacts)

    fun materialKey(): String =
        "material:prepared:${sourceKind.name.lowercase()}:" +
            CanonicalIdentityDigestEncoder("prepared-material-key-v2")
                .text("sourceKind", sourceKind.name)
                .text("sourceHash", sourceHash)
                .text("entryPoint", entryPoint)
                .text("uniformLayout", uniformLayoutHash)
                .text("capabilityClass", capabilityClass)
                .text("targetFormatClass", targetFormatClass)
                .text("dictionaryVersion", dictionaryVersion)
                .text("uniformBytesHash", uniformBytesHash)
                .floatBits("paintAlpha", paintAlpha)
                .text("preCoverageSourceAlpha", preCoverageSourceAlpha.name)
                .texts("keyFacts", keyFacts)
                .texts("sampledResourceFacts", sampledResourceFacts)
                .texts("childPrograms", childProgramKeyFacts)
                .digestHex()

    fun requireMatches(
        materialKey: String,
        wgslSource: String,
        entryPoint: String,
        sourceKind: GPUMaterialSourceKind,
        uniformBytes: List<Int>,
        sampledResources: List<GPUPreparedMaterialSampledResource>,
        childPrograms: List<GPUPreparedRuntimeEffectChildProgram>,
        paintAlpha: Float,
        preCoverageSourceAlpha: GPUSourceAlphaClassification,
    ) {
        require(materialKey == materialKey()) {
            "Prepared material key must match its admitted program facts"
        }
        require(sha256Hex(wgslSource.encodeToByteArray()) == sourceHash) {
            "Prepared material WGSL must match its admitted program facts"
        }
        require(entryPoint == this.entryPoint) {
            "Prepared material entry point must match its admitted program facts"
        }
        require(sourceKind == this.sourceKind) {
            "Prepared material source kind must match its admitted program facts"
        }
        require(uniformBytes.size == uniformByteCount) {
            "Prepared material uniforms must match its admitted program facts"
        }
        require(sha256Hex(uniformBytes.toUnsignedByteArray()) == uniformBytesHash) {
            "Prepared material uniform content must match its admitted program facts"
        }
        require(sampledResources.size == sampledResourceCount) {
            "Prepared material resources must match its admitted program facts"
        }
        require(sampledResources.identityFacts() == sampledResourceFacts) {
            "Prepared material resource content must match its admitted program facts"
        }
        require(childPrograms.size == childProgramCount) {
            "Prepared runtime-effect child count must match its admitted program facts"
        }
        require(childPrograms.keyFacts() == childProgramKeyFacts) {
            "Prepared runtime-effect children must match their admitted program facts"
        }
        require(paintAlpha.toRawBits() == this.paintAlpha.toRawBits()) {
            "Prepared material paint alpha must match its admitted program facts"
        }
        require(preCoverageSourceAlpha == this.preCoverageSourceAlpha) {
            "Prepared material pre-coverage source alpha must match its admitted program facts"
        }
    }

    fun programAbiHash(
        fragmentHash: String,
        fragmentAbiHash: String,
    ): String =
        CanonicalIdentityDigestEncoder("prepared-material-abi-v2")
            .text("sourceKind", sourceKind.name)
            .text("sourceHash", sourceHash)
            .text("entryPoint", entryPoint)
            .text("uniformLayout", uniformLayoutHash)
            .int("uniformByteCount", uniformByteCount)
            .int("sampledResourceCount", sampledResourceCount)
            .text("preCoverageSourceAlpha", preCoverageSourceAlpha.name)
            .text("fragmentHash", fragmentHash)
            .text("fragmentAbiHash", fragmentAbiHash)
            .texts("registeredAbiFacts", registeredAbiFacts)
            .texts("childPrograms", childProgramAbiFacts)
            .texts("reflectedAbiFacts", reflectedAbiFacts)
            .digestIdentity()

    companion object {
        @JvmSynthetic
        internal fun issueValidated(
            fragmentAdmission: GPUPreparedMaterialFragmentAdmission,
            wgslSource: String,
            entryPoint: String,
            sourceKind: GPUMaterialSourceKind,
            uniformLayoutHash: String,
            uniformBytes: List<Int>,
            sampledResources: List<GPUPreparedMaterialSampledResource>,
            childPrograms: List<GPUPreparedRuntimeEffectChildProgram> = emptyList(),
            paintAlpha: Float,
            preCoverageSourceAlpha: GPUSourceAlphaClassification,
            capabilityClass: String,
            targetFormatClass: String,
            dictionaryVersion: String,
            keyFacts: List<String>,
            registeredAbiFacts: List<String>,
            reflectedAbi: WgslReflectionReport,
        ): GPUPreparedMaterialProgramAdmission {
            val sourceHash = sha256Hex(wgslSource.encodeToByteArray())
            val uniformBytesHash = sha256Hex(uniformBytes.toUnsignedByteArray())
            require(reflectedAbi.sourceId == sourceHash) {
                "Prepared material reflection must identify the admitted WGSL"
            }
            require(
                reflectedAbi.validation.success &&
                    reflectedAbi.unsupportedFeatures.isEmpty(),
            ) {
                "Prepared material final reflection must be admitted"
            }
            return GPUPreparedMaterialProgramAdmission(
                fragmentAdmission = fragmentAdmission,
                sourceKind = sourceKind,
                sourceHash = sourceHash,
                entryPoint = entryPoint,
                uniformLayoutHash = uniformLayoutHash,
                uniformByteCount = uniformBytes.size,
                uniformBytesHash = uniformBytesHash,
                sampledResourceCount = sampledResources.size,
                sampledResourceFacts = sampledResources.identityFacts(),
                childProgramCount = childPrograms.size,
                childProgramKeyFacts = childPrograms.keyFacts(),
                childProgramAbiFacts = childPrograms.abiFacts(),
                paintAlpha = paintAlpha,
                preCoverageSourceAlpha = preCoverageSourceAlpha,
                capabilityClass = capabilityClass,
                targetFormatClass = targetFormatClass,
                dictionaryVersion = dictionaryVersion,
                keyFacts = keyFacts,
                registeredAbiFacts = registeredAbiFacts,
                reflectedAbiFacts = reflectedAbi.programAbiFacts(),
            )
        }
    }
}

private fun WgslReflectionReport.fragmentAbiFacts(): List<String> = buildList {
    bindings.sortedWith(compareBy({ it.group }, { it.binding }))
        .forEachIndexed { index, binding ->
            add(
                "binding[$index]=${binding.group}:${binding.binding}:${binding.name}:" +
                    "${binding.resourceKind}:${binding.access}:${binding.sampleType}:" +
                    "${binding.viewDimension}:${binding.storageFormat}:" +
                    "${binding.minBindingSize}",
            )
        }
    addLayoutFacts(layouts)
}

private fun WgslReflectionReport.programAbiFacts(): List<String> = buildList {
    entryPoints.sortedWith(compareBy({ it.name }, { it.stage }))
        .forEachIndexed { index, reflected ->
            add(
                "entry[$index]=${reflected.name}:${reflected.stage}:" +
                    "${reflected.workgroupSize}",
            )
        }
    bindings.sortedWith(compareBy({ it.group }, { it.binding }))
        .forEachIndexed { index, binding ->
            add(
                "binding[$index]=${binding.group}:${binding.binding}:${binding.name}:" +
                    "${binding.resourceKind}:${binding.access}:${binding.sampleType}:" +
                    "${binding.viewDimension}:${binding.storageFormat}:" +
                    "${binding.minBindingSize}",
            )
        }
    addLayoutFacts(layouts)
}

private fun MutableList<String>.addLayoutFacts(
    layouts: List<org.graphiks.kanvas.gpu.renderer.wgsl.WgslLayoutReflection>,
) {
    layouts.sortedWith(compareBy({ it.addressSpace }, { it.structName }))
        .forEachIndexed { layoutIndex, layout ->
            add(
                "layout[$layoutIndex]=${layout.structName}:${layout.addressSpace}:" +
                    "${layout.size}:${layout.alignment}",
            )
            layout.members.forEachIndexed { memberIndex, member ->
                add(
                    "layout[$layoutIndex].member[$memberIndex]=${member.name}:" +
                        "${member.type}:${member.offset}:${member.size}:" +
                        "${member.alignment}:${member.stride}",
                )
            }
        }
}

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun List<Int>.toUnsignedByteArray(): ByteArray =
    ByteArray(size) { index ->
        val value = this[index]
        require(value in 0..255) {
            "Prepared material uniforms must be unsigned bytes"
        }
        value.toByte()
    }

private fun List<GPUPreparedMaterialSampledResource>.identityFacts(): List<String> =
    flatMapIndexed { index, resource ->
        resource.identityFacts().map { fact -> "resource[$index].$fact" }
    }

private fun List<GPUPreparedRuntimeEffectChildProgram>.keyFacts(): List<String> =
    flatMapIndexed { index, child ->
        buildList {
            add("child[$index].name=${child.name}")
            add("child[$index].role=${child.role.name}")
            add("child[$index].programKey=${child.programKey}")
            add("child[$index].abiHash=${child.abiHash}")
            add("child[$index].uniformBytesHash=${sha256Hex(child.uniformBytes.toUnsignedByteArray())}")
            child.resourceFacts.forEachIndexed { factIndex, fact ->
                add("child[$index].resource[$factIndex]=$fact")
            }
        }
    }

private fun List<GPUPreparedRuntimeEffectChildProgram>.abiFacts(): List<String> =
    mapIndexed { index, child ->
        "child[$index]=${child.name}:${child.role.name}:${child.abiHash}"
    }

private const val MATERIAL_EVALUATION_FUNCTION = "kanvas_evaluate_material"
