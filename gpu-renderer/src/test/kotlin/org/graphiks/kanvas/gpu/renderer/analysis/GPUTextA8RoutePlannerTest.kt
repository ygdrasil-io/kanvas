package org.graphiks.kanvas.gpu.renderer.analysis

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.commands.GPULayerFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUOrderingFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlanner
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendSpecializationRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageConsumption
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceAlphaClassification
import org.graphiks.kanvas.gpu.renderer.passes.GPUTargetBlendFacts
import org.graphiks.kanvas.gpu.renderer.routing.GPURouteDecision
import org.graphiks.kanvas.gpu.renderer.text.GPUTextArtifactRef
import org.graphiks.kanvas.gpu.renderer.text.GPUTextDiagnostic
import org.graphiks.kanvas.gpu.renderer.text.GPUTextDiagnosticCodes

class GPUTextA8RoutePlannerTest {
    @Test
    fun `accepted draw text run builds native a8 atlas route and pass`() {
        val command = a8DrawTextRunCommand()
        val plan = GPUTextA8RoutePlanner().plan(command)
        val routeDecision = assertIs<GPURouteDecision.Native>(plan.routeDecision)
        val analysisDecision = assertIs<GPUDrawAnalysisDecision.Candidate>(plan.analysisDecision)
        val invocation = plan.pass.invocations.single()

        assertEquals("analysis.draw_text_run.42", plan.analysisRecord.recordId)
        assertEquals("DrawTextRun", plan.analysisRecord.commandFamily)
        assertEquals("native.draw_text_run.a8_atlas", analysisDecision.routeDecisionLabel)
        assertEquals("native.draw_text_run.a8_atlas", routeDecision.route.consumerKind)
        assertEquals("text.a8_mask.sample", routeDecision.route.renderStepIdentity)
        assertContains(routeDecision.route.requirements.toString(), "wgsl_module=text.a8-mask")
        assertEquals("pass.text.42", plan.pass.passId)
        assertEquals(listOf("pending.pipeline.draw_text_run.a8_atlas.rgba8unorm.src_over"), plan.pass.pipelineKeys)
        assertEquals("text.a8_mask.sample", invocation.renderStepId.value)
        assertEquals("text", invocation.role)
        assertNull(invocation.uniformSlot)
        assertNull(invocation.resourceSlot)
    }

    @Test
    fun `accepted draw text run emits atlas generation diagnostics`() {
        val command = a8DrawTextRunCommand()
        val plan = GPUTextA8RoutePlanner().plan(command)

        assertContains(plan.analysisRecord.diagnostics.map { it.code }, "text:atlas_gen=3")
    }

    @Test
    fun `accepted draw text run declares resource artifacts`() {
        val command = a8DrawTextRunCommand()
        val plan = GPUTextA8RoutePlanner().plan(command)
        val analysisDecision = assertIs<GPUDrawAnalysisDecision.Candidate>(plan.analysisDecision)

        assertEquals(
            listOf("artifact:GlyphAtlasArtifact:sha256:a8-atlas"),
            analysisDecision.resourceDeclarations,
        )
    }

    @Test
    fun `accepted A8 packet retains exact scissor clip provenance and scalar coverage blend`() {
        val clip = GPUClipFacts.deviceRect(GPUBounds(4f, 5f, 16f, 17f))
        val blend = GPUBlendFacts(
            mode = GPUBlendMode.SRC,
            sourceAlpha = GPUSourceAlphaClassification.Translucent,
        )
        val command = a8DrawTextRunCommand(
            clip = clip,
            blend = blend,
            source = GPUCommandSource(
                adapter = "unit-test",
                operation = "drawTextRun",
                frameProvenance = GPUFrameProvenance.GmContent,
            ),
        )

        val packet = GPUTextA8RoutePlanner().plan(command).pass.drawPackets.single()
        val expectedBlend = GPUBlendPlanner().plan(
            GPUBlendSpecializationRequest(
                mode = blend.mode,
                coverage = GPUCoverageConsumption.ScalarCoverage,
                sourceAlpha = blend.sourceAlpha,
                target = GPUTargetBlendFacts(
                    formatClass = command.layer.target.colorFormat,
                    clampsNormalizedColorWrites = true,
                    premultipliedAlpha = true,
                ),
                samplePlan = GPUSamplePlan.SingleSampleFrame,
            ),
        )

        assertEquals("scissor_4.0_5.0_16.0_17.0", packet.scissorBoundsHash)
        assertEquals(clip.coveragePlan, packet.clipCoveragePlan)
        assertEquals(clip.executionPlan, packet.clipExecutionPlan)
        assertEquals(GPUFrameProvenance.GmContent, packet.frameProvenance)
        assertEquals(expectedBlend, packet.blendPlan)
    }

    @Test
    fun `draw text run with empty artifacts refuses with unregistered diagnostic`() {
        val command = a8DrawTextRunCommand(artifactRefs = emptyList())
        val plan = GPUTextA8RoutePlanner().plan(command)
        assertRefused(plan, "unsupported.text.artifact_unregistered")
    }

