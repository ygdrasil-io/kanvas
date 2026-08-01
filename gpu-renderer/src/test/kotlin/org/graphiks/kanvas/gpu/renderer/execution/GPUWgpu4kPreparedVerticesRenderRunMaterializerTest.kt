package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUPrimitiveTopology
import io.ygdrasil.webgpu.GPUTextureView
import io.ygdrasil.webgpu.GPUVertexFormat
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesShaderResult
import org.graphiks.kanvas.gpu.renderer.artifacts.PreparedVerticesShaderAssembler
import org.graphiks.kanvas.gpu.renderer.artifacts.buildVerticesFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedVerticesPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedVerticesPayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedVerticesPayloadResult
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexMode

/**
 * Native-fake tests for the frame-local prepared-vertices materializer.
 *
 * Every test drives the exact wgpu4k acquisition order for one accepted vertices run:
 * pipeline/layout entries, vertex/index buffers, bind groups, and the target-bound draw
 * commands. Success and failure at every acquisition point must close every created
 * object exactly once and never close an uncreated object.
 */
class GPUWgpu4kPreparedVerticesRenderRunMaterializerTest {

    @Test
    fun `non-indexed triangles materialize in exact command order and close once`() {
        val fixture = verticesPreflightFixture(indexed = false)
        val native = RecordingPreparedVerticesNative()
        val plan = preparedVerticesRenderRunTestPlan(fixture)
        val generation = fixture.context.deviceGeneration

        val ready = assertIs<GPUPreparedRenderRunMaterialization.Ready>(
            GPUWgpu4kPreparedVerticesRenderRunMaterializer(native.device)
                .materializeAcceptedRun(plan, generation, targetViewOperand(generation, native)),
        )

        val render = assertIs<GPUPreparedNativeScopeOperand.Render>(
            ready.scopeOperands.single(),
        )
        val vertexBuffer = native.bufferRecords.single { it.label == "vertex" }
        assertEquals(GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst, vertexBuffer.descriptor.usage)
        assertEquals(48uL, vertexBuffer.descriptor.size)
        val commands = render.commands
        assertEquals(
            listOf(
                "SetPipeline",
                "SetBindGroup",
                "SetBindGroup",
                "SetVertexBuffer",
                "SetScissor",
                "Draw",
            ),
            commands.map { command -> command::class.simpleName },
        )
        val setVertex = assertIs<GPUPreparedNativeRenderCommand.SetVertexBuffer>(commands[3])
        assertEquals(0, setVertex.slot)
        assertEquals(0L, setVertex.offset)
        assertEquals(48L, setVertex.size)
        assertEquals(8L, setVertex.vertexStrideBytes)
        val draw = assertIs<GPUPreparedNativeRenderCommand.Draw>(commands[5]).drawCall
        assertEquals(6, draw.vertexCount)
        assertEquals(1, draw.instanceCount)
        assertEquals(0, draw.firstVertex)
        assertEquals(0, draw.firstInstance)
        val scissor = assertIs<GPUPreparedNativeRenderCommand.SetScissor>(commands[4])
        assertEquals(0, scissor.x)
        assertEquals(0, scissor.y)
        assertEquals(16, scissor.width)
        assertEquals(16, scissor.height)
        assertTrue(
            render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetBindGroup>()
                .all { group -> group.bindGroup.ownership == GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion },
        )
        assertTrue(
            render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetVertexBuffer>()
                .all { command -> command.buffer.ownership == GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion },
        )
        val pipelineOperand = assertIs<GPUPreparedNativeRenderCommand.SetPipeline>(commands[0])
            .pipeline
        assertEquals(GPUPreparedNativeOperandOwnership.Borrowed, pipelineOperand.ownership)

        assertEquals(listOf("vertex", "draw-uniforms", "material-uniforms"),
            ready.uniformUploads.map { it.uploadRole })
        val vertexUpload = ready.uniformUploads.single { it.uploadRole == "vertex" }
        assertContentEquals(
            plan.packets.single().artifact.vertexBytesForUpload(),
            vertexUpload.data.bytes(),
        )
        assertEquals(0L, vertexUpload.destinationOffset)
        assertEquals(listOf(plan.sourceScopeIndex), vertexUpload.consumerSourceStepIndices)
        assertEquals(
            GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
            vertexUpload.destination.ownership,
        )
        val drawUniformUpload = ready.uniformUploads.single { it.uploadRole == "draw-uniforms" }
        assertEquals(64, drawUniformUpload.data.bytes().size)

        val pipelineDescriptor = native.pipelineDescriptors.single()
        assertEquals("vs_main", pipelineDescriptor.vertex.entryPoint)
        assertEquals("fs_main", pipelineDescriptor.fragment!!.entryPoint)
        assertEquals(GPUPrimitiveTopology.TriangleList, pipelineDescriptor.primitive.topology)
        val vertexLayout = pipelineDescriptor.vertex.buffers.single()
        assertEquals(8uL, vertexLayout.arrayStride)
        assertEquals(1, vertexLayout.attributes.size)
        assertEquals(GPUVertexFormat.Float32x2, vertexLayout.attributes.single().format)
        assertEquals(0uL, vertexLayout.attributes.single().offset)
        assertEquals(0u, vertexLayout.attributes.single().shaderLocation)
        assertEquals(
            io.ygdrasil.webgpu.GPUTextureFormat.RGBA8UnormSrgb,
            pipelineDescriptor.fragment!!.targets.single().format,
        )

        val created = native.createdHandles()
        ready.ownedResources.single().close()
        created.forEach { handle ->
            assertEquals(1, native.closeCounts[handle], "close-once for $handle")
        }
    }

