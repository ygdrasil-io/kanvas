package org.graphiks.kanvas.gpu.renderer.materials

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUPreparedBlenderChildDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUPreparedColorFilterChildDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURuntimeEffectChildDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURuntimeEffectChildRole
import org.graphiks.kanvas.gpu.renderer.commands.GPURuntimeEffectUniformValue
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialFragment
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialFragmentAdmission
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialProgramAdmission
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialSampledBinding
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialUniformBinding
import org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedRuntimeEffectChildCpuProgram
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlanner
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendSpecializationRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageConsumption
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceAlphaClassification
import org.graphiks.kanvas.gpu.renderer.passes.GPUTargetBlendFacts
import org.graphiks.kanvas.gpu.renderer.wgsl.BitmapShaderWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.hasMaterialColorFunctionSignature
import org.graphiks.kanvas.gpu.renderer.wgsl.reflectWgslModule
import org.graphiks.wgsl.parser.Lowerer
import org.graphiks.wgsl.parser.parseWgslResult

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
        val preCoverageSourceAlpha = if (
            descriptor is GPUMaterialDescriptor.SolidColor &&
            descriptor.a == 1f &&
            paintAlpha == 1f
        ) {
            GPUSourceAlphaClassification.ProvenOpaque
        } else {
            GPUSourceAlphaClassification.Translucent
        }
        val finalReflection = when (
            val validation = validateFinalModule(prepared)
        ) {
            is FinalModuleValidation.Ready -> validation.reflection
            is FinalModuleValidation.Refused ->
                return refused(
                    code = "unsupported.material.wgsl_validation",
                    sourceKind = prepared.sourceKind,
                    message = validation.message,
                )
        }
        val uniformBinding = prepared.uniformBytes.takeIf { it.isNotEmpty() }?.let {
            GPUPreparedMaterialUniformBinding(minBindingSizeBytes = it.size)
        }
        val sampledBindings = canonicalSampledBindings(prepared.sampledResources.size)
        val evaluationFunctionWgsl = materialEvaluationFunctionWgsl(
            sourceFunction = prepared.sourceFunction,
            sourceColorContract = prepared.sourceColorContract,
        )
        val fragmentAdmission = when (
            val validation = validateComposableFragment(
                prepared = prepared,
                evaluationFunctionWgsl = evaluationFunctionWgsl,
                uniformBinding = uniformBinding,
                sampledBindings = sampledBindings,
            )
        ) {
            is ComposableFragmentValidation.Ready -> validation.admission
            is ComposableFragmentValidation.Refused ->
                return refused(
                    code = "unsupported.material.wgsl_validation",
                    sourceKind = prepared.sourceKind,
                    message = validation.message,
                )
        }
        val uniformSnapshot = immutableList(prepared.uniformBytes.map { it.toInt() and 0xff })
        val resourceSnapshot = immutableList(prepared.sampledResources)
        val admission = GPUPreparedMaterialProgramAdmission.issueValidated(
            fragmentAdmission = fragmentAdmission,
            wgslSource = prepared.wgslSource,
            entryPoint = prepared.entryPoint,
            sourceKind = prepared.sourceKind,
            uniformLayoutHash = prepared.uniformLayoutHash,
            uniformBytes = uniformSnapshot,
            sampledResources = resourceSnapshot,
            childPrograms = prepared.childPrograms,
            paintAlpha = paintAlpha,
            preCoverageSourceAlpha = preCoverageSourceAlpha,
            capabilityClass = context.capabilityClass,
            targetFormatClass = context.targetFormatClass,
            dictionaryVersion = context.dictionaryVersion,
            keyFacts = prepared.keyFacts,
            registeredAbiFacts = prepared.abiFacts,
            reflectedAbi = finalReflection,
        )

        return GPUPreparedMaterialProgramResult.Ready(
            GPUPreparedMaterialProgram.createAuthenticated(
                wgslSource = prepared.wgslSource,
                entryPoint = prepared.entryPoint,
                uniformBytes = uniformSnapshot,
                sampledResources = resourceSnapshot,
                childPrograms = prepared.childPrograms,
                paintAlpha = paintAlpha,
                sourceKind = prepared.sourceKind,
                preCoverageSourceAlpha = preCoverageSourceAlpha,
                admission = admission,
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
            is GPUMaterialDescriptor.Unsupported -> refusedSource(
                code = descriptor.reason.diagnosticCode,
                sourceKind = descriptor.sourceKind(),
                message = descriptor.reason.diagnosticMessage,
            )
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
                colorSpecLabel = "srgb-straight-rgba-f32",
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
        val alpha = descriptor.a
        val uniforms = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
            putFloat(preparedMaterialSrgbToLinear(descriptor.r) * alpha)
            putFloat(preparedMaterialSrgbToLinear(descriptor.g) * alpha)
            putFloat(preparedMaterialSrgbToLinear(descriptor.b) * alpha)
            putFloat(alpha)
        }.array()

        return PreparedSourceResult.Ready(
            PreparedSource(
                wgslSource = solidMaterialWgsl(),
                entryPoint = FINAL_FRAGMENT_ENTRY_POINT,
                composableDeclarationsWgsl = solidComposableDeclarationsWgsl(),
                sourceFunction = MATERIAL_SOURCE_FUNCTION,
                sourceColorContract = PreparedSourceColorContract.LinearPremultipliedRgba,
                uniformBytes = uniforms,
                sampledResources = emptyList(),
                sourceKind = GPUMaterialSourceKind.SolidColor,
                uniformLayoutHash = GPUSolidMaterialDictionary.SolidMaterialLayoutHash,
                abiExpectation = solidAbiExpectation(),
                keyFacts = listOf(
                    "lowererKey=${lowererKey.value}",
                    "solidSemantics=linear-premultiplied-rgba-f32",
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
                composableDeclarationsWgsl = shader.composableDeclarationsWgsl,
                sourceFunction = MATERIAL_SOURCE_FUNCTION,
                sourceColorContract = PreparedSourceColorContract.LinearPremultipliedRgba,
                uniformBytes = uniformBytes,
                sampledResources = emptyList(),
                sourceKind = GPUMaterialSourceKind.Gradient,
                uniformLayoutHash = shader.uniformLayoutHash,
                abiExpectation = gradientAbiExpectation(normalized),
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
            putFloat(preparedMaterialSrgbToLinear(descriptor.tintR) * tintAlpha)
            putFloat(preparedMaterialSrgbToLinear(descriptor.tintG) * tintAlpha)
            putFloat(preparedMaterialSrgbToLinear(descriptor.tintB) * tintAlpha)
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
                composableDeclarationsWgsl = imageComposableDeclarationsWgsl(),
                sourceFunction = MATERIAL_SOURCE_FUNCTION,
                sourceColorContract = PreparedSourceColorContract.LinearPremultipliedRgba,
                uniformBytes = uniforms,
                sampledResources = listOf(sampled),
                sourceKind = GPUMaterialSourceKind.ImageShader,
                uniformLayoutHash = IMAGE_UNIFORM_LAYOUT_HASH,
                abiExpectation = imageAbiExpectation(),
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
        val childPrograms = when (
            val compilation = prepareRuntimeEffectChildren(
                slots = program.childSlots,
                supplied = descriptor.childDescriptors,
                context = context,
            )
        ) {
            is RuntimeEffectChildCompilation.Ready -> compilation.children
            is RuntimeEffectChildCompilation.Refused ->
                return runtimeEffectChildRefusal(compilation.code, compilation.message)
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
        val childDeclarations = mergePreparedRuntimeEffectWgsl(
            childPrograms
                .filter { child -> child.role != GPUPreparedRuntimeEffectChildRole.Shader }
                .map { child -> child.wgslSource },
        )
        val executableSource = mergePreparedRuntimeEffectWgsl(
            listOf(program.wgslSource, childDeclarations),
        )

        return PreparedSourceResult.Ready(
            PreparedSource(
                wgslSource = wrapMaterialSource(executableSource, program.sourceFunction),
                entryPoint = FINAL_FRAGMENT_ENTRY_POINT,
                composableDeclarationsWgsl = runtimeEffectComposableDeclarationsWgsl(
                    program = program,
                    childDeclarations = childDeclarations,
                ),
                sourceFunction = MATERIAL_SOURCE_FUNCTION,
                sourceColorContract = program.sourceColorContract.toPreparedSourceColorContract(),
                uniformBytes = uniformBytes,
                sampledResources = emptyList(),
                childPrograms = childPrograms,
                sourceKind = GPUMaterialSourceKind.RuntimeEffect,
                uniformLayoutHash = program.uniformSchemaHash,
                abiExpectation = runtimeEffectAbiExpectation(program),
                keyFacts = listOf(
                    "lowererKey=${lowererKey.value}",
                    "runtimeEffect=${program.effectId}@${program.descriptorVersion}",
                    "runtimeSourceColorContract=${program.sourceColorContract.name}",
                    "runtimeSource=${program.sourceHash}",
                    "runtimeModule=${program.moduleHash}",
                    "runtimeReflection=${program.reflectionHash}",
                    "runtimeBindings=${program.bindingPlanHash}",
                    "runtimeRoute=${program.routeContractHash}",
                    *program.childSlots.mapIndexed { index, slot ->
                        "runtimeSlot[$index]=${slot.name}:${slot.role.name}:" +
                            "${slot.bindingIndex}:${slot.abiHash}"
                    }.toTypedArray(),
                ),
                abiFacts = buildList {
                    add("runtimeModule=${program.moduleHash}")
                    add("runtimeReflection=${program.reflectionHash}")
                    add("runtimeBindings=${program.bindingPlanHash}")
                    add("runtimeRoute=${program.routeContractHash}")
                    add("runtimeSourceColorContract=${program.sourceColorContract.name}")
                    program.childSlots.forEachIndexed { index, slot ->
                        add(
                            "runtimeSlot[$index]=${slot.name}:${slot.role.name}:" +
                                "${slot.bindingIndex}:${slot.abiHash}",
                        )
                    }
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
        val destinationDescriptor = descriptor.storedDst
        val sourceDescriptor = descriptor.storedSrc
        if (
            destinationDescriptor is GPUMaterialDescriptor.ImageDraw ||
            sourceDescriptor is GPUMaterialDescriptor.ImageDraw
        ) {
            return blendRefusal(
                "Prepared blend image children are refused until tint, alpha and resources are proven",
            )
        }
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

        val destination = when (val result = prepareSource(destinationDescriptor, context)) {
            is PreparedSourceResult.Ready -> result.source
            is PreparedSourceResult.Refused ->
                return blendRefusal("Destination child refused: ${result.code}")
        }
        val source = when (val result = prepareSource(sourceDescriptor, context)) {
            is PreparedSourceResult.Ready -> result.source
            is PreparedSourceResult.Refused ->
                return blendRefusal("Source child refused: ${result.code}")
        }
        if (
            !destinationDescriptor.hasExactBlendChildShape() ||
            !sourceDescriptor.hasExactBlendChildShape()
        ) {
            return blendRefusal("Blend shader child shape would lose source semantics")
        }

        val wgsl = runCatching {
            BlendWgslBuilder.buildWgsl(
                destinationDescriptor,
                sourceDescriptor,
                mode.name,
            )
        }.getOrElse { failure ->
            return blendRefusal("Blend WGSL construction failed: ${failure::class.simpleName}")
        }
        val uniforms = runCatching {
            BlendWgslBuilder.packUniforms(
                destinationDescriptor,
                sourceDescriptor,
                mode.name,
            )
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
                composableDeclarationsWgsl = BlendWgslBuilder.buildComposableDeclarationsWgsl(
                    destinationDescriptor,
                    sourceDescriptor,
                    mode.name,
                ),
                sourceFunction = MATERIAL_SOURCE_FUNCTION,
                sourceColorContract = PreparedSourceColorContract.LinearPremultipliedRgba,
                uniformBytes = uniforms,
                sampledResources = resources,
                sourceKind = GPUMaterialSourceKind.ShaderBlend,
                uniformLayoutHash = "layout:blend-material:${mode.gpuLabel}:" +
                    "${destination.uniformLayoutHash}:${source.uniformLayoutHash}:v1",
                abiExpectation = blendAbiExpectation(
                    destinationDescriptor,
                    sourceDescriptor,
                ),
                keyFacts = listOf(
                    "blendMode=${mode.gpuLabel}",
                    "blendPlan=${blendPlan::class.simpleName}",
                    "blendDestination=$destinationIdentity",
                    "blendSource=$sourceIdentity",
                ),
            ),
        )
    }

    private fun prepareRuntimeEffectChildren(
        slots: List<GPUPreparedRuntimeEffectChildSlot>,
        supplied: Map<String, GPURuntimeEffectChildDescriptor>,
        context: GPUMaterialLoweringContext,
    ): RuntimeEffectChildCompilation {
        val expectedNames = slots.map { slot -> slot.name }
        val suppliedNames = supplied.keys.toList()
        val missing = expectedNames.filterNot(supplied::containsKey)
        if (missing.isNotEmpty()) {
            return RuntimeEffectChildCompilation.Refused(
                code = "unsupported.material.runtime_effect.child_missing",
                message = "Prepared runtime-effect child is missing: ${missing.first()}",
            )
        }
        val extra = suppliedNames.filterNot(expectedNames::contains)
        if (extra.isNotEmpty()) {
            return RuntimeEffectChildCompilation.Refused(
                code = "unsupported.material.runtime_effect.child_extra",
                message = "Prepared runtime-effect child is not declared: ${extra.first()}",
            )
        }
        if (suppliedNames != expectedNames) {
            return RuntimeEffectChildCompilation.Refused(
                code = "unsupported.material.runtime_effect.child_order",
                message = "Prepared runtime-effect child order does not match registered slots",
            )
        }
        slots.forEachIndexed { index, slot ->
            if (slot.bindingIndex != index) {
                return RuntimeEffectChildCompilation.Refused(
                    code = "unsupported.material.runtime_effect.child_binding",
                    message = "Prepared runtime-effect child binding does not match slot ${slot.name}",
                )
            }
            val child = supplied.getValue(slot.name)
            val suppliedRole = child.role.toPreparedRole()
            if (suppliedRole != slot.role) {
                return RuntimeEffectChildCompilation.Refused(
                    code = "unsupported.material.runtime_effect.child_role",
                    message = "Prepared runtime-effect child role does not match slot ${slot.name}",
                )
            }
            val invocationAbi = preparedRuntimeEffectChildAbiHash(suppliedRole)
            if (slot.abiHash != invocationAbi) {
                return RuntimeEffectChildCompilation.Refused(
                    code = "unsupported.material.runtime_effect.child_abi",
                    message = "Prepared runtime-effect child ABI does not match slot ${slot.name}",
                )
            }
        }

        val children = ArrayList<GPUPreparedRuntimeEffectChildProgram>(slots.size)
        slots.forEach { slot ->
            val child = supplied.getValue(slot.name)
            val compiled = when (child) {
                is GPURuntimeEffectChildDescriptor.Shader ->
                    compileShaderRuntimeChild(slot.name, child, context)
                is GPURuntimeEffectChildDescriptor.ColorFilter ->
                    compileColorFilterRuntimeChild(
                        GPUPreparedRuntimeEffectChildPath.Root(slot.name),
                        child.filter,
                        context,
                    )
                is GPURuntimeEffectChildDescriptor.Blender ->
                    compileBlenderRuntimeChild(
                        GPUPreparedRuntimeEffectChildPath.Root(slot.name),
                        child.blender,
                    )
            }
            when (compiled) {
                is RuntimeEffectSingleChildCompilation.Ready -> children += compiled.child
                is RuntimeEffectSingleChildCompilation.Refused ->
                    return RuntimeEffectChildCompilation.Refused(
                        code = "unsupported.material.runtime_effect.child_program",
                        message = "Prepared runtime-effect child ${slot.name} refused: " +
                            compiled.message,
                    )
            }
        }
        return RuntimeEffectChildCompilation.Ready(immutableList(children))
    }

    private fun compileShaderRuntimeChild(
        name: String,
        child: GPURuntimeEffectChildDescriptor.Shader,
        context: GPUMaterialLoweringContext,
    ): RuntimeEffectSingleChildCompilation = when (
        val result = compile(child.storedMaterial, paintAlpha = 1f, context = context)
    ) {
        is GPUPreparedMaterialProgramResult.Refused ->
            RuntimeEffectSingleChildCompilation.Refused(result.code)
        is GPUPreparedMaterialProgramResult.Ready -> {
            val program = result.program
            val composable = program.composableFragment
            val childSource = listOf(
                composable.declarationsWgsl,
                composable.evaluationFunctionWgsl,
            ).joinToString("\n\n")
            RuntimeEffectSingleChildCompilation.Ready(
                GPUPreparedRuntimeEffectChildProgram(
                    name = name,
                    role = GPUPreparedRuntimeEffectChildRole.Shader,
                    programKey = program.materialKey,
                    abiHash = program.abiHash,
                    uniformBytes = immutableList(
                        program.uniformBytes +
                            program.childPrograms.flatMap { nested -> nested.uniformBytes },
                    ),
                    resourceFacts = immutableList(
                        program.sampledResources.flatMapIndexed { index, resource ->
                            resource.identityFacts().map { fact -> "resource[$index].$fact" }
                        } + program.childPrograms.flatMapIndexed { childIndex, nested ->
                            nested.resourceFacts.map { fact -> "child[$childIndex].$fact" }
                        },
                    ),
                    wgslSource = childSource,
                    evaluationFunction = composable.evaluationFunction,
                    cpuProgram = GPUPreparedRuntimeEffectChildCpuProgram.Shader(
                        materialKey = program.materialKey,
                    ),
                ),
            )
        }
    }

    private fun compileColorFilterRuntimeChild(
        path: GPUPreparedRuntimeEffectChildPath,
        filter: GPUPreparedColorFilterChildDescriptor,
        context: GPUMaterialLoweringContext,
    ): RuntimeEffectSingleChildCompilation = when (filter) {
        is GPUPreparedColorFilterChildDescriptor.Matrix -> {
            val program = GPUPreparedRuntimeEffectChildProgramAuthority.compileMatrix(
                path = path,
                values = filter.values,
            ) ?: return RuntimeEffectSingleChildCompilation.Refused(
                "invalid color-matrix payload",
            )
            RuntimeEffectSingleChildCompilation.Ready(program)
        }
        is GPUPreparedColorFilterChildDescriptor.Blend -> {
            val program = GPUPreparedRuntimeEffectChildProgramAuthority.compileBlendColorFilter(
                path = path,
                rgba = filter.rgba,
                mode = filter.mode,
            ) ?: return RuntimeEffectSingleChildCompilation.Refused(
                "invalid or unavailable canonical blend color-filter program",
            )
            RuntimeEffectSingleChildCompilation.Ready(program)
        }
        is GPUPreparedColorFilterChildDescriptor.Compose -> {
            val inner = compileColorFilterRuntimeChild(
                GPUPreparedRuntimeEffectChildPath.Inner(path),
                filter.inner,
                context,
            )
            if (inner is RuntimeEffectSingleChildCompilation.Refused) return inner
            val outer = compileColorFilterRuntimeChild(
                GPUPreparedRuntimeEffectChildPath.Outer(path),
                filter.outer,
                context,
            )
            if (outer is RuntimeEffectSingleChildCompilation.Refused) return outer
            val innerChild = (inner as RuntimeEffectSingleChildCompilation.Ready).child
            val outerChild = (outer as RuntimeEffectSingleChildCompilation.Ready).child
            val program = GPUPreparedRuntimeEffectChildProgramAuthority.composeColorFilters(
                path = path,
                inner = innerChild,
                outer = outerChild,
            ) ?: return RuntimeEffectSingleChildCompilation.Refused(
                "canonical composed color-filter program is unavailable",
            )
            RuntimeEffectSingleChildCompilation.Ready(program)
        }
        is GPUPreparedColorFilterChildDescriptor.RegisteredRuntimeEffect ->
            RuntimeEffectSingleChildCompilation.Refused(
                "registered color-filter CPU plus WGSL placement authority is unavailable",
            )
    }

    private fun compileBlenderRuntimeChild(
        path: GPUPreparedRuntimeEffectChildPath,
        blender: GPUPreparedBlenderChildDescriptor,
    ): RuntimeEffectSingleChildCompilation = when (blender) {
        is GPUPreparedBlenderChildDescriptor.Mode -> {
            val program = GPUPreparedRuntimeEffectChildProgramAuthority.compileModeBlender(
                path = path,
                mode = blender.mode,
            ) ?: return RuntimeEffectSingleChildCompilation.Refused(
                "canonical mode blender program is unavailable",
            )
            RuntimeEffectSingleChildCompilation.Ready(program)
        }
        is GPUPreparedBlenderChildDescriptor.Arithmetic ->
            RuntimeEffectSingleChildCompilation.Refused(
                "canonical arithmetic blender CPU plus WGSL authority is unavailable",
            )
    }

    private fun validateFinalModule(prepared: PreparedSource): FinalModuleValidation {
        val source = prepared.wgslSource
        val entryPoint = prepared.entryPoint
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
        prepared.abiExpectation.mismatch(report, prepared.uniformBytes.size)?.let { message ->
            return FinalModuleValidation.Refused(message)
        }
        return FinalModuleValidation.Ready(report)
    }

    private fun validateComposableFragment(
        prepared: PreparedSource,
        evaluationFunctionWgsl: String,
        uniformBinding: GPUPreparedMaterialUniformBinding?,
        sampledBindings: List<GPUPreparedMaterialSampledBinding>,
    ): ComposableFragmentValidation {
        val source = prepared.composableDeclarationsWgsl + "\n\n" + evaluationFunctionWgsl
        val parsed = runCatching { parseWgslResult(source) }
            .getOrElse { failure ->
                return ComposableFragmentValidation.Refused(
                    "wgsl4k fragment parser failed: ${failure::class.simpleName.orEmpty()}",
                )
            }
        if (!parsed.isSuccess) {
            return ComposableFragmentValidation.Refused(
                "wgsl4k fragment parser diagnostics: " +
                    parsed.errors.joinToString { it.message },
            )
        }
        val lowered = runCatching { Lowerer().lower(parsed.translationUnit) }
            .getOrElse { failure ->
                return ComposableFragmentValidation.Refused(
                    "wgsl4k fragment lowering failed: ${failure::class.simpleName.orEmpty()}",
                )
            }
        val report = runCatching {
            lowered.reflectWgslModule(sourceId = sha256Hex(source.encodeToByteArray()))
        }.getOrElse { failure ->
            return ComposableFragmentValidation.Refused(
                "wgsl4k fragment reflection failed: ${failure::class.simpleName.orEmpty()}",
            )
        }
        if (
            !report.validation.success ||
            report.unsupportedFeatures.isNotEmpty() ||
            !lowered.hasMaterialColorFunctionSignature(MATERIAL_EVALUATION_FUNCTION)
        ) {
            return ComposableFragmentValidation.Refused(
                "Prepared material fragment did not prove its canonical evaluation function",
            )
        }
        composableBindingMismatch(
            uniformBinding = uniformBinding,
            sampledBindings = sampledBindings,
            reflectedBindings = report.bindings,
        )?.let { message ->
            return ComposableFragmentValidation.Refused(
                message,
            )
        }
        composableUniformLayoutMismatch(
            expected = prepared.abiExpectation.uniformLayout,
            uniformBindingSize = uniformBinding?.minBindingSizeBytes,
            reflectedLayouts = report.layouts,
        )?.let { message ->
            return ComposableFragmentValidation.Refused(
                message,
            )
        }
        return ComposableFragmentValidation.Ready(
            GPUPreparedMaterialFragmentAdmission.issueValidated(
                declarationsWgsl = prepared.composableDeclarationsWgsl,
                evaluationFunctionWgsl = evaluationFunctionWgsl,
                uniformBinding = uniformBinding,
                sampledBindings = sampledBindings,
                reflectedAbi = report,
            ),
        )
    }

    private fun sampledResource(
        descriptor: GPUMaterialDescriptor.ImageDraw,
    ): SampledResourceResult {
        val rgbaSnapshot = descriptor.rgbaPixels.copyOf()
        val expectedBytes = exactRgbaByteCount(descriptor.imageWidth, descriptor.imageHeight)
            ?: return SampledResourceResult.Refused(
                "Prepared material image dimensions are invalid or overflow RGBA byte count",
            )
        if (expectedBytes != rgbaSnapshot.size.toLong()) {
            return SampledResourceResult.Refused(
                "Prepared material image byte length does not equal width * height * 4",
            )
        }
        if (descriptor.samplingFilterMode !in SUPPORTED_IMAGE_FILTERS) {
            return SampledResourceResult.Refused(
                "Prepared material image sampling must be nearest or linear",
            )
        }
        val contentHash = sha256Hex(rgbaSnapshot)
        val resourceKey = "sampled-material:" +
            CanonicalIdentityEncoder("prepared-material-sampled-resource-v2")
                .int("width", descriptor.imageWidth)
                .int("height", descriptor.imageHeight)
                .text("samplingFilterMode", descriptor.samplingFilterMode)
                .boolean("alphaOnly", descriptor.alphaOnly)
                .text("contentHash", contentHash)
                .digestHex()
        return SampledResourceResult.Ready(
            GPUPreparedMaterialSampledResource(
                width = descriptor.imageWidth,
                height = descriptor.imageHeight,
                samplingFilterMode = descriptor.samplingFilterMode,
                alphaOnly = descriptor.alphaOnly,
                rgba8Bytes = rgbaSnapshot,
                resourceKey = resourceKey,
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

    private fun runtimeEffectChildRefusal(
        code: String,
        message: String,
    ): PreparedSourceResult.Refused =
        refusedSource(
            code,
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
    val composableDeclarationsWgsl: String,
    val sourceFunction: String,
    val sourceColorContract: PreparedSourceColorContract,
    val uniformBytes: ByteArray,
    val sampledResources: List<GPUPreparedMaterialSampledResource>,
    val childPrograms: List<GPUPreparedRuntimeEffectChildProgram> = emptyList(),
    val sourceKind: GPUMaterialSourceKind,
    val uniformLayoutHash: String,
    val abiExpectation: PreparedModuleAbiExpectation,
    val keyFacts: List<String>,
    val abiFacts: List<String> = emptyList(),
) {
    fun materialShapeIdentity(): String {
        val resourceFacts = sampledResources.flatMapIndexed { index, resource ->
            resource.identityFacts().map { fact -> "resource[$index].$fact" }
        }
        return CanonicalIdentityEncoder("prepared-material-shape-v2")
            .text("sourceKind", sourceKind.name)
            .text("sourceHash", sha256Hex(wgslSource.encodeToByteArray()))
            .text("entryPoint", entryPoint)
            .text("uniformLayout", uniformLayoutHash)
            .int("uniformByteCount", uniformBytes.size)
            .text("uniformBytesHash", sha256Hex(uniformBytes))
            .texts("keyFacts", keyFacts)
            .texts("sampledResourceFacts", resourceFacts)
            .digestHex()
    }
}

internal data class PreparedModuleAbiExpectation(
    val bindings: List<PreparedAbiBinding>,
    val uniformLayout: PreparedAbiLayout,
) {
    fun mismatch(
        report: org.graphiks.kanvas.gpu.renderer.wgsl.WgslReflectionReport,
        uniformPayloadSize: Int,
    ): String? {
        val expectedEntries = listOf(
            "fs_main" to "fragment",
            "vs_main" to "vertex",
        )
        val actualEntries = report.entryPoints
            .map { entry -> entry.name to entry.stage }
            .sortedWith(compareBy({ it.first }, { it.second }))
        if (actualEntries != expectedEntries) {
            return "Prepared material entry-point stages do not match the registered ABI"
        }
        if (!report.validation.success || report.unsupportedFeatures.isNotEmpty()) {
            return "Prepared material reflection did not prove a supported ABI"
        }
        val actualBindings = report.bindings
            .map { binding ->
                PreparedAbiBinding(
                    group = binding.group,
                    binding = binding.binding,
                    resourceKind = binding.resourceKind,
                    access = binding.access,
                    sampleType = binding.sampleType,
                    viewDimension = binding.viewDimension,
                    storageFormat = binding.storageFormat,
                    minBindingSize = binding.minBindingSize,
                )
            }
            .sortedWith(compareBy({ it.group }, { it.binding }))
        if (actualBindings != bindings.sortedWith(compareBy({ it.group }, { it.binding }))) {
            return "Prepared material resource topology does not match the registered ABI"
        }
        val uniformLayouts = report.layouts.filter { layout -> layout.addressSpace == "uniform" }
        if (report.layouts.size != 1 || uniformLayouts.size != 1) {
            return "Prepared material must reflect exactly one registered uniform layout"
        }
        val actualLayout = uniformLayouts.single().let { layout ->
            PreparedAbiLayout(
                size = layout.size,
                alignment = layout.alignment,
                members = layout.members.map { member ->
                    PreparedAbiMember(
                        name = member.name,
                        type = member.type,
                        offset = member.offset,
                        size = member.size,
                        alignment = member.alignment,
                        stride = member.stride,
                    )
                },
            )
        }
        if (actualLayout != uniformLayout) {
            return "Prepared material uniform layout does not match the registered ABI"
        }
        if (uniformPayloadSize != uniformLayout.size) {
            return "Prepared material uniform payload size does not match the reflected ABI"
        }
        return null
    }
}

internal data class PreparedAbiBinding(
    val group: Int,
    val binding: Int,
    val resourceKind: String,
    val access: String?,
    val sampleType: String? = null,
    val viewDimension: String? = null,
    val storageFormat: String? = null,
    val minBindingSize: Int? = null,
)

internal fun composableBindingMismatch(
    uniformBinding: GPUPreparedMaterialUniformBinding?,
    sampledBindings: List<GPUPreparedMaterialSampledBinding>,
    reflectedBindings: List<org.graphiks.kanvas.gpu.renderer.wgsl.WgslBindingReflection>,
): String? {
    val expectedBindings = buildList {
        uniformBinding?.let {
            add(
                PreparedAbiBinding(
                    group = it.group,
                    binding = it.binding,
                    resourceKind = "uniformBuffer",
                    access = "read",
                    minBindingSize = it.minBindingSizeBytes,
                ),
            )
        }
        sampledBindings.forEach {
            add(
                PreparedAbiBinding(
                    group = it.textureGroup,
                    binding = it.textureBinding,
                    resourceKind = "sampledTexture",
                    access = "read",
                    sampleType = "float",
                    viewDimension = "2d",
                ),
            )
            add(
                PreparedAbiBinding(
                    group = it.samplerGroup,
                    binding = it.samplerBinding,
                    resourceKind = "sampler",
                    access = "read",
                ),
            )
        }
    }
    val actualBindings = reflectedBindings.map {
        PreparedAbiBinding(
            group = it.group,
            binding = it.binding,
            resourceKind = it.resourceKind,
            access = it.access,
            sampleType = it.sampleType,
            viewDimension = it.viewDimension,
            storageFormat = it.storageFormat,
            minBindingSize = it.minBindingSize,
        )
    }
    return if (
        actualBindings.sortedWith(compareBy({ it.group }, { it.binding })) ==
        expectedBindings.sortedWith(compareBy({ it.group }, { it.binding }))
    ) {
        null
    } else {
        "Prepared material fragment bindings do not match the canonical schema"
    }
}

internal data class PreparedAbiLayout(
    val size: Int,
    val alignment: Int,
    val members: List<PreparedAbiMember>,
)

internal data class PreparedAbiMember(
    val name: String,
    val type: String,
    val offset: Int,
    val size: Int,
    val alignment: Int,
    val stride: Int? = null,
)

internal fun composableUniformLayoutMismatch(
    expected: PreparedAbiLayout?,
    uniformBindingSize: Int?,
    reflectedLayouts: List<org.graphiks.kanvas.gpu.renderer.wgsl.WgslLayoutReflection>,
): String? {
    val uniformLayouts = reflectedLayouts.filter { layout -> layout.addressSpace == "uniform" }
    if (expected == null || uniformBindingSize == null) {
        return if (
            expected == null &&
            uniformBindingSize == null &&
            reflectedLayouts.isEmpty()
        ) {
            null
        } else {
            "Prepared material fragment reflects an unexpected uniform layout"
        }
    }
    if (reflectedLayouts.size != 1 || uniformLayouts.size != 1) {
        return "Prepared material fragment must reflect exactly one canonical uniform layout"
    }
    val actual = uniformLayouts.single().let { layout ->
        PreparedAbiLayout(
            size = layout.size,
            alignment = layout.alignment,
            members = layout.members.map { member ->
                PreparedAbiMember(
                    name = member.name,
                    type = member.type,
                    offset = member.offset,
                    size = member.size,
                    alignment = member.alignment,
                    stride = member.stride,
                )
            },
        )
    }
    if (actual != expected) {
        return "Prepared material fragment uniform layout does not match the registered ABI"
    }
    if (uniformBindingSize != expected.size) {
        return "Prepared material fragment uniform binding size does not match the registered ABI"
    }
    return null
}

private fun solidAbiExpectation(): PreparedModuleAbiExpectation =
    uniformAbiExpectation(
        group = 1,
        binding = 0,
        size = 16,
        members = listOf(vec4Member("color", 0)),
    )

private fun gradientAbiExpectation(
    descriptor: GPUMaterialDescriptor,
): PreparedModuleAbiExpectation {
    val stopData = PreparedAbiMember(
        name = "stopData",
        type = "array<vec4<f32>, 32>",
        offset = when (descriptor) {
            is GPUMaterialDescriptor.RadialGradient -> 16
            is GPUMaterialDescriptor.LinearGradient,
            is GPUMaterialDescriptor.SweepGradient,
            -> 32
            is GPUMaterialDescriptor.ConicalGradient -> 48
            else -> error("Not a gradient descriptor")
        },
        size = 512,
        alignment = 16,
        stride = 16,
    )
    val members = when (descriptor) {
        is GPUMaterialDescriptor.LinearGradient -> listOf(
            vec2Member("start", 0),
            vec2Member("end", 8),
            u32Member("count", 16),
            stopData,
        )
        is GPUMaterialDescriptor.RadialGradient -> listOf(
            vec2Member("center", 0),
            f32Member("radius", 8),
            u32Member("count", 12),
            stopData,
        )
        is GPUMaterialDescriptor.SweepGradient -> listOf(
            vec2Member("center", 0),
            f32Member("startAngle", 8),
            f32Member("endAngle", 12),
            u32Member("count", 16),
            stopData,
        )
        is GPUMaterialDescriptor.ConicalGradient -> listOf(
            vec2Member("start", 0),
            vec2Member("end", 8),
            f32Member("r1", 16),
            f32Member("r2", 20),
            u32Member("count", 24),
            u32Member("_pad0", 28),
            u32Member("_pad1", 32),
            u32Member("_pad2", 36),
            u32Member("_pad3", 40),
            u32Member("_pad4", 44),
            stopData,
        )
    }
    val size = stopData.offset + stopData.size
    return uniformAbiExpectation(group = 0, binding = 0, size = size, members = members)
}

private fun imageAbiExpectation(): PreparedModuleAbiExpectation =
    uniformAbiExpectation(
        group = 1,
        binding = 0,
        size = 32,
        members = listOf(
            vec4Member("tint", 0),
            PreparedAbiMember("flags", "vec4<u32>", 16, 16, 16),
        ),
        resourceBindings = listOf(
            PreparedAbiBinding(
                group = 1,
                binding = 1,
                resourceKind = "sampledTexture",
                access = "read",
                sampleType = "float",
                viewDimension = "2d",
            ),
            PreparedAbiBinding(
                group = 1,
                binding = 2,
                resourceKind = "sampler",
                access = "read",
            ),
        ),
    )

private fun runtimeEffectAbiExpectation(
    program: GPUPreparedRuntimeEffectProgram,
): PreparedModuleAbiExpectation =
    PreparedModuleAbiExpectation(
        bindings = program.bindings.map { binding ->
            PreparedAbiBinding(
                group = binding.group,
                binding = binding.binding,
                resourceKind = binding.resourceKind,
                access = "read",
                minBindingSize = binding.minBindingSizeBytes,
            )
        },
        uniformLayout = PreparedAbiLayout(
            size = program.uniformBlockSizeBytes,
            alignment = program.uniformFields.maxOfOrNull { field -> field.alignmentBytes } ?: 1,
            members = program.uniformFields.map { field ->
                PreparedAbiMember(
                    name = field.name,
                    type = field.type.wgslTypeName(),
                    offset = field.offsetBytes,
                    size = field.sizeBytes,
                    alignment = field.alignmentBytes,
                    stride = field.strideBytes,
                )
            },
        ),
    )

private fun blendAbiExpectation(
    destination: GPUMaterialDescriptor,
    source: GPUMaterialDescriptor,
): PreparedModuleAbiExpectation {
    val members = buildList {
        addAll(blendChildAbiMembers("dst", destination, 0))
        addAll(blendChildAbiMembers("src", source, 48))
        add(u32Member("_pad0", 96))
        add(u32Member("_pad1", 100))
        add(u32Member("_pad2", 104))
    }
    return uniformAbiExpectation(group = 0, binding = 0, size = 112, members = members)
}

private fun blendChildAbiMembers(
    prefix: String,
    child: GPUMaterialDescriptor,
    baseOffset: Int,
): List<PreparedAbiMember> =
    when (child) {
        is GPUMaterialDescriptor.LinearGradient -> listOf(
            vec2Member("${prefix}_start", baseOffset),
            vec2Member("${prefix}_end", baseOffset + 8),
            vec4Member("${prefix}_color", baseOffset + 16),
            vec4Member("${prefix}_pad", baseOffset + 32),
        )
        is GPUMaterialDescriptor.RadialGradient -> listOf(
            vec2Member("${prefix}_center", baseOffset),
            f32Member("${prefix}_radius", baseOffset + 8),
            f32Member("${prefix}_pad0", baseOffset + 12),
            vec4Member("${prefix}_color", baseOffset + 16),
            vec4Member("${prefix}_pad", baseOffset + 32),
        )
        is GPUMaterialDescriptor.SweepGradient -> listOf(
            vec2Member("${prefix}_center", baseOffset),
            f32Member("${prefix}_startAngle", baseOffset + 8),
            f32Member("${prefix}_endAngle", baseOffset + 12),
            vec4Member("${prefix}_color", baseOffset + 16),
            vec4Member("${prefix}_pad", baseOffset + 32),
        )
        is GPUMaterialDescriptor.SolidColor,
        is GPUMaterialDescriptor.ImageDraw,
        -> listOf(
            vec4Member("${prefix}_color", baseOffset),
            vec4Member("${prefix}_pad", baseOffset + 16),
            vec4Member("${prefix}_pad2", baseOffset + 32),
        )
        else -> error("Unsupported blend child ABI: ${child.kind}")
    }

private fun uniformAbiExpectation(
    group: Int,
    binding: Int,
    size: Int,
    members: List<PreparedAbiMember>,
    resourceBindings: List<PreparedAbiBinding> = emptyList(),
): PreparedModuleAbiExpectation =
    PreparedModuleAbiExpectation(
        bindings = listOf(
            PreparedAbiBinding(
                group = group,
                binding = binding,
                resourceKind = "uniformBuffer",
                access = "read",
                minBindingSize = size,
            ),
        ) + resourceBindings,
        uniformLayout = PreparedAbiLayout(size = size, alignment = 16, members = members),
    )

private fun vec2Member(name: String, offset: Int): PreparedAbiMember =
    PreparedAbiMember(name, "vec2<f32>", offset, 8, 8)

private fun vec4Member(name: String, offset: Int): PreparedAbiMember =
    PreparedAbiMember(name, "vec4<f32>", offset, 16, 16)

private fun f32Member(name: String, offset: Int): PreparedAbiMember =
    PreparedAbiMember(name, "f32", offset, 4, 4)

private fun u32Member(name: String, offset: Int): PreparedAbiMember =
    PreparedAbiMember(name, "u32", offset, 4, 4)

private fun GPUPreparedRuntimeEffectUniformType.wgslTypeName(): String =
    when (this) {
        GPUPreparedRuntimeEffectUniformType.Float1 -> "f32"
        GPUPreparedRuntimeEffectUniformType.Float2 -> "vec2<f32>"
        GPUPreparedRuntimeEffectUniformType.Float3 -> "vec3<f32>"
        GPUPreparedRuntimeEffectUniformType.Float4 -> "vec4<f32>"
        GPUPreparedRuntimeEffectUniformType.Int1 -> "i32"
        GPUPreparedRuntimeEffectUniformType.Matrix3x3 -> "mat3x3<f32>"
        GPUPreparedRuntimeEffectUniformType.Matrix4x4 -> "mat4x4<f32>"
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
    data class Ready(
        val reflection: org.graphiks.kanvas.gpu.renderer.wgsl.WgslReflectionReport,
    ) : FinalModuleValidation
    data class Refused(val message: String) : FinalModuleValidation
}

private sealed interface ComposableFragmentValidation {
    data class Ready(
        val admission: GPUPreparedMaterialFragmentAdmission,
    ) : ComposableFragmentValidation
    data class Refused(val message: String) : ComposableFragmentValidation
}

private sealed interface RuntimeEffectChildCompilation {
    data class Ready(
        val children: List<GPUPreparedRuntimeEffectChildProgram>,
    ) : RuntimeEffectChildCompilation

    data class Refused(
        val code: String,
        val message: String,
    ) : RuntimeEffectChildCompilation
}

private sealed interface RuntimeEffectSingleChildCompilation {
    data class Ready(
        val child: GPUPreparedRuntimeEffectChildProgram,
    ) : RuntimeEffectSingleChildCompilation

    data class Refused(val message: String) : RuntimeEffectSingleChildCompilation
}

private fun GPURuntimeEffectChildRole.toPreparedRole(): GPUPreparedRuntimeEffectChildRole =
    when (this) {
        GPURuntimeEffectChildRole.Shader -> GPUPreparedRuntimeEffectChildRole.Shader
        GPURuntimeEffectChildRole.ColorFilter -> GPUPreparedRuntimeEffectChildRole.ColorFilter
        GPURuntimeEffectChildRole.Blender -> GPUPreparedRuntimeEffectChildRole.Blender
    }

private enum class PreparedSourceColorContract {
    LinearStraightRgba,
    LinearPremultipliedRgba,
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
    is GPUMaterialDescriptor.Unsupported -> when (kind) {
        org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialKind.SolidColor ->
            GPUMaterialSourceKind.SolidColor
        org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialKind.LinearGradient,
        org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialKind.RadialGradient,
        org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialKind.SweepGradient,
        org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialKind.TwoPointConical,
        -> GPUMaterialSourceKind.Gradient
        org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialKind.ImageDraw ->
            GPUMaterialSourceKind.ImageShader
        org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialKind.RuntimeEffect ->
            GPUMaterialSourceKind.RuntimeEffect
        org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialKind.ShaderBlend ->
            GPUMaterialSourceKind.ShaderBlend
    }
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
                payloadHash = CanonicalIdentityEncoder("prepared-gradient-stop-payload-v2")
                    .bytes("positions", positions.toByteArray())
                    .bytes("colors", colors.toByteArray())
                    .digestHex(),
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
    is GPUMaterialDescriptor.SolidColor -> true
    is GPUMaterialDescriptor.LinearGradient,
    is GPUMaterialDescriptor.RadialGradient,
    is GPUMaterialDescriptor.SweepGradient,
    is GPUMaterialDescriptor.ConicalGradient,
    is GPUMaterialDescriptor.ImageDraw,
    is GPUMaterialDescriptor.RuntimeEffect,
    is GPUMaterialDescriptor.BlendShader,
    is GPUMaterialDescriptor.Unsupported,
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

private fun solidComposableDeclarationsWgsl(): String = """
    struct SolidMaterialBlock {
        color: vec4<f32>,
    }
    @group(1) @binding(0) var<uniform> solidMaterial: SolidMaterialBlock;

    fn kanvas_material_source(localPosition: vec2<f32>) -> vec4<f32> {
        return solidMaterial.color;
    }
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
            return sampled.a * imageMaterial.tint;
        }
        let source = vec4f(sampled.rgb * sampled.a, sampled.a);
        return vec4f(
            source.rgb * imageMaterial.tint.rgb,
            source.a * imageMaterial.tint.a,
        );
    }
""".trimIndent()

private fun imageComposableDeclarationsWgsl(): String = """
    struct PreparedMaterialImageUniforms {
        tint: vec4<f32>,
        flags: vec4<u32>,
    }
    @group(1) @binding(0) var<uniform> imageMaterial: PreparedMaterialImageUniforms;

    $BitmapShaderWgsl

    fn kanvas_material_source(localPosition: vec2<f32>) -> vec4<f32> {
        let sampled = bitmap_shader_clamp(localPosition);
        if (imageMaterial.flags.x != 0u) {
            return sampled.a * imageMaterial.tint;
        }
        let source = vec4f(sampled.rgb * sampled.a, sampled.a);
        return vec4f(
            source.rgb * imageMaterial.tint.rgb,
            source.a * imageMaterial.tint.a,
        );
    }
""".trimIndent()

private fun runtimeEffectComposableDeclarationsWgsl(
    program: GPUPreparedRuntimeEffectProgram,
    childDeclarations: String = "",
): String = mergePreparedRuntimeEffectWgsl(
    listOf(
        program.wgslSource,
        childDeclarations,
        """
            fn kanvas_material_source(localPosition: vec2<f32>) -> vec4<f32> {
                return ${program.sourceFunction}(localPosition);
            }
        """.trimIndent(),
    ),
)

private fun materialEvaluationFunctionWgsl(
    sourceFunction: String,
    sourceColorContract: PreparedSourceColorContract,
): String {
    val normalization = when (sourceColorContract) {
        PreparedSourceColorContract.LinearStraightRgba ->
            "return vec4<f32>(source.rgb * source.a, source.a);"
        PreparedSourceColorContract.LinearPremultipliedRgba -> "return source;"
    }
    return """
        fn kanvas_normalize_material_to_premul(source: vec4<f32>) -> vec4<f32> {
            $normalization
        }

        fn kanvas_evaluate_material(localPosition: vec2<f32>) -> vec4<f32> {
            let source = $sourceFunction(localPosition);
            return kanvas_normalize_material_to_premul(source);
        }
    """.trimIndent()
}

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

private fun canonicalSampledBindings(
    sampledResourceCount: Int,
): List<GPUPreparedMaterialSampledBinding> =
    List(sampledResourceCount) { resourceIndex ->
        val textureBinding = Math.addExact(1, Math.multiplyExact(resourceIndex, 2))
        val samplerBinding = Math.addExact(textureBinding, 1)
        GPUPreparedMaterialSampledBinding(
            resourceIndex = resourceIndex,
            textureBinding = textureBinding,
            samplerBinding = samplerBinding,
        )
    }

private fun GPUPreparedRuntimeEffectSourceColorContract.toPreparedSourceColorContract():
    PreparedSourceColorContract =
    when (this) {
        GPUPreparedRuntimeEffectSourceColorContract.LinearStraightRgba ->
            PreparedSourceColorContract.LinearStraightRgba
        GPUPreparedRuntimeEffectSourceColorContract.LinearPremultipliedRgba ->
            PreparedSourceColorContract.LinearPremultipliedRgba
    }

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
private const val MATERIAL_SOURCE_FUNCTION = "kanvas_material_source"
private const val MATERIAL_EVALUATION_FUNCTION = "kanvas_evaluate_material"
private const val IMAGE_UNIFORM_LAYOUT_HASH = "layout:prepared-material-image:v1"
private val SUPPORTED_TILE_MODES = setOf("clamp", "repeat", "mirror", "decal")
private val SUPPORTED_IMAGE_FILTERS = setOf("nearest", "linear")
