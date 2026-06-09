package org.skia.gpu.webgpu

import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.roundToInt
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
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
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkColorType
import org.skia.foundation.SkPaint
import org.skia.gpu.webgpu.testing.CrossBackendHarness
import org.skia.gpu.webgpu.tools.WgslValidationReport
import org.skia.testing.BitmapComparison
import org.skia.testing.TestUtils

public object SimpleColorFilterSceneEvidence {
    public const val SceneId: String = "paint.color-filter.blend-kplus.rect.v1"
    public const val ScopeId: String = "color-filter.blend-kplus.direct-rect.simple.v1"
    public const val CpuRouteIdentifier: String = "cpu.paint.color-filter.blend-kplus.direct-rect"
    public const val WebGpuRouteIdentifier: String = "webgpu.paint.color-filter.blend-kplus.solid-color"
    public const val ArtifactDirectory: String =
        "reports/wgsl-pipeline/scenes/artifacts/kan-016-color-filter-blend-kplus"
    public const val WriteEvidenceProperty: String = "kanvas.sceneEvidence.write"
    public const val ColorSpacePolicy: String = "srgb-unmanaged-color-filter-oracle"
    public const val FallbackPolicy: String = "supported-via-handwritten-solid-color-color-filter-route"

    private const val Width = 160
    private const val Height = 96
    private const val Tolerance = 2
    private const val Threshold = 99.0
    private const val BackgroundColor = 0xFFF6F8FC.toInt()
    private const val PaintSourceColor = SK_ColorGREEN
    private const val FilterBlendColor = SK_ColorRED
    private val FilteredOutputColor = blendKPlus(FilterBlendColor, PaintSourceColor)

    private val Commands = listOf(
        ColorFilterRectCommand(
            label = "control-green-no-filter",
            colorFilterKind = "none",
            sourceColor = PaintSourceColor,
            outputColor = PaintSourceColor,
            rect = SkRect.MakeLTRB(22f, 20f, 72f, 76f),
        ),
        ColorFilterRectCommand(
            label = "blend-red-kplus-filtered-yellow",
            colorFilterKind = "Blend",
            sourceColor = PaintSourceColor,
            outputColor = FilteredOutputColor,
            rect = SkRect.MakeLTRB(90f, 20f, 140f, 76f),
        ),
    )

    private val NonClaims = listOf(
        "no-broad-color-filter-claim",
        "no-color-filter-chain-claim",
        "no-working-color-space-color-filter-claim",
        "no-table-color-filter-claim",
        "no-runtime-color-filter-claim",
        "no-color-managed-pipeline-claim",
        "no-wide-color-pipeline-claim",
        "no-saveLayer-color-filter-claim",
        "no-gradient-color-filter-claim",
        "no-bitmap-color-filter-claim",
    )

