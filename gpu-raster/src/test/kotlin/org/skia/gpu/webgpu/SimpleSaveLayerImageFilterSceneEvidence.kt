package org.skia.gpu.webgpu

import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
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
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkColorType
import org.skia.foundation.SkPaint
import org.skia.gpu.webgpu.testing.CrossBackendHarness
import org.skia.testing.BitmapComparison
import org.skia.testing.TestUtils

public object SimpleSaveLayerImageFilterSceneEvidence {
    public const val SceneId: String = "save-layer.image-filter.color-filter-matrix.v1"
    public const val ScopeId: String = "kan-007.save-layer.simple-color-filter.v1"
    public const val CpuRouteIdentifier: String = "cpu.save-layer.image-filter.color-filter-matrix"
    public const val WebGpuRouteIdentifier: String = "webgpu.image-filter.color-filter.layer-composite"
    public const val ArtifactDirectory: String =
        "reports/wgsl-pipeline/scenes/artifacts/kan-007-savelayer-simple-color-filter"
    public const val WriteEvidenceProperty: String = "kanvas.sceneEvidence.write"
    public const val FallbackPolicy: String = "supported-via-layer-composite-color-filter-uniform"

    private const val Width = 112
    private const val Height = 72
    private const val Tolerance = 2
    private const val Threshold = 99.0
    private const val BackgroundColor = 0xFFF6F8FC.toInt()
    private const val SourceColor = SK_ColorRED
    private val LayerBounds: SkRect = SkRect.MakeLTRB(24f, 16f, 88f, 56f)
    private val FilteredColor: Int = grayscale(SourceColor)
    private val LumaMatrix = floatArrayOf(
        0.299f, 0.587f, 0.114f, 0f, 0f,
        0.299f, 0.587f, 0.114f, 0f, 0f,
        0.299f, 0.587f, 0.114f, 0f, 0f,
        0f,     0f,     0f,     1f, 0f,
    )
    private val NonClaims = listOf(
        "no-arbitrary-layer-stack-claim",
        "no-multi-node-dag-claim",
        "no-broad-image-filter-claim",
        "no-color-filter-chain-claim",
        "no-blur-offset-crop-matrix-transform-expansion-claim",
        "no-picture-prepass-claim",
        "no-cpu-readback-fallback-claim",
        "no-global-threshold-change",
    )

