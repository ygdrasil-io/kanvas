package org.graphiks.kanvas.glyph.gpu

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class A8TextRoutePlanTest {
    @Test
    fun `default A8 route plan is deterministic and stays unpromoted`() {
        val plan = defaultGPUTextA8RoutePlan()

        val json = plan.toCanonicalJson()

        assertEquals(json, defaultGPUTextA8RoutePlan().toCanonicalJson())
        assertContains(json, """"schema":"org.graphiks.kanvas.glyph.gpu.GPUTextA8RoutePlan.v1"""")
        assertContains(json, """"planId":"a8-text-route-plan-simple-latin"""")
        assertContains(json, """"selectedRoute":"AtlasMaskSample"""")
        assertContains(json, """"renderStep":"A8TextMaskStep"""")
        assertContains(json, """"wgslModuleId":"text.a8-mask"""")
        assertContains(json, """"textureFormat":"R8Unorm"""")
        assertContains(json, """"bindingName":"glyphAtlas"""")
        assertContains(json, """"bindingName":"glyphSampler"""")
        assertContains(json, """"bindingName":"textParams"""")
        assertContains(json, """"routePromotion":"not-promoted"""")
        assertContains(json, """"productActivation":false""")
        assertContains(json, """"nonClaims":["no-complete-target-support-claim","no-broad-gpu-text-support-claim","no-dftext-retirement"]""")
        assertEquals(1, plan.atlasPages.size)
        assertEquals(2, plan.entryRefs.size)
        assertEquals(listOf("upload-a8-page-0"), plan.uploadDependencyLabels)
        assertFalse(json.contains("SkTypeface"))
        assertFalse(json.contains("GPUHandle"))
        assertFalse(json.contains("fontBytes"))
    }

    @Test
    fun `planner accepts bounded A8 atlas route inputs`() {
        val result = planGPUTextA8Route(defaultGPUTextA8RouteFixture())

        val accepted = assertIs<GPUTextA8RoutePlanningResult.Accepted>(result)
        assertEquals("AtlasMaskSample", accepted.plan.selectedRoute)
        assertEquals("A8TextMaskStep", accepted.plan.renderStep)
        assertEquals("R8Unorm", accepted.plan.atlasPages.single().textureFormat)
        assertEquals("sha256:glyph-mask-A", accepted.plan.entryRefs.first().sourceMaskHash)
    }

    @Test
    fun `planner refuses missing upload plan with stable diagnostics`() {
        val result = planGPUTextA8Route(
            defaultGPUTextA8RouteFixture().copy(uploadPlan = null),
        )

        val refusal = assertIs<GPUTextA8RoutePlanningResult.Refused>(result).refusal
        assertEquals(GPUTextRouteBlocker.UPLOAD_PLAN, refusal.blocker)
        assertEquals("text.gpu.upload-plan-missing", refusal.handoffDiagnostic)
        assertEquals("unsupported.text.upload_plan_missing", refusal.rendererDiagnostic)
        assertEquals("AtlasMaskSample", refusal.attemptedRoute)
    }

    @Test
    fun `planner refuses stale atlas generation with stable diagnostics`() {
        val fixture = defaultGPUTextA8RouteFixture()
        val staleEntry = fixture.entryRefs.first().copy(atlasGeneration = fixture.expectedAtlasGeneration + 1)

        val result = planGPUTextA8Route(
            fixture.copy(entryRefs = listOf(staleEntry, fixture.entryRefs[1])),
        )

        val refusal = assertIs<GPUTextA8RoutePlanningResult.Refused>(result).refusal
        assertEquals(GPUTextRouteBlocker.STALE_GENERATION, refusal.blocker)
        assertEquals("text.gpu.atlas-generation-stale", refusal.handoffDiagnostic)
        assertEquals("unsupported.text.atlas_generation_stale", refusal.rendererDiagnostic)
    }

    @Test
    fun `planner refuses missing atlas entry with stable diagnostics`() {
        val fixture = defaultGPUTextA8RouteFixture()

        val result = planGPUTextA8Route(
            fixture.copy(entryRefs = listOf(fixture.entryRefs.first().copy(pageIndex = 7))),
        )

        val refusal = assertIs<GPUTextA8RoutePlanningResult.Refused>(result).refusal
        assertEquals(GPUTextRouteBlocker.ATLAS_ENTRY, refusal.blocker)
        assertEquals("text.gpu.atlas-entry-missing", refusal.handoffDiagnostic)
        assertEquals("unsupported.text.atlas_entry_missing", refusal.rendererDiagnostic)
    }

    @Test
    fun `default A8 refusal report captures missing entry and stale generation`() {
        val report = defaultGPUTextA8RouteRefusalReport()
        val json = report.toCanonicalJson()

        assertContains(json, """"fixtureName":"gpu-text-a8-route-refusals.json"""")
        assertContains(json, """"refusalId":"a8-text-route-plan-simple-latin.atlas-entry-missing"""")
        assertContains(json, """"refusalId":"a8-text-route-plan-simple-latin.atlas-generation-stale"""")
        assertContains(json, """"handoffDiagnostic":"text.gpu.atlas-entry-missing"""")
        assertContains(json, """"rendererDiagnostic":"unsupported.text.atlas_generation_stale"""")
        assertContains(json, """"blocker":"ATLAS_ENTRY"""")
        assertContains(json, """"blocker":"STALE_GENERATION"""")
    }

    @Test
    fun `planner refuses unsupported atlas texture format with stable diagnostics`() {
        val result = planGPUTextA8Route(
            defaultGPUTextA8RouteFixture().copy(
                atlasPages = listOf(
                    defaultGPUTextA8RouteFixture().atlasPages.single().copy(textureFormat = "rgba8unorm"),
                ),
            ),
        )

        val refusal = assertIs<GPUTextA8RoutePlanningResult.Refused>(result).refusal
        assertEquals(GPUTextRouteBlocker.ATLAS_DESCRIPTOR, refusal.blocker)
        assertEquals("text.gpu.atlas-descriptor-unaccepted", refusal.handoffDiagnostic)
        assertEquals("unsupported.text.atlas_descriptor_unaccepted", refusal.rendererDiagnostic)
    }
}
