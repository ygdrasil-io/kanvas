package org.graphiks.kanvas.glyph.gpu

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GPUTextHandoffMetricsEvidenceTest {
    @Test
    fun `gpu handoff metrics and draw text upload evidence dumps match repo goldens`() {
        val root = projectRoot()
        val expectedHandoffMetrics = Files.readString(root.resolve("reports/pure-kotlin-text/gpu-text-handoff-metrics.json"))
        val expectedUploadPlan = Files.readString(root.resolve("reports/pure-kotlin-text/draw-text-run-upload-plan.json"))

        assertEquals(expectedHandoffMetrics.trimEnd(), defaultGPUTextHandoffMetricsJson().trimEnd())
        assertEquals(expectedUploadPlan.trimEnd(), defaultDrawTextRunUploadPlanJson().trimEnd())
    }

    @Test
    fun `gpu handoff metrics stay advisory and keep stable refusal diagnostics`() {
        val json = defaultGPUTextHandoffMetricsJson()

        assertContains(json, """"dumpId": "gpu-text-handoff-metrics"""")
        assertContains(json, """"ownerTickets": ["KFONT-M12-005"]""")
        assertContains(json, """"routeOutcome":"selected"""")
        assertContains(json, """"routeOutcome":"refused"""")
        assertContains(json, """"handoffDiagnostic":"text.gpu.artifact-unregistered"""")
        assertContains(json, """"rendererDiagnostic":"unsupported.text.cpu_rendered_texture_forbidden"""")
        assertContains(json, """"rendererDiagnostic":"unsupported.text.artifact_budget_exceeded"""")
        assertContains(json, """"gpuAdapter":"wgpu-nvidia-rtx-3070"""")
        assertContains(json, """"gpuBackend":"webgpu"""")
        assertContains(json, """"no-performance-release-gate-claim"""")
        assertFalse(json.contains("SkFont", ignoreCase = true))
        assertFalse(json.contains("GPUHandle", ignoreCase = true))
    }

    @Test
    fun `draw text run upload evidence keeps material key and leakage audit bounded`() {
        val json = defaultDrawTextRunUploadPlanJson()

        assertContains(json, """"dumpId": "draw-text-run-upload-plan"""")
        assertContains(json, """"ownerTickets": ["KFONT-M12-005"]""")
        assertContains(json, """"commandId":"draw-text-001"""")
        assertContains(json, """"materialKey":"material:text-black"""")
        assertContains(json, """"uploadDependencyLabels":["upload-a8-page-0"]""")
        assertContains(json, """"artifactKeyHashes":["sha256:a8-atlas"]""")
        assertContains(json, """"payloadKind":"DrawTextRunPayload"""")
        assertContains(json, """"status":"pass"""")
        assertContains(json, """"fieldPath":"material.materialKey"""")
        assertContains(json, """"findings":[]""")
        assertFalse(json.contains("SkTypeface", ignoreCase = true))
        assertFalse(json.contains("fontBytes", ignoreCase = true))
        assertFalse(json.contains("GPUHandle", ignoreCase = true))
    }

    private fun projectRoot(): Path {
        var current = Path.of("").toAbsolutePath().normalize()
        while (current.parent != null && !Files.isDirectory(current.resolve("reports/pure-kotlin-text"))) {
            current = current.parent
        }
        return current
    }
}
