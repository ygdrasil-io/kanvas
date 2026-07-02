package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.wgsl.ConicalGradientDecalEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.ConicalGradientEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.ConicalGradientMirrorEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.ConicalGradientRepeatEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.ConicalGradientSnippetSourceHash

object GPUConicalGradientMaterialDictionary {
    const val DictionaryVersion: String = "material-dictionary:conical-gradient:v1"

    val ConicalGradientSnippetID: WGSLSnippetID = WGSLSnippetID("material.conical_gradient.v1")

    const val ConicalGradientMaterialLayoutHash: String = "layout:conical-gradient-material-block:v1"

    const val ConicalGradientMaterialModuleSalt: String = "kanvas-gpu-renderer:conical-gradient-material:v1"

    fun create(): GPUMaterialDictionary =
        GPUMaterialDictionary(
            dictionaryVersion = DictionaryVersion,
            snippets = listOf(conicalGradientSnippet()),
            rootSets = listOf(conicalGradientRootSet()),
        )

    fun expandConicalGradientMaterialOrRefuse(
        materialKey: MaterialKey,
        dictionary: GPUMaterialDictionary,
    ): GPUMaterialAssemblyResult {
        val diagnostic = validateConicalGradientDictionary(dictionary)
        if (diagnostic != null) {
            return GPUMaterialAssemblyResult.Refused(diagnostic)
        }

        return GPUMaterialAssemblyResult.Accepted(
            GPUMaterialAssemblyPlan(
                programId = GPUMaterialProgramID("program:${materialKey.value}"),
                rootSet = dictionary.rootSets.single { ConicalGradientSnippetID in it.snippetIds },
                snippetGraph = listOf(
                    WGSLSnippetNode(
                        snippetId = ConicalGradientSnippetID,
                        children = emptyList(),
                        evaluationOrder = 0,
                    ),
                ),
                moduleSalt = ConicalGradientMaterialModuleSalt,
            ),
        )
    }

    fun validateConicalGradientDictionary(
        dictionary: GPUMaterialDictionary,
    ): GPUMaterialSourceDiagnostic? {
        if (dictionary.dictionaryVersion != DictionaryVersion) {
            return GPUMaterialSourceDiagnostic(
                code = "unsupported.material.dictionary_version_mismatch",
                sourceKind = GPUMaterialSourceKind.Gradient,
                message = "Conical gradient material dictionary version ${dictionary.dictionaryVersion} does not match $DictionaryVersion",
                terminal = true,
            )
        }
        if (dictionary.snippets.none { it.snippetId == ConicalGradientSnippetID }) {
            return GPUMaterialSourceDiagnostic(
                code = "unsupported.material.dictionary_missing_snippet",
                sourceKind = GPUMaterialSourceKind.Gradient,
                message = "Conical gradient material dictionary is missing snippet ${ConicalGradientSnippetID.value}",
                terminal = true,
            )
        }
        if (dictionary.rootSets.none { ConicalGradientSnippetID in it.snippetIds }) {
            return GPUMaterialSourceDiagnostic(
                code = "unsupported.material.dictionary_missing_root_set",
                sourceKind = GPUMaterialSourceKind.Gradient,
                message = "Conical gradient material dictionary is missing a root set for ${ConicalGradientSnippetID.value}",
                terminal = true,
            )
        }
        return null
    }

    private fun conicalGradientSnippet(): WGSLSnippet =
        WGSLSnippet(
            snippetId = ConicalGradientSnippetID,
            sourceHash = ConicalGradientSnippetSourceHash,
            entryPoint = ConicalGradientEntryPoint,
            requiredBindings = listOf("group1.binding0.ConicalGradientMaterialBlock"),
            category = "material-source",
            version = "v1",
            uniformLayoutHashes = listOf(ConicalGradientMaterialLayoutHash),
            requiredFeatures = emptyList(),
        )

    private fun conicalGradientRootSet(): GPUMaterialRootSet =
        GPUMaterialRootSet(
            rootSetId = "sourceRoot:conical-gradient",
            snippetIds = listOf(ConicalGradientSnippetID),
            payloadShapeHash = "payload:ConicalGradientMaterialBlock.startEnd.vec4f32@group1.binding0",
        )
}

