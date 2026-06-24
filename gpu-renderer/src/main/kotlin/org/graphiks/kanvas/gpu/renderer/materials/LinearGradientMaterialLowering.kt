package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.wgsl.LinearGradientEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.LinearGradientSnippetSourceHash

object GPULinearGradientMaterialDictionary {
    const val DictionaryVersion: String = "material-dictionary:linear-gradient:v1"

    val LinearGradientSnippetID: WGSLSnippetID = WGSLSnippetID("material.linear_gradient.v1")

    const val LinearGradientMaterialLayoutHash: String = "layout:linear-gradient-material-block:v1"

    const val LinearGradientMaterialModuleSalt: String = "kanvas-gpu-renderer:linear-gradient-material:v1"

    fun create(): GPUMaterialDictionary =
        GPUMaterialDictionary(
            dictionaryVersion = DictionaryVersion,
            snippets = listOf(linearGradientSnippet()),
            rootSets = listOf(linearGradientRootSet()),
        )

    fun expandLinearGradientMaterialOrRefuse(
        materialKey: MaterialKey,
        dictionary: GPUMaterialDictionary,
    ): GPUMaterialAssemblyResult {
        val diagnostic = validateLinearGradientDictionary(dictionary)
        if (diagnostic != null) {
            return GPUMaterialAssemblyResult.Refused(diagnostic)
        }

        return GPUMaterialAssemblyResult.Accepted(
            GPUMaterialAssemblyPlan(
                programId = GPUMaterialProgramID("program:${materialKey.value}"),
                rootSet = dictionary.rootSets.single { LinearGradientSnippetID in it.snippetIds },
                snippetGraph = listOf(
                    WGSLSnippetNode(
                        snippetId = LinearGradientSnippetID,
                        children = emptyList(),
                        evaluationOrder = 0,
                    ),
                ),
                moduleSalt = LinearGradientMaterialModuleSalt,
            ),
        )
    }

    fun validateLinearGradientDictionary(
        dictionary: GPUMaterialDictionary,
    ): GPUMaterialSourceDiagnostic? {
        if (dictionary.dictionaryVersion != DictionaryVersion) {
            return GPUMaterialSourceDiagnostic(
                code = "unsupported.material.dictionary_version_mismatch",
                sourceKind = GPUMaterialSourceKind.Gradient,
                message = "Linear gradient material dictionary version ${dictionary.dictionaryVersion} does not match $DictionaryVersion",
                terminal = true,
            )
        }
        if (dictionary.snippets.none { it.snippetId == LinearGradientSnippetID }) {
            return GPUMaterialSourceDiagnostic(
                code = "unsupported.material.dictionary_missing_snippet",
                sourceKind = GPUMaterialSourceKind.Gradient,
                message = "Linear gradient material dictionary is missing snippet ${LinearGradientSnippetID.value}",
                terminal = true,
            )
        }
        if (dictionary.rootSets.none { LinearGradientSnippetID in it.snippetIds }) {
            return GPUMaterialSourceDiagnostic(
                code = "unsupported.material.dictionary_missing_root_set",
                sourceKind = GPUMaterialSourceKind.Gradient,
                message = "Linear gradient material dictionary is missing a root set for ${LinearGradientSnippetID.value}",
                terminal = true,
            )
        }
        return null
    }

    private fun linearGradientSnippet(): WGSLSnippet =
        WGSLSnippet(
            snippetId = LinearGradientSnippetID,
            sourceHash = LinearGradientSnippetSourceHash,
            entryPoint = LinearGradientEntryPoint,
            requiredBindings = listOf("group1.binding0.LinearGradientMaterialBlock"),
            category = "material-source",
            version = "v1",
            uniformLayoutHashes = listOf(LinearGradientMaterialLayoutHash),
            requiredFeatures = emptyList(),
        )

    private fun linearGradientRootSet(): GPUMaterialRootSet =
        GPUMaterialRootSet(
            rootSetId = "sourceRoot:linear-gradient",
            snippetIds = listOf(LinearGradientSnippetID),
            payloadShapeHash = "payload:LinearGradientMaterialBlock.startEnd.vec4f32@group1.binding0",
        )
}

