package org.graphiks.kanvas.gpu.plan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.color.ColorSpace
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
import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity
import org.graphiks.kanvas.render.ir.RenderPlanResult
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneExtent
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.kanvas.render.ir.StrokeCapNode
import org.graphiks.kanvas.render.ir.StrokeJoinNode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32

class CapabilityCompilerChainTest {
    @Test
    fun `chain chooses first candidate and plans only that opaque candidate`() {
        val chain = CapabilityCompilerChain.of(
            listOf(NotCandidateCompiler("first.gap"), W3SolidRectPlanCompiler()),
        )
        val selected = assertIs<GpuPlanSelection.Candidate>(chain.select(scene(), target()))
        val graph = assertIs<RenderPlanResult.Ready<RenderGraph>>(
            chain.plan(selected.candidate, capabilities(), PlanBudget(1L shl 20)),
        ).plan

        assertEquals(W3SolidRectPlanCompiler.CAPABILITY_ID, graph.capabilityId)
    }

    @Test
    fun `all gaps remain ordered and require no physical snapshot`() {
        val result = CapabilityCompilerChain.of(
            listOf(NotCandidateCompiler("a"), NotCandidateCompiler("b")),
        )
            .select(scene(), target())

        assertEquals(listOf("a", "b"), assertIs<GpuPlanSelection.NotCandidate>(result).diagnostics().map { it.code.value })
    }

    @Test
    fun `invalid selection stops the chain`() {
        val result = CapabilityCompilerChain.of(
            listOf(NotCandidateCompiler("first.gap"), InvalidSceneCompiler("invalid.scene"), NotCandidateCompiler("later.gap")),
        ).select(scene(), target())

        assertEquals(listOf("invalid.scene"), assertIs<GpuPlanSelection.InvalidScene>(result).diagnostics().map { it.code.value })
    }

    @Test
    fun `a candidate from another chain is rejected with the stable diagnostic`() {
        val compiler = W3SolidRectPlanCompiler()
        val source = CapabilityCompilerChain.of(listOf(compiler))
        val destination = CapabilityCompilerChain.of(listOf(compiler))
        val candidate = assertIs<GpuPlanSelection.Candidate>(source.select(scene(), target())).candidate

        val result = assertIs<RenderPlanResult.InvalidScene>(
            destination.plan(candidate, capabilities(), PlanBudget(1L shl 20)),
        )

        assertEquals("gpu-plan.selection.invalid-candidate", result.diagnostics.single().code.value)
    }

    @Test
    fun `W3 W4a W4b chain keeps rect selections and chooses W4b for rrect`() {
        val chain = CapabilityCompilerChain.of(listOf(
            W3SolidRectPlanCompiler(), W4aAnalyticRectPlanCompiler(), W4bAnalyticRRectPlanCompiler(),
        ))

        assertEquals(W3SolidRectPlanCompiler.CAPABILITY_ID, ready(chain, rectScene(0f)).capabilityId)
        assertEquals(W4aAnalyticRectPlanCompiler.CAPABILITY_ID, ready(chain, rectScene(0.25f)).capabilityId)
        assertEquals(W4bAnalyticRRectPlanCompiler.CAPABILITY_ID, ready(chain, rrectScene()).capabilityId)
    }

    @Test
    fun resourceLimitSelectionIsTerminalAndSnapshotsImmutableDiagnostics() {
        val diagnostics = mutableListOf(resourceDiagnostic("w4c.path.resource_limit"))
        val direct = GpuPlanSelection.ResourceLimitExceeded(diagnostics)
        diagnostics.clear()

        assertEquals(listOf("w4c.path.resource_limit"), direct.diagnostics().map { it.code.value })
        kotlin.test.assertFailsWith<UnsupportedOperationException> {
            (direct.diagnostics() as MutableList<RenderDiagnostic>).clear()
        }

        val result = CapabilityCompilerChain.of(
            listOf(
                ResourceLimitCompiler("w4c.path.resource_limit"),
                InvalidSceneCompiler("later.compiler.must.not-be-authority"),
            ),
        ).select(scene(), target())

        assertEquals(
            listOf("w4c.path.resource_limit"),
            assertIs<GpuPlanSelection.ResourceLimitExceeded>(result).diagnostics().map { it.code.value },
        )
    }

    @Test
    fun W3W4aW4bW4cW4dChainKeepsHistoricalSelectionsAndOrdersW4dAfterW4c() {
        val chain = CapabilityCompilerChain.of(
            listOf(
                W3SolidRectPlanCompiler(),
                W4aAnalyticRectPlanCompiler(),
                W4bAnalyticRRectPlanCompiler(),
                W4cPathFillPlanCompiler(),
                W4dPathStrokePlanCompiler(),
            ),
        )

        assertEquals(W3SolidRectPlanCompiler.CAPABILITY_ID, ready(chain, rectScene(0f)).capabilityId)
        assertEquals(W4aAnalyticRectPlanCompiler.CAPABILITY_ID, ready(chain, rectScene(0.25f)).capabilityId)
        assertEquals(W4bAnalyticRRectPlanCompiler.CAPABILITY_ID, ready(chain, rrectScene()).capabilityId)
        assertEquals(W4cPathFillPlanCompiler.CAPABILITY_ID, ready(chain, pathScene()).capabilityId)
        assertEquals(W4dPathStrokePlanCompiler.CAPABILITY_ID, ready(chain, pathScene(PaintStyleNode.STROKE)).capabilityId)
    }

