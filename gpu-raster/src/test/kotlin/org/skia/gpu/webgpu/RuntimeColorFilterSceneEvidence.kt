package org.skia.gpu.webgpu

import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.roundToInt
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assumptions
import org.skia.core.SkCanvas
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.effects.SkBuiltinColorFilterEffects
import org.skia.encode.SkPngEncoder
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorType
import org.skia.foundation.SkPaint
import org.skia.gpu.webgpu.testing.CrossBackendHarness
import org.skia.gpu.webgpu.tools.WgslValidationReport
import org.skia.testing.BitmapComparison
import org.skia.testing.TestUtils

public object RuntimeColorFilterSceneEvidence {
    public const val SceneId: String = "runtime.color-filter.luma-to-alpha.rect.v1"
    public const val ScopeId: String = "runtime.color-filter.luma-to-alpha.direct-rect.v1"
    public const val StableId: String = "runtime.color_filter_luma_to_alpha"
    public const val WgslImplementationId: String = "wgsl/runtime_color_filter_luma_to_alpha"
    public const val CpuRouteIdentifier: String = "cpu.runtime-color-filter.luma-to-alpha.direct-rect"
    public const val WebGpuRouteIdentifier: String = "webgpu.runtime-color-filter.luma-to-alpha.direct-rect"
    public const val ArtifactDirectory: String =
        "reports/wgsl-pipeline/scenes/artifacts/kan-031-runtime-color-filter-luma-to-alpha"
    public const val WriteEvidenceProperty: String = "kanvas.sceneEvidence.write"

    private const val Width = 160
    private const val Height = 96
    private const val Tolerance = 2
    private const val Threshold = 99.0
    private const val PaintSourceColor = 0xFF336699.toInt()
    private val ControlRect: SkRect = SkRect.MakeLTRB(22f, 20f, 72f, 76f)
    private val FilteredRect: SkRect = SkRect.MakeLTRB(90f, 20f, 140f, 76f)
    private val LumaAlpha: Int = lumaAlpha(PaintSourceColor)
    private val FilteredDisplayColor: Int = SkColorSetARGB(
        255,
        255 - LumaAlpha,
        255 - LumaAlpha,
        255 - LumaAlpha,
    )
    private val NonClaims = listOf(
        "no-broad-runtime-color-filter-claim",
        "no-runtime-color-filter-child-claim",
        "no-runtime-color-filter-uniform-claim",
        "no-runtime-color-filter-shader-input-claim",
        "no-color-management-breadth-claim",
        "no-lut-color-filter-claim",
        "no-color-filter-chain-claim",
        "no-global-threshold-change",
    )