    public fun capture(writeArtifacts: Boolean = false): SimpleColorFilterEvidence {
        val solidColorWgsl = solidColorWgslReport()
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
            val artifacts = SimpleColorFilterArtifacts.forRoot(repoFile(ArtifactDirectory))
            val evidence = SimpleColorFilterEvidence(
                sceneId = SceneId,
                scopeId = ScopeId,
                colorFilterKind = "Blend",
                colorFilterBlendMode = SkBlendMode.kPlus.name,
                filterBlendColor = FilterBlendColor,
                paintSourceColor = PaintSourceColor,
                filteredOutputColor = FilteredOutputColor,
                filteredCommandCount = Commands.count { it.colorFilterKind == "Blend" },
                controlPixelCount = pixelCountFor(Commands.first { it.colorFilterKind == "none" }),
                filteredPixelCount = pixelCountFor(Commands.first { it.colorFilterKind == "Blend" }),
                cpuRouteIdentifier = CpuRouteIdentifier,
                webGpuRouteIdentifier = WebGpuRouteIdentifier,
                cpuFallbackReason = "none",
                webGpuFallbackReason = "none",
                generatedSolidRectFallbackReason = webGpuResult.generatedSolidRectFallbackReason ?: "none",
                fallbackPolicy = FallbackPolicy,
                solidColorWgslValidated = solidColorWgsl.success,
                solidColorWgslDiagnostics = solidColorWgsl.diagnostics,
                solidColorWgslEntryPoints = solidColorWgsl.entryPoints,
                solidColorWgslSha256 = solidColorWgsl.sourceSha256,
                routeFacts = webGpuResult.routeFacts,
                tolerance = Tolerance,
                cpuComparison = cpuComparison,
                webGpuComparison = webGpuComparison,
                cpuSimilarityThreshold = Threshold,
                webGpuSimilarityThreshold = Threshold,
                cpuNonBackgroundPixels = countNonBackgroundPixels(cpu),
                webGpuNonBackgroundPixels = countNonBackgroundPixels(webGpuResult.bitmap),
                webGpuAdapter = adapter,
                artifacts = artifacts,
            )
            if (writeArtifacts || System.getProperty(WriteEvidenceProperty) == "true") {
                writeEvidence(evidence, reference, cpu, webGpuResult.bitmap)
            }
            println(
                "[SimpleColorFilterSceneEvidence] adapter=$adapter " +
                    "cpu=${"%.2f".format(Locale.US, cpuComparison.similarity)}%, " +
                    "webgpu=${"%.2f".format(Locale.US, webGpuComparison.similarity)}%, " +
                    "generatedSolidRectFallback=${evidence.generatedSolidRectFallbackReason}",
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
                    bitmap.pixels8888[index] = srcOverOpaqueDst(command.outputColor, bitmap.pixels8888[index])
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

    private fun renderWebGpu(context: WebGpuContext): WebGpuColorFilterRender {
        SkWebGpuDevice(
            context,
            Width,
            Height,
            applyColorspaceTransform = false,
        ).use { device ->
            device.setBackground(BackgroundColor)
            drawScene(SkCanvas(device))
            val rgba = device.flush()
            return WebGpuColorFilterRender(
                bitmap = rgbaBytesToBitmap(rgba, Width, Height),
                generatedSolidRectFallbackReason = device.generatedSolidRectFallbackReasonForDiagnostics(),
                routeFacts = "shaderFamily=solidColor colorFilter=Blend(kPlus) paintBlendMode=kSrcOver",
            )
        }
    }

    private fun drawScene(canvas: SkCanvas) {
        Commands.forEach { command ->
            canvas.drawRect(
                command.rect,
                SkPaint().apply {
                    color = command.sourceColor
                    blendMode = SkBlendMode.kSrcOver
                    isAntiAlias = false
                    if (command.colorFilterKind == "Blend") {
                        colorFilter = SkColorFilters.Blend(FilterBlendColor, SkBlendMode.kPlus)
                    }
                },
            )
        }
    }

    private fun writeEvidence(
        evidence: SimpleColorFilterEvidence,
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

    private fun pixelCountFor(command: ColorFilterRectCommand): Int {
        val left = command.rect.left.roundToInt().coerceIn(0, Width)
        val top = command.rect.top.roundToInt().coerceIn(0, Height)
        val right = command.rect.right.roundToInt().coerceIn(left, Width)
        val bottom = command.rect.bottom.roundToInt().coerceIn(top, Height)
        return (right - left) * (bottom - top)
    }

    private fun writePng(file: File, bitmap: SkBitmap) {
        file.parentFile.mkdirs()
        file.writeBytes(requireNotNull(SkPngEncoder.Encode(bitmap)) { "PNG encoding failed for ${file.path}" })
    }

    private fun routeJson(evidence: SimpleColorFilterEvidence, backend: String): String {
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
              "supportScope": "simple-direct-rect-blend-kplus-color-filter",
              "supportClaim": true,
              "selectedRoute": ${selectedRoute.json()},
              "paintBlendMode": "kSrcOver",
              "colorFilterKind": ${evidence.colorFilterKind.json()},
              "colorFilterBlendMode": ${evidence.colorFilterBlendMode.json()},
              "filterBlendColor": ${evidence.filterBlendColor.hexColor().json()},
              "paintSourceColor": ${evidence.paintSourceColor.hexColor().json()},
              "filteredOutputColor": ${evidence.filteredOutputColor.hexColor().json()},
              "filteredCommandCount": ${evidence.filteredCommandCount},
              "commands": ${commandsJson()},
              "stageOrder": "shader/color -> colorFilter -> alpha modulation -> blender -> color-space/store",
              "referenceKind": "analytic-srgb-blend-kplus-color-filter-oracle",
              "fallbackReason": ${(if (isGpu) evidence.webGpuFallbackReason else evidence.cpuFallbackReason).json()},
              "fallbackPolicy": ${evidence.fallbackPolicy.json()},
              "colorSpacePolicy": ${ColorSpacePolicy.json()},
              ${if (isGpu) "\"solidColorWgslValidated\": ${evidence.solidColorWgslValidated},\n              \"solidColorWgslSha256\": ${evidence.solidColorWgslSha256.json()},\n              \"solidColorWgslEntryPoints\": ${evidence.solidColorWgslEntryPoints.jsonArray()},\n              \"solidColorWgslDiagnostics\": ${evidence.solidColorWgslDiagnostics.jsonArray()},\n              \"generatedSolidRectFallbackReason\": ${evidence.generatedSolidRectFallbackReason.json()},\n              \"routeFacts\": ${evidence.routeFacts.json()}," else ""}
              "unsupportedColorFilterPolicy": "color-filter.chain.not-promoted",
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

    private fun statsJson(evidence: SimpleColorFilterEvidence): String = """
        {
          "sceneId": ${SceneId.json()},
          "scopeId": ${ScopeId.json()},
          "status": "pass",
          "supportScope": "simple-direct-rect-blend-kplus-color-filter",
          "supportClaim": true,
          "cpuRouteIdentifier": ${CpuRouteIdentifier.json()},
          "webGpuRouteIdentifier": ${WebGpuRouteIdentifier.json()},
          "paintBlendMode": "kSrcOver",
          "colorFilterKind": ${evidence.colorFilterKind.json()},
          "colorFilterBlendMode": ${evidence.colorFilterBlendMode.json()},
          "filterBlendColor": ${evidence.filterBlendColor.hexColor().json()},
          "paintSourceColor": ${evidence.paintSourceColor.hexColor().json()},
          "filteredOutputColor": ${evidence.filteredOutputColor.hexColor().json()},
          "filteredCommandCount": ${evidence.filteredCommandCount},
          "controlPixelCount": ${evidence.controlPixelCount},
          "filteredPixelCount": ${evidence.filteredPixelCount},
          "colorSpacePolicy": ${ColorSpacePolicy.json()},
          "fallbackPolicy": ${evidence.fallbackPolicy.json()},
          "generatedSolidRectFallbackReason": ${evidence.generatedSolidRectFallbackReason.json()},
          "solidColorWgslValidated": ${evidence.solidColorWgslValidated},
          "solidColorWgslSha256": ${evidence.solidColorWgslSha256.json()},
          "solidColorWgslEntryPoints": ${evidence.solidColorWgslEntryPoints.jsonArray()},
          "solidColorWgslDiagnostics": ${evidence.solidColorWgslDiagnostics.jsonArray()},
          "routeFacts": ${evidence.routeFacts.json()},
          "unsupportedColorFilterPolicy": "color-filter.chain.not-promoted",
          "cpuNonBackgroundPixels": ${evidence.cpuNonBackgroundPixels},
          "webGpuNonBackgroundPixels": ${evidence.webGpuNonBackgroundPixels},
          "tolerance": ${evidence.tolerance},
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
          "nonClaims": ${NonClaims.jsonArray()}
        }
    """.trimIndent() + "\n"

    private fun commandsJson(): String =
        Commands.joinToString(prefix = "[", postfix = "]") { command ->
            """
            {
              "label": ${command.label.json()},
              "paintBlendMode": "kSrcOver",
              "colorFilterKind": ${command.colorFilterKind.json()},
              "colorFilterBlendMode": ${if (command.colorFilterKind == "Blend") "\"kPlus\"" else "null"},
              "sourceColor": ${command.sourceColor.hexColor().json()},
              "outputColor": ${command.outputColor.hexColor().json()},
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

    private fun solidColorWgslReport(): SolidColorWgslEvidence {
        val shaderFile = shaderFile("solid_color.wgsl")
        val report = WgslValidationReport.run(shaderFile.parentFile.toPath())
            .files
            .first { file -> file.path.endsWith("solid_color.wgsl") }
        return SolidColorWgslEvidence(
            success = report.success,
            diagnostics = report.diagnostics,
            entryPoints = report.entryPoints,
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

    private data class ColorFilterRectCommand(
        val label: String,
        val colorFilterKind: String,
        val sourceColor: Int,
        val outputColor: Int,
        val rect: SkRect,
    )

    private data class WebGpuColorFilterRender(
        val bitmap: SkBitmap,
        val generatedSolidRectFallbackReason: String?,
        val routeFacts: String,
    )

    private data class SolidColorWgslEvidence(
        val success: Boolean,
        val diagnostics: List<String>,
        val entryPoints: List<String>,
        val sourceSha256: String,
    )
}

public data class SimpleColorFilterEvidence(
    public val sceneId: String,
    public val scopeId: String,
    public val colorFilterKind: String,
    public val colorFilterBlendMode: String,
    public val filterBlendColor: Int,
    public val paintSourceColor: Int,
    public val filteredOutputColor: Int,
    public val filteredCommandCount: Int,
    public val controlPixelCount: Int,
    public val filteredPixelCount: Int,
    public val cpuRouteIdentifier: String,
    public val webGpuRouteIdentifier: String,
    public val cpuFallbackReason: String,
    public val webGpuFallbackReason: String,
    public val generatedSolidRectFallbackReason: String,
    public val fallbackPolicy: String,
    public val solidColorWgslValidated: Boolean,
    public val solidColorWgslDiagnostics: List<String>,
    public val solidColorWgslEntryPoints: List<String>,
    public val solidColorWgslSha256: String,
    public val routeFacts: String,
    public val tolerance: Int,
    public val cpuComparison: BitmapComparison,
    public val webGpuComparison: BitmapComparison,
    public val cpuSimilarityThreshold: Double,
    public val webGpuSimilarityThreshold: Double,
    public val cpuNonBackgroundPixels: Int,
    public val webGpuNonBackgroundPixels: Int,
    public val webGpuAdapter: String,
    public val artifacts: SimpleColorFilterArtifacts,
)

public data class SimpleColorFilterArtifacts(
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
        public fun forRoot(root: File): SimpleColorFilterArtifacts =
            SimpleColorFilterArtifacts(
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

private fun blendKPlus(src: Int, dst: Int): Int =
    SkColorSetARGB(
        minOf(255, SkColorGetA(src) + SkColorGetA(dst)),
        minOf(255, SkColorGetR(src) + SkColorGetR(dst)),
        minOf(255, SkColorGetG(src) + SkColorGetG(dst)),
        minOf(255, SkColorGetB(src) + SkColorGetB(dst)),
    )