    @Test
    fun `indexed uint16 geometry binds an index buffer and draws indexed`() {
        val fixture = verticesPreflightFixture(indexed = true, indexFormat = "uint16")
        val native = RecordingPreparedVerticesNative()
        val plan = preparedVerticesRenderRunTestPlan(fixture)

        val ready = assertIs<GPUPreparedRenderRunMaterialization.Ready>(
            GPUWgpu4kPreparedVerticesRenderRunMaterializer(native.device)
                .materializeAcceptedRun(plan, fixture.context.deviceGeneration,
                    targetViewOperand(fixture.context.deviceGeneration, native)),
        )

        val render = assertIs<GPUPreparedNativeScopeOperand.Render>(
            ready.scopeOperands.single(),
        )
        val indexBuffer = native.bufferRecords.single { it.label == "index" }
        assertEquals(GPUBufferUsage.Index or GPUBufferUsage.CopyDst, indexBuffer.descriptor.usage)
        assertEquals(12uL, indexBuffer.descriptor.size)
        val commands = render.commands
        assertEquals(
            listOf(
                "SetPipeline",
                "SetBindGroup",
                "SetBindGroup",
                "SetVertexBuffer",
                "SetIndexBuffer",
                "SetScissor",
                "DrawIndexed",
            ),
            commands.map { command -> command::class.simpleName },
        )
        val setIndex = assertIs<GPUPreparedNativeRenderCommand.SetIndexBuffer>(commands[4])
        assertEquals(GPUPreparedNativeIndexFormat.Uint16, setIndex.format)
        assertEquals(0L, setIndex.offset)
        assertEquals(12L, setIndex.size)
        val drawIndexed = assertIs<GPUPreparedNativeRenderCommand.DrawIndexed>(commands[6])
            .drawCall
        assertEquals(6, drawIndexed.indexCount)
        assertEquals(0, drawIndexed.firstIndex)
        assertEquals(0, drawIndexed.baseVertex)
        assertEquals(0, drawIndexed.firstInstance)
        assertEquals(
            listOf("vertex", "index", "draw-uniforms", "material-uniforms"),
            ready.uniformUploads.map { it.uploadRole },
        )
        val indexUpload = ready.uniformUploads.single { it.uploadRole == "index" }
        assertContentEquals(
            plan.packets.single().artifact.indexBytesForUpload(),
            indexUpload.data.bytes(),
        )
        val created = native.createdHandles()
        ready.ownedResources.single().close()
        created.forEach { handle -> assertEquals(1, native.closeCounts[handle]) }
    }

