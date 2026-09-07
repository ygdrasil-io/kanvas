package org.graphiks.kanvas.gpu.renderer.planning

import io.ygdrasil.webgpu.GPUTextureFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.gpu.plan.AttachmentLoadPlan
import org.graphiks.kanvas.gpu.plan.GpuPlanSelection
import org.graphiks.kanvas.gpu.plan.PlanBudget
import org.graphiks.kanvas.gpu.plan.PlanBufferAllocationPolicy
import org.graphiks.kanvas.gpu.plan.PlanCapabilitySnapshot
import org.graphiks.kanvas.gpu.plan.PlanDepthStencilFormat
import org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat
import org.graphiks.kanvas.gpu.plan.PlanOperationCapability
import org.graphiks.kanvas.gpu.plan.PlanPass
import org.graphiks.kanvas.gpu.plan.PathFillDraw
import org.graphiks.kanvas.gpu.plan.PathStrokeDraw
import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.plan.W4aAnalyticRectPlanCompiler
import org.graphiks.kanvas.gpu.plan.W4cPathFillPlanCompiler
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureFormatSampleSupport
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureSampleCountSupport
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveFillRule
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.passes.W4cSessionScratchDrawV1
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUDepthStencilLoadStorePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUStencilLoadOperation
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUPathSourceAuthority
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.kanvas.render.ir.BlendMode
import org.graphiks.kanvas.render.ir.BlendNode
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.CoverageRequest
import org.graphiks.kanvas.render.ir.DrawNode
import org.graphiks.kanvas.render.ir.DrawOrigin
import org.graphiks.kanvas.render.ir.EffectStack
import org.graphiks.kanvas.render.ir.GeometryNode
import org.graphiks.kanvas.render.ir.MaterialNode
import org.graphiks.kanvas.render.ir.PaintNode
import org.graphiks.kanvas.render.ir.PaintStyleNode
import org.graphiks.kanvas.render.ir.RenderPlanResult
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneExtent
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.kanvas.render.ir.StrokeCapNode
import org.graphiks.kanvas.render.ir.StrokeJoinNode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.FillRule
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.PathStrokeCap
import org.graphiks.math.geometry.PathStrokeDrawMode
import org.graphiks.math.geometry.PathStrokeGeometryF32
import org.graphiks.math.geometry.PathStrokeJoin
import org.graphiks.math.geometry.PathStrokePreparationResult
import org.graphiks.math.geometry.PathStrokeStyleF64
import org.graphiks.math.geometry.PathStrokeWidthF64
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.matrix.preparePathStrokeGeometryF32
import org.junit.jupiter.api.Test as JunitTest

class GpuPlanTaskListLowererW4cTest {
    private val lowerer = GpuPlanTaskListLowerer()

