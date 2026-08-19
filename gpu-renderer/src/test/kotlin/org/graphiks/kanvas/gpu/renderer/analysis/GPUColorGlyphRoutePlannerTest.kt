package org.graphiks.kanvas.gpu.renderer.analysis

import org.graphiks.kanvas.glyph.gpu.GPUColorGlyphLayer
import org.graphiks.kanvas.glyph.gpu.GPUColorGlyphLayerPlan
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactID
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactKey
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlanner
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendSpecializationRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageConsumption
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceAlphaClassification
import org.graphiks.kanvas.gpu.renderer.passes.GPUTargetBlendFacts
import org.graphiks.kanvas.gpu.renderer.text.ColorGlyphRefusalKind
import org.graphiks.kanvas.gpu.renderer.text.GPUColorGlyphRouteDecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class GPUColorGlyphRoutePlannerTest {

    private fun key(): GPUTextArtifactKey = GPUTextArtifactKey(
        artifactID = GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655440031")),
        generation = GPUTextArtifactGeneration(1),
        contentFingerprint = "color-plan-sha256",
    )

    private fun layer(glyph: UInt): GPUColorGlyphLayer = GPUColorGlyphLayer(
        layerGlyphID = glyph,
        paletteIndex = 0,
        resolvedColorArgb = 0xFFFF0000.toInt(),
        useForeground = false,
    )

    private fun plan(layerN: Int): GPUColorGlyphLayerPlan = GPUColorGlyphLayerPlan(
        artifactKey = key(),
        baseGlyphID = 7u,
        layers = (1..layerN).map { i -> layer(i.toUInt()) },
    )

    @Test
    fun `accepts COLRv0 within layer budget`() {
        val decision = GPUColorGlyphRoutePlanner()
            .planColorGlyphRoute(plan(GPUColorGlyphRoutePlanner.MAX_COLOR_LAYERS))
        assertTrue(decision is GPUColorGlyphRouteDecision.Accepted)
        assertEquals(7u, (decision as GPUColorGlyphRouteDecision.Accepted).route.plan.baseGlyphID)
    }

    @Test
    fun `accepts exactly the max layer budget`() {
        val decision = GPUColorGlyphRoutePlanner().planColorGlyphRoute(plan(16))
        assertTrue(decision is GPUColorGlyphRouteDecision.Accepted)
    }

    @Test
    fun `refuses layer count exceeding max`() {
        val decision = GPUColorGlyphRoutePlanner()
            .planColorGlyphRoute(plan(GPUColorGlyphRoutePlanner.MAX_COLOR_LAYERS + 1))
        val refused = assertIs<GPUColorGlyphRouteDecision.Refused>(decision)
        assertEquals(ColorGlyphRefusalKind.LAYER_COUNT_EXCEEDED, refused.refusalKind)
        assertEquals(
            "unsupported.text.color_font.layer_count_exceeded",
            refused.diagnostic.code,
        )
    }

    @Test
    fun `refuses unsupported color format`() {
        val refused = GPUColorGlyphRoutePlanner().refuseUnsupportedColorFormat("COLRv1")
        assertEquals(ColorGlyphRefusalKind.FORMAT_UNAVAILABLE, refused.refusalKind)
        assertEquals(
            "unsupported.text.color_font.format_unavailable",
            refused.diagnostic.code,
        )
    }

    private fun colorDrawTextRunCommand(
        plans: List<GPUColorGlyphLayerPlan>,
        clip: GPUClipFacts = GPUClipFacts.wideOpen(GPUBounds(0f, 0f, 128f, 64f)),
        blend: GPUBlendFacts = GPUBlendFacts.srcOver(),
        source: GPUCommandSource = GPUCommandSource(
            adapter = "unit-test",
            operation = "drawTextRun",
        ),
    ): org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand.DrawTextRun {
        val target = org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts(
            width = 128, height = 64, colorFormat = "rgba8unorm",
        )
        val bounds = org.graphiks.kanvas.gpu.renderer.commands.GPUBounds(0f, 0f, 128f, 64f)
        return org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand.DrawTextRun(
            commandId = org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID(42),
            textLayoutResultId = "layout-42",
            glyphRunId = "run-7",
            glyphRunDescriptorRefs = listOf("run-7"),
            artifactRefs = emptyList(),
            artifactKeyHashes = emptyList(),
            atlasGenerations = emptyList(),
            uploadDependencyFacts = emptyList(),
            routeDiagnostics = emptyList(),
            transform = org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts.identity(),
            clip = clip,
            layer = org.graphiks.kanvas.gpu.renderer.commands.GPULayerFacts.root(target = target),
            material = org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.SolidColor(
                r = 0f, g = 0f, b = 0f, a = 1f,
            ),
            bounds = bounds,
            blend = blend,
            ordering = org.graphiks.kanvas.gpu.renderer.commands.GPUOrderingFacts(
                paintOrder = 4, dependsOnDestination = false, requiresBarrier = false,
            ),
            source = source,
            colorGlyphPlans = plans,
        )
    }

    @Test
    fun `plans a COLRv0 command as a native color route`() {
        val command = colorDrawTextRunCommand(listOf(plan(2)))

        val routePlan = GPUColorGlyphRoutePlanner().plan(command)

        assertIs<org.graphiks.kanvas.gpu.renderer.analysis.GPUDrawAnalysisDecision.Candidate>(
            routePlan.analysisDecision,
        )
        assertEquals(
            "native.draw_text_run.colrv0_composite",
            routePlan.analysisRecord.routeDecisionLabel,
        )
    }

    @Test
    fun `plans an over-budget COLRv0 command as a refusal`() {
        val command = colorDrawTextRunCommand(listOf(plan(17)))

        val routePlan = GPUColorGlyphRoutePlanner().plan(command)

        val refuse = assertIs<org.graphiks.kanvas.gpu.renderer.analysis.GPUDrawAnalysisDecision.Refuse>(
            routePlan.analysisDecision,
        )
        assertEquals(
            "unsupported.text.color_font.layer_count_exceeded",
            refuse.diagnostic.code,
        )
    }

    @Test
    fun `accepted COLRv0 packet retains exact scissor clip provenance and scalar coverage blend`() {
        val clip = GPUClipFacts.deviceRect(GPUBounds(4f, 5f, 16f, 17f))
        val blend = GPUBlendFacts(
            mode = GPUBlendMode.SRC,
            sourceAlpha = GPUSourceAlphaClassification.Translucent,
        )
        val command = colorDrawTextRunCommand(
            plans = listOf(plan(2)),
            clip = clip,
            blend = blend,
            source = GPUCommandSource(
                adapter = "unit-test",
                operation = "drawTextRun",
                frameProvenance = GPUFrameProvenance.GmContent,
            ),
        )

        val packet = GPUColorGlyphRoutePlanner().plan(command).pass.drawPackets.single()
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
}