    @Test
    fun `indexed uint32 geometry uses the uint32 index format`() {
        val fixture = verticesPreflightFixture(indexed = true, indexFormat = "uint32")
        val native = RecordingPreparedVerticesNative()
        val plan = preparedVerticesRenderRunTestPlan(fixture)

        val ready = assertIs<GPUPreparedRenderRunMaterialization.Ready>(
            GPUWgpu4kPreparedVerticesRenderRunMaterializer(native.device)
                .materializeAcceptedRun(plan, fixture.context.deviceGeneration,
                    targetViewOperand(fixture.context.deviceGeneration, native)),
        )

        val render = assertIs<GPUPreparedNativeScopeOperand.Render>(
            ready.scopeOperands.single(),
        )
        val indexBuffer = native.bufferRecords.single { it.label == "index" }
        assertEquals(24uL, indexBuffer.descriptor.size)
        assertEquals(
            GPUPreparedNativeIndexFormat.Uint32,
            render.commands
                .filterIsInstance<GPUPreparedNativeRenderCommand.SetIndexBuffer>()
                .single().format,
        )
        val drawIndexed = render.commands
            .filterIsInstance<GPUPreparedNativeRenderCommand.DrawIndexed>()
            .single().drawCall
        assertEquals(6, drawIndexed.indexCount)
        ready.ownedResources.single().close()
    }

    @Test
    fun `triangle strip topology reaches the native pipeline descriptor`() {
        val fixture = verticesPreflightFixture(
            topology = GPUVertexMode.TriangleStrip,
            indexed = false,
        )
        val native = RecordingPreparedVerticesNative()
        val plan = preparedVerticesRenderRunTestPlan(fixture)

        val ready = assertIs<GPUPreparedRenderRunMaterialization.Ready>(
            GPUWgpu4kPreparedVerticesRenderRunMaterializer(native.device)
                .materializeAcceptedRun(plan, fixture.context.deviceGeneration,
                    targetViewOperand(fixture.context.deviceGeneration, native)),
        )

        assertEquals(
            GPUPrimitiveTopology.TriangleStrip,
            native.pipelineDescriptors.single().primitive.topology,
        )
        ready.ownedResources.single().close()
    }

    @Test
    fun `non-symmetric affine transform transposes into exact column-major draw uniforms`() {
        val transformBytes = listOf(
            2f.toRawBits(), 0f.toRawBits(), 0.5f.toRawBits(),
            0f.toRawBits(), 3f.toRawBits(), (-1.25f).toRawBits(),
            0f.toRawBits(), 0f.toRawBits(), 1f.toRawBits(),
        )
        val fixture = verticesPreflightFixtureWithTransform(transformBytes)
        val native = RecordingPreparedVerticesNative()
        val plan = preparedVerticesRenderRunTestPlan(fixture)

        val ready = assertIs<GPUPreparedRenderRunMaterialization.Ready>(
            GPUWgpu4kPreparedVerticesRenderRunMaterializer(native.device)
                .materializeAcceptedRun(plan, fixture.context.deviceGeneration,
                    targetViewOperand(fixture.context.deviceGeneration, native)),
        )

        val bytes = ready.uniformUploads.single { it.uploadRole == "draw-uniforms" }.data.bytes()
        assertEquals(64, bytes.size)
        val uniform = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(2f, uniform.getFloat(0))
        assertEquals(0f, uniform.getFloat(4))
        assertEquals(0f, uniform.getFloat(8))
        assertEquals(0, uniform.getInt(12))
        assertEquals(0f, uniform.getFloat(16))
        assertEquals(3f, uniform.getFloat(20))
        assertEquals(0f, uniform.getFloat(24))
        assertEquals(0, uniform.getInt(28))
        assertEquals(0.5f, uniform.getFloat(32))
        assertEquals(-1.25f, uniform.getFloat(36))
        assertEquals(1f, uniform.getFloat(40))
        assertEquals(0, uniform.getInt(44))
        assertEquals(16f, uniform.getFloat(48))
        assertEquals(16f, uniform.getFloat(52))
        assertEquals(0f, uniform.getFloat(56))
        assertEquals(0f, uniform.getFloat(60))
        ready.ownedResources.single().close()
    }

