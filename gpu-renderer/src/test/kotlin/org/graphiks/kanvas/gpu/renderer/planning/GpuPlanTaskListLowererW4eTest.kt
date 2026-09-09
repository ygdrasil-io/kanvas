package org.graphiks.kanvas.gpu.renderer.planning

import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.gpu.plan.GpuPlanSelection
import org.graphiks.kanvas.gpu.plan.PlanBudget
import org.graphiks.kanvas.gpu.plan.PlanBufferAllocationPolicy
import org.graphiks.kanvas.gpu.plan.PlanCapabilitySnapshot
import org.graphiks.kanvas.gpu.plan.PlanDepthStencilFormat
import org.graphiks.kanvas.gpu.plan.PlanLogicalColorFormat
import org.graphiks.kanvas.gpu.plan.PlanId
import org.graphiks.kanvas.gpu.plan.PlanOperationCapability
import org.graphiks.kanvas.gpu.plan.PlanResourceUsage
import org.graphiks.kanvas.gpu.plan.PlanResource
import org.graphiks.kanvas.gpu.plan.PlanResourceRole
import org.graphiks.kanvas.gpu.plan.PlanTextureFormat
import org.graphiks.kanvas.gpu.plan.PlanTextureResolveSupport
import org.graphiks.kanvas.gpu.plan.PlanTextureSampleSupport
import org.graphiks.kanvas.gpu.plan.PlanPass
import org.graphiks.kanvas.gpu.plan.PathDrawGeometry
import org.graphiks.kanvas.gpu.plan.BinaryMaskFetchPlan
import org.graphiks.kanvas.gpu.plan.SamplePlan
import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.plan.W4eClipPlanCompiler
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureFormatSampleSupport
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureSampleCountSupport
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.passes.GPUW4ePreparedClipConsumerAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUW4ePreparedInverseInteriorCoverage
import org.graphiks.kanvas.gpu.renderer.passes.GPUW4ePreparedClipPassAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUW4ePreparedClipGeometry
import org.graphiks.kanvas.gpu.renderer.passes.GPUSampleResolveAction
import org.graphiks.kanvas.gpu.renderer.passes.GPUW4eSceneResolveAction
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlanner
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.render.ir.BlendMode
import org.graphiks.kanvas.render.ir.BlendNode
import org.graphiks.kanvas.render.ir.ClipEntry
import org.graphiks.kanvas.render.ir.ClipOperation
import org.graphiks.kanvas.render.ir.ClipStackNode
import org.graphiks.kanvas.render.ir.ClipTransformSnapshot
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
import org.graphiks.math.geometry.PathBuilder
import org.graphiks.math.geometry.FillRule
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32

class GpuPlanTaskListLowererW4eTest {
    @Test
    fun `AA clip lowering prepares four-sample mask resolve and D24S8 together`() {
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(
            GpuPlanTaskListLowerer().lower(request(aaMaskGraph())),
        )

        val prepared = assertIs<GPUTask.PrepareResources>(lowered.taskList.tasks.first()).requests
        val clipTextures = prepared.filter { it.role in setOf(
            GPUFrameResourceRole.ClipMask,
            GPUFrameResourceRole.ClipDepthStencil,
        ) }
        assertEquals(
            listOf(1, 1, 1, 4, 4),
            clipTextures.map { assertIs<GPUFrameTextureDescriptor>(it.descriptor).sampleCount }.sorted(),
        )
        assertEquals(4, clipTextures.count { it.role == GPUFrameResourceRole.ClipMask })
        assertEquals(1, clipTextures.count { it.role == GPUFrameResourceRole.ClipDepthStencil })
    }

