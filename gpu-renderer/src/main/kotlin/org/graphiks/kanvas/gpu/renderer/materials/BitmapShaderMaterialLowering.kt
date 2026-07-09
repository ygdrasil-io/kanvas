package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.renderer.wgsl.BitmapShaderClampEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.BitmapShaderDecalEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.BitmapShaderMirrorEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.BitmapShaderRepeatEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.BitmapShaderSnippetSourceHash
import org.graphiks.kanvas.gpu.renderer.wgsl.BitmapShaderSourceEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.bitmapShaderWgslForEntryPoint
import java.security.MessageDigest

object GPUBitmapShaderMaterialDictionary {
    const val DictionaryVersion: String = "material-dictionary:bitmap-shader:v1"

    val BitmapShaderSnippetID: WGSLSnippetID = WGSLSnippetID("material.bitmap_shader.v1")

    const val BitmapShaderMaterialLayoutHash: String = "layout:bitmap-shader-material-block:v1"

    const val BitmapShaderMaterialModuleSalt: String = "kanvas-gpu-renderer:bitmap-shader-material:v1"

    /** Creates a bitmap shader material dictionary. */
    fun create(): GPUMaterialDictionary =
        GPUMaterialDictionary(
            dictionaryVersion = DictionaryVersion,
            snippets = listOf(bitmapShaderSnippet()),
            rootSets = listOf(bitmapShaderRootSet()),
        )

    /** Expands a bitmap shader material entry or refuses with diagnostic. */
    fun expandBitmapShaderMaterialOrRefuse(
        materialKey: MaterialKey,
        dictionary: GPUMaterialDictionary,
    ): GPUMaterialAssemblyResult {
        val diagnostic = validateBitmapShaderDictionary(dictionary)
        if (diagnostic != null) {
            return GPUMaterialAssemblyResult.Refused(diagnostic)
        }

        return expandBitmapShaderMaterial(
            materialKey = materialKey,
            dictionary = dictionary,
            selectedEntryPoint = BitmapShaderClampEntryPoint,
        )
    }

    /** Expands an accepted bitmap shader source plan into the WGSL source variant it selected. */
    fun expandBitmapShaderMaterialOrRefuse(
        materialKey: MaterialKey,
        dictionary: GPUMaterialDictionary,
        sourcePlan: GPUMaterialSourcePlan.Accepted,
    ): GPUMaterialAssemblyResult {
        val diagnostic = validateBitmapShaderDictionary(dictionary)
        if (diagnostic != null) {
            return GPUMaterialAssemblyResult.Refused(diagnostic)
        }
        if (sourcePlan.snippetId != BitmapShaderSnippetID ||
            sourcePlan.source !is GPUMaterialSourceDescriptor.Image
        ) {
            return GPUMaterialAssemblyResult.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.material.bitmap_source_plan_mismatch",
                    sourceKind = sourcePlan.source.kind,
                    message = "Bitmap shader material expansion requires an accepted bitmap image source plan",
                    terminal = true,
                ),
            )
        }

        return expandBitmapShaderMaterial(
            materialKey = materialKey,
            dictionary = dictionary,
            selectedEntryPoint = sourcePlan.entryPoint,
        )
    }

    /** Validates a bitmap shader material dictionary. */
    fun validateBitmapShaderDictionary(
        dictionary: GPUMaterialDictionary,
    ): GPUMaterialSourceDiagnostic? {
        if (dictionary.dictionaryVersion != DictionaryVersion) {
            return GPUMaterialSourceDiagnostic(
                code = "unsupported.material.dictionary_version_mismatch",
                sourceKind = GPUMaterialSourceKind.ImageShader,
                message = "Bitmap shader material dictionary version ${dictionary.dictionaryVersion} does not match $DictionaryVersion",
                terminal = true,
            )
        }
        if (dictionary.snippets.none { it.snippetId == BitmapShaderSnippetID }) {
            return GPUMaterialSourceDiagnostic(
                code = "unsupported.material.dictionary_missing_snippet",
                sourceKind = GPUMaterialSourceKind.ImageShader,
                message = "Bitmap shader material dictionary is missing snippet ${BitmapShaderSnippetID.value}",
                terminal = true,
            )
        }
        if (dictionary.rootSets.none { BitmapShaderSnippetID in it.snippetIds }) {
            return GPUMaterialSourceDiagnostic(
                code = "unsupported.material.dictionary_missing_root_set",
                sourceKind = GPUMaterialSourceKind.ImageShader,
                message = "Bitmap shader material dictionary is missing a root set for ${BitmapShaderSnippetID.value}",
                terminal = true,
            )
        }
        return null
    }

    private fun bitmapShaderSnippet(): WGSLSnippet =
        WGSLSnippet(
            snippetId = BitmapShaderSnippetID,
            sourceHash = BitmapShaderSnippetSourceHash,
            entryPoint = BitmapShaderSourceEntryPoint,
            requiredBindings = listOf("group1.binding1.texture_2d_rgba8_unorm", "group1.binding2.sampler"),
            category = "material-source",
            version = "v1",
            uniformLayoutHashes = listOf(BitmapShaderMaterialLayoutHash),
            requiredFeatures = emptyList(),
        )

    private fun bitmapShaderRootSet(): GPUMaterialRootSet =
        GPUMaterialRootSet(
            rootSetId = "sourceRoot:bitmap-shader",
            snippetIds = listOf(BitmapShaderSnippetID),
            payloadShapeHash = "payload:BitmapShaderMaterialBlock.textureSampled@group1.binding1+sampler@group1.binding2",
        )

    private fun expandBitmapShaderMaterial(
        materialKey: MaterialKey,
        dictionary: GPUMaterialDictionary,
        selectedEntryPoint: String,
    ): GPUMaterialAssemblyResult =
        GPUMaterialAssemblyResult.Accepted(
            GPUMaterialAssemblyPlan(
                programId = GPUMaterialProgramID("program:${materialKey.value}"),
                rootSet = dictionary.rootSets.single { BitmapShaderSnippetID in it.snippetIds },
                snippetGraph = listOf(
                    WGSLSnippetNode(
                        snippetId = BitmapShaderSnippetID,
                        children = emptyList(),
                        evaluationOrder = 0,
                    ),
                ),
                moduleSalt = BitmapShaderMaterialModuleSalt,
                sourceEntryPoint = BitmapShaderSourceEntryPoint,
                sourceWgsl = bitmapShaderWgslForEntryPoint(selectedEntryPoint),
            ),
        )
}

