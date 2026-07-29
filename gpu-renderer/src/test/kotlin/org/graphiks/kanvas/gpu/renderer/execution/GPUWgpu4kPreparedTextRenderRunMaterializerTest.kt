package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBlendFactor
import io.ygdrasil.webgpu.GPUBlendOperation
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUPipelineLayout
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUSampler
import io.ygdrasil.webgpu.GPUShaderModule
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureView
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.SamplerDescriptor
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep

class GPUWgpu4kPreparedTextRenderRunMaterializerTest {
    @Test
    fun `unique image contents share the two exact material sampler states`() {
        val fixtures = (0 until 20).map { index ->
            val filter = if (index % 2 == 0) "nearest" else "linear"
            preparedTextNativePreflightFixture(
                materialProgram = compiledPreparedTextMaterial(
                    org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.ImageDraw(
                        imageSourceId = "sampler-cache:$index",
                        imageWidth = 2,
                        imageHeight = 2,
                        rgbaPixels = ByteArray(16) { byteIndex ->
                            (index * 19 + byteIndex * 7).toByte()
                        },
                        samplingFilterMode = filter,
                        alphaOnly = false,
                    ),
                ),
            )
        }
        val plans = fixtures.map(::preparedTextTestRunPlan)
        val generation = fixtures.first().context.deviceGeneration
        val program = plans.first().bindings.first().nativeProgram
        val resources = plans.flatMap { plan ->
            plan.bindings.flatMap { binding -> binding.materialSampledResourcePlans }
        }.distinctBy { resource -> resource.resourceKey }
        assertEquals(20, resources.map { it.resourceKey }.distinct().size)
        val native = RecordingPreparedTextNative()
        val cache = GPUWgpu4kPreparedTextSessionCache(native.device, generation)

        val ready = assertIs<GPUPreparedTextCacheBatchAcquire.Ready>(
            cache.acquireBatch(
                programs = listOf(program),
                generation = generation,
                materialResourcesByPipelineKey = mapOf(program.pipelineKey to resources),
            ),
        )
        val samplers = ready.pipelinesByKey.getValue(program.pipelineKey)
            .materialSamplersByResourceKey

        assertEquals(resources.map { it.resourceKey }.toSet(), samplers.keys)
        assertEquals(2, samplers.values.toSet().size)
        assertEquals(
            GPUPreparedTextSamplerCacheSnapshot(
                residentEntryCount = 2,
                hitCount = 18,
                missCount = 2,
            ),
            cache.samplerCacheSnapshot(),
        )
        assertEquals(
            2,
            native.samplerDescriptors.count { descriptor ->
                ".material." in descriptor.label.orEmpty()
            },
        )

        cache.close()
    }

    @Test
    fun `native pipeline preserves exact target format and fixed blend matrix`() {
        listOf(GPUColorFormat.RGBA8Unorm, GPUColorFormat.RGBA8UnormSrgb).forEach { format ->
            listOf(GPUBlendMode.SRC, GPUBlendMode.SRC_OVER).forEach { mode ->
                val fixture = preparedTextNativePreflightFixture(
                    targetFormat = format,
                    blendMode = mode,
                )
                val native = RecordingPreparedTextNative()
                val cache = GPUWgpu4kPreparedTextSessionCache(
                    native.device,
                    fixture.context.deviceGeneration,
                )
                val ready = assertIs<GPUPreparedRenderRunMaterialization.Ready>(
                    GPUWgpu4kPreparedTextRenderRunMaterializer(
                        native.device,
                        cache,
                    ).materializeAcceptedRun(
                        preparedTextTestRunPlan(fixture),
                        fixture.context.deviceGeneration,
                    ),
                )
                val target = native.pipelineDescriptors.single().fragment!!.targets.single()
                assertEquals(
                    when (format) {
                        GPUColorFormat.RGBA8Unorm ->
                            io.ygdrasil.webgpu.GPUTextureFormat.RGBA8Unorm
                        GPUColorFormat.RGBA8UnormSrgb ->
                            io.ygdrasil.webgpu.GPUTextureFormat.RGBA8UnormSrgb
                        else -> error("Unexpected test target $format")
                    },
                    target.format,
                )
                val expectedDestination = when (mode) {
                    GPUBlendMode.SRC -> GPUBlendFactor.Zero
                    GPUBlendMode.SRC_OVER -> GPUBlendFactor.OneMinusSrcAlpha
                    else -> error("Unexpected test blend $mode")
                }
                assertEquals(GPUBlendFactor.One, target.blend!!.color.srcFactor)
                assertEquals(expectedDestination, target.blend!!.color.dstFactor)
                assertEquals(GPUBlendOperation.Add, target.blend!!.color.operation)
                assertEquals(GPUBlendFactor.One, target.blend!!.alpha.srcFactor)
                assertEquals(expectedDestination, target.blend!!.alpha.dstFactor)
                assertEquals(GPUBlendOperation.Add, target.blend!!.alpha.operation)

                ready.ownedResources.single().close()
                cache.close()
            }
        }
    }