    @Test
    fun `material uniforms are uploaded with exact bytes and bound in group one`() {
        val material = compiledPreparedVerticesMaterial(
            org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.RuntimeEffect(
                effectId = "runtime.simple_rt",
                descriptorVersion = 1,
                uniforms = mapOf(
                    "gColor" to org.graphiks.kanvas.gpu.renderer.commands
                        .GPURuntimeEffectUniformValue.Float4(0.25f, 0.5f, 0.75f, 0.8f),
                ),
            ),
        )
        val fixture = verticesPreflightFixture(material = material)
        val native = RecordingPreparedVerticesNative()
        val plan = preparedVerticesRenderRunTestPlan(fixture)

        val ready = assertIs<GPUPreparedRenderRunMaterialization.Ready>(
            GPUWgpu4kPreparedVerticesRenderRunMaterializer(native.device)
                .materializeAcceptedRun(plan, fixture.context.deviceGeneration,
                    targetViewOperand(fixture.context.deviceGeneration, native)),
        )

        val materialUniform = native.bufferRecords.single { it.label == "material-uniforms" }
        assertEquals(material.uniformBytes.size.toULong(), materialUniform.descriptor.size)
        val upload = ready.uniformUploads.single { it.uploadRole == "material-uniforms" }
        assertContentEquals(
            material.uniformBytes.map(Int::toByte).toByteArray(),
            upload.data.bytes(),
        )
        val render = assertIs<GPUPreparedNativeScopeOperand.Render>(ready.scopeOperands.single())
        assertEquals(
            2,
            render.commands.filterIsInstance<GPUPreparedNativeRenderCommand.SetBindGroup>().size,
        )
        ready.ownedResources.single().close()
    }

    @Test
    fun `sampled material resources refuse before any native creation`() {
        val material = sampledPreparedVerticesMaterialProgram("vertices:sampled")
        val fixture = verticesPreflightFixture(material = material)
        val native = RecordingPreparedVerticesNative()
        val plan = preparedVerticesRenderRunTestPlan(fixture)
        assertTrue(material.sampledResources.isNotEmpty())

        val refused = assertIs<GPUPreparedRenderRunMaterialization.Refused>(
            GPUWgpu4kPreparedVerticesRenderRunMaterializer(native.device)
                .materializeAcceptedRun(plan, fixture.context.deviceGeneration,
                    targetViewOperand(fixture.context.deviceGeneration, native)),
        )

        assertEquals("unsupported.prepared-vertices.sampled-material", refused.code)
        assertTrue(native.createdHandles().isEmpty())
        assertEquals(null, refused.retainedCloseOwner)
    }

    @Test
    fun `failure at each acquisition point closes acquired owners once`() {
        listOf(
            "shader-module",
            "bind-group-layout",
            "vertex",
            "index",
            "bind-group",
            "pipeline",
        ).forEach { acquisition ->
            val fixture = verticesPreflightFixture(indexed = true)
            val native = RecordingPreparedVerticesNative()
            val plan = preparedVerticesRenderRunTestPlan(fixture)
            native.fail(acquisition, ordinal = 1)

            val refused = assertIs<GPUPreparedRenderRunMaterialization.Refused>(
                GPUWgpu4kPreparedVerticesRenderRunMaterializer(native.device)
                    .materializeAcceptedRun(plan, fixture.context.deviceGeneration,
                        targetViewOperand(fixture.context.deviceGeneration, native)),
            )

            assertEquals(
                "failed.prepared_vertices.materialization",
                refused.code,
                acquisition,
            )
            val created = native.createdHandles()
            created.forEach { handle ->
                assertEquals(1, native.closeCounts[handle], "close-once on $acquisition for $handle")
            }
            assertEquals(null, refused.retainedCloseOwner, acquisition)
        }
    }