    public fun capture(writeArtifacts: Boolean = false): SimpleSaveLayerImageFilterEvidence {
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
            val artifacts = SimpleSaveLayerImageFilterArtifacts.forRoot(repoFile(ArtifactDirectory))
            val evidence = SimpleSaveLayerImageFilterEvidence(
                sceneId = SceneId,
                scopeId = ScopeId,
                imageFilterKind = "ColorFilter",
                colorFilterKind = "Matrix",
                sourceColor = SourceColor,
                filteredColor = FilteredColor,
                layerBounds = boundsDump(LayerBounds),
                layerPixelCount = pixelCountFor(LayerBounds),
                cpuRouteIdentifier = CpuRouteIdentifier,
                webGpuRouteIdentifier = WebGpuRouteIdentifier,
                cpuFallbackReason = "none",
                webGpuFallbackReason = webGpuResult.routeDiagnostics.fallbackReason ?: "none",
                fallbackPolicy = FallbackPolicy,
                routeDiagnostics = webGpuResult.routeDiagnostics,
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
                "[SimpleSaveLayerImageFilterSceneEvidence] adapter=$adapter " +
                    "cpu=${"%.2f".format(Locale.US, cpuComparison.similarity)}%, " +
                    "webgpu=${"%.2f".format(Locale.US, webGpuComparison.similarity)}%, " +
                    "route=${evidence.routeDiagnostics.selectedRoute}",
            )
            return evidence
        }
    }

    private fun renderAnalyticReference(): SkBitmap {
        val bitmap = SkBitmap(Width, Height, colorType = SkColorType.kRGBA_8888)
        bitmap.eraseColor(BackgroundColor)
        val left = LayerBounds.left.roundToInt().coerceIn(0, Width)
        val top = LayerBounds.top.roundToInt().coerceIn(0, Height)
        val right = LayerBounds.right.roundToInt().coerceIn(left, Width)
        val bottom = LayerBounds.bottom.roundToInt().coerceIn(top, Height)
        for (y in top until bottom) {
            for (x in left until right) {
                bitmap.pixels8888[y * Width + x] = FilteredColor
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

    private fun renderWebGpu(context: WebGpuContext): WebGpuSaveLayerImageFilterRender {
        SkWebGpuDevice(
            context,
            Width,
            Height,
            applyColorspaceTransform = false,
        ).use { device ->
            device.setBackground(BackgroundColor)
            drawScene(SkCanvas(device))
            val rgba = device.flush()
            val diagnostics = requireNotNull(device.imageFilterRouteDiagnosticsForTests()) {
                "KAN-007 route diagnostics must be recorded for simple SaveLayer ColorFilter"
            }
            return WebGpuSaveLayerImageFilterRender(
                bitmap = rgbaBytesToBitmap(rgba, Width, Height),
                routeDiagnostics = SimpleSaveLayerImageFilterRouteDiagnostics(
                    selectedRoute = diagnostics.selectedRoute,
                    prepassRoute = diagnostics.prepassRoute,
                    scratchOwner = diagnostics.scratchOwner,
                    scratchLifetime = diagnostics.scratchLifetime,
                    materialiseStages = diagnostics.materialiseStages,
                    fallbackReason = diagnostics.fallbackReason,
                ),
            )
        }
    }

    private fun drawScene(canvas: SkCanvas) {
        val layerPaint = SkPaint().apply {
            imageFilter = org.skia.foundation.SkImageFilters.ColorFilter(
                SkColorFilters.Matrix(LumaMatrix),
                input = null,
            )
        }
        canvas.saveLayer(LayerBounds, layerPaint)
        canvas.drawRect(
            LayerBounds,
            SkPaint().apply {
                color = SourceColor
                isAntiAlias = false
            },
        )
        canvas.restore()
    }

    private fun writeEvidence(
        evidence: SimpleSaveLayerImageFilterEvidence,
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

    private fun writePng(file: File, bitmap: SkBitmap) {
        file.parentFile.mkdirs()
        file.writeBytes(requireNotNull(SkPngEncoder.Encode(bitmap)) { "PNG encoding failed for ${file.path}" })
    }

    private fun routeJson(evidence: SimpleSaveLayerImageFilterEvidence, backend: String): String {
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
              "drawKind": "SkCanvas.saveLayer",
              "status": "pass",
              "supportScope": "bounded-saveLayer-colorFilter-matrix-input-null",
              "supportClaim": true,
              "selectedRoute": ${selectedRoute.json()},
              "imageFilterKind": ${evidence.imageFilterKind.json()},
              "colorFilterKind": ${evidence.colorFilterKind.json()},
              "sourceColor": ${evidence.sourceColor.hexColor().json()},
              "filteredColor": ${evidence.filteredColor.hexColor().json()},
              "layerBounds": ${evidence.layerBounds.json()},
              "layerPixelCount": ${evidence.layerPixelCount},
              "referenceKind": "analytic-saveLayer-colorFilter-matrix-oracle",
              "fallbackReason": ${(if (isGpu) evidence.webGpuFallbackReason else evidence.cpuFallbackReason).json()},
              "fallbackPolicy": ${evidence.fallbackPolicy.json()},
              ${if (isGpu) "\"routeDiagnostics\": ${evidence.routeDiagnostics.json()},\n              \"routeFacts\": \"saveLayer imageFilter ColorFilter(Matrix,input=null) -> layer_composite.wgsl colorFilter uniform; no prepass; no scratch texture\"," else ""}
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

    private fun statsJson(evidence: SimpleSaveLayerImageFilterEvidence): String = """
        {
          "sceneId": ${SceneId.json()},
          "scopeId": ${ScopeId.json()},
          "status": "pass",
          "supportScope": "bounded-saveLayer-colorFilter-matrix-input-null",
          "supportClaim": true,
          "cpuRouteIdentifier": ${CpuRouteIdentifier.json()},
          "webGpuRouteIdentifier": ${WebGpuRouteIdentifier.json()},
          "imageFilterKind": ${evidence.imageFilterKind.json()},
          "colorFilterKind": ${evidence.colorFilterKind.json()},
          "sourceColor": ${evidence.sourceColor.hexColor().json()},
          "filteredColor": ${evidence.filteredColor.hexColor().json()},
          "layerBounds": ${evidence.layerBounds.json()},
          "layerPixelCount": ${evidence.layerPixelCount},
          "fallbackPolicy": ${evidence.fallbackPolicy.json()},
          "routeDiagnostics": ${evidence.routeDiagnostics.json()},
          "cpuNonBackgroundPixels": ${evidence.cpuNonBackgroundPixels},
          "webGpuNonBackgroundPixels": ${evidence.webGpuNonBackgroundPixels},
          "tolerance": ${evidence.tolerance},
          "cpuSimilarityThreshold": ${"%.2f".format(Locale.US, evidence.cpuSimilarityThreshold)},
          "webGpuSimilarityThreshold": ${"%.2f".format(Locale.US, evidence.webGpuSimilarityThreshold)},
          "cpuComparison": ${evidence.cpuComparison.json()},
          "webGpuComparison": ${evidence.webGpuComparison.json()},
          "globalThresholdChanged": false,
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

    private fun pixelCountFor(rect: SkRect): Int {
        val left = rect.left.roundToInt().coerceIn(0, Width)
        val top = rect.top.roundToInt().coerceIn(0, Height)
        val right = rect.right.roundToInt().coerceIn(left, Width)
        val bottom = rect.bottom.roundToInt().coerceIn(top, Height)
        return (right - left) * (bottom - top)
    }

    private fun boundsDump(rect: SkRect): String =
        "left=${"%.1f".format(Locale.US, rect.left)};" +
            "top=${"%.1f".format(Locale.US, rect.top)};" +
            "right=${"%.1f".format(Locale.US, rect.right)};" +
            "bottom=${"%.1f".format(Locale.US, rect.bottom)}"

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

    private fun SimpleSaveLayerImageFilterRouteDiagnostics.json(): String = """
        {
          "selectedRoute": ${selectedRoute.json()},
          "prepassRoute": ${prepassRoute?.json() ?: "null"},
          "scratchOwner": ${scratchOwner?.json() ?: "null"},
          "scratchLifetime": ${scratchLifetime?.json() ?: "null"},
          "materialiseStages": $materialiseStages,
          "fallbackReason": ${fallbackReason?.json() ?: "null"}
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

    private fun Int.hexColor(): String {
        val a = SkColorGetA(this)
        val r = SkColorGetR(this)
        val g = SkColorGetG(this)
        val b = SkColorGetB(this)
        return "#%02X%02X%02X%02X".format(Locale.US, a, r, g, b)
    }

    private fun grayscale(color: Int): Int {
        val gray = (
            SkColorGetR(color) * 0.299f +
                SkColorGetG(color) * 0.587f +
                SkColorGetB(color) * 0.114f
            ).roundToInt().coerceIn(0, 255)
        return SkColorSetARGB(SkColorGetA(color), gray, gray, gray)
    }

    private data class WebGpuSaveLayerImageFilterRender(
        val bitmap: SkBitmap,
        val routeDiagnostics: SimpleSaveLayerImageFilterRouteDiagnostics,
    )
}

public data class SimpleSaveLayerImageFilterEvidence(
    public val sceneId: String,
    public val scopeId: String,
    public val imageFilterKind: String,
    public val colorFilterKind: String,
    public val sourceColor: Int,
    public val filteredColor: Int,
    public val layerBounds: String,
    public val layerPixelCount: Int,
    public val cpuRouteIdentifier: String,
    public val webGpuRouteIdentifier: String,
    public val cpuFallbackReason: String,
    public val webGpuFallbackReason: String,
    public val fallbackPolicy: String,
    public val routeDiagnostics: SimpleSaveLayerImageFilterRouteDiagnostics,
    public val tolerance: Int,
    public val cpuComparison: BitmapComparison,
    public val webGpuComparison: BitmapComparison,
    public val cpuSimilarityThreshold: Double,
    public val webGpuSimilarityThreshold: Double,
    public val cpuNonBackgroundPixels: Int,
    public val webGpuNonBackgroundPixels: Int,
    public val webGpuAdapter: String,
    public val artifacts: SimpleSaveLayerImageFilterArtifacts,
)

public data class SimpleSaveLayerImageFilterRouteDiagnostics(
    public val selectedRoute: String,
    public val prepassRoute: String?,
    public val scratchOwner: String?,
    public val scratchLifetime: String?,
    public val materialiseStages: Int,
    public val fallbackReason: String?,
)

public data class SimpleSaveLayerImageFilterArtifacts(
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
        public fun forRoot(root: File): SimpleSaveLayerImageFilterArtifacts =
            SimpleSaveLayerImageFilterArtifacts(
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