    @Test
    fun `solid gradient image and registered runtime effect bind the exact material matrix`() {
        val rows = listOf(
            "solid" to GPUPreparedTextPreflightFixture.baselineMaterialProgram(),
            "gradient" to compiledPreparedTextMaterial(
                org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.LinearGradient(
                    startX = 0f,
                    startY = 0f,
                    endX = 16f,
                    endY = 4f,
                    startR = 1f,
                    startG = 0f,
                    startB = 0f,
                    startA = 1f,
                    endR = 0f,
                    endG = 0f,
                    endB = 1f,
                    endA = 0.5f,
                    allStopPositions = floatArrayOf(0f, 1f),
                    allStopColors =
                        floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f, 1f, 0.5f),
                ),
            ),
            "image" to sampledPreparedTextMaterialProgram("matrix:image"),
            "runtime" to compiledPreparedTextMaterial(
                org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.RuntimeEffect(
                    effectId = "runtime.simple_rt",
                    descriptorVersion = 1,
                    uniforms = mapOf(
                        "gColor" to
                            org.graphiks.kanvas.gpu.renderer.commands
                                .GPURuntimeEffectUniformValue.Float4(
                                    0.25f,
                                    0.5f,
                                    0.75f,
                                    0.8f,
                                ),
                    ),
                ),
            ),
        )

