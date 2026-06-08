package org.skia.gpu.webgpu.tools

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class WgslValidationReportTest {

    @Test
    fun `validation report parses all shaders and reflects at least one uniform layout`() {
        val report = WgslValidationReport.run(Path.of("src/main/resources/shaders"))
        assertTrue(report.fileCount > 0, "expected at least one WGSL shader")
        assertTrue(report.parsedCount > 0, "expected at least one shader parsed successfully")
        assertTrue(report.reflectionCoverageCount > 0, "expected at least one reflected uniform struct")

        val firstUniform = report.files.asSequence()
            .flatMap { it.uniformStructs.asSequence() }
            .firstOrNull()
        assertTrue(firstUniform != null, "expected at least one reflected uniform report")
        assertFalse(firstUniform!!.members.isEmpty(), "reflected uniform should contain members")
        assertTrue(firstUniform.members.first().offset == 0, "first uniform member offset should be zero")
    }

    @Test
    fun `layer_composite uniform reflection matches Kotlin packer offsets`() {
        val report = WgslValidationReport.run(Path.of("src/main/resources/shaders"))
        val layerComposite = report.files.firstOrNull { it.path.endsWith("layer_composite.wgsl") }
        assertNotNull(layerComposite, "expected layer_composite.wgsl in shader validation report")

        val uniforms = layerComposite!!.uniformStructs.firstOrNull { it.variable == "uniforms" }
        assertNotNull(uniforms, "expected var<uniform> uniforms in layer_composite.wgsl")

        val reflectedByName = uniforms!!.members.associate { it.name to it.offset }
        val expectedByName = mapOf(
            "dstOriginSize" to 0,
            "paintColor" to 16,
            "colorFilterKindMode" to 32,
            "colorFilterParam0" to 48,
            "colorFilterParam1" to 64,
            "colorFilterParam2" to 80,
            "colorFilterParam3" to 96,
            "colorFilterBias" to 112,
            "devToLayerRow0" to 128,
            "devToLayerRow1" to 144,
            "samplingMode" to 160,
            "imageFilterKindMode" to 176,
            "imageFilterRectA" to 192,
            "imageFilterRectB" to 208,
        )

        verifyUniformLayout(expectedByName, reflectedByName)
        assertEquals(
            expectedByName.values.max() + 16,
            224,
            "layer_composite uniform block size must remain 224 bytes",
        )
    }

    @Test
    fun `uniform layout verifier reports mismatched field name in diagnostic`() {
        val reflectedByName = mapOf(
            "dstOriginSize" to 0,
            "paintColor" to 16,
        )
        val mismatchedExpected = mapOf(
            "dstOriginSize" to 0,
            "paintColor" to 20,
        )

        val error = assertThrows(AssertionError::class.java) {
            verifyUniformLayout(mismatchedExpected, reflectedByName)
        }
        assertTrue(error.message!!.contains("paintColor"), "diagnostic should include field name")
        assertTrue(error.message!!.contains("expected=20"), "diagnostic should include expected offset")
        assertTrue(error.message!!.contains("actual=16"), "diagnostic should include actual offset")
    }

    @Test
    fun `legacy wgsl diagnostics match deterministic allowlist`() {
        val report = WgslValidationReport.run(Path.of("src/main/resources/shaders"))
        val actual = report.files
            .flatMap { fileReport ->
                val shaderPath = Path.of("src/main/resources/shaders")
                    .relativize(Path.of(fileReport.path))
                    .let { Path.of("src/main/resources/shaders").resolve(it) }
                    .toString()
                fileReport.diagnostics.map { diagnostic ->
                    "$shaderPath|${diagnosticKind(diagnostic)}|$diagnostic"
                }
            }
            .sorted()

        val expected = Files.readAllLines(Path.of("src/test/resources/wgsl-diagnostics-allowlist.txt"))
            .filter { line -> line.isNotBlank() && !line.startsWith("#") }
            .sorted()

        assertEquals(expected, actual)
        assertEquals(30, actual.size)
    }

    private fun verifyUniformLayout(expectedByName: Map<String, Int>, reflectedByName: Map<String, Int>) {
        expectedByName.forEach { (name, expectedOffset) ->
            val actualOffset = reflectedByName[name]
            assertNotNull(actualOffset, "missing reflected uniform member '$name'")
            if (actualOffset != expectedOffset) {
                throw AssertionError("uniform member '$name' offset mismatch: expected=$expectedOffset actual=$actualOffset")
            }
        }
    }

    private fun diagnosticKind(diagnostic: String): String =
        if (diagnostic.startsWith("reflection-skip ")) "reflection" else "parser"
}