    @Test
    fun `prepared prefix retains exact producer fold attachments and ping pong`() {
        val graph = aaMaskGraph()
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(
            GpuPlanTaskListLowerer().lower(request(graph)),
        )
        val renders = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>()
        val producer = renders.mapNotNull { task ->
            task.drawPackets.single().w4ePreparedClipPass as? GPUW4ePreparedClipPassAuthority.Producer
        }.single()
        assertEquals(4, producer.sampleCount)
        assertTrue(producer.resolveTargetResourceId != null)
        assertTrue(producer.depthStencilResourceId != null)
        val producerTask = renders.single { it.drawPackets.single().w4ePreparedClipPass === producer }
        assertEquals(
            listOf(GPUFrameResourceRole.ClipMask, GPUFrameResourceRole.ClipMask, GPUFrameResourceRole.ClipDepthStencil),
            producerTask.resourceUses.map { it.role },
        )
        assertTrue(producerTask.resourceUses.single { use ->
            use.resource.value.substringAfterLast('.') == producer.resolveTargetResourceId
        }.write)
        val fold = renders.mapNotNull { task ->
            task.drawPackets.single().w4ePreparedClipPass as? GPUW4ePreparedClipPassAuthority.Fold
        }.single()
        assertTrue(fold.previousResourceId != fold.outputResourceId)
        assertEquals(
            listOf(fold.previousResourceId, fold.sourceResourceId, fold.outputResourceId),
            renders.single { it.drawPackets.single().w4ePreparedClipPass === fold }.resourceUses
                .map { it.resource.value.substringAfterLast('.') },
        )
        assertTrue(GPUFrameMemoryBudgetPlanner.hasExactLimitIndependentFacts(lowered.taskList.memoryBudget))
        assertTrue(lowered.taskList.memoryBudget.allocations.isNotEmpty())
        graph.resources().forEach { resource ->
            val allocation = lowered.taskList.memoryBudget.allocations.single { allocation ->
                allocation.label == "w4e.${resource.id.value}"
            }
            assertEquals(resource.firstPassIndex, allocation.firstPassIndex)
            assertEquals(resource.lastPassIndexExclusive, allocation.lastPassIndexExclusive)
        }
        assertEquals(
            graph.peakFrameLocalBytes,
            lowered.taskList.memoryBudget.peakFrameTransientBytes + lowered.taskList.memoryBudget.targetResidentBytes,
        )
    }

    @Test
    fun `lowering rejects a graph whose canonical W4e identity was forged`() {
        val graph = aaMaskGraph()
        val forged = RenderGraph.of(
            id = PlanId("forged-w4e-identity"),
            capabilityId = graph.capabilityId,
            targetExtent = graph.targetExtent,
            colorFormat = graph.colorFormat,
            capabilities = graph.capabilities,
            budget = graph.budget,
            visualCommandCount = graph.visualCommandCount,
            resources = graph.resources(),
            passes = graph.passes(),
            dependencies = graph.dependencies(),
            peakFrameLocalBytes = graph.peakFrameLocalBytes,
        )

        assertIs<GpuPlanLoweringResult.InvalidPlan>(GpuPlanTaskListLowerer().lower(request(forged)))
    }

    @Test
    fun `lowering materializes the sealed mask chain before its color consumer`() {
        val graph = aaMaskGraph()
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(GpuPlanTaskListLowerer().lower(request(graph)))

        val renders = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>()
        assertEquals(graph.passes().size - 1, renders.size)
        assertEquals(
            graph.passes().dropLast(1).map { pass -> pass.id.value },
            renders.map { render -> render.taskId.value.substringAfterLast('.') },
        )
        assertEquals(lowered.taskList.tasks.size - 1, lowered.taskList.dependencies.size)
    }

    @Test
    fun `reused sealed clip emits one preparation chain before both consumers`() {
        val graph = aaMaskGraph(drawCount = 2)
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(GpuPlanTaskListLowerer().lower(request(graph)))

        assertEquals(1, graph.passes().filterIsInstance<PlanPass.ClipMaskInitialize>().size)
        assertEquals(1, graph.passes().filterIsInstance<PlanPass.ClipMaskProducer>().size)
        assertEquals(1, graph.passes().filterIsInstance<PlanPass.ClipMaskFold>().size)
        assertEquals(graph.passes().size - 1, lowered.taskList.tasks.filterIsInstance<GPUTask.Render>().size)
    }

    @Test
    fun `two consumers retain one sealed mask strategy and its sampled resource`() {
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(
            GpuPlanTaskListLowerer().lower(request(aaMaskGraph(drawCount = 2))),
        )

        val consumers = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>().mapNotNull { render ->
            render.drawPackets.single().w4ePreparedClipConsumer as? GPUW4ePreparedClipConsumerAuthority.Mask
        }
        assertEquals(2, consumers.size)
        assertEquals(1, consumers.map { it.maskResourceId }.distinct().size)
        lowered.taskList.tasks.filterIsInstance<GPUTask.Render>().filter { render ->
            render.drawPackets.single().w4ePreparedClipConsumer is GPUW4ePreparedClipConsumerAuthority.Mask
        }.forEach { render ->
            assertTrue(render.resourceUses.any { use ->
                use.role == GPUFrameResourceRole.ClipMask &&
                    use.resource.value.substringAfterLast('.') == consumers.first().maskResourceId
            })
        }
    }

