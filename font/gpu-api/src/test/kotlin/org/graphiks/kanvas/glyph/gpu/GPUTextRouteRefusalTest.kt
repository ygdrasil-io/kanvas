package org.graphiks.kanvas.glyph.gpu

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GPUTextRouteRefusalTest {
    @Test
    fun `default route refusal report covers route blockers without claim promotion`() {
        val report = defaultGPUTextRouteRefusalReport()

        assertEquals(
            listOf(
                "sdf-route-unavailable",
                "outline-route-unavailable",
                "color-glyph-route-unavailable",
                "bitmap-glyph-route-unavailable",
                "svg-glyph-route-unavailable",
                "artifact-unregistered",
                "upload-plan-missing",
                "atlas-generation-stale",
                "transform-unsupported",
                "cpu-rendered-texture-forbidden",
            ),
            report.refusals.map { refusal -> refusal.refusalId },
        )
        assertEquals(
            listOf(
                "unsupported.text.sdf_route_unavailable",
                "unsupported.text.outline_route_unavailable",
                "unsupported.text.color_plan_unsupported",
                "unsupported.text.bitmap_route_unsupported",
                "unsupported.text.svg_plan_unsupported",
                "unsupported.text.artifact_unregistered",
                "unsupported.text.upload_plan_missing",
                "unsupported.text.artifact_generation_stale",
                "unsupported.text.sdf_transform_unsupported",
                "unsupported.text.cpu_rendered_texture_forbidden",
            ),
            report.refusals.map { refusal -> refusal.rendererDiagnostic },
        )
        assertTrue(report.refusals.all { refusal -> !refusal.claimPromotionAllowed })
        assertTrue(report.refusals.all { refusal -> refusal.artifactKeyHash != null })
        assertEquals(
            listOf(
                "DependencyGated",
                "DependencyGated",
                "DependencyGated",
                "DependencyGated",
                "DependencyGated",
                "GPU-gated",
                "GPU-gated",
                "GPU-gated",
                "DependencyGated",
                "expected-unsupported",
            ),
            report.refusals.map { refusal -> refusal.classification },
        )
        assertTrue(
            report.refusals
                .filter { refusal -> refusal.classification == "DependencyGated" }
                .all { refusal -> refusal.blocker == GPUTextRouteBlocker.MISSING_RENDERER_CAPABILITY },
        )
        assertEquals(listOf("dftext"), report.refusal("sdf-route-unavailable").legacyGates)
        assertEquals(listOf("coloremoji_blendmodes"), report.refusal("color-glyph-route-unavailable").legacyGates)
        assertEquals(listOf("scaledemoji_rendering"), report.refusal("bitmap-glyph-route-unavailable").legacyGates)
        assertEquals(
            listOf("dftext", "scaledemoji_rendering", "coloremoji_blendmodes"),
            report.refusal("cpu-rendered-texture-forbidden").legacyGates,
        )
    }

    @Test
    fun `diagnostic mapping table maps handoff reasons to renderer refusals`() {
        val report = defaultGPUTextRouteRefusalReport()

        assertEquals(
            listOf(
                "SDFGlyphAtlasArtifact" to ("text.gpu.capability-missing" to "unsupported.text.sdf_route_unavailable"),
                "OutlineGlyphPlan" to ("text.gpu.capability-missing" to "unsupported.text.outline_route_unavailable"),
                "ColorGlyphPlan" to ("text.gpu.color-plan-unsupported" to "unsupported.text.color_plan_unsupported"),
                "BitmapGlyphPlan" to ("text.gpu.capability-missing" to "unsupported.text.bitmap_route_unsupported"),
                "SVGGlyphPlan" to ("text.gpu.SVG-plan-unsupported" to "unsupported.text.svg_plan_unsupported"),
                "UnregisteredTextArtifact" to ("text.gpu.artifact-unregistered" to "unsupported.text.artifact_unregistered"),
                "GlyphUploadPlan" to ("text.gpu.upload-plan-missing" to "unsupported.text.upload_plan_missing"),
                "GlyphAtlasArtifact" to ("text.gpu.atlas-generation-stale" to "unsupported.text.artifact_generation_stale"),
                "SDFGlyphAtlasArtifact" to ("text.gpu.transform-unsupported" to "unsupported.text.sdf_transform_unsupported"),
                "CPURenderedTextTexture" to (
                    "text.gpu.CPU-rendered-texture-forbidden" to
                        "unsupported.text.cpu_rendered_texture_forbidden"
                    ),
            ),
            report.diagnosticMappings.map { mapping ->
                mapping.artifactType to (mapping.handoffDiagnostic to mapping.rendererDiagnostic)
            },
        )
        assertTrue(report.diagnosticMappings.all { mapping -> mapping.handoffDiagnostic.startsWith("text.gpu.") })
        assertTrue(report.diagnosticMappings.all { mapping -> mapping.rendererDiagnostic.startsWith("unsupported.text.") })
    }

    @Test
    fun `canonical report json is deterministic and explicit non claim evidence`() {
        val report = defaultGPUTextRouteRefusalReport()
        val json = report.toCanonicalJson()

        assertEquals(json, defaultGPUTextRouteRefusalReport().toCanonicalJson())
        assertContains(json, """"schema":"org.graphiks.kanvas.glyph.gpu.GPUTextRouteRefusalReport.v1"""")
        assertContains(json, """"fixtureName":"gpu-text-route-refusals.json"""")
        assertContains(json, """"claimPromotionAllowed":false""")
        assertContains(json, """"artifactType":"SDFGlyphAtlasArtifact"""")
        assertContains(json, """"handoffDiagnostic":"text.gpu.upload-plan-missing"""")
        assertContains(json, """"rendererDiagnostic":"unsupported.text.cpu_rendered_texture_forbidden"""")
        assertContains(json, """"legacyGates":["dftext","scaledemoji_rendering","coloremoji_blendmodes"]""")
        assertContains(
            json,
            """"classificationRows":[{"blocker":"MISSING_RENDERER_CAPABILITY","classification":"DependencyGated","count":6}""",
        )
        listOf("Ganesh", "Graphite", "SkSL", "SkFont", "SkTypeface", "SkTextBlob", "SkPaint").forEach { token ->
            assertFalse(json.contains(token), "Route refusal report leaked forbidden token $token: $json")
        }
    }

    @Test
    fun `route refusals snapshot caller supplied legacy gate lists`() {
        val legacyGates = mutableListOf("dftext")
        val refusal = GPUTextRouteRefusal(
            refusalId = "snapshot-fixture",
            commandId = "draw-text-route-refusal-fixture",
            textRange = "0..1",
            glyphRange = "0..0",
            artifactType = "GlyphAtlasArtifact",
            artifactKeyHash = "sha256:snapshot",
            attemptedRoute = "AtlasGenerationValidation",
            blocker = GPUTextRouteBlocker.STALE_GENERATION,
            handoffDiagnostic = "text.gpu.atlas-generation-stale",
            rendererDiagnostic = "unsupported.text.artifact_generation_stale",
            legacyGates = legacyGates,
        )
        val json = refusal.toCanonicalJson()

        legacyGates += "scaledemoji_rendering"

        assertEquals(listOf("dftext"), refusal.legacyGates)
        assertEquals(json, refusal.toCanonicalJson())
        assertFalse(refusal.toCanonicalJson().contains("scaledemoji_rendering"))
    }
}
