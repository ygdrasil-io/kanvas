package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsSimple
import org.skia.foundation.SkData
import org.skia.foundation.SkPaint
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SkRect
import org.skia.gpu.webgpu.tools.WgslValidationReport
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
    fun `SimpleRT runtime shader renders through descriptor-backed WGSL path`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val effect = SkRuntimeEffect.MakeForShader(SkBuiltinShaderEffectsSimple.SIMPLE_RT_SKSL).effect!!
        val shader = effect.makeShader(
            uniforms = SkData.MakeWithCopy(bytesOf(0f, 0f, 0.25f, 1f)),
            children = emptyArray(),
        )!!

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorBLACK)
                SkCanvas(device).drawRect(
                    SkRect.MakeLTRB(0f, 0f, 48f, 48f),
                    SkPaint().apply {
                        this.shader = shader
                        isAntiAlias = false
                    },
                )
                val out = device.flush()
                assertEquals(null, device.runtimeEffectFallbackReasonForDiagnostics())
                out
            }
        }

        val sample = pixels.rgbaAt(16, 20)
        assertTrue(sample[0] in 4..40, "R follows x coord, got ${sample[0]}")
        assertTrue(sample[1] in 8..48, "G follows y coord, got ${sample[1]}")
        assertTrue(sample[2] in 45..90, "B follows gColor.b, got ${sample[2]}")
        assertTrue(sample[3] >= 250, "A remains opaque, got ${sample[3]}")
    }

    @Test
    fun `registered runtime shader without WGSL descriptor fails with stable diagnostic`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val effect = SkRuntimeEffect.MakeForShader(SkBuiltinShaderEffectsSimple.SPIRAL_RT_SKSL).effect!!
        val shader = effect.makeShader(
            uniforms = SkData.MakeWithCopy(bytesOf(0.01f, 0f, 8f, 1f, 0f, 0f, 1f, 1f, 0f, 1f, 0f, 1f)),
            children = emptyArray(),
        )!!

        context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                val error = assertThrows(IllegalStateException::class.java) {
                    SkCanvas(device).drawRect(
                        SkRect.MakeLTRB(0f, 0f, 16f, 16f),
                        SkPaint().apply {
                            this.shader = shader
                            isAntiAlias = false
                        },
                    )
                    device.flush()
                }
                assertTrue(
                    error.message!!.contains("runtime effect descriptor missing"),
                    "expected stable descriptor diagnostic, got ${error.message}",
                )
            }
        }
    }

    private fun bytesOf(vararg floats: Float): ByteArray {
        val bb = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.nativeOrder())
        floats.forEach(bb::putFloat)
        return bb.array()
    }

    private fun ByteArray.rgbaAt(x: Int, y: Int): List<Int> {
        val i = (y * W + x) * 4
        return listOf(
            this[i].toInt() and 0xFF,
            this[i + 1].toInt() and 0xFF,
            this[i + 2].toInt() and 0xFF,
            this[i + 3].toInt() and 0xFF,
        )
    }

    private companion object {
        const val W = 64
        const val H = 64
    }
}