    @Test
    fun `target binding config preserves clear load and store semantics`() {
        val fixture = verticesPreflightFixture(indexed = false)
        val native = RecordingPreparedVerticesNative()
        val plan = preparedVerticesRenderRunTestPlan(fixture)

        val ready = assertIs<GPUPreparedRenderRunMaterialization.Ready>(
            GPUWgpu4kPreparedVerticesRenderRunMaterializer(native.device)
                .materializeAcceptedRun(plan, fixture.context.deviceGeneration,
                    targetViewOperand(fixture.context.deviceGeneration, native)),
        )

        val render = assertIs<GPUPreparedNativeScopeOperand.Render>(ready.scopeOperands.single())
        assertEquals(GPUPreparedNativeLoadOperation.Clear, render.pass.loadOperation)
        assertEquals(GPUPreparedNativeStoreOperation.Store, render.pass.storeOperation)
        assertEquals(GPUPreparedNativeClearColor(0.0, 0.0, 0.0, 0.0), render.pass.clearColor)
        ready.ownedResources.single().close()
    }

    @Test
    fun `closing the owner twice never double closes a native handle`() {
        val fixture = verticesPreflightFixture(indexed = false)
        val native = RecordingPreparedVerticesNative()
        val plan = preparedVerticesRenderRunTestPlan(fixture)

        val ready = assertIs<GPUPreparedRenderRunMaterialization.Ready>(
            GPUWgpu4kPreparedVerticesRenderRunMaterializer(native.device)
                .materializeAcceptedRun(plan, fixture.context.deviceGeneration,
                    targetViewOperand(fixture.context.deviceGeneration, native)),
        )

        val owner = ready.ownedResources.single()
        owner.close()
        owner.close()
        native.createdHandles().forEach { handle ->
            assertEquals(1, native.closeCounts[handle])
        }
    }
}

