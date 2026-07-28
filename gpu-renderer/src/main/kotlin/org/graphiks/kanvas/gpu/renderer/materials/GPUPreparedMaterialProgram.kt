package org.graphiks.kanvas.gpu.renderer.materials

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURuntimeEffectUniformValue
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlanner
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendSpecializationRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageConsumption
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceAlphaClassification
import org.graphiks.kanvas.gpu.renderer.passes.GPUTargetBlendFacts
import org.graphiks.kanvas.gpu.renderer.wgsl.BitmapShaderWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.reflectWgslModule
import org.graphiks.wgsl.parser.Lowerer
import org.graphiks.wgsl.parser.parseWgslResult

/**
 * Handle-free snapshot of one sampled image binding admitted by material lowering.
 *
 * This class does not normalize image content or replace prepared-image authority.
 * It only freezes the exact already-expanded RGBA facts carried by an admitted
 * [GPUMaterialDescriptor.ImageDraw].
 */
class GPUPreparedMaterialSampledResource internal constructor(
    val width: Int,
    val height: Int,
    val samplingFilterMode: String,
    val alphaOnly: Boolean,
    rgba8Bytes: ByteArray,
) {
    private val rgba8Snapshot = rgba8Bytes.copyOf()

    val contentHash: String = sha256Hex(rgba8Snapshot)

    val resourceKey: String = "sampled-material:" + sha256Hex(
        buildList {
            add("prepared-material-sampled-resource-v1")
            add("width=$width")
            add("height=$height")
            add("sampling=$samplingFilterMode")
            add("alphaOnly=$alphaOnly")
            add("content=$contentHash")
        }.joinToString("\n").encodeToByteArray(),
    )

    init {
        require(width > 0 && height > 0) {
            "Prepared material sampled resource dimensions must be positive"
        }
        val expectedBytes = exactRgbaByteCount(width, height)
        require(expectedBytes != null && expectedBytes == rgba8Snapshot.size.toLong()) {
            "Prepared material sampled resource must contain width * height * 4 bytes"
        }
        require(samplingFilterMode == "nearest" || samplingFilterMode == "linear") {
            "Prepared material sampled resource requires nearest or linear sampling"
        }
    }

    fun rgba8Bytes(): ByteArray = rgba8Snapshot.copyOf()

    internal fun identityFacts(): List<String> = listOf(
        "key=$resourceKey",
        "content=$contentHash",
        "dimensions=${width}x$height",
        "sampling=$samplingFilterMode",
        "alphaOnly=$alphaOnly",
    )
}

data class GPUPreparedMaterialProgram(
    val materialKey: String,
    val wgslSource: String,
    val entryPoint: String,
    val uniformBytes: List<Int>,
    val sampledResources: List<GPUPreparedMaterialSampledResource>,
    val paintAlpha: Float,
    val sourceKind: GPUMaterialSourceKind,
    val abiHash: String,
)

sealed interface GPUPreparedMaterialProgramResult {
    data class Ready(val program: GPUPreparedMaterialProgram) : GPUPreparedMaterialProgramResult

    data class Refused(
        val code: String,
        val sourceKind: GPUMaterialSourceKind,
        val message: String,
    ) : GPUPreparedMaterialProgramResult
}

object GPUPreparedMaterialProgramCompiler {
    private val blendPlanner = GPUBlendPlanner()

    fun compile(
        descriptor: GPUMaterialDescriptor,
        paintAlpha: Float,
        context: GPUMaterialLoweringContext,
    ): GPUPreparedMaterialProgramResult {
        if (!paintAlpha.isFinite() || paintAlpha !in 0f..1f) {
            return refused(
                code = "unsupported.material.paint_alpha",
                sourceKind = descriptor.sourceKind(),
                message = "Prepared material paint alpha must be finite and within 0..1",
            )
        }

        val prepared = when (val result = prepareSource(descriptor, context)) {
            is PreparedSourceResult.Ready -> result.source
            is PreparedSourceResult.Refused ->
                return refused(result.code, result.sourceKind, result.message)
        }
        val reflectedAbiFacts = when (
            val validation = validateFinalModule(prepared.wgslSource, prepared.entryPoint)
        ) {
            is FinalModuleValidation.Ready -> validation.abiFacts
            is FinalModuleValidation.Refused ->
                return refused(
                    code = "unsupported.material.wgsl_validation",
                    sourceKind = prepared.sourceKind,
                    message = validation.message,
                )
        }

        val uniformSnapshot = immutableList(prepared.uniformBytes.map { it.toInt() and 0xff })
        val resourceSnapshot = immutableList(prepared.sampledResources)
        val resourceFacts = resourceSnapshot.flatMapIndexed { index, resource ->
            resource.identityFacts().map { fact -> "resource[$index].$fact" }
        }
        val sourceHash = sha256Hex(prepared.wgslSource.encodeToByteArray())
        val materialKey = "material:prepared:${prepared.sourceKind.name.lowercase()}:" +
            sha256Hex(
                buildList {
                    add("prepared-material-key-v1")
                    add("sourceKind=${prepared.sourceKind}")
                    add("sourceHash=$sourceHash")
                    add("entryPoint=${prepared.entryPoint}")
                    add("uniformLayout=${prepared.uniformLayoutHash}")
                    add("capability=${context.capabilityClass}")
                    add("target=${context.targetFormatClass}")
                    add("dictionary=${context.dictionaryVersion}")
                    add("uniformBytes=${sha256Hex(prepared.uniformBytes)}")
                    add("paintAlphaBits=${paintAlpha.toRawBits().toUInt().toString(16)}")
                    addAll(prepared.keyFacts)
                    addAll(resourceFacts)
                }.joinToString("\n").encodeToByteArray(),
            )
        val abiHash = "sha256:" + sha256Hex(
            buildList {
                add("prepared-material-abi-v1")
                add("sourceKind=${prepared.sourceKind}")
                add("sourceHash=$sourceHash")
                add("entryPoint=${prepared.entryPoint}")
                add("uniformLayout=${prepared.uniformLayoutHash}")
                add("uniformByteCount=${uniformSnapshot.size}")
                add("sampledResourceCount=${resourceSnapshot.size}")
                addAll(prepared.abiFacts)
                addAll(reflectedAbiFacts)
            }.joinToString("\n").encodeToByteArray(),
        )

        return GPUPreparedMaterialProgramResult.Ready(
            GPUPreparedMaterialProgram(
                materialKey = materialKey,
                wgslSource = prepared.wgslSource,
                entryPoint = prepared.entryPoint,
                uniformBytes = uniformSnapshot,
                sampledResources = resourceSnapshot,
                paintAlpha = paintAlpha,
                sourceKind = prepared.sourceKind,
                abiHash = abiHash,
            ),
        )
    }