    @Test
    fun `draw text run with unsupported route hint refuses with a8 atlas unavailable`() {
        val command = a8DrawTextRunCommand(
            artifactRefs = listOf(
                a8ArtifactRef().copy(routeHint = "SDFMaskAtlas"),
            ),
        )
        val plan = GPUTextA8RoutePlanner().plan(command)
        assertRefused(plan, "unsupported.text.a8_atlas_route_unavailable")
    }

    @Test
    fun `draw text run without upload dependencies refuses with upload plan missing`() {
        val command = a8DrawTextRunCommand(uploadDependencyFacts = emptyList())
        val plan = GPUTextA8RoutePlanner().plan(command)
        assertRefused(plan, "unsupported.text.upload_plan_missing")
    }

    @Test
    fun `draw text run with stale atlas generation refuses with generation stale`() {
        val command = a8DrawTextRunCommand(
            atlasGenerations = listOf(GPUTextArtifactGeneration(4)),
        )
        val plan = GPUTextA8RoutePlanner().plan(command)
        assertRefused(plan, "unsupported.text.atlas_generation_stale")
    }

    @Test
    fun `draw text run with terminal route diagnostics refuses with that diagnostic`() {
        val command = a8DrawTextRunCommand(
            routeDiagnostics = listOf(
                GPUTextDiagnostic(
                    code = GPUTextDiagnosticCodes.ARTIFACT_BUDGET_EXCEEDED,
                    message = "Artifact budget exceeded.",
                    terminal = true,
                ),
            ),
        )
        val plan = GPUTextA8RoutePlanner().plan(command)
        assertRefused(plan, GPUTextDiagnosticCodes.ARTIFACT_BUDGET_EXCEEDED)
    }

    private fun assertRefused(plan: GPUFirstRoutePlan, expectedCode: String) {
        val routeDecision = assertIs<GPURouteDecision.Refused>(plan.routeDecision)
        val analysisDecision = assertIs<GPUDrawAnalysisDecision.Refuse>(plan.analysisDecision)

        assertEquals(expectedCode, routeDecision.diagnostic.code)
        assertEquals(expectedCode, analysisDecision.diagnostic.code)
        assertEquals(emptyList(), plan.analysisRecord.renderStepCandidates)
        assertEquals(listOf(expectedCode), plan.pass.diagnostics.map { it.code })
        assertEquals(emptyList(), plan.pass.invocations)
        assertEquals(emptyList(), plan.pass.pipelineKeys)
    }

    private fun a8DrawTextRunCommand(
        artifactRefs: List<GPUTextArtifactRef> = listOf(a8ArtifactRef()),
        atlasGenerations: List<GPUTextArtifactGeneration> = listOf(GPUTextArtifactGeneration(3)),
        uploadDependencyFacts: List<String> = listOf("upload-before-sample"),
        routeDiagnostics: List<GPUTextDiagnostic> = emptyList(),
        clip: GPUClipFacts = GPUClipFacts.wideOpen(bounds = GPUBounds(0f, 0f, 128f, 64f)),
        blend: GPUBlendFacts = GPUBlendFacts.srcOver(),
        source: GPUCommandSource = GPUCommandSource(adapter = "unit-test", operation = "drawTextRun"),
    ): NormalizedDrawCommand.DrawTextRun {
        val target = GPUTargetFacts(width = 128, height = 64, colorFormat = "rgba8unorm")
        return NormalizedDrawCommand.DrawTextRun(
            commandId = GPUDrawCommandID(42),
            textLayoutResultId = "layout-42",
            glyphRunId = "run-7",
            glyphRunDescriptorRefs = listOf("run-7"),
            artifactRefs = artifactRefs,
            artifactKeyHashes = artifactRefs.map { it.artifactKeyHash },
            atlasGenerations = atlasGenerations,
            uploadDependencyFacts = uploadDependencyFacts,
            routeDiagnostics = routeDiagnostics,
            transform = GPUTransformFacts.identity(),
            clip = clip,
            layer = GPULayerFacts.root(target = target),
            material = GPUMaterialDescriptor.SolidColor(r = 0f, g = 0f, b = 0f, a = 1f),
            blend = blend,
            bounds = GPUBounds(0f, 0f, 128f, 64f),
            ordering = GPUOrderingFacts(
                paintOrder = 4,
                dependsOnDestination = false,
                requiresBarrier = false,
            ),
            source = source,
        )
    }

    private fun a8ArtifactRef(routeHint: String? = "AtlasMaskSample"): GPUTextArtifactRef =
        GPUTextArtifactRef(
            artifactType = "GlyphAtlasArtifact",
            artifactId = "artifact-9",
            artifactKeyHash = "sha256:a8-atlas",
            generation = GPUTextArtifactGeneration(3),
            routeHint = routeHint,
        )
}
