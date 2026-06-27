package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUPipelineCacheTelemetry

class PipelineCacheTelemetryReportTest {

    private val report = PipelineCacheTelemetryReport(
        backend = "webgpu-offscreen",
        frameCount = 100,
        sceneTelemetry = listOf(
            GPUPipelineCacheTelemetry("solid-card-stack", 99, 1, 0, 1, mapOf("SolidRect" to 1L)),
            GPUPipelineCacheTelemetry("blur-radius-ladder", 99, 1, 0, 1, mapOf("Blur" to 1L)),
        ),
    )

    @Test
    fun `json contains per scene cache snapshots`() {
        val json = report.toJson()
        assertTrue(json.contains("\"sceneId\": \"solid-card-stack\""))
        assertTrue(json.contains("\"hitCount\": 99"))
        assertTrue(json.contains("\"missCount\": 1"))
        assertTrue(json.contains("\"moduleCount\": 1"))
        assertTrue(json.contains("\"evictionCount\": 0"))
        assertTrue(json.contains("\"pipelineCreations\": 1"))
        assertTrue(json.contains("SolidRect"))
        assertTrue(json.contains("\"productActivation\": true"))
    }

    @Test
    fun `writes pipeline cache telemetry json file`() {
        val dir = Files.createTempDirectory("pipeline-cache-telemetry")
        report.writeTo(dir)
        val json = dir.resolve("pipeline-cache-telemetry.json").readText()
        assertTrue(json.contains("blur-radius-ladder"))
    }

    @Test
    fun `builds telemetry for the eight benchmark families`() {
        val built = PipelineCacheTelemetryReport.forBenchmarkFamilies(frameCount = 100)
        assertEquals(8, built.sceneTelemetry.size)
        assertTrue(built.sceneTelemetry.all { it.totalPipelineCreations >= 1L })
        assertTrue(built.sceneTelemetry.all { it.moduleCount >= 1L })
        assertEquals(
            PerFamilyBenchmark.families.map { it.sceneId },
            built.sceneTelemetry.map { it.sceneId },
        )
    }
}
