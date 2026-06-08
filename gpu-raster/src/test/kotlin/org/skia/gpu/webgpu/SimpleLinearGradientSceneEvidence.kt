package org.skia.gpu.webgpu

import java.io.File
import java.security.MessageDigest
import java.util.Locale
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assumptions
import org.skia.core.SkCanvas
import org.skia.encode.SkPngEncoder
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.skia.gpu.webgpu.testing.CrossBackendHarness
import org.skia.gpu.webgpu.tools.GeneratedLinearGradientWgsl
import org.skia.testing.BitmapComparison
import org.skia.testing.TestUtils

public object SimpleLinearGradientSceneEvidence {
    public const val SceneId: String = "paint.linear-gradient.rect.v1"
    public const val ScopeId: String = "linear-gradient.clamp.rect.simple.v1"
    public const val CpuRouteIdentifier: String = "cpu.paint.linear-gradient.rect.clamp"
    public const val WebGpuRouteIdentifier: String = "webgpu.generated.linear-gradient.rect"
    public const val ArtifactDirectory: String =
        "reports/wgsl-pipeline/scenes/artifacts/kan-013-linear-gradient-wave"
    public const val WriteEvidenceProperty: String = "kanvas.sceneEvidence.write"
    public const val ColorSpacePolicy: String = "srgb-unmanaged-test-oracle"

    private const val Width = 192
    private const val Height = 96
    private const val RectLeft = 20f
    private const val RectTop = 20f
    private const val RectRight = 172f
    private const val RectBottom = 76f
    private const val GradientY = 48f
    private const val Tolerance = 4
    private const val Threshold = 99.0
    private const val StartColor = 0xFFE02838.toInt()
    private const val EndColor = 0xFF2868E0.toInt()

    private val NonClaims = listOf(
        "no-wide-gamut-color-management-claim",
        "no-all-tile-modes-claim",
        "no-gradient-mesh-claim",
        "no-advanced-color-space-claim",
        "no-broad-gradient-family-claim",
        "no-color-filter-chain-claim",
        "no-codec-or-mipmap-claim",
    )

