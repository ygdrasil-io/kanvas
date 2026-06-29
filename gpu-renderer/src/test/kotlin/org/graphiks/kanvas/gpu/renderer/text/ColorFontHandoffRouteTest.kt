package org.graphiks.kanvas.gpu.renderer.text

import org.graphiks.kanvas.glyph.gpu.ColorGlyphPlan
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactID
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactKey
import org.graphiks.kanvas.glyph.gpu.GPUTextRouteBlocker
import org.graphiks.kanvas.glyph.gpu.artifactReference
import org.graphiks.kanvas.glyph.gpu.defaultGPUTextRouteRefusalReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ColorFontHandoffRouteTest {

    private fun colorGlyphPlan(): ColorGlyphPlan = ColorGlyphPlan(
        artifactKey = GPUTextArtifactKey(
            artifactID = GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655440002")),
            generation = GPUTextArtifactGeneration(1),
            contentFingerprint = "color-glyph-sha256",
        ),
        glyphIDs = listOf(3u),
        layerCount = 2,
    )

    @Test
    fun `color font plan is a dumpable handoff fact`() {
        val plan = colorGlyphPlan()

        assertEquals(2, plan.layerCount)
        assertEquals(listOf(3u), plan.glyphIDs)

        val reference = plan.artifactReference()
        assertEquals("ColorGlyphPlan", reference.artifactName)
        assertEquals("ColorGlyphPlan", reference.artifactType)
        assertEquals("color-glyph-sha256", reference.artifactKeyHash)
    }

    @Test
    fun `color font gpu route refuses rendering with a stable dependency gated diagnostic`() {
        val refusal = defaultGPUTextRouteRefusalReport().refusal("color-glyph-route-unavailable")

        assertEquals("ColorGlyphPlan", refusal.artifactType)
        assertEquals("text.gpu.color-plan-unsupported", refusal.handoffDiagnostic)
        assertEquals("unsupported.text.color_plan_unsupported", refusal.rendererDiagnostic)
        assertEquals(GPUTextRouteBlocker.MISSING_RENDERER_CAPABILITY, refusal.blocker)
        assertEquals("DependencyGated", refusal.classification)
        assertFalse(refusal.claimPromotionAllowed)
    }

    @Test
    fun `color font representation gate is promoted with render evidence`() {
        val gates = GPUTextRepresentationGateMatrix.byRepresentation()
        val colorGate = gates.getValue("COLRColorGlyph")

        assertEquals(GPUTextDiagnosticCodes.COLOR_PLAN_UNSUPPORTED, colorGate.diagnosticCode)
        assertTrue(colorGate.promoted)
    }

    @Test
    fun `color font route never falls back to a cpu rendered texture`() {
        val refusal = defaultGPUTextRouteRefusalReport().refusal("cpu-rendered-texture-forbidden")

        assertEquals(GPUTextRouteBlocker.CPU_RENDERED_TEXTURE, refusal.blocker)
        assertEquals("expected-unsupported", refusal.classification)
        assertEquals("text.gpu.CPU-rendered-texture-forbidden", refusal.handoffDiagnostic)
        assertFalse(refusal.claimPromotionAllowed)
    }
}