        rows.forEach { (label, material) ->
            val fixture = preparedTextNativePreflightFixture(materialProgram = material)
            val plan = preparedTextTestRunPlan(fixture)
            val generation = fixture.context.deviceGeneration
            val native = RecordingPreparedTextNative()
            val cache = GPUWgpu4kPreparedTextSessionCache(native.device, generation)
            val ready = assertIs<GPUPreparedRenderRunMaterialization.Ready>(
                GPUWgpu4kPreparedTextRenderRunMaterializer(
                    native.device,
                    cache,
                ).materializeAcceptedRun(plan, generation),
                label,
            )
            val acquisition = assertIs<GPUPreparedTextCacheBatchAcquire.Ready>(
                cache.acquireBatch(
                    programs = plan.bindings.map { it.nativeProgram },
                    generation = generation,
                    materialResourcesByPipelineKey = plan.bindings
                        .groupBy { binding -> binding.nativeProgram.pipelineKey }
                        .mapValues { (_, bindings) ->
                            bindings.flatMap { binding ->
                                binding.materialSampledResourcePlans
                            }.distinctBy { resource -> resource.resourceKey }
                        },
                ),
            )
            assertEquals(
                plan.bindings.map { it.nativeProgram.pipelineKey }.toSet(),
                acquisition.pipelinesByKey.keys,
                label,
            )
            if (label == "image") {
                val samplers = acquisition.pipelinesByKey.values.single()
                    .materialSamplersByResourceKey
                assertEquals(
                    plan.bindings.first().materialSampledResourcePlans
                        .map { it.resourceKey }.toSet(),
                    samplers.keys,
                )
                @Suppress("UNCHECKED_CAST")
                assertFailsWith<UnsupportedOperationException> {
                    (samplers as MutableMap<String, GPUSampler>).clear()
                }
            }
            val uploads = ready.scopeOperands
                .filterIsInstance<GPUPreparedNativeScopeOperand.TextureUpload>()
            val sampledPlans = plan.bindings
                .flatMap { it.materialSampledResourcePlans }
                .distinctBy { it.resourceKey }
            assertEquals(sampledPlans.size, uploads.count {
                it.uploadRole.startsWith("material:")
            }, label)
            assertEquals(
                if (label == "image") 1 else 0,
                uploads.count { it.uploadRole.startsWith("material:") },
                label,
            )
            ready.scopeOperands
                .filterIsInstance<GPUPreparedNativeScopeOperand.PreparedTextRenderRun>()
                .forEachIndexed { index, run ->
                    val groups = run.commands
                        .filterIsInstance<GPUPreparedNativeRenderCommand.SetBindGroup>()
                    assertEquals(listOf(0, 1, 2), groups.map { it.index }, label)
                    assertEquals(
                        listOf(plan.bindings[index].materialUniformOffsetBytes),
                        groups.single { it.index == 1 }.dynamicOffsets,
                        label,
                    )
                }

            ready.ownedResources.single().close()
            cache.close()
        }
    }

    @Test
    fun `one frame-global text batch writes once and draws 64 then 36 instances`() {
        val fixture = preparedTextNativePreflightFixture(
            textInstanceCounts = listOf(64, 36),
        )
        val generation = fixture.context.deviceGeneration
        val native = RecordingPreparedTextNative()
        val cache = GPUWgpu4kPreparedTextSessionCache(native.device, generation)
        val plan = preparedTextTestRunPlan(fixture)

        val ready = assertIs<GPUPreparedRenderRunMaterialization.Ready>(
            GPUWgpu4kPreparedTextRenderRunMaterializer(
                device = native.device,
                sessionCache = cache,
            ).materializeAcceptedRun(plan, generation),
        )

        assertEquals(1, native.createdInstanceBuffers.size)
        assertEquals(1, native.createdDrawUniformBuffers.size)
        assertEquals(1, native.createdMaterialUniformBuffers.size)
        assertEquals(1, native.createdR8Textures.size)
        assertEquals(
            1,
            ready.scopeOperands.filterIsInstance<
                GPUPreparedNativeScopeOperand.TextureUpload
                >().count { it.uploadRole == "text-atlas" },
        )
        assertEquals(
            listOf("instances", "draw-uniforms", "material-uniforms"),
            ready.uniformUploads.map { it.uploadRole },
        )
        val runCommands = ready.scopeOperands
            .filterIsInstance<GPUPreparedNativeScopeOperand.PreparedTextRenderRun>()
            .flatMap { it.commands }
        val draws = runCommands
            .filterIsInstance<GPUPreparedNativeRenderCommand.Draw>()
            .map { it.drawCall }
        assertEquals(listOf(64, 36), draws.map { it.instanceCount })
        assertEquals(listOf(0, 64), draws.map { it.firstInstance })
        assertTrue(draws.all { it.vertexCount == 6 && it.firstVertex == 0 })
        val vertexBindings = runCommands
            .filterIsInstance<GPUPreparedNativeRenderCommand.SetVertexBuffer>()
        assertTrue(vertexBindings.all { it.offset == 0L && it.vertexStrideBytes == 64L })
        val scissors = runCommands
            .filterIsInstance<GPUPreparedNativeRenderCommand.SetScissor>()
        assertEquals(
            plan.packets.map { packet -> packet.scissorBounds },
            scissors.map { scissor ->
                org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(
                    scissor.x,
                    scissor.y,
                    scissor.width,
                    scissor.height,
                )
            },
        )
        assertEquals(
            listOf(0, 1, 2, 0, 1, 2),
            runCommands.filterIsInstance<GPUPreparedNativeRenderCommand.SetBindGroup>()
                .map { it.index },
        )

        ready.ownedResources.single().close()
        cache.close()
    }

    @Test
    fun `permuted texture plans retain their exact upload scope identity`() {
        val fixture = preparedTextNativePreflightFixture(
            materialProgram = sampledPreparedTextMaterialProgram("mapping:permuted"),
        )
        val canonical = preparedTextTestRunPlan(fixture)
        val plan = canonical.copy(textureUploads = canonical.textureUploads.reversed())
        val native = RecordingPreparedTextNative()
        val cache = GPUWgpu4kPreparedTextSessionCache(
            native.device,
            fixture.context.deviceGeneration,
        )

        val ready = assertIs<GPUPreparedRenderRunMaterialization.Ready>(
            GPUWgpu4kPreparedTextRenderRunMaterializer(
                native.device,
                cache,
            ).materializeAcceptedRun(plan, fixture.context.deviceGeneration),
        )

        val uploadsByStep = ready.scopeOperands
            .filterIsInstance<GPUPreparedNativeScopeOperand.TextureUpload>()
            .associateBy { it.sourceStepIndex }
        plan.textureUploads.forEach { upload ->
            val materialized = uploadsByStep.getValue(upload.exactScopeKey.sourceStepIndex)
            assertEquals(upload.exactScopeKey.operandKeys, materialized.exactOperandKeys)
            assertEquals(
                when (upload) {
                    is GPUPreparedTextTextureUploadPlan.Atlas -> "text-atlas"
                    is GPUPreparedTextTextureUploadPlan.Material ->
                        "material:${upload.resourcePlan.resourceKey}"
                },
                materialized.uploadRole,
            )
        }

        ready.ownedResources.single().close()
        cache.close()
    }

    @Test
    fun `prepared text run rejects a generic bind-group pipeline operand`() {
        val fixture = preparedTextNativePreflightFixture()
        val plan = preparedTextTestRunPlan(fixture)
        val native = RecordingPreparedTextNative()
        val cache = GPUWgpu4kPreparedTextSessionCache(
            native.device,
            fixture.context.deviceGeneration,
        )
        val acquired = assertIs<GPUPreparedTextCacheBatchAcquire.Ready>(
            cache.acquireBatch(
                plan.bindings.map { it.nativeProgram },
                fixture.context.deviceGeneration,
            ),
        ).pipelinesByKey.values.single()
        val renderScope = plan.exactScopeKeys.first {
            it.operationKind == GPUEncoderOperationKind.Render
        }

        assertFailsWith<IllegalArgumentException> {
            GPUPreparedNativeScopeOperand.PreparedTextRenderRun(
                sourceStepIndex = renderScope.sourceStepIndex,
                commands = listOf(
                    GPUPreparedNativeRenderCommand.SetPipeline(
                        GPUPreparedNativeRenderPipelineOperand(
                            acquired.pipeline,
                            fixture.context.deviceGeneration,
                        ),
                    ),
                    GPUPreparedNativeRenderCommand.Draw(
                        GPUPreparedNativeDrawCall.Draw(vertexCount = 6),
                    ),
                ),
                exactOperandKeys = renderScope.operandKeys,
                semanticPayloads = listOf(plan.packets.first()),
            )
        }
        cache.close()
    }
}

