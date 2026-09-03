package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.color.ColorF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame

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
                PlanPass.RenderPass(1, targetResource().id, emptyList(), AttachmentLoadPlan.ClearTransparent, AttachmentStorePlan.Store),
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
            PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL, SizeI32(1, 1), 4,
            setOf(PlanResourceUsage.RenderAttachment), PlanResourceLifetime.FrameLocal, 0, 1)
        val second = PlanResource.of(PlanResourceRole.LogicalTarget, 1, PlanResourceKind.Texture2D,
            PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL, SizeI32(1, 1), 4,
            setOf(PlanResourceUsage.RenderAttachment), PlanResourceLifetime.FrameLocal, 0, 1)
        assertEquals(first.id, PlanResource.of(PlanResourceRole.LogicalTarget, 0, PlanResourceKind.Texture2D,
            PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL, SizeI32(1, 1), 4,
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
            PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL, SizeI32(1, 1), 4,
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

    private fun supportedCapabilities(
        formats: Set<PlanLogicalColorFormat>,
        copyBytesPerRowAlignment: Int = 256,
    ) = PlanCapabilitySnapshot.of(
        deviceGeneration = 0,
        maxTextureDimension2D = 1024,
        maxBufferSizeBytes = 4096,
        copyBytesPerRowAlignment = copyBytesPerRowAlignment,
        supportedFormats = formats,
        minUniformBufferOffsetAlignment = 256,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
        supportedOperations = setOf(PlanOperationCapability.RenderPass, PlanOperationCapability.Readback),
        bufferAllocationPolicy = PlanBufferAllocationPolicy.of(16_384, 4_096, 4_096),
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
        PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL, extent, byteSize,
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
}