    private fun prepareSource(
        descriptor: GPUMaterialDescriptor,
        context: GPUMaterialLoweringContext,
    ): PreparedSourceResult =
        when (descriptor) {
            is GPUMaterialDescriptor.SolidColor -> prepareSolid(descriptor, context)
            is GPUMaterialDescriptor.LinearGradient,
            is GPUMaterialDescriptor.RadialGradient,
            is GPUMaterialDescriptor.SweepGradient,
            is GPUMaterialDescriptor.ConicalGradient,
            -> prepareGradient(descriptor, context)
            is GPUMaterialDescriptor.ImageDraw -> prepareImage(descriptor, context)
            is GPUMaterialDescriptor.RuntimeEffect -> prepareRuntimeEffect(descriptor, context)
            is GPUMaterialDescriptor.BlendShader -> prepareBlend(descriptor, context)
        }

    private fun prepareSolid(
        descriptor: GPUMaterialDescriptor.SolidColor,
        context: GPUMaterialLoweringContext,
    ): PreparedSourceResult {
        val logicalSource = GPUMaterialSourceDescriptor.Solid(
            GPUSolidColorPlan(
                r = descriptor.r,
                g = descriptor.g,
                b = descriptor.b,
                a = descriptor.a,
                colorSpecLabel = "straight-rgba-f32",
            ),
        )
        val loweringContext = context.copy(
            dictionaryVersion = GPUSolidMaterialDictionary.DictionaryVersion,
        )
        val plan = GPUSolidMaterialLowering.planSource(logicalSource, loweringContext)
        if (plan is GPUMaterialSourcePlan.Refused) {
            return plan.diagnostic.toPreparedRefusal()
        }
        val accepted = plan as GPUMaterialSourcePlan.Accepted
        val lowererKey = GPUSolidMaterialLowering.deriveMaterialKey(accepted, loweringContext)
        val uniforms = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
            putFloat(descriptor.r)
            putFloat(descriptor.g)
            putFloat(descriptor.b)
            putFloat(descriptor.a)
        }.array()