private fun compiledPreparedTextMaterial(
    descriptor: org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor,
): org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram {
    return assertIs<
        org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramResult.Ready
        >(
        org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramCompiler.compile(
            descriptor = descriptor,
            paintAlpha = 1f,
            context =
                org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialLoweringContext(
                    capabilityClass = "webgpu-test",
                    targetFormatClass = "rgba8unorm",
                    dictionaryVersion = "material-dictionary:prepared-material:v1",
                    runtimeEffectResolver =
                        org.graphiks.kanvas.gpu.renderer.runtimeeffects
                            .KanvasPreparedRuntimeEffectResolver(),
                ),
        ),
    ).program
}

internal fun preparedTextTestRunPlan(
    fixture: PreparedTextNativePreflightFixture,
): GPUPreparedTextRenderRunPlan {
    val renders = fixture.framePlan.steps.withIndex().mapNotNull { indexed ->
        (indexed.value as? GPUFrameStep.RenderPassStep)?.let { indexed.index to it }
    }
    val packets = renders.flatMap { (_, render) ->
        render.drawPackets.map { packet ->
            packet.semanticPayload as GPUDrawSemanticPayload.TextA8
        }
    }
    val bindings = renders.flatMap { (_, render) ->
        render.drawPackets.map { packet ->
            render.preparedTextBindingsByPacketId.getValue(packet.packetId)
        }
    }
    val sourceScopeIndices = fixture.framePlan.steps.mapIndexedNotNull { index, step ->
        when (step) {
            is GPUFrameStep.UploadResourceStep ->
                index.takeIf {
                    step.r8ResourcePlan != null || step.materialResourcePlan != null
                }
            is GPUFrameStep.RenderPassStep -> index
            else -> null
        }
    }
    val exactScopeKeys = preparedTextTestScopeKeys(fixture.framePlan, sourceScopeIndices)
    val textureUploads = sourceScopeIndices.mapNotNull { sourceStepIndex ->
        val step = fixture.framePlan.steps[sourceStepIndex] as?
            GPUFrameStep.UploadResourceStep ?: return@mapNotNull null
        val key = exactScopeKeys.single { it.sourceStepIndex == sourceStepIndex }
        step.r8ResourcePlan?.let { resource ->
            GPUPreparedTextTextureUploadPlan.Atlas(key, resource)
        } ?: step.materialResourcePlan?.let { resource ->
            GPUPreparedTextTextureUploadPlan.Material(key, resource)
        }
    }
    return GPUPreparedTextRenderRunPlan(
        sourceScopeIndices = sourceScopeIndices,
        packets = packets,
        bindings = bindings,
        exactScopeKeys = exactScopeKeys,
        textureUploads = textureUploads,
    )
}

