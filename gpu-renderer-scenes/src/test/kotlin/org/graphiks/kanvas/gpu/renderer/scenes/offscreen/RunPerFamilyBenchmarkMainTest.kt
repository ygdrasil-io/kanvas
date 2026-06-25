package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RunPerFamilyBenchmarkMainTest {

    @Test
    fun `writes the three M27 performance reports`() {
        val dir = Files.createTempDirectory("per-family-benchmark-main")
        runPerFamilyBenchmark(arrayOf(dir.toString()))

        val benchmark = dir.resolve("per-family-benchmark.json")
        val telemetry = dir.resolve("pipeline-cache-telemetry.json")
        val gate = dir.resolve("frame-gate-policy.json")

        assertTrue(benchmark.exists())
        assertTrue(telemetry.exists())
        assertTrue(gate.exists())

        assertTrue(benchmark.readText().contains("\"families\""))
        assertTrue(telemetry.readText().contains("\"scenes\""))
        assertTrue(gate.readText().contains("\"hardwareBaseline\": \"Apple M-series\""))
    }

    @Test
    fun `requires an output directory argument`() {
        assertFailsWith<IllegalArgumentException> { runPerFamilyBenchmark(emptyArray()) }
    }
}