        return PreparedSourceResult.Ready(
            PreparedSource(
                wgslSource = solidMaterialWgsl(),
                entryPoint = FINAL_FRAGMENT_ENTRY_POINT,
                uniformBytes = uniforms,
                sampledResources = emptyList(),
                sourceKind = GPUMaterialSourceKind.SolidColor,
                uniformLayoutHash = GPUSolidMaterialDictionary.SolidMaterialLayoutHash,
                keyFacts = listOf(
                    "lowererKey=${lowererKey.value}",
                    "solidSemantics=straight-rgba-f32",
                ),
            ),
        )
    }

    private fun prepareGradient(
        descriptor: GPUMaterialDescriptor,
        context: GPUMaterialLoweringContext,
    ): PreparedSourceResult {
        val normalized = normalizeGradientStops(descriptor)
            ?: return refusedSource(
                "unsupported.material.gradient_stop_payload",
                GPUMaterialSourceKind.Gradient,
                "Gradient stop positions and colors must be finite and have matching non-empty lengths",
            )
        if (normalized.tileModeLabel() !in SUPPORTED_TILE_MODES) {
            return refusedSource(
                "unsupported.material.gradient_tile_mode_unsupported",
                GPUMaterialSourceKind.Gradient,
                "Prepared material gradient tile mode is not supported",
            )
        }
        if (!normalized.gradientScalarFacts().all(Float::isFinite)) {
            return refusedSource(
                "unsupported.material.gradient_non_finite_coords",
                GPUMaterialSourceKind.Gradient,
                "Prepared material gradient coordinates and colors must be finite",
            )
        }
        if (!GradientWgslShaderProvider.canHandle(normalized)) {
            return refusedSource(
                "unsupported.material.gradient_stop_count_exceeded",
                GPUMaterialSourceKind.Gradient,
                "Prepared material gradient supports at most 16 stops",
            )
        }

        val logicalSource = normalized.toGradientSourceDescriptor()
        val loweringContext = context.copy(dictionaryVersion = normalized.gradientDictionaryVersion())
        val sourcePlan = normalized.planGradientSource(logicalSource, loweringContext)
        if (sourcePlan is GPUMaterialSourcePlan.Refused) {
            return sourcePlan.diagnostic.toPreparedRefusal()
        }
        val accepted = sourcePlan as GPUMaterialSourcePlan.Accepted
        val lowererKey = normalized.deriveGradientMaterialKey(accepted, loweringContext)
        val shader = requireNotNull(GradientWgslShaderProvider.shaderFor(normalized))
        val uniformBytes = requireNotNull(GradientWgslShaderProvider.uniformBytesFor(normalized))
        val stopCount = normalized.gradientStopPositions().size

        return PreparedSourceResult.Ready(
            PreparedSource(
                wgslSource = shader.wgslSource,
                entryPoint = FINAL_FRAGMENT_ENTRY_POINT,
                uniformBytes = uniformBytes,
                sampledResources = emptyList(),
                sourceKind = GPUMaterialSourceKind.Gradient,
                uniformLayoutHash = shader.uniformLayoutHash,
                keyFacts = listOf(
                    "lowererKey=${lowererKey.value}",
                    "gradientFamily=${normalized.kind}",
                    "tileMode=${normalized.tileModeLabel()}",
                    "stopCount=$stopCount",
                ),
            ),
        )
    }

    private fun prepareImage(
        descriptor: GPUMaterialDescriptor.ImageDraw,
        context: GPUMaterialLoweringContext,
    ): PreparedSourceResult {
        val sampled = when (val result = sampledResource(descriptor)) {
            is SampledResourceResult.Ready -> result.resource
            is SampledResourceResult.Refused ->
                return refusedSource(
                    "unsupported.material.image_resource",
                    GPUMaterialSourceKind.ImageShader,
                    result.message,
                )
        }
        val tintChannels = listOf(
            descriptor.tintR,
            descriptor.tintG,
            descriptor.tintB,
            descriptor.tintA,
        )
        if (tintChannels.any { !it.isFinite() || it !in 0f..1f }) {
            return refusedSource(
                "unsupported.material.image_tint",
                GPUMaterialSourceKind.ImageShader,
                "Prepared image tint must be finite and within 0..1",
            )
        }
        val logicalSource = GPUMaterialSourceDescriptor.Image(
            GPUImageShaderPlan(
                imageSourceKey = sampled.resourceKey,
                sampling = GPUMaterialSamplingPlan(
                    tileModeX = GPUMaterialTileMode.Clamp,
                    tileModeY = GPUMaterialTileMode.Clamp,
                    filterMode = descriptor.samplingFilterMode,
                    mipmapMode = "none",
                ),
                colorTreatment = if (descriptor.alphaOnly) "a8-coverage-tint" else "rgba8-color",
            ),
        )
        val loweringContext = context.copy(
            dictionaryVersion = GPUBitmapShaderMaterialDictionary.DictionaryVersion,
        )
        val plan = GPUBitmapShaderMaterialLowering.planSource(logicalSource, loweringContext)
        if (plan is GPUMaterialSourcePlan.Refused) {
            return plan.diagnostic.toPreparedRefusal()
        }
        val accepted = plan as GPUMaterialSourcePlan.Accepted
        val lowererKey = GPUBitmapShaderMaterialLowering.deriveMaterialKey(accepted, loweringContext)
        val tintAlpha = descriptor.tintA
        val uniforms = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN).apply {
            putFloat(descriptor.tintR * tintAlpha)
            putFloat(descriptor.tintG * tintAlpha)
            putFloat(descriptor.tintB * tintAlpha)
            putFloat(tintAlpha)
            putInt(if (descriptor.alphaOnly) 1 else 0)
            putInt(0)
            putInt(0)
            putInt(0)
        }.array()

        return PreparedSourceResult.Ready(
            PreparedSource(
                wgslSource = imageMaterialWgsl(),
                entryPoint = FINAL_FRAGMENT_ENTRY_POINT,
                uniformBytes = uniforms,
                sampledResources = listOf(sampled),
                sourceKind = GPUMaterialSourceKind.ImageShader,
                uniformLayoutHash = IMAGE_UNIFORM_LAYOUT_HASH,
                keyFacts = listOf(
                    "lowererKey=${lowererKey.value}",
                    "imageAlphaOnly=${descriptor.alphaOnly}",
                    "imageSampling=${descriptor.samplingFilterMode}",
                ),
            ),
        )
    }

    private fun prepareRuntimeEffect(
        descriptor: GPUMaterialDescriptor.RuntimeEffect,
        context: GPUMaterialLoweringContext,
    ): PreparedSourceResult {
        if (descriptor.effectId.isBlank()) {
            return runtimeEffectRefusal("Runtime-effect descriptor ID must not be blank")
        }
        val program = when (
            val resolution = context.runtimeEffectResolver.resolve(
                descriptor.effectId,
                descriptor.descriptorVersion,
            )
        ) {
            is GPUPreparedRuntimeEffectResolution.DescriptorUnavailable ->
                return runtimeEffectRefusal(resolution.message)
            is GPUPreparedRuntimeEffectResolution.ProgramUnavailable ->
                return runtimeEffectProgramRefusal(resolution.message)
            is GPUPreparedRuntimeEffectResolution.Ready -> resolution.program
        }
        if (descriptor.children.isNotEmpty()) {
            return runtimeEffectChildrenRefusal(
                "Prepared runtime-effect children are not supported by this compiler",
            )
        }
        val uniformBytes = when (
            val packing = packRuntimeEffectUniforms(program, descriptor.uniforms)
        ) {
            is RuntimeEffectUniformPacking.Ready -> packing.bytes
            is RuntimeEffectUniformPacking.Refused ->
                return runtimeEffectUniformRefusal(packing.message)
        }
        val logicalSource = GPUMaterialSourceDescriptor.RuntimeEffect(
            effectId = program.effectId,
            descriptorVersion = program.descriptorVersion,
            routeContractHash = program.routeContractHash,
        )
        val plan = GPURuntimeEffectMaterialLowering.planSource(logicalSource, context)
        if (plan is GPUMaterialSourcePlan.Refused) {
            return plan.diagnostic.toPreparedRefusal()
        }
        val accepted = plan as GPUMaterialSourcePlan.Accepted
        val lowererKey = GPURuntimeEffectMaterialLowering.deriveMaterialKey(accepted, context)

        return PreparedSourceResult.Ready(
            PreparedSource(
                wgslSource = wrapMaterialSource(program.wgslSource, program.sourceFunction),
                entryPoint = FINAL_FRAGMENT_ENTRY_POINT,
                uniformBytes = uniformBytes,
                sampledResources = emptyList(),
                sourceKind = GPUMaterialSourceKind.RuntimeEffect,
                uniformLayoutHash = program.uniformSchemaHash,
                keyFacts = listOf(
                    "lowererKey=${lowererKey.value}",
                    "runtimeEffect=${program.effectId}@${program.descriptorVersion}",
                    "runtimeSource=${program.sourceHash}",
                    "runtimeModule=${program.moduleHash}",
                    "runtimeReflection=${program.reflectionHash}",
                    "runtimeBindings=${program.bindingPlanHash}",
                    "runtimeRoute=${program.routeContractHash}",
                ),
                abiFacts = buildList {
                    add("runtimeModule=${program.moduleHash}")
                    add("runtimeReflection=${program.reflectionHash}")
                    add("runtimeBindings=${program.bindingPlanHash}")
                    add("runtimeRoute=${program.routeContractHash}")
                    program.uniformFields.forEachIndexed { index, field ->
                        add(
                            "runtimeField[$index]=${field.name}:${field.type}:" +
                                "${field.offsetBytes}:${field.sizeBytes}:" +
                                "${field.alignmentBytes}:${field.strideBytes}",
                        )
                    }
                    program.bindings.forEachIndexed { index, binding ->
                        add(
                            "runtimeBinding[$index]=${binding.group}:${binding.binding}:" +
                                "${binding.resourceKind}:${binding.minBindingSizeBytes}",
                        )
                    }
                },
            ),
        )
    }

    private fun prepareBlend(
        descriptor: GPUMaterialDescriptor.BlendShader,
        context: GPUMaterialLoweringContext,
    ): PreparedSourceResult {
        if (!GPUBlendShaderLowering.canHandle(descriptor)) {
            return blendRefusal("Blend shader children are not supported by common lowering")
        }
        val mode = GPUBlendMode.entries.singleOrNull {
            it.name.equals(descriptor.mode, ignoreCase = true) ||
                it.gpuLabel.equals(descriptor.mode, ignoreCase = true)
        } ?: return blendRefusal("Blend shader mode is not registered")

        val blendPlan = blendPlanner.plan(
            GPUBlendSpecializationRequest(
                mode = mode,
                coverage = GPUCoverageConsumption.FullOrScissor,
                sourceAlpha = GPUSourceAlphaClassification.Translucent,
                target = GPUTargetBlendFacts(
                    formatClass = context.targetFormatClass,
                    clampsNormalizedColorWrites = "unorm" in context.targetFormatClass,
                    premultipliedAlpha = true,
                ),
                samplePlan = GPUSamplePlan.SingleSampleFrame,
            ),
        )
        if (blendPlan is GPUBlendPlan.UnsupportedBlend) {
            return blendRefusal(blendPlan.diagnostic.message)
        }

        val destination = when (val result = prepareSource(descriptor.dst, context)) {
            is PreparedSourceResult.Ready -> result.source
            is PreparedSourceResult.Refused ->
                return blendRefusal("Destination child refused: ${result.code}")
        }
        val source = when (val result = prepareSource(descriptor.src, context)) {
            is PreparedSourceResult.Ready -> result.source
            is PreparedSourceResult.Refused ->
                return blendRefusal("Source child refused: ${result.code}")
        }
        if (!descriptor.dst.hasExactBlendChildShape() || !descriptor.src.hasExactBlendChildShape()) {
            return blendRefusal("Blend shader child shape would lose source semantics")
        }

        val wgsl = runCatching {
            BlendWgslBuilder.buildWgsl(descriptor.dst, descriptor.src, mode.name)
        }.getOrElse { failure ->
            return blendRefusal("Blend WGSL construction failed: ${failure::class.simpleName}")
        }
        val uniforms = runCatching {
            BlendWgslBuilder.packUniforms(descriptor.dst, descriptor.src, mode.name)
        }.getOrElse { failure ->
            return blendRefusal("Blend uniform packing failed: ${failure::class.simpleName}")
        }
        val resources = destination.sampledResources + source.sampledResources
        val destinationIdentity = destination.materialShapeIdentity()
        val sourceIdentity = source.materialShapeIdentity()

        return PreparedSourceResult.Ready(
            PreparedSource(
                wgslSource = wgsl,
                entryPoint = FINAL_FRAGMENT_ENTRY_POINT,
                uniformBytes = uniforms,
                sampledResources = resources,
                sourceKind = GPUMaterialSourceKind.ShaderBlend,
                uniformLayoutHash = "layout:blend-material:${mode.gpuLabel}:" +
                    "${destination.uniformLayoutHash}:${source.uniformLayoutHash}:v1",
                keyFacts = listOf(
                    "blendMode=${mode.gpuLabel}",
                    "blendPlan=${blendPlan::class.simpleName}",
                    "blendDestination=$destinationIdentity",
                    "blendSource=$sourceIdentity",
                ),
            ),
        )
    }

    private fun validateFinalModule(
        source: String,
        entryPoint: String,
    ): FinalModuleValidation {
        val parsed = runCatching { parseWgslResult(source) }
            .getOrElse { failure ->
                return FinalModuleValidation.Refused(
                    "wgsl4k parser failed: ${failure::class.simpleName.orEmpty()}",
                )
            }
        if (!parsed.isSuccess) {
            return FinalModuleValidation.Refused(
                "wgsl4k parser diagnostics: ${parsed.errors.joinToString { it.message }}",
            )
        }
        val lowered = runCatching { Lowerer().lower(parsed.translationUnit) }
            .getOrElse { failure ->
                return FinalModuleValidation.Refused(
                    "wgsl4k lowering failed: ${failure::class.simpleName.orEmpty()}",
                )
            }
        if (lowered.entryPoints.none { it.name == entryPoint }) {
            return FinalModuleValidation.Refused(
                "wgsl4k did not expose entry point $entryPoint",
            )
        }
        val report = runCatching {
            lowered.reflectWgslModule(sourceId = sha256Hex(source.encodeToByteArray()))
        }.getOrElse { failure ->
            return FinalModuleValidation.Refused(
                "wgsl4k reflection failed: ${failure::class.simpleName.orEmpty()}",
            )
        }
        val abiFacts = buildList {
            report.entryPoints.sortedWith(compareBy({ it.name }, { it.stage }))
                .forEachIndexed { index, reflected ->
                    add(
                        "entry[$index]=${reflected.name}:${reflected.stage}:" +
                            "${reflected.workgroupSize}",
                    )
                }
            report.bindings.sortedWith(compareBy({ it.group }, { it.binding }))
                .forEachIndexed { index, binding ->
                    add(
                        "binding[$index]=${binding.group}:${binding.binding}:${binding.name}:" +
                            "${binding.resourceKind}:${binding.access}:${binding.sampleType}:" +
                            "${binding.viewDimension}:${binding.storageFormat}:" +
                            "${binding.minBindingSize}",
                    )
                }
            report.layouts.sortedWith(compareBy({ it.addressSpace }, { it.structName }))
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
        return FinalModuleValidation.Ready(abiFacts)
    }

    private fun sampledResource(
        descriptor: GPUMaterialDescriptor.ImageDraw,
    ): SampledResourceResult {
        val expectedBytes = exactRgbaByteCount(descriptor.imageWidth, descriptor.imageHeight)
            ?: return SampledResourceResult.Refused(
                "Prepared material image dimensions are invalid or overflow RGBA byte count",
            )
        if (expectedBytes != descriptor.rgbaPixels.size.toLong()) {
            return SampledResourceResult.Refused(
                "Prepared material image byte length does not equal width * height * 4",
            )
        }
        if (descriptor.samplingFilterMode !in SUPPORTED_IMAGE_FILTERS) {
            return SampledResourceResult.Refused(
                "Prepared material image sampling must be nearest or linear",
            )
        }
        return SampledResourceResult.Ready(
            GPUPreparedMaterialSampledResource(
                width = descriptor.imageWidth,
                height = descriptor.imageHeight,
                samplingFilterMode = descriptor.samplingFilterMode,
                alphaOnly = descriptor.alphaOnly,
                rgba8Bytes = descriptor.rgbaPixels,
            ),
        )
    }

    private fun runtimeEffectRefusal(message: String): PreparedSourceResult.Refused =
        refusedSource(
            "unsupported.material.runtime_effect.descriptor",
            GPUMaterialSourceKind.RuntimeEffect,
            message,
        )

    private fun runtimeEffectProgramRefusal(message: String): PreparedSourceResult.Refused =
        refusedSource(
            "unsupported.material.runtime_effect.wgsl_not_available",
            GPUMaterialSourceKind.RuntimeEffect,
            message,
        )

    private fun runtimeEffectUniformRefusal(message: String): PreparedSourceResult.Refused =
        refusedSource(
            "unsupported.material.runtime_effect.uniform_payload",
            GPUMaterialSourceKind.RuntimeEffect,
            message,
        )

    private fun runtimeEffectChildrenRefusal(message: String): PreparedSourceResult.Refused =
        refusedSource(
            "unsupported.material.runtime_effect.children",
            GPUMaterialSourceKind.RuntimeEffect,
            message,
        )

    private fun blendRefusal(message: String): PreparedSourceResult.Refused =
        refusedSource(
            "unsupported.material.blend_shader",
            GPUMaterialSourceKind.ShaderBlend,
            message,
        )
}

