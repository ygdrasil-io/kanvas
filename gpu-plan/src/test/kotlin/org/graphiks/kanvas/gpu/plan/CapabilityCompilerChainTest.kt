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

    private fun scene(): SceneSnapshot = SceneSnapshot.of(
        SceneExtent(1, 1), ColorSpace.SRGB,
        listOf(SceneCommand.DrawColor(ColorARGB.White, BlendMode.SRC_OVER)),
    )

    private fun target(): RenderTargetDescriptor =
        RenderTargetDescriptor(SceneExtent(1, 1), ColorSpace.SRGB)

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
    )

}
