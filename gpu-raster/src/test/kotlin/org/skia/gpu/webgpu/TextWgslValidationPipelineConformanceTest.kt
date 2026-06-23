package org.skia.gpu.webgpu

import org.graphiks.kanvas.glyph.gpu.defaultTextWgslReflectionReport
import org.graphiks.kanvas.glyph.gpu.defaultTextWgslReflectionReportJson
import org.graphiks.kanvas.glyph.gpu.defaultTextWgslValidationReport
import org.graphiks.kanvas.glyph.gpu.defaultTextWgslValidationReportJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class TextWgslValidationPipelineConformanceTest {
    @Test
    fun `text wgsl validation evidence matches pipeline conformance fixtures`() {
        val root = projectRoot()

        assertEquals(
            Files.readString(root.resolve("reports/pure-kotlin-text/text-wgsl-reflection.json")).trimEnd(),
            defaultTextWgslReflectionReportJson().trimEnd(),
        )
        assertEquals(
            Files.readString(root.resolve("reports/pure-kotlin-text/text-wgsl-validation-report.json")).trimEnd(),
            defaultTextWgslValidationReportJson().trimEnd(),
        )

        val reflection = defaultTextWgslReflectionReport()
        val validation = defaultTextWgslValidationReport()
        val reflectedSlots = reflection.reflectedBindings.associateBy { binding -> binding.slotRef }

        assertEquals("WGSL", reflection.wgslLanguage)
        assertEquals("accepted", reflection.parserValidation)
        assertEquals("not-promoted", reflection.routePromotion)
        assertFalse(reflection.productActivation)
        assertEquals("sampledTexture", reflectedSlots.getValue("group=2,binding=0").resourceKind)
        assertEquals("sampler", reflectedSlots.getValue("group=2,binding=1").resourceKind)
        assertEquals("uniformBuffer", reflectedSlots.getValue("group=2,binding=2").resourceKind)
        assertEquals("TextParams", reflection.uniformLayouts.single().structName)

        assertEquals("gpu-text-binding-a8-0", validation.bindingPlanId)
        assertEquals("gpu-text-resource-a8-0", validation.resourcePlanId)
        assertEquals("gpu-text-ordering-a8-0", validation.orderingTokenId)
        assertEquals("not-promoted", validation.routePromotion)
        assertFalse(validation.productActivation)
        assertTrue(
            validation.refusals.map { diagnostic -> diagnostic.code }.containsAll(
                listOf(
                    "wgsl4k.validation.syntax_error",
                    "unsupported.wgsl.binding_reflection_mismatch",
                    "unsupported.text.sdf_params_missing",
                    "unsupported.wgsl.unregistered_module",
                ),
            ),
        )
    }

    private fun projectRoot(): Path {
        var current = Path.of("").toAbsolutePath().normalize()
        while (current.parent != null && !Files.isDirectory(current.resolve("reports/pure-kotlin-text"))) {
            current = current.parent
        }
        return current
    }
}