private data class PreparedSource(
    val wgslSource: String,
    val entryPoint: String,
    val uniformBytes: ByteArray,
    val sampledResources: List<GPUPreparedMaterialSampledResource>,
    val sourceKind: GPUMaterialSourceKind,
    val uniformLayoutHash: String,
    val keyFacts: List<String>,
    val abiFacts: List<String> = emptyList(),
) {
    fun materialShapeIdentity(): String = sha256Hex(
        buildList {
            add("prepared-material-shape-v1")
            add("sourceKind=$sourceKind")
            add("sourceHash=${sha256Hex(wgslSource.encodeToByteArray())}")
            add("entryPoint=$entryPoint")
            add("uniformLayout=$uniformLayoutHash")
            add("uniformByteCount=${uniformBytes.size}")
            add("uniformBytes=${sha256Hex(uniformBytes)}")
            addAll(keyFacts)
            sampledResources.forEachIndexed { index, resource ->
                resource.identityFacts().forEach { fact -> add("resource[$index].$fact") }
            }
        }.joinToString("\n").encodeToByteArray(),
    )
}

private sealed interface PreparedSourceResult {
    data class Ready(val source: PreparedSource) : PreparedSourceResult

    data class Refused(
        val code: String,
        val sourceKind: GPUMaterialSourceKind,
        val message: String,
    ) : PreparedSourceResult
}