internal fun preparedTextTestScopeKeys(
    framePlan: org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan,
    sourceScopeIndices: List<Int>,
): List<GPUPreparedNativeScopeKey> =
    sourceScopeIndices.map { sourceStepIndex ->
        val step = framePlan.steps[sourceStepIndex]
        when (step) {
            is GPUFrameStep.UploadResourceStep -> GPUPreparedNativeScopeKey(
                sourceStepIndex = sourceStepIndex,
                operationKind = GPUEncoderOperationKind.Upload,
                operandKeys = listOf(
                    GPUPreparedNativeOperandKey(
                        GPUPreparedNativeOperandRole.UploadSource,
                        GPUPreparedNativeOperandKind.Buffer,
                        gpuPreparedNativeBindingKey("text-test-upload-source:$sourceStepIndex"),
                    ),
                    GPUPreparedNativeOperandKey(
                        GPUPreparedNativeOperandRole.UploadDestination,
                        GPUPreparedNativeOperandKind.Texture,
                        gpuPreparedNativeBindingKey("text-test-upload-destination:$sourceStepIndex"),
                    ),
                ),
            )
            is GPUFrameStep.RenderPassStep -> {
                val packetCount = step.drawPackets.size
                GPUPreparedNativeScopeKey(
                    sourceStepIndex = sourceStepIndex,
                    operationKind = GPUEncoderOperationKind.Render,
                    operandKeys = listOf(
                        GPUPreparedNativeOperandKey(
                            GPUPreparedNativeOperandRole.RenderColorTarget,
                            GPUPreparedNativeOperandKind.TextureView,
                            gpuPreparedNativeBindingKey("text-test-target:$sourceStepIndex"),
                        ),
                    ) + List(packetCount) { packetIndex ->
                        listOf(
                            GPUPreparedNativeOperandKey(
                                GPUPreparedNativeOperandRole.RenderPipeline,
                                GPUPreparedNativeOperandKind.RenderPipeline,
                                gpuPreparedNativeBindingKey(
                                    "text-test-pipeline:$sourceStepIndex:$packetIndex",
                                ),
                            ),
                            GPUPreparedNativeOperandKey(
                                GPUPreparedNativeOperandRole.RenderBindGroup,
                                GPUPreparedNativeOperandKind.BindGroup,
                                gpuPreparedNativeBindingKey(
                                    "text-test-draw-group:$sourceStepIndex:$packetIndex",
                                ),
                            ),
                            GPUPreparedNativeOperandKey(
                                GPUPreparedNativeOperandRole.RenderBindGroup,
                                GPUPreparedNativeOperandKind.BindGroup,
                                gpuPreparedNativeBindingKey(
                                    "text-test-material-group:$sourceStepIndex:$packetIndex",
                                ),
                            ),
                            GPUPreparedNativeOperandKey(
                                GPUPreparedNativeOperandRole.RenderBindGroup,
                                GPUPreparedNativeOperandKind.BindGroup,
                                gpuPreparedNativeBindingKey(
                                    "text-test-atlas-group:$sourceStepIndex:$packetIndex",
                                ),
                            ),
                            GPUPreparedNativeOperandKey(
                                GPUPreparedNativeOperandRole.RenderVertexBuffer,
                                GPUPreparedNativeOperandKind.Buffer,
                                gpuPreparedNativeBindingKey(
                                    "text-test-instances:$sourceStepIndex:$packetIndex",
                                ),
                            ),
                        )
                    }.flatten(),
                )
            }
            else -> error("Unexpected prepared-text test scope $sourceStepIndex")
        }
    }

