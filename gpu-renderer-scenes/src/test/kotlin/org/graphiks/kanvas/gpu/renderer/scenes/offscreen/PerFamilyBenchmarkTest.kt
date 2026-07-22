package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PerFamilyBenchmarkTest {

    @Test
    fun `families cover the ten draw families with representative scenes`() {
        val families = PerFamilyBenchmark.families
        assertEquals(10, families.size)
        assertEquals(
            listOf(
                "FillRect" to "solid-card-stack",
                "LinearGradient" to "linear-gradient-lanes",
                "RadialGradient" to "radial-swatch",
                "SweepGradient" to "sweep-disk",
                "PathFill" to "path-fill-stencil",
                "BitmapRect" to "bitmap-sampler-matrix",
                "Text" to "glyph-atlas-strip",
                "Blur" to "gaussian-blur-photo",
                "ColorMatrix" to "color-matrix-filter",
                "Stroke" to "stroke-rect-outline",
            ),
            families.map { it.family to it.sceneId },
        )
    }

    @Test
    fun `frame time statistics computes min median max and mean`() {
        // 1ms, 2ms, 4ms in nanos
        val stats = FrameTimeStatistics.of(listOf(1_000_000L, 2_000_000L, 4_000_000L))
        assertEquals(1.0, stats.minMs, 0.0001)
        assertEquals(2.0, stats.medianMs, 0.0001)
        assertEquals(4.0, stats.maxMs, 0.0001)
        assertEquals(7.0 / 3.0, stats.meanMs, 0.0001)
    }

    @Test
    fun `frame time statistics median averages the middle two for even counts`() {
        val stats = FrameTimeStatistics.of(listOf(2_000_000L, 4_000_000L, 6_000_000L, 8_000_000L))
        assertEquals(5.0, stats.medianMs, 0.0001)
    }

    @Test
    fun `frame time statistics derives fps from mean frame time`() {
        val stats = FrameTimeStatistics.of(listOf(10_000_000L, 10_000_000L))
        assertEquals(100.0, stats.fps, 0.0001)
    }

    @Test
    fun `frame time statistics rejects empty samples`() {
        assertFailsWith<IllegalArgumentException> { FrameTimeStatistics.of(emptyList()) }
    }

    @Test
    fun `benchmark skips every family when gpu is unavailable`() {
        val outputDir = Files.createTempDirectory("per-family-benchmark")
        val report = PerFamilyBenchmark(sessionFactory = { null }).run(outputDir)

        assertEquals(10, report.results.size)
        assertTrue(report.results.all { it.status == BenchmarkFamilyStatus.GpuUnavailable })
        assertTrue(report.results.all { it.statistics == null })
        assertTrue(report.results.all { result -> result.diagnostics.any { it.contains("webgpu-context-unavailable") } })
        assertEquals(true, report.productActivation)
        assertNull(report.adapterInfo)
    }

    @Test
    fun `benchmark writes per-family-benchmark json`() {
        val outputDir = Files.createTempDirectory("per-family-benchmark-json")
        PerFamilyBenchmark(sessionFactory = { null }).run(outputDir)

        val json = outputDir.resolve("per-family-benchmark.json").readText()
        assertTrue(json.contains("\"family\": \"FillRect\""))
        assertTrue(json.contains("\"sceneId\": \"gaussian-blur-photo\""))
        assertTrue(json.contains("\"sceneId\": \"stroke-rect-outline\""))
        assertTrue(json.contains("\"hardwareBaseline\": \"Apple M-series\""))
        assertTrue(json.contains("\"productActivation\": true"))
        assertTrue(json.contains("\"status\": \"gpu-unavailable\""))
    }

    @Test
    fun `prepared families remain valid across successive native target generations`() {
        val outputDir = Files.createTempDirectory("per-family-benchmark-native-generations")
        val report = PerFamilyBenchmark().run(
            outputDir = outputDir,
            warmupFrames = 0,
            measuredFrames = 1,
        )

        if (report.adapterInfo == null) return

        val preparedFamilies = setOf(
            "FillRect",
            "LinearGradient",
            "RadialGradient",
            "SweepGradient",
            "Blur",
            "ColorMatrix",
            "Stroke",
        )
        val preparedResults = report.results.filter { it.family in preparedFamilies }
        assertEquals(preparedFamilies, preparedResults.map { it.family }.toSet())
        preparedResults.forEach { result ->
            assertEquals(BenchmarkFamilyStatus.Sampled, result.status, result.diagnostics.joinToString("\n"))
            assertTrue(
                result.diagnostics.any { it.contains("via prepared submit+completion") },
                result.diagnostics.joinToString("\n"),
            )
            assertTrue(
                result.diagnostics.none { it.contains("stale.preflight.resource_generation") },
                result.diagnostics.joinToString("\n"),
            )
        }
    }

    @Test
    fun `report sampled measurements only include sampled families`() {
        val sampled = FamilyBenchmarkResult(
            family = "FillRect",
            sceneId = "solid-card-stack",
            status = BenchmarkFamilyStatus.Sampled,
            warmupFrames = 10,
            measuredFrames = 90,
            statistics = FrameTimeStatistics.of(listOf(8_000_000L, 8_000_000L)),
            diagnostics = listOf("ok"),
        )
        val skipped = FamilyBenchmarkResult(
            family = "Blur",
            sceneId = "blur-radius-ladder",
            status = BenchmarkFamilyStatus.GpuUnavailable,
            warmupFrames = 10,
            measuredFrames = 90,
            statistics = null,
            diagnostics = listOf("webgpu-context-unavailable"),
        )
        val report = PerFamilyBenchmarkReport(
            backend = "webgpu-offscreen",
            adapterInfo = "test-adapter",
            hardwareBaseline = "Apple M-series",
            warmupFrames = 10,
            measuredFrames = 90,
            results = listOf(sampled, skipped),
        )
        assertEquals(listOf("FillRect" to 8.0), report.sampledMeasurements())
    }

    @Test
    fun `sampled family json includes fps and frame time stats`() {
        val result = FamilyBenchmarkResult(
            family = "FillRect",
            sceneId = "solid-card-stack",
            status = BenchmarkFamilyStatus.Sampled,
            warmupFrames = 10,
            measuredFrames = 90,
            statistics = FrameTimeStatistics.of(listOf(10_000_000L, 10_000_000L)),
            diagnostics = listOf("ok"),
        )
        val json = result.toJson()
        assertTrue(json.contains("\"status\": \"sampled\""))
        assertTrue(json.contains("\"fps\":"))
        assertTrue(json.contains("\"medianMs\":"))
    }
}