object GPUConicalGradientMaterialLowering {
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
            is GPUMaterialSourceDescriptor.Gradient -> source.planConicalGradient(context)
            else -> GPUMaterialSourcePlan.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.material_source.unknown",
                    sourceKind = source.kind,
                    message = "Only finite conical gradient material sources are accepted by M13",
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
        val preimage = conicalGradientMaterialKeyPreimage(context = context)

        return MaterialKey("material:conical_gradient:${preimage.dump().stableHash()}")
    }

    private fun GPUMaterialSourceDescriptor.Gradient.planConicalGradient(
        context: GPUMaterialLoweringContext,
    ): GPUMaterialSourcePlan {
        if (context.dictionaryVersion != GPUConicalGradientMaterialDictionary.DictionaryVersion) {
            return GPUMaterialSourcePlan.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.material.dictionary_version_mismatch",
                    sourceKind = GPUMaterialSourceKind.Gradient,
                    message = "Conical gradient material requires ${GPUConicalGradientMaterialDictionary.DictionaryVersion}",
                    terminal = true,
                ),
            )
        }

        if (plan.tileMode == GPUMaterialTileMode.Decal) {
            return GPUMaterialSourcePlan.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.material.conical_gradient_decal_unavailable",
                    sourceKind = GPUMaterialSourceKind.Gradient,
                    message = "Decal tile mode for conical gradients is not yet supported",
                    terminal = true,
                ),
            )
        }

        val entryPoint = when (plan.tileMode) {
            GPUMaterialTileMode.Clamp -> ConicalGradientEntryPoint
            GPUMaterialTileMode.Repeat -> ConicalGradientRepeatEntryPoint
            GPUMaterialTileMode.Mirror -> ConicalGradientMirrorEntryPoint
            GPUMaterialTileMode.Decal -> ConicalGradientDecalEntryPoint
        }

        if (plan.stops.size > 16) {
            return GPUMaterialSourcePlan.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.material.gradient_stop_count_exceeded",
                    sourceKind = GPUMaterialSourceKind.Gradient,
                    message = "M13 conical gradient WGSL supports at most 16 stops (got ${plan.stops.size})",
                    terminal = true,
                ),
            )
        }

        val r1 = plan.geometry.controlPoints[4]
        val r2 = plan.geometry.controlPoints[5]
        if (r1 > r2) {
            return GPUMaterialSourcePlan.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.material.conical_gradient_focal_case",
                    sourceKind = GPUMaterialSourceKind.Gradient,
                    message = "Conical gradient focal case (r1 > r2) is not yet supported",
                    terminal = true,
                ),
            )
        }

        return GPUMaterialSourcePlan.Accepted(
            source = this,
            snippetId = GPUConicalGradientMaterialDictionary.ConicalGradientSnippetID,
            payloadPlanHash = "payload:ConicalGradientMaterialBlock.startEnd.vec4f32@group1.binding0",
            entryPoint = entryPoint,
            diagnostics = listOf(
                GPUMaterialSourceDiagnostic(
                    code = "accepted.material_source.conical_gradient",
                    sourceKind = GPUMaterialSourceKind.Gradient,
                    message = "Conical gradient source accepted as uniform payload",
                    terminal = false,
                ),
            ),
        )
    }
}

private fun conicalGradientMaterialKeyPreimage(
    context: GPUMaterialLoweringContext,
): MaterialKeyPreimage =
    MaterialKeyPreimage(
        sourceKind = GPUMaterialSourceKind.Gradient,
        snippetId = GPUConicalGradientMaterialDictionary.ConicalGradientSnippetID,
        dictionaryVersion = context.dictionaryVersion,
        uniformLayoutHash = GPUConicalGradientMaterialDictionary.ConicalGradientMaterialLayoutHash,
        uniformLayoutLabel = "ConicalGradientMaterialBlock(startEnd:vec4<f32>,colors:vec4<f32>x2)",
        payloadFields = listOf("startEnd@group1.binding0.offset0.vec4<f32>"),
        codeShapeFacts = listOf(
            "sourceFunction=conical_gradient_source",
            "payloadBlock=ConicalGradientMaterialBlock",
        ),
        featureFlags = listOf("conical-gradient-material-abi-v1"),
    )

private fun String.stableHash(): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
    return digest.take(16)
}
