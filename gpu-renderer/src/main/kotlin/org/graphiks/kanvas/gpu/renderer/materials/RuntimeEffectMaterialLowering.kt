package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor

object GPURuntimeEffectMaterialLowering {
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
            is GPUMaterialSourceDescriptor.RuntimeEffect -> source.planRuntimeEffect(context)
            else -> GPUMaterialSourcePlan.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.material_source.unknown",
                    sourceKind = source.kind,
                    message = "Only registered runtime-effect sources are accepted",
                    terminal = true,
                ),
            )
        }

    fun deriveMaterialKey(
        accepted: GPUMaterialSourcePlan.Accepted,
        context: GPUMaterialLoweringContext,
    ): MaterialKey {
        val source = accepted.source as? GPUMaterialSourceDescriptor.RuntimeEffect
            ?: error("RuntimeEffect MaterialKey derivation only accepts runtime-effect source plans")
        val preimage = runtimeEffectMaterialKeyPreimage(
            context = context,
            effectId = source.effectId,
            descriptorVersion = source.descriptorVersion,
        )
        return MaterialKey("material:runtime_effect:${preimage.dump().stableHash()}")
    }

    private fun GPUMaterialSourceDescriptor.RuntimeEffect.planRuntimeEffect(
        context: GPUMaterialLoweringContext,
    ): GPUMaterialSourcePlan =
        GPUMaterialSourcePlan.Accepted(
            source = this,
            snippetId = WGSLSnippetID("material.runtime_effect.${effectId}.v${descriptorVersion}"),
            payloadPlanHash = "payload:RuntimeEffectBlock.${effectId}@group1.binding0",
            entryPoint = "runtime_effect_source",
            diagnostics = listOf(
                GPUMaterialSourceDiagnostic(
                    code = "accepted.material_source.runtime_effect",
                    sourceKind = GPUMaterialSourceKind.RuntimeEffect,
                    message = "Runtime effect source accepted: $effectId@v$descriptorVersion",
                    terminal = false,
                ),
            ),
        )
}

private fun runtimeEffectMaterialKeyPreimage(
    context: GPUMaterialLoweringContext,
    effectId: String,
    descriptorVersion: Int,
): MaterialKeyPreimage =
    MaterialKeyPreimage(
        sourceKind = GPUMaterialSourceKind.RuntimeEffect,
        snippetId = WGSLSnippetID("material.runtime_effect.$effectId.v$descriptorVersion"),
        dictionaryVersion = context.dictionaryVersion,
        uniformLayoutHash = "layout:runtime-effect-material-block:$effectId:v$descriptorVersion",
        uniformLayoutLabel = "RuntimeEffectMaterialBlock(effectId=$effectId, version=$descriptorVersion)",
        payloadFields = listOf("flex@group1.binding0.offset0"),
        codeShapeFacts = listOf(
            "sourceFunction=runtime_effect_source",
            "effectId=$effectId",
            "descriptorVersion=$descriptorVersion",
            "payloadBlock=RuntimeEffectMaterialBlock",
        ),
        featureFlags = listOf("runtime-effect-material-abi-v1"),
    )

private fun String.stableHash(): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
    return digest.take(16)
}
