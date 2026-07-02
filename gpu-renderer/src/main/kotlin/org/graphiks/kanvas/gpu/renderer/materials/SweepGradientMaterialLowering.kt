package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.wgsl.SweepGradientDecalEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.SweepGradientEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.SweepGradientMirrorEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.SweepGradientRepeatEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.SweepGradientSnippetSourceHash

object GPUSweepGradientMaterialDictionary {
    const val DictionaryVersion: String = "material-dictionary:sweep-gradient:v1"

    val SweepGradientSnippetID: WGSLSnippetID = WGSLSnippetID("material.sweep_gradient.v1")

    const val SweepGradientMaterialLayoutHash: String = "layout:sweep-gradient-material-block:v1"

    const val SweepGradientMaterialModuleSalt: String = "kanvas-gpu-renderer:sweep-gradient-material:v1"

    /** Creates a sweep gradient material dictionary. */
    fun create(): GPUMaterialDictionary =
        GPUMaterialDictionary(
            dictionaryVersion = DictionaryVersion,
            snippets = listOf(sweepGradientSnippet()),
            rootSets = listOf(sweepGradientRootSet()),
        )

    /** Expands a sweep gradient material entry or refuses with diagnostic. */
    fun expandSweepGradientMaterialOrRefuse(
        materialKey: MaterialKey,
        dictionary: GPUMaterialDictionary,
    ): GPUMaterialAssemblyResult {
        val diagnostic = validateSweepGradientDictionary(dictionary)
        if (diagnostic != null) {
            return GPUMaterialAssemblyResult.Refused(diagnostic)
        }

        return GPUMaterialAssemblyResult.Accepted(
            GPUMaterialAssemblyPlan(
                programId = GPUMaterialProgramID("program:${materialKey.value}"),
                rootSet = dictionary.rootSets.single { SweepGradientSnippetID in it.snippetIds },
                snippetGraph = listOf(
                    WGSLSnippetNode(
                        snippetId = SweepGradientSnippetID,
                        children = emptyList(),
                        evaluationOrder = 0,
                    ),
                ),
                moduleSalt = SweepGradientMaterialModuleSalt,
            ),
        )
    }

    /** Validates a sweep gradient material dictionary. */
    fun validateSweepGradientDictionary(
        dictionary: GPUMaterialDictionary,
    ): GPUMaterialSourceDiagnostic? {
        if (dictionary.dictionaryVersion != DictionaryVersion) {
            return GPUMaterialSourceDiagnostic(
                code = "unsupported.material.dictionary_version_mismatch",
                sourceKind = GPUMaterialSourceKind.Gradient,
                message = "Sweep gradient material dictionary version ${dictionary.dictionaryVersion} does not match $DictionaryVersion",
                terminal = true,
            )
        }
        if (dictionary.snippets.none { it.snippetId == SweepGradientSnippetID }) {
            return GPUMaterialSourceDiagnostic(
                code = "unsupported.material.dictionary_missing_snippet",
                sourceKind = GPUMaterialSourceKind.Gradient,
                message = "Sweep gradient material dictionary is missing snippet ${SweepGradientSnippetID.value}",
                terminal = true,
            )
        }
        if (dictionary.rootSets.none { SweepGradientSnippetID in it.snippetIds }) {
            return GPUMaterialSourceDiagnostic(
                code = "unsupported.material.dictionary_missing_root_set",
                sourceKind = GPUMaterialSourceKind.Gradient,
                message = "Sweep gradient material dictionary is missing a root set for ${SweepGradientSnippetID.value}",
                terminal = true,
            )
        }
        return null
    }

    private fun sweepGradientSnippet(): WGSLSnippet =
        WGSLSnippet(
            snippetId = SweepGradientSnippetID,
            sourceHash = SweepGradientSnippetSourceHash,
            entryPoint = SweepGradientEntryPoint,
            requiredBindings = listOf("group1.binding0.SweepGradientMaterialBlock"),
            category = "material-source",
            version = "v1",
            uniformLayoutHashes = listOf(SweepGradientMaterialLayoutHash),
            requiredFeatures = emptyList(),
        )

    private fun sweepGradientRootSet(): GPUMaterialRootSet =
        GPUMaterialRootSet(
            rootSetId = "sourceRoot:sweep-gradient",
            snippetIds = listOf(SweepGradientSnippetID),
            payloadShapeHash = "payload:SweepGradientMaterialBlock.centerAngles.vec4f32@group1.binding0",
        )
}

object GPUSweepGradientMaterialLowering {
    /** Plans a paint descriptor into a sweep gradient pipeline. */
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

