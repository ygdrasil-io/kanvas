package org.graphiks.kanvas.gpu.renderer.text

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.glyph.gpu.GPUTextResourceContractFixture
import org.graphiks.kanvas.glyph.gpu.planGPUTextResourceContracts

class GPUTextResourcePlanEvidenceTest {
    @Test
    fun `accepted text resource plan produces evidence with upload instance and binding facts`() {
        val result = planRendererTextResourceContracts(defaultFixture())
        val accepted = assertIs<GPUTextResourcePlanEvidenceResult.Accepted>(result)

        assertEquals("draw-text-a8-001", accepted.evidence.resourcePlan.commandId)
        assertEquals("GlyphAtlasArtifact", accepted.evidence.resourcePlan.artifactType)
        assertEquals("sha256:a8-atlas", accepted.evidence.resourcePlan.artifactKeyHash)
        assertEquals("gpu-text-upload-a8-page-0", accepted.evidence.uploadPlan.uploadPlanId)
        assertEquals("gpu-text-binding-a8-0", accepted.evidence.bindingPlan.bindingPlanId)
    }

    @Test
    fun `accepted text resource plan evidence includes upload before sample ordering`() {
        val result = planRendererTextResourceContracts(defaultFixture())
        val accepted = assertIs<GPUTextResourcePlanEvidenceResult.Accepted>(result)

        val uploadBeforeSample = accepted.evidence.uploadPlan.uploadBeforeSampleDependency
        assertContains(uploadBeforeSample, "upload-before-sample")
        assertContains(uploadBeforeSample, "upload-a8-page-0")
        assertContains(uploadBeforeSample, "sample-after-upload")
    }

    @Test
    fun `accepted text resource plan dump lines contain upload instance and binding facts`() {
        val result = planRendererTextResourceContracts(defaultFixture())
        val accepted = assertIs<GPUTextResourcePlanEvidenceResult.Accepted>(result)
        val lines = accepted.dumpLines()

        assertEquals(3, lines.size)
        assertContains(lines[0], "planAvailable=true")
        assertContains(lines[0], "byteSize=512")
        assertContains(lines[0], "uploadBeforeSample")
        assertContains(lines[1], "layoutId=text.a8-mask.instance-layout.v1")
        assertContains(lines[1], "strideBytes=48")
        assertContains(lines[2], "planId=gpu-text-binding-a8-0")
        assertContains(lines[2], "bindingHash=fnv1a64:text-a8-layout")
    }

    @Test
    fun `refused text resource plan produces correct upload plan missing diagnostic`() {
        val result = planRendererTextResourceContracts(
            defaultFixture().copy(uploadPlanAvailable = false),
        )
        val refused = assertIs<GPUTextResourcePlanEvidenceResult.Refused>(result)

        assertEquals("unsupported.text.upload_plan_missing", refused.code)
        assertContains(refused.message, "upload-plan-missing")
        assertContains(refused.message, "unsupported.text.upload_plan_missing")
    }

    @Test
    fun `refused text resource plan produces correct upload budget exceeded diagnostic`() {
        val result = planRendererTextResourceContracts(
            defaultFixture().copy(uploadByteBudget = 128),
        )
        val refused = assertIs<GPUTextResourcePlanEvidenceResult.Refused>(result)

        assertEquals("unsupported.text.upload_budget_exceeded", refused.code)
        assertContains(refused.message, "upload-budget-exceeded")
    }

    @Test
    fun `refused text resource plan produces correct atlas page unavailable diagnostic`() {
        val result = planRendererTextResourceContracts(
            defaultFixture().copy(atlasPageAvailable = false),
        )
        val refused = assertIs<GPUTextResourcePlanEvidenceResult.Refused>(result)

        assertEquals("unsupported.text.atlas_page_unavailable", refused.code)
        assertContains(refused.message, "atlas-page-unavailable")
    }

    @Test
    fun `refused text resource plan produces correct atlas entry missing diagnostic`() {
        val result = planRendererTextResourceContracts(
            defaultFixture().copy(atlasEntryAvailable = false),
        )
        val refused = assertIs<GPUTextResourcePlanEvidenceResult.Refused>(result)

        assertEquals("unsupported.text.atlas_entry_missing", refused.code)
        assertContains(refused.message, "atlas-entry-missing")
    }

    @Test
    fun `refused text resource plan produces correct binding layout unavailable diagnostic`() {
        val result = planRendererTextResourceContracts(
            defaultFixture().copy(bindingLayoutAvailable = false),
        )
        val refused = assertIs<GPUTextResourcePlanEvidenceResult.Refused>(result)

        assertEquals("unsupported.text.binding_layout_unavailable", refused.code)
        assertContains(refused.message, "binding-layout-unavailable")
    }

    @Test
    fun `refused text resource plan dump lines contain code and message`() {
        val result = planRendererTextResourceContracts(
            defaultFixture().copy(uploadPlanAvailable = false),
        )
        val refused = assertIs<GPUTextResourcePlanEvidenceResult.Refused>(result)
        val lines = refused.dumpLines()

        assertEquals(1, lines.size)
        assertContains(lines[0], "unsupported.text.upload_plan_missing")
    }

    @Test
    fun `all five refusal cases produce distinct diagnostic codes`() {
        val cases = listOf(
            defaultFixture().copy(uploadPlanAvailable = false),
            defaultFixture().copy(uploadByteBudget = 128),
            defaultFixture().copy(atlasPageAvailable = false),
            defaultFixture().copy(atlasEntryAvailable = false),
            defaultFixture().copy(bindingLayoutAvailable = false),
        )
        val codes = cases.map { fixture ->
            (planRendererTextResourceContracts(fixture) as GPUTextResourcePlanEvidenceResult.Refused).code
        }

        assertEquals(codes.toSet().size, codes.size, "All five refusal codes must be distinct")
    }

    private fun defaultFixture(): GPUTextResourceContractFixture =
        GPUTextResourceContractFixture(
            commandId = "draw-text-a8-001",
            subRunId = "atlas-page-generation-split.0",
            artifactType = "GlyphAtlasArtifact",
            artifactKeyHash = "sha256:a8-atlas",
            route = "AtlasMaskSample",
            uploadPlanAvailable = true,
            uploadByteSize = 512,
            uploadByteBudget = 1024,
            atlasPageAvailable = true,
            atlasEntryAvailable = true,
            bindingLayoutAvailable = true,
        )
}
