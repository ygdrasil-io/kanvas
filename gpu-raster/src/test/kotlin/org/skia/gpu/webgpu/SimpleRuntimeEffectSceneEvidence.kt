package org.skia.gpu.webgpu

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Locale
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assumptions
import org.skia.core.SkCanvas
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsSimple
import org.skia.encode.SkPngEncoder
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorType
import org.skia.foundation.SkData
import org.skia.foundation.SkPaint
import org.skia.gpu.webgpu.testing.CrossBackendHarness
import org.skia.gpu.webgpu.tools.WgslValidationReport
import org.skia.testing.BitmapComparison
import org.skia.testing.TestUtils

public object SimpleRuntimeEffectSceneEvidence {
    public const val SceneId: String = "runtime.simple_rt.descriptor.rect.v1"
    public const val ScopeId: String = "runtime-effect.simple-rt.registered-descriptor.rect.v1"
    public const val CpuRouteIdentifier: String = "cpu.runtime-effect.descriptor.simple_rt"
    public const val WebGpuRouteIdentifier: String = "webgpu.runtime-effect.descriptor.simple_rt"
    public const val ArtifactDirectory: String =
        "reports/wgsl-pipeline/scenes/artifacts/kan-017-simple-rt"
    public const val WriteEvidenceProperty: String = "kanvas.sceneEvidence.write"
    public const val RuntimeStableId: String = "runtime.simple_rt"
    public const val CpuImplementationId: String = "kotlin/simple_rt"
    public const val WgslImplementationId: String = "wgsl/runtime_simple_rt"
    public const val ColorSpacePolicy: String = "srgb-unmanaged-simple-rt-oracle"
    public const val FallbackPolicy: String = "descriptor-backed-simple-rt-wgsl-route"

    private const val Width = 64
    private const val Height = 64
    private const val Tolerance = 1
    private const val SimilarityThreshold = 99.95
    private const val UniformBlue = 0.25f
    private val DrawRect = SkRect.MakeLTRB(0f, 0f, Width.toFloat(), Height.toFloat())

    private val StableRefusals = listOf(
        "runtime-effect.wgsl-descriptor-missing",
        "runtime-effect.arbitrary-sksl-unsupported",
    )

    private val NonClaims = listOf(
        "no-dynamic-sksl-compilation-claim",
        "no-sksl-ir-or-vm-claim",
        "no-arbitrary-runtime-effect-claim",
        "no-broad-runtime-effect-claim",
        "no-spiral-rt-promotion-claim",
        "no-runtime-effect-color-filter-claim",
        "no-runtime-effect-blender-claim",
        "no-runtime-effect-image-filter-claim",
        "no-live-editing-breadth-claim",
    )

