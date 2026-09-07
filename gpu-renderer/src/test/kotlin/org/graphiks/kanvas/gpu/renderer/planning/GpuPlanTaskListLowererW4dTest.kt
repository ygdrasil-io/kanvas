package org.graphiks.kanvas.gpu.renderer.planning

import io.ygdrasil.webgpu.GPUTextureFormat
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.gpu.plan.GpuPlanSelection
import org.graphiks.kanvas.gpu.plan.PlanBudget
import org.graphiks.kanvas.gpu.plan.PlanBufferAllocationPolicy
import org.graphiks.kanvas.gpu.plan.PlanCapabilitySnapshot
import org.graphiks.kanvas.gpu.plan.PlanDepthStencilFormat
import org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat
import org.graphiks.kanvas.gpu.plan.PlanOperationCapability
import org.graphiks.kanvas.gpu.plan.PlanAtomicGroupId
import org.graphiks.kanvas.gpu.plan.PlanPass
import org.graphiks.kanvas.gpu.plan.PlanPassDependency
import org.graphiks.kanvas.gpu.plan.PlanResource
import org.graphiks.kanvas.gpu.plan.PlanResourceRole
import org.graphiks.kanvas.gpu.plan.PathStrokeDraw
import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.plan.W4aAnalyticRectPlanCompiler
import org.graphiks.kanvas.gpu.plan.W4dPathStrokePlanCompiler
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureFormatSampleSupport
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureSampleCountSupport
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.passes.W4dSessionScratchV1
import org.graphiks.kanvas.gpu.renderer.recording.GPUDepthStencilLoadStorePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUStencilLoadOperation
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
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
import org.graphiks.kanvas.render.ir.PathEffectNode
import org.graphiks.kanvas.render.ir.ImmutableFloats
import org.graphiks.kanvas.render.ir.RenderPlanResult
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneExtent
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.kanvas.render.ir.StrokeCapNode
import org.graphiks.kanvas.render.ir.StrokeJoinNode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorF32
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.FillRule
import org.graphiks.math.geometry.PathF32
import org.graphiks.math.geometry.PathStrokeCap
import org.graphiks.math.geometry.PathStrokeDrawMode
import org.graphiks.math.geometry.PathStrokeJoin
import org.graphiks.math.geometry.PathStrokeStyleF64
import org.graphiks.math.geometry.PathStrokeWidthF64
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Test

class GpuPlanTaskListLowererW4dTest {
    @Test
    fun `real mixed W4d graph lowers direct and atomic stencil passes without reclassifying styles`() {
        val graph = readyGraph(
            listOf(
                pathDraw(PaintStyleNode.FILL),
                pathDraw(PaintStyleNode.FILL, path = concavePath(FillRule.EVEN_ODD)),
                pathDraw(
                    PaintStyleNode.STROKE,
                    width = 3f,
                    cap = StrokeCapNode.ROUND,
                    join = StrokeJoinNode.BEVEL,
                    miter = 2f,
                    effect = PathEffectNode.Dash(ImmutableFloats.copyOf(floatArrayOf(2f, 1f)), -1f),
                ),
                pathDraw(PaintStyleNode.STROKE_AND_FILL, width = 0f),
            ),
        )

        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(GpuPlanTaskListLowerer().lower(request(graph)))
        val tasks = lowered.taskList.tasks
        val prepare = assertIs<GPUTask.PrepareResources>(tasks.first())
        val renders = tasks.filterIsInstance<GPUTask.Render>()
        val readback = assertIs<GPUTask.Readback>(tasks.last())
        assertEquals(
            listOf("direct", "producer", "cover", "producer", "cover", "direct"),
            renders.map(::semanticRole),
        )
        assertEquals(listOf("clear", "load", "load", "load", "load", "load"), renders.map { it.loadStore.loadOp })
        assertEquals(listOf(0, 1, 1, 2, 2, 3), renders.map { it.drawPackets.single().originalPaintOrder })
        assertEquals(
            listOf(null, null, "w4c:1", null, "w4d:2", null, null),
            lowered.taskList.dependencies.map { it.atomicGroupId?.value },
        )
        assertEquals(
            listOf(GPUFrameResourceRole.SceneTarget, GPUFrameResourceRole.ReadbackStaging),
            prepare.requests.map { it.role },
        )
        assertEquals(1, graph.resources().count { it.role == PlanResourceRole.DepthStencil })
        assertEquals(
            GPUDepthStencilLoadStorePlan.WritableStencil(GPUStencilLoadOperation.Clear, GPUStorePlan.Store, 0u),
            renders[1].depthStencilLoadStore,
        )
        assertEquals(
            GPUDepthStencilLoadStorePlan.WritableStencil(GPUStencilLoadOperation.Load, GPUStorePlan.Store, null),
            renders[2].depthStencilLoadStore,
        )

        val semantics = renders.map { render ->
            assertIs<GPUDrawSemanticPayload.CorePrimitive>(render.drawPackets.single().semanticPayload)
        }
        assertEquals(
            listOf(
                GPUCorePrimitiveGeometryMode.DirectTriangles,
                GPUCorePrimitiveGeometryMode.StencilEdgeFan,
                GPUCorePrimitiveGeometryMode.StencilEdgeFan,
                GPUCorePrimitiveGeometryMode.StencilEdgeFan,
                GPUCorePrimitiveGeometryMode.StencilEdgeFan,
                GPUCorePrimitiveGeometryMode.DirectTriangles,
            ),
            semantics.map { semantic ->
                assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry).geometryMode
            },
        )
        semantics.forEach { semantic ->
            val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
            assertEquals(GPUPathSourceAuthority.W4dPlannedPathStrokeV1, geometry.sourceAuthority)
            assertNull(geometry.strokeStyle, "W4d must not synthesize the legacy stroke-style ABI")
        }

