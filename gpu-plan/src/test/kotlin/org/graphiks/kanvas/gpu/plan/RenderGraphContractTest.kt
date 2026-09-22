package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.color.ColorF32
import org.graphiks.math.geometry.FillRule
import org.graphiks.math.geometry.PathFillGeometryF32
import org.graphiks.math.geometry.PathFillInputF64
import org.graphiks.math.geometry.PathFillPreparationResult
import org.graphiks.math.geometry.PathFillSegmentF64
import org.graphiks.math.geometry.PathStrokeCap
import org.graphiks.math.geometry.PathStrokeDeviceFillSegmentMapperF64
import org.graphiks.math.geometry.PathStrokeDrawMode
import org.graphiks.math.geometry.PathStrokeGeometryF32
import org.graphiks.math.geometry.PathStrokeJoin
import org.graphiks.math.geometry.PathStrokePreparationResult
import org.graphiks.math.geometry.PathStrokeProjectionF64
import org.graphiks.math.geometry.PathStrokeProjectionIntervalResultF64
import org.graphiks.math.geometry.PathStrokeProjectionPointResultF64
import org.graphiks.math.geometry.PathStrokeStyleF64
import org.graphiks.math.geometry.PathStrokeWidthF64
import org.graphiks.math.geometry.Point2F64
import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.geometry.ClipGeometryF32
import org.graphiks.math.geometry.InverseInteriorCoverageF32
import org.graphiks.math.geometry.InversePathGeometryF32
import org.graphiks.math.matrix.LayerMappingF64
import org.graphiks.math.matrix.Matrix3x3F64
import org.graphiks.kanvas.render.ir.CapturedFilterNodeId
import org.graphiks.kanvas.render.ir.LayerDescriptor
import org.graphiks.kanvas.render.ir.SceneExtent
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.math.geometry.preparePathFillGeometryF32
import org.graphiks.math.geometry.prepareProjectedPathStrokeGeometryF32
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

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

    @Test
    fun `direct path frames require a final readback and consecutive dependencies`() {
        val resources = directPathResources(passCount = 1, readbackPassIndex = 0)
        val render = directRenderPass(
            ordinal = 0,
            target = resources.target.id,
            draws = listOf(directDraw(0)),
            load = AttachmentLoadPlan.ClearTransparent,
            drawDataResources = directDrawDataResources(resources),
        )

        assertFailsWith<IllegalArgumentException> {
            graphOf(
                resources = listOf(resources.target, resources.vertex, resources.index, resources.uniform),
                passes = listOf(render),
                dependencies = emptyList(),
                visualCommandCount = 1,
                capabilities = w4cCapabilities(),
            )
        }
    }

    @Test
    fun `path frames reject a readback that is not terminal`() {
        val resources = directPathResources(passCount = 3, readbackPassIndex = 1)
        val first = directRenderPass(
            ordinal = 0,
            target = resources.target.id,
            draws = listOf(directDraw(0)),
            load = AttachmentLoadPlan.ClearTransparent,
            drawDataResources = directDrawDataResources(resources),
        )
        val readback = directReadback(resources)
        val second = directRenderPass(
            ordinal = 1,
            target = resources.target.id,
            draws = listOf(directDraw(1)),
            load = AttachmentLoadPlan.Load,
            drawDataResources = directDrawDataResources(resources),
        )

        assertFailsWith<IllegalArgumentException> {
            graphOf(
                resources = resources.all,
                passes = listOf(first, readback, second),
                dependencies = listOf(
                    PlanPassDependency(first.id, readback.id),
                    PlanPassDependency(readback.id, second.id),
                ),
                visualCommandCount = 2,
                capabilities = w4cCapabilities(),
            )
        }
    }

    @Test
    fun `path frames reject a second color target`() {
        val resources = directPathResources(passCount = 3, readbackPassIndex = 2)
        val secondTarget = PlanResource.of(
            role = PlanResourceRole.LogicalTarget,
            ordinal = 1,
            kind = PlanResourceKind.Texture2D,
            format = PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
            extent = SizeI32(1, 1),
            byteSize = 4,
            usages = setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource),
            lifetime = PlanResourceLifetime.FrameLocal,
            firstPassIndex = 0,
            lastPassIndexExclusive = 3,
        )
        val first = directRenderPass(
            ordinal = 0,
            target = resources.target.id,
            draws = listOf(directDraw(0)),
            load = AttachmentLoadPlan.ClearTransparent,
            drawDataResources = directDrawDataResources(resources),
        )
        val second = directRenderPass(
            ordinal = 1,
            target = secondTarget.id,
            draws = listOf(directDraw(1)),
            load = AttachmentLoadPlan.Load,
            drawDataResources = directDrawDataResources(resources),
        )
        val readback = directReadback(resources)

        assertFailsWith<IllegalArgumentException> {
            graphOf(
                resources = resources.all + secondTarget,
                passes = listOf(first, second, readback),
                dependencies = listOf(
                    PlanPassDependency(first.id, second.id),
                    PlanPassDependency(second.id, readback.id),
                ),
                visualCommandCount = 2,
                capabilities = w4cCapabilities(),
            )
        }
    }

    @Test
    fun `path frames count their unique visual draws`() {
        assertFailsWith<IllegalArgumentException> {
            directPathGraph(visualCommandCount = 0)
        }
    }

    @Test
    fun `direct path render requires vertex index and uniform resources`() {
        assertFailsWith<IllegalArgumentException> {
            directPathGraph(drawDataResources = null)
        }
    }

    @Test
    fun `direct path render contains exactly one draw`() {
        assertFailsWith<IllegalArgumentException> {
            directPathGraph(
                draws = listOf(directDraw(0), directDraw(1)),
                visualCommandCount = 2,
            )
        }
    }

    @Test
    fun `direct path render cannot mix a non path draw`() {
        val solidRect = SolidRectDraw.of(
            commandIndex = 1,
            color = ColorF32.of(0f, 0f, 1f, 1f),
            visibleBounds = RectI32(0, 0, 1, 1),
            scissor = RectI32(0, 0, 1, 1),
        )

        assertFailsWith<IllegalArgumentException> {
            directPathGraph(
                draws = listOf(directDraw(0), solidRect),
                visualCommandCount = 2,
            )
        }
    }

    @Test
    fun `path vertex data cannot be a texture`() {
        val resources = directPathResources()
        val textureVertex = PlanResource.of(
            role = PlanResourceRole.VertexData,
            ordinal = 0,
            kind = PlanResourceKind.Texture2D,
            format = PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
            extent = SizeI32(1, 1),
            byteSize = 4,
            usages = setOf(PlanResourceUsage.Vertex, PlanResourceUsage.CopyDestination),
            lifetime = PlanResourceLifetime.FrameLocal,
            firstPassIndex = 0,
            lastPassIndexExclusive = 2,
        )

        assertFailsWith<IllegalArgumentException> {
            directPathGraph(
                resources = resources,
                graphResources = resources.all.map { resource ->
                    if (resource.id == resources.vertex.id) textureVertex else resource
                },
            )
        }
    }

    @Test
    fun `path vertex data cannot use a different resource role`() {
        val resources = directPathResources()
        val wrongRoleVertex = PlanResource.of(
            role = PlanResourceRole.ReadbackStaging,
            ordinal = 1,
            kind = PlanResourceKind.Buffer,
            format = null,
            extent = null,
            byteSize = 4,
            usages = setOf(PlanResourceUsage.Vertex, PlanResourceUsage.CopyDestination),
            lifetime = PlanResourceLifetime.FrameLocal,
            firstPassIndex = 0,
            lastPassIndexExclusive = 2,
        )
        val bindings = PlanDrawDataResources(
            wrongRoleVertex.id,
            resources.index.id,
            resources.uniform.id,
        )

        assertFailsWith<IllegalArgumentException> {
            directPathGraph(
                resources = resources,
                graphResources = resources.all + wrongRoleVertex,
                drawDataResources = bindings,
            )
        }
    }

    @Test
    fun `path vertex data requires its semantic usage`() {
        val resources = directPathResources()
        val missingVertexUsage = PlanResource.of(
            role = PlanResourceRole.VertexData,
            ordinal = 0,
            kind = PlanResourceKind.Buffer,
            format = null,
            extent = null,
            byteSize = 4,
            usages = setOf(PlanResourceUsage.CopyDestination),
            lifetime = PlanResourceLifetime.FrameLocal,
            firstPassIndex = 0,
            lastPassIndexExclusive = 2,
        )

        assertFailsWith<IllegalArgumentException> {
            directPathGraph(
                resources = resources,
                graphResources = resources.all.map { resource ->
                    if (resource.id == resources.vertex.id) missingVertexUsage else resource
                },
            )
        }
    }

    @Test
    fun `path vertex data requires copy destination usage`() {
        val resources = directPathResources()
        val missingCopyDestination = PlanResource.of(
            role = PlanResourceRole.VertexData,
            ordinal = 0,
            kind = PlanResourceKind.Buffer,
            format = null,
            extent = null,
            byteSize = 4,
            usages = setOf(PlanResourceUsage.Vertex),
            lifetime = PlanResourceLifetime.FrameLocal,
            firstPassIndex = 0,
            lastPassIndexExclusive = 2,
        )

        assertFailsWith<IllegalArgumentException> {
            directPathGraph(
                resources = resources,
                graphResources = resources.all.map { resource ->
                    if (resource.id == resources.vertex.id) missingCopyDestination else resource
                },
            )
        }
    }

    @Test
    fun `depth stencil attachment usage is reserved for D24S8 textures`() {
        assertFailsWith<IllegalArgumentException> {
            PlanResource.of(
                role = PlanResourceRole.VertexData,
                ordinal = 0,
                kind = PlanResourceKind.Buffer,
                format = null,
                extent = null,
                byteSize = 4,
                usages = setOf(PlanResourceUsage.DepthStencilAttachment),
                lifetime = PlanResourceLifetime.FrameLocal,
                firstPassIndex = 0,
                lastPassIndexExclusive = 1,
            )
        }
    }

    @Test
    fun `path graphs require copy upload and uniform buffer capabilities`() {
        assertFailsWith<IllegalArgumentException> {
            directPathGraph(
                capabilities = w4cCapabilities(
                    supportedOperations = setOf(
                        PlanOperationCapability.RenderPass,
                        PlanOperationCapability.Readback,
                    ),
                ),
            )
        }
    }

    @Test
    fun `path draw data keeps its shared lease through terminal readback`() {
        val resources = directPathResources(dataLastPassExclusive = 1)

        assertFailsWith<IllegalArgumentException> {
            directPathGraph(
                resources = resources,
                peakFrameLocalBytes = 260,
            )
        }
    }

    @Test
    fun `stencil atomic groups are canonical command identities`() {
        val resources = atomicResources()
        val arbitraryGroup = PlanAtomicGroupId("not-w4c-command")

        assertFailsWith<IllegalArgumentException> {
            atomicGraph(
                resources,
                atomicPasses(
                    resources,
                    stencilDraw(0),
                    producerGroup = arbitraryGroup,
                    coverGroup = arbitraryGroup,
                ),
            )
        }
    }

    @Test
    fun `stencil atomic groups cannot be reused by different commands`() {
        val resources = atomicResources(passCount = 5)
        val reused = PlanAtomicGroupId("w4c:0")
        val first = atomicPasses(
            resources = resources,
            producerDraw = stencilDraw(0),
            producerGroup = reused,
            coverGroup = reused,
        )
        val second = atomicPasses(
            resources = resources,
            producerDraw = stencilDraw(1),
            producerOrdinal = 1,
            coverOrdinal = 1,
            producerLoad = AttachmentLoadPlan.Load,
            producerGroup = reused,
            coverGroup = reused,
        )
        val readback = atomicReadback(resources)

        assertFailsWith<IllegalArgumentException> {
            graphOf(
                resources = resources.all,
                passes = listOf(first.producer, first.cover, second.producer, second.cover, readback),
                dependencies = listOf(
                    PlanPassDependency(first.producer.id, first.cover.id),
                    PlanPassDependency(first.cover.id, second.producer.id),
                    PlanPassDependency(second.producer.id, second.cover.id),
                    PlanPassDependency(second.cover.id, readback.id),
                ),
                visualCommandCount = 2,
                capabilities = w4cCapabilities(),
            )
        }
    }

    @Test
    fun `path graphs reject a separate solid rect render pass`() {
        assertFailsWith<IllegalArgumentException> {
            directPathGraphWithInterposedPass(visualCommandCount = 2) { resources ->
                directRenderPass(
                    ordinal = 1,
                    target = resources.target.id,
                    draws = listOf(
                        SolidRectDraw.of(
                            commandIndex = 1,
                            color = ColorF32.of(0f, 0f, 1f, 1f),
                            visibleBounds = RectI32(0, 0, 1, 1),
                            scissor = RectI32(0, 0, 1, 1),
                        ),
                    ),
                    load = AttachmentLoadPlan.Load,
                    drawDataResources = null,
                )
            }
        }
    }

    @Test
    fun `path graphs reject empty render passes`() {
        assertFailsWith<IllegalArgumentException> {
            directPathGraphWithInterposedPass { resources ->
                directRenderPass(
                    ordinal = 1,
                    target = resources.target.id,
                    draws = emptyList(),
                    load = AttachmentLoadPlan.Load,
                    drawDataResources = null,
                )
            }
        }
    }

    @Test
    fun `path graphs reject texture copy passes`() {
        assertFailsWith<IllegalArgumentException> {
            directPathGraphWithInterposedPass { resources ->
                PlanPass.TextureCopy(0, resources.target.id, resources.target.id)
            }
        }
    }

    @Test
    fun `path graphs reject filter passes`() {
        assertFailsWith<IllegalArgumentException> {
            directPathGraphWithInterposedPass { resources ->
                val deviceBounds = RectI32(0, 0, 1, 1)
                val mapping = requireNotNull(LayerMappingF64.ofOrNull(Matrix3x3F64(), Point2I32.Origin))
                val filterBounds = FilterBoundsPlanV1(null, deviceBounds, deviceBounds, deviceBounds, Point2I32.Origin)
                PlanPass.FilterPass(
                    0,
                    listOf(resources.target.id),
                    planResourceId(PlanResourceRole.FilterTarget, 0),
                    FilterEvaluationKeyV1.of(CapturedFilterNodeId(0), resources.target.id, mapping, deviceBounds),
                    FilterPassOperationV1.SeparableBlur(
                        FilterImplementationKindV1.IMAGE_BLUR_X,
                        1f,
                        FilterAxisV1.X,
                        org.graphiks.kanvas.render.ir.TileMode.CLAMP,
                        filterBounds,
                    ),
                )
            }
        }
    }

    @Test
    fun `w6b publication witness accepts an x then y chain consumed by its composite`() {
        val graph = w6bFilterPublicationGraph()

        assertEquals(2, graph.passes().filterIsInstance<PlanPass.FilterPass>().size)
    }

    @Test
    fun `w6b publication witness rejects a filter bound to an unrelated source`() {
        assertFailsWith<IllegalArgumentException> {
            w6bFilterPublicationGraph(boundSource = planResourceId(PlanResourceRole.LogicalTarget, 0))
        }
    }

    @Test
    fun `w6b publication witness rejects a broken x producer and an unconsumed terminal output`() {
        assertFailsWith<IllegalArgumentException> {
            w6bFilterPublicationGraph(verticalInput = planResourceId(PlanResourceRole.FilterTarget, 9))
        }
        assertFailsWith<IllegalArgumentException> {
            w6bFilterPublicationGraph(consumeTerminalOutput = false)
        }
    }

    @Test
    fun `w6b publication witness rejects an occurrence chain that starts at y or changes evaluation key`() {
        assertFailsWith<IllegalArgumentException> {
            w6bFilterPublicationGraph(firstAxis = FilterAxisV1.Y)
        }
        assertFailsWith<IllegalArgumentException> {
            w6bFilterPublicationGraph(verticalUsesDifferentKey = true)
        }
    }

    @Test
    fun `w6b publication witness rejects mutable layer sources and nonterminal composites`() {
        assertFailsWith<IllegalArgumentException> {
            w6bFilterPublicationGraph(sourceRole = PlanResourceRole.LayerTarget)
        }
        assertFailsWith<IllegalArgumentException> {
            w6bFilterPublicationGraph(compositeIntermediateOutput = true)
        }
    }

    @Test
    fun `w6b publication witness validates drop shadow original ownership and mode arity`() {
        assertFailsWith<IllegalArgumentException> {
            w6bDropShadowPublicationGraph(wrongOriginal = true)
        }
        assertFailsWith<IllegalArgumentException> {
            w6bDropShadowPublicationGraph(
                mode = org.graphiks.kanvas.render.ir.CapturedDropShadowModeV1.SHADOW_ONLY,
                includeOriginal = true,
            )
        }
    }

    @Test
    fun `w6b publication witness recognizes Picture coverage as a material consumer`() {
        val fragment = w6bPictureCoverageWitnessFragment()

        assertEquals(0, W6bFilterGraphWitnessV1.seal(fragment.first, fragment.second).occurrences().size)
    }

    @Test
    fun `w6b publication witness rejects a Picture coverage input without a producer`() {
        val resource = w6bWitnessTexture(PlanResourceRole.FilterSource, 0)
        val coverage = w6bWitnessTexture(PlanResourceRole.FilterTarget, 0)

        assertFailsWith<IllegalArgumentException> {
            W6bFilterGraphWitnessV1.seal(
                listOf(resource, coverage),
                listOf(PlanPass.PictureSourcePass(0, resource.id, "picture", 0, coverageSource = coverage.id)),
            )
        }
    }

    @Test
    fun `w6b publication witness rejects shadow colorize without its matching vertical blur`() {
        assertFailsWith<IllegalArgumentException> {
            w6bDropShadowPublicationGraph(wrongColorizeInput = true)
        }
    }

    @Test
    fun `Picture alpha coverage publication binds the exact sealed source generation`() {
        val source = w6bWitnessTexture(PlanResourceRole.PictureAggregateSource, 0)
        val coverage = w6bWitnessTexture(PlanResourceRole.CoverageSource, 0)
        val aggregate = PictureStreamAggregateIdI32(0)
        val mapping = requireNotNull(LayerMappingF64.ofOrNull(Matrix3x3F64(), Point2I32.Origin))
        val scene = SceneSnapshot.of(SceneExtent(1, 1), ColorSpace.SRGB, emptyList())
        val occurrence = FilterOccurrenceSourceV1(scene, 0, null, emptyList(), LayerDescriptor.of())
        val seal = PlanPass.PictureAggregateSealPass(0, aggregate, source.id, source.id, 2L)
        fun alpha(generation: Long) = PlanPass.FilterCoverageSourcePass(1, coverage.id, occurrence,
            sealedAlphaSource = PictureAlphaSourceV1(aggregate, source.id, generation, mapping, RectI32(0, 0, 1, 1)))

        assertEquals(0, W6bFilterGraphWitnessV1.seal(listOf(source, coverage), listOf(seal, alpha(2L))).occurrences().size)
        assertFailsWith<IllegalArgumentException> {
            W6bFilterGraphWitnessV1.seal(listOf(source, coverage), listOf(seal, alpha(1L)))
        }
        assertFailsWith<IllegalArgumentException> {
            W6bFilterGraphWitnessV1.seal(listOf(source, coverage), listOf(alpha(2L), seal))
        }
    }

    @Test
    fun `picture draw publication rejects captured source without a frozen W4 W5 terminal`() {
        val root = w6bWitnessTexture(PlanResourceRole.LogicalTarget, 0)
        val locator = PictureSourceLocatorV1(0, 0)
        val planned = FramePlannedCommandIdI32(1)
        val pass = PlanPass.PictureSourcePass(0, root.id, "picture-source", 0,
            pictureSourceLocator = locator, plannedCommandId = planned,
            aggregateId = PictureStreamAggregateIdI32(0))
        val aggregate = PictureStreamAggregateV1(
            PictureStreamAggregateIdI32(0), PictureStreamExecutionModeV1.INLINE_CURRENT_TARGET,
            "picture-source", 1, 0, FramePlannedCommandIdI32(0), emptyList(),
            requireNotNull(LayerMappingF64.ofOrNull(Matrix3x3F64(), Point2I32.Origin)),
            org.graphiks.kanvas.render.ir.ClipStackNode.Empty,
            org.graphiks.kanvas.render.ir.ClipStackNode.Empty,
            RectI32(0, 0, 1, 1), RectI32(0, 0, 1, 1), root.id, null, null, null,
            PictureStreamRegionsV1(RectI32(0, 0, 1, 1), RectI32(0, 0, 1, 1),
                RectI32(0, 0, 1, 1), RectI32(0, 0, 1, 1)),
            listOf(PictureStreamEntryV1.Draw(PictureStreamEntryIdI32(0), locator, planned, pass.id)),
            null, null, pass.id,
        )
        val failure = assertFailsWith<W6bFilterGraphConstruction.ConstructionFailure> {
            validatePictureStreamAggregates(LayerFramePlanV1(emptyList(), emptyList(), listOf(aggregate)),
                listOf(root), listOf(pass), emptyList())
        }
        assertEquals(W6bFilterDiagnostics.PictureStreamInvalid, failure.diagnostic.code.value)
    }

    @Test
    fun `picture aggregate publication rejects an overlapping source partition with its stable diagnostic`() {
        val root = w6bWitnessTexture(PlanResourceRole.LogicalTarget, 0)
        val first = PlanPass.PictureSourcePass(0, root.id, "picture-source", 0)
        val second = PlanPass.PictureSourcePass(1, root.id, "picture-source", 0)
        val locator = PictureSourceLocatorV1(0, 0)
        val aggregate = PictureStreamAggregateV1(
            PictureStreamAggregateIdI32(0),
            PictureStreamExecutionModeV1.INLINE_CURRENT_TARGET,
            "picture-source",
            1,
            0,
            FramePlannedCommandIdI32(0),
            emptyList(),
            requireNotNull(LayerMappingF64.ofOrNull(Matrix3x3F64(), Point2I32.Origin)),
            org.graphiks.kanvas.render.ir.ClipStackNode.Empty,
            org.graphiks.kanvas.render.ir.ClipStackNode.Empty,
            RectI32(0, 0, 1, 1),
            RectI32(0, 0, 1, 1),
            root.id,
            null,
            null,
            null,
            PictureStreamRegionsV1(RectI32(0, 0, 1, 1), RectI32(0, 0, 1, 1), RectI32(0, 0, 1, 1), RectI32(0, 0, 1, 1)),
            listOf(
                PictureStreamEntryV1.Clear(PictureStreamEntryIdI32(0), locator, FramePlannedCommandIdI32(1),
                    ColorF32.of(0f, 0f, 0f, 0f), first.id),
                PictureStreamEntryV1.Clear(PictureStreamEntryIdI32(1), locator, FramePlannedCommandIdI32(2),
                    ColorF32.of(0f, 0f, 0f, 0f), second.id),
            ),
            null,
            null,
            second.id,
        )

        val failure = assertFailsWith<W6bFilterGraphConstruction.ConstructionFailure> {
            validatePictureStreamAggregates(
                LayerFramePlanV1(emptyList(), emptyList(), listOf(aggregate)),
                listOf(root),
                listOf(first, second),
                listOf(PlanPassDependency(first.id, second.id)),
            )
        }
        assertEquals(W6bFilterDiagnostics.PictureStreamInvalid, failure.diagnostic.code.value)
        assertTrue(failure.diagnostic.message.contains("aggregateIdI32=0"))
    }

    @Test
    fun `root source order rejects B before the Picture terminal without comparing aggregate local indices`() {
        val root = w6bWitnessTexture(PlanResourceRole.LogicalTarget, 0)
        val aggregateId = PictureStreamAggregateIdI32(0)
        val firstLocator = PictureSourceLocatorV1(0, 0)
        val secondLocator = PictureSourceLocatorV1(0, 1)
        val firstPlanned = FramePlannedCommandIdI32(1)
        val secondPlanned = FramePlannedCommandIdI32(2)
        fun rootDraw(commandIndexI32: Int) = SolidRectDraw.of(
            commandIndexI32,
            ColorF32.of(1f, 1f, 1f, 1f),
            RectI32(0, 0, 1, 1),
            RectI32(0, 0, 1, 1),
        )
        val drawA = PlanPass.RenderPass(0, root.id, listOf(rootDraw(0)),
            AttachmentLoadPlan.ClearTransparent, AttachmentStorePlan.Store)
        val pictureFirst = PlanPass.PictureSourcePass(0, root.id, "picture", 0,
            pictureSourceLocator = firstLocator, plannedCommandId = firstPlanned, aggregateId = aggregateId)
        val drawB = PlanPass.RenderPass(1, root.id, listOf(rootDraw(2)),
            AttachmentLoadPlan.Load, AttachmentStorePlan.Store)
        val pictureTerminal = PlanPass.PictureSourcePass(1, root.id, "picture", 1,
            pictureSourceLocator = secondLocator, plannedCommandId = secondPlanned, aggregateId = aggregateId)
        val aggregate = PictureStreamAggregateV1(
            aggregateId,
            PictureStreamExecutionModeV1.INLINE_CURRENT_TARGET,
            "picture",
            2,
            0,
            FramePlannedCommandIdI32(0),
            emptyList(),
            requireNotNull(LayerMappingF64.ofOrNull(Matrix3x3F64(), Point2I32.Origin)),
            org.graphiks.kanvas.render.ir.ClipStackNode.Empty,
            org.graphiks.kanvas.render.ir.ClipStackNode.Empty,
            RectI32(0, 0, 1, 1),
            RectI32(0, 0, 1, 1),
            root.id,
            null,
            null,
            null,
            PictureStreamRegionsV1(RectI32(0, 0, 1, 1), RectI32(0, 0, 1, 1),
                RectI32(0, 0, 1, 1), RectI32(0, 0, 1, 1)),
            listOf(
                PictureStreamEntryV1.Clear(PictureStreamEntryIdI32(0), firstLocator, firstPlanned,
                    ColorF32.of(0f, 0f, 0f, 0f), pictureFirst.id),
                PictureStreamEntryV1.Clear(PictureStreamEntryIdI32(1), secondLocator, secondPlanned,
                    ColorF32.of(0f, 0f, 0f, 0f), pictureTerminal.id),
            ),
            null,
            null,
            pictureTerminal.id,
            rootSourceCommandIndexI32 = 1,
        )

        val failure = assertFailsWith<W6bFilterGraphConstruction.ConstructionFailure> {
            validatePictureStreamAggregates(
                LayerFramePlanV1(emptyList(), emptyList(), listOf(aggregate)),
                listOf(root),
                listOf(drawA, pictureFirst, drawB, pictureTerminal),
                listOf(
                    PlanPassDependency(drawA.id, pictureFirst.id),
                    PlanPassDependency(pictureFirst.id, drawB.id),
                    PlanPassDependency(drawB.id, pictureTerminal.id),
                ),
            )
        }

        assertEquals(W6bFilterDiagnostics.PictureStreamInvalid, failure.diagnostic.code.value)
        assertTrue(failure.diagnostic.message.contains("Root source order"))
    }

    @Test
    fun `frozen Picture execution schedule rejects an omitted pass`() {
        val root = w6bWitnessTexture(PlanResourceRole.LogicalTarget, 0)
        val first = PlanPass.PictureSourcePass(0, root.id, "picture", 0)
        val second = PlanPass.PictureSourcePass(1, root.id, "picture", 1)
        val locator = PictureSourceLocatorV1(0, 0)
        val aggregate = PictureStreamAggregateV1(
            PictureStreamAggregateIdI32(0),
            PictureStreamExecutionModeV1.INLINE_CURRENT_TARGET,
            "picture", 1, 0, FramePlannedCommandIdI32(0), emptyList(),
            requireNotNull(LayerMappingF64.ofOrNull(Matrix3x3F64(), Point2I32.Origin)),
            org.graphiks.kanvas.render.ir.ClipStackNode.Empty,
            org.graphiks.kanvas.render.ir.ClipStackNode.Empty,
            RectI32(0, 0, 1, 1), RectI32(0, 0, 1, 1), root.id, null, null, null,
            PictureStreamRegionsV1(RectI32(0, 0, 1, 1), RectI32(0, 0, 1, 1),
                RectI32(0, 0, 1, 1), RectI32(0, 0, 1, 1)),
            listOf(PictureStreamEntryV1.Clear(PictureStreamEntryIdI32(0), locator,
                FramePlannedCommandIdI32(1), ColorF32.of(0f, 0f, 0f, 0f), first.id)),
            null, null, first.id,
            executionPassIds = listOf(first.id),
        )

        val failure = assertFailsWith<W6bFilterGraphConstruction.ConstructionFailure> {
            validatePictureStreamAggregates(
                LayerFramePlanV1(emptyList(), emptyList(), listOf(aggregate), frozenPassSchedule = listOf(first.id)),
                listOf(root), listOf(first, second), listOf(PlanPassDependency(first.id, second.id)),
            )
        }

        assertEquals(W6bFilterDiagnostics.PictureStreamInvalid, failure.diagnostic.code.value)
        assertTrue(failure.diagnostic.message.contains("Frozen execution schedule"))
    }

    @Test
    fun `picture aggregate publication rejects a layer descriptor without its real W6a scope`() {
        val root = w6bWitnessTexture(PlanResourceRole.LogicalTarget, 0)
        val layerTarget = w6bWitnessTexture(PlanResourceRole.LayerTarget, 0)
        val restore = LayerRestorePlanV1(
            1f,
            null,
            BlendPlan.SrcOver,
            readsPriorDevice = false,
            writesParentDevice = true,
            restoreAffectsTransparentBlack = false,
            parentVersionBefore = DestinationVersionI64(0),
            parentVersionAfter = DestinationVersionI64(1),
        )
        val terminal = PlanPass.LayerComposite(
            0,
            LayerScopeIdI32(0),
            layerTarget.id,
            root.id,
            RectI32(0, 0, 1, 1),
            Point2I32.Origin,
            restore,
            AttachmentLoadPlan.Load,
            AttachmentStorePlan.Store,
            DestinationVersionI64(1),
        )
        val locator = PictureSourceLocatorV1(0, 0)
        val aggregate = PictureStreamAggregateV1(
            PictureStreamAggregateIdI32(0),
            PictureStreamExecutionModeV1.INLINE_CURRENT_TARGET,
            "picture-source",
            2,
            0,
            FramePlannedCommandIdI32(0),
            emptyList(),
            requireNotNull(LayerMappingF64.ofOrNull(Matrix3x3F64(), Point2I32.Origin)),
            org.graphiks.kanvas.render.ir.ClipStackNode.Empty,
            org.graphiks.kanvas.render.ir.ClipStackNode.Empty,
            RectI32(0, 0, 1, 1),
            RectI32(0, 0, 1, 1),
            root.id,
            null,
            null,
            null,
            PictureStreamRegionsV1(RectI32(0, 0, 1, 1), RectI32(0, 0, 1, 1), RectI32(0, 0, 1, 1), RectI32(0, 0, 1, 1)),
            listOf(PictureStreamEntryV1.Layer(
                PictureStreamEntryIdI32(0),
                locator,
                FramePlannedCommandIdI32(1),
                1,
                LayerScopeIdI32(0),
                layerTarget.id,
                emptyList(),
                terminal.id,
            )),
            null,
            null,
            terminal.id,
        )

        val failure = assertFailsWith<W6bFilterGraphConstruction.ConstructionFailure> {
            validatePictureStreamAggregates(
                LayerFramePlanV1(emptyList(), emptyList(), listOf(aggregate)),
                listOf(root, layerTarget),
                listOf(terminal),
                emptyList(),
            )
        }
        assertEquals(W6bFilterDiagnostics.PictureStreamInvalid, failure.diagnostic.code.value)
        assertTrue(failure.diagnostic.message.contains("Layer entry references an absent W6a scope"))
    }

    @Test
    fun `path graphs reject resolve passes`() {
        assertFailsWith<IllegalArgumentException> {
            directPathGraphWithInterposedPass { resources ->
                PlanPass.ResolvePass(0, resources.target.id, resources.target.id)
            }
        }
    }

    @Test
    fun `path graphs reject duplicate staging and data resources`() {
        val resources = directPathResources()
        val duplicateStaging = frameBufferResource(
            role = PlanResourceRole.ReadbackStaging,
            ordinal = 1,
            byteSize = 256,
            usages = setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.MapRead),
            firstPassIndex = 1,
            lastPassIndexExclusive = 2,
        )
        val duplicateVertex = frameBufferResource(
            role = PlanResourceRole.VertexData,
            ordinal = 1,
            byteSize = 4,
            usages = setOf(PlanResourceUsage.Vertex, PlanResourceUsage.CopyDestination),
        )
        val duplicateIndex = frameBufferResource(
            role = PlanResourceRole.IndexData,
            ordinal = 1,
            byteSize = 4,
            usages = setOf(PlanResourceUsage.Index, PlanResourceUsage.CopyDestination),
        )
        val duplicateUniform = frameBufferResource(
            role = PlanResourceRole.UniformData,
            ordinal = 1,
            byteSize = 4,
            usages = setOf(PlanResourceUsage.Uniform, PlanResourceUsage.CopyDestination),
        )

        assertFailsWith<IllegalArgumentException> {
            directPathGraph(
                resources = resources,
                graphResources = resources.all + listOf(
                    duplicateStaging,
                    duplicateVertex,
                    duplicateIndex,
                    duplicateUniform,
                ),
            )
        }
    }

    @Test
    fun `path readback requires a readback staging resource`() {
        val resources = directPathResources()
        val wrongStaging = frameBufferResource(
            role = PlanResourceRole.VertexData,
            ordinal = 1,
            byteSize = 256,
            usages = setOf(
                PlanResourceUsage.Vertex,
                PlanResourceUsage.CopyDestination,
                PlanResourceUsage.MapRead,
            ),
            firstPassIndex = 1,
            lastPassIndexExclusive = 2,
        )
        val render = directRenderPass(
            ordinal = 0,
            target = resources.target.id,
            draws = listOf(directDraw(0)),
            load = AttachmentLoadPlan.ClearTransparent,
            drawDataResources = directDrawDataResources(resources),
        )
        val readback = PlanPass.ReadbackPass(0, resources.target.id, wrongStaging.id, 256)

        assertFailsWith<IllegalArgumentException> {
            graphOf(
                resources = resources.all.filterNot { it.id == resources.staging.id } + wrongStaging,
                passes = listOf(render, readback),
                dependencies = listOf(PlanPassDependency(render.id, readback.id)),
                visualCommandCount = 1,
                capabilities = w4cCapabilities(),
            )
        }
    }

    @Test
    fun `path graphs reject staging aliased with vertex data`() {
        val resources = directPathResources()
        val aliasedVertex = frameBufferResource(
            role = PlanResourceRole.VertexData,
            ordinal = 0,
            byteSize = 256,
            usages = setOf(
                PlanResourceUsage.Vertex,
                PlanResourceUsage.CopyDestination,
                PlanResourceUsage.MapRead,
            ),
        )
        val bindings = PlanDrawDataResources(
            aliasedVertex.id,
            resources.index.id,
            resources.uniform.id,
        )
        val render = directRenderPass(
            ordinal = 0,
            target = resources.target.id,
            draws = listOf(directDraw(0)),
            load = AttachmentLoadPlan.ClearTransparent,
            drawDataResources = bindings,
        )
        val readback = PlanPass.ReadbackPass(0, resources.target.id, aliasedVertex.id, 256)

        assertFailsWith<IllegalArgumentException> {
            graphOf(
                resources = listOf(resources.target, aliasedVertex, resources.index, resources.uniform),
                passes = listOf(render, readback),
                dependencies = listOf(PlanPassDependency(render.id, readback.id)),
                visualCommandCount = 1,
                capabilities = w4cCapabilities(),
            )
        }
    }

    @Test
    fun `path graphs share one vertex index uniform triplet`() {
        val resources = directPathResources(passCount = 3, readbackPassIndex = 2)
        val alternateVertex = frameBufferResource(
            role = PlanResourceRole.VertexData,
            ordinal = 1,
            byteSize = 4,
            usages = setOf(PlanResourceUsage.Vertex, PlanResourceUsage.CopyDestination),
            lastPassIndexExclusive = 3,
        )
        val alternateIndex = frameBufferResource(
            role = PlanResourceRole.IndexData,
            ordinal = 1,
            byteSize = 4,
            usages = setOf(PlanResourceUsage.Index, PlanResourceUsage.CopyDestination),
            lastPassIndexExclusive = 3,
        )
        val alternateUniform = frameBufferResource(
            role = PlanResourceRole.UniformData,
            ordinal = 1,
            byteSize = 4,
            usages = setOf(PlanResourceUsage.Uniform, PlanResourceUsage.CopyDestination),
            lastPassIndexExclusive = 3,
        )
        val first = directRenderPass(
            ordinal = 0,
            target = resources.target.id,
            draws = listOf(directDraw(0)),
            load = AttachmentLoadPlan.ClearTransparent,
            drawDataResources = directDrawDataResources(resources),
        )
        val second = directRenderPass(
            ordinal = 1,
            target = resources.target.id,
            draws = listOf(directDraw(1)),
            load = AttachmentLoadPlan.Load,
            drawDataResources = PlanDrawDataResources(
                alternateVertex.id,
                alternateIndex.id,
                alternateUniform.id,
            ),
        )
        val readback = directReadback(resources)

        assertFailsWith<IllegalArgumentException> {
            graphOf(
                resources = resources.all + listOf(alternateVertex, alternateIndex, alternateUniform),
                passes = listOf(first, second, readback),
                dependencies = listOf(
                    PlanPassDependency(first.id, second.id),
                    PlanPassDependency(second.id, readback.id),
                ),
                visualCommandCount = 2,
                capabilities = w4cCapabilities(),
            )
        }
    }

    @Test
    fun `W4d stroke draw snapshots its immutable geometry and scissor`() {
        val sourceScissor = RectI32(0, 0, 1, 1)
        val draw = PathStrokeDraw.of(
            commandIndex = 2,
            color = ColorF32.of(0.25f, 0.5f, 0.75f, 1f),
            geometryF32 = directStrokeAndFillGeometry(),
            scissorI32 = sourceScissor,
        )
        sourceScissor.left = 99

        assertEquals(RectI32(0, 0, 1, 1), draw.copyScissorI32())
        assertEquals(FillRule.WINDING, draw.copyGeometryF32().copyFillGeometryF32().fillRule)
        assertEquals(PathFillStrategy.DirectTriangle, draw.strategy)
        assertEquals(CoveragePlan.FullOrScissor, draw.coverage)
        assertEquals(SamplePlan.SingleSample, draw.sample)
        assertEquals(BlendPlan.SrcOver, draw.blend)
        assertFailsWith<IllegalArgumentException> {
            PathStrokeDraw.of(-1, draw.color, draw.copyGeometryF32(), RectI32(0, 0, 1, 1))
        }
        assertFailsWith<IllegalArgumentException> {
            PathStrokeDraw.of(0, draw.color, draw.copyGeometryF32(), RectI32(0, 0, 0, 1))
        }
    }

    @Test
    fun `W4d graph preserves mixed fill and stroke direct paint order`() {
        val resources = directPathResources(passCount = 3, readbackPassIndex = 2)
        val first = directRenderPass(
            ordinal = 0,
            target = resources.target.id,
            draws = listOf(directDraw(0)),
            load = AttachmentLoadPlan.ClearTransparent,
            drawDataResources = directDrawDataResources(resources),
        )
        val second = directRenderPass(
            ordinal = 1,
            target = resources.target.id,
            draws = listOf(directStrokeDraw(1)),
            load = AttachmentLoadPlan.Load,
            drawDataResources = directDrawDataResources(resources),
        )
        val readback = directReadback(resources)

        val graph = graphOf(
            resources = resources.all,
            passes = listOf(first, second, readback),
            dependencies = listOf(
                PlanPassDependency(first.id, second.id),
                PlanPassDependency(second.id, readback.id),
            ),
            visualCommandCount = 2,
            capabilities = w4cCapabilities(),
        )

        assertEquals(0, graph.passes().filterIsInstance<PlanPass.RenderPass>().first().draws().single().commandIndex)
        assertEquals(1, graph.passes().filterIsInstance<PlanPass.RenderPass>()[1].draws().single().commandIndex)
        assertIs<PathFillDraw>(graph.passes().filterIsInstance<PlanPass.RenderPass>().first().draws().single())
        assertIs<PathStrokeDraw>(graph.passes().filterIsInstance<PlanPass.RenderPass>()[1].draws().single())
    }

    @Test
    fun `W4d stroke and fill uses one adjacent stencil producer cover group`() {
        val resources = atomicResources()
        val draw = stencilStrokeAndFillDraw(0)
        val pair = atomicPasses(resources, draw)
        val graph = atomicGraph(resources, pair)

        assertSame(draw, pair.producer.draw)
        assertSame(draw, pair.cover.draw)
        assertEquals(PlanAtomicGroupId("w4d:0"), pair.producer.atomicGroup)
        assertEquals(pair.producer.atomicGroup, pair.cover.atomicGroup)
        assertEquals(pair.producer.id, graph.dependencies().first().before)
        assertEquals(pair.cover.id, graph.dependencies().first().after)
        assertEquals(1, graph.resources().count { it.format == PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8) })
    }

    @Test
    fun `W4d graph rejects incoherent stroke atomic geometry and resources`() {
        val resources = atomicResources()
        val draw = stencilStrokeAndFillDraw(0)

        assertFailsWith<IllegalArgumentException> {
            atomicGraph(
                resources,
                atomicPasses(resources, draw, coverDraw = directStrokeDraw(0)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            atomicGraph(
                resources,
                atomicPasses(
                    resources,
                    draw,
                    coverGroup = PlanAtomicGroupId("w4c:0"),
                ),
            )
        }
    }

    @Test
    fun `AA4 capability snapshot records exact sample and resolve support`() {
        val capabilities = aa4Capabilities()

        assertTrue(
            capabilities.supportsTexture(
                PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
                4,
                setOf(PlanResourceUsage.RenderAttachment),
            ),
        )
        assertTrue(
            capabilities.supportsTexture(
                PlanTextureFormat.CoverageMask,
                1,
                setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled),
            ),
        )
        assertTrue(
            capabilities.supportsResolve(
                PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
                4,
                1,
            ),
        )
        assertEquals(64, checkedTextureBytesI64(4, 2, 2, 4))
        assertFailsWith<IllegalArgumentException> {
            PlanTextureResolveSupport.of(
                PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
                1,
                4,
            )
        }
    }

    @Test
    fun `AA4 graph orders hard mask work and resolves only from final color pass`() {
        val graph = aa4MixedPathGraph()
        val passes = graph.passes()
        val colorTarget = graph.resources().single { it.role == PlanResourceRole.MultisampleColorTarget }
        val resolveTarget = graph.resources().single { it.role == PlanResourceRole.LogicalTarget }
        val mask = graph.resources().single { it.role == PlanResourceRole.PathHardEdgeMask }
        val hardDepth = graph.resources().single { it.role == PlanResourceRole.PathHardEdgeDepthStencil }
        val aaProducer = assertIs<PlanPass.PathRenderPass>(passes[0])
        val aaCover = assertIs<PlanPass.PathRenderPass>(passes[1])
        val maskClear = assertIs<PlanPass.PathMaskClearPass>(passes[2])
        val producer = assertIs<PlanPass.PathRenderPass>(passes[3])
        val cover = assertIs<PlanPass.PathRenderPass>(passes[4])
        val binaryCover = assertIs<PlanPass.PathRenderPass>(passes[5])
        val binaryDraw = assertIs<BinaryMaskedPathDraw>(binaryCover.draw)

        assertEquals(4, colorTarget.sampleCountI32)
        assertEquals(1, resolveTarget.sampleCountI32)
        assertEquals(1, mask.sampleCountI32)
        assertEquals(1, hardDepth.sampleCountI32)
        assertEquals(PathRenderPhase.MultisampleStencilProducer, aaProducer.phase)
        assertEquals(PathRenderPhase.MultisampleStencilColorCover, aaCover.phase)
        assertSame(aaProducer.draw, aaCover.draw)
        assertEquals(aaProducer.drawDataResources, aaCover.drawDataResources)
        assertEquals(aaProducer.target, aaCover.target)
        assertEquals(aaProducer.depthStencil, aaCover.depthStencil)
        assertEquals(aaProducer.atomicGroup, aaCover.atomicGroup)
        assertEquals(null, aaProducer.resolveTarget)
        assertEquals(null, aaCover.resolveTarget)
        assertEquals(mask.id, maskClear.target)
        assertEquals(PathRenderPhase.HardEdgeMaskStencilProducer, producer.phase)
        assertEquals(PathRenderPhase.HardEdgeMaskStencilCover, cover.phase)
        assertEquals(PathRenderPhase.HardEdgeBinaryColorCover, binaryCover.phase)
        assertEquals(producer.atomicGroup, cover.atomicGroup)
        assertEquals(producer.atomicGroup, binaryCover.atomicGroup)
        assertEquals(colorTarget.id, binaryCover.target)
        assertEquals(resolveTarget.id, binaryCover.resolveTarget)
        assertEquals(mask.id, binaryDraw.mask)
        assertEquals(BinaryMaskFetchPlan.TextureLoadUnfiltered, binaryDraw.maskFetch)
        assertEquals(4, binaryDraw.broadcastSampleCountI32)
        assertEquals(2, mask.firstPassIndex)
        assertEquals(6, mask.lastPassIndexExclusive)
        assertTrue(passes.dropLast(1).filterIsInstance<PlanPass.PathRenderPass>().dropLast(1).all {
            it.resolveTarget == null
        })
    }

    @Test
    fun `AA4 graph rejects a nonfinal or missing resolve and incompatible masks`() {
        assertFailsWith<IllegalArgumentException> {
            aa4MixedPathGraph(resolveOnFinalColor = false)
        }
        assertFailsWith<IllegalArgumentException> {
            aa4MixedPathGraph(resolveOnMaskProducer = true)
        }
        assertFailsWith<IllegalArgumentException> {
            PlanResource.of(
                role = PlanResourceRole.PathHardEdgeMask,
                ordinal = 0,
                kind = PlanResourceKind.Texture2D,
                format = PlanTextureFormat.CoverageMask,
                extent = SizeI32(1, 1),
                byteSize = 4,
                usages = setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled),
                lifetime = PlanResourceLifetime.FrameLocal,
                firstPassIndex = 0,
                lastPassIndexExclusive = 1,
                sampleCountI32 = 4,
            )
        }
    }

    @Test
    fun `explicit hard-only direct graph remains single-sample and never resolves`() {
        val graph = hardOnlyDirectPathGraph()
        val pathPass = assertIs<PlanPass.PathRenderPass>(graph.passes().first())

        assertEquals(PathRenderPhase.SingleSampleDirectColor, pathPass.phase)
        assertEquals(SamplePlan.SingleSample, pathPass.draw.sample)
        assertEquals(PlanResourceRole.LogicalTarget, graph.resources().single { it.id == pathPass.target }.role)
        assertEquals(null, pathPass.resolveTarget)
        assertTrue(graph.resources().none { it.role == PlanResourceRole.MultisampleColorTarget })
        assertTrue(graph.resources().none { it.role == PlanResourceRole.PathHardEdgeMask })
    }

    @Test
    fun `explicit hard-only stencil graph keeps adjacent atomic producer and color cover`() {
        val graph = hardOnlyStencilPathGraph()
        val producer = assertIs<PlanPass.PathRenderPass>(graph.passes()[0])
        val cover = assertIs<PlanPass.PathRenderPass>(graph.passes()[1])

        assertEquals(PathRenderPhase.SingleSampleStencilProducer, producer.phase)
        assertEquals(PathRenderPhase.SingleSampleStencilColorCover, cover.phase)
        assertSame(producer.draw, cover.draw)
        assertEquals(producer.drawDataResources, cover.drawDataResources)
        assertEquals(producer.target, cover.target)
        assertEquals(producer.depthStencil, cover.depthStencil)
        assertEquals(producer.atomicGroup, cover.atomicGroup)
        assertEquals(null, producer.resolveTarget)
        assertEquals(null, cover.resolveTarget)
        assertEquals(PlanPassDependency(producer.id, cover.id), graph.dependencies().first())
    }

    @Test
    fun `explicit resolved target requires render attachment and copy source support`() {
        assertFailsWith<IllegalArgumentException> {
            aa4MixedPathGraph(resolvedColorUsages = setOf(PlanResourceUsage.CopySource))
        }
    }

    @Test
    fun `legacy render passes reject general path draw bypasses`() {
        val legacyGeneralPath = GeneralPathDraw.of(
            commandIndex = 0,
            color = ColorF32.of(0.5f, 0f, 0f, 0.5f),
            geometry = PathDrawGeometry.Fill(directGeometry()),
            strategy = PathFillStrategy.DirectTriangle,
            scissorI32 = RectI32(0, 0, 1, 1),
            coverage = CoveragePlan.FullOrScissor,
            sample = SamplePlan.SingleSample,
        )

        assertFailsWith<IllegalArgumentException> {
            directPathGraph(draws = listOf(legacyGeneralPath))
        }
    }

    @Test
    fun `standalone resolve passes are rejected globally`() {
        val target = targetResource(lastPassIndexExclusive = 3)
        val staging = PlanResource.of(
            PlanResourceRole.ReadbackStaging,
            0,
            PlanResourceKind.Buffer,
            null,
            null,
            256,
            setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.MapRead),
            PlanResourceLifetime.FrameLocal,
            2,
            3,
        )
        val render = renderPass(target)
        val resolve = PlanPass.ResolvePass(0, target.id, target.id)
        val readback = PlanPass.ReadbackPass(0, target.id, staging.id, 256)

        assertFailsWith<IllegalArgumentException> {
            validGraph(
                resources = listOf(target, staging),
                passes = listOf(render, resolve, readback),
                dependencies = listOf(
                    PlanPassDependency(render.id, resolve.id),
                    PlanPassDependency(resolve.id, readback.id),
                ),
                peakFrameLocalBytes = 260,
            )
        }
    }

    @Test
    fun `legacy W4c D24S8 resource rejects a four-sample mutant`() {
        val resources = atomicResources(depthStencilSampleCountI32 = 4)

        assertFailsWith<IllegalArgumentException> {
            atomicGraph(resources, capabilities = aa4Capabilities())
        }
    }

    @Test
    fun `sequential hard masks have disjoint lifetimes and pooled peak excludes their sum`() {
        val graph = aa4SequentialHardMaskGraph()
        val masks = graph.resources().filter { it.role == PlanResourceRole.PathHardEdgeMask }.sortedBy { it.ordinal }

        assertEquals(2, masks.size)
        assertEquals(masks[0].lastPassIndexExclusive, masks[1].firstPassIndex)
        assertEquals(6_156, graph.peakFrameLocalBytes)
    }

    @Test
    fun `ClipMask graph preserves linear coverage resources and ordered ping pong folds`() {
        val graph = clipMaskGraph()
        val passes = graph.passes()
        val initialize = assertIs<PlanPass.ClipMaskInitialize>(passes[0])
        val producer = assertIs<PlanPass.ClipMaskProducer>(passes[1])
        val fold = assertIs<PlanPass.ClipMaskFold>(passes[2])

        assertEquals(1f, initialize.clearCoverageF32)
        assertEquals(PlanTextureFormat.CoverageMask(PlanCoverageMaskFormat.RGBA8_UNORM_LINEAR),
            graph.resources().single { it.id == initialize.output }.format)
        assertEquals(1, producer.sampleCountI32)
        assertEquals(null, producer.resolveTarget)
        assertEquals(initialize.output, fold.previous)
        assertEquals(producer.target, fold.source)
        assertEquals(ClipCombineOperation.Intersect, fold.operation)
        assertTrue(graph.dependencies().contains(PlanPassDependency(producer.id, fold.id)))
    }

    @Test
    fun `ClipMask graph rejects fold aliases that sample a render attachment`() {
        val resources = clipMaskResources()
        val initialize = PlanPass.ClipMaskInitialize(0, resources.accumulatorA.id, RectI32(0, 0, 4, 4), 1f, CLIP_GROUP)
        val producer = PlanPass.ClipMaskProducer(
            0, resources.scratch.id, null, null, 1, clipRectGeometry(), CLIP_GROUP,
        )
        val aliased = PlanPass.ClipMaskFold(
            0, resources.accumulatorA.id, resources.scratch.id, resources.accumulatorA.id,
            ClipCombineOperation.Intersect, RectI32(0, 0, 4, 4), CLIP_GROUP,
        )

        assertFailsWith<IllegalArgumentException> {
            clipGraph(resources.all, listOf(initialize, producer, aliased))
        }
    }

    @Test
    fun `Inverse zero clip covers its finite domain without allocating a producer`() {
        val graph = inverseZeroClipGraph()
        val inverse = assertIs<ClipPlanStrategy.InverseMask>(
            assertIs<ClippedPlanDraw>(assertIs<PlanPass.RenderPass>(graph.passes()[1]).draws().single()).strategy,
        )

        assertEquals(InverseInteriorCoverageF32.Zero, inverse.geometryF32.interiorCoverageF32)
        assertEquals(RectI32(0, 0, 4, 4), inverse.geometryF32.copyDomainI32())
        assertEquals(1, graph.passes().filterIsInstance<PlanPass.ClipMaskInitialize>().size)
        assertTrue(graph.passes().none { it is PlanPass.ClipMaskProducer })
    }

    @Test
    fun `ClipMask AA4 producer uses separate multisample scratch resolve and D24S8`() {
        val graph = aa4ClipMaskGraph()
        val producer = graph.passes().filterIsInstance<PlanPass.ClipMaskProducer>().single()
        val resources = graph.resources().associateBy { it.id }

        assertEquals(4, producer.sampleCountI32)
        assertEquals(PlanResourceRole.CoverageMaskMultisampleScratch, resources.getValue(producer.target).role)
        assertEquals(PlanResourceRole.CoverageMaskScratch, resources.getValue(requireNotNull(producer.resolveTarget)).role)
        assertEquals(PlanResourceRole.CoverageMaskDepthStencil, resources.getValue(requireNotNull(producer.depthStencil)).role)
        assertTrue(graph.capabilities.supportsResolve(
            PlanTextureFormat.CoverageMask(PlanCoverageMaskFormat.RGBA8_UNORM_LINEAR), 4, 1,
        ))
    }

    @Test
    fun `ClipMask rejects an antialiased rounded-rectangle producer downgraded to one sample`() {
        val resources = clipMaskResources()
        val initialize = PlanPass.ClipMaskInitialize(0, resources.accumulatorA.id, RectI32(0, 0, 4, 4), 1f, CLIP_GROUP)
        val downgraded = PlanPass.ClipMaskProducer(
            0, resources.scratch.id, null, null, 1, clipRRectGeometry(), CLIP_GROUP, antiAlias = true,
        )
        val fold = PlanPass.ClipMaskFold(
            0, resources.accumulatorA.id, resources.scratch.id, resources.accumulatorB.id,
            ClipCombineOperation.Intersect, RectI32(0, 0, 4, 4), CLIP_GROUP,
        )
        val target = clipColorTarget(3, 4)
        val consumer = clipRenderPass(
            target,
            listOf(ClippedPlanDraw.of(clipSolidDraw(), ClipPlanStrategy.Mask(resources.accumulatorB.id))),
        )

        assertFailsWith<IllegalArgumentException> {
            clipGraph(resources.all + target, listOf(initialize, downgraded, fold, consumer))
        }
    }

    @Test
    fun `AA4 hard clip consumer keeps binary one sample coverage across four samples`() {
        val graph = aa4MixedPathGraph()
        val binary = assertIs<BinaryMaskedPathDraw>(
            assertIs<PlanPass.PathRenderPass>(graph.passes()[5]).draw,
        )
        val clipped = ClippedBinaryMaskedPathDraw.of(binary, ClipPlanStrategy.Mask(binary.mask))

        assertEquals(CoveragePlan.BinaryMaskCover4, clipped.coverage)
        assertEquals(SamplePlan.Multisample4, clipped.sample)
        assertEquals(BinaryMaskFetchPlan.TextureLoadUnfiltered, clipped.source.maskFetch)
        assertEquals(4, clipped.source.broadcastSampleCountI32)
        assertEquals(1, clipped.sourceMaskSampleCountI32)
    }

    @Test
    fun `nested clipped draw cannot hide an unproduced mask consumer`() {
        val draw = ClippedPlanDraw.of(
            ClippedPlanDraw.of(
                SolidRectDraw.of(
                    0, ColorF32.of(1f, 0f, 0f, 1f), RectI32(0, 0, 1, 1), RectI32(0, 0, 1, 1),
                ),
                ClipPlanStrategy.Mask(PlanResourceId("hidden-mask")),
            ),
            ClipPlanStrategy.Scissor(RectI32(0, 0, 1, 1)),
        )

        assertFailsWith<IllegalArgumentException> { directPathGraph(draws = listOf(draw)) }
    }

    @Test
    fun `nested clipped draw cannot hide an unproduced inverse mask consumer`() {
        val inverse = InversePathGeometryF32.of(InverseInteriorCoverageF32.Zero, RectI32(0, 0, 1, 1))
        val draw = ClippedPlanDraw.of(
            ClippedPlanDraw.of(
                SolidRectDraw.of(
                    0, ColorF32.of(1f, 0f, 0f, 1f), RectI32(0, 0, 1, 1), RectI32(0, 0, 1, 1),
                ),
                ClipPlanStrategy.InverseMask(inverse, PlanResourceId("hidden-inverse-mask")),
            ),
            ClipPlanStrategy.Scissor(RectI32(0, 0, 1, 1)),
        )

        assertFailsWith<IllegalArgumentException> { directPathGraph(draws = listOf(draw)) }
    }

    @Test
    fun `nested clipped W4c draw retains its underlying path contract`() {
        val draw = ClippedPlanDraw.of(
            ClippedPlanDraw.of(stencilDraw(0), ClipPlanStrategy.Scissor(RectI32(0, 0, 1, 1))),
            ClipPlanStrategy.Scissor(RectI32(0, 0, 1, 1)),
        )

        assertFailsWith<IllegalArgumentException> { directPathGraph(draws = listOf(draw)) }
    }

    @Test
    fun `nested clipped W4c direct draw preserves its valid underlying path contract`() {
        val draw = ClippedPlanDraw.of(
            ClippedPlanDraw.of(directDraw(0), ClipPlanStrategy.Scissor(RectI32(0, 0, 1, 1))),
            ClipPlanStrategy.Scissor(RectI32(0, 0, 1, 1)),
        )

        val graph = directPathGraph(draws = listOf(draw))

        assertEquals(1, graph.visualCommandCount)
        assertEquals(0, graph.passes().filterIsInstance<PlanPass.RenderPass>().single().draws().single().commandIndex)
    }

    @Test
    fun `AA4 hard consumer composes a binary one-sample mask with a folded clip accumulator`() {
        val graph = aa4MixedPathGraph(withClip = true)
        val consumer = assertIs<ClippedBinaryMaskedPathDraw>(
            assertIs<PlanPass.PathRenderPass>(graph.passes()[8]).draw,
        )
        val resources = graph.resources().associateBy { it.id }
        val scissor = assertIs<ClipPlanStrategy.Scissor>(consumer.clip)
        val stencil = assertIs<ClipPlanStrategy.Stencil>(requireNotNull(scissor.child))
        val mask = assertIs<ClipPlanStrategy.Mask>(requireNotNull(stencil.child))

        assertEquals(PlanResourceRole.PathHardEdgeMask, resources.getValue(consumer.source.mask).role)
        assertEquals(PlanResourceRole.CoverageMaskAccumulator,
            resources.getValue(mask.resource).role)
        assertEquals(PlanResourceRole.DepthStencil, resources.getValue(stencil.depthStencil).role)
        assertEquals(CoveragePlan.BinaryMaskCover4, consumer.coverage)
        assertEquals(1, consumer.sourceMaskSampleCountI32)
    }

    @Test
    fun `AA4 hard inverse clip broadcasts a one-sample binary source to every color sample`() {
        val graph = aa4MixedPathGraph(withClip = true, inverseClip = true)
        val consumer = assertIs<ClippedBinaryMaskedPathDraw>(
            assertIs<PlanPass.PathRenderPass>(graph.passes()[8]).draw,
        )
        val scissor = assertIs<ClipPlanStrategy.Scissor>(consumer.clip)
        val stencil = assertIs<ClipPlanStrategy.Stencil>(requireNotNull(scissor.child))
        val inverse = assertIs<ClipPlanStrategy.InverseMask>(requireNotNull(stencil.child))

        assertEquals(RectI32(0, 0, 1, 1), inverse.geometryF32.copyDomainI32())
        assertEquals(CoveragePlan.BinaryMaskCover4, consumer.coverage)
        assertEquals(SamplePlan.Multisample4, consumer.sample)
        assertEquals(1, consumer.sourceMaskSampleCountI32)
        assertEquals(4, consumer.source.broadcastSampleCountI32)
    }

    @Test
    fun `nested mask consumer requires a producer graph`() {
        val mask = clipCoverageResource(PlanResourceRole.CoverageMaskAccumulator, 0, 0, 1)
        val target = clipColorTarget(0, 1)
        val draw = ClippedPlanDraw.of(
            ClippedPlanDraw.of(clipSolidDraw(), ClipPlanStrategy.Mask(mask.id)),
            ClipPlanStrategy.Scissor(RectI32(0, 0, 4, 4)),
        )

        assertFailsWith<IllegalArgumentException> {
            clipGraph(
                resources = listOf(mask, target),
                passes = listOf(clipRenderPass(target, listOf(draw))),
            )
        }
    }

    @Test
    fun `clip mask producer requires a consumer`() {
        val masks = clipMaskResources(finalAccumulatorLastPassExclusive = 3)
        val target = clipColorTarget(3, 4)

        assertFailsWith<IllegalArgumentException> {
            clipGraph(
                resources = masks.all + target,
                passes = clipMaskPasses(masks) + clipRenderPass(target, emptyList()),
            )
        }
    }

    @Test
    fun `clip accumulator lifetime ends exactly at its final consumer`() {
        val masks = clipMaskResources(finalAccumulatorLastPassExclusive = 5)
        val target = clipColorTarget(3, 5)
        val draw = ClippedPlanDraw.of(clipSolidDraw(), ClipPlanStrategy.Mask(masks.accumulatorB.id))

        assertFailsWith<IllegalArgumentException> {
            clipGraph(
                resources = masks.all + target,
                passes = clipMaskPasses(masks) + listOf(
                    clipRenderPass(target, listOf(draw)),
                    PlanPass.RenderPass(
                        1, target.id, emptyList(), AttachmentLoadPlan.Load, AttachmentStorePlan.Store,
                    ),
                ),
            )
        }
    }

    @Test
    fun `clip stencil requires the depth stencil semantic role`() {
        assertFailsWith<IllegalArgumentException> {
            clipStencilGraph(
                clipStencilResource(role = PlanResourceRole.CoverageMaskDepthStencil),
            )
        }
    }

    @Test
    fun `depth stencil attachments require the D24S8 format`() {
        assertFailsWith<IllegalArgumentException> {
            PlanResource.of(
                role = PlanResourceRole.PathHardEdgeDepthStencil,
                ordinal = 0,
                kind = PlanResourceKind.Texture2D,
                format = PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
                extent = SizeI32(4, 4),
                byteSize = 64,
                usages = setOf(PlanResourceUsage.DepthStencilAttachment),
                lifetime = PlanResourceLifetime.FrameLocal,
                firstPassIndex = 0,
                lastPassIndexExclusive = 1,
            )
        }
    }

    @Test
    fun `clip stencil sample count matches its render pass`() {
        assertFailsWith<IllegalArgumentException> {
            clipStencilGraph(clipStencilResource(sampleCountI32 = 4))
        }
    }

    @Test
    fun `clip stencil extent matches its graph target`() {
        assertFailsWith<IllegalArgumentException> {
            clipStencilGraph(clipStencilResource(extent = SizeI32(2, 2)))
        }
    }

    @Test
    fun `clip stencil cannot alias its color output`() {
        assertFailsWith<IllegalArgumentException> {
            clipStencilGraph(
                stencil = clipStencilResource(),
                aliasesColorTarget = true,
            )
        }
    }

    @Test
    fun `clip mask writer requires a dependency to its consumer`() {
        val masks = clipMaskResources()
        val target = clipColorTarget(3, 4)
        val draw = ClippedPlanDraw.of(clipSolidDraw(), ClipPlanStrategy.Mask(masks.accumulatorB.id))
        val passes = clipMaskPasses(masks) + clipRenderPass(target, listOf(draw))

        assertFailsWith<IllegalArgumentException> {
            clipGraph(
                resources = masks.all + target,
                passes = passes,
                dependencies = listOf(
                    PlanPassDependency(passes[0].id, passes[1].id),
                    PlanPassDependency(passes[1].id, passes[2].id),
                ),
            )
        }
    }

    private val CLIP_GROUP: PlanAtomicGroupId = PlanAtomicGroupId("clip:0")

    private data class ClipMaskResources(
        val accumulatorA: PlanResource,
        val accumulatorB: PlanResource,
        val scratch: PlanResource,
        val all: List<PlanResource>,
    )

    private fun clipMaskResources(finalAccumulatorLastPassExclusive: Int = 4): ClipMaskResources {
        fun coverage(role: PlanResourceRole, ordinal: Int, first: Int, last: Int) = clipCoverageResource(
            role, ordinal, first, last,
        )
        val accumulatorA = coverage(PlanResourceRole.CoverageMaskAccumulator, 0, 0, 3)
        val accumulatorB = coverage(PlanResourceRole.CoverageMaskAccumulator, 1, 2, finalAccumulatorLastPassExclusive)
        val scratch = coverage(PlanResourceRole.CoverageMaskScratch, 0, 1, 3)
        return ClipMaskResources(accumulatorA, accumulatorB, scratch, listOf(accumulatorA, accumulatorB, scratch))
    }

    private fun clipCoverageResource(
        role: PlanResourceRole,
        ordinal: Int,
        firstPassIndex: Int,
        lastPassIndexExclusive: Int,
    ): PlanResource = PlanResource.of(
            role, ordinal, PlanResourceKind.Texture2D,
            PlanTextureFormat.CoverageMask(PlanCoverageMaskFormat.RGBA8_UNORM_LINEAR), SizeI32(4, 4), 64,
            setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled), PlanResourceLifetime.FrameLocal,
            firstPassIndex, lastPassIndexExclusive,
        )

    private fun clipRectGeometry(): ClipGeometryF32 = ClipGeometryF32.Rect(RectF32(0f, 0f, 4f, 4f))

    private fun clipRRectGeometry(): ClipGeometryF32 = ClipGeometryF32.RRect(
        org.graphiks.math.geometry.RRectF32.of(RectF32(0f, 0f, 4f, 4f), 1f),
    )

    private fun clipMaskPasses(resources: ClipMaskResources): List<PlanPass> = listOf(
        PlanPass.ClipMaskInitialize(0, resources.accumulatorA.id, RectI32(0, 0, 4, 4), 1f, CLIP_GROUP),
        PlanPass.ClipMaskProducer(0, resources.scratch.id, null, null, 1, clipRectGeometry(), CLIP_GROUP),
        PlanPass.ClipMaskFold(
            0, resources.accumulatorA.id, resources.scratch.id, resources.accumulatorB.id,
            ClipCombineOperation.Intersect, RectI32(0, 0, 4, 4), CLIP_GROUP,
        ),
    )

    private fun clipColorTarget(firstPassIndex: Int, lastPassIndexExclusive: Int): PlanResource = PlanResource.of(
        PlanResourceRole.LogicalTarget, 0, PlanResourceKind.Texture2D,
        PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), SizeI32(4, 4), 64,
        setOf(PlanResourceUsage.RenderAttachment), PlanResourceLifetime.FrameLocal,
        firstPassIndex, lastPassIndexExclusive,
    )

    private fun clipSolidDraw(): SolidRectDraw = SolidRectDraw.of(
        0, ColorF32.of(1f, 0f, 0f, 1f), RectI32(0, 0, 4, 4), RectI32(0, 0, 4, 4),
    )

    private fun clipRenderPass(target: PlanResource, draws: List<PlanDraw>): PlanPass.RenderPass = PlanPass.RenderPass(
        0, target.id, draws, AttachmentLoadPlan.ClearTransparent, AttachmentStorePlan.Store,
    )

    private fun clipStencilResource(
        role: PlanResourceRole = PlanResourceRole.DepthStencil,
        extent: SizeI32 = SizeI32(4, 4),
        sampleCountI32: Int = 1,
    ): PlanResource = PlanResource.of(
        role, 0, PlanResourceKind.Texture2D,
        PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8), extent,
        4L * extent.width * extent.height * sampleCountI32,
        setOf(PlanResourceUsage.DepthStencilAttachment), PlanResourceLifetime.FrameLocal,
        0, 1, sampleCountI32,
    )

    private fun clipStencilGraph(
        stencil: PlanResource,
        aliasesColorTarget: Boolean = false,
    ): RenderGraph {
        val target = clipColorTarget(0, 1)
        val stencilReference = if (aliasesColorTarget) target.id else stencil.id
        val draw = ClippedPlanDraw.of(clipSolidDraw(), ClipPlanStrategy.Stencil(stencilReference))
        return clipGraph(
            resources = listOf(target, stencil),
            passes = listOf(clipRenderPass(target, listOf(draw))),
            capabilities = aa4Capabilities(),
        )
    }

    private fun clipMaskGraph(): RenderGraph {
        val resources = clipMaskResources()
        val target = clipColorTarget(3, 4)
        val draw = ClippedPlanDraw.of(
            clipSolidDraw(),
            ClipPlanStrategy.Mask(resources.accumulatorB.id),
        )
        return clipGraph(resources.all + target, clipMaskPasses(resources) + clipRenderPass(target, listOf(draw)))
    }

    private fun aa4ClipMaskGraph(): RenderGraph {
        fun texture(
            role: PlanResourceRole,
            ordinal: Int,
            format: PlanTextureFormat,
            bytes: Long,
            usages: Set<PlanResourceUsage>,
            first: Int,
            last: Int,
            samples: Int,
        ) = PlanResource.of(role, ordinal, PlanResourceKind.Texture2D, format, SizeI32(4, 4), bytes, usages,
            PlanResourceLifetime.FrameLocal, first, last, samples)
        val format = PlanTextureFormat.CoverageMask(PlanCoverageMaskFormat.RGBA8_UNORM_LINEAR)
        val accumulatorA = texture(PlanResourceRole.CoverageMaskAccumulator, 0, format, 64,
            setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled), 0, 3, 1)
        val accumulatorB = texture(PlanResourceRole.CoverageMaskAccumulator, 1, format, 64,
            setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled), 2, 4, 1)
        val multisample = texture(PlanResourceRole.CoverageMaskMultisampleScratch, 0, format, 256,
            setOf(PlanResourceUsage.RenderAttachment), 1, 2, 4)
        val scratch = texture(PlanResourceRole.CoverageMaskScratch, 0, format, 64,
            setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled), 1, 3, 1)
        val depth = texture(PlanResourceRole.CoverageMaskDepthStencil, 0,
            PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8), 256,
            setOf(PlanResourceUsage.DepthStencilAttachment), 1, 2, 4)
        val initialize = PlanPass.ClipMaskInitialize(0, accumulatorA.id, RectI32(0, 0, 4, 4), 1f, CLIP_GROUP)
        val producer = PlanPass.ClipMaskProducer(0, multisample.id, scratch.id, depth.id, 4, clipRectGeometry(), CLIP_GROUP)
        val fold = PlanPass.ClipMaskFold(0, accumulatorA.id, scratch.id, accumulatorB.id,
            ClipCombineOperation.Difference, RectI32(0, 0, 4, 4), CLIP_GROUP)
        val target = texture(PlanResourceRole.LogicalTarget, 0,
            PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), 64,
            setOf(PlanResourceUsage.RenderAttachment), 3, 4, 1)
        val draw = ClippedPlanDraw.of(
            SolidRectDraw.of(0, ColorF32.of(1f, 0f, 0f, 1f), RectI32(0, 0, 4, 4), RectI32(0, 0, 4, 4)),
            ClipPlanStrategy.Mask(accumulatorB.id),
        )
        val render = PlanPass.RenderPass(0, target.id, listOf(draw), AttachmentLoadPlan.ClearTransparent, AttachmentStorePlan.Store)
        val resources = listOf(accumulatorA, accumulatorB, multisample, scratch, depth, target)
        val passes = listOf(initialize, producer, fold, render)
        return RenderGraph.of(
            PlanId("aa4-clip-mask"), "clip-mask", SizeI32(4, 4),
            PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL, aa4ClipCapabilities(), PlanBudget(2_048), 0,
            resources, passes, passes.zipWithNext().map { (before, after) -> PlanPassDependency(before.id, after.id) },
            peak(resources, passes.size),
        )
    }

    private fun inverseZeroClipGraph(): RenderGraph {
        val accumulator = PlanResource.of(
            PlanResourceRole.CoverageMaskAccumulator, 0, PlanResourceKind.Texture2D,
            PlanTextureFormat.CoverageMask(PlanCoverageMaskFormat.RGBA8_UNORM_LINEAR), SizeI32(4, 4), 64,
            setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled), PlanResourceLifetime.FrameLocal, 0, 2,
        )
        val target = PlanResource.of(
            PlanResourceRole.LogicalTarget, 0, PlanResourceKind.Texture2D,
            PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), SizeI32(4, 4), 64,
            setOf(PlanResourceUsage.RenderAttachment), PlanResourceLifetime.FrameLocal, 1, 2,
        )
        val initialize = PlanPass.ClipMaskInitialize(0, accumulator.id, RectI32(0, 0, 4, 4), 1f, CLIP_GROUP)
        val inverse = InversePathGeometryF32.of(InverseInteriorCoverageF32.Zero, RectI32(0, 0, 4, 4))
        val draw = ClippedPlanDraw.of(
            SolidRectDraw.of(0, ColorF32.of(1f, 0f, 0f, 1f), RectI32(0, 0, 4, 4), RectI32(0, 0, 4, 4)),
            ClipPlanStrategy.InverseMask(inverse, accumulator.id),
        )
        val render = PlanPass.RenderPass(0, target.id, listOf(draw), AttachmentLoadPlan.ClearTransparent, AttachmentStorePlan.Store)
        return RenderGraph.of(
            PlanId("inverse-zero-clip"), "clip-mask", SizeI32(4, 4),
            PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL, clipCapabilities(), PlanBudget(1_024), 1,
            listOf(accumulator, target), listOf(initialize, render), listOf(PlanPassDependency(initialize.id, render.id)),
            peak(listOf(accumulator, target), 2),
        )
    }

    private fun clipGraph(
        resources: List<PlanResource>,
        passes: List<PlanPass>,
        dependencies: List<PlanPassDependency> = passes.zipWithNext().map { (before, after) ->
            PlanPassDependency(before.id, after.id)
        },
        capabilities: PlanCapabilitySnapshot = clipCapabilities(),
    ): RenderGraph = RenderGraph.of(
        PlanId("clip-mask"), "clip-mask", SizeI32(4, 4),
        PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL, capabilities, PlanBudget(1_024), 0,
        resources, passes, dependencies,
        peak(resources, passes.size),
    )

    private fun clipCapabilities(): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
        deviceGeneration = 0,
        maxTextureDimension2D = 1024,
        maxBufferSizeBytes = 4096,
        copyBytesPerRowAlignment = 256,
        supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
        minUniformBufferOffsetAlignment = 256,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
        supportedOperations = setOf(PlanOperationCapability.RenderPass),
        bufferAllocationPolicy = PlanBufferAllocationPolicy.of(1_024, 1_024, 1_024),
        supportedTextureSampleSupports = setOf(
            PlanTextureSampleSupport.of(PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), 1,
                setOf(PlanResourceUsage.RenderAttachment)),
            PlanTextureSampleSupport.of(
                PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), 1,
                setOf(PlanResourceUsage.RenderAttachment),
            ),
            PlanTextureSampleSupport.of(
                PlanTextureFormat.CoverageMask(PlanCoverageMaskFormat.RGBA8_UNORM_LINEAR), 1,
                setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled),
            ),
        ),
    )

    private fun aa4ClipCapabilities(): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
        deviceGeneration = 0,
        maxTextureDimension2D = 1024,
        maxBufferSizeBytes = 4096,
        copyBytesPerRowAlignment = 256,
        supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
        minUniformBufferOffsetAlignment = 256,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
        supportedOperations = setOf(PlanOperationCapability.RenderPass, PlanOperationCapability.DepthStencilAttachment),
        bufferAllocationPolicy = PlanBufferAllocationPolicy.of(2_048, 2_048, 2_048),
        supportedDepthStencilFormats = setOf(PlanDepthStencilFormat.Depth24PlusStencil8),
        supportedTextureSampleSupports = setOf(
            PlanTextureSampleSupport.of(PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), 1,
                setOf(PlanResourceUsage.RenderAttachment)),
            PlanTextureSampleSupport.of(PlanTextureFormat.CoverageMask(PlanCoverageMaskFormat.RGBA8_UNORM_LINEAR), 1,
                setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled)),
            PlanTextureSampleSupport.of(PlanTextureFormat.CoverageMask(PlanCoverageMaskFormat.RGBA8_UNORM_LINEAR), 4,
                setOf(PlanResourceUsage.RenderAttachment)),
            PlanTextureSampleSupport.of(PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8), 4,
                setOf(PlanResourceUsage.DepthStencilAttachment)),
        ),
        supportedTextureResolveSupports = setOf(
            PlanTextureResolveSupport.of(PlanTextureFormat.CoverageMask(PlanCoverageMaskFormat.RGBA8_UNORM_LINEAR), 4, 1),
        ),
    )

    private fun aa4Capabilities(): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
        deviceGeneration = 0,
        maxTextureDimension2D = 1024,
        maxBufferSizeBytes = 4096,
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
        bufferAllocationPolicy = PlanBufferAllocationPolicy.of(16_384, 4_096, 4_096),
        supportedDepthStencilFormats = setOf(PlanDepthStencilFormat.Depth24PlusStencil8),
        supportedTextureSampleSupports = setOf(
            PlanTextureSampleSupport.of(
                PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
                1,
                setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource),
            ),
            PlanTextureSampleSupport.of(
                PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
                4,
                setOf(PlanResourceUsage.RenderAttachment),
            ),
            PlanTextureSampleSupport.of(
                PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8),
                4,
                setOf(PlanResourceUsage.DepthStencilAttachment),
            ),
            PlanTextureSampleSupport.of(
                PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8),
                1,
                setOf(PlanResourceUsage.DepthStencilAttachment),
            ),
            PlanTextureSampleSupport.of(
                PlanTextureFormat.CoverageMask,
                1,
                setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled),
            ),
        ),
        supportedTextureResolveSupports = setOf(
            PlanTextureResolveSupport.of(
                PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
                4,
                1,
            ),
        ),
    )

    private fun aa4MixedPathGraph(
        resolveOnFinalColor: Boolean = true,
        resolveOnMaskProducer: Boolean = false,
        withClip: Boolean = false,
        inverseClip: Boolean = false,
        resolvedColorUsages: Set<PlanResourceUsage> = setOf(
            PlanResourceUsage.RenderAttachment,
            PlanResourceUsage.CopySource,
        ),
    ): RenderGraph {
        val extent = SizeI32(1, 1)
        val clipPassCount = if (withClip) 3 else 0
        fun texture(
            role: PlanResourceRole,
            ordinal: Int,
            format: PlanTextureFormat,
            byteSize: Long,
            usages: Set<PlanResourceUsage>,
            firstPassIndex: Int,
            lastPassIndexExclusive: Int,
            sampleCountI32: Int,
        ) = PlanResource.of(
            role = role,
            ordinal = ordinal,
            kind = PlanResourceKind.Texture2D,
            format = format,
            extent = extent,
            byteSize = byteSize,
            usages = usages,
            lifetime = PlanResourceLifetime.FrameLocal,
            firstPassIndex = firstPassIndex,
            lastPassIndexExclusive = lastPassIndexExclusive,
            sampleCountI32 = sampleCountI32,
        )
        fun data(role: PlanResourceRole, usage: PlanResourceUsage) = PlanResource.of(
            role = role,
            ordinal = 0,
            kind = PlanResourceKind.Buffer,
            format = null,
            extent = null,
            byteSize = 4,
            usages = setOf(usage, PlanResourceUsage.CopyDestination),
            lifetime = PlanResourceLifetime.FrameLocal,
            firstPassIndex = clipPassCount,
            lastPassIndexExclusive = 6 + clipPassCount,
        )
        val multisampleColor = texture(
            PlanResourceRole.MultisampleColorTarget,
            0,
            PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
            16,
            setOf(PlanResourceUsage.RenderAttachment),
            clipPassCount,
            6 + clipPassCount,
            4,
        )
        val resolvedColor = texture(
            PlanResourceRole.LogicalTarget,
            0,
            PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
            4,
            resolvedColorUsages,
            5 + clipPassCount,
            7 + clipPassCount,
            1,
        )
        val multisampleDepth = PlanResource.of(
            PlanResourceRole.DepthStencil,
            0,
            PlanResourceKind.Texture2D,
            PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8),
            extent,
            16,
            setOf(PlanResourceUsage.DepthStencilAttachment),
            PlanResourceLifetime.FrameLocal,
            clipPassCount,
            if (withClip) 6 + clipPassCount else 2 + clipPassCount,
            4,
        )
        val mask = texture(
            PlanResourceRole.PathHardEdgeMask,
            0,
            PlanTextureFormat.CoverageMask,
            4,
            setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled),
            2 + clipPassCount,
            6 + clipPassCount,
            1,
        )
        val hardDepth = texture(
            PlanResourceRole.PathHardEdgeDepthStencil,
            0,
            PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8),
            4,
            setOf(PlanResourceUsage.DepthStencilAttachment),
            3 + clipPassCount,
            5 + clipPassCount,
            1,
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
            6 + clipPassCount,
            7 + clipPassCount,
        )
        val vertex = data(PlanResourceRole.VertexData, PlanResourceUsage.Vertex)
        val index = data(PlanResourceRole.IndexData, PlanResourceUsage.Index)
        val uniform = data(PlanResourceRole.UniformData, PlanResourceUsage.Uniform)
        val drawData = PlanDrawDataResources(vertex.id, index.id, uniform.id)
        val drawDataResources = listOf(vertex, index, uniform)
        val antiAliased = GeneralPathDraw.of(
            commandIndex = 0,
            color = ColorF32.of(0.2f, 0.3f, 0.4f, 1f),
            geometry = PathDrawGeometry.Fill(stencilGeometry()),
            strategy = PathFillStrategy.StencilCover,
            scissorI32 = RectI32(0, 0, 1, 1),
            coverage = CoveragePlan.StencilAA4,
            sample = SamplePlan.Multisample4,
        )
        val hard = GeneralPathDraw.of(
            commandIndex = 1,
            color = ColorF32.of(0.5f, 0f, 0f, 0.5f),
            geometry = PathDrawGeometry.Fill(stencilGeometry()),
            strategy = PathFillStrategy.StencilCover,
            scissorI32 = RectI32(0, 0, 1, 1),
            coverage = CoveragePlan.FullOrScissor,
            sample = SamplePlan.SingleSample,
        )
        val aaGroup = canonicalGeneralPathAtomicGroup(antiAliased)
        val group = canonicalGeneralPathAtomicGroup(hard)
        val clipAccumulatorA = texture(
            PlanResourceRole.CoverageMaskAccumulator, 0,
            PlanTextureFormat.CoverageMask(PlanCoverageMaskFormat.RGBA8_UNORM_LINEAR), 4,
            setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled), 0, 3, 1,
        )
        val clipAccumulatorB = texture(
            PlanResourceRole.CoverageMaskAccumulator, 1,
            PlanTextureFormat.CoverageMask(PlanCoverageMaskFormat.RGBA8_UNORM_LINEAR), 4,
            setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled), 2, if (withClip) 9 else 3, 1,
        )
        val clipScratch = texture(
            PlanResourceRole.CoverageMaskScratch, 0,
            PlanTextureFormat.CoverageMask(PlanCoverageMaskFormat.RGBA8_UNORM_LINEAR), 4,
            setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled), 1, 3, 1,
        )
        val clipPrefix = if (withClip) listOf(
            PlanPass.ClipMaskInitialize(0, clipAccumulatorA.id, RectI32(0, 0, 1, 1), 1f, CLIP_GROUP),
            PlanPass.ClipMaskProducer(0, clipScratch.id, null, null, 1,
                ClipGeometryF32.Rect(RectF32(0f, 0f, 1f, 1f)), CLIP_GROUP),
            PlanPass.ClipMaskFold(0, clipAccumulatorA.id, clipScratch.id, clipAccumulatorB.id,
                ClipCombineOperation.Intersect, RectI32(0, 0, 1, 1), CLIP_GROUP),
        ) else emptyList()
        val finalClip = if (inverseClip) {
            ClipPlanStrategy.InverseMask(
                InversePathGeometryF32.of(
                    InverseInteriorCoverageF32.Geometry.of(directGeometry()),
                    RectI32(0, 0, 1, 1),
                ),
                clipAccumulatorB.id,
            )
        } else {
            ClipPlanStrategy.Mask(clipAccumulatorB.id)
        }
        val binaryCoverDraw: PathRenderDraw = BinaryMaskedPathDraw.of(hard, mask.id).let { binary ->
            if (withClip) ClippedBinaryMaskedPathDraw.of(
                binary,
                ClipPlanStrategy.Scissor(
                    RectI32(0, 0, 1, 1),
                    ClipPlanStrategy.Stencil(
                        multisampleDepth.id,
                        finalClip,
                    ),
                ),
            ) else binary
        }
        val pathPasses = listOf(
            PlanPass.PathRenderPass(
                ordinal = 0,
                target = multisampleColor.id,
                draw = antiAliased,
                phase = PathRenderPhase.MultisampleStencilProducer,
                drawDataResources = drawData,
                atomicGroup = aaGroup,
                depthStencil = multisampleDepth.id,
                load = AttachmentLoadPlan.ClearTransparent,
                store = AttachmentStorePlan.Store,
                depthStencilAccess = PlanDepthStencilAccess.Write,
                depthStencilLoadStore = PlanDepthStencilLoadStore.ClearZeroStore,
                resolveTarget = null,
            ),
            PlanPass.PathRenderPass(
                ordinal = 1,
                target = multisampleColor.id,
                draw = antiAliased,
                phase = PathRenderPhase.MultisampleStencilColorCover,
                drawDataResources = drawData,
                atomicGroup = aaGroup,
                depthStencil = multisampleDepth.id,
                load = AttachmentLoadPlan.Load,
                store = AttachmentStorePlan.Store,
                depthStencilAccess = PlanDepthStencilAccess.ReadWrite,
                depthStencilLoadStore = PlanDepthStencilLoadStore.LoadStoreTestReset,
                resolveTarget = null,
            ),
            PlanPass.PathMaskClearPass(0, mask.id, group),
            PlanPass.PathRenderPass(
                ordinal = 2,
                target = mask.id,
                draw = hard,
                phase = PathRenderPhase.HardEdgeMaskStencilProducer,
                drawDataResources = drawData,
                atomicGroup = group,
                depthStencil = hardDepth.id,
                load = AttachmentLoadPlan.Load,
                store = AttachmentStorePlan.Store,
                depthStencilAccess = PlanDepthStencilAccess.Write,
                depthStencilLoadStore = PlanDepthStencilLoadStore.ClearZeroStore,
                resolveTarget = if (resolveOnMaskProducer) resolvedColor.id else null,
            ),
            PlanPass.PathRenderPass(
                ordinal = 3,
                target = mask.id,
                draw = hard,
                phase = PathRenderPhase.HardEdgeMaskStencilCover,
                drawDataResources = drawData,
                atomicGroup = group,
                depthStencil = hardDepth.id,
                load = AttachmentLoadPlan.Load,
                store = AttachmentStorePlan.Store,
                depthStencilAccess = PlanDepthStencilAccess.ReadWrite,
                depthStencilLoadStore = PlanDepthStencilLoadStore.LoadStoreTestReset,
                resolveTarget = null,
            ),
            PlanPass.PathRenderPass(
                ordinal = 4,
                target = multisampleColor.id,
                draw = binaryCoverDraw,
                phase = PathRenderPhase.HardEdgeBinaryColorCover,
                drawDataResources = drawData,
                atomicGroup = group,
                depthStencil = null,
                load = AttachmentLoadPlan.Load,
                store = AttachmentStorePlan.Store,
                depthStencilAccess = null,
                depthStencilLoadStore = null,
                resolveTarget = if (resolveOnFinalColor) resolvedColor.id else null,
            ),
            PlanPass.ReadbackPass(0, resolvedColor.id, staging.id, 256),
        )
        val passes = clipPrefix + pathPasses
        val resources = listOf(
            multisampleColor,
            resolvedColor,
            multisampleDepth,
            mask,
            hardDepth,
            staging,
        ) + drawDataResources + if (withClip) listOf(clipAccumulatorA, clipAccumulatorB, clipScratch) else emptyList()
        return RenderGraph.of(
            id = PlanId("aa4-plan"),
            capabilityId = "w4d-general-path-aa",
            targetExtent = extent,
            colorFormat = PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL,
            capabilities = aa4Capabilities(),
            budget = PlanBudget(4096),
            visualCommandCount = 2,
            resources = resources,
            passes = passes,
            dependencies = passes.zipWithNext().map { (before, after) -> PlanPassDependency(before.id, after.id) },
            peakFrameLocalBytes = peak(resources, passes.size),
        )
    }

    private fun hardOnlyDirectPathGraph(): RenderGraph = hardOnlyPathGraph(stencil = false)

    private fun hardOnlyStencilPathGraph(): RenderGraph = hardOnlyPathGraph(stencil = true)

    private fun hardOnlyPathGraph(stencil: Boolean): RenderGraph {
        val passCount = if (stencil) 3 else 2
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
            passCount - 1,
        )
        val vertex = data(PlanResourceRole.VertexData, PlanResourceUsage.Vertex)
        val index = data(PlanResourceRole.IndexData, PlanResourceUsage.Index)
        val uniform = data(PlanResourceRole.UniformData, PlanResourceUsage.Uniform)
        val drawData = PlanDrawDataResources(vertex.id, index.id, uniform.id)
        val draw = GeneralPathDraw.of(
            commandIndex = 0,
            color = ColorF32.of(0.5f, 0f, 0f, 0.5f),
            geometry = PathDrawGeometry.Fill(if (stencil) stencilGeometry() else directGeometry()),
            strategy = if (stencil) PathFillStrategy.StencilCover else PathFillStrategy.DirectTriangle,
            scissorI32 = RectI32(0, 0, 1, 1),
            coverage = CoveragePlan.FullOrScissor,
            sample = SamplePlan.SingleSample,
        )
        val depth = if (stencil) {
            PlanResource.of(
                PlanResourceRole.DepthStencil,
                0,
                PlanResourceKind.Texture2D,
                PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8),
                SizeI32(1, 1),
                4,
                setOf(PlanResourceUsage.DepthStencilAttachment),
                PlanResourceLifetime.FrameLocal,
                0,
                2,
            )
        } else {
            null
        }
        val pathPasses = if (stencil) {
            val group = canonicalGeneralPathAtomicGroup(draw)
            listOf(
                PlanPass.PathRenderPass(
                    0, target.id, draw, PathRenderPhase.SingleSampleStencilProducer, drawData, group,
                    requireNotNull(depth).id, AttachmentLoadPlan.ClearTransparent, AttachmentStorePlan.Store,
                    PlanDepthStencilAccess.Write, PlanDepthStencilLoadStore.ClearZeroStore, null,
                ),
                PlanPass.PathRenderPass(
                    1, target.id, draw, PathRenderPhase.SingleSampleStencilColorCover, drawData, group,
                    depth.id, AttachmentLoadPlan.Load, AttachmentStorePlan.Store,
                    PlanDepthStencilAccess.ReadWrite, PlanDepthStencilLoadStore.LoadStoreTestReset, null,
                ),
            )
        } else {
            listOf(
                PlanPass.PathRenderPass(
                    0, target.id, draw, PathRenderPhase.SingleSampleDirectColor, drawData, null, null,
                    AttachmentLoadPlan.ClearTransparent, AttachmentStorePlan.Store, null, null, null,
                ),
            )
        }
        val readback = PlanPass.ReadbackPass(0, target.id, staging.id, 256)
        val passes = pathPasses + readback
        val resources = listOfNotNull(target, staging, vertex, index, uniform, depth)
        return RenderGraph.of(
            PlanId(if (stencil) "hard-stencil" else "hard-direct"),
            "w4d-general-path-aa",
            SizeI32(1, 1),
            PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL,
            aa4Capabilities(),
            PlanBudget(4096),
            1,
            resources,
            passes,
            passes.zipWithNext().map { (before, after) -> PlanPassDependency(before.id, after.id) },
            peak(resources, passes.size),
        )
    }

    private fun aa4SequentialHardMaskGraph(): RenderGraph {
        val extent = SizeI32(16, 16)
        fun texture(
            role: PlanResourceRole,
            ordinal: Int,
            format: PlanTextureFormat,
            bytes: Long,
            usages: Set<PlanResourceUsage>,
            first: Int,
            last: Int,
            samples: Int,
        ) = PlanResource.of(
            role, ordinal, PlanResourceKind.Texture2D, format, extent, bytes, usages,
            PlanResourceLifetime.FrameLocal, first, last, samples,
        )
        fun data(role: PlanResourceRole, usage: PlanResourceUsage) = PlanResource.of(
            role, 0, PlanResourceKind.Buffer, null, null, 4,
            setOf(usage, PlanResourceUsage.CopyDestination), PlanResourceLifetime.FrameLocal, 0, 7,
        )
        val multisampleColor = texture(
            PlanResourceRole.MultisampleColorTarget, 0,
            PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), 4_096,
            setOf(PlanResourceUsage.RenderAttachment), 0, 7, 4,
        )
        val resolved = texture(
            PlanResourceRole.LogicalTarget, 0,
            PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), 1_024,
            setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource), 6, 8, 1,
        )
        val firstMask = texture(
            PlanResourceRole.PathHardEdgeMask, 0, PlanTextureFormat.CoverageMask, 1_024,
            setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled), 1, 4, 1,
        )
        val secondMask = texture(
            PlanResourceRole.PathHardEdgeMask, 1, PlanTextureFormat.CoverageMask, 1_024,
            setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled), 4, 7, 1,
        )
        val staging = PlanResource.of(
            PlanResourceRole.ReadbackStaging, 0, PlanResourceKind.Buffer, null, null, 4_096,
            setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.MapRead), PlanResourceLifetime.FrameLocal, 7, 8,
        )
        val vertex = data(PlanResourceRole.VertexData, PlanResourceUsage.Vertex)
        val index = data(PlanResourceRole.IndexData, PlanResourceUsage.Index)
        val uniform = data(PlanResourceRole.UniformData, PlanResourceUsage.Uniform)
        val drawData = PlanDrawDataResources(vertex.id, index.id, uniform.id)
        fun path(commandIndex: Int, coverage: CoveragePlan, sample: SamplePlan) = GeneralPathDraw.of(
            commandIndex, ColorF32.of(0.5f, 0f, 0f, 0.5f), PathDrawGeometry.Fill(directGeometry()),
            PathFillStrategy.DirectTriangle, RectI32(0, 0, 16, 16), coverage, sample,
        )
        val aa = path(0, CoveragePlan.StencilAA4, SamplePlan.Multisample4)
        val firstHard = path(1, CoveragePlan.FullOrScissor, SamplePlan.SingleSample)
        val secondHard = path(2, CoveragePlan.FullOrScissor, SamplePlan.SingleSample)
        fun directMaskProducer(ordinal: Int, mask: PlanResource, draw: GeneralPathDraw): PlanPass.PathRenderPass =
            PlanPass.PathRenderPass(
                ordinal, mask.id, draw, PathRenderPhase.HardEdgeMaskProducer, drawData,
                canonicalGeneralPathAtomicGroup(draw), null, AttachmentLoadPlan.Load, AttachmentStorePlan.Store,
                null, null, null,
            )
        fun binaryCover(
            ordinal: Int,
            mask: PlanResource,
            draw: GeneralPathDraw,
            resolveTarget: PlanResourceId?,
        ): PlanPass.PathRenderPass = PlanPass.PathRenderPass(
            ordinal, multisampleColor.id, BinaryMaskedPathDraw.of(draw, mask.id), PathRenderPhase.HardEdgeBinaryColorCover,
            drawData, canonicalGeneralPathAtomicGroup(draw), null, AttachmentLoadPlan.Load, AttachmentStorePlan.Store,
            null, null, resolveTarget,
        )
        val passes = listOf(
            PlanPass.PathRenderPass(
                0, multisampleColor.id, aa, PathRenderPhase.MultisampleDirectColor, drawData, null, null,
                AttachmentLoadPlan.ClearTransparent, AttachmentStorePlan.Store, null, null, null,
            ),
            PlanPass.PathMaskClearPass(0, firstMask.id, canonicalGeneralPathAtomicGroup(firstHard)),
            directMaskProducer(1, firstMask, firstHard),
            binaryCover(2, firstMask, firstHard, null),
            PlanPass.PathMaskClearPass(1, secondMask.id, canonicalGeneralPathAtomicGroup(secondHard)),
            directMaskProducer(3, secondMask, secondHard),
            binaryCover(4, secondMask, secondHard, resolved.id),
            PlanPass.ReadbackPass(0, resolved.id, staging.id, 256),
        )
        val resources = listOf(multisampleColor, resolved, firstMask, secondMask, staging, vertex, index, uniform)
        return RenderGraph.of(
            PlanId("aa4-sequential-hard-masks"), "w4d-general-path-aa", extent,
            PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL, aa4Capabilities(), PlanBudget(16_384), 3,
            resources, passes, passes.zipWithNext().map { (before, after) -> PlanPassDependency(before.id, after.id) },
            peak(resources, passes.size),
        )
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

    private data class DirectPathResources(
        val target: PlanResource,
        val staging: PlanResource,
        val vertex: PlanResource,
        val index: PlanResource,
        val uniform: PlanResource,
        val all: List<PlanResource>,
    )

    private fun directPathResources(
        passCount: Int = 2,
        readbackPassIndex: Int = passCount - 1,
        dataLastPassExclusive: Int = passCount,
    ): DirectPathResources {
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
            readbackPassIndex,
            readbackPassIndex + 1,
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
            dataLastPassExclusive,
        )
        val vertex = data(PlanResourceRole.VertexData, PlanResourceUsage.Vertex)
        val index = data(PlanResourceRole.IndexData, PlanResourceUsage.Index)
        val uniform = data(PlanResourceRole.UniformData, PlanResourceUsage.Uniform)
        return DirectPathResources(
            target,
            staging,
            vertex,
            index,
            uniform,
            listOf(target, staging, vertex, index, uniform),
        )
    }

    private fun directDrawDataResources(resources: DirectPathResources): PlanDrawDataResources = PlanDrawDataResources(
        resources.vertex.id,
        resources.index.id,
        resources.uniform.id,
    )

    private fun directRenderPass(
        ordinal: Int,
        target: PlanResourceId,
        draws: List<PlanDraw>,
        load: AttachmentLoadPlan,
        drawDataResources: PlanDrawDataResources?,
    ): PlanPass.RenderPass = PlanPass.RenderPass(
        ordinal,
        target,
        draws,
        load,
        AttachmentStorePlan.Store,
        drawDataResources,
    )

    private fun directReadback(resources: DirectPathResources): PlanPass.ReadbackPass =
        PlanPass.ReadbackPass(0, resources.target.id, resources.staging.id, 256)

    private fun directPathGraph(
        resources: DirectPathResources = directPathResources(),
        graphResources: List<PlanResource> = resources.all,
        draws: List<PlanDraw> = listOf(directDraw(0)),
        drawDataResources: PlanDrawDataResources? = directDrawDataResources(resources),
        visualCommandCount: Int = draws.size,
        capabilities: PlanCapabilitySnapshot = w4cCapabilities(),
        peakFrameLocalBytes: Long = peak(graphResources, 2),
    ): RenderGraph {
        val render = directRenderPass(
            ordinal = 0,
            target = resources.target.id,
            draws = draws,
            load = AttachmentLoadPlan.ClearTransparent,
            drawDataResources = drawDataResources,
        )
        val readback = directReadback(resources)
        return graphOf(
            resources = graphResources,
            passes = listOf(render, readback),
            dependencies = listOf(PlanPassDependency(render.id, readback.id)),
            visualCommandCount = visualCommandCount,
            capabilities = capabilities,
            peakFrameLocalBytes = peakFrameLocalBytes,
        )
    }

    private fun directPathGraphWithInterposedPass(
        visualCommandCount: Int = 1,
        intermediate: (DirectPathResources) -> PlanPass,
    ): RenderGraph {
        val resources = directPathResources(passCount = 3, readbackPassIndex = 2)
        val render = directRenderPass(
            ordinal = 0,
            target = resources.target.id,
            draws = listOf(directDraw(0)),
            load = AttachmentLoadPlan.ClearTransparent,
            drawDataResources = directDrawDataResources(resources),
        )
        val middle = intermediate(resources)
        val readback = directReadback(resources)
        return graphOf(
            resources = resources.all,
            passes = listOf(render, middle, readback),
            dependencies = listOf(
                PlanPassDependency(render.id, middle.id),
                PlanPassDependency(middle.id, readback.id),
            ),
            visualCommandCount = visualCommandCount,
            capabilities = w4cCapabilities(),
        )
    }

    private fun frameBufferResource(
        role: PlanResourceRole,
        ordinal: Int,
        byteSize: Long,
        usages: Set<PlanResourceUsage>,
        firstPassIndex: Int = 0,
        lastPassIndexExclusive: Int = 2,
    ): PlanResource = PlanResource.of(
        role,
        ordinal,
        PlanResourceKind.Buffer,
        null,
        null,
        byteSize,
        usages,
        PlanResourceLifetime.FrameLocal,
        firstPassIndex,
        lastPassIndexExclusive,
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

    /** Exercises W6a publication, including its immutable W6b occurrence witness. */
    private fun w6bFilterPublicationGraph(
        boundSource: PlanResourceId = planResourceId(PlanResourceRole.FilterSource, 0),
        verticalInput: PlanResourceId = planResourceId(PlanResourceRole.FilterTarget, 0),
        consumeTerminalOutput: Boolean = true,
        firstAxis: FilterAxisV1 = FilterAxisV1.X,
        verticalUsesDifferentKey: Boolean = false,
        sourceRole: PlanResourceRole = PlanResourceRole.FilterSource,
        compositeIntermediateOutput: Boolean = false,
    ): RenderGraph {
        val passCountI32 = when {
            compositeIntermediateOutput && consumeTerminalOutput -> 7
            compositeIntermediateOutput -> 6
            consumeTerminalOutput -> 6
            else -> 5
        }
        val root = PlanResource.of(
            PlanResourceRole.LogicalTarget, 0, PlanResourceKind.Texture2D,
            PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), SizeI32(1, 1), 4,
            setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource, PlanResourceUsage.Sampled),
            PlanResourceLifetime.FrameLocal, 0, passCountI32,
        )
        fun filterTexture(role: PlanResourceRole, ordinal: Int) = PlanResource.of(
            role, ordinal, PlanResourceKind.Texture2D,
            PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), SizeI32(1, 1), 4,
            setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled),
            PlanResourceLifetime.FrameLocal, 0, passCountI32,
        )
        val source = filterTexture(sourceRole, 0)
        val horizontal = filterTexture(PlanResourceRole.FilterTarget, 0)
        val vertical = filterTexture(PlanResourceRole.FilterTarget, 1)
        val staging = PlanResource.of(
            PlanResourceRole.ReadbackStaging, 0, PlanResourceKind.Buffer, null, null, 256,
            setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.MapRead),
            PlanResourceLifetime.FrameLocal, 0, passCountI32,
        )
        val deviceBounds = RectI32(0, 0, 1, 1)
        val mapping = requireNotNull(LayerMappingF64.ofOrNull(Matrix3x3F64(), Point2I32.Origin))
        val actualBoundSource = if (sourceRole == PlanResourceRole.FilterSource) boundSource else source.id
        val key = FilterEvaluationKeyV1.of(CapturedFilterNodeId(0), actualBoundSource, mapping, deviceBounds)
        val verticalKey = if (verticalUsesDifferentKey)
            FilterEvaluationKeyV1.of(CapturedFilterNodeId(0), actualBoundSource, mapping, deviceBounds) else key
        val bounds = FilterBoundsPlanV1(deviceBounds, deviceBounds, deviceBounds, deviceBounds, Point2I32.Origin)
        val passes = buildList<PlanPass> {
            add(PlanPass.RenderPass(0, root.id, emptyList(), AttachmentLoadPlan.ClearTransparent, AttachmentStorePlan.Store,
                destinationVersionAfter = DestinationVersionI64(0)))
            add(PlanPass.PictureSourcePass(1, source.id, "captured-picture-source", 0))
            add(PlanPass.FilterPass(2, listOf(source.id), horizontal.id, key,
                FilterPassOperationV1.SeparableBlur(if (firstAxis == FilterAxisV1.X)
                    FilterImplementationKindV1.IMAGE_BLUR_X else FilterImplementationKindV1.IMAGE_BLUR_Y,
                    1f, firstAxis,
                    org.graphiks.kanvas.render.ir.TileMode.CLAMP, bounds)))
            if (compositeIntermediateOutput) add(PlanPass.FilterComposite(3, horizontal.id, root.id, key,
                deviceBounds, Point2I32.Origin, FilterCompositeOperationV1.Draw(BlendPlan.SrcOver),
                destinationVersionAfter = DestinationVersionI64(1)))
            add(PlanPass.FilterPass(if (compositeIntermediateOutput) 4 else 3, listOf(verticalInput), vertical.id, verticalKey,
                FilterPassOperationV1.SeparableBlur(FilterImplementationKindV1.IMAGE_BLUR_Y, 1f, FilterAxisV1.Y,
                    org.graphiks.kanvas.render.ir.TileMode.CLAMP, bounds)))
            if (consumeTerminalOutput) add(PlanPass.FilterComposite(if (compositeIntermediateOutput) 5 else 4, vertical.id, root.id, verticalKey,
                deviceBounds, Point2I32.Origin, FilterCompositeOperationV1.Draw(BlendPlan.SrcOver),
                destinationVersionAfter = DestinationVersionI64(if (compositeIntermediateOutput) 2 else 1)))
            add(PlanPass.ReadbackPass(when {
                compositeIntermediateOutput && consumeTerminalOutput -> 6
                compositeIntermediateOutput -> 5
                consumeTerminalOutput -> 5
                else -> 4
            }, root.id, staging.id, 256, mappedBytesI64 = 4))
        }
        val resources = listOf(root, source, horizontal, vertical, staging)
        val budget = PlanBudget(4_096)
        return RenderGraph.of(
            PlanId("w6b-publication"), W6aLayerPlanCompiler.CAPABILITY_ID, SizeI32(1, 1),
            PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL,
            supportedCapabilities(setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL)), budget, 0,
            resources, passes, passes.zipWithNext { before, after -> PlanPassDependency(before.id, after.id) },
            W6aLayerPlanBudget.peak(resources, passes.size, budget),
        )
    }

    /** A real sealed W6b fragment whose mask terminal is consumed by a W5 Picture source. */
    private fun w6bPictureCoverageWitnessFragment(): Pair<List<PlanResource>, List<PlanPass>> {
        val coverage = w6bWitnessTexture(PlanResourceRole.CoverageSource, 0)
        val horizontal = w6bWitnessTexture(PlanResourceRole.FilterTarget, 0)
        val vertical = w6bWitnessTexture(PlanResourceRole.FilterTarget, 1)
        val pictureSource = w6bWitnessTexture(PlanResourceRole.FilterSource, 0)
        val scene = SceneSnapshot.of(SceneExtent(1, 1), ColorSpace.SRGB, emptyList())
        val occurrence = FilterOccurrenceSourceV1(scene, 0, null, emptyList(), LayerDescriptor.of())
        val bounds = RectI32(0, 0, 1, 1)
        val mapping = requireNotNull(LayerMappingF64.ofOrNull(Matrix3x3F64(), Point2I32.Origin))
        val key = FilterEvaluationKeyV1.forMaskOccurrence(0, coverage.id, mapping, bounds)
        val filterBounds = FilterBoundsPlanV1(bounds, bounds, bounds, bounds, Point2I32.Origin)
        val passes = listOf<PlanPass>(
            PlanPass.FilterCoverageSourcePass(0, coverage.id, occurrence),
            PlanPass.FilterPass(1, listOf(coverage.id), horizontal.id, key,
                FilterPassOperationV1.SeparableBlur(FilterImplementationKindV1.MASK_COVERAGE_BLUR_X, 1f,
                    FilterAxisV1.X, org.graphiks.kanvas.render.ir.TileMode.CLAMP, filterBounds)),
            PlanPass.FilterPass(2, listOf(horizontal.id), vertical.id, key,
                FilterPassOperationV1.SeparableBlur(FilterImplementationKindV1.MASK_COVERAGE_BLUR_Y, 1f,
                    FilterAxisV1.Y, org.graphiks.kanvas.render.ir.TileMode.CLAMP, filterBounds)),
            PlanPass.PictureSourcePass(3, pictureSource.id, scene.canonicalId.value, 0, occurrence, vertical.id),
        )
        return listOf(coverage, horizontal, vertical, pictureSource) to passes
    }

    private fun w6bWitnessTexture(role: PlanResourceRole, ordinal: Int): PlanResource = PlanResource.of(
        role, ordinal, PlanResourceKind.Texture2D,
        PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), SizeI32(1, 1), 4,
        setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled),
        PlanResourceLifetime.FrameLocal, 0, 4,
    )

    private fun w6bDropShadowPublicationGraph(
        mode: org.graphiks.kanvas.render.ir.CapturedDropShadowModeV1 = org.graphiks.kanvas.render.ir.CapturedDropShadowModeV1.COMPOSITE,
        wrongOriginal: Boolean = false,
        includeOriginal: Boolean = mode == org.graphiks.kanvas.render.ir.CapturedDropShadowModeV1.COMPOSITE,
        wrongColorizeInput: Boolean = false,
    ): RenderGraph {
        val root = PlanResource.of(
            PlanResourceRole.LogicalTarget, 0, PlanResourceKind.Texture2D,
            PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), SizeI32(1, 1), 4,
            setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource, PlanResourceUsage.Sampled),
            PlanResourceLifetime.FrameLocal, 0, 9,
        )
        fun filterTexture(role: PlanResourceRole, ordinal: Int) = PlanResource.of(
            role, ordinal, PlanResourceKind.Texture2D,
            PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), SizeI32(1, 1), 4,
            setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled),
            PlanResourceLifetime.FrameLocal, 0, 9,
        )
        val source = filterTexture(PlanResourceRole.FilterSource, 0)
        val wrong = filterTexture(PlanResourceRole.FilterSource, 1)
        val horizontal = filterTexture(PlanResourceRole.FilterTarget, 0)
        val vertical = filterTexture(PlanResourceRole.FilterTarget, 1)
        val colorized = filterTexture(PlanResourceRole.FilterTarget, 2)
        val terminal = filterTexture(PlanResourceRole.FilterTarget, 3)
        val staging = PlanResource.of(
            PlanResourceRole.ReadbackStaging, 0, PlanResourceKind.Buffer, null, null, 256,
            setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.MapRead), PlanResourceLifetime.FrameLocal, 0, 9,
        )
        val deviceBounds = RectI32(0, 0, 1, 1)
        val mapping = requireNotNull(LayerMappingF64.ofOrNull(Matrix3x3F64(), Point2I32.Origin))
        val key = FilterEvaluationKeyV1.of(CapturedFilterNodeId(0), source.id, mapping, deviceBounds)
        val bounds = FilterBoundsPlanV1(deviceBounds, deviceBounds, deviceBounds, deviceBounds, Point2I32.Origin)
        val inputs = buildList {
            add(colorized.id)
            if (includeOriginal) add(if (wrongOriginal) wrong.id else source.id)
        }
        val passes = listOf<PlanPass>(
            PlanPass.RenderPass(0, root.id, emptyList(), AttachmentLoadPlan.ClearTransparent, AttachmentStorePlan.Store,
                destinationVersionAfter = DestinationVersionI64(0)),
            PlanPass.PictureSourcePass(1, source.id, "shadow-source", 0),
            PlanPass.PictureSourcePass(2, wrong.id, "wrong-shadow-source", 1),
            PlanPass.FilterPass(3, listOf(source.id), horizontal.id, key,
                FilterPassOperationV1.SeparableBlur(FilterImplementationKindV1.IMAGE_BLUR_X, 1f,
                    FilterAxisV1.X, org.graphiks.kanvas.render.ir.TileMode.CLAMP, bounds)),
            PlanPass.FilterPass(4, listOf(horizontal.id), vertical.id, key,
                FilterPassOperationV1.SeparableBlur(FilterImplementationKindV1.IMAGE_BLUR_Y, 1f,
                    FilterAxisV1.Y, org.graphiks.kanvas.render.ir.TileMode.CLAMP, bounds)),
            PlanPass.FilterPass(5, listOf(if (wrongColorizeInput) source.id else vertical.id), colorized.id, key,
                FilterPassOperationV1.DropShadowColorize(org.graphiks.math.color.ColorARGB.of(255, 1, 2, 3),
                    org.graphiks.math.vector.Vector2F64(0.0, 0.0), bounds)),
            PlanPass.FilterPass(6, inputs, terminal.id, key, FilterPassOperationV1.DropShadowComposite(
                mode, if (includeOriginal) source.id else null, bounds)),
            PlanPass.FilterComposite(7, terminal.id, root.id, key, deviceBounds, Point2I32.Origin,
                FilterCompositeOperationV1.Draw(BlendPlan.SrcOver), destinationVersionAfter = DestinationVersionI64(1)),
            PlanPass.ReadbackPass(8, root.id, staging.id, 256),
        )
        val resources = listOf(root, source, wrong, horizontal, vertical, colorized, terminal, staging)
        val budget = PlanBudget(4_096)
        return RenderGraph.of(
            PlanId("w6b-shadow-publication"), W6aLayerPlanCompiler.CAPABILITY_ID, SizeI32(1, 1),
            PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL,
            supportedCapabilities(setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL)), budget, 0,
            resources, passes, passes.zipWithNext { before, after -> PlanPassDependency(before.id, after.id) },
            W6aLayerPlanBudget.peak(resources, passes.size, budget),
        )
    }

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
        dataLastPassExclusive: Int = passCount,
        duplicateDepthStencil: Boolean = false,
        depthStencilSampleCountI32: Int = 1,
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
            dataLastPassExclusive,
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
            4L * depthStencilSampleCountI32,
            setOf(PlanResourceUsage.DepthStencilAttachment),
            PlanResourceLifetime.FrameLocal,
            0,
            depthStencilLastPassExclusive,
            depthStencilSampleCountI32,
        )
        val duplicate = if (duplicateDepthStencil) {
            listOf(
                PlanResource.of(
                    PlanResourceRole.DepthStencil,
                    1,
                    PlanResourceKind.Texture2D,
                    PlanTextureFormat.DepthStencil(PlanDepthStencilFormat.Depth24PlusStencil8),
                    SizeI32(1, 1),
                    4L * depthStencilSampleCountI32,
                    setOf(PlanResourceUsage.DepthStencilAttachment),
                    PlanResourceLifetime.FrameLocal,
                    0,
                    passCount,
                    depthStencilSampleCountI32,
                ),
            )
        } else {
            emptyList()
        }
        return AtomicResources(target, staging, vertex, index, uniform, depthStencil, listOf(target, staging, vertex, index, uniform, depthStencil) + duplicate)
    }

    private fun atomicPasses(
        resources: AtomicResources,
        producerDraw: PathDraw,
        coverDraw: PathDraw = producerDraw,
        coverTarget: PlanResourceId = resources.target.id,
        producerOrdinal: Int = 0,
        coverOrdinal: Int = producerOrdinal,
        producerGroup: PlanAtomicGroupId = canonicalPathAtomicGroup(producerDraw),
        coverGroup: PlanAtomicGroupId = producerGroup,
        producerLoad: AttachmentLoadPlan = AttachmentLoadPlan.ClearTransparent,
        coverLoad: AttachmentLoadPlan = AttachmentLoadPlan.Load,
        producerDepthStencilLoadStore: PlanDepthStencilLoadStore = PlanDepthStencilLoadStore.ClearZeroStore,
        coverDepthStencilLoadStore: PlanDepthStencilLoadStore = PlanDepthStencilLoadStore.LoadStoreTestReset,
        producerDepthStencilAccess: PlanDepthStencilAccess = PlanDepthStencilAccess.Write,
        coverDepthStencilAccess: PlanDepthStencilAccess = PlanDepthStencilAccess.ReadWrite,
        coverDrawDataResources: PlanDrawDataResources? = null,
    ): AtomicPassPair {
        val drawDataResources = PlanDrawDataResources(resources.vertex.id, resources.index.id, resources.uniform.id)
        return AtomicPassPair(
            PlanPass.StencilProducer(
                ordinal = producerOrdinal,
                target = resources.target.id,
                depthStencil = resources.depthStencil.id,
                draw = producerDraw,
                drawDataResources = drawDataResources,
                atomicGroup = producerGroup,
                load = producerLoad,
                store = AttachmentStorePlan.Store,
                depthStencilAccess = producerDepthStencilAccess,
                depthStencilLoadStore = producerDepthStencilLoadStore,
            ),
            PlanPass.StencilCover(
                ordinal = coverOrdinal,
                target = coverTarget,
                depthStencil = resources.depthStencil.id,
                draw = coverDraw,
                drawDataResources = coverDrawDataResources ?: drawDataResources,
                atomicGroup = coverGroup,
                load = coverLoad,
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
        peakFrameLocalBytes: Long = peak(resources, passes.size),
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
        peakFrameLocalBytes = peakFrameLocalBytes,
    )

    private fun w4cCapabilities(
        supportedOperations: Set<PlanOperationCapability> = setOf(
            PlanOperationCapability.RenderPass,
            PlanOperationCapability.CopyUpload,
            PlanOperationCapability.UniformBuffer,
            PlanOperationCapability.Readback,
            PlanOperationCapability.DepthStencilAttachment,
            PlanOperationCapability.StencilCover,
        ),
    ): PlanCapabilitySnapshot = supportedCapabilities(
            formats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
            supportedDepthStencilFormats = setOf(PlanDepthStencilFormat.Depth24PlusStencil8),
            supportedOperations = supportedOperations,
        )

    private fun atomicCapabilities(): PlanCapabilitySnapshot = w4cCapabilities()

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

    private fun directDraw(commandIndex: Int): PathFillDraw = PathFillDraw.of(
        commandIndex = commandIndex,
        color = ColorF32.of(0.5f, 0f, 0f, 0.5f),
        geometryF32 = directGeometry(),
        strategy = PathFillStrategy.DirectTriangle,
        scissorI32 = RectI32(0, 0, 1, 1),
    )

    private fun directGeometry(): PathFillGeometryF32 = assertIs<PathFillPreparationResult.Ready>(
        preparePathFillGeometryF32(
            PathFillInputF64.of(
                FillRule.WINDING,
                listOf(
                    PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.0)),
                    PathFillSegmentF64.LineTo(Point2F64(1.0, 0.0)),
                    PathFillSegmentF64.LineTo(Point2F64(0.0, 1.0)),
                    PathFillSegmentF64.Close,
                ),
            ),
        ),
    ).geometryF32

    private fun directStrokeDraw(commandIndex: Int): PathStrokeDraw = PathStrokeDraw.of(
        commandIndex = commandIndex,
        color = ColorF32.of(0.5f, 0f, 0f, 0.5f),
        geometryF32 = directStrokeAndFillGeometry(),
        scissorI32 = RectI32(0, 0, 1, 1),
    )

    private fun stencilStrokeAndFillDraw(commandIndex: Int): PathStrokeDraw = PathStrokeDraw.of(
        commandIndex = commandIndex,
        color = ColorF32.of(0.5f, 0f, 0f, 0.5f),
        geometryF32 = stencilStrokeAndFillGeometry(),
        scissorI32 = RectI32(0, 0, 1, 1),
    )

    private fun directStrokeAndFillGeometry(): PathStrokeGeometryF32 = readyStrokeGeometry(
        inputF64 = PathFillInputF64.of(
            FillRule.WINDING,
            listOf(
                PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.0)),
                PathFillSegmentF64.LineTo(Point2F64(1.0, 0.0)),
                PathFillSegmentF64.LineTo(Point2F64(0.0, 1.0)),
                PathFillSegmentF64.Close,
            ),
        ),
        widthF64 = 0.0,
    )

    private fun stencilStrokeAndFillGeometry(): PathStrokeGeometryF32 = readyStrokeGeometry(
        inputF64 = PathFillInputF64.of(
            FillRule.WINDING,
            listOf(
                PathFillSegmentF64.MoveTo(Point2F64(0.0, 0.0)),
                PathFillSegmentF64.LineTo(Point2F64(1.0, 0.0)),
                PathFillSegmentF64.LineTo(Point2F64(1.0, 1.0)),
                PathFillSegmentF64.LineTo(Point2F64(0.0, 1.0)),
                PathFillSegmentF64.Close,
            ),
        ),
        widthF64 = 0.25,
    )

    private fun readyStrokeGeometry(
        inputF64: PathFillInputF64,
        widthF64: Double,
    ): PathStrokeGeometryF32 = assertIs<PathStrokePreparationResult.Ready>(
        prepareProjectedPathStrokeGeometryF32(
            inputF64 = inputF64,
            styleF64 = PathStrokeStyleF64(
                widthF64 = PathStrokeWidthF64.Finite(widthF64),
                cap = PathStrokeCap.Butt,
                join = PathStrokeJoin.Miter,
                miterLimitF64 = 4.0,
            ),
            mode = PathStrokeDrawMode.StrokeAndFill,
            projectionF64 = object : PathStrokeProjectionF64 {
                override fun projectPointF64(pointF64: Point2F64): PathStrokeProjectionPointResultF64 =
                    PathStrokeProjectionPointResultF64.Ready(pointF64)

                override fun certifyOutlineIntervalF64(intervalF64: org.graphiks.math.geometry.PathStrokeOutlineIntervalF64): PathStrokeProjectionIntervalResultF64 =
                    PathStrokeProjectionIntervalResultF64.Bounded(intervalF64.sourceSagittaUpperBoundF64)
            },
            deviceFillSegmentMapperF64 = PathStrokeDeviceFillSegmentMapperF64 { it },
        ),
    ).geometryF32

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
