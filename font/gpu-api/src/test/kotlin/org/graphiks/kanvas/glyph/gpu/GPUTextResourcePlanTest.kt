package org.graphiks.kanvas.glyph.gpu

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class GPUTextResourcePlanTest {
    @Test
    fun `default resource upload instance and binding reports match repo goldens`() {
        val root = projectRoot()

        assertEquals(
            Files.readString(root.resolve("reports/pure-kotlin-text/gpu-text-resource-plan.json")).trimEnd(),
            defaultGPUTextResourcePlanReportJson().trimEnd(),
        )
        assertEquals(
            Files.readString(root.resolve("reports/pure-kotlin-text/gpu-text-upload-plan.json")).trimEnd(),
            defaultGPUTextUploadPlanReportJson().trimEnd(),
        )
        assertEquals(
            Files.readString(root.resolve("reports/pure-kotlin-text/gpu-text-instance-layout.json")).trimEnd(),
            defaultGPUTextInstanceLayoutReportJson().trimEnd(),
        )
        assertEquals(
            Files.readString(root.resolve("reports/pure-kotlin-text/gpu-text-binding-plan.json")).trimEnd(),
            defaultGPUTextBindingPlanReportJson().trimEnd(),
        )
        assertEquals(
            Files.readString(root.resolve("reports/pure-kotlin-text/gpu-text-resource-refusals.json")).trimEnd(),
            defaultGPUTextResourceRefusalReportJson().trimEnd(),
        )
    }

    @Test
    fun `accepted A8 resource contract references subrun resource upload instance and binding plans`() {
        val result = planGPUTextResourceContracts(defaultGPUTextResourceContractFixture())

        val accepted = assertIs<GPUTextResourceContractPlanningResult.Accepted>(result)
        val evidence = accepted.evidence

        assertEquals("atlas-page-generation-split.0", evidence.resourcePlan.subRunId)
        assertEquals("gpu-text-upload-a8-page-0", evidence.uploadPlan.uploadPlanId)
        assertEquals("gpu-text-instance-buffer-a8-0", evidence.instanceBufferPlan.instanceBufferPlanId)
        assertEquals("gpu-text-binding-a8-0", evidence.bindingPlan.bindingPlanId)
        assertEquals(listOf("GPUTextResourcePlan", "GPUTextUploadPlan", "GPUTextInstanceLayout", "GPUTextBinding"), evidence.dumpFamilies)
        assertEquals("upload-before-sample:upload-a8-page-0->sample-after-upload:0", evidence.uploadPlan.uploadBeforeSampleDependency)
        assertEquals("instance-upload-before-draw:instance-a8-0->draw-text-a8-001", evidence.instanceBufferPlan.instanceUploadBeforeDrawDependency)
    }

    @Test
    fun `accepted resource contract evidence reflects fixture identity fields`() {
        val fixture = defaultGPUTextResourceContractFixture().copy(
            commandId = "draw-text-a8-custom",
            subRunId = "atlas-page-generation-split.custom",
            artifactKeyHash = "sha256:custom-atlas",
            route = "CustomAtlasMaskSample",
            uploadByteSize = 384,
        )

        val accepted = assertIs<GPUTextResourceContractPlanningResult.Accepted>(
            planGPUTextResourceContracts(fixture),
        )
        val evidence = accepted.evidence

        assertEquals("draw-text-a8-custom", evidence.resourcePlan.commandId)
        assertEquals("atlas-page-generation-split.custom", evidence.resourcePlan.subRunId)
        assertEquals("CustomAtlasMaskSample", evidence.resourcePlan.route)
        assertEquals("sha256:custom-atlas", evidence.resourcePlan.artifactKeyHash)
        assertEquals("sha256:custom-atlas", evidence.uploadPlan.sourceArtifactKeyHash)
        assertEquals(384, evidence.uploadPlan.byteSize)
    }

    @Test
    fun `binding plan keeps atlas resource facts outside material identity`() {
        val binding = defaultGPUTextResourceContractEvidence().bindingPlan

        assertEquals("material:text-black", binding.materialPlanRef)
        assertEquals("fnv1a64:text-a8-layout", binding.bindingLayoutHash)
        assertContains(binding.materialKeyExcludedFields, "glyphId")
        assertContains(binding.materialKeyExcludedFields, "atlasRect")
        assertContains(binding.materialKeyExcludedFields, "atlasGeneration")
        assertContains(binding.materialKeyExcludedFields, "uploadToken")
        assertContains(binding.materialKeyExcludedFields, "liveTextureHandle")
        assertFalse(binding.materialPlanRef.contains("glyphId"))
        assertFalse(binding.materialPlanRef.contains("atlasRect"))
        assertFalse(binding.materialPlanRef.contains("upload"))
    }

    @Test
    fun `resource plan contracts snapshot caller supplied lists`() {
        val usageFlags = mutableListOf("copyDst")
        val destinationTexturePlan = GPUTextDestinationTexturePlan(
            texturePlanId = "texture:test",
            ownershipPlanId = "ownership:test",
            textureFormat = "R8Unorm",
            usageFlags = usageFlags,
            width = 16,
            height = 16,
            rowStrideBytes = 16,
            pageRegion = GPUTextIntRect(left = 0, top = 0, right = 16, bottom = 16),
        )

        usageFlags += "sampledTexture"

        assertEquals(listOf("copyDst"), destinationTexturePlan.usageFlags)

        val atlasGenerationFacts = mutableListOf("page-0:generation=1")
        val resourceSlots = mutableListOf(
            GPUTextBindingSlot(group = 2, binding = 0, name = "glyphAtlas", kind = "sampledTexture", resourceRef = "texture:test"),
        )
        val materialKeyExcludedFields = mutableListOf(
            "glyphId",
            "atlasRect",
            "atlasGeneration",
            "uploadToken",
            "liveTextureHandle",
        )
        val binding = GPUTextBinding(
            bindingPlanId = "binding:test",
            subRunId = "subrun:test",
            renderStep = "A8TextMaskStep",
            artifactType = "GlyphAtlasArtifact",
            bindingLayoutHash = "fnv1a64:test-layout",
            atlasGenerationFacts = atlasGenerationFacts,
            materialPlanRef = "material:test",
            resourceSlots = resourceSlots,
            materialKeyExcludedFields = materialKeyExcludedFields,
        )

        atlasGenerationFacts += "page-0:generation=2"
        resourceSlots += GPUTextBindingSlot(group = 2, binding = 1, name = "glyphSampler", kind = "sampler", resourceRef = "sampler:test")
        materialKeyExcludedFields += "mutated"

        assertEquals(listOf("page-0:generation=1"), binding.atlasGenerationFacts)
        assertEquals(1, binding.resourceSlots.size)
        assertFalse("mutated" in binding.materialKeyExcludedFields)
    }

    @Test
    fun `planner refuses resource contract blockers with stable diagnostics`() {
        val cases = listOf(
            defaultGPUTextResourceContractFixture().copy(uploadPlanAvailable = false) to
                Triple(GPUTextRouteBlocker.UPLOAD_PLAN, "text.gpu.upload-plan-missing", "unsupported.text.upload_plan_missing"),
            defaultGPUTextResourceContractFixture().copy(uploadByteBudget = 128) to
                Triple(GPUTextRouteBlocker.UPLOAD_BUDGET, "text.gpu.upload-budget-exceeded", "unsupported.text.upload_budget_exceeded"),
            defaultGPUTextResourceContractFixture().copy(atlasPageAvailable = false) to
                Triple(GPUTextRouteBlocker.ATLAS_PAGE, "text.gpu.atlas-page-unavailable", "unsupported.text.atlas_page_unavailable"),
            defaultGPUTextResourceContractFixture().copy(atlasEntryAvailable = false) to
                Triple(GPUTextRouteBlocker.ATLAS_ENTRY, "text.gpu.atlas-entry-missing", "unsupported.text.atlas_entry_missing"),
            defaultGPUTextResourceContractFixture().copy(bindingLayoutAvailable = false) to
                Triple(GPUTextRouteBlocker.BINDING_LAYOUT, "text.gpu.binding-layout-unavailable", "unsupported.text.binding_layout_unavailable"),
        )

        cases.forEach { (fixture, expected) ->
            val refusal = assertIs<GPUTextResourceContractPlanningResult.Refused>(
                planGPUTextResourceContracts(fixture),
            ).refusal

            assertEquals(expected.first, refusal.blocker)
            assertEquals(expected.second, refusal.handoffDiagnostic)
            assertEquals(expected.third, refusal.rendererDiagnostic)
            assertEquals("AtlasMaskSample", refusal.attemptedRoute)
            assertEquals("GlyphAtlasArtifact", refusal.artifactType)
        }
    }

    @Test
    fun `resource reports are deterministic and non promotional`() {
        val json = listOf(
            defaultGPUTextResourcePlanReportJson(),
            defaultGPUTextUploadPlanReportJson(),
            defaultGPUTextInstanceLayoutReportJson(),
            defaultGPUTextBindingPlanReportJson(),
            defaultGPUTextResourceRefusalReportJson(),
        ).joinToString(separator = "\n")

        assertContains(json, """"ownerTickets":["KFONT-M11-007"]""")
        assertContains(json, """"classification":"GPU-gated"""")
        assertContains(json, """"routePromotion":"not-promoted"""")
        assertContains(json, """"productActivation":false""")
        assertContains(json, """"uploadExecution":"not-executed"""")
        assertContains(json, """"resourceHandlesMaterialized":false""")
        listOf("SkFont", "SkTypeface", "SkTextBlob", "fontBytes", "GPUHandle").forEach { token ->
            assertFalse(json.contains(token), "Resource reports leaked forbidden token $token: $json")
        }
    }

    private fun projectRoot(): Path {
        var current = Path.of("").toAbsolutePath().normalize()
        while (current.parent != null && !Files.isDirectory(current.resolve("reports/pure-kotlin-text"))) {
            current = current.parent
        }
        return current
    }
}
