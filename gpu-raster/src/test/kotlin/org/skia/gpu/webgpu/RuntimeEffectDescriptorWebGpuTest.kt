package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.effects.runtime.ChildResolver
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectDispatch
import org.skia.effects.runtime.SkRuntimeImpl
import org.skia.effects.runtime.effects.SkBuiltinColorFilterEffects
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsChildren
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsSimple
import org.skia.effects.runtime.effects.SkBuiltinSpecialisedEffects
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkData
import org.skia.foundation.SkPaint
import org.skia.foundation.SkShader
import org.skia.foundation.SkShaders
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkPoint
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
    fun `SpiralRT runtime shader renders through descriptor-backed WGSL path`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val effect = SkRuntimeEffect.MakeForShader(SkBuiltinShaderEffectsSimple.SPIRAL_RT_SKSL).effect!!
        val shader = effect.makeShader(
            uniforms = SkData.MakeWithCopy(bytesOf(0.01f, 0f, 32f, 32f, 1f, 0f, 0f, 1f, 0f, 1f, 0f, 1f)),
            children = emptyArray(),
        )!!

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorBLACK)
                SkCanvas(device).drawRect(
                    SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat()),
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

        assertEquals(listOf(62, 193, 0, 255), pixels.rgbaAt(32, 32))
        assertEquals(listOf(194, 61, 0, 255), pixels.rgbaAt(40, 20))
        assertEquals(listOf(170, 85, 0, 255), pixels.rgbaAt(20, 40))
    }

    @Test
    fun `LinearGradientRT runtime shader renders through descriptor-backed WGSL path`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val effect = SkRuntimeEffect.MakeForShader(SkBuiltinShaderEffectsSimple.LINEAR_GRADIENT_RT_SKSL).effect!!
        val shader = effect.makeShader(
            uniforms = SkData.MakeWithCopy(bytesOf(1f, 0f, 0f, 1f, 0f, 1f, 0f, 1f)),
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

        assertEquals(listOf(212, 43, 0, 255), pixels.rgbaAt(43, 31))
        assertEquals(listOf(235, 115, 0, 255), pixels.rgbaAt(43, 32))
    }

    @Test
    fun `LumaToAlpha runtime color filter renders after solid color shader before blend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val effect = SkRuntimeEffect.MakeForColorFilter(SkBuiltinColorFilterEffects.LUMA_SRC_SKSL).effect!!
        val colorFilter = effect.makeColorFilter(uniforms = null)!!

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawRect(
                    SkRect.MakeLTRB(0f, 0f, 32f, 32f),
                    SkPaint().apply {
                        color = 0xFF336699.toInt()
                        this.colorFilter = colorFilter
                        isAntiAlias = false
                    },
                )
                val out = device.flush()
                assertEquals(null, device.runtimeEffectFallbackReasonForDiagnostics())
                out
            }
        }

        val sample = pixels.rgbaAt(16, 16)
        assertTrue(sample[0] in 158..168, "R should be white blended by luma alpha, got ${sample[0]}")
        assertTrue(sample[1] in 158..168, "G should be white blended by luma alpha, got ${sample[1]}")
        assertTrue(sample[2] in 158..168, "B should be white blended by luma alpha, got ${sample[2]}")
        assertTrue(sample[3] >= 250, "A should remain opaque after SrcOver onto white, got ${sample[3]}")
    }

    @Test
    fun `LumaToAlpha runtime color filter fully clipped rect is a no-op`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val effect = SkRuntimeEffect.MakeForColorFilter(SkBuiltinColorFilterEffects.LUMA_SRC_SKSL).effect!!
        val colorFilter = effect.makeColorFilter(uniforms = null)!!

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                canvas.clipRect(SkRect.MakeLTRB(0f, 0f, 4f, 4f))
                canvas.drawRect(
                    SkRect.MakeLTRB(32f, 32f, 48f, 48f),
                    SkPaint().apply {
                        color = 0xFF336699.toInt()
                        this.colorFilter = colorFilter
                        isAntiAlias = false
                    },
                )
                val out = device.flush()
                assertEquals(null, device.runtimeEffectFallbackReasonForDiagnostics())
                out
            }
        }

        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(0, 0))
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(40, 40))
    }

    @Test
    fun `registered runtime shader without descriptor fails with stable diagnostic`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val sksl = "half4 main(vec2 p) { return half4(0.2, 0.3, 0.4, 1); }"
        SkRuntimeEffectDispatch.register(sksl) { MissingDescriptorRuntimeImpl }
        val effect = SkRuntimeEffect.MakeForShader(sksl).effect!!
        val shader = effect.makeShader(
            uniforms = SkData.MakeWithCopy(ByteArray(0)),
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
                    "expected stable missing-WGSL diagnostic, got ${error.message}",
                )
            }
        }
    }

    @Test
    fun `runtime shader with child descriptor fails with stable child binding diagnostic`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val effect = SkRuntimeEffect.MakeForShader(SkBuiltinShaderEffectsChildren.UNSHARP_RT_SKSL).effect!!
        val shader = effect.makeShader(
            uniforms = null,
            children = arrayOf<SkShader?>(SkShaders.Color(SK_ColorBLACK)),
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
                    error.message!!.contains("runtime-effect.child-binding-unsupported"),
                    "expected stable child-binding diagnostic, got ${error.message}",
                )
            }
        }
    }

    @Test
    fun `runtime blender refuses WebGPU destination read without implicit readback`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val effect = SkRuntimeEffect.MakeForBlender(SkBuiltinSpecialisedEffects.INVERT_BLENDER_SKSL).effect!!
        val blender = effect.makeBlender(null)!!

        context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val error = assertThrows(IllegalStateException::class.java) {
                    SkCanvas(device).drawRect(
                        SkRect.MakeLTRB(0f, 0f, 16f, 16f),
                        SkPaint(SK_ColorBLACK).apply {
                            this.blender = blender
                            isAntiAlias = false
                        },
                    )
                    device.flush()
                }
                assertEquals(
                    "runtime-effect.blender-dst-read-unsupported",
                    device.runtimeEffectFallbackReasonForDiagnostics(),
                )
                assertTrue(
                    error.message!!.contains("runtime-effect.blender-dst-read-unsupported"),
                    "expected stable runtime blender dst-read diagnostic, got ${error.message}",
                )
                assertTrue(
                    error.message!!.contains("blend.shader-layer-required"),
                    "expected BlendPlan layer-composite boundary diagnostic, got ${error.message}",
                )
            }
        }
    }

    @Test
    fun `runtime blender refuses stroked WebGPU rect before style dispatch`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val effect = SkRuntimeEffect.MakeForBlender(SkBuiltinSpecialisedEffects.INVERT_BLENDER_SKSL).effect!!
        val blender = effect.makeBlender(null)!!

        context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                val error = assertThrows(IllegalStateException::class.java) {
                    SkCanvas(device).drawRect(
                        SkRect.MakeLTRB(0f, 0f, 16f, 16f),
                        SkPaint(SK_ColorBLACK).apply {
                            this.blender = blender
                            style = SkPaint.Style.kStroke_Style
                            strokeWidth = 2f
                            isAntiAlias = false
                        },
                    )
                    device.flush()
                }
                assertEquals(
                    "runtime-effect.blender-dst-read-unsupported",
                    device.runtimeEffectFallbackReasonForDiagnostics(),
                )
                assertTrue(
                    error.message!!.contains("blend.shader-layer-required"),
                    "expected style-independent BlendPlan refusal, got ${error.message}",
                )
            }
        }
    }

    @Test
    fun `runtime blender refuses blurred WebGPU rect before mask-filter layer route`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val effect = SkRuntimeEffect.MakeForBlender(SkBuiltinSpecialisedEffects.INVERT_BLENDER_SKSL).effect!!
        val blender = effect.makeBlender(null)!!

        context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                val error = assertThrows(IllegalStateException::class.java) {
                    SkCanvas(device).drawRect(
                        SkRect.MakeLTRB(8f, 8f, 40f, 40f),
                        SkPaint(SK_ColorBLACK).apply {
                            this.blender = blender
                            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 2f)
                            isAntiAlias = false
                        },
                    )
                    device.flush()
                }
                assertEquals(
                    "runtime-effect.blender-dst-read-unsupported",
                    device.runtimeEffectFallbackReasonForDiagnostics(),
                )
                assertTrue(
                    error.message!!.contains("No CPU readback or implicit destination read fallback is available."),
                    "expected pre-mask-filter runtime blender refusal, got ${error.message}",
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

    private object MissingDescriptorRuntimeImpl : SkRuntimeImpl {
        override val uniforms: List<SkRuntimeEffect.Uniform> = emptyList()
        override val children: List<SkRuntimeEffect.Child> = emptyList()
        override val flags: Int = SkRuntimeEffect.kAllowShader_Flag

        override fun shade(
            coords: SkPoint?,
            srcColor: SkColor4f?,
            dstColor: SkColor4f?,
            uniforms: ByteBuffer,
            children: Array<ChildResolver>,
        ): SkColor4f = SkColor4f(0.2f, 0.3f, 0.4f, 1f)
    }
}