internal fun preparedVerticesRenderRunTestPlan(
    fixture: PreparedVerticesPreflightFixture,
): GPUPreparedVerticesRenderRunPlan {
    val renderIndex = fixture.framePlan.steps.indexOfFirst { it is GPUFrameStep.RenderPassStep }
    val render = fixture.framePlan.steps[renderIndex] as GPUFrameStep.RenderPassStep
    val packets = render.drawPackets.map { packet ->
        packet.semanticPayload as GPUDrawSemanticPayload.Vertices
    }
    val deviceGeneration = fixture.framePlan.capabilitySeal.deviceGeneration.value
    val plans = packets.map(GPUDrawSemanticPayload.Vertices::artifact)
        .distinctBy { artifact -> artifact.key }
        .sortedBy { artifact -> artifact.key }
        .map { artifact -> buildVerticesFrameResourcePlan(artifact, deviceGeneration) }
    val drawFacts = render.drawPackets.map { packet ->
        val semantic = packet.semanticPayload as GPUDrawSemanticPayload.Vertices
        val plan = plans.single { candidate -> candidate.artifactKey == semantic.artifact.key }
        GPUPreparedVerticesDrawFacts(
            packetId = packet.packetId,
            artifactKey = semantic.artifact.key,
            vertexCount = semantic.artifact.vertexCount,
            indexCount = semantic.artifact.indexCount,
            indexFormat = semantic.artifact.indexFormat,
            vertexByteCount = plan.vertexBuffer.byteCount,
            indexByteCount = plan.indexBuffer?.byteCount,
        )
    }
    val shaderPrograms = render.drawPackets.associate { packet ->
        val semantic = packet.semanticPayload as GPUDrawSemanticPayload.Vertices
        packet.packetId to assertIs<GPUPreparedVerticesShaderResult.Ready>(
            PreparedVerticesShaderAssembler.assemble(
                layout = semantic.artifact.layout,
                topology = semantic.artifact.topology,
                material = semantic.material,
                hasPrimitiveColor = semantic.primitiveColorPresent,
            ),
        ).program
    }
    val exactScopeKey = GPUPreparedNativeScopeKey(
        sourceStepIndex = renderIndex,
        operationKind = GPUEncoderOperationKind.Render,
        operandKeys = buildList {
            add(
                GPUPreparedNativeOperandKey(
                    GPUPreparedNativeOperandRole.RenderColorTarget,
                    GPUPreparedNativeOperandKind.TextureView,
                    gpuPreparedNativeBindingKey("vertices-test-target:$renderIndex"),
                ),
            )
            packets.forEachIndexed { packetIndex, semantic ->
                add(
                    GPUPreparedNativeOperandKey(
                        GPUPreparedNativeOperandRole.RenderPipeline,
                        GPUPreparedNativeOperandKind.RenderPipeline,
                        gpuPreparedNativeBindingKey(
                            "vertices-test-pipeline:$renderIndex:$packetIndex",
                        ),
                    ),
                )
                add(
                    GPUPreparedNativeOperandKey(
                        GPUPreparedNativeOperandRole.RenderBindGroup,
                        GPUPreparedNativeOperandKind.BindGroup,
                        gpuPreparedNativeBindingKey(
                            "vertices-test-draw-group:$renderIndex:$packetIndex",
                        ),
                    ),
                )
                add(
                    GPUPreparedNativeOperandKey(
                        GPUPreparedNativeOperandRole.RenderBindGroup,
                        GPUPreparedNativeOperandKind.BindGroup,
                        gpuPreparedNativeBindingKey(
                            "vertices-test-material-group:$renderIndex:$packetIndex",
                        ),
                    ),
                )
                add(
                    GPUPreparedNativeOperandKey(
                        GPUPreparedNativeOperandRole.RenderVertexBuffer,
                        GPUPreparedNativeOperandKind.Buffer,
                        gpuPreparedNativeBindingKey(
                            "vertices-test-vertex:${semantic.artifact.key}",
                        ),
                    ),
                )
                if (semantic.artifact.indexCount != null) {
                    add(
                        GPUPreparedNativeOperandKey(
                            GPUPreparedNativeOperandRole.RenderIndexBuffer,
                            GPUPreparedNativeOperandKind.Buffer,
                            gpuPreparedNativeBindingKey(
                                "vertices-test-index:${semantic.artifact.key}",
                            ),
                        ),
                    )
                }
            }
        },
    )
    return GPUPreparedVerticesRenderRunPlan(
        sourceScopeIndex = renderIndex,
        renderStep = render,
        packets = packets,
        resourcePlans = plans,
        drawFacts = drawFacts,
        shaderProgramByPacketId = shaderPrograms,
        exactScopeKey = exactScopeKey,
    )
}

private fun targetViewOperand(
    generation: GPUDeviceGenerationID,
    native: RecordingPreparedVerticesNative,
): GPUPreparedNativeTextureViewOperand = GPUPreparedNativeTextureViewOperand(
    native.targetView,
    generation,
    GPUPreparedNativeOperandOwnership.Borrowed,
)