    @Test
    fun `inverse mask retains its full domain and zero interior for each reused consumer`() {
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(
            GpuPlanTaskListLowerer().lower(
                request(aaMaskGraph(drawCount = 2, inverse = true, inverseEmpty = true)),
            ),
        )

        val consumers = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>().mapNotNull { render ->
            render.drawPackets.single().w4ePreparedClipConsumer as? GPUW4ePreparedClipConsumerAuthority.InverseMask
        }
        assertEquals(2, consumers.size, "each visual draw must retain its sealed inverse-mask consumer")
        assertEquals(1, consumers.map { it.maskResourceId }.distinct().size)
        consumers.forEach { consumer ->
            assertEquals(0, consumer.domain.left)
            assertEquals(0, consumer.domain.top)
            assertEquals(16, consumer.domain.right)
            assertEquals(16, consumer.domain.bottom)
            assertIs<GPUW4ePreparedInverseInteriorCoverage.Zero>(consumer.interiorCoverage)
        }
    }

    @Test
    fun `inverting a sealed mask changes the prepared consumer while retaining finite interior`() {
        val mask = assertIs<GpuPlanLoweringResult.Lowered>(
            GpuPlanTaskListLowerer().lower(request(aaMaskGraph())),
        ).taskList.tasks.filterIsInstance<GPUTask.Render>().first { render ->
            render.drawPackets.single().w4ePreparedClipConsumer is GPUW4ePreparedClipConsumerAuthority.Mask
        }
        val inverse = assertIs<GpuPlanLoweringResult.Lowered>(
            GpuPlanTaskListLowerer().lower(request(aaMaskGraph(inverse = true))),
        ).taskList.tasks.filterIsInstance<GPUTask.Render>().first { render ->
            render.drawPackets.single().w4ePreparedClipConsumer is GPUW4ePreparedClipConsumerAuthority.InverseMask
        }

        assertIs<GPUW4ePreparedClipConsumerAuthority.Mask>(
            mask.drawPackets.single().w4ePreparedClipConsumer,
        )
        val inverseConsumer = assertIs<GPUW4ePreparedClipConsumerAuthority.InverseMask>(
            inverse.drawPackets.single().w4ePreparedClipConsumer,
        )
        val interior = assertIs<GPUW4ePreparedInverseInteriorCoverage.Geometry>(
            inverseConsumer.interiorCoverage,
        )
        assertEquals(16, inverseConsumer.domain.right)
        assertEquals(1, inverse.resourceUses.count { it.role == GPUFrameResourceRole.ClipMask })
        assertTrue(inverse.resourceUses.any { it.role == GPUFrameResourceRole.VertexData })
        assertTrue(inverse.resourceUses.any { it.role == GPUFrameResourceRole.IndexData })
        assertTrue(inverse.resourceUses.any { it.role == GPUFrameResourceRole.UniformData })
        assertTrue(interior.copyGeometryF32().emittedNonZeroClosedEdgeCountI32 > 0)
    }

    @Test
    fun `inverse zero restores the exact nonempty source without a sampled mask or D24`() {
        val graph = aaMaskGraph(inverse = true, zeroClip = true, inverseZeroSource = true)
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(GpuPlanTaskListLowerer().lower(request(graph)))
        val expectedPath = PathBuilder(FillRule.INVERSE_WINDING)
            .moveTo(2f, 2f).lineTo(12f, 2f).close().build()
        val expectedTransform = Matrix3x3F32.rotation(0.25f)

        val renders = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>().filter { task ->
            task.drawPackets.single().w4ePreparedClipConsumer is GPUW4ePreparedClipConsumerAuthority.InverseDomain
        }
        assertEquals(1, renders.size)
        renders.forEach { render ->
            val consumer = assertIs<GPUW4ePreparedClipConsumerAuthority.InverseDomain>(
                render.drawPackets.single().w4ePreparedClipConsumer,
            )
            assertEquals(16, consumer.domain.right)
            assertIs<GPUW4ePreparedInverseInteriorCoverage.Zero>(consumer.interiorCoverage)
            assertEquals(0, render.resourceUses.count { it.role == GPUFrameResourceRole.ClipMask })
            val path = requireNotNull(render.drawPackets.single().w4ePreparedPath)
            val source = assertIs<PathDrawGeometry.InverseDomainSource>(path.copyGeometry())
            assertEquals(expectedPath, source.copySourcePath(), "the original path segments are sealed, not the W4d proxy")
            assertEquals(expectedPath.toList(), source.copySourcePath().toList(), "source vertices remain bit-for-bit path facts")
            assertEquals(expectedTransform, source.copySourceTransform(), "the original transform remains sealed")
            assertEquals(listOf(2f, 2f, 12f, 2f), source.copySourcePath().flatMap { segment -> when (segment) {
                is org.graphiks.math.geometry.PathSegmentF32.MoveTo -> listOf(segment.point.x, segment.point.y)
                is org.graphiks.math.geometry.PathSegmentF32.LineTo -> listOf(segment.point.x, segment.point.y)
                else -> emptyList()
            } }, "the source bounds are derived from its original vertices")
            assertEquals(null, path.depthStencilResourceId)
            assertTrue(render.resourceUses.none { it.role == GPUFrameResourceRole.PathDepthStencil })
        }
        assertTrue(graph.verifyW4eCompilerWitness(), "the exact source facts participate in the canonical W4e seal")
    }