    @JunitTest
    fun `W4c lowerer preserves direct stencil direct roles loads ranges and authority`() {
        val graph = readyW4cGraph()

        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(lowerer.lower(request(graph)))
        val tasks = lowered.taskList.tasks
        val preparation = assertIs<GPUTask.PrepareResources>(tasks.first())
        val renders = tasks.filterIsInstance<GPUTask.Render>()
        val readback = assertIs<GPUTask.Readback>(tasks.last())

        assertEquals(
            listOf(GPUFrameResourceRole.SceneTarget, GPUFrameResourceRole.ReadbackStaging),
            preparation.requests.map { it.role },
        )
        assertEquals(listOf("direct", "producer", "cover", "direct"), renders.map(::w4cSemanticRole))
        assertEquals(
            listOf("clear", "load", "load", "load"),
            renders.map { it.loadStore.loadOp },
        )
        assertEquals(
            listOf(0L, 1L, 1L, 2L),
            renders.map { it.drawPackets.single().sortKey },
        )
        assertEquals(
            listOf(0, 1, 1, 2),
            renders.map { it.drawPackets.single().originalPaintOrder },
        )
        assertEquals(
            GPUDepthStencilLoadStorePlan.WritableStencil(
                GPUStencilLoadOperation.Clear,
                GPUStorePlan.Store,
                0u,
            ),
            renders[1].depthStencilLoadStore,
        )
        assertEquals(
            GPUDepthStencilLoadStorePlan.WritableStencil(
                GPUStencilLoadOperation.Load,
                GPUStorePlan.Store,
                null,
            ),
            renders[2].depthStencilLoadStore,
        )
        assertEquals(GPUFrameResourceRole.PathDepthStencil, renders[1].resourceUses.single().role)
        assertEquals(GPUFrameResourceRole.PathDepthStencil, renders[2].resourceUses.single().role)
        assertEquals(true, renders[1].resourceUses.single().write)
        assertEquals(true, renders[2].resourceUses.single().write)

        val semantics = renders.map { render ->
            assertIs<GPUDrawSemanticPayload.CorePrimitive>(render.drawPackets.single().semanticPayload)
        }
        assertEquals(
            listOf(
                GPUCorePrimitiveCoverageMode.FullOrScissor,
                GPUCorePrimitiveCoverageMode.Stencil1x,
                GPUCorePrimitiveCoverageMode.Stencil1x,
                GPUCorePrimitiveCoverageMode.FullOrScissor,
            ),
            semantics.map { it.coverageMode },
        )
        assertEquals(
            listOf(
                GPUCorePrimitiveGeometryMode.DirectTriangles,
                GPUCorePrimitiveGeometryMode.StencilEdgeFan,
                GPUCorePrimitiveGeometryMode.StencilEdgeFan,
                GPUCorePrimitiveGeometryMode.DirectTriangles,
            ),
            semantics.map { assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(it.geometry).geometryMode },
        )
        assertEquals(
            listOf(
                GPUCorePrimitiveFillRule.Winding,
                GPUCorePrimitiveFillRule.EvenOdd,
                GPUCorePrimitiveFillRule.EvenOdd,
                GPUCorePrimitiveFillRule.Winding,
            ),
            semantics.map { assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(it.geometry).fillRule },
        )
        semantics.forEach { semantic ->
            val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
            assertEquals(GPUPathSourceAuthority.W4cPlannedPathFillV1, geometry.sourceAuthority)
            assertEquals(32L, semantic.payloadRef.uniformBlock?.byteSize)
        }
        assertEquals(GPUPixelBounds(1, 0, 3, 3), semantics.first().scissorBounds)

        val firstGraphDraw = assertIs<PathFillDraw>(
            assertIs<PlanPass.RenderPass>(graph.passes()[0]).draws().single(),
        )
        val stencilGraphDraw = assertIs<PathFillDraw>(
            assertIs<PlanPass.StencilProducer>(graph.passes()[1]).draw,
        )
        assertEquals(
            firstGraphDraw.copyGeometryF32().copyDirectTriangleF32OrNull()!!.copyVerticesF32().toList(),
            assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantics[0].geometry).vertices,
        )
        assertEquals(
            stencilGraphDraw.copyGeometryF32().copyStencilEdgeFanF32OrNull()!!.copyVerticesF32().toList(),
            assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantics[1].geometry).vertices,
        )
        assertEquals(
            assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantics[1].geometry).vertices,
            assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantics[2].geometry).vertices,
        )

        val scratch = assertNotNull(renders.first().drawPackets.single().corePrimitivePreparedAuthority?.w4cSessionScratch)
        renders.flatMap { it.drawPackets }.forEach { packet ->
            assertSame(scratch, packet.corePrimitivePreparedAuthority?.w4cSessionScratch)
        }
        assertEquals(listOf(0L, 24L, 176L), scratch.draws.map { it.vertexOffsetBytes })
        assertEquals(listOf(24L, 152L, 24L), scratch.draws.map { it.vertexRangeBytes })
        assertEquals(listOf(0L, 12L, 96L), scratch.draws.map { it.indexOffsetBytes })
        assertEquals(listOf(12L, 84L, 12L), scratch.draws.map { it.indexRangeBytes })
        assertEquals(
            listOf(FillRule.WINDING, FillRule.EVEN_ODD, FillRule.WINDING),
            scratch.draws.map(W4cSessionScratchDrawV1::fillRule),
        )
        assertEquals(listOf(0L, 256L, 512L), scratch.uniformPlan.slots.map { it.alignedOffset })
        assertEquals(16_384L, scratch.vertexCapacityBytes)
        assertEquals(4_096L, scratch.indexCapacityBytes)
        assertEquals(4_096L, scratch.uniformCapacityBytes)
        assertEquals(
            listOf(
                preparation.taskId to renders[0].taskId,
                renders[0].taskId to renders[1].taskId,
                renders[1].taskId to renders[2].taskId,
                renders[2].taskId to renders[3].taskId,
                renders[3].taskId to readback.taskId,
            ),
            lowered.taskList.dependencies.map { it.fromTaskId to it.toTaskId },
        )
        assertEquals(
            listOf(null, null, "w4c:1", null, null),
            lowered.taskList.dependencies.map { it.atomicGroupId?.value },
        )
    }

    @JunitTest
    fun `W4c lowerer treats stale capability as unsupported and a foreign lane as invalid`() {
        val graph = readyW4cGraph()

        assertIs<GpuPlanLoweringResult.UnsupportedCapability>(
            lowerer.lower(request(graph, rendererCapabilities(depthStencilSupported = false))),
        )
        assertIs<GpuPlanLoweringResult.InvalidPlan>(
            W4cPathFillGraphLowerer().lower(request(readyW4aGraph())),
        )
    }

    @Test
    fun `W4c lowerer refuses direct-only graph without physical D24S8 facts`() {
        val graph = directOnlyW4cGraphWithoutDepthStencilFacts()

        assertIs<GpuPlanLoweringResult.UnsupportedCapability>(
            lowerer.lower(request(graph, rendererCapabilities(depthStencilSupported = false))),
        )
    }

    @Test
    fun `W4c lowerer rejects a forged W4c graph containing a stroke draw`() {
        val fillGraph = readyW4cGraph(listOf(triangle()))
        val fillPass = assertIs<PlanPass.RenderPass>(fillGraph.passes().first())
        val fillDraw = assertIs<PathFillDraw>(fillPass.draws().single())
        val strokePass = PlanPass.RenderPass(
            ordinal = fillPass.ordinal,
            target = fillPass.target,
            draws = listOf(
                PathStrokeDraw.of(
                    commandIndex = fillDraw.commandIndex,
                    color = fillDraw.color,
                    geometryF32 = directStrokeAndFillGeometry(),
                    scissorI32 = fillDraw.copyScissorI32(),
                ),
            ),
            load = fillPass.load,
            store = fillPass.store,
            drawDataResources = fillPass.drawDataResources,
        )
        val forged = RenderGraph.of(
            id = fillGraph.id,
            capabilityId = fillGraph.capabilityId,
            targetExtent = fillGraph.targetExtent,
            colorFormat = fillGraph.colorFormat,
            capabilities = fillGraph.capabilities,
            budget = fillGraph.budget,
            visualCommandCount = fillGraph.visualCommandCount,
            resources = fillGraph.resources(),
            passes = listOf(strokePass) + fillGraph.passes().drop(1),
            dependencies = fillGraph.dependencies(),
            peakFrameLocalBytes = fillGraph.peakFrameLocalBytes,
        )

        assertIs<GpuPlanLoweringResult.InvalidPlan>(W4cPathFillGraphLowerer().lower(request(forged)))
    }

    @Test
    fun `only sealed W4c lowering admits a 1025 edge fan while generic authorities keep the legacy ceiling`() {
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(
            lowerer.lower(request(readyW4cGraph(listOf(largeEvenOdd())))),
        )
        val w4c = assertIs<GPUDrawSemanticPayload.CorePrimitive>(
            lowered.taskList.tasks.filterIsInstance<GPUTask.Render>().first().drawPackets.single().semanticPayload,
        )

        assertEquals(
            1_025,
            assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(w4c.geometry).sourceVertexCount,
        )
        assertFailsWith<IllegalArgumentException> {
            pathSemantic(GPUPathSourceAuthority.W4cPlannedPathFillV1)
        }
        assertFailsWith<IllegalArgumentException> {
            pathSemantic(GPUPathSourceAuthority.Unknown)
        }
    }

    private fun w4cSemanticRole(render: GPUTask.Render): String = when (render.drawPackets.single().role.name) {
        "Shading" -> "direct"
        "PathStencilProducer" -> "producer"
        "PathStencilCover" -> "cover"
        else -> error("Unexpected W4c packet role")
    }

    private fun readyW4cGraph(
        draws: List<SceneCommand.Draw> = listOf(
            triangle(ClipStackNode.DeviceRect.of(RectF32(1f, 0f, 3f, 3f), antiAlias = false)),
            concaveEvenOdd(),
            triangle(),
        ),
    ): RenderGraph {
        val scene = SceneSnapshot.of(
            SceneExtent(4, 4),
            ColorSpace.SRGB,
            draws,
        )
        val compiler = W4cPathFillPlanCompiler()
        val candidate = assertIs<GpuPlanSelection.Candidate>(
            compiler.select(scene, RenderTargetDescriptor(scene.extent, scene.colorSpace)),
        ).candidate
        return assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(candidate, planCapabilities(), PlanBudget(1L shl 20)),
        ).plan
    }

    private fun readyW4aGraph(): RenderGraph {
        val scene = SceneSnapshot.of(
            SceneExtent(4, 4),
            ColorSpace.SRGB,
            listOf(
                SceneCommand.Draw(
                    DrawNode(
                        geometry = GeometryNode.Rect.of(RectF32(0.25f, 0.5f, 3f, 3f)),
                        material = MaterialNode.Solid(COLOR),
                        coverage = CoverageRequest.ANTIALIASED,
                        clip = ClipStackNode.Empty,
                        blend = BlendNode.SrcOver,
                        effects = EffectStack.Empty,
                        transform = Matrix3x3F32.Identity,
                        origin = DrawOrigin.RECT,
                        paint = PaintNode(
                            COLOR,
                            null,
                            BlendMode.SRC_OVER,
                            null,
                            null,
                            null,
                            null,
                            null,
                            PaintStyleNode.FILL,
                            0f,
                            StrokeCapNode.BUTT,
                            StrokeJoinNode.MITER,
                            4f,
                            false,
                        ),
                    ),
                ),
            ),
        )
        val compiler = W4aAnalyticRectPlanCompiler()
        val candidate = assertIs<GpuPlanSelection.Candidate>(
            compiler.select(scene, RenderTargetDescriptor(scene.extent, scene.colorSpace)),
        ).candidate
        return assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(candidate, historicalPlanCapabilities(), PlanBudget(1L shl 20)),
        ).plan
    }

    private fun directOnlyW4cGraphWithoutDepthStencilFacts(): RenderGraph {
        val scene = SceneSnapshot.of(
            SceneExtent(4, 4),
            ColorSpace.SRGB,
            listOf(triangle()),
        )
        val compiler = W4cPathFillPlanCompiler()
        val candidate = assertIs<GpuPlanSelection.Candidate>(
            compiler.select(scene, RenderTargetDescriptor(scene.extent, scene.colorSpace)),
        ).candidate
        val directOnly = assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(candidate, planCapabilities(), PlanBudget(1L shl 20)),
        ).plan
        return RenderGraph.of(
            id = directOnly.id,
            capabilityId = directOnly.capabilityId,
            targetExtent = directOnly.targetExtent,
            colorFormat = directOnly.colorFormat,
            capabilities = historicalPlanCapabilities(),
            budget = directOnly.budget,
            visualCommandCount = directOnly.visualCommandCount,
            resources = directOnly.resources(),
            passes = directOnly.passes(),
            dependencies = directOnly.dependencies(),
            peakFrameLocalBytes = directOnly.peakFrameLocalBytes,
        )
    }

    private fun directStrokeAndFillGeometry(): PathStrokeGeometryF32 = assertIs<PathStrokePreparationResult.Ready>(
        Matrix3x3F32.Identity.preparePathStrokeGeometryF32(
            path = PathBuilder()
                .moveTo(0f, 0f)
                .lineTo(4f, 0f)
                .lineTo(0f, 3f)
                .close()
                .build(),
            styleF64 = PathStrokeStyleF64(
                widthF64 = PathStrokeWidthF64.Finite(0.0),
                cap = PathStrokeCap.Butt,
                join = PathStrokeJoin.Miter,
                miterLimitF64 = 4.0,
            ),
            mode = PathStrokeDrawMode.StrokeAndFill,
        ),
    ).geometryF32

    private fun triangle(clip: ClipStackNode = ClipStackNode.Empty): SceneCommand.Draw =
        SceneCommand.Draw(pathNode(
            PathBuilder()
                .moveTo(0f, 0f)
                .lineTo(4f, 0f)
                .lineTo(0f, 3f)
                .close()
                .build(),
            clip,
        ))

    private fun concaveEvenOdd(): SceneCommand.Draw = SceneCommand.Draw(pathNode(
        PathBuilder(FillRule.EVEN_ODD)
            .moveTo(0f, 0f)
            .lineTo(4f, 0f)
            .lineTo(4f, 3f)
            .lineTo(2f, 1f)
            .lineTo(0f, 3f)
            .close()
            .build(),
        ))

    private fun largeEvenOdd(): SceneCommand.Draw {
        val path = PathBuilder(FillRule.EVEN_ODD).apply {
            repeat(1_025) { index ->
                val angle = 2.0 * Math.PI * index / 1_025.0
                val x = (2.0 + 1.5 * kotlin.math.cos(angle)).toFloat()
                val y = (2.0 + 1.5 * kotlin.math.sin(angle)).toFloat()
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }.build()
        return SceneCommand.Draw(pathNode(path))
    }

    private fun pathNode(path: org.graphiks.math.geometry.PathF32, clip: ClipStackNode = ClipStackNode.Empty): DrawNode =
        DrawNode(
            geometry = GeometryNode.Path(path),
            material = MaterialNode.Solid(COLOR),
            coverage = CoverageRequest.HARD_EDGE,
            clip = clip,
            blend = BlendNode.SrcOver,
            effects = EffectStack.Empty,
            transform = Matrix3x3F32.Identity,
            origin = DrawOrigin.PATH,
            paint = PaintNode(
                COLOR,
                null,
                BlendMode.SRC_OVER,
                null,
                null,
                null,
                null,
                null,
                PaintStyleNode.FILL,
                0f,
                StrokeCapNode.BUTT,
                StrokeJoinNode.MITER,
                4f,
                false,
            ),
        )

    private fun request(
        graph: RenderGraph,
        capabilities: GPUCapabilities = rendererCapabilities(),
    ): GpuPlanLoweringRequest = GpuPlanLoweringRequest(
        graph = graph,
        capabilities = capabilities,
        deviceGeneration = GPUDeviceGenerationID(7),
        currentBudget = graph.budget,
        frameId = GPUFrameID(4),
        recordingId = GPURecordingID("w4c-lowering"),
    )

    private fun planCapabilities(): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
        deviceGeneration = 7,
        maxTextureDimension2D = 2048,
        maxBufferSizeBytes = 1L shl 20,
        copyBytesPerRowAlignment = 256,
        supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
        minUniformBufferOffsetAlignment = 256,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
        supportedOperations = setOf(
            PlanOperationCapability.RenderPass,
            PlanOperationCapability.CopyUpload,
            PlanOperationCapability.UniformBuffer,
            PlanOperationCapability.Readback,
            PlanOperationCapability.DepthStencilAttachment,
            PlanOperationCapability.StencilCover,
        ),
        bufferAllocationPolicy = PlanBufferAllocationPolicy.of(16_384L, 4_096L, 4_096L),
        supportedDepthStencilFormats = setOf(PlanDepthStencilFormat.Depth24PlusStencil8),
    )

    private fun historicalPlanCapabilities(): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
        deviceGeneration = 7,
        maxTextureDimension2D = 2048,
        maxBufferSizeBytes = 1L shl 20,
        copyBytesPerRowAlignment = 256,
        supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
        minUniformBufferOffsetAlignment = 256,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
        supportedOperations = setOf(
            PlanOperationCapability.RenderPass,
            PlanOperationCapability.CopyUpload,
            PlanOperationCapability.UniformBuffer,
            PlanOperationCapability.Readback,
        ),
        bufferAllocationPolicy = PlanBufferAllocationPolicy.of(16_384L, 4_096L, 4_096L),
    )

    private fun rendererCapabilities(depthStencilSupported: Boolean = true): GPUCapabilities = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "test", "w4c", "device"),
        facts = emptyList(),
        snapshotId = "w4c-test",
        limits = GPULimits(
            maxTextureDimension2D = 2048,
            copyBytesPerRowAlignment = 256,
            minUniformBufferOffsetAlignment = 256,
            maxBufferSize = 1L shl 20,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
        supportedTextureFormats = buildSet {
            add(GPUTextureFormat.RGBA8UnormSrgb)
            if (depthStencilSupported) add(GPUTextureFormat.Depth24PlusStencil8)
        },
        textureFormatSampleSupport = GPUTextureFormatSampleSupport(
            buildMap {
                put(GPUTextureFormat.RGBA8UnormSrgb, GPUTextureSampleCountSupport(setOf(1)))
                if (depthStencilSupported) {
                    put(GPUTextureFormat.Depth24PlusStencil8, GPUTextureSampleCountSupport(setOf(1)))
                }
            },
        ),
        rendererFeatures = setOf(
            GPURendererFeature.RenderPass,
            GPURendererFeature.CopyUpload,
            GPURendererFeature.UniformBuffer,
            GPURendererFeature.Readback,
        ),
    )

    private fun pathSemantic(authority: GPUPathSourceAuthority): GPUDrawSemanticPayload.CorePrimitive {
        val edgeCount = 1_025
        fun point(index: Int): Pair<Float, Float> =
            ((index % 3) + 1).toFloat() to (((index / 3) % 3) + 1).toFloat()
        val vertices = buildList(edgeCount * 6) {
            repeat(edgeCount) { edge ->
                val current = point(edge)
                val next = point((edge + 1) % edgeCount)
                add(0f)
                add(0f)
                add(current.first)
                add(current.second)
                add(next.first)
                add(next.second)
            }
        }
        val target = GPUPixelBounds(0, 0, 4, 4)
        return GPUCorePrimitivePayloadGatherer().gatherSemantic(
            GPUCorePrimitivePayloadInput(
                commandIdValue = 7,
                sourceFamily = GPUCorePrimitiveSourceFamily.Path,
                geometry = GPUCorePrimitiveGeometryInput.TriangulatedPath(
                    vertices = vertices,
                    indices = (0 until edgeCount * 3).toList(),
                    sourceContourStarts = listOf(0),
                    sourceVertexCount = edgeCount,
                    coverBounds = target,
                    geometryMode = GPUCorePrimitiveGeometryMode.StencilEdgeFan,
                    fillRule = GPUCorePrimitiveFillRule.EvenOdd,
                    sourceAuthority = authority,
                ),
                premultipliedRgba = listOf(1f, 0f, 0f, 1f),
                targetBounds = target,
                scissorBounds = target,
                clipCoveragePlan = GPUClipCoveragePlan.NoClip,
                blendPlanIdentity = "w4c-test-src-over",
                frameProvenance = GPUFrameProvenance.None,
                coverageMode = GPUCorePrimitiveCoverageMode.Stencil1x,
            ),
        )
    }

    private companion object {
        val COLOR: ColorARGB = ColorARGB.fromPackedUInt(0x80ff0000u)
    }
}