internal fun compiledPreparedVerticesMaterial(
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

internal fun sampledPreparedVerticesMaterialProgram(
    resourceKeyPrefix: String,
): org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram {
    return assertIs<
        org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramResult.Ready
        >(
        org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramCompiler.compile(
            descriptor =
                org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.ImageDraw(
                    imageSourceId = "vertices-sampled:$resourceKeyPrefix",
                    imageWidth = 2,
                    imageHeight = 2,
                    rgbaPixels = ByteArray(16) { byteIndex -> (byteIndex * 7).toByte() },
                    samplingFilterMode = "nearest",
                    alphaOnly = false,
                ),
            paintAlpha = 1f,
            context =
                org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialLoweringContext(
                    capabilityClass = "webgpu-test",
                    targetFormatClass = "rgba8unorm",
                    dictionaryVersion = "material-dictionary:bitmap-shader:v1",
                ),
        ),
    ).program
}

/**
 * Rebuilds the prepared-vertices preflight fixture with one non-symmetric affine transform.
 *
 * The base fixture records the identity matrix; a column-order or transposition mistake in the
 * draw-uniform packing would stay invisible under identity because the transpose of the identity
 * is the identity. Distinct row-major values (scale(2,3) + translate(0.5,-1.25)) prove the
 * exact WGSL column-major storage order in the materializer.
 */
private fun verticesPreflightFixtureWithTransform(
    transformBytes: List<Int>,
): PreparedVerticesPreflightFixture {
    val fixture = verticesPreflightFixture(indexed = false)
    val framePlan = fixture.framePlan
    val renderIndex = framePlan.steps.indexOfFirst { it is GPUFrameStep.RenderPassStep }
    val render = framePlan.steps[renderIndex] as GPUFrameStep.RenderPassStep
    val packet = render.drawPackets.single()
    val original = packet.semanticPayload as GPUDrawSemanticPayload.Vertices
    val transformed = assertIs<GPUPreparedVerticesPayloadResult.Ready>(
        GPUPreparedVerticesPayloadGatherer.gather(
            GPUPreparedVerticesPayloadInput(
                payloadRef = original.payloadRef,
                artifact = original.artifact,
                material = original.material,
                topologyIdentity = original.topologyIdentity,
                transformBytes = transformBytes,
                targetBounds = original.targetBounds,
                scissorBounds = original.scissorBounds,
                targetFormat = original.targetFormat,
                clipIdentity = original.clipIdentity,
                clipCoverageIdentity = original.clipCoverageIdentity,
                primitiveColorPresent = original.primitiveColorPresent,
                primitiveBlendIdentity = original.primitiveBlendIdentity,
                finalBlendIdentity = original.finalBlendIdentity,
                capabilitySnapshotHash = original.capabilitySnapshotHash,
                drawProvenance = original.drawProvenance,
                frameProvenance = original.frameProvenance,
            ),
        ),
    ).payload
    val rebuiltPacket = GPUDrawPacket(
        packetId = packet.packetId,
        commandIdValue = packet.commandIdValue,
        analysisRecordId = packet.analysisRecordId,
        passId = packet.passId,
        layerId = packet.layerId,
        bindingListId = packet.bindingListId,
        insertionReasonCode = packet.insertionReasonCode,
        sortKey = packet.sortKey,
        sortKeyPreimage = packet.sortKeyPreimage,
        renderStepId = packet.renderStepId,
        renderStepVersion = packet.renderStepVersion,
        role = packet.role,
        blendPlan = packet.blendPlan,
        renderPipelineKey = packet.renderPipelineKey,
        bindingLayoutHash = packet.bindingLayoutHash,
        semanticPayload = transformed,
        vertexSourceLabel = packet.vertexSourceLabel,
        targetStateHash = packet.targetStateHash,
        originalPaintOrder = packet.originalPaintOrder,
        resourceGeneration = packet.resourceGeneration,
        frameProvenance = packet.frameProvenance,
        clipCoveragePlan = packet.clipCoveragePlan,
        clipExecutionPlan = packet.clipExecutionPlan,
    )
    val rebuiltRender = GPUFrameStep.RenderPassStep(
        target = render.target,
        loadStore = render.loadStore,
        samplePlan = render.samplePlan,
        resourceUses = render.resourceUses,
        drawPackets = listOf(rebuiltPacket),
        sourceTaskIds = render.sourceTaskIds,
        batches = render.batches,
        sampleContinuation = render.sampleContinuation,
        depthStencilLoadStore = render.depthStencilLoadStore,
        preparedImageBindingsByPacketId = render.preparedImageBindingsByPacketId,
        preparedTextBindingsByPacketId = render.preparedTextBindingsByPacketId,
    )
    val rebuiltSteps = framePlan.steps.map { step -> if (step === render) rebuiltRender else step }
    return fixture.copy(
        framePlan = GPUFramePlan(
            frameId = framePlan.frameId,
            capabilitySeal = framePlan.capabilitySeal,
            recordingSeals = framePlan.recordingSeals,
            steps = rebuiltSteps,
            memoryBudget = framePlan.memoryBudget,
            diagnostics = framePlan.diagnostics,
            dependencies = framePlan.dependencies,
            phaseOrder = framePlan.phaseOrder,
            elidedNoOpDraws = framePlan.elidedNoOpDraws,
            atomicallyRefused = framePlan.atomicallyRefused,
        ),
    )
}

private class BufferRecord(
    val buffer: GPUBuffer,
    val descriptor: BufferDescriptor,
    val label: String,
)

private class RecordingPreparedVerticesNative {
    val pipelineDescriptors = mutableListOf<RenderPipelineDescriptor>()
    val bufferRecords = mutableListOf<BufferRecord>()
    val events = mutableListOf<String>()
    val closeCounts = linkedMapOf<Any, Int>()
    private val handlesByLabel = linkedMapOf<String, MutableList<Any>>()
    private val fails = mutableMapOf<String, MutableList<Int>>()
    private var ordinal = 0

    fun fail(method: String, ordinal: Int) {
        fails.getOrPut(method) { mutableListOf() } += ordinal
    }

    private fun shouldFail(method: String): Boolean {
        val pending = fails[method] ?: return false
        val hit = pending.remove(1)
        if (hit != null && pending.isEmpty()) fails.remove(method)
        return hit != null
    }

    private inline fun <reified T : Any> handle(
        label: String,
        crossinline other: (String, Array<out Any?>?) -> Any? = { _, _ -> null },
        tracked: Boolean = true,
    ): T {
        val exactLabel = "$label.${ordinal++}"
        if (shouldFail(label)) {
            error("injected $label failure")
        }
        val proxy = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) {
                proxy, method, args ->
            when (method.name) {
                "close" -> {
                    events += "close:$exactLabel"
                    closeCounts[proxy] = closeCounts.getOrDefault(proxy, 0) + 1
                }
                "setLabel" -> Unit
                "getLabel", "toString" -> exactLabel
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.singleOrNull()
                else -> other(method.name, args)
            }
        } as T
        if (tracked) {
            handlesByLabel.getOrPut(label) { mutableListOf() } += proxy
        }
        events += "create:$exactLabel"
        return proxy
    }

    val targetView: GPUTextureView = handle("target-view", tracked = false)

    val device: GPUDevice = handle(
        "device",
        tracked = false,
        other = { methodName, args ->
            when (methodName) {
                "createShaderModule" ->
                    handle<io.ygdrasil.webgpu.GPUShaderModule>("shader-module")
                "createBindGroupLayout" ->
                    handle<io.ygdrasil.webgpu.GPUBindGroupLayout>("bind-group-layout")
                "createPipelineLayout" ->
                    handle<io.ygdrasil.webgpu.GPUPipelineLayout>("pipeline-layout")
                "createRenderPipeline" -> {
                    pipelineDescriptors += args?.first() as RenderPipelineDescriptor
                    handle<io.ygdrasil.webgpu.GPURenderPipeline>("pipeline")
                }
                "createBuffer" -> {
                    val descriptor = args?.first() as BufferDescriptor
                    val label = when {
                        "index" in descriptor.label -> "index"
                        "draw-uniforms" in descriptor.label -> "draw-uniforms"
                        "material-uniforms" in descriptor.label -> "material-uniforms"
                        else -> "vertex"
                    }
                    val buffer = handle<GPUBuffer>(label)
                    bufferRecords += BufferRecord(buffer, descriptor, label)
                    buffer
                }
                "createBindGroup" -> handle<GPUBindGroup>("bind-group")
                else -> null
            }
        },
    )

    fun createdHandles(prefix: String): List<Any> =
        handlesByLabel.entries
            .filter { (label, _) -> label.startsWith(prefix) }
            .flatMap { (_, handles) -> handles }

    fun createdHandles(): List<Any> = handlesByLabel.values.flatten()
}
