package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.color.ColorF32
import org.graphiks.math.geometry.FillRule
import org.graphiks.math.geometry.PathFillGeometryF32
import org.graphiks.math.geometry.PathFillInputF64
import org.graphiks.math.geometry.PathFillPreparationResult
import org.graphiks.math.geometry.PathFillSegmentF64
import org.graphiks.math.geometry.Point2F64
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.geometry.preparePathFillGeometryF32
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class RenderGraphContractTest {
    @Test
    fun `analytic rect draw owns exact defensive math geometry`() {
        val exact = RectF32(0.25f, 0.5f, 2.75f, 2.25f)
        val raster = RectI32(0, 0, 3, 3)
        val draw = AnalyticRectDraw.of(
            0, ColorF32.of(1f, 0f, 0f, 1f), exact, raster, RectI32(1, 0, 3, 3),
        )
        exact.left = 99f
        raster.left = 99

        assertEquals(RectF32(0.25f, 0.5f, 2.75f, 2.25f), draw.copyDeviceBounds())
        assertEquals(RectI32(0, 0, 3, 3), draw.copyRasterBounds())
        assertEquals(CoveragePlan.AnalyticScalarAA, draw.coverage)
        assertEquals(SamplePlan.SingleSample, draw.sample)
        assertEquals(BlendPlan.SrcOver, draw.blend)
    }

    @Test
    fun `render pass draw resources participate in lifetime validation`() {
        assertFailsWith<IllegalArgumentException> {
            graphWithAnalyticDrawResources(uniformLifetime = 1 until 2)
        }
    }

    @Test
    fun `rectangles and resource collections are defensive snapshots`() {
        val source = RectI32(1, 2, 5, 7)
        val draw = SolidRectDraw.of(0, ColorF32.of(0.25f, 0f, 0f, 0.5f), source, source)
        source.left = 99

        assertEquals(RectI32(1, 2, 5, 7), draw.copyVisibleBounds())
        val leaked = draw.copyVisibleBounds()
        leaked.left = 88
        assertEquals(RectI32(1, 2, 5, 7), draw.copyVisibleBounds())

        val resources = mutableListOf(targetResource())
        val graph = validGraph(
            resources = resources,
            passes = listOf(
                PlanPass.RenderPass(0, targetResource().id, emptyList(), AttachmentLoadPlan.ClearTransparent, AttachmentStorePlan.Store),
                PlanPass.RenderPass(1, targetResource().id, emptyList(), AttachmentLoadPlan.Load, AttachmentStorePlan.Store),
            ),
        )
        resources.clear()
        assertEquals(1, graph.resources().size)
        assertFailsWith<UnsupportedOperationException> {
            (graph.resources() as MutableList<PlanResource>).clear()
        }
    }

    @Test
    fun `graph rejects duplicate identities and dangling pass resources`() {
        assertFailsWith<IllegalArgumentException> {
            validGraph(resources = listOf(targetResource(), targetResource()))
        }
        assertFailsWith<IllegalArgumentException> {
            validGraph(passes = listOf(PlanPass.ReadbackPass(1, PlanResourceId("missing"), stagingResource().id, 256)))
        }
    }

    @Test
    fun `capability formats and graph planning inputs are immutable snapshots`() {
        val formats = mutableSetOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL)
        val capabilities = supportedCapabilities(formats)
        val graph = validGraph(capabilities = capabilities, budget = PlanBudget(4096))
        formats.clear()

        assertEquals(setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), graph.capabilities.supportedFormats())
        assertFailsWith<UnsupportedOperationException> {
            (graph.capabilities.supportedFormats() as MutableSet<PlanLogicalColorFormat>).clear()
        }
        assertEquals(4096, graph.budget.maxFrameLocalBytes)
    }

    @Test
    fun `capability snapshot accepts an immutable empty format set`() {
        val suppliedFormats = mutableSetOf<PlanLogicalColorFormat>()
        val capabilities = supportedCapabilities(suppliedFormats)
        suppliedFormats += PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL

        assertEquals(emptySet(), capabilities.supportedFormats())
        assertFailsWith<UnsupportedOperationException> {
            (capabilities.supportedFormats() as MutableSet<PlanLogicalColorFormat>).clear()
        }
    }

    @Test
    fun `graph target extent is a defensive snapshot`() {
        val source = SizeI32(1, 1)
        val graph = validGraph(targetExtent = source)

        assertEquals(SizeI32(1, 1), graph.targetExtent)
        val leaked = graph.targetExtent
        assertNotSame(leaked, graph.targetExtent)
        assertEquals(SizeI32(1, 1), graph.targetExtent)
    }

    @Test
    fun `blank plan resource and pass identities are rejected`() {
        assertFailsWith<IllegalArgumentException> { PlanId("") }
        assertFailsWith<IllegalArgumentException> { PlanResourceId(" ") }
        assertFailsWith<IllegalArgumentException> { PlanPassId("\t") }
    }

    @Test
    fun `same role ordinals derive stable distinct resource and pass identities`() {
        val first = PlanResource.of(PlanResourceRole.LogicalTarget, 0, PlanResourceKind.Texture2D,
            PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), SizeI32(1, 1), 4,
            setOf(PlanResourceUsage.RenderAttachment), PlanResourceLifetime.FrameLocal, 0, 1)
        val second = PlanResource.of(PlanResourceRole.LogicalTarget, 1, PlanResourceKind.Texture2D,
            PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), SizeI32(1, 1), 4,
            setOf(PlanResourceUsage.RenderAttachment), PlanResourceLifetime.FrameLocal, 0, 1)
        assertEquals(first.id, PlanResource.of(PlanResourceRole.LogicalTarget, 0, PlanResourceKind.Texture2D,
            PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), SizeI32(1, 1), 4,
            setOf(PlanResourceUsage.RenderAttachment), PlanResourceLifetime.FrameLocal, 0, 1).id)
        assert(first.id != second.id)
        assert(PlanPass.RenderPass(0, first.id, emptyList(), AttachmentLoadPlan.ClearTransparent, AttachmentStorePlan.Store).id !=
            PlanPass.RenderPass(1, first.id, emptyList(), AttachmentLoadPlan.ClearTransparent, AttachmentStorePlan.Store).id)
    }

    @Test
    fun `graph rejects readback resources and layout that cannot produce target pixels`() {
        val mismatchedExtentTarget = targetResource(extent = SizeI32(2, 1), byteSize = 8)
        val extentStaging = stagingResource()
        assertFailsWith<IllegalArgumentException> {
            validGraph(
                resources = listOf(mismatchedExtentTarget, extentStaging),
                passes = renderAndReadback(mismatchedExtentTarget, extentStaging),
                peakFrameLocalBytes = 264,
            )
        }

        val bufferSource = PlanResource.of(
            PlanResourceRole.LogicalTarget, 0, PlanResourceKind.Buffer, null, null, 256,
            setOf(PlanResourceUsage.CopySource), PlanResourceLifetime.FrameLocal, 0, 2,
        )
        val staging = stagingResource()
        assertFailsWith<IllegalArgumentException> {
            validGraph(resources = listOf(bufferSource, staging), passes = renderAndReadback(bufferSource, staging), peakFrameLocalBytes = 512)
        }

        val source = targetResource()
        val textureStaging = PlanResource.of(
            PlanResourceRole.ReadbackStaging, 0, PlanResourceKind.Texture2D,
            PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), SizeI32(1, 1), 4,
            setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.MapRead), PlanResourceLifetime.FrameLocal, 1, 2,
        )
        assertFailsWith<IllegalArgumentException> {
            validGraph(resources = listOf(source, textureStaging), passes = renderAndReadback(source, textureStaging), peakFrameLocalBytes = 8)
        }

        val sourceWithoutCopy = targetResource(usages = setOf(PlanResourceUsage.RenderAttachment))
        val validStaging = stagingResource()
        assertFailsWith<IllegalArgumentException> {
            validGraph(resources = listOf(sourceWithoutCopy, validStaging), passes = renderAndReadback(sourceWithoutCopy, validStaging), peakFrameLocalBytes = 260)
        }

        val copiedSource = targetResource()
        val stagingWithoutMapRead = stagingResource(usages = setOf(PlanResourceUsage.CopyDestination))
        assertFailsWith<IllegalArgumentException> {
            validGraph(resources = listOf(copiedSource, stagingWithoutMapRead), passes = renderAndReadback(copiedSource, stagingWithoutMapRead), peakFrameLocalBytes = 260)
        }

        val narrowCapabilities = supportedCapabilities(
            setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
            copyBytesPerRowAlignment = 1,
        )
        val narrowTarget = targetResource(extent = SizeI32(1, 1), byteSize = 4)
        val narrowStaging = stagingResource(byteSize = 3)
        assertFailsWith<IllegalArgumentException> {
            validGraph(
                capabilities = narrowCapabilities,
                resources = listOf(narrowTarget, narrowStaging),
                passes = renderAndReadback(narrowTarget, narrowStaging, bytesPerRow = 3),
                peakFrameLocalBytes = 7,
            )
        }

        val twoRowTarget = targetResource(extent = SizeI32(1, 2), byteSize = 8)
        val undersizedStaging = stagingResource(byteSize = 7)
        assertFailsWith<IllegalArgumentException> {
            validGraph(
                capabilities = narrowCapabilities,
                targetExtent = SizeI32(1, 2),
                resources = listOf(twoRowTarget, undersizedStaging),
                passes = renderAndReadback(twoRowTarget, undersizedStaging, bytesPerRow = 4),
                peakFrameLocalBytes = 15,
            )
        }
    }

    @Test
    fun `graph rejects a readback whose target lifetime has expired`() {
        val expiredTarget = targetResource(firstPassIndex = 0, lastPassIndexExclusive = 1)
        val staging = stagingResource()

        assertFailsWith<IllegalArgumentException> {
            validGraph(
                resources = listOf(expiredTarget, staging),
                passes = renderAndReadback(expiredTarget, staging),
                peakFrameLocalBytes = 256,
            )
        }
    }

    @Test
    fun `graph rejects render targets that are not render-attachment textures`() {
        val bufferTarget = PlanResource.of(
            PlanResourceRole.LogicalTarget, 0, PlanResourceKind.Buffer, null, null, 4,
            setOf(PlanResourceUsage.CopySource), PlanResourceLifetime.FrameLocal, 0, 1,
        )
        assertFailsWith<IllegalArgumentException> {
            validGraph(
                resources = listOf(bufferTarget),
                passes = listOf(renderPass(bufferTarget)),
                peakFrameLocalBytes = 4,
            )
        }

        val textureWithoutAttachment = targetResource(
            usages = setOf(PlanResourceUsage.CopySource),
            lastPassIndexExclusive = 1,
        )
        assertFailsWith<IllegalArgumentException> {
            validGraph(
                resources = listOf(textureWithoutAttachment),
                passes = listOf(renderPass(textureWithoutAttachment)),
                peakFrameLocalBytes = 4,
            )
        }
    }

    @Test
    fun `texture resources reject under-allocation and overflow`() {
        assertFailsWith<IllegalArgumentException> {
            targetResource(extent = SizeI32(2, 2), byteSize = 15)
        }
        assertFailsWith<IllegalArgumentException> {
            targetResource(extent = SizeI32(Int.MAX_VALUE, Int.MAX_VALUE), byteSize = Long.MAX_VALUE)
        }
    }

    @Test
    fun `texture formats and capabilities distinguish color from depth stencil`() {
        val color = PlanResource.of(
            PlanResourceRole.LogicalTarget,
            0,
            PlanResourceKind.Texture2D,
            PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
            SizeI32(4, 4),
            64,
            setOf(PlanResourceUsage.RenderAttachment),
            PlanResourceLifetime.FrameLocal,
            0,
            1,
        )
        val depthStencil = PlanResource.of(
            PlanResourceRole.DepthStencil,
            0,
            PlanResourceKind.Texture2D,
            PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8),
            SizeI32(4, 4),
            64,
            setOf(PlanResourceUsage.DepthStencilAttachment),
            PlanResourceLifetime.FrameLocal,
            0,
            1,
        )

        assertEquals(
            PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
            color.format,
        )
        assertEquals(
            PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8),
            depthStencil.format,
        )
        assertFailsWith<IllegalArgumentException> {
            PlanResource.of(
                PlanResourceRole.DepthStencil,
                0,
                PlanResourceKind.Texture2D,
                PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8),
                SizeI32(4, 4),
                64,
                setOf(PlanResourceUsage.RenderAttachment),
                PlanResourceLifetime.FrameLocal,
                0,
                1,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PlanResource.of(
                PlanResourceRole.LogicalTarget,
                0,
                PlanResourceKind.Texture2D,
                PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8),
                SizeI32(4, 4),
                64,
                setOf(PlanResourceUsage.DepthStencilAttachment),
                PlanResourceLifetime.FrameLocal,
                0,
                1,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PlanResource.of(
                PlanResourceRole.VertexData,
                0,
                PlanResourceKind.Buffer,
                PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
                null,
                4,
                setOf(PlanResourceUsage.Vertex),
                PlanResourceLifetime.FrameLocal,
                0,
                1,
            )
        }

        val formats = mutableSetOf(PlanDepthStencilFormat.Depth24PlusStencil8)
        val capabilities = supportedCapabilities(
            setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
            supportedDepthStencilFormats = formats,
        )
        formats.clear()

        assertEquals(
            setOf(PlanDepthStencilFormat.Depth24PlusStencil8),
            capabilities.supportedDepthStencilFormats(),
        )
        assertFailsWith<UnsupportedOperationException> {
            (capabilities.supportedDepthStencilFormats() as MutableSet<PlanDepthStencilFormat>).clear()
        }
    }

    @Test
    fun `path fill draw snapshots geometry scissor and fixed render contracts`() {
        val sourceScissor = RectI32(0, 0, 1, 1)
        val geometry = stencilGeometry()
        val draw = PathFillDraw.of(
            commandIndex = 0,
            color = ColorF32.of(0.5f, 0f, 0f, 0.5f),
            geometryF32 = geometry,
            strategy = PathFillStrategy.StencilCover,
            scissorI32 = sourceScissor,
        )
        sourceScissor.left = 99

        assertEquals(RectI32(0, 0, 1, 1), draw.copyScissorI32())
        assertEquals(FillRule.WINDING, draw.copyGeometryF32().fillRule)
        assertEquals(4, draw.copyGeometryF32().emittedNonZeroClosedEdgeCountI32)
        assertEquals(CoveragePlan.FullOrScissor, draw.coverage)
        assertEquals(SamplePlan.SingleSample, draw.sample)
        assertEquals(BlendPlan.SrcOver, draw.blend)
    }

    @Test
    fun `stencil producer cover and readback graph preserves one atomic draw group`() {
        val resources = atomicResources()
        val draw = stencilDraw(0)
        val pair = atomicPasses(resources, draw)
        val graph = atomicGraph(resources, pair)

        assertSame(draw, pair.producer.draw)
        assertSame(draw, pair.cover.draw)
        assertEquals(pair.producer.atomicGroup, pair.cover.atomicGroup)
        assertEquals(PlanDepthStencilAccess.Write, pair.producer.depthStencilAccess)
        assertEquals(PlanDepthStencilAccess.ReadWrite, pair.cover.depthStencilAccess)
        assertEquals(PlanDepthStencilLoadStore.ClearZeroStore, pair.producer.depthStencilLoadStore)
        assertEquals(PlanDepthStencilLoadStore.LoadStoreTestReset, pair.cover.depthStencilLoadStore)
        assertEquals(AttachmentLoadPlan.ClearTransparent, pair.producer.load)
        assertEquals(AttachmentLoadPlan.Load, pair.cover.load)
        assertEquals(AttachmentStorePlan.Store, pair.producer.store)
        assertEquals(AttachmentStorePlan.Store, pair.cover.store)
        assertEquals(
            listOf(PlanPassDependency(pair.producer.id, pair.cover.id), PlanPassDependency(pair.cover.id, graph.passes().last().id)),
            graph.dependencies(),
        )
    }

    @Test
    fun `stencil graphs require typed depth stencil capabilities`() {
        val resources = atomicResources()
        val pair = atomicPasses(resources, stencilDraw(0))

        assertFailsWith<IllegalArgumentException> {
            atomicGraph(
                resources,
                pair,
                capabilities = supportedCapabilities(
                    formats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
                    supportedOperations = setOf(
                        PlanOperationCapability.RenderPass,
                        PlanOperationCapability.Readback,
                    ),
                ),
            )
        }
    }

    @Test
    fun `render graph rejects forged stencil atomic contracts`() {
        val resources = atomicResources()
        val draw = stencilDraw(0)
        val valid = atomicPasses(resources, draw)

        assertFailsWith<IllegalArgumentException> {
            atomicGraph(
                resources,
                atomicPasses(resources, draw, coverGroup = PlanAtomicGroupId("w4c:other")),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            atomicGraph(resources, atomicPasses(resources, draw, coverDraw = stencilDraw(1)))
        }
        assertFailsWith<IllegalArgumentException> {
            atomicGraph(resources, atomicPasses(resources, draw, coverDraw = stencilDraw(0)))
        }
        val alternateTarget = PlanResource.of(
            PlanResourceRole.LogicalTarget,
            1,
            PlanResourceKind.Texture2D,
            PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
            SizeI32(1, 1),
            4,
            setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource),
            PlanResourceLifetime.FrameLocal,
            0,
            3,
        )
        assertFailsWith<IllegalArgumentException> {
            atomicGraph(
                resources,
                atomicPasses(resources, draw, coverTarget = alternateTarget.id),
                graphResources = resources.all + alternateTarget,
            )
        }
        val alternateVertex = PlanResource.of(
            PlanResourceRole.VertexData,
            1,
            PlanResourceKind.Buffer,
            null,
            null,
            4,
            setOf(PlanResourceUsage.Vertex, PlanResourceUsage.CopyDestination),
            PlanResourceLifetime.FrameLocal,
            0,
            3,
        )
        assertFailsWith<IllegalArgumentException> {
            atomicGraph(
                resources,
                atomicPasses(
                    resources,
                    draw,
                    coverDrawDataResources = PlanDrawDataResources(
                        alternateVertex.id,
                        resources.index.id,
                        resources.uniform.id,
                    ),
                ),
                graphResources = resources.all + alternateVertex,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            atomicGraph(
                resources,
                atomicPasses(
                    resources,
                    draw,
                    producerDepthStencilLoadStore = PlanDepthStencilLoadStore.LoadStoreTestReset,
                    coverDepthStencilLoadStore = PlanDepthStencilLoadStore.ClearZeroStore,
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            atomicGraph(
                resources,
                atomicPasses(resources, draw, coverDepthStencilAccess = PlanDepthStencilAccess.Write),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            atomicGraph(
                resources,
                valid,
                dependencies = listOf(PlanPassDependency(valid.cover.id, atomicReadback(resources).id)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            atomicGraph(resources, valid, graphResources = resources.all.filterNot { it.role == PlanResourceRole.DepthStencil })
        }
        assertFailsWith<IllegalArgumentException> {
            val duplicated = atomicResources(duplicateDepthStencil = true)
            atomicGraph(duplicated, atomicPasses(duplicated, stencilDraw(0)))
        }
        assertFailsWith<IllegalArgumentException> {
            val expired = atomicResources(depthStencilLastPassExclusive = 2)
            atomicGraph(expired, atomicPasses(expired, stencilDraw(0)))
        }
    }

    @Test
    fun `render graph rejects non adjacent stencil cover direct only depth and descending commands`() {
        val nonAdjacentResources = atomicResources(passCount = 4)
        val nonAdjacentPair = atomicPasses(nonAdjacentResources, stencilDraw(0))
        val middle = PlanPass.RenderPass(
            ordinal = 1,
            target = nonAdjacentResources.target.id,
            draws = emptyList(),
            load = AttachmentLoadPlan.Load,
            store = AttachmentStorePlan.Store,
        )
        val nonAdjacentReadback = atomicReadback(nonAdjacentResources)
        assertFailsWith<IllegalArgumentException> {
            graphOf(
                resources = nonAdjacentResources.all,
                passes = listOf(nonAdjacentPair.producer, middle, nonAdjacentPair.cover, nonAdjacentReadback),
                dependencies = listOf(
                    PlanPassDependency(nonAdjacentPair.producer.id, middle.id),
                    PlanPassDependency(middle.id, nonAdjacentPair.cover.id),
                    PlanPassDependency(nonAdjacentPair.cover.id, nonAdjacentReadback.id),
                ),
                visualCommandCount = 1,
            )
        }

        val directOnlyResources = atomicResources(passCount = 2)
        val direct = PlanPass.RenderPass(
            ordinal = 0,
            target = directOnlyResources.target.id,
            draws = emptyList(),
            load = AttachmentLoadPlan.ClearTransparent,
            store = AttachmentStorePlan.Store,
        )
        val directReadback = PlanPass.ReadbackPass(0, directOnlyResources.target.id, directOnlyResources.staging.id, 256)
        assertFailsWith<IllegalArgumentException> {
            graphOf(
                resources = listOf(directOnlyResources.target, directOnlyResources.staging, directOnlyResources.depthStencil),
                passes = listOf(direct, directReadback),
                dependencies = listOf(PlanPassDependency(direct.id, directReadback.id)),
                visualCommandCount = 0,
            )
        }

        val descendingResources = atomicResources(passCount = 4)
        val descendingPair = atomicPasses(descendingResources, stencilDraw(1))
        val descendingDirect = PlanPass.RenderPass(
            ordinal = 1,
            target = descendingResources.target.id,
            draws = listOf(
                SolidRectDraw.of(
                    0,
                    ColorF32.of(0f, 0f, 1f, 1f),
                    RectI32(0, 0, 1, 1),
                    RectI32(0, 0, 1, 1),
                ),
            ),
            load = AttachmentLoadPlan.Load,
            store = AttachmentStorePlan.Store,
        )
        val descendingReadback = atomicReadback(descendingResources)
        assertFailsWith<IllegalArgumentException> {
            graphOf(
                resources = descendingResources.all,
                passes = listOf(descendingPair.producer, descendingPair.cover, descendingDirect, descendingReadback),
                dependencies = listOf(
                    PlanPassDependency(descendingPair.producer.id, descendingPair.cover.id),
                    PlanPassDependency(descendingPair.cover.id, descendingDirect.id),
                    PlanPassDependency(descendingDirect.id, descendingReadback.id),
                ),
                visualCommandCount = 2,
            )
        }
    }

    private fun supportedCapabilities(
        formats: Set<PlanLogicalColorFormat>,
        copyBytesPerRowAlignment: Int = 256,
        supportedDepthStencilFormats: Set<PlanDepthStencilFormat> = emptySet(),
        supportedOperations: Set<PlanOperationCapability> = setOf(
            PlanOperationCapability.RenderPass,
            PlanOperationCapability.Readback,
        ),
    ) = PlanCapabilitySnapshot.of(
        deviceGeneration = 0,
        maxTextureDimension2D = 1024,
        maxBufferSizeBytes = 4096,
        copyBytesPerRowAlignment = copyBytesPerRowAlignment,
        supportedFormats = formats,
        minUniformBufferOffsetAlignment = 256,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
        supportedOperations = supportedOperations,
        bufferAllocationPolicy = PlanBufferAllocationPolicy.of(16_384, 4_096, 4_096),
        supportedDepthStencilFormats = supportedDepthStencilFormats,
    )

    private fun graphWithAnalyticDrawResources(uniformLifetime: IntRange): RenderGraph {
        val target = targetResource()
        val staging = stagingResource()
        fun scratch(
            role: PlanResourceRole,
            usage: PlanResourceUsage,
            lifetime: IntRange = 0 until 2,
        ) = PlanResource.of(
            role, 0, PlanResourceKind.Buffer, null, null, 4_096,
            setOf(usage, PlanResourceUsage.CopyDestination),
            PlanResourceLifetime.FrameLocal, lifetime.first, lifetime.last + 1,
        )
        val vertex = scratch(PlanResourceRole.VertexData, PlanResourceUsage.Vertex)
        val index = scratch(PlanResourceRole.IndexData, PlanResourceUsage.Index)
        val uniform = scratch(
            PlanResourceRole.UniformData,
            PlanResourceUsage.Uniform,
            uniformLifetime,
        )
        val draw = AnalyticRectDraw.of(
            0, ColorF32.of(1f, 0f, 0f, 1f),
            RectF32(0.25f, 0f, 0.75f, 1f), RectI32(0, 0, 1, 1), RectI32(0, 0, 1, 1),
        )
        val render = PlanPass.RenderPass(
            0, target.id, listOf(draw), AttachmentLoadPlan.ClearTransparent,
            AttachmentStorePlan.Store, PlanDrawDataResources(vertex.id, index.id, uniform.id),
        )
        val readback = PlanPass.ReadbackPass(0, target.id, staging.id, 256)
        return validGraph(
            resources = listOf(target, staging, vertex, index, uniform),
            passes = listOf(render, readback),
            dependencies = listOf(PlanPassDependency(render.id, readback.id)),
            peakFrameLocalBytes = 12_548,
        )
    }

    private fun targetResource(
        usages: Set<PlanResourceUsage> = setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource),
        extent: SizeI32 = SizeI32(1, 1),
        byteSize: Long = 4,
        firstPassIndex: Int = 0,
        lastPassIndexExclusive: Int = 2,
    ) = PlanResource.of(
        PlanResourceRole.LogicalTarget, 0, PlanResourceKind.Texture2D,
        PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), extent, byteSize,
        usages, PlanResourceLifetime.FrameLocal, firstPassIndex, lastPassIndexExclusive,
    )

    private fun stagingResource(
        usages: Set<PlanResourceUsage> = setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.MapRead),
        byteSize: Long = 256,
    ) = PlanResource.of(
        PlanResourceRole.ReadbackStaging, 0, PlanResourceKind.Buffer, null, null, byteSize,
        usages,
        PlanResourceLifetime.FrameLocal, 1, 2,
    )

    private fun renderAndReadback(
        target: PlanResource,
        staging: PlanResource,
        bytesPerRow: Long = 256,
    ): List<PlanPass> = listOf(
        renderPass(target),
        PlanPass.ReadbackPass(1, target.id, staging.id, bytesPerRow),
    )

    private fun renderPass(target: PlanResource): PlanPass.RenderPass = PlanPass.RenderPass(
        0, target.id, emptyList(), AttachmentLoadPlan.ClearTransparent, AttachmentStorePlan.Store,
    )

    private fun validGraph(
        capabilities: PlanCapabilitySnapshot = supportedCapabilities(setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL)),
        budget: PlanBudget = PlanBudget(1024),
        resources: List<PlanResource> = listOf(targetResource(), stagingResource()),
        passes: List<PlanPass> = listOf(
            PlanPass.RenderPass(0, targetResource().id, emptyList(), AttachmentLoadPlan.ClearTransparent, AttachmentStorePlan.Store),
            PlanPass.ReadbackPass(1, targetResource().id, stagingResource().id, 256),
        ),
        dependencies: List<PlanPassDependency> = if (passes.size > 1) {
            listOf(PlanPassDependency(passes[0].id, passes[1].id))
        } else {
            emptyList()
        },
        targetExtent: SizeI32 = SizeI32(1, 1),
        peakFrameLocalBytes: Long = if (resources.size == 1) 4 else 260,
    ): RenderGraph = RenderGraph.of(
        PlanId("plan"), "capabilities", targetExtent, PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL,
        capabilities, budget, 0, resources, passes,
        dependencies,
        peakFrameLocalBytes,
    )

    private data class AtomicResources(
        val target: PlanResource,
        val staging: PlanResource,
        val vertex: PlanResource,
        val index: PlanResource,
        val uniform: PlanResource,
        val depthStencil: PlanResource,
        val all: List<PlanResource>,
    )

    private data class AtomicPassPair(
        val producer: PlanPass.StencilProducer,
        val cover: PlanPass.StencilCover,
    )

    private fun atomicResources(
        passCount: Int = 3,
        depthStencilLastPassExclusive: Int = passCount,
        duplicateDepthStencil: Boolean = false,
    ): AtomicResources {
        val target = PlanResource.of(
            PlanResourceRole.LogicalTarget,
            0,
            PlanResourceKind.Texture2D,
            PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
            SizeI32(1, 1),
            4,
            setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource),
            PlanResourceLifetime.FrameLocal,
            0,
            passCount,
        )
        val staging = PlanResource.of(
            PlanResourceRole.ReadbackStaging,
            0,
            PlanResourceKind.Buffer,
            null,
            null,
            256,
            setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.MapRead),
            PlanResourceLifetime.FrameLocal,
            passCount - 1,
            passCount,
        )
        fun data(role: PlanResourceRole, usage: PlanResourceUsage) = PlanResource.of(
            role,
            0,
            PlanResourceKind.Buffer,
            null,
            null,
            4,
            setOf(usage, PlanResourceUsage.CopyDestination),
            PlanResourceLifetime.FrameLocal,
            0,
            passCount,
        )
        val vertex = data(PlanResourceRole.VertexData, PlanResourceUsage.Vertex)
        val index = data(PlanResourceRole.IndexData, PlanResourceUsage.Index)
        val uniform = data(PlanResourceRole.UniformData, PlanResourceUsage.Uniform)
        val depthStencil = PlanResource.of(
            PlanResourceRole.DepthStencil,
            0,
            PlanResourceKind.Texture2D,
            PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8),
            SizeI32(1, 1),
            4,
            setOf(PlanResourceUsage.DepthStencilAttachment),
            PlanResourceLifetime.FrameLocal,
            0,
            depthStencilLastPassExclusive,
        )
        val duplicate = if (duplicateDepthStencil) {
            listOf(
                PlanResource.of(
                    PlanResourceRole.DepthStencil,
                    1,
                    PlanResourceKind.Texture2D,
                    PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8),
                    SizeI32(1, 1),
                    4,
                    setOf(PlanResourceUsage.DepthStencilAttachment),
                    PlanResourceLifetime.FrameLocal,
                    0,
                    passCount,
                ),
            )
        } else {
            emptyList()
        }
        return AtomicResources(target, staging, vertex, index, uniform, depthStencil, listOf(target, staging, vertex, index, uniform, depthStencil) + duplicate)
    }

    private fun atomicPasses(
        resources: AtomicResources,
        producerDraw: PathFillDraw,
        coverDraw: PathFillDraw = producerDraw,
        coverTarget: PlanResourceId = resources.target.id,
        producerGroup: PlanAtomicGroupId = PlanAtomicGroupId("w4c:${producerDraw.commandIndex}"),
        coverGroup: PlanAtomicGroupId = producerGroup,
        producerDepthStencilLoadStore: PlanDepthStencilLoadStore = PlanDepthStencilLoadStore.ClearZeroStore,
        coverDepthStencilLoadStore: PlanDepthStencilLoadStore = PlanDepthStencilLoadStore.LoadStoreTestReset,
        producerDepthStencilAccess: PlanDepthStencilAccess = PlanDepthStencilAccess.Write,
        coverDepthStencilAccess: PlanDepthStencilAccess = PlanDepthStencilAccess.ReadWrite,
        coverDrawDataResources: PlanDrawDataResources? = null,
    ): AtomicPassPair {
        val drawDataResources = PlanDrawDataResources(resources.vertex.id, resources.index.id, resources.uniform.id)
        return AtomicPassPair(
            PlanPass.StencilProducer(
                ordinal = 0,
                target = resources.target.id,
                depthStencil = resources.depthStencil.id,
                draw = producerDraw,
                drawDataResources = drawDataResources,
                atomicGroup = producerGroup,
                load = AttachmentLoadPlan.ClearTransparent,
                store = AttachmentStorePlan.Store,
                depthStencilAccess = producerDepthStencilAccess,
                depthStencilLoadStore = producerDepthStencilLoadStore,
            ),
            PlanPass.StencilCover(
                ordinal = 0,
                target = coverTarget,
                depthStencil = resources.depthStencil.id,
                draw = coverDraw,
                drawDataResources = coverDrawDataResources ?: drawDataResources,
                atomicGroup = coverGroup,
                load = AttachmentLoadPlan.Load,
                store = AttachmentStorePlan.Store,
                depthStencilAccess = coverDepthStencilAccess,
                depthStencilLoadStore = coverDepthStencilLoadStore,
            ),
        )
    }

    private fun atomicReadback(resources: AtomicResources): PlanPass.ReadbackPass =
        PlanPass.ReadbackPass(0, resources.target.id, resources.staging.id, 256)

    private fun atomicGraph(
        resources: AtomicResources,
        pair: AtomicPassPair = atomicPasses(resources, stencilDraw(0)),
        graphResources: List<PlanResource> = resources.all,
        dependencies: List<PlanPassDependency> = listOf(
            PlanPassDependency(pair.producer.id, pair.cover.id),
            PlanPassDependency(pair.cover.id, atomicReadback(resources).id),
        ),
        capabilities: PlanCapabilitySnapshot = atomicCapabilities(),
    ): RenderGraph = graphOf(
        resources = graphResources,
        passes = listOf(pair.producer, pair.cover, atomicReadback(resources)),
        dependencies = dependencies,
        visualCommandCount = 1,
        capabilities = capabilities,
    )

    private fun graphOf(
        resources: List<PlanResource>,
        passes: List<PlanPass>,
        dependencies: List<PlanPassDependency>,
        visualCommandCount: Int,
        capabilities: PlanCapabilitySnapshot = atomicCapabilities(),
    ): RenderGraph = RenderGraph.of(
        id = PlanId("atomic-plan"),
        capabilityId = "atomic-stencil-contract",
        targetExtent = SizeI32(1, 1),
        colorFormat = PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL,
        capabilities = capabilities,
        budget = PlanBudget(4_096),
        visualCommandCount = visualCommandCount,
        resources = resources,
        passes = passes,
        dependencies = dependencies,
        peakFrameLocalBytes = peak(resources, passes.size),
    )

    private fun atomicCapabilities(): PlanCapabilitySnapshot = supportedCapabilities(
            formats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
            supportedDepthStencilFormats = setOf(PlanDepthStencilFormat.Depth24PlusStencil8),
            supportedOperations = setOf(
                PlanOperationCapability.RenderPass,
                PlanOperationCapability.Readback,
                PlanOperationCapability.DepthStencilAttachment,
                PlanOperationCapability.StencilCover,
            ),
        )

    private fun peak(resources: List<PlanResource>, passCount: Int): Long =
        (0 until passCount).maxOf { passIndex ->
            resources.filter { resource ->
                resource.firstPassIndex <= passIndex && passIndex < resource.lastPassIndexExclusive
            }.fold(0L) { total, resource -> Math.addExact(total, resource.byteSize) }
        }

    private fun stencilDraw(commandIndex: Int): PathFillDraw = PathFillDraw.of(
        commandIndex = commandIndex,
        color = ColorF32.of(0.5f, 0f, 0f, 0.5f),
        geometryF32 = stencilGeometry(),
        strategy = PathFillStrategy.StencilCover,
        scissorI32 = RectI32(0, 0, 1, 1),
    )

    private fun stencilGeometry(): PathFillGeometryF32 = assertIs<PathFillPreparationResult.Ready>(
        preparePathFillGeometryF32(
            PathFillInputF64.of(
                FillRule.WINDING,
                listOf(
                    PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.0)),
                    PathFillSegmentF64.LineTo(Point2F64(1.0, 0.0)),
                    PathFillSegmentF64.LineTo(Point2F64(1.0, 1.0)),
                    PathFillSegmentF64.LineTo(Point2F64(0.0, 1.0)),
                    PathFillSegmentF64.Close,
                ),
            ),
        ),
    ).geometryF32
}