private class RecordingPreparedTextNative {
    val createdInstanceBuffers = mutableListOf<GPUBuffer>()
    val createdDrawUniformBuffers = mutableListOf<GPUBuffer>()
    val createdMaterialUniformBuffers = mutableListOf<GPUBuffer>()
    val createdR8Textures = mutableListOf<GPUTexture>()
    val pipelineDescriptors = mutableListOf<RenderPipelineDescriptor>()
    val samplerDescriptors = mutableListOf<SamplerDescriptor>()
    private var ordinal = 0

    val device: GPUDevice = handle("device") { methodName, args ->
        when (methodName) {
            "createShaderModule" -> handle<GPUShaderModule>("shader")
            "createBindGroupLayout" ->
                handle<io.ygdrasil.webgpu.GPUBindGroupLayout>("bind-group-layout")
            "createPipelineLayout" -> handle<GPUPipelineLayout>("pipeline-layout")
            "createRenderPipeline" -> {
                pipelineDescriptors += args?.first() as RenderPipelineDescriptor
                handle<GPURenderPipeline>("pipeline")
            }
            "createSampler" -> {
                samplerDescriptors += args?.first() as SamplerDescriptor
                handle<GPUSampler>("sampler")
            }
            "createTexture" -> {
                val label = args?.firstOrNull()?.toString().orEmpty()
                textureHandle().also {
                    if ("text" in label || createdR8Textures.isEmpty()) createdR8Textures += it
                }
            }
            "createBuffer" -> {
                val label = args?.firstOrNull()?.toString().orEmpty()
                handle<GPUBuffer>("buffer").also { buffer ->
                    when {
                        "instances" in label -> createdInstanceBuffers += buffer
                        "draw" in label -> createdDrawUniformBuffers += buffer
                        "material" in label -> createdMaterialUniformBuffers += buffer
                    }
                }
            }
            "createBindGroup" -> handle<GPUBindGroup>("bind-group")
            else -> null
        }
    }

    private fun textureHandle(): GPUTexture {
        val exactLabel = "texture.${ordinal++}"
        return Proxy.newProxyInstance(
            GPUTexture::class.java.classLoader,
            arrayOf(GPUTexture::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "close", "setLabel" -> Unit
                "getLabel", "toString" -> exactLabel
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.singleOrNull()
                "createView" -> handle<GPUTextureView>("texture-view")
                else -> null
            }
        } as GPUTexture
    }

    private inline fun <reified T> handle(
        label: String,
        crossinline other: (String, Array<out Any?>?) -> Any? = { _, _ -> null },
    ): T {
        val exactLabel = "$label.${ordinal++}"
        return Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) {
                proxy, method, args ->
            when (method.name) {
                "close", "setLabel" -> Unit
                "getLabel", "toString" -> exactLabel
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.singleOrNull()
                else -> other(method.name, args)
            }
        } as T
    }
}