private sealed interface FinalModuleValidation {
    data class Ready(val abiFacts: List<String>) : FinalModuleValidation
    data class Refused(val message: String) : FinalModuleValidation
}

private sealed interface RuntimeEffectUniformPacking {
    data class Ready(val bytes: ByteArray) : RuntimeEffectUniformPacking
    data class Refused(val message: String) : RuntimeEffectUniformPacking
}

private sealed interface SampledResourceResult {
    data class Ready(val resource: GPUPreparedMaterialSampledResource) : SampledResourceResult
    data class Refused(val message: String) : SampledResourceResult
}

private fun packRuntimeEffectUniforms(
    program: GPUPreparedRuntimeEffectProgram,
    uniforms: Map<String, GPURuntimeEffectUniformValue>,
): RuntimeEffectUniformPacking {
    val expectedNames = program.uniformFields.map { it.name }
    if (
        uniforms.size != expectedNames.size ||
        uniforms.keys != expectedNames.toSet()
    ) {
        return RuntimeEffectUniformPacking.Refused(
            "Runtime-effect uniform names must exactly match the registered schema",
        )
    }
    if (program.uniformBlockSizeBytes < 0) {
        return RuntimeEffectUniformPacking.Refused(
            "Runtime-effect registered uniform block size is invalid",
        )
    }
    val bytes = ByteArray(program.uniformBlockSizeBytes)
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val occupied = mutableListOf<IntRange>()

    for (field in program.uniformFields) {
        val endExclusive = field.offsetBytes.toLong() + field.sizeBytes.toLong()
        if (
            field.name.isBlank() ||
            field.offsetBytes < 0 ||
            field.sizeBytes <= 0 ||
            field.alignmentBytes <= 0 ||
            field.offsetBytes % field.alignmentBytes != 0 ||
            endExclusive > bytes.size.toLong()
        ) {
            return RuntimeEffectUniformPacking.Refused(
                "Runtime-effect registered uniform field layout is invalid",
            )
        }
        val range = field.offsetBytes until endExclusive.toInt()
        if (occupied.any { existing -> existing.first < range.last + 1 && range.first <= existing.last }) {
            return RuntimeEffectUniformPacking.Refused(
                "Runtime-effect registered uniform fields overlap",
            )
        }
        occupied += range

        val value = uniforms.getValue(field.name)
        if (!value.matches(field.type)) {
            return RuntimeEffectUniformPacking.Refused(
                "Runtime-effect uniform ${field.name} has the wrong registered type",
            )
        }
        if (!value.hasFiniteFloatPayload()) {
            return RuntimeEffectUniformPacking.Refused(
                "Runtime-effect uniform ${field.name} contains non-finite values",
            )
        }
        if (!buffer.writeRegisteredValue(field, value)) {
            return RuntimeEffectUniformPacking.Refused(
                "Runtime-effect uniform ${field.name} does not fit its registered field layout",
            )
        }
    }
    return RuntimeEffectUniformPacking.Ready(bytes)
}

private fun GPURuntimeEffectUniformValue.matches(
    type: GPUPreparedRuntimeEffectUniformType,
): Boolean =
    when (type) {
        GPUPreparedRuntimeEffectUniformType.Float1 -> this is GPURuntimeEffectUniformValue.Float1
        GPUPreparedRuntimeEffectUniformType.Float2 -> this is GPURuntimeEffectUniformValue.Float2
        GPUPreparedRuntimeEffectUniformType.Float3 -> this is GPURuntimeEffectUniformValue.Float3
        GPUPreparedRuntimeEffectUniformType.Float4 -> this is GPURuntimeEffectUniformValue.Float4
        GPUPreparedRuntimeEffectUniformType.Int1 -> this is GPURuntimeEffectUniformValue.Int1
        GPUPreparedRuntimeEffectUniformType.Matrix3x3 ->
            this is GPURuntimeEffectUniformValue.Matrix3x3
        GPUPreparedRuntimeEffectUniformType.Matrix4x4 ->
            this is GPURuntimeEffectUniformValue.Matrix4x4
    }