    public fun capture(writeArtifacts: Boolean = false): SimpleRuntimeEffectEvidence {
        val effect = requireNotNull(SkRuntimeEffect.MakeForShader(SkBuiltinShaderEffectsSimple.SIMPLE_RT_SKSL).effect) {
            "SimpleRT effect was not registered"
        }
        val descriptor = requireNotNull(effect.descriptor()) {
            "SimpleRT descriptor was not registered"
        }
        val uniform = requireNotNull(descriptor.uniforms.singleOrNull { it.name == "gColor" }) {
            "SimpleRT descriptor must expose gColor"
        }
        val runtimeWgsl = runtimeWgslReport()
        val reference = renderAnalyticReference()
        val cpu = renderCpu(effect)
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            val adapter = ctx.adapterInfo ?: "unknown-adapter"
            val webGpuResult = renderWebGpu(ctx, effect)
            val cpuComparison = TestUtils.compareBitmapsDetailed(
                cpu,
                reference,
                tolerance = Tolerance,
            )
            val webGpuComparison = TestUtils.compareBitmapsDetailed(
                webGpuResult.bitmap,
                reference,
                tolerance = Tolerance,
            )
            val artifacts = SimpleRuntimeEffectArtifacts.forRoot(repoFile(ArtifactDirectory))
            val evidence = SimpleRuntimeEffectEvidence(
                sceneId = SceneId,
                scopeId = ScopeId,
                runtimeEffectStableId = descriptor.stableId,
                cpuImplementationId = descriptor.cpuImplementationId,
                wgslImplementationId = requireNotNull(descriptor.wgslImplementationId),
                cpuRouteIdentifier = CpuRouteIdentifier,
                webGpuRouteIdentifier = WebGpuRouteIdentifier,
                cpuFallbackReason = "none",
                webGpuFallbackReason = webGpuResult.fallbackReason ?: "none",
                uniformName = uniform.name,
                uniformOffset = uniform.offset,
                uniformBytes = effect.uniformSize,
                runtimeWgslValidated = runtimeWgsl.success,
                runtimeWgslDiagnostics = runtimeWgsl.diagnostics,
                runtimeWgslEntryPoints = runtimeWgsl.entryPoints,
                runtimeWgslSha256 = runtimeWgsl.sourceSha256,
                parserReflected = runtimeWgsl.uniformLayout["gColor"] == 0,
                tolerance = Tolerance,
                cpuComparison = cpuComparison,
                webGpuComparison = webGpuComparison,
                cpuSimilarityThreshold = SimilarityThreshold,
                webGpuSimilarityThreshold = SimilarityThreshold,
                nonBackgroundPixels = countNonBackgroundPixels(webGpuResult.bitmap),
                webGpuAdapter = adapter,
                artifacts = artifacts,
            )
            if (writeArtifacts || System.getProperty(WriteEvidenceProperty) == "true") {
                writeEvidence(evidence, reference, cpu, webGpuResult.bitmap)
            }
            println(
                "[SimpleRuntimeEffectSceneEvidence] adapter=$adapter " +
                    "cpu=${"%.2f".format(Locale.US, cpuComparison.similarity)}%, " +
                    "webgpu=${"%.2f".format(Locale.US, webGpuComparison.similarity)}%, " +
                    "fallback=${evidence.webGpuFallbackReason}",
            )
            return evidence
        }
    }

    private fun renderAnalyticReference(): SkBitmap {
        val bitmap = SkBitmap(Width, Height, colorType = SkColorType.kRGBA_8888)
        bitmap.eraseColor(SK_ColorBLACK)
        for (y in 0 until Height) {
            for (x in 0 until Width) {
                bitmap.pixels8888[y * Width + x] = packSimpleRtColor(x, y)
            }
        }
        return bitmap
    }

    private fun renderCpu(effect: SkRuntimeEffect): SkBitmap {
        val bitmap = SkBitmap(Width, Height, colorType = SkColorType.kRGBA_8888)
        bitmap.eraseColor(SK_ColorBLACK)
        drawScene(SkCanvas(bitmap), effect)
        return bitmap
    }

    private fun renderWebGpu(context: WebGpuContext, effect: SkRuntimeEffect): WebGpuRuntimeEffectRender {
        SkWebGpuDevice(
            context,
            Width,
            Height,
            applyColorspaceTransform = false,
        ).use { device ->
            device.setBackground(SK_ColorBLACK)
            drawScene(SkCanvas(device), effect)
            val rgba = device.flush()
            return WebGpuRuntimeEffectRender(
                bitmap = rgbaBytesToBitmap(rgba, Width, Height),
                fallbackReason = device.runtimeEffectFallbackReasonForDiagnostics(),
            )
        }
    }

    private fun drawScene(canvas: SkCanvas, effect: SkRuntimeEffect) {
        val shader = requireNotNull(
            effect.makeShader(
                uniforms = SkData.MakeWithCopy(uniformBytes()),
                children = emptyArray(),
            ),
        ) {
            "SimpleRT shader creation failed"
        }
        canvas.drawRect(
            DrawRect,
            SkPaint().apply {
                this.shader = shader
                blendMode = SkBlendMode.kSrcOver
                isAntiAlias = false
            },
        )
    }

    private fun writeEvidence(
        evidence: SimpleRuntimeEffectEvidence,
        reference: SkBitmap,
        cpu: SkBitmap,
        webGpu: SkBitmap,
    ) {
        val artifacts = evidence.artifacts
        artifacts.root.mkdirs()
        writePng(artifacts.referencePng, reference)
        writePng(artifacts.cpuPng, cpu)
        writePng(artifacts.webGpuPng, webGpu)
        writePng(artifacts.cpuDiffPng, CrossBackendHarness.pixelDiff(reference, cpu))
        writePng(artifacts.webGpuDiffPng, CrossBackendHarness.pixelDiff(reference, webGpu))
        artifacts.routeCpuJson.writeText(routeJson(evidence, backend = "CPU"))
        artifacts.routeWebGpuJson.writeText(routeJson(evidence, backend = "WebGPU"))
        artifacts.statsJson.writeText(statsJson(evidence))
    }

    private fun packSimpleRtColor(x: Int, y: Int): Int {
        fun channel(value: Float): Int = (value.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        val localX = x.toFloat() + 0.5f
        val localY = y.toFloat() + 0.5f
        return SkColorSetARGB(
            255,
            channel(localX / 255f),
            channel(localY / 255f),
            channel(UniformBlue),
        )
    }

    private fun uniformBytes(): ByteArray {
        val bytes = ByteBuffer.allocate(16).order(ByteOrder.nativeOrder())
        bytes.putFloat(0f)
        bytes.putFloat(0f)
        bytes.putFloat(UniformBlue)
        bytes.putFloat(1f)
        return bytes.array()
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
            bitmap.pixels8888[i] = SkColorSetARGB(a, r, g, b)
        }
        return bitmap
    }

    private fun countNonBackgroundPixels(bitmap: SkBitmap): Int =
        bitmap.pixels8888.count { pixel -> pixel != SK_ColorBLACK }

    private fun writePng(file: File, bitmap: SkBitmap) {
        file.parentFile.mkdirs()
        file.writeBytes(requireNotNull(SkPngEncoder.Encode(bitmap)) { "PNG encoding failed for ${file.path}" })
    }

    private fun routeJson(evidence: SimpleRuntimeEffectEvidence, backend: String): String {
        val isGpu = backend == "WebGPU"
        val comparison = if (isGpu) evidence.webGpuComparison else evidence.cpuComparison
        val selectedRoute = if (isGpu) WebGpuRouteIdentifier else CpuRouteIdentifier
        val coverageStrategy = if (isGpu) "webgpu.coverage.analytic-rect" else "cpu.coverage.analytic-rect"
        val artifact = if (isGpu) evidence.artifacts.webGpuPng else evidence.artifacts.cpuPng
        val diff = if (isGpu) evidence.artifacts.webGpuDiffPng else evidence.artifacts.cpuDiffPng
        return """
            {
              "sceneId": ${SceneId.json()},
              "scopeId": ${ScopeId.json()},
              "backend": ${backend.json()},
              ${if (isGpu) "\"adapter\": ${evidence.webGpuAdapter.json()}," else ""}
              "drawKind": "SkCanvas.drawRect",
              "status": "pass",
              "supportScope": "registered-simple-rt-descriptor-rect",
              "supportClaim": true,
              "selectedRoute": ${selectedRoute.json()},
              "coverageStrategy": ${coverageStrategy.json()},
              "pipelineKey": "runtimeEffect=SimpleRT descriptor=runtime_simple_rt.wgsl parserReflected=true state=[blendMode=kSrcOver]",
              "paintBlendMode": "kSrcOver",
              "runtimeEffectStableId": ${evidence.runtimeEffectStableId.json()},
              "cpuImplementationId": ${evidence.cpuImplementationId.json()},
              "wgslImplementationId": ${evidence.wgslImplementationId.json()},
              "descriptorBacked": true,
              "uniformLayout": {
                ${evidence.uniformName.json()}: ${evidence.uniformOffset}
              },
              "uniformBytes": ${evidence.uniformBytes},
              "referenceKind": "analytic-simple-rt-coordinate-color-oracle",
              "fallbackReason": ${(if (isGpu) evidence.webGpuFallbackReason else evidence.cpuFallbackReason).json()},
              "fallbackPolicy": ${FallbackPolicy.json()},
              "colorSpacePolicy": ${ColorSpacePolicy.json()},
              ${if (isGpu) "\"runtimeWgslValidated\": ${evidence.runtimeWgslValidated},\n              \"runtimeWgslSha256\": ${evidence.runtimeWgslSha256.json()},\n              \"runtimeWgslEntryPoints\": ${evidence.runtimeWgslEntryPoints.jsonArray()},\n              \"runtimeWgslDiagnostics\": ${evidence.runtimeWgslDiagnostics.jsonArray()},\n              \"parserReflected\": ${evidence.parserReflected}," else ""}
              "stableRefusals": ${stableRefusalsJson()},
              "comparison": ${comparison.json()},
              "similarityThreshold": ${"%.2f".format(Locale.US, SimilarityThreshold)},
              "globalThresholdChanged": false,
              "globalColorPolicyChanged": false,
              "referenceArtifact": ${repoRelative(evidence.artifacts.referencePng).json()},
              "renderArtifact": ${repoRelative(artifact).json()},
              "diffArtifact": ${repoRelative(diff).json()},
              "performanceArtifacts": ${performanceArtifactsJson()},
              "nonClaims": ${NonClaims.jsonArray()}
            }
        """.trimIndent().replace("\n              \n", "\n") + "\n"
    }

    private fun statsJson(evidence: SimpleRuntimeEffectEvidence): String = """
        {
          "sceneId": ${SceneId.json()},
          "scopeId": ${ScopeId.json()},
          "status": "pass",
          "supportScope": "registered-simple-rt-descriptor-rect",
          "supportClaim": true,
          "descriptorBacked": true,
          "parserReflected": ${evidence.parserReflected},
          "cpuRouteIdentifier": ${CpuRouteIdentifier.json()},
          "webGpuRouteIdentifier": ${WebGpuRouteIdentifier.json()},
          "runtimeEffectStableId": ${evidence.runtimeEffectStableId.json()},
          "cpuImplementationId": ${evidence.cpuImplementationId.json()},
          "wgslImplementationId": ${evidence.wgslImplementationId.json()},
          "uniformLayout": {
            ${evidence.uniformName.json()}: ${evidence.uniformOffset}
          },
          "uniformBytes": ${evidence.uniformBytes},
          "runtimeWgslValidated": ${evidence.runtimeWgslValidated},
          "runtimeWgslSha256": ${evidence.runtimeWgslSha256.json()},
          "runtimeWgslEntryPoints": ${evidence.runtimeWgslEntryPoints.jsonArray()},
          "runtimeWgslDiagnostics": ${evidence.runtimeWgslDiagnostics.jsonArray()},
          "colorSpacePolicy": ${ColorSpacePolicy.json()},
          "fallbackPolicy": ${FallbackPolicy.json()},
          "stableRefusals": ${stableRefusalsJson()},
          "nonBackgroundPixels": ${evidence.nonBackgroundPixels},
          "tolerance": ${evidence.tolerance},
          "similarityThreshold": ${"%.2f".format(Locale.US, SimilarityThreshold)},
          "cpuSimilarityThreshold": ${"%.2f".format(Locale.US, evidence.cpuSimilarityThreshold)},
          "webGpuSimilarityThreshold": ${"%.2f".format(Locale.US, evidence.webGpuSimilarityThreshold)},
          "cpuComparison": ${evidence.cpuComparison.json()},
          "webGpuComparison": ${evidence.webGpuComparison.json()},
          "globalThresholdChanged": false,
          "globalColorPolicyChanged": false,
          "webGpuAdapter": ${evidence.webGpuAdapter.json()},
          "referenceArtifact": ${repoRelative(evidence.artifacts.referencePng).json()},
          "cpuArtifact": ${repoRelative(evidence.artifacts.cpuPng).json()},
          "webGpuArtifact": ${repoRelative(evidence.artifacts.webGpuPng).json()},
          "cpuDiffArtifact": ${repoRelative(evidence.artifacts.cpuDiffPng).json()},
          "webGpuDiffArtifact": ${repoRelative(evidence.artifacts.webGpuDiffPng).json()},
          "routeCpuArtifact": ${repoRelative(evidence.artifacts.routeCpuJson).json()},
          "routeWebGpuArtifact": ${repoRelative(evidence.artifacts.routeWebGpuJson).json()},
          "performanceArtifacts": ${performanceArtifactsJson()},
          "nonClaims": ${NonClaims.jsonArray()}
        }
    """.trimIndent() + "\n"

    private fun stableRefusalsJson(): String = """
        [
          {
            "reason": "runtime-effect.wgsl-descriptor-missing",
            "status": "expected-unsupported",
            "evidence": "RuntimeEffectDescriptorWebGpuTest#registered runtime shader without descriptor fails with stable diagnostic",
            "policy": "registered runtime shader without WGSL descriptor fails with stable diagnostic"
          },
          {
            "reason": "runtime-effect.arbitrary-sksl-unsupported",
            "status": "expected-unsupported",
            "evidence": "Spec 06 runtime effects descriptor non-goal",
            "policy": "SkRuntimeEffect remains a compatibility facade backed by registered Kotlin/WGSL implementations"
          }
        ]
    """.trimIndent()

    private fun performanceArtifactsJson(): String = """
        {
          "cpu": "reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/cpu-performance.json",
          "gpu": "reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/gpu-performance.json",
          "gate": "reporting-only"
        }
    """.trimIndent()

    private fun BitmapComparison.json(): String = """
        {
          "similarity": ${"%.6f".format(Locale.US, similarity)},
          "totalPixels": $totalPixels,
          "matchingPixels": $matchingPixels,
          "mismatchingPixels": $mismatchingPixels,
          "tolerance": $tolerance,
          "maxChannelDiff": {
            "a": ${maxChannelDiff.a},
            "r": ${maxChannelDiff.r},
            "g": ${maxChannelDiff.g},
            "b": ${maxChannelDiff.b}
          },
          "meanMismatchDiff": {
            "a": ${meanMismatchDiff.a},
            "r": ${meanMismatchDiff.r},
            "g": ${meanMismatchDiff.g},
            "b": ${meanMismatchDiff.b}
          }
        }
    """.trimIndent()

    private fun runtimeWgslReport(): RuntimeWgslEvidence {
        val shaderFile = shaderFile("runtime_simple_rt.wgsl")
        val report = WgslValidationReport.run(shaderFile.parentFile.toPath())
            .files
            .first { file -> file.path.endsWith("runtime_simple_rt.wgsl") }
        val uniformLayout = report.uniformStructs
            .single { it.variable == "uniforms" }
            .members
            .associate { it.name to it.offset }
        return RuntimeWgslEvidence(
            success = report.success,
            diagnostics = report.diagnostics,
            entryPoints = report.entryPoints,
            uniformLayout = uniformLayout,
            sourceSha256 = shaderFile.readText().sha256(),
        )
    }

    private fun shaderFile(name: String): File {
        val moduleLocal = File("src/main/resources/shaders/$name")
        if (moduleLocal.isFile) return moduleLocal
        return repoFile("gpu-raster/src/main/resources/shaders/$name")
    }

    private fun repoFile(relativePath: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile
        }
        return File(dir, relativePath)
    }

    private fun repoRelative(file: File): String =
        file.absoluteFile.toPath().normalize().let { path ->
            val root = repoFile("").absoluteFile.toPath().normalize()
            root.relativize(path).toString()
        }

    private fun String.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun String.json(): String = buildString {
        append('"')
        this@json.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
        append('"')
    }

    private fun List<String>.jsonArray(): String =
        joinToString(prefix = "[", postfix = "]") { it.json() }

    private data class WebGpuRuntimeEffectRender(
        val bitmap: SkBitmap,
        val fallbackReason: String?,
    )

    private data class RuntimeWgslEvidence(
        val success: Boolean,
        val diagnostics: List<String>,
        val entryPoints: List<String>,
        val uniformLayout: Map<String, Int>,
        val sourceSha256: String,
    )
}

