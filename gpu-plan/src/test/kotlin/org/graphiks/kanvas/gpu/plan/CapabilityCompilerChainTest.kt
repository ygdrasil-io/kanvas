package org.graphiks.kanvas.gpu.plan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.render.ir.BlendMode
import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity
import org.graphiks.kanvas.render.ir.RenderPlanResult
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.kanvas.render.ir.SceneExtent
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.math.color.ColorARGB

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
