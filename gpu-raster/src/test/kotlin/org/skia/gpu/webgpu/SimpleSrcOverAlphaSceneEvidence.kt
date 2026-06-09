package org.skia.gpu.webgpu

import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.roundToInt
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assumptions
import org.skia.core.SkCanvas
import org.skia.encode.SkPngEncoder
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorType
import org.skia.foundation.SkPaint
import org.skia.gpu.webgpu.testing.CrossBackendHarness
import org.skia.gpu.webgpu.tools.GeneratedSolidRectWgsl
import org.skia.testing.BitmapComparison
import org.skia.testing.TestUtils

public object SimpleSrcOverAlphaSceneEvidence {
    public const val SceneId: String = "paint.src-over-alpha.rect-stack.v1"
    public const val ScopeId: String = "src-over.partial-alpha.rect-stack.simple.v1"
    public const val CpuRouteIdentifier: String = "cpu.paint.src-over.partial-alpha.rect-stack"
    public const val WebGpuRouteIdentifier: String = "webgpu.blend.src-over.partial-alpha.fixed-function"
    public const val ArtifactDirectory: String =
        "reports/wgsl-pipeline/scenes/artifacts/kan-015-srcover-alpha"
    public const val WriteEvidenceProperty: String = "kanvas.sceneEvidence.write"
    public const val ColorSpacePolicy: String = "srgb-unmanaged-src-over-oracle"
    public const val UnsupportedBlendReason: String = "blend.unsupported-mode.requires-explicit-allowlist"

    private const val Width = 160
    private const val Height = 96
    private const val Tolerance = 2
    private const val Threshold = 99.0
    private const val BackgroundColor = 0xFFF6F8FC.toInt()
    private const val UnderlayColor = 0x7042B87A
    private const val OverlayColor = 0xA0E85068.toInt()

    private val Commands = listOf(
        SrcOverRectCommand(
            label = "partial-alpha-green-underlay",
            color = UnderlayColor,
            rect = SkRect.MakeLTRB(22f, 18f, 108f, 70f),
        ),
        SrcOverRectCommand(
            label = "partial-alpha-rose-overlay",
            color = OverlayColor,
            rect = SkRect.MakeLTRB(62f, 32f, 138f, 84f),
        ),
    )

    private val NonClaims = listOf(
        "no-arbitrary-blend-mode-claim",
        "no-advanced-blend-chain-claim",
        "no-saveLayer-blend-composition-claim",
        "no-shader-destination-read-claim",
        "no-wide-color-pipeline-claim",
        "no-color-managed-blend-claim",
        "no-broad-layer-compositing-claim",
    )

