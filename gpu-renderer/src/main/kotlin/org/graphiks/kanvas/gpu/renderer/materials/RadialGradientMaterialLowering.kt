package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.wgsl.RadialGradientEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.RadialGradientSnippetSourceHash

object GPURadialGradientMaterialDictionary {
    const val DictionaryVersion: String = "material-dictionary:radial-gradient:v1"

    val RadialGradientSnippetID: WGSLSnippetID = WGSLSnippetID("material.radial_gradient.v1")

    const val RadialGradientMaterialLayoutHash: String = "layout:radial-gradient-material-block:v1"

    const val RadialGradientMaterialModuleSalt: String = "kanvas-gpu-renderer:radial-gradient-material:v1"

    /** Creates a material dictionary for radial gradient with version, snippet, and root set. */
    fun create(): GPUMaterialDictionary =
        GPUMaterialDictionary(
            dictionaryVersion = DictionaryVersion,
            snippets = listOf(radialGradientSnippet()),
            rootSets = listOf(radialGradientRootSet()),
        )

    /** Expands a material dictionary entry into a radial gradient assembly plan, or refuses with a diagnostic. */
    fun expandRadialGradientMaterialOrRefuse(
        materialKey: MaterialKey,
        dictionary: GPUMaterialDictionary,
    ): GPUMaterialAssemblyResult {
        val diagnostic = validateRadialGradientDictionary(dictionary)
        if (diagnostic != null) {
            return GPUMaterialAssemblyResult.Refused(diagnostic)
        }

        return GPUMaterialAssemblyResult.Accepted(
            GPUMaterialAssemblyPlan(
                programId = GPUMaterialProgramID("program:${materialKey.value}"),
                rootSet = dictionary.rootSets.single { RadialGradientSnippetID in it.snippetIds },
                snippetGraph = listOf(
                    WGSLSnippetNode(
                        snippetId = RadialGradientSnippetID,
                        children = emptyList(),
                        evaluationOrder = 0,
                    ),
                ),
                moduleSalt = RadialGradientMaterialModuleSalt,
            ),
        )
    }

    /** Validates a radial gradient material dictionary. */
    fun validateRadialGradientDictionary(
        dictionary: GPUMaterialDictionary,
    ): GPUMaterialSourceDiagnostic? {
        if (dictionary.dictionaryVersion != DictionaryVersion) {
            return GPUMaterialSourceDiagnostic(
                code = "unsupported.material.dictionary_version_mismatch",
                sourceKind = GPUMaterialSourceKind.Gradient,
                message = "Radial gradient material dictionary version ${dictionary.dictionaryVersion} does not match $DictionaryVersion",
                terminal = true,
            )
        }
        if (dictionary.snippets.none { it.snippetId == RadialGradientSnippetID }) {
            return GPUMaterialSourceDiagnostic(
                code = "unsupported.material.dictionary_missing_snippet",
                sourceKind = GPUMaterialSourceKind.Gradient,
                message = "Radial gradient material dictionary is missing snippet ${RadialGradientSnippetID.value}",
                terminal = true,
            )
        }
        if (dictionary.rootSets.none { RadialGradientSnippetID in it.snippetIds }) {
            return GPUMaterialSourceDiagnostic(
                code = "unsupported.material.dictionary_missing_root_set",
                sourceKind = GPUMaterialSourceKind.Gradient,
                message = "Radial gradient material dictionary is missing a root set for ${RadialGradientSnippetID.value}",
                terminal = true,
            )
        }
        return null
    }

    private fun radialGradientSnippet(): WGSLSnippet =
        WGSLSnippet(
            snippetId = RadialGradientSnippetID,
            sourceHash = RadialGradientSnippetSourceHash,
            entryPoint = RadialGradientEntryPoint,
            requiredBindings = listOf("group1.binding0.RadialGradientMaterialBlock"),
            category = "material-source",
            version = "v1",
            uniformLayoutHashes = listOf(RadialGradientMaterialLayoutHash),
            requiredFeatures = emptyList(),
        )