object GPUBitmapShaderMaterialLowering {
    private val supportedBitmapTileModes = setOf(
        GPUMaterialTileMode.Clamp,
        GPUMaterialTileMode.Repeat,
        GPUMaterialTileMode.Mirror,
        GPUMaterialTileMode.Decal,
    )

    /** Plans a paint descriptor into a bitmap shader pipeline. */
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

    /** Plans a material source descriptor into a bitmap shader source plan. */
    fun planSource(
        source: GPUMaterialSourceDescriptor,
        context: GPUMaterialLoweringContext,
    ): GPUMaterialSourcePlan =
        when (source) {
            is GPUMaterialSourceDescriptor.Image -> source.planBitmapShader(context)
            else -> GPUMaterialSourcePlan.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.material_source.unknown",
                    sourceKind = source.kind,
                    message = "Only image shader material sources are accepted by M17 bitmap shader lowering",
                    terminal = true,
                ),
            )
        }

    /** Derives a unique material key from an accepted bitmap shader source plan. */
    fun deriveMaterialKey(
        accepted: GPUMaterialSourcePlan.Accepted,
        context: GPUMaterialLoweringContext,
    ): MaterialKey {
        val source = accepted.source as? GPUMaterialSourceDescriptor.Image
            ?: error("M17 MaterialKey derivation only accepts image shader source plans")
        val preimage = bitmapShaderMaterialKeyPreimage(source = source, context = context)

        return MaterialKey("material:bitmap_shader:${preimage.dump().stableHash()}")
    }

    private fun GPUMaterialSourceDescriptor.Image.planBitmapShader(
        context: GPUMaterialLoweringContext,
    ): GPUMaterialSourcePlan {
        if (context.dictionaryVersion != GPUBitmapShaderMaterialDictionary.DictionaryVersion) {
            return GPUMaterialSourcePlan.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.material.dictionary_version_mismatch",
                    sourceKind = GPUMaterialSourceKind.ImageShader,
                    message = "Bitmap shader material requires ${GPUBitmapShaderMaterialDictionary.DictionaryVersion}",
                    terminal = true,
                ),
            )
        }

        if (plan.sampling.tileModeX !in supportedBitmapTileModes ||
            plan.sampling.tileModeY !in supportedBitmapTileModes
        ) {
            return GPUMaterialSourcePlan.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.material.bitmap_tile_mode_unknown",
                    sourceKind = GPUMaterialSourceKind.ImageShader,
                    message = "Bitmap shader tile mode must be one of ${supportedBitmapTileModes.joinToString { it.name }} " +
                        "(got ${plan.sampling.tileModeX}/${plan.sampling.tileModeY})",
                    terminal = true,
                ),
            )
        }

        if (plan.imageSourceKey.isBlank()) {
            return GPUMaterialSourcePlan.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.material.bitmap_source_key_missing",
                    sourceKind = GPUMaterialSourceKind.ImageShader,
                    message = "M17 bitmap shader requires a non-blank image source key",
                    terminal = true,
                ),
            )
        }

        return GPUMaterialSourcePlan.Accepted(
            source = this,
            snippetId = GPUBitmapShaderMaterialDictionary.BitmapShaderSnippetID,
            payloadPlanHash = "payload:BitmapShaderMaterialBlock.textureSampled@group1.binding1+sampler@group1.binding2",
            entryPoint = bitmapShaderEntryPoint(plan.sampling.tileModeX, plan.sampling.tileModeY),
            diagnostics = listOf(
                GPUMaterialSourceDiagnostic(
                    code = "accepted.material_source.bitmap_shader",
                    sourceKind = GPUMaterialSourceKind.ImageShader,
                    message = "Bitmap shader source accepted as sampled texture payload",
                    terminal = false,
                ),
            ),
        )
    }

    private fun bitmapShaderEntryPoint(
        tileModeX: GPUMaterialTileMode,
        tileModeY: GPUMaterialTileMode,
    ): String {
        val tokenX = tileModeX.bitmapShaderEntryPointToken()
        val tokenY = tileModeY.bitmapShaderEntryPointToken()
        return if (tileModeX == tileModeY) {
            when (tileModeX) {
                GPUMaterialTileMode.Clamp -> BitmapShaderClampEntryPoint
                GPUMaterialTileMode.Repeat -> BitmapShaderRepeatEntryPoint
                GPUMaterialTileMode.Mirror -> BitmapShaderMirrorEntryPoint
                GPUMaterialTileMode.Decal -> BitmapShaderDecalEntryPoint
            }
        } else {
            "bitmap_shader_${tokenX}_${tokenY}"
        }
    }

    private fun GPUMaterialTileMode.bitmapShaderEntryPointToken(): String =
        when (this) {
            GPUMaterialTileMode.Clamp -> "clamp"
            GPUMaterialTileMode.Repeat -> "repeat"
            GPUMaterialTileMode.Mirror -> "mirror"
            GPUMaterialTileMode.Decal -> "decal"
        }
}

