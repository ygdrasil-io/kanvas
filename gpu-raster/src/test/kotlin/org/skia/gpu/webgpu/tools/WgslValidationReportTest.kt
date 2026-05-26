package org.skia.gpu.webgpu.tools

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
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
}