private fun GPURuntimeEffectUniformValue.hasFiniteFloatPayload(): Boolean =
    when (this) {
        is GPURuntimeEffectUniformValue.Float1 -> value.isFinite()
        is GPURuntimeEffectUniformValue.Float2 -> x.isFinite() && y.isFinite()
        is GPURuntimeEffectUniformValue.Float3 -> x.isFinite() && y.isFinite() && z.isFinite()
        is GPURuntimeEffectUniformValue.Float4 ->
            x.isFinite() && y.isFinite() && z.isFinite() && w.isFinite()
        is GPURuntimeEffectUniformValue.Int1 -> true
        is GPURuntimeEffectUniformValue.Matrix3x3 -> values.all(Float::isFinite)
        is GPURuntimeEffectUniformValue.Matrix4x4 -> values.all(Float::isFinite)
    }

private fun ByteBuffer.writeRegisteredValue(
    field: GPUPreparedRuntimeEffectUniformField,
    value: GPURuntimeEffectUniformValue,
): Boolean {
    val offset = field.offsetBytes
    return when (value) {
        is GPURuntimeEffectUniformValue.Float1 -> {
            if (field.sizeBytes < 4) return false
            putFloat(offset, value.value)
            true
        }
        is GPURuntimeEffectUniformValue.Float2 -> {
            if (field.sizeBytes < 8) return false
            putFloat(offset, value.x)
            putFloat(offset + 4, value.y)
            true
        }
        is GPURuntimeEffectUniformValue.Float3 -> {
            if (field.sizeBytes < 12) return false
            putFloat(offset, value.x)
            putFloat(offset + 4, value.y)
            putFloat(offset + 8, value.z)
            true
        }
        is GPURuntimeEffectUniformValue.Float4 -> {
            if (field.sizeBytes < 16) return false
            putFloat(offset, value.x)
            putFloat(offset + 4, value.y)
            putFloat(offset + 8, value.z)
            putFloat(offset + 12, value.w)
            true
        }
        is GPURuntimeEffectUniformValue.Int1 -> {
            if (field.sizeBytes < 4) return false
            putInt(offset, value.value)
            true
        }
        is GPURuntimeEffectUniformValue.Matrix3x3 -> {
            val stride = field.strideBytes ?: 16
            if (stride < 12 || field.sizeBytes < stride * 3) return false
            repeat(3) { column ->
                repeat(3) { row ->
                    putFloat(
                        offset + column * stride + row * 4,
                        value.values[row * 3 + column],
                    )
                }
            }
            true
        }
        is GPURuntimeEffectUniformValue.Matrix4x4 -> {
            val stride = field.strideBytes ?: 16
            if (stride < 16 || field.sizeBytes < stride * 4) return false
            repeat(4) { column ->
                repeat(4) { row ->
                    putFloat(
                        offset + column * stride + row * 4,
                        value.values[row * 4 + column],
                    )
                }
            }
            true
        }
    }
}

private fun refusedSource(
    code: String,
    sourceKind: GPUMaterialSourceKind,
    message: String,
): PreparedSourceResult.Refused =
    PreparedSourceResult.Refused(code, sourceKind, message)

private fun refused(
    code: String,
    sourceKind: GPUMaterialSourceKind,
    message: String,
): GPUPreparedMaterialProgramResult.Refused =
    GPUPreparedMaterialProgramResult.Refused(code, sourceKind, message)

private fun GPUMaterialSourceDiagnostic.toPreparedRefusal(): PreparedSourceResult.Refused =
    refusedSource(code, sourceKind, message)

private fun GPUMaterialDescriptor.sourceKind(): GPUMaterialSourceKind = when (this) {
    is GPUMaterialDescriptor.SolidColor -> GPUMaterialSourceKind.SolidColor
    is GPUMaterialDescriptor.LinearGradient,
    is GPUMaterialDescriptor.RadialGradient,
    is GPUMaterialDescriptor.SweepGradient,
    is GPUMaterialDescriptor.ConicalGradient,
    -> GPUMaterialSourceKind.Gradient
    is GPUMaterialDescriptor.ImageDraw -> GPUMaterialSourceKind.ImageShader
    is GPUMaterialDescriptor.RuntimeEffect -> GPUMaterialSourceKind.RuntimeEffect
    is GPUMaterialDescriptor.BlendShader -> GPUMaterialSourceKind.ShaderBlend
}

private fun normalizeGradientStops(descriptor: GPUMaterialDescriptor): GPUMaterialDescriptor? {
    val existingPositions = descriptor.gradientStopPositionsOrNull()
    val existingColors = descriptor.gradientStopColorsOrNull()
    val positions: FloatArray
    val colors: FloatArray
    if (existingPositions == null && existingColors == null) {
        positions = floatArrayOf(0f, 1f)
        colors = descriptor.gradientEndpointColors()
    } else {
        positions = existingPositions?.copyOf() ?: return null
        colors = existingColors?.copyOf() ?: return null
    }
    if (
        positions.isEmpty() ||
        positions.size > 16 ||
        colors.size != positions.size * 4 ||
        positions.any { !it.isFinite() } ||
        colors.any { !it.isFinite() }
    ) {
        return null
    }
    return descriptor.copyGradientStops(positions, colors)
}

private fun GPUMaterialDescriptor.gradientStopPositionsOrNull(): FloatArray? = when (this) {
    is GPUMaterialDescriptor.LinearGradient -> allStopPositions
    is GPUMaterialDescriptor.RadialGradient -> allStopPositions
    is GPUMaterialDescriptor.SweepGradient -> allStopPositions
    is GPUMaterialDescriptor.ConicalGradient -> allStopPositions
    else -> null
}

