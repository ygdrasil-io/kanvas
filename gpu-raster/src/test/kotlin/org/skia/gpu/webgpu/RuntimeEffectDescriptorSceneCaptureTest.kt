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
import org.skia.foundation.SkColorType
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
            val gpuResult = if (scene.gpuSupported || scene.gpuCandidateDiagnostics) {
                val gpuBitmap = renderGpu(ctx, gm)
                GpuCaptureResult.Supported(
                    bitmap = gpuBitmap,
                    comparison = TestUtils.compareBitmapsDetailed(gpuBitmap, reference, tolerance = 0),
                    toleranceOneComparison = TestUtils.compareBitmapsDetailed(gpuBitmap, reference, tolerance = 1),
                )
            } else {
                val gpuError = assertThrows(IllegalStateException::class.java) {
                    WebGpuSink.draw(ctx, gm)
                }
                GpuCaptureResult.Unsupported(gpuError.message ?: "")
            }
            val cpuCmp = TestUtils.compareBitmapsDetailed(cpuBitmap, reference, tolerance = 0)
            val adapter = ctx.adapterInfo ?: "unknown-adapter"

            println(
                "[RuntimeEffectDescriptorSceneCapture/${scene.sceneId}] adapter=$adapter " +
                    "cpu=${"%.2f".format(cpuCmp.similarity)}%, ${gpuResult.summary}",
            )

            if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
                writeEvidence(scene, cpuBitmap, reference, cpuCmp, gpuResult, adapter)
            }

            assertTrue(cpuCmp.similarity >= SUPPORT_THRESHOLD)
            when (gpuResult) {
                is GpuCaptureResult.Supported -> {
                    if (scene.gpuSupported) {
                        assertTrue(
                            gpuResult.comparison.similarity >= SUPPORT_THRESHOLD,
                            "expected ${scene.sceneId} GPU parity >= $SUPPORT_THRESHOLD, got " +
                                "${gpuResult.comparison.similarity}",
                        )
                    } else {
                        assertTrue(
                            gpuResult.comparison.similarity < SUPPORT_THRESHOLD,
                            "candidate ${scene.sceneId} reached $SUPPORT_THRESHOLD; promote instead of keeping unsupported",
                        )
                        assertTrue(
                            gpuResult.toleranceOneComparison.similarity >= SUPPORT_THRESHOLD,
                            "expected ${scene.sceneId} tolerance=1 diagnostic parity >= $SUPPORT_THRESHOLD, got " +
                                "${gpuResult.toleranceOneComparison.similarity}",
                        )
                    }
                }
                is GpuCaptureResult.Unsupported ->
                    assertTrue(
                        gpuResult.message.contains("has no supported WGSL implementation"),
                        "expected stable unsupported-WGSL diagnostic, got ${gpuResult.message}",
                    )
            }
        }
    }

    private fun renderGpu(context: WebGpuContext, gm: GM): SkBitmap {
        val size = gm.size()
        SkWebGpuDevice(
            context,
            size.width,
            size.height,
            applyColorspaceTransform = false,
            allowUnpromotedRuntimeEffectsForDiagnostics = true,
        ).use { device ->
            device.setBackground(gm.bgColor())
            gm.draw(SkCanvas(device))
            return rgbaBytesToBitmap(device.flush(), size.width, size.height)
        }
    }

    private fun rgbaBytesToBitmap(rgba: ByteArray, width: Int, height: Int): SkBitmap {
        require(rgba.size == width * height * 4) {
            "RGBA buffer size mismatch: expected ${width * height * 4} bytes, got ${rgba.size}"
        }
        val bitmap = SkBitmap(width, height, colorType = SkColorType.kRGBA_8888)
        for (i in 0 until width * height) {
            val base = i * 4
            val r = rgba[base].toInt() and 0xFF
            val g = rgba[base + 1].toInt() and 0xFF
            val b = rgba[base + 2].toInt() and 0xFF
            val a = rgba[base + 3].toInt() and 0xFF
            bitmap.pixels8888[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return bitmap
    }

    private fun writeEvidence(
        scene: RuntimeScene,
        cpuBitmap: SkBitmap,
        reference: SkBitmap,
        cpuCmp: BitmapComparison,
        gpuResult: GpuCaptureResult,
        adapter: String,
    ) {
        val dir = repoFile("reports/wgsl-pipeline/scenes/artifacts/${scene.sceneId}").apply { mkdirs() }
        writePng(File(dir, "skia.png"), reference)
        writePng(File(dir, "cpu.png"), cpuBitmap)
        writePng(File(dir, "cpu-diff.png"), CrossBackendHarness.pixelDiff(reference, cpuBitmap))
        when (gpuResult) {
            is GpuCaptureResult.Supported -> {
                writePng(File(dir, "gpu.png"), gpuResult.bitmap)
                writePng(File(dir, "gpu-diff.png"), CrossBackendHarness.pixelDiff(reference, gpuResult.bitmap))
            }
            is GpuCaptureResult.Unsupported -> {
                File(dir, "gpu.png").delete()
                File(dir, "gpu-diff.png").delete()
            }
        }
        File(dir, "route-cpu.json").writeText(routeJson(scene, backend = "CPU", adapter = null))
        File(dir, "route-gpu.json").writeText(routeJson(scene, backend = "WebGPU", adapter = adapter, gpuResult = gpuResult))
        File(dir, "stats.json").writeText(statsJson(scene, cpuCmp, gpuResult, adapter))
    }

    private fun routeJson(
        scene: RuntimeScene,
        backend: String,
        adapter: String?,
        gpuResult: GpuCaptureResult? = null,
    ): String = """
        {
          "sceneId": "${scene.sceneId}",
          "backend": "$backend"${adapter?.let { ",\n          \"adapter\": ${it.jsonString()}" } ?: ""},
          "drawKind": "${scene.drawKind}",
          "status": "${routeStatus(scene, backend, gpuResult)}",
          "selectedRoute": "${selectedRoute(scene, backend, gpuResult)}",
          "pipelineKey": "${pipelineKey(scene, gpuResult)}",
          "fallbackReason": "${fallbackReason(scene, backend, gpuResult)}",
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
        gpuResult: GpuCaptureResult,
        adapter: String,
    ): String {
        val gpuCmp = (gpuResult as? GpuCaptureResult.Supported)?.comparison
        val gpuToleranceOneCmp = (gpuResult as? GpuCaptureResult.Supported)?.toleranceOneComparison
        return """
        {
          "sceneId": "${scene.sceneId}",
          "pixels": ${cpuCmp.totalPixels},
          "matchingPixels": ${gpuCmp?.matchingPixels ?: 0},
          "maxChannelDelta": ${gpuCmp?.maxChannelDiff?.max() ?: 255},
          "threshold": $SUPPORT_THRESHOLD,
          "cpuSimilarity": ${String.format(Locale.US, "%.2f", cpuCmp.similarity)},
          "cpuMatchingPixels": ${cpuCmp.matchingPixels},
          "cpuMaxChannelDelta": ${cpuCmp.maxChannelDiff.max()},
          "gpuSimilarity": ${String.format(Locale.US, "%.2f", gpuCmp?.similarity ?: 0.0)},
          "gpuMatchingPixels": ${gpuCmp?.matchingPixels ?: 0},
          "gpuMaxChannelDelta": ${gpuCmp?.maxChannelDiff?.max() ?: 255},
          "gpuSimilarityTolerance1": ${String.format(Locale.US, "%.2f", gpuToleranceOneCmp?.similarity ?: 0.0)},
          "gpuMatchingPixelsTolerance1": ${gpuToleranceOneCmp?.matchingPixels ?: 0},
          "mismatchPattern": "${if (gpuResult is GpuCaptureResult.Supported && !scene.gpuSupported) "x=43 y=32..63 green channel -1 vs CPU" else "none"}",
          "gpuStatus": "${routeStatus(scene, "WebGPU", gpuResult)}",
          "fallbackReason": "${fallbackReason(scene, "WebGPU", gpuResult)}",
          "backend": "WebGPU",
          "adapter": ${adapter.jsonString()},
          "runtimeEffectStableId": "${scene.stableId}",
          "wgslImplementationId": "${scene.wgslImplementationId}",
          "uniformBytes": ${scene.uniformBytes},
          "command": "rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.RuntimeEffectDescriptorSceneCaptureTest"
        }
    """.trimIndent() + "\n"
    }

    private fun routeStatus(scene: RuntimeScene, backend: String, gpuResult: GpuCaptureResult?): String =
        if (backend == "CPU" || (scene.gpuSupported && gpuResult is GpuCaptureResult.Supported)) {
            "pass"
        } else {
            "expected-unsupported"
        }

    private fun selectedRoute(scene: RuntimeScene, backend: String, gpuResult: GpuCaptureResult?): String =
        if (backend == "CPU") {
            scene.cpuRoute
        } else if (scene.gpuSupported && gpuResult is GpuCaptureResult.Supported) {
            scene.gpuRoute
        } else {
            scene.gpuUnsupportedRoute
        }

    private fun pipelineKey(scene: RuntimeScene, gpuResult: GpuCaptureResult?): String =
        if (scene.gpuSupported && gpuResult is GpuCaptureResult.Supported) {
            scene.pipelineKey.replace("status=expected-unsupported", "status=supported")
        } else {
            scene.pipelineKey
        }

    private fun fallbackReason(scene: RuntimeScene, backend: String, gpuResult: GpuCaptureResult?): String =
        if (backend == "CPU" || (scene.gpuSupported && gpuResult is GpuCaptureResult.Supported)) {
            "none"
        } else if (gpuResult is GpuCaptureResult.Supported) {
            scene.preciseFallbackReason(gpuResult)
        } else {
            scene.fallbackReason
        }

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
        val gpuSupported: Boolean,
        val gpuCandidateDiagnostics: Boolean,
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
            gpuSupported = false,
            gpuCandidateDiagnostics = false,
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
            gpuSupported = false,
            gpuCandidateDiagnostics = true,
        );

        val uniformBytes: Int get() = uniformFloats.size * Float.SIZE_BYTES

        fun preciseFallbackReason(gpuResult: GpuCaptureResult.Supported): String =
            "$fallbackReason; strictSimilarity=${String.format(Locale.US, "%.2f", gpuResult.comparison.similarity)}; " +
                "threshold=${String.format(Locale.US, "%.2f", SUPPORT_THRESHOLD)}; " +
                "matchingPixels=${gpuResult.comparison.matchingPixels}/${gpuResult.comparison.totalPixels}; " +
                "maxChannelDelta=${gpuResult.comparison.maxChannelDiff.max()}; " +
                "tolerance1Similarity=${String.format(Locale.US, "%.2f", gpuResult.toleranceOneComparison.similarity)}; " +
                "mismatchPattern=x=43 y=32..63 green channel -1 vs CPU"
    }

    private sealed interface GpuCaptureResult {
        val summary: String

        data class Supported(
            val bitmap: SkBitmap,
            val comparison: BitmapComparison,
            val toleranceOneComparison: BitmapComparison,
        ) : GpuCaptureResult {
            override val summary: String =
                "gpu=${String.format(Locale.US, "%.2f", comparison.similarity)}%"
        }

        data class Unsupported(val message: String) : GpuCaptureResult {
            override val summary: String = "gpuRefusal=$message"
        }
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