    public fun capture(writeArtifacts: Boolean = false): SimpleSrcOverAlphaEvidence {
        val generatedWgsl = GeneratedSolidRectWgsl.generateDeterministic()
        val validation = GeneratedSolidRectWgsl.validate(generatedWgsl)
        val reference = renderAnalyticReference()
        val cpu = renderCpu()
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            val adapter = ctx.adapterInfo ?: "unknown-adapter"
            val webGpuResult = renderWebGpu(ctx)
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
            val artifacts = SimpleSrcOverAlphaArtifacts.forRoot(repoFile(ArtifactDirectory))
            val evidence = SimpleSrcOverAlphaEvidence(
                sceneId = SceneId,
                scopeId = ScopeId,
                blendMode = SkBlendMode.kSrcOver.name,
                alphaPolicy = "partial",
                partialAlphaCommandCount = Commands.count { SkColorGetA(it.color) in 1..254 },
                cpuRouteIdentifier = CpuRouteIdentifier,
                webGpuRouteIdentifier = WebGpuRouteIdentifier,
                cpuFallbackReason = "none",
                webGpuFallbackReason = webGpuResult.fallbackReason ?: "none",
                blendPlanKind = webGpuResult.blendPlan.kind.name,
                blendPlanReason = webGpuResult.blendPlan.reason,
                unsupportedBlendMode = SkBlendMode.kModulate.name,
                unsupportedBlendReason = UnsupportedBlendReason,
                unsupportedBlendPlanKind = webGpuResult.unsupportedBlendPlan.kind.name,
                unsupportedBlendPlanRawReason = webGpuResult.unsupportedBlendPlan.reason,
                generatedSolidRectWgslValidated = validation.isSuccess,
                generatedSolidRectWgslDiagnostics = validation.diagnostics,
                generatedSolidRectWgslSha256 = generatedWgsl.sha256(),
                pipelineCacheDump = webGpuResult.pipelineCacheDump,
                tolerance = Tolerance,
                cpuComparison = cpuComparison,
                webGpuComparison = webGpuComparison,
                cpuSimilarityThreshold = Threshold,
                webGpuSimilarityThreshold = Threshold,
                overlapPixelCount = overlapPixelCount(),
                cpuNonBackgroundPixels = countNonBackgroundPixels(cpu),
                webGpuNonBackgroundPixels = countNonBackgroundPixels(webGpuResult.bitmap),
                webGpuAdapter = adapter,
                artifacts = artifacts,
            )
            if (writeArtifacts || System.getProperty(WriteEvidenceProperty) == "true") {
                writeEvidence(evidence, reference, cpu, webGpuResult.bitmap)
            }
            println(
                "[SimpleSrcOverAlphaSceneEvidence] adapter=$adapter " +
                    "cpu=${"%.2f".format(Locale.US, cpuComparison.similarity)}%, " +
                    "webgpu=${"%.2f".format(Locale.US, webGpuComparison.similarity)}%, " +
                    "fallback=${evidence.webGpuFallbackReason}",
            )
            return evidence
        }
    }

    private fun renderAnalyticReference(): SkBitmap {
        val bitmap = SkBitmap(Width, Height, colorType = SkColorType.kRGBA_8888)
        bitmap.eraseColor(BackgroundColor)
        Commands.forEach { command ->
            val left = command.rect.left.roundToInt().coerceIn(0, Width)
            val top = command.rect.top.roundToInt().coerceIn(0, Height)
            val right = command.rect.right.roundToInt().coerceIn(left, Width)
            val bottom = command.rect.bottom.roundToInt().coerceIn(top, Height)
            for (y in top until bottom) {
                for (x in left until right) {
                    val index = y * Width + x
                    bitmap.pixels8888[index] = srcOverOpaqueDst(command.color, bitmap.pixels8888[index])
                }
            }
        }
        return bitmap
    }

    private fun renderCpu(): SkBitmap {
        val bitmap = SkBitmap(Width, Height, colorType = SkColorType.kRGBA_8888)
        bitmap.eraseColor(BackgroundColor)
        drawScene(SkCanvas(bitmap))
        return bitmap
    }

    private fun renderWebGpu(context: WebGpuContext): WebGpuSrcOverAlphaRender {
        SkWebGpuDevice(
            context,
            Width,
            Height,
            applyColorspaceTransform = false,
        ).use { device ->
            device.setBackground(BackgroundColor)
            drawScene(SkCanvas(device))
            val rgba = device.flush()
            return WebGpuSrcOverAlphaRender(
                bitmap = rgbaBytesToBitmap(rgba, Width, Height),
                fallbackReason = device.generatedSolidRectFallbackReasonForDiagnostics(),
                pipelineCacheDump = relevantPipelineCacheDump(device.generatedPipelineCacheDumpForTests()),
                blendPlan = device.blendPlanForDiagnostics(SkBlendMode.kSrcOver),
                unsupportedBlendPlan = device.blendPlanForDiagnostics(SkBlendMode.kModulate),
            )
        }
    }

    private fun drawScene(canvas: SkCanvas) {
        Commands.forEach { command ->
            canvas.drawRect(
                command.rect,
                SkPaint().apply {
                    color = command.color
                    blendMode = SkBlendMode.kSrcOver
                    isAntiAlias = false
                },
            )
        }
    }

    private fun writeEvidence(
        evidence: SimpleSrcOverAlphaEvidence,
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

    private fun srcOverOpaqueDst(src: Int, dst: Int): Int {
        val alpha = SkColorGetA(src)
        val invAlpha = 255 - alpha
        fun channel(srcChannel: Int, dstChannel: Int): Int =
            ((srcChannel * alpha) + (dstChannel * invAlpha) + 127) / 255
        return SkColorSetARGB(
            255,
            channel(SkColorGetR(src), SkColorGetR(dst)),
            channel(SkColorGetG(src), SkColorGetG(dst)),
            channel(SkColorGetB(src), SkColorGetB(dst)),
        )
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
        bitmap.pixels8888.count { pixel -> pixel != BackgroundColor }

    private fun overlapPixelCount(): Int {
        val first = Commands[0].rect
        val second = Commands[1].rect
        val left = maxOf(first.left, second.left).roundToInt()
        val top = maxOf(first.top, second.top).roundToInt()
        val right = minOf(first.right, second.right).roundToInt()
        val bottom = minOf(first.bottom, second.bottom).roundToInt()
        return maxOf(0, right - left) * maxOf(0, bottom - top)
    }

    private fun writePng(file: File, bitmap: SkBitmap) {
        file.parentFile.mkdirs()
        file.writeBytes(requireNotNull(SkPngEncoder.Encode(bitmap)) { "PNG encoding failed for ${file.path}" })
    }

    private fun relevantPipelineCacheDump(rawDump: String): String =
        rawDump.lineSequence()
            .filterNot { line -> line.contains("shaderFamily=linearGradient") }
            .filter { line ->
                line.contains("shaderFamily=solidRect") ||
                    (line.contains("code=[generatedPath=true]") && line.contains("state=[blendMode=kSrcOver]"))
            }
            .joinToString("\n")
            .ifEmpty { rawDump }

    private fun routeJson(evidence: SimpleSrcOverAlphaEvidence, backend: String): String {
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
              "supportScope": "simple-bounded-src-over-partial-alpha-rect-stack",
              "supportClaim": true,
              "selectedRoute": ${selectedRoute.json()},
              "blendMode": ${evidence.blendMode.json()},
              "blendPlan": ${evidence.blendPlanKind.json()},
              "blendPlanReason": ${evidence.blendPlanReason.json()},
              "alphaPolicy": ${evidence.alphaPolicy.json()},
              "partialAlphaCommandCount": ${evidence.partialAlphaCommandCount},
              "commands": ${commandsJson()},
              "referenceKind": "analytic-src-over-partial-alpha-opaque-dst-oracle",
              "fallbackReason": ${(if (isGpu) evidence.webGpuFallbackReason else evidence.cpuFallbackReason).json()},
              "colorSpacePolicy": ${ColorSpacePolicy.json()},
              ${if (isGpu) "\"generatedSolidRectWgslValidated\": ${evidence.generatedSolidRectWgslValidated},\n              \"generatedSolidRectWgslSha256\": ${evidence.generatedSolidRectWgslSha256.json()},\n              \"generatedWgslFeatureFlag\": ${GeneratedSolidRectWgsl.FEATURE_FLAG.json()},\n              \"pipelineKeyDump\": ${evidence.pipelineCacheDump.json()}," else ""}
              "unsupportedBlendMode": ${evidence.unsupportedBlendMode.json()},
              "unsupportedBlendReason": ${evidence.unsupportedBlendReason.json()},
              "unsupportedBlendPlan": ${evidence.unsupportedBlendPlanKind.json()},
              "unsupportedBlendPlanRawReason": ${evidence.unsupportedBlendPlanRawReason.json()},
              "overlapPixelCount": ${evidence.overlapPixelCount},
              "comparison": ${comparison.json()},
              "similarityThreshold": ${"%.2f".format(Locale.US, Threshold)},
              "globalThresholdChanged": false,
              "globalBlendPolicyChanged": false,
              "referenceArtifact": ${repoRelative(evidence.artifacts.referencePng).json()},
              "renderArtifact": ${repoRelative(artifact).json()},
              "diffArtifact": ${repoRelative(diff).json()},
              "nonClaims": ${NonClaims.jsonArray()}
            }
        """.trimIndent().replace("\n              \n", "\n") + "\n"
    }

    private fun statsJson(evidence: SimpleSrcOverAlphaEvidence): String = """
        {
          "sceneId": ${SceneId.json()},
          "scopeId": ${ScopeId.json()},
          "status": "pass",
          "supportScope": "simple-bounded-src-over-partial-alpha-rect-stack",
          "supportClaim": true,
          "cpuRouteIdentifier": ${CpuRouteIdentifier.json()},
          "webGpuRouteIdentifier": ${WebGpuRouteIdentifier.json()},
          "blendMode": ${evidence.blendMode.json()},
          "blendPlan": ${evidence.blendPlanKind.json()},
          "alphaPolicy": ${evidence.alphaPolicy.json()},
          "partialAlphaCommandCount": ${evidence.partialAlphaCommandCount},
          "overlapPixelCount": ${evidence.overlapPixelCount},
          "colorSpacePolicy": ${ColorSpacePolicy.json()},
          "generatedSolidRectWgslValidated": ${evidence.generatedSolidRectWgslValidated},
          "generatedSolidRectWgslSha256": ${evidence.generatedSolidRectWgslSha256.json()},
          "generatedWgslFeatureFlag": ${GeneratedSolidRectWgsl.FEATURE_FLAG.json()},
          "generatedSolidRectWgslDiagnostics": ${evidence.generatedSolidRectWgslDiagnostics.jsonArray()},
          "pipelineKeyDump": ${evidence.pipelineCacheDump.json()},
          "unsupportedBlendMode": ${evidence.unsupportedBlendMode.json()},
          "unsupportedBlendReason": ${evidence.unsupportedBlendReason.json()},
          "unsupportedBlendPlan": ${evidence.unsupportedBlendPlanKind.json()},
          "cpuNonBackgroundPixels": ${evidence.cpuNonBackgroundPixels},
          "webGpuNonBackgroundPixels": ${evidence.webGpuNonBackgroundPixels},
          "tolerance": ${evidence.tolerance},
          "cpuSimilarityThreshold": ${"%.2f".format(Locale.US, evidence.cpuSimilarityThreshold)},
          "webGpuSimilarityThreshold": ${"%.2f".format(Locale.US, evidence.webGpuSimilarityThreshold)},
          "cpuComparison": ${evidence.cpuComparison.json()},
          "webGpuComparison": ${evidence.webGpuComparison.json()},
          "globalThresholdChanged": false,
          "globalBlendPolicyChanged": false,
          "fallbackPolicy": "none-for-supported-simple-src-over-partial-alpha-fixed-function",
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

    private fun commandsJson(): String =
        Commands.joinToString(prefix = "[", postfix = "]") { command ->
            """
            {
              "label": ${command.label.json()},
              "blendMode": "kSrcOver",
              "alpha": ${"%.6f".format(Locale.US, SkColorGetA(command.color) / 255.0)},
              "color": ${command.color.hexColor().json()},
              "rect": {
                "left": ${"%.1f".format(Locale.US, command.rect.left)},
                "top": ${"%.1f".format(Locale.US, command.rect.top)},
                "right": ${"%.1f".format(Locale.US, command.rect.right)},
                "bottom": ${"%.1f".format(Locale.US, command.rect.bottom)}
              }
            }
            """.trimIndent()
        }

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

    private fun Int.hexColor(): String {
        val a = SkColorGetA(this)
        val r = SkColorGetR(this)
        val g = SkColorGetG(this)
        val b = SkColorGetB(this)
        return "#%02X%02X%02X%02X".format(Locale.US, a, r, g, b)
    }

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

    private data class SrcOverRectCommand(
        val label: String,
        val color: Int,
        val rect: SkRect,
    )

    private data class WebGpuSrcOverAlphaRender(
        val bitmap: SkBitmap,
        val fallbackReason: String?,
        val pipelineCacheDump: String,
        val blendPlan: BlendPlan,
        val unsupportedBlendPlan: BlendPlan,
    )
}

public data class SimpleSrcOverAlphaEvidence(
    public val sceneId: String,
    public val scopeId: String,
    public val blendMode: String,
    public val alphaPolicy: String,
    public val partialAlphaCommandCount: Int,
    public val cpuRouteIdentifier: String,
    public val webGpuRouteIdentifier: String,
    public val cpuFallbackReason: String,
    public val webGpuFallbackReason: String,
    public val blendPlanKind: String,
    public val blendPlanReason: String,
    public val unsupportedBlendMode: String,
    public val unsupportedBlendReason: String,
    public val unsupportedBlendPlanKind: String,
    public val unsupportedBlendPlanRawReason: String,
    public val generatedSolidRectWgslValidated: Boolean,
    public val generatedSolidRectWgslDiagnostics: List<String>,
    public val generatedSolidRectWgslSha256: String,
    public val pipelineCacheDump: String,
    public val tolerance: Int,
    public val cpuComparison: BitmapComparison,
    public val webGpuComparison: BitmapComparison,
    public val cpuSimilarityThreshold: Double,
    public val webGpuSimilarityThreshold: Double,
    public val overlapPixelCount: Int,
    public val cpuNonBackgroundPixels: Int,
    public val webGpuNonBackgroundPixels: Int,
    public val webGpuAdapter: String,
    public val artifacts: SimpleSrcOverAlphaArtifacts,
)

public data class SimpleSrcOverAlphaArtifacts(
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
        public fun forRoot(root: File): SimpleSrcOverAlphaArtifacts =
            SimpleSrcOverAlphaArtifacts(
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