private fun GPUMaterialDescriptor.gradientStopColorsOrNull(): FloatArray? = when (this) {
    is GPUMaterialDescriptor.LinearGradient -> allStopColors
    is GPUMaterialDescriptor.RadialGradient -> allStopColors
    is GPUMaterialDescriptor.SweepGradient -> allStopColors
    is GPUMaterialDescriptor.ConicalGradient -> allStopColors
    else -> null
}

private fun GPUMaterialDescriptor.gradientEndpointColors(): FloatArray = when (this) {
    is GPUMaterialDescriptor.LinearGradient ->
        floatArrayOf(startR, startG, startB, startA, endR, endG, endB, endA)
    is GPUMaterialDescriptor.RadialGradient ->
        floatArrayOf(startR, startG, startB, startA, endR, endG, endB, endA)
    is GPUMaterialDescriptor.SweepGradient ->
        floatArrayOf(startR, startG, startB, startA, endR, endG, endB, endA)
    is GPUMaterialDescriptor.ConicalGradient ->
        floatArrayOf(startR, startG, startB, startA, endR, endG, endB, endA)
    else -> error("Not a gradient descriptor")
}

private fun GPUMaterialDescriptor.copyGradientStops(
    positions: FloatArray,
    colors: FloatArray,
): GPUMaterialDescriptor = when (this) {
    is GPUMaterialDescriptor.LinearGradient ->
        copy(allStopPositions = positions, allStopColors = colors)
    is GPUMaterialDescriptor.RadialGradient ->
        copy(allStopPositions = positions, allStopColors = colors)
    is GPUMaterialDescriptor.SweepGradient ->
        copy(allStopPositions = positions, allStopColors = colors)
    is GPUMaterialDescriptor.ConicalGradient ->
        copy(allStopPositions = positions, allStopColors = colors)
    else -> error("Not a gradient descriptor")
}

private fun GPUMaterialDescriptor.gradientStopPositions(): FloatArray =
    requireNotNull(gradientStopPositionsOrNull())

private fun GPUMaterialDescriptor.tileModeLabel(): String = when (this) {
    is GPUMaterialDescriptor.LinearGradient -> tileMode
    is GPUMaterialDescriptor.RadialGradient -> tileMode
    is GPUMaterialDescriptor.SweepGradient -> tileMode
    is GPUMaterialDescriptor.ConicalGradient -> tileMode
    else -> error("Not a gradient descriptor")
}

private fun GPUMaterialDescriptor.gradientScalarFacts(): List<Float> = when (this) {
    is GPUMaterialDescriptor.LinearGradient ->
        listOf(
            startX, startY, endX, endY,
            startR, startG, startB, startA,
            endR, endG, endB, endA,
        ) + requireNotNull(allStopPositions).toList() + requireNotNull(allStopColors).toList()
    is GPUMaterialDescriptor.RadialGradient ->
        listOf(
            centerX, centerY, radius,
            startR, startG, startB, startA,
            endR, endG, endB, endA,
        ) + requireNotNull(allStopPositions).toList() + requireNotNull(allStopColors).toList()
    is GPUMaterialDescriptor.SweepGradient ->
        listOf(
            centerX, centerY, startAngle, endAngle,
            startR, startG, startB, startA,
            endR, endG, endB, endA,
        ) + requireNotNull(allStopPositions).toList() + requireNotNull(allStopColors).toList()
    is GPUMaterialDescriptor.ConicalGradient ->
        listOf(
            startX, startY, endX, endY, startRadius, endRadius,
            startR, startG, startB, startA,
            endR, endG, endB, endA,
        ) + requireNotNull(allStopPositions).toList() + requireNotNull(allStopColors).toList()
    else -> error("Not a gradient descriptor")
}

private fun GPUMaterialDescriptor.toGradientSourceDescriptor(): GPUMaterialSourceDescriptor.Gradient {
    val positions = gradientStopPositions()
    val colors = requireNotNull(gradientStopColorsOrNull())
    val geometry = when (this) {
        is GPUMaterialDescriptor.LinearGradient ->
            GPUGradientGeometryPlan(GPUGradientKind.Linear, listOf(startX, startY, endX, endY))
        is GPUMaterialDescriptor.RadialGradient ->
            GPUGradientGeometryPlan(GPUGradientKind.Radial, listOf(centerX, centerY, radius))
        is GPUMaterialDescriptor.SweepGradient ->
            GPUGradientGeometryPlan(GPUGradientKind.Sweep, listOf(centerX, centerY, startAngle, endAngle))
        is GPUMaterialDescriptor.ConicalGradient ->
            GPUGradientGeometryPlan(
                GPUGradientKind.TwoPointConical,
                listOf(startX, startY, endX, endY, startRadius, endRadius),
            )
        else -> error("Not a gradient descriptor")
    }
    val stops = positions.indices.map { index ->
        GPUGradientStopPlan(
            offset = positions[index],
            colorLabel = colors.copyOfRange(index * 4, index * 4 + 4)
                .joinToString(",") { channel -> channel.toRawBits().toString(16) },
        )
    }
    return GPUMaterialSourceDescriptor.Gradient(
        GPUGradientPlan(
            geometry = geometry,
            stops = stops,
            stopStore = GPUGradientStopStorePlan(
                stopCount = stops.size,
                storageKind = "uniform-array-16",
                payloadHash = sha256Hex(
                    positions.toByteArray() + colors.toByteArray(),
                ),
            ),
            tileMode = when (tileModeLabel()) {
                "clamp" -> GPUMaterialTileMode.Clamp
                "repeat" -> GPUMaterialTileMode.Repeat
                "mirror" -> GPUMaterialTileMode.Mirror
                "decal" -> GPUMaterialTileMode.Decal
                else -> error("Tile mode validated before lowering")
            },
        ),
    )
}

private fun GPUMaterialDescriptor.gradientDictionaryVersion(): String = when (this) {
    is GPUMaterialDescriptor.LinearGradient -> GPULinearGradientMaterialDictionary.DictionaryVersion
    is GPUMaterialDescriptor.RadialGradient -> GPURadialGradientMaterialDictionary.DictionaryVersion
    is GPUMaterialDescriptor.SweepGradient -> GPUSweepGradientMaterialDictionary.DictionaryVersion
    is GPUMaterialDescriptor.ConicalGradient -> GPUConicalGradientMaterialDictionary.DictionaryVersion
    else -> error("Not a gradient descriptor")
}