    @Test
    fun `inverse zero with an actually empty source remains a D24-free domain cover`() {
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(
            GpuPlanTaskListLowerer().lower(
                request(aaMaskGraph(inverse = true, zeroClip = true, inverseEmpty = true)),
            ),
        )

        val render = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>().single { task ->
            task.drawPackets.single().w4ePreparedClipConsumer is GPUW4ePreparedClipConsumerAuthority.InverseDomain
        }
        val consumer = assertIs<GPUW4ePreparedClipConsumerAuthority.InverseDomain>(
            render.drawPackets.single().w4ePreparedClipConsumer,
        )
        assertIs<GPUW4ePreparedInverseInteriorCoverage.Zero>(consumer.interiorCoverage)
        val path = requireNotNull(render.drawPackets.single().w4ePreparedPath)
        assertIs<PathDrawGeometry.Empty>(path.copyGeometry())
        assertEquals(null, path.depthStencilResourceId)
        assertTrue(render.resourceUses.none { it.role == GPUFrameResourceRole.PathDepthStencil })
    }

    @Test
    fun `prepared W4e geometry snapshots resist mutations through returned data`() {
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(
            GpuPlanTaskListLowerer().lower(request(aaMaskGraph(inverse = true))),
        )
        val renders = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>()
        val path = requireNotNull(renders.mapNotNull { it.drawPackets.single().w4ePreparedPath }.firstOrNull())
        val producer = assertIs<GPUW4ePreparedClipPassAuthority.Producer>(
            renders.mapNotNull { it.drawPackets.single().w4ePreparedClipPass }
                .filterIsInstance<GPUW4ePreparedClipPassAuthority.Producer>()
                .single(),
        )
        val inverse = renders.mapNotNull { render ->
            render.drawPackets.single().w4ePreparedClipConsumer as? GPUW4ePreparedClipConsumerAuthority.InverseMask
        }.first()

        fun mutateAndReread(copy: () -> org.graphiks.math.geometry.PathFillGeometryF32) {
            val first = copy()
            val mutableVertices = first.copyDirectTriangleF32OrNull()?.copyVerticesF32()
                ?: first.copyStencilEdgeFanF32OrNull()!!.copyVerticesF32()
            val retainedFirstVertex = mutableVertices[0]
            mutableVertices[0] = retainedFirstVertex + 100f
            val reread = copy().copyDirectTriangleF32OrNull()?.copyVerticesF32()
                ?: copy().copyStencilEdgeFanF32OrNull()!!.copyVerticesF32()
            assertEquals(retainedFirstVertex, reread[0])
        }

        mutateAndReread {
            assertIs<PathDrawGeometry.Fill>(path.copyGeometry()).valueF32
        }
        mutateAndReread {
            assertIs<GPUW4ePreparedClipGeometry.Path>(producer.geometry).copyPathGeometryF32()
        }
        mutateAndReread {
            assertIs<GPUW4ePreparedInverseInteriorCoverage.Geometry>(inverse.interiorCoverage).copyGeometryF32()
        }
        val changedDomain = inverse.domain.copy(right = inverse.domain.right - 1)
        assertEquals(16, inverse.domain.right)
        assertEquals(15, changedDomain.right)
    }