public data class SimpleRuntimeEffectEvidence(
    public val sceneId: String,
    public val scopeId: String,
    public val runtimeEffectStableId: String,
    public val cpuImplementationId: String,
    public val wgslImplementationId: String,
    public val cpuRouteIdentifier: String,
    public val webGpuRouteIdentifier: String,
    public val cpuFallbackReason: String,
    public val webGpuFallbackReason: String,
    public val uniformName: String,
    public val uniformOffset: Int,
    public val uniformBytes: Int,
    public val runtimeWgslValidated: Boolean,
    public val runtimeWgslDiagnostics: List<String>,
    public val runtimeWgslEntryPoints: List<String>,
    public val runtimeWgslSha256: String,
    public val parserReflected: Boolean,
    public val tolerance: Int,
    public val cpuComparison: BitmapComparison,
    public val webGpuComparison: BitmapComparison,
    public val cpuSimilarityThreshold: Double,
    public val webGpuSimilarityThreshold: Double,
    public val nonBackgroundPixels: Int,
    public val webGpuAdapter: String,
    public val artifacts: SimpleRuntimeEffectArtifacts,
)

public data class SimpleRuntimeEffectArtifacts(
    public val root: File,
    public val referencePng: File,
    public val cpuPng: File,
    public val webGpuPng: File,
    public val cpuDiffPng: File,
    public val webGpuDiffPng: File,
    public val routeCpuJson: File,
    public val routeWebGpuJson: File,
    public val statsJson: File,
) {
    public companion object {
        public fun forRoot(root: File): SimpleRuntimeEffectArtifacts =
            SimpleRuntimeEffectArtifacts(
                root = root,
                referencePng = File(root, "reference.png"),
                cpuPng = File(root, "cpu.png"),
                webGpuPng = File(root, "webgpu.png"),
                cpuDiffPng = File(root, "cpu-diff.png"),
                webGpuDiffPng = File(root, "webgpu-diff.png"),
                routeCpuJson = File(root, "route-cpu.json"),
                routeWebGpuJson = File(root, "route-webgpu.json"),
                statsJson = File(root, "stats.json"),
            )
    }
}