    @Test
    fun `real W4 chain keeps the W4d stroke draw limit terminal without claiming unsupported frames`() {
        val chain = CapabilityCompilerChain.of(
            listOf(
                W3SolidRectPlanCompiler(),
                W4aAnalyticRectPlanCompiler(),
                W4bAnalyticRRectPlanCompiler(),
                W4cPathFillPlanCompiler(),
                W4dPathStrokePlanCompiler(),
            ),
        )
        val strokes = repeatedPathScene(513, PaintStyleNode.STROKE)
        val fills = repeatedPathScene(513, PaintStyleNode.FILL)
        val base = assertIs<SceneCommand.Draw>(pathScene(PaintStyleNode.STROKE).commandAt(0)).node
        val unsupported = listOf(
            base.copy(coverage = CoverageRequest.ANTIALIASED),
            base.copy(transform = Matrix3x3F32.rotation(0.25f)),
            base.copy(
                clip = ClipStackNode.Operations.of(
                    listOf(org.graphiks.kanvas.render.ir.ClipEntry(base.geometry, org.graphiks.kanvas.render.ir.ClipOperation.INTERSECT)),
                ),
            ),
            base.copy(blend = BlendNode.Mode(BlendMode.SRC)),
            base.copy(material = MaterialNode.Transparent),
        ).map { node ->
            SceneSnapshot.of(SceneExtent(4, 3), ColorSpace.SRGB, List(513) { SceneCommand.Draw(node) })
        }

        val refused = assertIs<GpuPlanSelection.ResourceLimitExceeded>(chain.select(strokes, targetFor(strokes)))
        assertEquals(W4dPlanDiagnostics.PathResourceLimit, refused.diagnostics().single().code)
        assertIs<GpuPlanSelection.NotCandidate>(chain.select(fills, targetFor(fills)))
        unsupported.forEach { scene ->
            assertIs<GpuPlanSelection.NotCandidate>(chain.select(scene, targetFor(scene)))
        }
    }

    private class NotCandidateCompiler(private val code: String) : GpuPlanCompiler {
        override fun select(scene: SceneSnapshot, target: RenderTargetDescriptor): GpuPlanSelection =
            GpuPlanSelection.NotCandidate(listOf(RenderDiagnostic(
                RenderDiagnosticCode(code), RenderDiagnosticDomain.SCENE,
                RenderDiagnosticSeverity.INFO, "Fixture gap $code",
            )))

        override fun plan(
            candidate: GpuPlanCandidate,
            capabilities: PlanCapabilitySnapshot,
            budget: PlanBudget,
        ): RenderPlanResult<RenderGraph> = error("A gap compiler must never receive plan()")
    }

    private class InvalidSceneCompiler(private val code: String) : GpuPlanCompiler {
        override fun select(scene: SceneSnapshot, target: RenderTargetDescriptor): GpuPlanSelection =
            GpuPlanSelection.InvalidScene(listOf(RenderDiagnostic(
                RenderDiagnosticCode(code), RenderDiagnosticDomain.SCENE,
                RenderDiagnosticSeverity.INFO, "Fixture gap $code",
            )))

        override fun plan(
            candidate: GpuPlanCandidate,
            capabilities: PlanCapabilitySnapshot,
            budget: PlanBudget,
        ): RenderPlanResult<RenderGraph> = error("An invalid compiler must never receive plan()")
    }

    private class ResourceLimitCompiler(private val code: String) : GpuPlanCompiler {
        override fun select(scene: SceneSnapshot, target: RenderTargetDescriptor): GpuPlanSelection =
            GpuPlanSelection.ResourceLimitExceeded(listOf(resourceDiagnostic(code)))

        override fun plan(
            candidate: GpuPlanCandidate,
            capabilities: PlanCapabilitySnapshot,
            budget: PlanBudget,
        ): RenderPlanResult<RenderGraph> = error("A terminal compiler must never receive plan")
    }

    private fun scene(): SceneSnapshot = SceneSnapshot.of(
        SceneExtent(1, 1), ColorSpace.SRGB,
        listOf(SceneCommand.DrawColor(ColorARGB.White, BlendMode.SRC_OVER)),
    )

    private fun target(): RenderTargetDescriptor =
        RenderTargetDescriptor(SceneExtent(1, 1), ColorSpace.SRGB)

    private fun targetFor(scene: SceneSnapshot): RenderTargetDescriptor =
        RenderTargetDescriptor(scene.extent, scene.colorSpace)