    @Test
    fun `prepared path carries the sealed command and W4e MSAA continuation into the frame planner`() {
        val graph = aaMaskGraph(drawCount = 2)
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(GpuPlanTaskListLowerer().lower(request(graph)))
        val sealedByPass = graph.passes().filterIsInstance<PlanPass.PathRenderPass>().associateBy { it.id.value }
        val prepared = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>().mapNotNull { render ->
            render.drawPackets.single().w4ePreparedPath?.let { path -> render to path }
        }

        assertEquals(sealedByPass.size, prepared.size)
        prepared.forEach { (render, path) ->
            val source = sealedByPass.getValue(path.passId)
            assertEquals(source.draw.commandIndex, path.commandIdValue)
            assertEquals(source.phase, path.phase)
            assertEquals(source.draw.color, path.color)
            assertEquals(source.draw.strategy, path.fillStrategy)
            assertEquals(source.draw.coverage, path.coverage)
            assertEquals(source.draw.blend, path.blend)
            assertEquals(source.draw.copyScissorI32().right, path.scissor.right)
            if (path.sample == SamplePlan.Multisample4) {
                assertEquals(null, render.sampleContinuationKey)
                assertTrue(render.w4eSceneContinuation != null)
            }
        }
        assertFalse(GPUFramePlanner.plan(lowered.taskList).atomicallyRefused)
    }

    @Test
    fun `hard binary cover retains its one-to-four mask contract and exact depth allocation role`() {
        val hardGraph = aaMaskGraph(coverage = CoverageRequest.HARD_EDGE, concave = true)
        val hardResult = GpuPlanTaskListLowerer().lower(
            request(hardGraph),
        )
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(hardResult)
        val hard = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>().single { render ->
            render.drawPackets.single().w4ePreparedPath?.binarySourceMaskResourceId != null
        }
        val path = requireNotNull(hard.drawPackets.single().w4ePreparedPath)
        assertEquals(BinaryMaskFetchPlan.TextureLoadUnfiltered, path.binaryMaskFetch)
        assertEquals(4, path.binaryBroadcastSampleCount)
        assertEquals(null, hard.sampleContinuationKey)
        assertTrue(hard.w4eSceneContinuation != null)
        assertTrue(hard.resourceUses.any { use ->
            use.role == GPUFrameResourceRole.ClipMask &&
                use.resource.value.substringAfterLast('.') == path.binarySourceMaskResourceId
        })
        val hardProducer = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>().first { render ->
            render.drawPackets.single().w4ePreparedPath?.depthStencilResourceId != null
        }
        assertTrue(hardProducer.resourceUses.any { it.role == GPUFrameResourceRole.PathDepthStencil })
        assertTrue(hardProducer.resourceUses.any { it.role == GPUFrameResourceRole.VertexData })
        assertTrue(hardProducer.resourceUses.any { it.role == GPUFrameResourceRole.IndexData })
        assertTrue(hardProducer.resourceUses.any { it.role == GPUFrameResourceRole.UniformData })
        val depth = assertIs<GPUFrameTextureDescriptor>(
            lowered.taskList.tasks.filterIsInstance<GPUTask.PrepareResources>().single().requests
                .single { request ->
                    request.role == GPUFrameResourceRole.PathDepthStencil &&
                        (request.descriptor as GPUFrameTextureDescriptor).sampleCount == 1
                }.descriptor,
        )
        assertEquals(1, depth.sampleCount)
        val allocations = lowered.taskList.memoryBudget.allocations.associateBy { it.label.removePrefix("w4e.") }
        hardGraph.resources().filter { resource -> resource.format is PlanTextureFormat.DepthStencil }.forEach { resource ->
            assertEquals(
                if (resource.sampleCountI32 == 4) {
                    GPUFrameMemoryCategory.FrameLocalMsaaDepthStencil
                } else {
                    GPUFrameMemoryCategory.ReusableScratch
                },
                allocations.getValue(resource.id.value).category,
            )
        }
        assertTrue(lowered.taskList.dependencies.any { it.atomicGroupId != null })
    }

    @Test
    fun `accepted W4e graph refuses an insufficient current memory budget without partial tasks`() {
        val graph = aaMaskGraph()

        val refused = GpuPlanTaskListLowerer().lower(
            request(graph).copy(rendererAggregateMemoryBudgetBytes = 1L),
        )

        assertIs<GpuPlanLoweringResult.InvalidPlan>(refused)
    }

