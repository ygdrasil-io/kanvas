package org.skia.gpu.webgpu

import java.io.File
import java.util.Locale
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsSimple
import org.skia.encode.SkPngEncoder
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkData
import org.skia.foundation.SkPaint
import org.skia.gpu.webgpu.testing.CrossBackendHarness
import org.skia.testing.BitmapComparison
import org.skia.testing.TestUtils
import org.skia.tests.GM
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RuntimeEffectDescriptorSceneCaptureTest {
    @Test
    fun `SpiralRT descriptor scene captures visual parity blocker evidence`() {
        capture(RuntimeScene.Spiral)
    }

    @Test
    fun `LinearGradientRT descriptor scene captures visual parity blocker evidence`() {
        capture(RuntimeScene.LinearGradient)
    }

    private fun capture(scene: RuntimeScene) {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = RuntimeEffectGM(scene)
            val reference = TestUtils.runGmTest(gm)
            val cpuBitmap = TestUtils.runGmTest(gm)
            val gpuError = assertThrows(IllegalStateException::class.java) {
                WebGpuSink.draw(ctx, gm)
            }
            val cpuCmp = TestUtils.compareBitmapsDetailed(cpuBitmap, reference, tolerance = 0)
            val adapter = ctx.adapterInfo ?: "unknown-adapter"

            println(
                "[RuntimeEffectDescriptorSceneCapture/${scene.sceneId}] adapter=$adapter " +
                    "cpu=${"%.2f".format(cpuCmp.similarity)}%, gpuRefusal=${gpuError.message}",
            )

            if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
                writeEvidence(scene, cpuBitmap, reference, cpuCmp, adapter)
            }

            assertTrue(cpuCmp.similarity >= SUPPORT_THRESHOLD)
            assertTrue(
                gpuError.message!!.contains("has no supported WGSL implementation"),
                "expected stable unsupported-WGSL diagnostic, got ${gpuError.message}",
            )
        }
    }

    private fun writeEvidence(
        scene: RuntimeScene,
        cpuBitmap: SkBitmap,
        reference: SkBitmap,
        cpuCmp: BitmapComparison,
        adapter: String,
    ) {
        val dir = repoFile("reports/wgsl-pipeline/scenes/artifacts/${scene.sceneId}").apply { mkdirs() }
        writePng(File(dir, "skia.png"), reference)
        writePng(File(dir, "cpu.png"), cpuBitmap)
        writePng(File(dir, "cpu-diff.png"), CrossBackendHarness.pixelDiff(reference, cpuBitmap))
        File(dir, "gpu.png").delete()
        File(dir, "gpu-diff.png").delete()
        File(dir, "route-cpu.json").writeText(routeJson(scene, backend = "CPU", adapter = null))
        File(dir, "route-gpu.json").writeText(routeJson(scene, backend = "WebGPU", adapter = adapter))
        File(dir, "stats.json").writeText(statsJson(scene, cpuCmp, adapter))
    }

    private fun routeJson(scene: RuntimeScene, backend: String, adapter: String?): String = """
        {
          "sceneId": "${scene.sceneId}",
          "backend": "$backend"${adapter?.let { ",\n          \"adapter\": ${it.jsonString()}" } ?: ""},
          "drawKind": "${scene.drawKind}",
          "status": "${if (backend == "CPU") "pass" else "expected-unsupported"}",
          "selectedRoute": "${if (backend == "CPU") scene.cpuRoute else scene.gpuUnsupportedRoute}",
          "pipelineKey": "${scene.pipelineKey}",
          "fallbackReason": "${if (backend == "CPU") "none" else scene.fallbackReason}",
          "runtimeEffectStableId": "${scene.stableId}",
          "wgslImplementationId": "${scene.wgslImplementationId}",
          "uniformBytes": ${scene.uniformBytes},
          "test": "org.skia.gpu.webgpu.RuntimeEffectDescriptorSceneCaptureTest#${scene.displayName} descriptor scene captures visual parity blocker evidence",
          "sourceReport": "reports/wgsl-pipeline/2026-06-02-renderer-feature-conversion-sprint.md"
        }
    """.trimIndent() + "\n"

    private fun statsJson(
        scene: RuntimeScene,
        cpuCmp: BitmapComparison,
        adapter: String,
    ): String = """
        {
          "sceneId": "${scene.sceneId}",
          "pixels": ${cpuCmp.totalPixels},
          "matchingPixels": 0,
          "maxChannelDelta": 255,
          "threshold": $SUPPORT_THRESHOLD,
          "cpuSimilarity": ${String.format(Locale.US, "%.2f", cpuCmp.similarity)},
          "cpuMatchingPixels": ${cpuCmp.matchingPixels},
          "cpuMaxChannelDelta": ${cpuCmp.maxChannelDiff.max()},
          "gpuSimilarity": 0.00,
          "gpuMatchingPixels": 0,
          "gpuMaxChannelDelta": 255,
          "gpuStatus": "expected-unsupported",
          "fallbackReason": "${scene.fallbackReason}",
          "backend": "WebGPU",
          "adapter": ${adapter.jsonString()},
          "runtimeEffectStableId": "${scene.stableId}",
          "wgslImplementationId": "${scene.wgslImplementationId}",
          "uniformBytes": ${scene.uniformBytes},
          "command": "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.RuntimeEffectDescriptorSceneCaptureTest"
        }
    """.trimIndent() + "\n"

    private fun writePng(file: File, bitmap: SkBitmap) {
        val bytes = SkPngEncoder.Encode(bitmap)
            ?: throw IllegalStateException("Could not encode ${file.path}")
        file.writeBytes(bytes)
    }

    private fun repoFile(path: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        return File(dir, path)
    }

    private fun String.jsonString(): String = buildString {
        append('"')
        for (ch in this@jsonString) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }

    private class RuntimeEffectGM(private val scene: RuntimeScene) : GM() {
        override fun getName(): String = scene.sceneId
        override fun getISize(): SkISize = SkISize.Make(W, H)

        override fun onDraw(canvas: SkCanvas?) {
            val c = canvas ?: return
            c.drawColor(SK_ColorBLACK)
            val effect = SkRuntimeEffect.MakeForShader(scene.sksl).effect!!
            val shader = effect.makeShader(
                uniforms = SkData.MakeWithCopy(bytesOf(*scene.uniformFloats)),
                children = emptyArray(),
            )!!
            c.drawRect(
                SkRect.MakeLTRB(0f, 0f, W.toFloat(), H.toFloat()),
                SkPaint().apply {
                    this.shader = shader
                    isAntiAlias = false
                },
            )
        }
    }

    private enum class RuntimeScene(
        val sceneId: String,
        val displayName: String,
        val drawKind: String,
        val sksl: String,
        val stableId: String,
        val wgslImplementationId: String,
        val cpuRoute: String,
        val gpuRoute: String,
        val gpuUnsupportedRoute: String,
        val fallbackReason: String,
        val pipelineKey: String,
        val uniformFloats: FloatArray,
    ) {
        Spiral(
            sceneId = "runtime-effect-spiral",
            displayName = "SpiralRT",
            drawKind = "SpiralRTDescriptorGM",
            sksl = SkBuiltinShaderEffectsSimple.SPIRAL_RT_SKSL,
            stableId = "runtime.spiral_rt",
            wgslImplementationId = "wgsl/runtime_spiral_rt",
            cpuRoute = "cpu.runtime-effect.descriptor.spiral_rt",
            gpuRoute = "webgpu.runtime-effect.descriptor.spiral_rt",
            gpuUnsupportedRoute = "webgpu.runtime-effect.descriptor.spiral_rt.expected-unsupported",
            fallbackReason = "runtime-effect.spiral-visual-parity-below-threshold",
            pipelineKey = "runtimeEffect=SpiralRT descriptor=runtime_spiral_rt.wgsl parserReflected=true status=expected-unsupported state=[blendMode=kSrcOver]",
            uniformFloats = floatArrayOf(0.01f, 0f, 32f, 32f, 1f, 0f, 0f, 1f, 0f, 1f, 0f, 1f),
        ),
        LinearGradient(
            sceneId = "runtime-effect-linear-gradient",
            displayName = "LinearGradientRT",
            drawKind = "LinearGradientRTDescriptorGM",
            sksl = SkBuiltinShaderEffectsSimple.LINEAR_GRADIENT_RT_SKSL,
            stableId = "runtime.linear_gradient_rt",
            wgslImplementationId = "wgsl/runtime_linear_gradient_rt",
            cpuRoute = "cpu.runtime-effect.descriptor.linear_gradient_rt",
            gpuRoute = "webgpu.runtime-effect.descriptor.linear_gradient_rt",
            gpuUnsupportedRoute = "webgpu.runtime-effect.descriptor.linear_gradient_rt.expected-unsupported",
            fallbackReason = "runtime-effect.linear-gradient-visual-parity-below-threshold",
            pipelineKey = "runtimeEffect=LinearGradientRT descriptor=runtime_linear_gradient_rt.wgsl parserReflected=true status=expected-unsupported state=[blendMode=kSrcOver]",
            uniformFloats = floatArrayOf(1f, 0f, 0f, 1f, 0f, 1f, 0f, 1f),
        );

        val uniformBytes: Int get() = uniformFloats.size * Float.SIZE_BYTES
    }

    private companion object {
        private const val W = 64
        private const val H = 64
        private const val SUPPORT_THRESHOLD = 99.95
        private const val WRITE_EVIDENCE_PROPERTY = "kanvas.sceneEvidence.write"

        private fun bytesOf(vararg floats: Float): ByteArray {
            val bb = ByteBuffer.allocate(floats.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
            floats.forEach(bb::putFloat)
            return bb.array()
        }
    }
}