        val scratch = assertNotNull(
            renders.first().drawPackets.single().corePrimitivePreparedAuthority?.w4dSessionScratch,
        )
        renders.flatMap { it.drawPackets }.forEach { packet ->
            assertSame(scratch, packet.corePrimitivePreparedAuthority?.w4dSessionScratch)
            assertNull(packet.corePrimitivePreparedAuthority?.w4cSessionScratch)
        }
        assertEquals(W4dPathStrokePlanCompiler.CAPABILITY_ID, scratch.capabilityId)
        assertEquals(graph.passes().dropLast(1).map(PlanPass::id), scratch.renderPassIds)
        assertEquals(graph.passes().last().id, scratch.readbackPassId)
        assertEquals(graph.passes().size, scratch.resourceLastPassIndexExclusive)
        assertEquals(graph.resources().single { it.role == PlanResourceRole.DepthStencil }.firstPassIndex, scratch.depthStencilFirstPassIndex)
        assertEquals(listOf(false, false, true, true), scratch.draws.map { it.isStroke })
        assertEquals(listOf(null, null, PathStrokeDrawMode.Stroke, PathStrokeDrawMode.StrokeAndFill), scratch.draws.map { it.mode })
        val dashed = assertNotNull(scratch.draws[2].styleF64)
        assertEquals(PathStrokeCap.Round, dashed.cap)
        assertEquals(PathStrokeJoin.Bevel, dashed.join)
        assertEquals(3.0, assertIs<PathStrokeWidthF64.Finite>(dashed.widthF64).valueF64)
        assertEquals(2.0, dashed.miterLimitF64)
        assertContentEquals(doubleArrayOf(2.0, 1.0), dashed.dashF64!!.copyIntervalsF64())
        assertEquals(-1.0, dashed.dashF64!!.phaseF64)
        assertEquals(0.0, assertIs<PathStrokeWidthF64.Finite>(scratch.draws[3].styleF64!!.widthF64).valueF64)
        assertEquals(scratch.draws.sumOf { it.vertexRangeBytes }, scratch.vertexUsefulBytes)
        assertEquals(scratch.draws.sumOf { it.indexRangeBytes }, scratch.indexUsefulBytes)
        assertEquals(scratch.draws.size * 32L, scratch.uniformUsefulBytes)
        assertEquals(listOf(0L, 256L, 512L, 768L), scratch.uniformPlan.slots.map { it.alignedOffset })
        assertEquals(graph.resources().single { it.role == PlanResourceRole.VertexData }.byteSize, scratch.vertexCapacityBytes)
        assertEquals(graph.resources().single { it.role == PlanResourceRole.IndexData }.byteSize, scratch.indexCapacityBytes)
        assertEquals(graph.resources().single { it.role == PlanResourceRole.UniformData }.byteSize, scratch.uniformCapacityBytes)
        assertEquals("w4d.${graph.id.value}.readback", lowered.readbackRequestId)
        assertEquals(readback.request.requestId.value, lowered.readbackRequestId)
    }

    @Test
    fun `direct-only zero-width stroke-and-fill lowers without D24S8 capability or ownership`() {
        val graph = readyGraph(
            listOf(pathDraw(PaintStyleNode.STROKE_AND_FILL, width = 0f)),
            planCapabilities(depthStencilSupported = false),
        )

        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(
            GpuPlanTaskListLowerer().lower(request(graph, rendererCapabilities(depthStencilSupported = false))),
        )
        val render = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>().single()
        val scratch = assertNotNull(render.drawPackets.single().corePrimitivePreparedAuthority?.w4dSessionScratch)
        assertEquals("direct", semanticRole(render))
        assertNull(scratch.depthStencilResourceId)
        assertNull(scratch.depthStencilFirstPassIndex)
        assertEquals(0L, scratch.depthStencilBytes)
        assertEquals(0, graph.resources().count { it.role == PlanResourceRole.DepthStencil })
    }

    @Test
    fun `lowerer retains every cap join miter dash and hairline style axis in sealed W4d scratch`() {
        StrokeCapNode.entries.forEach { cap ->
            StrokeJoinNode.entries.forEach { join ->
                val graph = readyGraph(
                    listOf(
                        pathDraw(
                            PaintStyleNode.STROKE,
                            width = 2f,
                            cap = cap,
                            join = join,
                            miter = 0.5f,
                            effect = PathEffectNode.Dash(
                                ImmutableFloats.copyOf(floatArrayOf(3f, 1f)),
                                -2f,
                            ),
                        ),
                    ),
                )

                val lowered = assertIs<GpuPlanLoweringResult.Lowered>(
                    GpuPlanTaskListLowerer().lower(request(graph)),
                )
                val render = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>().first()
                val scratch = assertNotNull(
                    render.drawPackets.single().corePrimitivePreparedAuthority?.w4dSessionScratch,
                )
                val draw = scratch.draws.single()
                val style = assertNotNull(draw.styleF64)
                assertEquals(PathStrokeDrawMode.Stroke, draw.mode)
                assertEquals(expectedCap(cap), style.cap)
                assertEquals(expectedJoin(join), style.join)
                assertEquals(2.0, assertIs<PathStrokeWidthF64.Finite>(style.widthF64).valueF64)
                assertEquals(0.5, style.miterLimitF64)
                assertContentEquals(doubleArrayOf(3.0, 1.0), style.dashF64!!.copyIntervalsF64())
                assertEquals(-2.0, style.dashF64!!.phaseF64)
            }
        }

        val hairline = readyGraph(listOf(pathDraw(PaintStyleNode.STROKE, width = 0f)))
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(GpuPlanTaskListLowerer().lower(request(hairline)))
        val render = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>().first()
        val draw = assertNotNull(
            render.drawPackets.single().corePrimitivePreparedAuthority?.w4dSessionScratch,
        ).draws.single()
        assertEquals(PathStrokeDrawMode.Stroke, draw.mode)
        assertIs<PathStrokeWidthF64.Hairline>(assertNotNull(draw.styleF64).widthF64)
    }

    @Test
    fun `W4d lowering rejects foreign stale and structurally forged graphs terminally`() {
        val graph = readyGraph(listOf(pathDraw(PaintStyleNode.STROKE)))
        assertIs<GpuPlanLoweringResult.InvalidPlan>(W4dPathStrokeGraphLowerer().lower(request(readyW4aGraph())))
        assertIs<GpuPlanLoweringResult.UnsupportedCapability>(
            GpuPlanTaskListLowerer().lower(request(graph, rendererCapabilities(depthStencilSupported = false))),
        )

        val reorderedResources = forgedGraph(graph, resources = graph.resources().reversed())
        assertIs<GpuPlanLoweringResult.InvalidPlan>(GpuPlanTaskListLowerer().lower(request(reorderedResources)))
        val forgedIdentity = forgedGraph(graph, id = org.graphiks.kanvas.gpu.plan.PlanId("not-a-canonical-plan-id"))
        assertIs<GpuPlanLoweringResult.InvalidPlan>(GpuPlanTaskListLowerer().lower(request(forgedIdentity)))
        val canonicalLookingForgery = forgedGraph(
            graph,
            id = org.graphiks.kanvas.gpu.plan.PlanId("0".repeat(64)),
        )
        assertIs<GpuPlanLoweringResult.InvalidPlan>(
            GpuPlanTaskListLowerer().lower(request(canonicalLookingForgery)),
        )
        val forgedCapability = forgedGraph(graph, capabilityId = "gpu.plan.w4d.path-stroke.v999")
        assertIs<GpuPlanLoweringResult.InvalidPlan>(GpuPlanTaskListLowerer().lower(request(forgedCapability)))
        val forgedSnapshot = forgedGraph(
            graph,
            capabilities = planCapabilities(maxBufferSizeBytes = 2L shl 20),
        )
        assertIs<GpuPlanLoweringResult.UnsupportedCapability>(
            GpuPlanTaskListLowerer().lower(request(forgedSnapshot)),
        )
        val forgedBudget = forgedGraph(graph, budget = PlanBudget(graph.budget.maxFrameLocalBytes + 1L))
        assertIs<GpuPlanLoweringResult.InvalidPlan>(GpuPlanTaskListLowerer().lower(request(forgedBudget)))
        listOf(
            PlanResourceRole.VertexData,
            PlanResourceRole.IndexData,
            PlanResourceRole.UniformData,
        ).forEach { role ->
            val resource = graph.resources().single { it.role == role }
            val undersized = resource.copyWith(byteSize = 1L)
            val forged = forgedGraph(
                graph,
                resources = graph.resources().map { if (it.id == resource.id) undersized else it },
                peakFrameLocalBytes = graph.peakFrameLocalBytes - resource.byteSize + undersized.byteSize,
            )
            assertIs<GpuPlanLoweringResult.InvalidPlan>(
                GpuPlanTaskListLowerer().lower(request(forged)),
                "$role must not publish W4d authority from undersized scratch",
            )
        }
        val depthStencil = graph.resources().single { it.role == PlanResourceRole.DepthStencil }
        assertFailsWith<IllegalArgumentException> {
            depthStencil.copyWith(byteSize = depthStencil.byteSize - 1L)
        }
        val hugeCapabilities = planCapabilities(maxBufferSizeBytes = Long.MAX_VALUE)
        val vertex = graph.resources().single { it.role == PlanResourceRole.VertexData }
        assertFailsWith<ArithmeticException> {
            RenderGraph.of(
                graph.id,
                graph.capabilityId,
                graph.targetExtent,
                graph.colorFormat,
                hugeCapabilities,
                PlanBudget(Long.MAX_VALUE),
                graph.visualCommandCount,
                graph.resources().map {
                    if (it.id == vertex.id) it.copyWith(byteSize = Long.MAX_VALUE) else it
                },
                graph.passes(),
                graph.dependencies(),
                graph.peakFrameLocalBytes,
            )
        }
        val fillOnly = readyGraph(listOf(pathDraw(PaintStyleNode.STROKE_AND_FILL, width = 0f)))
        val fillPass = assertIs<PlanPass.RenderPass>(fillOnly.passes().first())
        val graphFill = assertIs<org.graphiks.kanvas.gpu.plan.PathStrokeDraw>(fillPass.draws().single())
            .copyGeometryF32().copyFillGeometryF32()
        val forgedDraw = org.graphiks.kanvas.gpu.plan.PathFillDraw.of(
            commandIndex = 0,
            color = fillPass.draws().single().color,
            geometryF32 = graphFill,
            strategy = org.graphiks.kanvas.gpu.plan.PathFillStrategy.DirectTriangle,
            scissorI32 = fillPass.draws().single().let { (it as org.graphiks.kanvas.gpu.plan.PathDraw).copyScissorI32() },
        )
        val forgedPass = PlanPass.RenderPass(
            fillPass.ordinal,
            fillPass.target,
            listOf(forgedDraw),
            fillPass.load,
            fillPass.store,
            fillPass.drawDataResources,
        )
        val fillOnlyForgery = forgedGraph(fillOnly, passes = listOf(forgedPass) + fillOnly.passes().drop(1))
        assertIs<GpuPlanLoweringResult.InvalidPlan>(GpuPlanTaskListLowerer().lower(request(fillOnlyForgery)))
    }

    @Test
    fun `W4d witness rejects coherent style mode color and geometry substitutions retaining the old identity`() {
        val graph = readyGraph(listOf(pathDraw(PaintStyleNode.STROKE)))
        val original = graph.firstStrokeDraw()
        val changedStyle = PathStrokeDraw.of(
            original.commandIndex,
            original.color,
            original.copyGeometryF32(),
            original.copyScissorI32(),
            original.mode,
            PathStrokeStyleF64(
                PathStrokeWidthF64.Finite(7.0),
                PathStrokeCap.Square,
                PathStrokeJoin.Round,
                1.25,
            ),
        )
        val changedMode = PathStrokeDraw.of(
            original.commandIndex,
            original.color,
            original.copyGeometryF32(),
            original.copyScissorI32(),
            PathStrokeDrawMode.StrokeAndFill,
            original.styleF64,
        )
        val changedColor = PathStrokeDraw.of(
            original.commandIndex,
            ColorF32.Blue,
            original.copyGeometryF32(),
            original.copyScissorI32(),
            original.mode,
            original.styleF64,
        )
        val donor = readyGraph(
            listOf(pathDraw(PaintStyleNode.STROKE, path = concavePath(FillRule.WINDING))),
        ).firstStrokeDraw()
        val changedGeometry = PathStrokeDraw.of(
            original.commandIndex,
            original.color,
            donor.copyGeometryF32(),
            original.copyScissorI32(),
            original.mode,
            original.styleF64,
        )

        listOf(changedStyle, changedMode, changedColor, changedGeometry).forEach { substitution ->
            val forged = forgedGraph(graph, passes = graph.replaceStrokeDraw(substitution))
            assertIs<GpuPlanLoweringResult.InvalidPlan>(
                GpuPlanTaskListLowerer().lower(request(forged)),
            )
        }
    }

    @Test
    fun `W4d pass order group dependency and resource lifetime mutations fail before authority publication`() {
        val graph = readyGraph(listOf(pathDraw(PaintStyleNode.STROKE)))
        val producer = assertIs<PlanPass.StencilProducer>(graph.passes()[0])
        val cover = assertIs<PlanPass.StencilCover>(graph.passes()[1])

        assertFailsWith<IllegalArgumentException> {
            forgedGraph(graph, passes = listOf(cover, producer) + graph.passes().drop(2))
        }
        val forgedGroup = PlanAtomicGroupId("w4d-forged:${producer.draw.commandIndex}")
        val changedProducer = PlanPass.StencilProducer(
            producer.ordinal,
            producer.target,
            producer.depthStencil,
            producer.draw,
            producer.drawDataResources,
            forgedGroup,
            producer.load,
            producer.store,
            producer.depthStencilAccess,
            producer.depthStencilLoadStore,
        )
        val changedCover = PlanPass.StencilCover(
            cover.ordinal,
            cover.target,
            cover.depthStencil,
            cover.draw,
            cover.drawDataResources,
            forgedGroup,
            cover.load,
            cover.store,
            cover.depthStencilAccess,
            cover.depthStencilLoadStore,
        )
        assertFailsWith<IllegalArgumentException> {
            forgedGraph(graph, passes = listOf(changedProducer, changedCover) + graph.passes().drop(2))
        }
        assertFailsWith<IllegalArgumentException> {
            forgedGraph(graph, dependencies = graph.dependencies().drop(1))
        }

        val staging = graph.resources().single { it.role == PlanResourceRole.ReadbackStaging }
        val widenedLifetime = staging.copyWith(firstPassIndex = 0)
        val forgedLifetime = forgedGraph(
            graph,
            resources = graph.resources().map { if (it.id == staging.id) widenedLifetime else it },
        )
        assertIs<GpuPlanLoweringResult.InvalidPlan>(
            GpuPlanTaskListLowerer().lower(request(forgedLifetime)),
        )

        val changedLoad = PlanPass.StencilProducer(
            producer.ordinal,
            producer.target,
            producer.depthStencil,
            producer.draw,
            producer.drawDataResources,
            producer.atomicGroup,
            org.graphiks.kanvas.gpu.plan.AttachmentLoadPlan.Load,
            producer.store,
            producer.depthStencilAccess,
            producer.depthStencilLoadStore,
        )
        assertFailsWith<IllegalArgumentException> {
            forgedGraph(graph, passes = listOf(changedLoad) + graph.passes().drop(1))
        }
    }

    @Test
    fun `W4d prepared scratch refuses inconsistent facts and checked I64 overflow before publication`() {
        val graph = readyGraph(
            listOf(pathDraw(PaintStyleNode.STROKE), pathDraw(PaintStyleNode.STROKE)),
        )
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(GpuPlanTaskListLowerer().lower(request(graph)))
        val firstRender = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>().first()
        val scratch = assertNotNull(
            firstRender.drawPackets.single().corePrimitivePreparedAuthority?.w4dSessionScratch,
        )

        assertFailsWith<IllegalArgumentException> {
            scratch.rebuild(vertexUsefulBytes = scratch.vertexUsefulBytes + 1L)
        }
        assertFailsWith<ArithmeticException> {
            scratch.rebuild(uniformStrideBytes = Long.MAX_VALUE)
        }
    }

    @Test
    fun `W4d payload and prepared scratch retain defensive immutable snapshots`() {
        val intervals = floatArrayOf(4f, 2f)
        val graph = readyGraph(
            listOf(
                pathDraw(
                    PaintStyleNode.STROKE,
                    effect = PathEffectNode.Dash(ImmutableFloats.copyOf(intervals), -3f),
                ),
            ),
        )
        intervals[0] = 99f

        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(GpuPlanTaskListLowerer().lower(request(graph)))
        val render = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>().first()
        val packet = render.drawPackets.single()
        val scratch = assertNotNull(packet.corePrimitivePreparedAuthority?.w4dSessionScratch)
        val style = assertNotNull(scratch.draws.single().styleF64)
        val copied = style.dashF64!!.copyIntervalsF64()
        copied[0] = 123.0
        assertContentEquals(doubleArrayOf(4.0, 2.0), style.dashF64!!.copyIntervalsF64())
        assertFailsWith<UnsupportedOperationException> {
            (scratch.draws as MutableList).clear()
        }
        assertFailsWith<UnsupportedOperationException> {
            (scratch.renderPassIds as MutableList).clear()
        }
        val semantic = assertIs<GPUDrawSemanticPayload.CorePrimitive>(packet.semanticPayload)
        assertFailsWith<UnsupportedOperationException> {
            (assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry).vertices as MutableList).clear()
        }
    }

    private fun readyGraph(
        draws: List<SceneCommand.Draw>,
        capabilities: PlanCapabilitySnapshot = planCapabilities(),
    ): RenderGraph {
        val scene = SceneSnapshot.of(SceneExtent(16, 16), ColorSpace.SRGB, draws)
        val compiler = W4dPathStrokePlanCompiler()
        val candidate = assertIs<GpuPlanSelection.Candidate>(
            compiler.select(scene, RenderTargetDescriptor(scene.extent, scene.colorSpace)),
        ).candidate
        return assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(candidate, capabilities, PlanBudget(1L shl 20)),
        ).plan
    }

    private fun pathDraw(
        style: PaintStyleNode,
        width: Float = 2f,
        cap: StrokeCapNode = StrokeCapNode.BUTT,
        join: StrokeJoinNode = StrokeJoinNode.MITER,
        miter: Float = 4f,
        effect: PathEffectNode? = null,
        path: PathF32 = trianglePath(),
    ): SceneCommand.Draw {
        val paint = PaintNode(
            COLOR, null, BlendMode.SRC_OVER, null, null, null, null, null,
            style, width, cap, join, miter, false,
        )
        return SceneCommand.Draw(
            DrawNode(
                GeometryNode.Path(path), MaterialNode.Solid(COLOR), CoverageRequest.HARD_EDGE,
                ClipStackNode.Empty, BlendNode.SrcOver, EffectStack.Empty, Matrix3x3F32.Identity,
                DrawOrigin.PATH, paint.copy(pathEffect = effect),
            ),
        )
    }

    private fun request(
        graph: RenderGraph,
        capabilities: GPUCapabilities = rendererCapabilities(),
    ): GpuPlanLoweringRequest = GpuPlanLoweringRequest(
        graph = graph,
        capabilities = capabilities,
        deviceGeneration = GPUDeviceGenerationID(7),
        currentBudget = graph.budget,
        frameId = GPUFrameID(8),
        recordingId = GPURecordingID("w4d-lowering"),
    )

    private fun planCapabilities(
        depthStencilSupported: Boolean = true,
        maxBufferSizeBytes: Long = 1L shl 20,
    ): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
        deviceGeneration = 7,
        maxTextureDimension2D = 2048,
        maxBufferSizeBytes = maxBufferSizeBytes,
        copyBytesPerRowAlignment = 256,
        supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
        minUniformBufferOffsetAlignment = 256,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
        supportedOperations = buildSet {
            add(PlanOperationCapability.RenderPass)
            add(PlanOperationCapability.CopyUpload)
            add(PlanOperationCapability.UniformBuffer)
            add(PlanOperationCapability.Readback)
            if (depthStencilSupported) {
                add(PlanOperationCapability.DepthStencilAttachment)
                add(PlanOperationCapability.StencilCover)
            }
        },
        bufferAllocationPolicy = PlanBufferAllocationPolicy.of(16_384L, 4_096L, 4_096L),
        supportedDepthStencilFormats = if (depthStencilSupported) {
            setOf(PlanDepthStencilFormat.Depth24PlusStencil8)
        } else {
            emptySet()
        },
    )

    private fun rendererCapabilities(depthStencilSupported: Boolean = true): GPUCapabilities = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "test", "w4d", "device"),
        facts = emptyList(),
        snapshotId = "w4d-test",
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

    private fun semanticRole(render: GPUTask.Render): String = when (render.drawPackets.single().role.name) {
        "Shading" -> "direct"
        "PathStencilProducer" -> "producer"
        "PathStencilCover" -> "cover"
        else -> error("Unexpected W4d packet role")
    }

    private fun trianglePath(): PathF32 =
        PathBuilder().moveTo(2f, 2f).lineTo(12f, 2f).lineTo(2f, 12f).close().build()

    private fun concavePath(fillRule: FillRule): PathF32 = PathBuilder(fillRule)
        .moveTo(2f, 2f)
        .lineTo(14f, 2f)
        .lineTo(14f, 14f)
        .lineTo(8f, 6f)
        .lineTo(2f, 14f)
        .close()
        .build()

    private fun readyW4aGraph(): RenderGraph {
        val node = DrawNode(
            geometry = GeometryNode.Rect.of(RectF32(0.25f, 0.5f, 3f, 3f)),
            material = MaterialNode.Solid(COLOR),
            coverage = CoverageRequest.ANTIALIASED,
            clip = ClipStackNode.Empty,
            blend = BlendNode.SrcOver,
            effects = EffectStack.Empty,
            transform = Matrix3x3F32.Identity,
            origin = DrawOrigin.RECT,
            paint = PaintNode(
                COLOR, null, BlendMode.SRC_OVER, null, null, null, null, null,
                PaintStyleNode.FILL, 0f, StrokeCapNode.BUTT, StrokeJoinNode.MITER, 4f, false,
            ),
        )
        val scene = SceneSnapshot.of(SceneExtent(16, 16), ColorSpace.SRGB, listOf(SceneCommand.Draw(node)))
        val compiler = W4aAnalyticRectPlanCompiler()
        val candidate = assertIs<GpuPlanSelection.Candidate>(
            compiler.select(scene, RenderTargetDescriptor(scene.extent, scene.colorSpace)),
        ).candidate
        return assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(candidate, planCapabilities(depthStencilSupported = false), PlanBudget(1L shl 20)),
        ).plan
    }

    private fun forgedGraph(
        base: RenderGraph,
        id: org.graphiks.kanvas.gpu.plan.PlanId = base.id,
        capabilityId: String = base.capabilityId,
        resources: List<org.graphiks.kanvas.gpu.plan.PlanResource> = base.resources(),
        passes: List<PlanPass> = base.passes(),
        dependencies: List<PlanPassDependency> = base.dependencies(),
        peakFrameLocalBytes: Long = base.peakFrameLocalBytes,
        capabilities: PlanCapabilitySnapshot = base.capabilities,
        budget: PlanBudget = base.budget,
    ): RenderGraph = RenderGraph.of(
        id = id,
        capabilityId = capabilityId,
        targetExtent = base.targetExtent,
        colorFormat = base.colorFormat,
        capabilities = capabilities,
        budget = budget,
        visualCommandCount = base.visualCommandCount,
        resources = resources,
        passes = passes,
        dependencies = dependencies,
        peakFrameLocalBytes = peakFrameLocalBytes,
    )

    private fun RenderGraph.firstStrokeDraw(): org.graphiks.kanvas.gpu.plan.PathStrokeDraw =
        passes().mapNotNull { pass -> when (pass) {
            is PlanPass.RenderPass -> pass.draws().singleOrNull()
            is PlanPass.StencilProducer -> pass.draw
            else -> null
        } }.filterIsInstance<org.graphiks.kanvas.gpu.plan.PathStrokeDraw>().first()

    private fun RenderGraph.replaceStrokeDraw(
        replacement: org.graphiks.kanvas.gpu.plan.PathStrokeDraw,
    ): List<PlanPass> = passes().map { pass -> when (pass) {
        is PlanPass.RenderPass -> PlanPass.RenderPass(
            pass.ordinal,
            pass.target,
            pass.draws().map { draw -> if (draw.commandIndex == replacement.commandIndex) replacement else draw },
            pass.load,
            pass.store,
            pass.drawDataResources,
        )
        is PlanPass.StencilProducer -> if (pass.draw.commandIndex == replacement.commandIndex) {
            PlanPass.StencilProducer(
                pass.ordinal,
                pass.target,
                pass.depthStencil,
                replacement,
                pass.drawDataResources,
                pass.atomicGroup,
                pass.load,
                pass.store,
                pass.depthStencilAccess,
                pass.depthStencilLoadStore,
            )
        } else pass
        is PlanPass.StencilCover -> if (pass.draw.commandIndex == replacement.commandIndex) {
            PlanPass.StencilCover(
                pass.ordinal,
                pass.target,
                pass.depthStencil,
                replacement,
                pass.drawDataResources,
                pass.atomicGroup,
                pass.load,
                pass.store,
                pass.depthStencilAccess,
                pass.depthStencilLoadStore,
            )
        } else pass
        else -> pass
    } }

    private fun PlanResource.copyWith(
        byteSize: Long = this.byteSize,
        firstPassIndex: Int = this.firstPassIndex,
        lastPassIndexExclusive: Int = this.lastPassIndexExclusive,
    ): PlanResource = PlanResource.of(
        role,
        ordinal,
        kind,
        format,
        copyExtent(),
        byteSize,
        usages(),
        lifetime,
        firstPassIndex,
        lastPassIndexExclusive,
    )

    private fun W4dSessionScratchV1.rebuild(
        uniformStrideBytes: Long = this.uniformStrideBytes,
        vertexUsefulBytes: Long = this.vertexUsefulBytes,
    ): W4dSessionScratchV1 = W4dSessionScratchV1(
        planId,
        capabilitySealHash,
        deviceGeneration,
        target,
        staging,
        targetBounds,
        vertexResourceId,
        indexResourceId,
        uniformResourceId,
        depthStencilResourceId,
        targetBytes,
        stagingBytes,
        capabilityId,
        renderPassIds,
        readbackPassId,
        resourceLastPassIndexExclusive,
        depthStencilFirstPassIndex,
        draws,
        uniformPlan,
        uniformStrideBytes,
        vertexUsefulBytes,
        indexUsefulBytes,
        uniformUsefulBytes,
        vertexCapacityBytes,
        indexCapacityBytes,
        uniformCapacityBytes,
        depthStencilBytes,
        poolCapacities,
        maxBufferSize,
        maxDynamicUniformBuffersPerPipelineLayout,
    )

    private fun expectedCap(cap: StrokeCapNode): PathStrokeCap = when (cap) {
        StrokeCapNode.BUTT -> PathStrokeCap.Butt
        StrokeCapNode.ROUND -> PathStrokeCap.Round
        StrokeCapNode.SQUARE -> PathStrokeCap.Square
    }

    private fun expectedJoin(join: StrokeJoinNode): PathStrokeJoin = when (join) {
        StrokeJoinNode.MITER -> PathStrokeJoin.Miter
        StrokeJoinNode.ROUND -> PathStrokeJoin.Round
        StrokeJoinNode.BEVEL -> PathStrokeJoin.Bevel
    }

    private companion object {
        val COLOR: ColorARGB = ColorARGB.fromPackedUInt(0x80ff0000u)
    }
}