    @Test
    fun `sealed W4e resource contracts reject format usage sample and lifetime substitutions`() {
        val graph = aaMaskGraph()
        val mask = graph.resources().first { it.role == PlanResourceRole.CoverageMaskAccumulator }
        val aaScratch = graph.resources().first { it.role == PlanResourceRole.CoverageMaskMultisampleScratch }

        fun forgeGraph(resource: PlanResource): RenderGraph = RenderGraph.of(
            id = PlanId("forged-w4e-resource-contract"),
            capabilityId = graph.capabilityId,
            targetExtent = graph.targetExtent,
            colorFormat = graph.colorFormat,
            capabilities = graph.capabilities,
            budget = graph.budget,
            visualCommandCount = graph.visualCommandCount,
            resources = graph.resources().map { current -> if (current.id == resource.id) resource else current },
            passes = graph.passes(),
            dependencies = graph.dependencies(),
            peakFrameLocalBytes = graph.peakFrameLocalBytes,
        )

        val wrongFormat = PlanResource.of(
            mask.role, mask.ordinal, mask.kind, PlanTextureFormat.Color(graph.colorFormat),
            mask.copyExtent(), mask.byteSize, mask.usages(), mask.lifetime,
            mask.firstPassIndex, mask.lastPassIndexExclusive, mask.sampleCountI32,
        )
        assertFailsWith<IllegalArgumentException> { forgeGraph(wrongFormat) }

        listOf(
            {
                PlanResource.of(
                    mask.role, mask.ordinal, mask.kind, mask.format, mask.copyExtent(), mask.byteSize,
                    setOf(PlanResourceUsage.RenderAttachment), mask.lifetime,
                    mask.firstPassIndex, mask.lastPassIndexExclusive, mask.sampleCountI32,
                )
            },
            {
                PlanResource.of(
                    aaScratch.role, aaScratch.ordinal, aaScratch.kind, aaScratch.format, aaScratch.copyExtent(),
                    aaScratch.byteSize / 4L, aaScratch.usages(), aaScratch.lifetime,
                    aaScratch.firstPassIndex, aaScratch.lastPassIndexExclusive, 1,
                )
            },
            {
                PlanResource.of(
                    mask.role, mask.ordinal, mask.kind, mask.format, mask.copyExtent(), mask.byteSize,
                    mask.usages(), mask.lifetime, mask.firstPassIndex, mask.firstPassIndex, mask.sampleCountI32,
                )
            },
        ).forEach { forge -> assertFailsWith<IllegalArgumentException> { forge() } }
    }

    @Test
    fun `mixed AA and hard W4e color passes retain the sealed scene target until the final resolve`() {
        val scene = SceneSnapshot.of(
            SceneExtent(16, 16), ColorSpace.SRGB,
            listOf(
                pathDraw(coverage = CoverageRequest.ANTIALIASED),
                pathDraw(coverage = CoverageRequest.HARD_EDGE, concave = true),
            ),
        )
        val compiler = W4eClipPlanCompiler()
        val candidate = assertIs<GpuPlanSelection.Candidate>(
            compiler.select(scene, RenderTargetDescriptor(scene.extent, scene.colorSpace)),
        ).candidate
        val graph = assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(candidate, planCapabilities(), PlanBudget(1L shl 20)),
        ).plan
        val lowered = assertIs<GpuPlanLoweringResult.Lowered>(GpuPlanTaskListLowerer().lower(request(graph)))
        val colorTasks = lowered.taskList.tasks.filterIsInstance<GPUTask.Render>().filter { render ->
            render.drawPackets.single().w4ePreparedPath?.sample == SamplePlan.Multisample4
        }
        assertTrue(colorTasks.any { task ->
            task.drawPackets.single().w4ePreparedPath?.depthStencilResourceId != null
        })
        assertTrue(colorTasks.any { task ->
            task.drawPackets.single().w4ePreparedPath?.phase == org.graphiks.kanvas.gpu.plan.PathRenderPhase.HardEdgeBinaryColorCover &&
                task.drawPackets.single().w4ePreparedPath?.depthStencilResourceId == null
        })
        assertTrue(colorTasks.all { task -> task.sampleContinuationKey == null })
        assertEquals(1, colorTasks.mapNotNull(GPUTask.Render::w4eSceneContinuation).map {
            it.sceneTargetResourceId
        }.distinct().size)
        colorTasks.forEach { task ->
            val continuation = requireNotNull(task.w4eSceneContinuation)
            assertTrue(task.resourceUses.any { use ->
                use.role == GPUFrameResourceRole.LayerTarget && use.write &&
                    use.resource.value.substringAfterLast('.') == continuation.sceneTargetResourceId
            }, "each AA4 scene pass must retain its sealed multisample color attachment")
        }
        val sceneMsaa = lowered.taskList.tasks.filterIsInstance<GPUTask.PrepareResources>().single().requests.single { request ->
            request.role == GPUFrameResourceRole.LayerTarget &&
                (request.descriptor as? GPUFrameTextureDescriptor)?.sampleCount == 4
        }
        assertEquals(4, assertIs<GPUFrameTextureDescriptor>(sceneMsaa.descriptor).sampleCount)
        assertEquals(
            1,
            lowered.taskList.tasks.filterIsInstance<GPUTask.PrepareResources>().single().requests.count {
                it.role == GPUFrameResourceRole.SceneTarget
            },
            "only the logical canonical target is a SceneTarget",
        )
        lowered.taskList.tasks.filterIsInstance<GPUTask.Render>().filter { task ->
            task.drawPackets.single().w4ePreparedPath?.phase?.name?.startsWith("HardEdgeMask") == true
        }.forEach { task ->
            val path = requireNotNull(task.drawPackets.single().w4ePreparedPath)
            assertTrue(task.resourceUses.any { use ->
                use.resource.value.substringAfterLast('.') == path.targetResourceId &&
                    use.role == GPUFrameResourceRole.ClipMask && use.write
            }, "hard-edge mask targets retain their ClipMask role")
        }

