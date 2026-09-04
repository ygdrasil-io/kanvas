package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUTextureFormat
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.gpu.plan.GpuPlanSelection
import org.graphiks.kanvas.gpu.plan.PlanBudget
import org.graphiks.kanvas.gpu.plan.PlanBufferAllocationPolicy
import org.graphiks.kanvas.gpu.plan.PlanCapabilitySnapshot
import org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat
import org.graphiks.kanvas.gpu.plan.PlanOperationCapability
import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.plan.W4bAnalyticRRectPlanCompiler
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureFormatSampleSupport
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureSampleCountSupport
import org.graphiks.kanvas.gpu.renderer.passes.CORE_PRIMITIVE_ANALYTIC_SHAPE_UNIFORM_BYTES
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticShapeUniformBuildResult
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveAnalyticShapeUniformBlock
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitivePreparedSemanticAuthority
import org.graphiks.kanvas.gpu.renderer.passes.buildCorePrimitiveAnalyticShapeUniform
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanLoweringRequest
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanLoweringResult
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanTaskListLowerer
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.render.ir.BlendNode
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.CoverageRequest
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.kanvas.render.ir.EffectStack
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.MaterialNode
import org.graphiks.kanvas.render.ir.RenderPlanResult
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneExtent
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class GPUCorePrimitiveAnalyticShapeUniformAbiTest {
    @Test
    fun `analytic shape uniform v1 packs exact reflected 80 byte layout`() {
        val packed = GPUCorePrimitiveAnalyticShapeUniformBlock(
            targetWidth = 32f,
            targetHeight = 24f,
            antiAlias = true,
            premultipliedRgba = listOf(0.1f, 0.2f, 0.3f, 0.4f),
            deviceBounds = listOf(1.25f, 2.5f, 29.75f, 21.5f),
            normalizedRadii = listOf(3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f),
        ).packedBytes()

        assertEquals(80, CORE_PRIMITIVE_ANALYTIC_SHAPE_UNIFORM_BYTES)
        assertEquals(80, packed.size)
        assertEquals(
            "dynamic-uniform80-analytic-shape-v1",
            CORE_PRIMITIVE_ANALYTIC_SHAPE_NATIVE_BINDING_LAYOUT_IDENTITY,
        )

        val bytes = ByteBuffer.wrap(packed).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(32f, bytes.getFloat(0))
        assertEquals(24f, bytes.getFloat(4))
        assertEquals(1, bytes.getInt(8))
        assertEquals(0, bytes.getInt(12))
        assertContentEquals(listOf(0.1f, 0.2f, 0.3f, 0.4f), bytes.floatsAt(16, 4))
        assertContentEquals(listOf(1.25f, 2.5f, 29.75f, 21.5f), bytes.floatsAt(32, 4))
        assertContentEquals(listOf(3f, 4f, 5f, 6f), bytes.floatsAt(48, 4))
        assertContentEquals(listOf(7f, 8f, 9f, 10f), bytes.floatsAt(64, 4))
    }

    @Test
    fun `W4b lowered RRect authority builds Uniform80 at reflected bounds and radii offsets`() {
        val scene = SceneSnapshot.of(
            SceneExtent(4, 4),
            ColorSpace.SRGB,
            listOf(
                SceneCommand.Draw(
                    DrawNode(
                        GeometryNode.Rect.of(RectF32(0f, 0f, 2f, 2f)),
                        MaterialNode.Solid(ColorARGB.fromPackedUInt(0xff0000ffu)),
                        CoverageRequest.ANTIALIASED,
                        ClipStackNode.Empty,
                        BlendNode.SrcOver,
                        EffectStack.Empty,
                        Matrix3x3F32.Identity,
                        DrawOrigin.RECT,
                    ),
                ),
                SceneCommand.Draw(
                    DrawNode(
                        GeometryNode.RRect.of(
                            RRectF32.of(
                                RectF32(1f, 1f, 4f, 4f),
                                CornerRadiiF32.of(1f, 1f),
                                CornerRadiiF32.of(2f, 1f),
                                CornerRadiiF32.of(1f, 2f),
                                CornerRadiiF32.of(0.5f, 1f),
                            ),
                        ),
                        MaterialNode.Solid(ColorARGB.fromPackedUInt(0x80ff0000u)),
                        CoverageRequest.ANTIALIASED,
                        ClipStackNode.Empty,
                        BlendNode.SrcOver,
                        EffectStack.Empty,
                        Matrix3x3F32.Identity,
                        DrawOrigin.RRECT,
                    ),
                ),
            ),
        )
        val compiler = W4bAnalyticRRectPlanCompiler()
        val candidate = assertIs<GpuPlanSelection.Candidate>(
            compiler.select(scene, RenderTargetDescriptor(scene.extent, scene.colorSpace)),
        ).candidate
        val graph = assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(candidate, w4bPlanCapabilities(), PlanBudget(1L shl 20)),
        ).plan
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(
            GpuPlanTaskListLowerer().lower(
                GpuPlanLoweringRequest(
                    graph = graph,
                    capabilities = w4bCapabilities(),
                    deviceGeneration = GPUDeviceGenerationID(7),
                    currentBudget = graph.budget,
                    frameId = GPUFrameID(705),
                    recordingId = GPURecordingID("w4b-uniform-abi"),
                ),
            ),
        )
        val semantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(
            lowered.taskList.tasks.filterIsInstance<GPUTask.Render>().single().drawPackets.last().semanticPayload,
        )
        val packed = assertIs<GPUCorePrimitiveAnalyticShapeUniformBuildResult.Accepted>(
            buildCorePrimitiveAnalyticShapeUniform(
                semantic,
                GPUCorePrimitivePreparedSemanticAuthority.capture(semantic),
            ),
        ).bytes
        val bytes = ByteBuffer.wrap(packed).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(80, packed.size)
        assertContentEquals(listOf(1f, 1f, 4f, 4f), bytes.floatsAt(32, 4))
        assertContentEquals(listOf(1f, 1f, 2f, 1f), bytes.floatsAt(48, 4))
        assertContentEquals(listOf(1f, 2f, 0.5f, 1f), bytes.floatsAt(64, 4))
    }

    @Test
    fun `analytic shape uniform snapshots caller owned vectors before packing`() {
        val color = mutableListOf(0.1f, 0.2f, 0.3f, 0.4f)
        val bounds = mutableListOf(1f, 2f, 9f, 10f)
        val radii = mutableListOf(1f, 2f, 3f, 4f, 2f, 2f, 2f, 2f)
        val block = GPUCorePrimitiveAnalyticShapeUniformBlock(
            targetWidth = 16f,
            targetHeight = 12f,
            antiAlias = false,
            premultipliedRgba = color,
            deviceBounds = bounds,
            normalizedRadii = radii,
        )
        val expected = block.packedBytes()

        color.fill(99f)
        bounds.fill(99f)
        radii.fill(99f)

        assertContentEquals(expected, block.packedBytes())
        assertEquals(0, ByteBuffer.wrap(expected).order(ByteOrder.LITTLE_ENDIAN).getInt(8))
    }

    @Test
    fun `analytic shape uniform rejects invalid target color geometry and normalized radii`() {
        val invalidBlocks = listOf(
            { validBlock(targetWidth = Float.NaN) },
            { validBlock(targetHeight = Float.POSITIVE_INFINITY) },
            { validBlock(targetWidth = 0f) },
            { validBlock(premultipliedRgba = listOf(-0.1f, 0f, 0f, 1f)) },
            { validBlock(premultipliedRgba = listOf(Float.NaN, 0f, 0f, 1f)) },
            { validBlock(premultipliedRgba = listOf(0.8f, 0f, 0f, 0.5f)) },
            { validBlock(deviceBounds = listOf(9f, 2f, 1f, 10f)) },
            { validBlock(deviceBounds = listOf(1f, 10f, 9f, 2f)) },
            { validBlock(deviceBounds = listOf(1f, 2f, Float.POSITIVE_INFINITY, 10f)) },
            { validBlock(normalizedRadii = listOf(-1f, 2f, 0f, 0f, 0f, 0f, 0f, 0f)) },
            { validBlock(normalizedRadii = listOf(Float.NaN, 2f, 0f, 0f, 0f, 0f, 0f, 0f)) },
            { validBlock(normalizedRadii = listOf(5f, 1f, 5f, 1f, 0f, 0f, 0f, 0f)) },
        ) + (0 until 4).flatMap { corner ->
            listOf(
                { validBlock(normalizedRadii = mixedZeroRadii(corner, 0f, 1f)) },
                { validBlock(normalizedRadii = mixedZeroRadii(corner, 1f, 0f)) },
            )
        }

        invalidBlocks.forEach { construct ->
            assertFailsWith<IllegalArgumentException> { construct() }
        }
    }

    private fun validBlock(
        targetWidth: Float = 16f,
        targetHeight: Float = 12f,
        premultipliedRgba: List<Float> = listOf(0.25f, 0.125f, 0f, 0.5f),
        deviceBounds: List<Float> = listOf(1f, 2f, 9f, 10f),
        normalizedRadii: List<Float> = listOf(2f, 2f, 3f, 2f, 1f, 1f, 1f, 1f),
    ) = GPUCorePrimitiveAnalyticShapeUniformBlock(
        targetWidth = targetWidth,
        targetHeight = targetHeight,
        antiAlias = true,
        premultipliedRgba = premultipliedRgba,
        deviceBounds = deviceBounds,
        normalizedRadii = normalizedRadii,
    )

    private fun w4bPlanCapabilities(): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
        deviceGeneration = 7,
        maxTextureDimension2D = 2048,
        maxBufferSizeBytes = 1L shl 20,
        copyBytesPerRowAlignment = 256,
        supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
        minUniformBufferOffsetAlignment = 256,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
        supportedOperations = PlanOperationCapability.entries.toSet(),
        bufferAllocationPolicy = PlanBufferAllocationPolicy.of(16_384, 4_096, 4_096),
    )

    private fun w4bCapabilities(): GPUCapabilities = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "w4b", "adapter", "device"),
        facts = listOf(GPUCapabilityFact("w4b.scalar_aa", "test", "supported", true, "w4b")),
        snapshotId = "w4b-uniform-abi",
        limits = GPULimits(
            maxTextureDimension2D = 2048,
            copyBytesPerRowAlignment = 256,
            minUniformBufferOffsetAlignment = 256,
            maxBufferSize = 1L shl 20,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
        supportedTextureFormats = setOf(GPUTextureFormat.RGBA8UnormSrgb),
        textureFormatSampleSupport = GPUTextureFormatSampleSupport(
            mapOf(GPUTextureFormat.RGBA8UnormSrgb to GPUTextureSampleCountSupport(setOf(1))),
        ),
        rendererFeatures = setOf(
            GPURendererFeature.RenderPass,
            GPURendererFeature.CopyUpload,
            GPURendererFeature.UniformBuffer,
            GPURendererFeature.Readback,
        ),
    )

    private fun ByteBuffer.floatsAt(offset: Int, count: Int): List<Float> =
        List(count) { index -> getFloat(offset + index * Float.SIZE_BYTES) }

    private fun mixedZeroRadii(corner: Int, radiusX: Float, radiusY: Float): List<Float> =
        MutableList(8) { 0f }.apply {
            this[corner * 2] = radiusX
            this[corner * 2 + 1] = radiusY
        }
}
