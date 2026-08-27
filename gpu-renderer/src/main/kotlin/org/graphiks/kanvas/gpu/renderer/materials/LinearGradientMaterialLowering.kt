package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor

/** Identity of the bounded prepared-material WGSL emitted by [GradientWgslShaderProvider]. */
private const val LinearGradientMaterialV2SourceHash = "fragment:prepared_material_linear_gradient:v2"
private const val LinearGradientMaterialV2EntryPoint = "fs_main"
private const val LinearGradientMaterialV2PayloadShape =
    "payload:GradientBlock.v2.start-end-local-matrix-stop-data@group1.binding0"

object GPULinearGradientMaterialDictionary {
    const val DictionaryVersion: String = "material-dictionary:linear-gradient:v2"

    val LinearGradientSnippetID: WGSLSnippetID = WGSLSnippetID("material.linear_gradient.v2")

    const val LinearGradientMaterialLayoutHash: String = "layout:linear-gradient-material-block:v2"

    const val LinearGradientMaterialModuleSalt: String = "kanvas-gpu-renderer:linear-gradient-material:v2"

    /** Creates a linear gradient material dictionary. */
    fun create(): GPUMaterialDictionary =
        GPUMaterialDictionary(
            dictionaryVersion = DictionaryVersion,
            snippets = listOf(linearGradientSnippet()),
            rootSets = listOf(linearGradientRootSet()),
        )

    /** Expands a linear gradient material entry or refuses with diagnostic. */
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

    /** Validates a linear gradient material dictionary. */
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
            sourceHash = LinearGradientMaterialV2SourceHash,
            entryPoint = LinearGradientMaterialV2EntryPoint,
            requiredBindings = listOf("group1.binding0.GradientBlock"),
            category = "material-source",
            version = "v2",
            uniformLayoutHashes = listOf(LinearGradientMaterialLayoutHash),
            requiredFeatures = emptyList(),
        )

    private fun linearGradientRootSet(): GPUMaterialRootSet =
        GPUMaterialRootSet(
            rootSetId = "sourceRoot:linear-gradient",
            snippetIds = listOf(LinearGradientSnippetID),
            payloadShapeHash = LinearGradientMaterialV2PayloadShape,
        )
}

object GPULinearGradientMaterialLowering {
    /** Plans a paint descriptor into a linear gradient pipeline. */
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

    /** Derives a unique material key from an accepted source plan. */
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

        if (plan.geometry.kind != GPUGradientKind.Linear) {
            return GPUMaterialSourcePlan.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.material.gradient_geometry",
                    sourceKind = GPUMaterialSourceKind.Gradient,
                    message = "Prepared linear gradient material only accepts linear geometry",
                    terminal = true,
                ),
            )
        }
        if (plan.tileMode != GPUMaterialTileMode.Clamp) {
            return GPUMaterialSourcePlan.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.material.gradient_tile_mode",
                    sourceKind = GPUMaterialSourceKind.Gradient,
                    message = "Prepared linear gradient material only accepts clamp tile mode",
                    terminal = true,
                ),
            )
        }
        if (plan.stops.size != 2) {
            return GPUMaterialSourcePlan.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.material.gradient_stop_count_exceeded",
                    sourceKind = GPUMaterialSourceKind.Gradient,
                    message = "Prepared linear gradient material requires exactly two stops (got ${plan.stops.size})",
                    terminal = true,
                ),
            )
        }

        return GPUMaterialSourcePlan.Accepted(
            source = this,
            snippetId = GPULinearGradientMaterialDictionary.LinearGradientSnippetID,
            payloadPlanHash = LinearGradientMaterialV2PayloadShape,
            entryPoint = LinearGradientMaterialV2EntryPoint,
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
        uniformLayoutLabel = "GradientBlock.v2(start:vec2<f32>,end:vec2<f32>,localMatrix0:vec4<f32>,localMatrix1:vec4<f32>,count:u32,stopData:array<vec4<f32>,32>)",
        payloadFields = listOf(
            "start@group1.binding0.offset0.vec2<f32>",
            "end@group1.binding0.offset8.vec2<f32>",
            "localMatrix0@group1.binding0.offset16.vec4<f32>",
            "localMatrix1@group1.binding0.offset32.vec4<f32>",
            "count@group1.binding0.offset48.u32",
            "stopData@group1.binding0.offset64.vec4<f32>x32",
        ),
        codeShapeFacts = listOf(
            "sourceFunction=fs_main",
            "payloadBlock=GradientBlock.v2",
        ),
        featureFlags = listOf("linear-gradient-material-abi-v2"),
    )

private fun String.stableHash(): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
    return digest.take(16)
}