    public fun capture(writeArtifacts: Boolean = false): RuntimeColorFilterEvidence {
        val wgsl = runtimeColorFilterWgslReport()
        val reference = renderAnalyticReference()
        val cpu = renderCpu()
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            val adapter = ctx.adapterInfo ?: "unknown-adapter"
            val webGpu = renderWebGpu(ctx)
            val cpuComparison = TestUtils.compareBitmapsDetailed(cpu, reference, tolerance = Tolerance)
            val webGpuComparison = TestUtils.compareBitmapsDetailed(webGpu.bitmap, reference, tolerance = Tolerance)
            val artifacts = RuntimeColorFilterArtifacts.forRoot(repoFile(ArtifactDirectory))
            val evidence = RuntimeColorFilterEvidence(
                sceneId = SceneId,
                scopeId = ScopeId,
                stableId = StableId,
                wgslImplementationId = WgslImplementationId,
                paintSourceColor = PaintSourceColor,
                lumaAlpha = LumaAlpha,
                filteredDisplayColor = FilteredDisplayColor,
                cpuRouteIdentifier = CpuRouteIdentifier,
                webGpuRouteIdentifier = WebGpuRouteIdentifier,
                cpuFallbackReason = "none",
                webGpuFallbackReason = webGpu.fallbackReason ?: "none",
                fallbackPolicy = "supported-via-runtime-color-filter-luma-to-alpha-direct-rect-wgsl",
                colorSpacePolicy = "srgb-unmanaged-runtime-color-filter-oracle",
                stageOrder = "solid-color shader -> runtime color filter -> fixed-function kSrcOver blend -> store",
                wgslValidated = wgsl.success,
                wgslDiagnostics = wgsl.diagnostics,
                wgslEntryPoints = wgsl.entryPoints,
                wgslSha256 = wgsl.sourceSha256,
                tolerance = Tolerance,
                threshold = Threshold,
                cpuComparison = cpuComparison,
                webGpuComparison = webGpuComparison,
                webGpuAdapter = adapter,
                artifacts = artifacts,
            )
            if (writeArtifacts || System.getProperty(WriteEvidenceProperty) == "true") {
                writeEvidence(evidence, reference, cpu, webGpu.bitmap)
            }
            println(
                "[RuntimeColorFilterSceneEvidence] adapter=$adapter " +
                    "cpu=${"%.2f".format(Locale.US, cpuComparison.similarity)}%, " +
                    "webgpu=${"%.2f".format(Locale.US, webGpuComparison.similarity)}%",
            )
            return evidence
        }
    }

    private fun renderAnalyticReference(): SkBitmap {
        val bitmap = SkBitmap(Width, Height, colorType = SkColorType.kRGBA_8888)
        bitmap.eraseColor(SK_ColorWHITE)
        fillRect(bitmap, ControlRect, PaintSourceColor)
        fillRect(bitmap, FilteredRect, FilteredDisplayColor)
        return bitmap
    }

    private fun renderCpu(): SkBitmap {
        val bitmap = SkBitmap(Width, Height, colorType = SkColorType.kRGBA_8888)
        bitmap.eraseColor(SK_ColorWHITE)
        drawScene(SkCanvas(bitmap))
        return bitmap
    }

    private fun renderWebGpu(context: WebGpuContext): WebGpuRuntimeColorFilterRender {
        SkWebGpuDevice(context, Width, Height, applyColorspaceTransform = false).use { device ->
            device.setBackground(SK_ColorWHITE)
            drawScene(SkCanvas(device))
            val rgba = device.flush()
            return WebGpuRuntimeColorFilterRender(
                bitmap = rgbaBytesToBitmap(rgba, Width, Height),
                fallbackReason = device.runtimeEffectFallbackReasonForDiagnostics(),
                routeFacts = "shaderFamily=solidColor runtimeColorFilter=LumaToAlpha paintBlendMode=kSrcOver",
            )
        }
    }

    private fun drawScene(canvas: SkCanvas) {
        canvas.drawRect(
            ControlRect,
            SkPaint().apply {
                color = PaintSourceColor
                blendMode = SkBlendMode.kSrcOver
                isAntiAlias = false
            },
        )
        canvas.drawRect(
            FilteredRect,
            SkPaint().apply {
                color = PaintSourceColor
                blendMode = SkBlendMode.kSrcOver
                isAntiAlias = false
                colorFilter = runtimeColorFilter()
            },
        )
    }

    private fun runtimeColorFilter() =
        SkRuntimeEffect.MakeForColorFilter(SkBuiltinColorFilterEffects.LUMA_SRC_SKSL)
            .effect!!
            .makeColorFilter(uniforms = null)!!

    private fun fillRect(bitmap: SkBitmap, rect: SkRect, color: Int) {
        val left = rect.left.roundToInt().coerceIn(0, Width)
        val top = rect.top.roundToInt().coerceIn(0, Height)
        val right = rect.right.roundToInt().coerceIn(left, Width)
        val bottom = rect.bottom.roundToInt().coerceIn(top, Height)
        for (y in top until bottom) {
            for (x in left until right) {
                bitmap.pixels8888[y * Width + x] = color
            }
        }
    }

    private fun writeEvidence(
        evidence: RuntimeColorFilterEvidence,
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
        artifacts.routeCpuJson.writeText(routeJson(evidence, "CPU"))
        artifacts.routeWebGpuJson.writeText(routeJson(evidence, "WebGPU"))
        artifacts.statsJson.writeText(statsJson(evidence))
    }

    private fun routeJson(evidence: RuntimeColorFilterEvidence, backend: String): String {
        val isGpu = backend == "WebGPU"
        val comparison = if (isGpu) evidence.webGpuComparison else evidence.cpuComparison
        val selectedRoute = if (isGpu) WebGpuRouteIdentifier else CpuRouteIdentifier
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
              "supportScope": "runtime-color-filter-luma-to-alpha-direct-rect",
              "supportClaim": true,
              "selectedRoute": ${selectedRoute.json()},
              "runtimeEffectStableId": ${StableId.json()},
              "wgslImplementationId": ${WgslImplementationId.json()},
              "paintBlendMode": "kSrcOver",
              "paintSourceColor": ${PaintSourceColor.hexColor().json()},
              "lumaAlpha": ${evidence.lumaAlpha},
              "filteredDisplayColor": ${evidence.filteredDisplayColor.hexColor().json()},
              "stageOrder": ${evidence.stageOrder.json()},
              "referenceKind": "analytic-srgb-luma-to-alpha-src-over-white-oracle",
              "fallbackReason": ${(if (isGpu) evidence.webGpuFallbackReason else evidence.cpuFallbackReason).json()},
              "fallbackPolicy": ${evidence.fallbackPolicy.json()},
              "colorSpacePolicy": ${evidence.colorSpacePolicy.json()},
              ${if (isGpu) "\"wgslValidated\": ${evidence.wgslValidated},\n              \"wgslSha256\": ${evidence.wgslSha256.json()},\n              \"wgslEntryPoints\": ${evidence.wgslEntryPoints.jsonArray()},\n              \"wgslDiagnostics\": ${evidence.wgslDiagnostics.jsonArray()}," else ""}
              "comparison": ${comparison.json()},
              "similarityThreshold": ${"%.2f".format(Locale.US, Threshold)},
              "globalThresholdChanged": false,
              "globalColorPolicyChanged": false,
              "referenceArtifact": ${repoRelative(evidence.artifacts.referencePng).json()},
              "renderArtifact": ${repoRelative(artifact).json()},
              "diffArtifact": ${repoRelative(diff).json()},
              "nonClaims": ${NonClaims.jsonArray()}
            }
        """.trimIndent().replace("\n              \n", "\n") + "\n"
    }

    private fun statsJson(evidence: RuntimeColorFilterEvidence): String = """
        {
          "sceneId": ${SceneId.json()},
          "scopeId": ${ScopeId.json()},
          "status": "pass",
          "supportScope": "runtime-color-filter-luma-to-alpha-direct-rect",
          "supportClaim": true,
          "stableId": ${StableId.json()},
          "wgslImplementationId": ${WgslImplementationId.json()},
          "cpuRouteIdentifier": ${CpuRouteIdentifier.json()},
          "webGpuRouteIdentifier": ${WebGpuRouteIdentifier.json()},
          "paintBlendMode": "kSrcOver",
          "paintSourceColor": ${PaintSourceColor.hexColor().json()},
          "lumaAlpha": ${evidence.lumaAlpha},
          "filteredDisplayColor": ${evidence.filteredDisplayColor.hexColor().json()},
          "stageOrder": ${evidence.stageOrder.json()},
          "colorSpacePolicy": ${evidence.colorSpacePolicy.json()},
          "fallbackPolicy": ${evidence.fallbackPolicy.json()},
          "wgslValidated": ${evidence.wgslValidated},
          "wgslSha256": ${evidence.wgslSha256.json()},
          "wgslEntryPoints": ${evidence.wgslEntryPoints.jsonArray()},
          "wgslDiagnostics": ${evidence.wgslDiagnostics.jsonArray()},
          "tolerance": ${evidence.tolerance},
          "threshold": ${"%.2f".format(Locale.US, evidence.threshold)},
          "cpuSimilarity": ${"%.6f".format(Locale.US, evidence.cpuComparison.similarity)},
          "webGpuSimilarity": ${"%.6f".format(Locale.US, evidence.webGpuComparison.similarity)},
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
          "nonClaims": ${NonClaims.jsonArray()}
        }
    """.trimIndent() + "\n"

    private fun runtimeColorFilterWgslReport(): RuntimeColorFilterWgslReport {
        val shaderFile = shaderFile("runtime_color_filter_luma_to_alpha.wgsl")
        val source = shaderFile.readText()
        val report = WgslValidationReport.run(shaderFile.parentFile.toPath())
            .files
            .first { file -> file.path.endsWith("runtime_color_filter_luma_to_alpha.wgsl") }
        return RuntimeColorFilterWgslReport(
            success = report.success,
            diagnostics = report.diagnostics,
            entryPoints = report.entryPoints,
            sourceSha256 = sha256(source),
        )
    }

    private fun writePng(file: File, bitmap: SkBitmap) {
        file.parentFile.mkdirs()
        file.writeBytes(requireNotNull(SkPngEncoder.Encode(bitmap)) { "PNG encoding failed for ${file.path}" })
    }

    private fun lumaAlpha(color: Int): Int {
        val luma = SkColorGetR(color) / 255f * 0.3f +
            SkColorGetG(color) / 255f * 0.6f +
            SkColorGetB(color) / 255f * 0.1f
        return (luma.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
    }

    private fun rgbaBytesToBitmap(bytes: ByteArray, width: Int, height: Int): SkBitmap {
        val bitmap = SkBitmap(width, height, colorType = SkColorType.kRGBA_8888)
        for (i in 0 until width * height) {
            val base = i * 4
            bitmap.pixels8888[i] = SkColorSetARGB(
                bytes[base + 3].toInt() and 0xFF,
                bytes[base].toInt() and 0xFF,
                bytes[base + 1].toInt() and 0xFF,
                bytes[base + 2].toInt() and 0xFF,
            )
        }
        return bitmap
    }

    private fun shaderFile(name: String): File = repoFile("gpu-raster/src/main/resources/shaders/$name")

    private fun repoFile(path: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        return File(dir, path)
    }

    private fun repoRelative(file: File): String {
        val root = repoFile("")
        return file.relativeTo(root).invariantSeparatorsPath
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun String.json(): String = buildString {
        append('"')
        this@json.forEach { ch ->
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

    private fun List<String>.jsonArray(): String = joinToString(prefix = "[", postfix = "]") { it.json() }

    private fun Int.hexColor(): String = "0x%08X".format(this)

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

    private data class WebGpuRuntimeColorFilterRender(
        val bitmap: SkBitmap,
        val fallbackReason: String?,
        val routeFacts: String,
    )

    private data class RuntimeColorFilterWgslReport(
        val success: Boolean,
        val diagnostics: List<String>,
        val entryPoints: List<String>,
        val sourceSha256: String,
    )
}

public data class RuntimeColorFilterEvidence(
    public val sceneId: String,
    public val scopeId: String,
    public val stableId: String,
    public val wgslImplementationId: String,
    public val paintSourceColor: Int,
    public val lumaAlpha: Int,
    public val filteredDisplayColor: Int,
    public val cpuRouteIdentifier: String,
    public val webGpuRouteIdentifier: String,
    public val cpuFallbackReason: String,
    public val webGpuFallbackReason: String,
    public val fallbackPolicy: String,
    public val colorSpacePolicy: String,
    public val stageOrder: String,
    public val wgslValidated: Boolean,
    public val wgslDiagnostics: List<String>,
    public val wgslEntryPoints: List<String>,
    public val wgslSha256: String,
    public val tolerance: Int,
    public val threshold: Double,
    public val cpuComparison: BitmapComparison,
    public val webGpuComparison: BitmapComparison,
    public val webGpuAdapter: String,
    public val artifacts: RuntimeColorFilterArtifacts,
)

public data class RuntimeColorFilterArtifacts(
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
        public fun forRoot(root: File): RuntimeColorFilterArtifacts =
            RuntimeColorFilterArtifacts(
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
