package org.skia.gpu.webgpu.tools

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class WgslStrictValidationReportTest {
    @Test
    fun `strict generated and registered wgsl modules parse and reflect required layout`() {
        val summary = WgslStrictValidationReport.run(Path.of("src/main/resources/shaders"))

        assertTrue(summary.success, "strict WGSL validation failures: ${summary.failedModules}")
        assertEquals(6, summary.moduleCount)

        val byLabel = summary.modules.associateBy { it.spec.label }
        assertTrue(byLabel.keys.contains("generated/solid_rect_generated.wgsl"))
        assertTrue(byLabel.keys.contains("generated/linear_gradient_generated.wgsl"))
        assertTrue(byLabel.keys.contains("registered/runtime_color_filter_luma_to_alpha.wgsl"))
        assertTrue(byLabel.keys.contains("registered/runtime_linear_gradient_rt.wgsl"))
        assertTrue(byLabel.keys.contains("registered/runtime_simple_rt.wgsl"))
        assertTrue(byLabel.keys.contains("registered/runtime_spiral_rt.wgsl"))

        byLabel.values.forEach { report ->
            assertTrue(report.validation.bindings.contains("uniforms@group=0,binding=0"))
            assertTrue(
                report.validation.uniformStructs.any {
                    it.variable == "uniforms" && it.source == UniformReflectionSource.LoweredLayout
                },
            )
        }
    }

    @Test
    fun `strict failure names missing required entrypoint`() {
        val report = WgslStrictModuleReport(
            spec = WgslStrictModuleSpec(
                label = "generated/broken.wgsl",
                expectedEntryPoints = setOf("fragment:fs_main"),
                expectedBindings = emptySet(),
                expectedUniformVariables = emptySet(),
            ),
            validation = WgslValidationReport.validateSource(
                "generated/broken.wgsl",
                """
                @vertex
                fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
                    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
                    let y = f32(idx & 2u) * 2.0 - 1.0;
                    return vec4f(x, y, 0.0, 1.0);
                }
                """.trimIndent(),
            ),
        )

        assertTrue(report.failures.contains("missing entrypoint: fragment:fs_main"))
    }
}