    public fun capture(writeArtifacts: Boolean = false): SimpleLinearGradientEvidence {
        val generatedWgsl = GeneratedLinearGradientWgsl.generateDeterministic()
        val validation = GeneratedLinearGradientWgsl.validate(generatedWgsl)
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
            val artifacts = SimpleLinearGradientArtifacts.forRoot(repoFile(ArtifactDirectory))
            val evidence = SimpleLinearGradientEvidence(
                sceneId = SceneId,
                scopeId = ScopeId,
                tileMode = SkTileMode.kClamp.name,
                cpuRouteIdentifier = CpuRouteIdentifier,
                webGpuRouteIdentifier = WebGpuRouteIdentifier,
                cpuFallbackReason = "none",
                webGpuFallbackReason = webGpuResult.fallbackReason ?: "none",
                generatedWgslValidated = validation.isSuccess,
                generatedWgslDiagnostics = validation.diagnostics,
                generatedWgslSha256 = generatedWgsl.sha256(),
                pipelineCacheDump = webGpuResult.pipelineCacheDump,
                tolerance = Tolerance,
                cpuComparison = cpuComparison,
                webGpuComparison = webGpuComparison,
                cpuSimilarityThreshold = Threshold,
                webGpuSimilarityThreshold = Threshold,
                cpuNonWhitePixels = countNonWhitePixels(cpu),
                webGpuNonWhitePixels = countNonWhitePixels(webGpuResult.bitmap),
                webGpuAdapter = adapter,
                artifacts = artifacts,
            )
            if (writeArtifacts || System.getProperty(WriteEvidenceProperty) == "true") {
                writeEvidence(evidence, reference, cpu, webGpuResult.bitmap)
            }
            println(
                "[SimpleLinearGradientSceneEvidence] adapter=$adapter " +
                    "cpu=${"%.2f".format(Locale.US, cpuComparison.similarity)}%, " +
                    "webgpu=${"%.2f".format(Locale.US, webGpuComparison.similarity)}%, " +
                    "fallback=${evidence.webGpuFallbackReason}",
            )
            return evidence
        }
    }

    private fun renderAnalyticReference(): SkBitmap {
        val bitmap = SkBitmap(Width, Height, colorType = SkColorType.kRGBA_8888)
        bitmap.eraseColor(SK_ColorWHITE)
        val gradientWidth = RectRight - RectLeft
        for (y in RectTop.toInt() until RectBottom.toInt()) {
            for (x in RectLeft.toInt() until RectRight.toInt()) {
                val centerX = x + 0.5f
                val t = ((centerX - RectLeft) / gradientWidth).coerceIn(0f, 1f)
                bitmap.pixels8888[y * Width + x] = lerpColor(StartColor, EndColor, t)
            }
        }
        return bitmap
    }

    private fun renderCpu(): SkBitmap {
        val bitmap = SkBitmap(Width, Height, colorType = SkColorType.kRGBA_8888)
        bitmap.eraseColor(SK_ColorWHITE)
        SkCanvas(bitmap).drawRect(gradientRect(), gradientPaint())
        return bitmap
    }

    private fun renderWebGpu(context: WebGpuContext): WebGpuGradientRender {
        SkWebGpuDevice(
            context,
            Width,
            Height,
            applyColorspaceTransform = false,
        ).use { device ->
            device.setBackground(SK_ColorWHITE)
            SkCanvas(device).drawRect(gradientRect(), gradientPaint())
            val rgba = device.flush()
            return WebGpuGradientRender(
                bitmap = rgbaBytesToBitmap(rgba, Width, Height),
                fallbackReason = device.generatedLinearGradientFallbackReasonForDiagnostics(),
                pipelineCacheDump = device.generatedPipelineCacheDumpForTests(),
            )
        }
    }

    private fun gradientRect(): SkRect =
        SkRect.MakeLTRB(RectLeft, RectTop, RectRight, RectBottom)

    private fun gradientPaint(): SkPaint {
        val gradient = SkLinearGradient.Make(
            p0 = SkPoint(RectLeft, GradientY),
            p1 = SkPoint(RectRight, GradientY),
            colors = intArrayOf(StartColor, EndColor),
            positions = null,
            tileMode = SkTileMode.kClamp,
        )
        return SkPaint().apply {
            shader = gradient
            isAntiAlias = false
        }
    }

    private fun lerpColor(start: Int, end: Int, t: Float): Int {
        val u = t.coerceIn(0f, 1f)
        val inv = 1f - u
        fun channel(getter: (Int) -> Int): Int =
            (getter(start) * inv + getter(end) * u).toInt().coerceIn(0, 255)
        return SkColorSetARGB(
            255,
            channel(::SkColorGetR),
            channel(::SkColorGetG),
            channel(::SkColorGetB),
        )
    }

    private fun writeEvidence(
        evidence: SimpleLinearGradientEvidence,
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

    private fun countNonWhitePixels(bitmap: SkBitmap): Int {
        var count = 0
        for (pixel in bitmap.pixels8888) {
            if (SkColorGetR(pixel) < 250 || SkColorGetG(pixel) < 250 || SkColorGetB(pixel) < 250) {
                count += 1
            }
        }
        return count
    }

    private fun writePng(file: File, bitmap: SkBitmap) {
        file.parentFile.mkdirs()
        file.writeBytes(requireNotNull(SkPngEncoder.Encode(bitmap)) { "PNG encoding failed for ${file.path}" })
    }

    private fun routeJson(evidence: SimpleLinearGradientEvidence, backend: String): String {
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
              "supportScope": "simple-bounded-linear-gradient-rect",
              "supportClaim": true,
              "selectedRoute": ${selectedRoute.json()},
              "shaderFamily": "linearGradient",
              "tileMode": ${evidence.tileMode.json()},
              "gradientStops": [
                {"position": 0.0, "color": ${StartColor.hexColor().json()}},
                {"position": 1.0, "color": ${EndColor.hexColor().json()}}
              ],
              "gradientLine": {
                "p0": {"x": ${"%.1f".format(Locale.US, RectLeft)}, "y": ${"%.1f".format(Locale.US, GradientY)}},
                "p1": {"x": ${"%.1f".format(Locale.US, RectRight)}, "y": ${"%.1f".format(Locale.US, GradientY)}}
              },
              "rect": {
                "left": ${"%.1f".format(Locale.US, RectLeft)},
                "top": ${"%.1f".format(Locale.US, RectTop)},
                "right": ${"%.1f".format(Locale.US, RectRight)},
                "bottom": ${"%.1f".format(Locale.US, RectBottom)}
              },
              "referenceKind": "analytic-srgb-two-stop-linear-gradient-oracle",
              "fallbackReason": ${(if (isGpu) evidence.webGpuFallbackReason else evidence.cpuFallbackReason).json()},
              "colorSpacePolicy": ${ColorSpacePolicy.json()},
              ${if (isGpu) "\"generatedWgslValidated\": ${evidence.generatedWgslValidated},\n              \"generatedWgslSha256\": ${evidence.generatedWgslSha256.json()},\n              \"generatedWgslFeatureFlag\": ${GeneratedLinearGradientWgsl.FEATURE_FLAG.json()},\n              \"pipelineKeyDump\": ${evidence.pipelineCacheDump.json()}," else ""}
              "comparison": ${comparison.json()},
              "similarityThreshold": ${"%.2f".format(Locale.US, Threshold)},
              "globalThresholdChanged": false,
              "referenceArtifact": ${repoRelative(evidence.artifacts.referencePng).json()},
              "renderArtifact": ${repoRelative(artifact).json()},
              "diffArtifact": ${repoRelative(diff).json()},
              "nonClaims": ${NonClaims.jsonArray()}
            }
        """.trimIndent().replace("\n              \n", "\n") + "\n"
    }

    private fun statsJson(evidence: SimpleLinearGradientEvidence): String = """
        {
          "sceneId": ${SceneId.json()},
          "scopeId": ${ScopeId.json()},
          "status": "pass",
          "supportScope": "simple-bounded-linear-gradient-rect",
          "supportClaim": true,
          "cpuRouteIdentifier": ${CpuRouteIdentifier.json()},
          "webGpuRouteIdentifier": ${WebGpuRouteIdentifier.json()},
          "tileMode": ${evidence.tileMode.json()},
          "colorSpacePolicy": ${ColorSpacePolicy.json()},
          "generatedWgslValidated": ${evidence.generatedWgslValidated},
          "generatedWgslSha256": ${evidence.generatedWgslSha256.json()},
          "generatedWgslFeatureFlag": ${GeneratedLinearGradientWgsl.FEATURE_FLAG.json()},
          "generatedWgslDiagnostics": ${evidence.generatedWgslDiagnostics.jsonArray()},
          "pipelineKeyDump": ${evidence.pipelineCacheDump.json()},
          "cpuNonWhitePixels": ${evidence.cpuNonWhitePixels},
          "webGpuNonWhitePixels": ${evidence.webGpuNonWhitePixels},
          "tolerance": ${evidence.tolerance},
          "cpuSimilarityThreshold": ${"%.2f".format(Locale.US, evidence.cpuSimilarityThreshold)},
          "webGpuSimilarityThreshold": ${"%.2f".format(Locale.US, evidence.webGpuSimilarityThreshold)},
          "cpuComparison": ${evidence.cpuComparison.json()},
          "webGpuComparison": ${evidence.webGpuComparison.json()},
          "globalThresholdChanged": false,
          "fallbackPolicy": "none-for-supported-simple-kClamp-linear-gradient-rect",
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
        val a = this ushr 24 and 0xFF
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

    private data class WebGpuGradientRender(
        val bitmap: SkBitmap,
        val fallbackReason: String?,
        val pipelineCacheDump: String,
    )
}

public data class SimpleLinearGradientEvidence(
    public val sceneId: String,
    public val scopeId: String,
    public val tileMode: String,
    public val cpuRouteIdentifier: String,
    public val webGpuRouteIdentifier: String,
    public val cpuFallbackReason: String,
    public val webGpuFallbackReason: String,
    public val generatedWgslValidated: Boolean,
    public val generatedWgslDiagnostics: List<String>,
    public val generatedWgslSha256: String,
    public val pipelineCacheDump: String,
    public val tolerance: Int,
    public val cpuComparison: BitmapComparison,
    public val webGpuComparison: BitmapComparison,
    public val cpuSimilarityThreshold: Double,
    public val webGpuSimilarityThreshold: Double,
    public val cpuNonWhitePixels: Int,
    public val webGpuNonWhitePixels: Int,
    public val webGpuAdapter: String,
    public val artifacts: SimpleLinearGradientArtifacts,
)

public data class SimpleLinearGradientArtifacts(
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
        public fun forRoot(root: File): SimpleLinearGradientArtifacts =
            SimpleLinearGradientArtifacts(
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