        val frame = GPUFramePlanner.plan(lowered.taskList)
        assertFalse(frame.atomicallyRefused)
        val colorSteps = frame.steps.filterIsInstance<GPUFrameStep.RenderPassStep>().filter { step ->
            step.drawPackets.any { it.w4ePreparedPath?.sample == SamplePlan.Multisample4 }
        }
        assertEquals(1, colorSteps.count { it.w4eSceneContinuation?.resolveAction == GPUW4eSceneResolveAction.ResolveCanonical })
        assertTrue(colorSteps.dropLast(1).all { it.w4eSceneContinuation?.resolveAction == GPUW4eSceneResolveAction.Skip })
        assertTrue(colorSteps.last().drawPackets.any { it.w4ePreparedPath?.resolveTargetResourceId != null })
    }

    private fun aaMaskGraph(
        drawCount: Int = 1,
        inverse: Boolean = false,
        zeroClip: Boolean = false,
        inverseEmpty: Boolean = false,
        coverage: CoverageRequest = CoverageRequest.ANTIALIASED,
        concave: Boolean = false,
        inverseZeroSource: Boolean = false,
    ): RenderGraph {
        val scene = SceneSnapshot.of(
            SceneExtent(16, 16),
            ColorSpace.SRGB,
            List(drawCount) { pathDraw(inverse, zeroClip, inverseEmpty, coverage, concave, inverseZeroSource) },
        )
        val compiler = W4eClipPlanCompiler()
        val candidate = assertIs<GpuPlanSelection.Candidate>(
            compiler.select(scene, RenderTargetDescriptor(scene.extent, scene.colorSpace)),
        ).candidate
        return assertIs<RenderPlanResult.Ready<RenderGraph>>(
            compiler.plan(candidate, planCapabilities(), PlanBudget(1L shl 20)),
        ).plan
    }

    private fun pathDraw(
        inverse: Boolean = false,
        zeroClip: Boolean = false,
        inverseEmpty: Boolean = false,
        coverage: CoverageRequest = CoverageRequest.ANTIALIASED,
        concave: Boolean = false,
        inverseZeroSource: Boolean = false,
    ): SceneCommand.Draw {
        val color = ColorARGB.fromPackedUInt(0xC0FF0000u)
        val fillRule = if (inverse) FillRule.INVERSE_WINDING else FillRule.WINDING
        val clipPath = if (zeroClip) {
            PathBuilder().build()
        } else {
            PathBuilder().moveTo(3f, 3f).lineTo(13f, 3f).lineTo(3f, 13f).close().build()
        }
        return SceneCommand.Draw(
            DrawNode(
                geometry = GeometryNode.Path(
                    if (inverseEmpty) PathBuilder(fillRule).build() else if (inverseZeroSource) PathBuilder(fillRule)
                        .moveTo(2f, 2f).lineTo(12f, 2f).close().build()
                    else if (concave) PathBuilder(fillRule)
                        .moveTo(2f, 2f).lineTo(12f, 2f).lineTo(12f, 12f).lineTo(7f, 6f).lineTo(2f, 12f).close().build()
                    else PathBuilder(fillRule).moveTo(2f, 2f).lineTo(12f, 2f).lineTo(2f, 12f).close().build(),
                ),
                material = MaterialNode.Solid(color),
                coverage = coverage,
                clip = ClipStackNode.Operations.of(listOf(
                    ClipEntry(
                        geometry = GeometryNode.Path(
                            clipPath,
                        ),
                        operation = if (zeroClip) ClipOperation.DIFFERENCE else ClipOperation.INTERSECT,
                        antiAlias = true,
                        transform = ClipTransformSnapshot.Known.of(Matrix3x3F32.rotation(0.1f)),
                    ),
                )),
                blend = BlendNode.SrcOver,
                effects = EffectStack.Empty,
                transform = Matrix3x3F32.rotation(0.25f),
                origin = DrawOrigin.PATH,
                paint = PaintNode(
                    color, null, BlendMode.SRC_OVER, null, null, null, null, null,
                    PaintStyleNode.FILL, 0f, StrokeCapNode.BUTT, StrokeJoinNode.MITER, 4f, true,
                ),
            ),
        )
    }

    private fun request(graph: RenderGraph): GpuPlanLoweringRequest = GpuPlanLoweringRequest(
        graph = graph,
        capabilities = rendererCapabilities(),
        deviceGeneration = GPUDeviceGenerationID(7),
        currentBudget = graph.budget,
        frameId = GPUFrameID(8),
        recordingId = GPURecordingID("w4e-red"),
    )

    private fun planCapabilities(): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
        deviceGeneration = 7,
        maxTextureDimension2D = 2048,
        maxBufferSizeBytes = 1L shl 20,
        copyBytesPerRowAlignment = 256,
        supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
        minUniformBufferOffsetAlignment = 256,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
        supportedOperations = PlanOperationCapability.entries.toSet(),
        bufferAllocationPolicy = PlanBufferAllocationPolicy.of(16_384L, 4_096L, 4_096L),
        supportedDepthStencilFormats = setOf(PlanDepthStencilFormat.Depth24PlusStencil8),
        supportedTextureSampleSupports = setOf(
            PlanTextureSampleSupport.of(
                PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
                1,
                setOf(
                    PlanResourceUsage.RenderAttachment,
                    PlanResourceUsage.CopySource,
                    PlanResourceUsage.Sampled,
                ),
            ),
            PlanTextureSampleSupport.of(PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), 4, setOf(PlanResourceUsage.RenderAttachment)),
            PlanTextureSampleSupport.of(PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8), 1, setOf(PlanResourceUsage.DepthStencilAttachment)),
            PlanTextureSampleSupport.of(PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8), 4, setOf(PlanResourceUsage.DepthStencilAttachment)),
            PlanTextureSampleSupport.of(PlanTextureFormat.CoverageMask, 1, setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled)),
            PlanTextureSampleSupport.of(PlanTextureFormat.CoverageMask, 4, setOf(PlanResourceUsage.RenderAttachment)),
        ),
        supportedTextureResolveSupports = setOf(
            PlanTextureResolveSupport.of(PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), 4, 1),
            PlanTextureResolveSupport.of(PlanTextureFormat.CoverageMask, 4, 1),
        ),
    )

    private fun rendererCapabilities(maxBufferSize: Long = 1L shl 20): GPUCapabilities = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "test", "w4e", "device"),
        facts = emptyList(),
        snapshotId = "w4e-test",
        limits = GPULimits(
            maxTextureDimension2D = 2048,
            copyBytesPerRowAlignment = 256,
            minUniformBufferOffsetAlignment = 256,
            maxBufferSize = maxBufferSize,
            maxDynamicUniformBuffersPerPipelineLayout = 1,
        ),
        supportedTextureFormats = setOf(
            GPUTextureFormat.RGBA8UnormSrgb,
            GPUTextureFormat.RGBA8Unorm,
            GPUTextureFormat.Depth24PlusStencil8,
        ),
        supportedTextureUsage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding or GPUTextureUsage.CopySrc,
        textureFormatSampleSupport = GPUTextureFormatSampleSupport(mapOf(
            GPUTextureFormat.RGBA8UnormSrgb to GPUTextureSampleCountSupport(setOf(1, 4), setOf(4)),
            GPUTextureFormat.RGBA8Unorm to GPUTextureSampleCountSupport(setOf(1, 4), setOf(4)),
            GPUTextureFormat.Depth24PlusStencil8 to GPUTextureSampleCountSupport(setOf(1, 4)),
        )),
        rendererFeatures = setOf(
            GPURendererFeature.RenderPass,
            GPURendererFeature.CopyUpload,
            GPURendererFeature.UniformBuffer,
            GPURendererFeature.Readback,
        ),
    )
}