object GPULinearGradientMaterialLowering {
    fun planPaint(
        descriptor: GPUPaintDescriptor,
        context: GPUMaterialLoweringContext,
    ): GPUPaintPipelinePlan {
        val sourcePlan = planSource(descriptor.source, context)
        val materialKey = when (sourcePlan) {
            is GPUMaterialSourcePlan.Accepted -> deriveMaterialKey(sourcePlan, context)
            is GPUMaterialSourcePlan.Refused -> MaterialKey("refused:${sourcePlan.diagnostic.code}")
        }

        return GPUPaintPipelinePlan(
            paint = descriptor,
            evaluationOrder = GPUPaintEvaluationOrder.SourceThenCoverage,
            stages = listOf(GPUPaintStagePlan.Material(sourcePlan)),
            materialKey = materialKey,
            diagnostics = emptyList(),
        )
    }

    fun planSource(
        source: GPUMaterialSourceDescriptor,
        context: GPUMaterialLoweringContext,
    ): GPUMaterialSourcePlan =
        when (source) {
            is GPUMaterialSourceDescriptor.Gradient -> source.planLinearGradient(context)
            else -> GPUMaterialSourcePlan.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.material_source.unknown",
                    sourceKind = source.kind,
                    message = "Only finite linear gradient material sources are accepted by M13",
                    terminal = true,
                ),
            )
        }

    fun deriveMaterialKey(
        accepted: GPUMaterialSourcePlan.Accepted,
        context: GPUMaterialLoweringContext,
    ): MaterialKey {
        val source = accepted.source as? GPUMaterialSourceDescriptor.Gradient
            ?: error("M13 MaterialKey derivation only accepts gradient source plans")
        val preimage = linearGradientMaterialKeyPreimage(context = context)

        return MaterialKey("material:linear_gradient:${preimage.dump().stableHash()}")
    }

    private fun GPUMaterialSourceDescriptor.Gradient.planLinearGradient(
        context: GPUMaterialLoweringContext,
    ): GPUMaterialSourcePlan {
        if (context.dictionaryVersion != GPULinearGradientMaterialDictionary.DictionaryVersion) {
            return GPUMaterialSourcePlan.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.material.dictionary_version_mismatch",
                    sourceKind = GPUMaterialSourceKind.Gradient,
                    message = "Linear gradient material requires ${GPULinearGradientMaterialDictionary.DictionaryVersion}",
                    terminal = true,
                ),
            )
        }

        if (plan.tileMode != GPUMaterialTileMode.Clamp) {
            return GPUMaterialSourcePlan.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.material.gradient_tile_mode_unimplemented",
                    sourceKind = GPUMaterialSourceKind.Gradient,
                    message = "M13 linear gradient only supports Clamp tile mode (got ${plan.tileMode})",
                    terminal = true,
                ),
            )
        }

        if (plan.stops.size > 16) {
            return GPUMaterialSourcePlan.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.material.gradient_stop_count_exceeded",
                    sourceKind = GPUMaterialSourceKind.Gradient,
                    message = "M13 linear gradient WGSL supports at most 16 stops (got ${plan.stops.size})",
                    terminal = true,
                ),
            )
        }

        return GPUMaterialSourcePlan.Accepted(
            source = this,
            snippetId = GPULinearGradientMaterialDictionary.LinearGradientSnippetID,
            payloadPlanHash = "payload:LinearGradientMaterialBlock.startEnd.vec4f32@group1.binding0",
            diagnostics = listOf(
                GPUMaterialSourceDiagnostic(
                    code = "accepted.material_source.linear_gradient",
                    sourceKind = GPUMaterialSourceKind.Gradient,
                    message = "Linear gradient source accepted as uniform payload",
                    terminal = false,
                ),
            ),
        )
    }
}

private fun linearGradientMaterialKeyPreimage(
    context: GPUMaterialLoweringContext,
): MaterialKeyPreimage =
    MaterialKeyPreimage(
        sourceKind = GPUMaterialSourceKind.Gradient,
        snippetId = GPULinearGradientMaterialDictionary.LinearGradientSnippetID,
        dictionaryVersion = context.dictionaryVersion,
        uniformLayoutHash = GPULinearGradientMaterialDictionary.LinearGradientMaterialLayoutHash,
        uniformLayoutLabel = "LinearGradientMaterialBlock(startEnd:vec4<f32>,colors:vec4<f32>x2)",
        payloadFields = listOf("startEnd@group1.binding0.offset0.vec4<f32>"),
        codeShapeFacts = listOf(
            "sourceFunction=linear_gradient_source",
            "payloadBlock=LinearGradientMaterialBlock",
        ),
        featureFlags = listOf("linear-gradient-material-abi-v1"),
    )

private fun String.stableHash(): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
    return digest.take(16)
}
