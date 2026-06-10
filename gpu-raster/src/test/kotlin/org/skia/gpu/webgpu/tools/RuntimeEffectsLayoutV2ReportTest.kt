package org.skia.gpu.webgpu.tools

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class RuntimeEffectsLayoutV2ReportTest {
    @Test
    fun `runtime effects layout V2 report matches Kotlin descriptor offsets to WGSL reflection`() {
        val summary = RuntimeEffectsLayoutV2Report.run(Path.of("src/main/resources/shaders"))

        assertEquals("kanvas.runtime-effects.v2.layout", summary.schemaVersion)
        assertEquals(3, summary.rows.size)
        assertEquals(3, summary.layoutMatched)
        assertEquals(0, summary.layoutMismatched)

        val simple = summary.rows.single { it.stableId == "runtime.simple_rt" }
        assertEquals("layout-matched", simple.status)
        assertEquals("wgsl/runtime_simple_rt", simple.wgslImplementationId)
        assertEquals("uniform-values-excluded", simple.pipelineCacheKeyPolicy)
        assertFalse(simple.pipelineCacheKeyAxes.any { it.contains("gColor") })
        assertEquals(
            listOf(
                RuntimeEffectLayoutV2Uniform(
                    name = "gColor",
                    descriptorType = "kFloat4",
                    descriptorOffset = 0,
                    descriptorSize = 16,
                    wgslOffset = 0,
                    wgslSize = 16,
                    wgslAlignment = 16,
                    status = "matched",
                    diagnostic = "none",
                ),
            ),
            simple.uniforms,
        )

        val spiral = summary.rows.single { it.stableId == "runtime.spiral_rt" }
        assertEquals("layout-matched", spiral.status)
        assertEquals(
            listOf("rad_scale" to 0, "in_center" to 8, "in_colors0" to 16, "in_colors1" to 32),
            spiral.uniforms.map { it.name to it.descriptorOffset },
        )
        assertEquals(
            listOf("rad_scale" to 4, "in_center" to 8, "in_colors0" to 16, "in_colors1" to 16),
            spiral.uniforms.map { it.name to it.wgslAlignment },
        )

        val linearGradient = summary.rows.single { it.stableId == "runtime.linear_gradient_rt" }
        assertEquals("layout-matched", linearGradient.status)
        assertEquals(32, linearGradient.uniformBlockSize)
        assertTrue(linearGradient.diagnostics.all { it == "none" })
    }

    @Test
    fun `runtime effects layout V2 exports deterministic JSON and Markdown with non claims`() {
        val summary = RuntimeEffectsLayoutV2Report.run(Path.of("src/main/resources/shaders"))
        val firstJson = RuntimeEffectsLayoutV2Report.exportJson(summary)
        val secondJson = RuntimeEffectsLayoutV2Report.exportJson(summary)
        val firstMarkdown = RuntimeEffectsLayoutV2Report.exportMarkdown(summary)
        val secondMarkdown = RuntimeEffectsLayoutV2Report.exportMarkdown(summary)

        assertEquals(firstJson, secondJson)
        assertEquals(firstMarkdown, secondMarkdown)
        assertTrue(firstJson.contains("\"schemaVersion\":\"kanvas.runtime-effects.v2.layout\""))
        assertTrue(firstJson.contains("\"layoutMatched\":3"))
        assertTrue(firstJson.contains("\"layoutMismatched\":0"))
        assertTrue(firstJson.contains("\"pipelineCacheKeyPolicy\":\"uniform-values-excluded\""))
        assertTrue(firstJson.contains("\"diagnostic\":\"none\""))
        assertFalse(firstJson.contains("uniformValue"))

        assertTrue(firstMarkdown.contains("# Runtime Effects Layout V2"))
        assertTrue(firstMarkdown.contains("Status counts: total=3; layout-matched=3; layout-mismatched=0."))
        assertTrue(firstMarkdown.contains("runtime-effect.layout-reflection-mismatch"))
        assertTrue(firstMarkdown.contains("No uniform values enter runtime-effect pipeline cache keys."))
    }

    @Test
    fun `runtime effects layout V2 writer materializes JSON and Markdown`(@TempDir tempDir: Path) {
        writeRuntimeEffectsLayoutV2Report(
            outputRoot = tempDir,
            shaderRoot = Path.of("src/main/resources/shaders"),
        )

        val json = Files.readString(tempDir.resolve("runtime-effects-layout-v2.json"))
        val markdown = Files.readString(tempDir.resolve("runtime-effects-layout-v2.md"))

        assertTrue(json.contains("\"schemaVersion\":\"kanvas.runtime-effects.v2.layout\""))
        assertTrue(json.contains("\"stableId\":\"runtime.simple_rt\""))
        assertTrue(markdown.contains("# Runtime Effects Layout V2"))
        assertTrue(markdown.contains("runtime.spiral_rt"))
    }
}