private fun bitmapShaderMaterialKeyPreimage(
    source: GPUMaterialSourceDescriptor.Image,
    context: GPUMaterialLoweringContext,
): MaterialKeyPreimage =
    MaterialKeyPreimage(
        sourceKind = GPUMaterialSourceKind.ImageShader,
        snippetId = GPUBitmapShaderMaterialDictionary.BitmapShaderSnippetID,
        dictionaryVersion = context.dictionaryVersion,
        uniformLayoutHash = GPUBitmapShaderMaterialDictionary.BitmapShaderMaterialLayoutHash,
        uniformLayoutLabel = "BitmapShaderMaterialBlock(texture:group1.binding1,sampler:group1.binding2)",
        payloadFields = listOf("texture@group1.binding1", "sampler@group1.binding2"),
        codeShapeFacts = listOf(
            "sourceFunction=bitmap_shader_source",
            "payloadBlock=BitmapShaderMaterialBlock",
            "tileModeX=${source.plan.sampling.tileModeX.name}",
            "tileModeY=${source.plan.sampling.tileModeY.name}",
            "filterMode=${source.plan.sampling.filterMode}",
            "mipmapMode=${source.plan.sampling.mipmapMode}",
        ),
        featureFlags = listOf("bitmap-shader-material-abi-v1"),
    )

private fun String.stableHash(): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
    return digest.take(16)
}