    private fun ready(chain: CapabilityCompilerChain, scene: SceneSnapshot): RenderGraph {
        val target = RenderTargetDescriptor(scene.extent, scene.colorSpace)
        val candidate = assertIs<GpuPlanSelection.Candidate>(chain.select(scene, target)).candidate
        return assertIs<RenderPlanResult.Ready<RenderGraph>>(chain.plan(candidate, capabilities(), PlanBudget(1L shl 20))).plan
    }

    private fun rectScene(left: Float): SceneSnapshot {
        val color = ColorARGB.fromPackedUInt(0x80FF0000u)
        return SceneSnapshot.of(SceneExtent(4, 3), ColorSpace.SRGB, listOf(SceneCommand.Draw(DrawNode(
            geometry = GeometryNode.Rect.of(RectF32(left, 0f, 3f, 2f)),
            material = MaterialNode.Solid(color),
            coverage = CoverageRequest.ANTIALIASED,
            clip = ClipStackNode.Empty,
            blend = BlendNode.SrcOver,
            effects = EffectStack.Empty,
            transform = Matrix3x3F32.Identity,
            origin = DrawOrigin.RECT,
            paint = PaintNode(color, null, BlendMode.SRC_OVER, null, null, null, null, null,
                PaintStyleNode.FILL, 0f, StrokeCapNode.BUTT, StrokeJoinNode.MITER, 4f, true),
        ))))
    }

    private fun rrectScene(): SceneSnapshot {
        val color = ColorARGB.fromPackedUInt(0x80FF0000u)
        return SceneSnapshot.of(SceneExtent(4, 3), ColorSpace.SRGB, listOf(SceneCommand.Draw(DrawNode(
            geometry = GeometryNode.RRect.of(RRectF32.of(
                RectF32(0f, 0f, 3f, 2f),
                CornerRadiiF32.of(0.5f), CornerRadiiF32.of(0.75f),
                CornerRadiiF32.of(0.5f), CornerRadiiF32.of(0.25f),
            )),
            material = MaterialNode.Solid(color),
            coverage = CoverageRequest.ANTIALIASED,
            clip = ClipStackNode.Empty,
            blend = BlendNode.SrcOver,
            effects = EffectStack.Empty,
            transform = Matrix3x3F32.Identity,
            origin = DrawOrigin.RRECT,
            paint = PaintNode(color, null, BlendMode.SRC_OVER, null, null, null, null, null,
                PaintStyleNode.FILL, 0f, StrokeCapNode.BUTT, StrokeJoinNode.MITER, 4f, true),
        ))))
    }

    private fun pathScene(style: PaintStyleNode = PaintStyleNode.FILL): SceneSnapshot {
        val color = ColorARGB.fromPackedUInt(0x80FF0000u)
        val path = org.graphiks.math.geometry.PathBuilder()
            .moveTo(0f, 0f)
            .lineTo(3f, 0f)
            .lineTo(0f, 2f)
            .close()
            .build()
        return SceneSnapshot.of(SceneExtent(4, 3), ColorSpace.SRGB, listOf(SceneCommand.Draw(DrawNode(
            geometry = GeometryNode.Path(path),
            material = MaterialNode.Solid(color),
            coverage = CoverageRequest.HARD_EDGE,
            clip = ClipStackNode.Empty,
            blend = BlendNode.SrcOver,
            effects = EffectStack.Empty,
            transform = Matrix3x3F32.Identity,
            origin = DrawOrigin.PATH,
            paint = PaintNode(color, null, BlendMode.SRC_OVER, null, null, null, null, null,
                style, 2f, StrokeCapNode.BUTT, StrokeJoinNode.MITER, 4f, false),
        ))))
    }

    private fun repeatedPathScene(count: Int, style: PaintStyleNode): SceneSnapshot {
        val command = assertIs<SceneCommand.Draw>(pathScene(style).commandAt(0))
        return SceneSnapshot.of(SceneExtent(4, 3), ColorSpace.SRGB, List(count) { command })
    }

    private fun capabilities(): PlanCapabilitySnapshot = PlanCapabilitySnapshot.of(
        deviceGeneration = 0,
        maxTextureDimension2D = 64,
        maxBufferSizeBytes = 1L shl 20,
        copyBytesPerRowAlignment = 256,
        supportedFormats = setOf(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL),
        minUniformBufferOffsetAlignment = 256,
        maxDynamicUniformBuffersPerPipelineLayout = 1,
        supportedOperations = PlanOperationCapability.entries.toSet(),
        bufferAllocationPolicy = PlanBufferAllocationPolicy.of(16_384, 4_096, 4_096),
        supportedDepthStencilFormats = setOf(PlanDepthStencilFormat.Depth24PlusStencil8),
    )

}

private fun resourceDiagnostic(code: String): RenderDiagnostic = RenderDiagnostic(
    RenderDiagnosticCode(code),
    RenderDiagnosticDomain.RESOURCE,
    RenderDiagnosticSeverity.ERROR,
    "Fixture diagnostic $code",
)
