package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.tools.WgslValidationReport
import java.nio.file.Path

class RuntimeEffectDescriptorWebGpuTest {
    @Test
    fun `runtime SimpleRT descriptor WGSL parses and reflects uniforms`() {
        val report = WgslValidationReport.run(Path.of("src/main/resources/shaders"))
            .files
            .single { it.path.endsWith("runtime_simple_rt.wgsl") }

        assertTrue(report.success, "expected runtime_simple_rt.wgsl to parse: ${report.diagnostics}")
        assertTrue(report.entryPoints.contains("vertex:vs_main"))
        assertTrue(report.entryPoints.contains("fragment:fs_main"))
        assertTrue(report.bindings.any { it == "uniforms@group=0,binding=0" })
        val uniforms = report.uniformStructs.single { it.variable == "uniforms" }
        assertEquals(mapOf("gColor" to 0), uniforms.members.associate { it.name to it.offset })
    }

    @Test
    fun `runtime SpiralRT descriptor WGSL parses and reflects uniforms`() {
        val report = WgslValidationReport.run(Path.of("src/main/resources/shaders"))
            .files
            .single { it.path.endsWith("runtime_spiral_rt.wgsl") }

        assertTrue(report.success, "expected runtime_spiral_rt.wgsl to parse: ${report.diagnostics}")
        assertTrue(report.entryPoints.contains("vertex:vs_main"))
        assertTrue(report.entryPoints.contains("fragment:fs_main"))
        assertTrue(report.bindings.any { it == "uniforms@group=0,binding=0" })
        val uniforms = report.uniformStructs.single { it.variable == "uniforms" }
        assertEquals(
            mapOf(
                "rad_scale" to 0,
                "in_center" to 8,
                "in_colors0" to 16,
                "in_colors1" to 32,
            ),
            uniforms.members.associate { it.name to it.offset },
        )
    }

    @Test
    fun `runtime LinearGradientRT descriptor WGSL parses and reflects uniforms`() {
        val report = WgslValidationReport.run(Path.of("src/main/resources/shaders"))
            .files
            .single { it.path.endsWith("runtime_linear_gradient_rt.wgsl") }

        assertTrue(report.success, "expected runtime_linear_gradient_rt.wgsl to parse: ${report.diagnostics}")
        assertTrue(report.entryPoints.contains("vertex:vs_main"))
        assertTrue(report.entryPoints.contains("fragment:fs_main"))
        assertTrue(report.bindings.any { it == "uniforms@group=0,binding=0" })
        val uniforms = report.uniformStructs.single { it.variable == "uniforms" }
        assertEquals(
            mapOf(
                "in_colors0" to 0,
                "in_colors1" to 16,
            ),
            uniforms.members.associate { it.name to it.offset },
        )
    }

    @Test
    fun `runtime LumaToAlpha color filter descriptor WGSL parses and reflects source color`() {
        val report = WgslValidationReport.run(Path.of("src/main/resources/shaders"))
            .files
            .single { it.path.endsWith("runtime_color_filter_luma_to_alpha.wgsl") }

        assertTrue(report.success, "expected runtime_color_filter_luma_to_alpha.wgsl to parse: ${report.diagnostics}")
        assertTrue(report.entryPoints.contains("vertex:vs_main"))
        assertTrue(report.entryPoints.contains("fragment:fs_main"))
        assertTrue(report.bindings.any { it == "uniforms@group=0,binding=0" })
        val uniforms = report.uniformStructs.single { it.variable == "uniforms" }
        assertEquals(mapOf("sourceColor" to 0), uniforms.members.associate { it.name to it.offset })
    }

}