private fun GPUMaterialDescriptor.planGradientSource(
    source: GPUMaterialSourceDescriptor,
    context: GPUMaterialLoweringContext,
): GPUMaterialSourcePlan = when (this) {
    is GPUMaterialDescriptor.LinearGradient -> GPULinearGradientMaterialLowering.planSource(source, context)
    is GPUMaterialDescriptor.RadialGradient -> GPURadialGradientMaterialLowering.planSource(source, context)
    is GPUMaterialDescriptor.SweepGradient -> GPUSweepGradientMaterialLowering.planSource(source, context)
    is GPUMaterialDescriptor.ConicalGradient -> GPUConicalGradientMaterialLowering.planSource(source, context)
    else -> error("Not a gradient descriptor")
}

private fun GPUMaterialDescriptor.deriveGradientMaterialKey(
    accepted: GPUMaterialSourcePlan.Accepted,
    context: GPUMaterialLoweringContext,
): MaterialKey = when (this) {
    is GPUMaterialDescriptor.LinearGradient ->
        GPULinearGradientMaterialLowering.deriveMaterialKey(accepted, context)
    is GPUMaterialDescriptor.RadialGradient ->
        GPURadialGradientMaterialLowering.deriveMaterialKey(accepted, context)
    is GPUMaterialDescriptor.SweepGradient ->
        GPUSweepGradientMaterialLowering.deriveMaterialKey(accepted, context)
    is GPUMaterialDescriptor.ConicalGradient ->
        GPUConicalGradientMaterialLowering.deriveMaterialKey(accepted, context)
    else -> error("Not a gradient descriptor")
}

private fun GPUMaterialDescriptor.hasExactBlendChildShape(): Boolean = when (this) {
    is GPUMaterialDescriptor.LinearGradient ->
        (allStopPositions?.size ?: 2) == 2 && (allStopColors?.size ?: 8) == 8
    is GPUMaterialDescriptor.RadialGradient ->
        (allStopPositions?.size ?: 2) == 2 && (allStopColors?.size ?: 8) == 8
    is GPUMaterialDescriptor.SweepGradient ->
        (allStopPositions?.size ?: 2) == 2 && (allStopColors?.size ?: 8) == 8
    is GPUMaterialDescriptor.SolidColor,
    is GPUMaterialDescriptor.ImageDraw,
    -> true
    is GPUMaterialDescriptor.ConicalGradient,
    is GPUMaterialDescriptor.RuntimeEffect,
    is GPUMaterialDescriptor.BlendShader,
    -> false
}

private fun solidMaterialWgsl(): String = """
    struct SolidMaterialBlock {
        color: vec4<f32>,
    }
    @group(1) @binding(0) var<uniform> solidMaterial: SolidMaterialBlock;

    fn solid_source(uv: vec2<f32>) -> vec4<f32> {
        return solidMaterial.color;
    }

    ${materialStageWrapper("solid_source")}
""".trimIndent()

private fun imageMaterialWgsl(): String = """
    struct PreparedMaterialImageUniforms {
        tint: vec4<f32>,
        flags: vec4<u32>,
    }
    @group(1) @binding(0) var<uniform> imageMaterial: PreparedMaterialImageUniforms;

    $BitmapShaderWgsl

    struct PreparedMaterialVertexOutput {
        @builtin(position) position: vec4<f32>,
        @location(0) uv: vec2<f32>,
    }

    @vertex
    fn vs_main(@builtin(vertex_index) vertexIndex: u32) -> PreparedMaterialVertexOutput {
        let positions = array<vec2<f32>, 3>(
            vec2f(-1.0, -1.0),
            vec2f(3.0, -1.0),
            vec2f(-1.0, 3.0),
        );
        let position = positions[vertexIndex];
        var output: PreparedMaterialVertexOutput;
        output.position = vec4f(position, 0.0, 1.0);
        output.uv = vec2f(position.x * 0.5 + 0.5, 1.0 - (position.y * 0.5 + 0.5));
        return output;
    }

    @fragment
    fn fs_main(input: PreparedMaterialVertexOutput) -> @location(0) vec4<f32> {
        let sampled = bitmap_shader_clamp(input.uv);
        if (imageMaterial.flags.x != 0u) {
            return sampled.r * imageMaterial.tint;
        }
        let source = vec4f(sampled.rgb * sampled.a, sampled.a);
        return vec4f(
            source.rgb * imageMaterial.tint.rgb,
            source.a * imageMaterial.tint.a,
        );
    }
""".trimIndent()

private fun wrapMaterialSource(source: String, sourceEntryPoint: String): String =
    "$source\n\n${materialStageWrapper(sourceEntryPoint)}"

private fun materialStageWrapper(sourceEntryPoint: String): String = """
    struct PreparedMaterialVertexOutput {
        @builtin(position) position: vec4<f32>,
        @location(0) uv: vec2<f32>,
    }

    @vertex
    fn vs_main(@builtin(vertex_index) vertexIndex: u32) -> PreparedMaterialVertexOutput {
        let positions = array<vec2<f32>, 3>(
            vec2f(-1.0, -1.0),
            vec2f(3.0, -1.0),
            vec2f(-1.0, 3.0),
        );
        let position = positions[vertexIndex];
        var output: PreparedMaterialVertexOutput;
        output.position = vec4f(position, 0.0, 1.0);
        output.uv = vec2f(position.x * 0.5 + 0.5, 1.0 - (position.y * 0.5 + 0.5));
        return output;
    }

    @fragment
    fn fs_main(input: PreparedMaterialVertexOutput) -> @location(0) vec4<f32> {
        return $sourceEntryPoint(input.uv);
    }
""".trimIndent()

private fun FloatArray.toByteArray(): ByteArray =
    ByteBuffer.allocate(size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
        forEach(::putFloat)
    }.array()

private fun exactRgbaByteCount(width: Int, height: Int): Long? {
    if (width <= 0 || height <= 0) return null
    return try {
        Math.multiplyExact(Math.multiplyExact(width.toLong(), height.toLong()), 4L)
    } catch (_: ArithmeticException) {
        null
    }
}

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

private const val FINAL_FRAGMENT_ENTRY_POINT = "fs_main"
private const val IMAGE_UNIFORM_LAYOUT_HASH = "layout:prepared-material-image:v1"
private val SUPPORTED_TILE_MODES = setOf("clamp", "repeat", "mirror", "decal")
private val SUPPORTED_IMAGE_FILTERS = setOf("nearest", "linear")