    private fun radialGradientRootSet(): GPUMaterialRootSet =
        GPUMaterialRootSet(
            rootSetId = "sourceRoot:radial-gradient",
            snippetIds = listOf(RadialGradientSnippetID),
            payloadShapeHash = "payload:RadialGradientMaterialBlock.centerRadius.vec4f32@group1.binding0",
        )
}

object GPURadialGradientMaterialLowering {
    /** Plans a paint descriptor into a radial gradient pipeline. */
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
            is GPUMaterialSourceDescriptor.Gradient -> source.planRadialGradient(context)
            else -> GPUMaterialSourcePlan.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.material_source.unknown",
                    sourceKind = source.kind,
                    message = "Only radial gradient material sources are accepted by M14",
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
        val preimage = radialGradientMaterialKeyPreimage(context = context)

        return MaterialKey("material:radial_gradient:${preimage.dump().stableHash()}")
    }

    private fun GPUMaterialSourceDescriptor.Gradient.planRadialGradient(
        context: GPUMaterialLoweringContext,
    ): GPUMaterialSourcePlan {
        if (plan.geometry.kind != GPUGradientKind.Radial) {
            return GPUMaterialSourcePlan.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.material_source.not_radial_gradient",
                    sourceKind = GPUMaterialSourceKind.Gradient,
                    message = "Radial gradient lowering requires Radial gradient kind",
                    terminal = true,
                ),
            )
        }

        if (context.dictionaryVersion != GPURadialGradientMaterialDictionary.DictionaryVersion) {
            return GPUMaterialSourcePlan.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.material.dictionary_version_mismatch",
                    sourceKind = GPUMaterialSourceKind.Gradient,
                    message = "Radial gradient material requires ${GPURadialGradientMaterialDictionary.DictionaryVersion}",
                    terminal = true,
                ),
            )
        }

        if (plan.tileMode != GPUMaterialTileMode.Clamp) {
            return GPUMaterialSourcePlan.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.material.gradient_tile_mode_unimplemented",
                    sourceKind = GPUMaterialSourceKind.Gradient,
                    message = "M14 radial gradient only supports Clamp tile mode (got ${plan.tileMode})",
                    terminal = true,
                ),
            )
        }

        if (plan.stops.size > 16) {
            return GPUMaterialSourcePlan.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.material.gradient_stop_count_exceeded",
                    sourceKind = GPUMaterialSourceKind.Gradient,
                    message = "M14 radial gradient WGSL supports at most 16 stops (got ${plan.stops.size})",
                    terminal = true,
                ),
            )
        }

        return GPUMaterialSourcePlan.Accepted(
            source = this,
            snippetId = GPURadialGradientMaterialDictionary.RadialGradientSnippetID,
            payloadPlanHash = "payload:RadialGradientMaterialBlock.centerRadius.vec4f32@group1.binding0",
            diagnostics = listOf(
                GPUMaterialSourceDiagnostic(
                    code = "accepted.material_source.radial_gradient",
                    sourceKind = GPUMaterialSourceKind.Gradient,
                    message = "Radial gradient source accepted as uniform payload",
                    terminal = false,
                ),
            ),
        )
    }
}

private fun radialGradientMaterialKeyPreimage(
    context: GPUMaterialLoweringContext,
): MaterialKeyPreimage =
    MaterialKeyPreimage(
        sourceKind = GPUMaterialSourceKind.Gradient,
        snippetId = GPURadialGradientMaterialDictionary.RadialGradientSnippetID,
        dictionaryVersion = context.dictionaryVersion,
        uniformLayoutHash = GPURadialGradientMaterialDictionary.RadialGradientMaterialLayoutHash,
        uniformLayoutLabel = "RadialGradientMaterialBlock(centerRadius:vec4<f32>,colors:vec4<f32>x2)",
        payloadFields = listOf("centerRadius@group1.binding0.offset0.vec4<f32>"),
        codeShapeFacts = listOf(
            "sourceFunction=radial_gradient_source",
            "payloadBlock=RadialGradientMaterialBlock",
        ),
        featureFlags = listOf("radial-gradient-material-abi-v1"),
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