    /** Plans a material source descriptor into a source plan. */
    fun planSource(
        source: GPUMaterialSourceDescriptor,
        context: GPUMaterialLoweringContext,
    ): GPUMaterialSourcePlan =
        when (source) {
            is GPUMaterialSourceDescriptor.Gradient -> source.planSweepGradient(context)
            else -> GPUMaterialSourcePlan.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.material_source.unknown",
                    sourceKind = source.kind,
                    message = "Only sweep gradient material sources are accepted by M14",
                    terminal = true,
                ),
            )
        }

    /** Derives a unique material key from an accepted source plan. */
    fun deriveMaterialKey(
        accepted: GPUMaterialSourcePlan.Accepted,
        context: GPUMaterialLoweringContext,
    ): MaterialKey {
        val source = accepted.source as? GPUMaterialSourceDescriptor.Gradient
            ?: error("M14 MaterialKey derivation only accepts gradient source plans")
        val preimage = sweepGradientMaterialKeyPreimage(context = context)

        return MaterialKey("material:sweep_gradient:${preimage.dump().stableHash()}")
    }

    private fun GPUMaterialSourceDescriptor.Gradient.planSweepGradient(
        context: GPUMaterialLoweringContext,
    ): GPUMaterialSourcePlan {
        if (plan.geometry.kind != GPUGradientKind.Sweep) {
            return GPUMaterialSourcePlan.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.material_source.not_sweep_gradient",
                    sourceKind = GPUMaterialSourceKind.Gradient,
                    message = "Sweep gradient lowering requires Sweep gradient kind",
                    terminal = true,
                ),
            )
        }

        if (context.dictionaryVersion != GPUSweepGradientMaterialDictionary.DictionaryVersion) {
            return GPUMaterialSourcePlan.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.material.dictionary_version_mismatch",
                    sourceKind = GPUMaterialSourceKind.Gradient,
                    message = "Sweep gradient material requires ${GPUSweepGradientMaterialDictionary.DictionaryVersion}",
                    terminal = true,
                ),
            )
        }

        val entryPoint = when (plan.tileMode) {
            GPUMaterialTileMode.Clamp -> SweepGradientEntryPoint
            GPUMaterialTileMode.Repeat -> SweepGradientRepeatEntryPoint
            GPUMaterialTileMode.Mirror -> SweepGradientMirrorEntryPoint
            GPUMaterialTileMode.Decal -> SweepGradientDecalEntryPoint
        }

        if (plan.stops.size > 16) {
            return GPUMaterialSourcePlan.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.material.gradient_stop_count_exceeded",
                    sourceKind = GPUMaterialSourceKind.Gradient,
                    message = "M14 sweep gradient WGSL supports at most 16 stops (got ${plan.stops.size})",
                    terminal = true,
                ),
            )
        }

        return GPUMaterialSourcePlan.Accepted(
            source = this,
            snippetId = GPUSweepGradientMaterialDictionary.SweepGradientSnippetID,
            payloadPlanHash = "payload:SweepGradientMaterialBlock.centerAngles.vec4f32@group1.binding0",
            entryPoint = entryPoint,
            diagnostics = listOf(
                GPUMaterialSourceDiagnostic(
                    code = "accepted.material_source.sweep_gradient",
                    sourceKind = GPUMaterialSourceKind.Gradient,
                    message = "Sweep gradient source accepted as uniform payload",
                    terminal = false,
                ),
            ),
        )
    }
}

private fun sweepGradientMaterialKeyPreimage(
    context: GPUMaterialLoweringContext,
): MaterialKeyPreimage =
    MaterialKeyPreimage(
        sourceKind = GPUMaterialSourceKind.Gradient,
        snippetId = GPUSweepGradientMaterialDictionary.SweepGradientSnippetID,
        dictionaryVersion = context.dictionaryVersion,
        uniformLayoutHash = GPUSweepGradientMaterialDictionary.SweepGradientMaterialLayoutHash,
        uniformLayoutLabel = "SweepGradientMaterialBlock(centerAngles:vec4<f32>,colors:vec4<f32>x2)",
        payloadFields = listOf("centerAngles@group1.binding0.offset0.vec4<f32>"),
        codeShapeFacts = listOf(
            "sourceFunction=sweep_gradient_source",
            "payloadBlock=SweepGradientMaterialBlock",
        ),
        featureFlags = listOf("sweep-gradient-material-abi-v1"),
    )

private fun String.stableHash(): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
    return digest.take(16)
}

private fun MaterialKeyPreimage.dump(): String =
    "sourceKind=${sourceKind}|snippetId=${snippetId.value}|dictionaryVersion=$dictionaryVersion|" +
        "uniformLayoutHash=$uniformLayoutHash|uniformLayoutLabel=$uniformLayoutLabel|" +
        "payloadFields=${payloadFields.joinToString(",")}|" +
        "codeShapeFacts=${codeShapeFacts.joinToString(",")}|" +
        "featureFlags=${featureFlags.joinToString(",")}"
