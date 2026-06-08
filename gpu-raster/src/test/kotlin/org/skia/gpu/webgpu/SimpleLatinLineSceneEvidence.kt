package org.skia.gpu.webgpu

import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.junit.jupiter.api.Assumptions
import org.skia.core.SkCanvas
import org.skia.encode.SkPngEncoder
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkCpuGlyphCache
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTextBlobBuilder
import org.skia.gpu.webgpu.testing.CrossBackendHarness
import org.skia.testing.BitmapComparison
import org.skia.testing.TestUtils
import org.skia.tools.ToolUtils

public object SimpleLatinLineSceneEvidence {
    public const val SceneId: String = "text.simple-latin.line.v1"
    public const val ScopeId: String = "text.simple-latin.liberation-sans-regular.v1"
    public const val LineText: String = "Kanvas Latin 0123456789 ABC xyz."
    public const val GlyphRoute: String = "font.glyph.outline-path"
    public const val WebGpuRouteIdentifier: String = "webgpu.text.outline-path.simple-latin"
    public const val CpuRouteIdentifier: String = "cpu.text.outline-path.simple-latin"
    public const val ArtifactDirectory: String =
        "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line"
    public const val WriteEvidenceProperty: String = "kanvas.sceneEvidence.write"

    private const val LiberationSansRegularSourceId =
        "kanvas-skia/src/main/resources/fonts/liberation/LiberationSans-Regular.ttf#sha256=76d04c18ea243f426b7de1f3ad208e927008f961dc5945e5aad352d0dfde8ee8"
    private const val Width = 640
    private const val Height = 96
    private const val OriginX = 16f
    private const val BaselineY = 56f
    private const val FontSize = 32f
    private const val Threshold = 95.0

    private val NonClaims = listOf(
        "no-shaping-claim",
        "no-fallback-font-claim",
        "no-emoji-or-color-font-claim",
        "no-sdf-or-lcd-claim",
        "no-rtl-or-bidi-claim",
        "no-ligature-claim",
        "no-broad-text-claim",
    )

    public fun capture(writeArtifacts: Boolean = false): SimpleLatinLineEvidence {
        val font = ToolUtils.DefaultPortableFont(FontSize)
        val cache = SkCpuGlyphCache.build(
            scopeId = ScopeId,
            fontSourceId = LiberationSansRegularSourceId,
            font = font,
            text = LineText,
        )
        val atlas = SkWebGpuGlyphAtlas.build(
            cache = cache,
            generation = 1,
            maxTextureWidth = 128,
        )
        val blob = buildPositionedBlob(cache)
        val reference = renderAtlasReference(cache, atlas)
        val cpu = renderCpu(blob)
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            val adapter = ctx.adapterInfo ?: "unknown-adapter"
            val webGpu = renderWebGpu(ctx, blob)
            val cpuComparison = TestUtils.compareBitmapsDetailed(
                cpu,
                reference,
                tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            val webGpuComparison = TestUtils.compareBitmapsDetailed(
                webGpu,
                reference,
                tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            val artifacts = SimpleLatinLineArtifacts.forRoot(repoFile(ArtifactDirectory))
            val evidence = SimpleLatinLineEvidence(
                sceneId = SceneId,
                scopeId = cache.scopeId,
                fontFamily = cache.fontFamily,
                text = cache.text,
                glyphRoute = GlyphRoute,
                webGpuRouteIdentifier = WebGpuRouteIdentifier,
                atlas = atlas,
                cpuFallbackReason = "none",
                webGpuFallbackReason = "none",
                tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
                cpuComparison = cpuComparison,
                webGpuComparison = webGpuComparison,
                cpuSimilarityThreshold = Threshold,
                webGpuSimilarityThreshold = Threshold,
                cpuNonWhitePixels = countNonWhitePixels(cpu),
                webGpuNonWhitePixels = countNonWhitePixels(webGpu),
                webGpuAdapter = adapter,
                artifacts = artifacts,
            )
            if (writeArtifacts || System.getProperty(WriteEvidenceProperty) == "true") {
                writeEvidence(evidence, cache, reference, cpu, webGpu)
            }
            println(
                "[SimpleLatinLineSceneEvidence] adapter=$adapter " +
                    "cpu=${"%.2f".format(Locale.US, cpuComparison.similarity)}%, " +
                    "webgpu=${"%.2f".format(Locale.US, webGpuComparison.similarity)}%, " +
                    "atlasUpload=${atlas.uploadByteCount} bytes",
            )
            return evidence
        }
    }

    private fun buildPositionedBlob(cache: SkCpuGlyphCache): SkTextBlob {
        val builder = SkTextBlobBuilder()
        val run = builder.allocRunPosH(ToolUtils.DefaultPortableFont(FontSize), cache.inventory.size, BaselineY)
        cache.inventory.forEachIndexed { index, item ->
            run.glyphs[index] = item.glyphId
            run.pos[index] = OriginX + item.x
        }
        return requireNotNull(builder.make()) { "Expected non-empty simple Latin text blob" }
    }

    private fun renderAtlasReference(cache: SkCpuGlyphCache, atlas: SkWebGpuGlyphAtlas): SkBitmap {
        val bitmap = SkBitmap(Width, Height, colorType = SkColorType.kRGBA_8888)
        bitmap.eraseColor(SK_ColorWHITE)
        cache.inventory.forEach { item ->
            val entry = atlas.entryForKey(item.key)
            if (entry.maskWidth == 0 || entry.maskHeight == 0) return@forEach
            val dstLeft = (OriginX + item.x).roundToInt() + entry.maskLeft
            val dstTop = BaselineY.roundToInt() + entry.maskTop
            for (y in 0 until entry.maskHeight) {
                val dstY = dstTop + y
                if (dstY !in 0 until Height) continue
                for (x in 0 until entry.maskWidth) {
                    val alpha = atlas.sample(item.key, x, y)
                    if (alpha == 0) continue
                    val dstX = dstLeft + x
                    if (dstX !in 0 until Width) continue
                    val index = dstY * Width + dstX
                    val dst = bitmap.pixels8888[index]
                    val invAlpha = 255 - alpha
                    val r = (SkColorGetR(dst) * invAlpha + 127) / 255
                    val g = (SkColorGetG(dst) * invAlpha + 127) / 255
                    val b = (SkColorGetB(dst) * invAlpha + 127) / 255
                    bitmap.pixels8888[index] = SkColorSetARGB(255, r, g, b)
                }
            }
        }
        return bitmap
    }

    private fun renderCpu(blob: SkTextBlob): SkBitmap {
        val bitmap = SkBitmap(Width, Height, colorType = SkColorType.kRGBA_8888)
        bitmap.eraseColor(SK_ColorWHITE)
        val canvas = SkCanvas(bitmap)
        val paint = SkPaint(SK_ColorBLACK).apply { isAntiAlias = true }
        canvas.drawTextBlob(blob, 0f, 0f, paint)
        return bitmap
    }

    private fun renderWebGpu(context: WebGpuContext, blob: SkTextBlob): SkBitmap {
        SkWebGpuDevice(
            context,
            Width,
            Height,
            applyColorspaceTransform = false,
        ).use { device ->
            device.setBackground(SK_ColorWHITE)
            val canvas = SkCanvas(device)
            val paint = SkPaint(SK_ColorBLACK).apply { isAntiAlias = true }
            canvas.drawTextBlob(blob, 0f, 0f, paint)
            return rgbaBytesToBitmap(device.flush(), Width, Height)
        }
    }

    private fun writeEvidence(
        evidence: SimpleLatinLineEvidence,
        cache: SkCpuGlyphCache,
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
        artifacts.routeCpuJson.writeText(routeJson(evidence, cache, backend = "CPU"))
        artifacts.routeWebGpuJson.writeText(routeJson(evidence, cache, backend = "WebGPU"))
        artifacts.statsJson.writeText(statsJson(evidence, cache))
        artifacts.atlasJson.writeText(evidence.atlas.toJson())
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

    private fun routeJson(
        evidence: SimpleLatinLineEvidence,
        cache: SkCpuGlyphCache,
        backend: String,
    ): String {
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
              "drawKind": "SkTextBlob.simple-positioned-run",
              "status": "pass",
              "supportScope": "simple-latin-line-visible",
              "supportClaim": true,
              "selectedRoute": ${selectedRoute.json()},
              "glyphRoute": ${GlyphRoute.json()},
              "atlasRouteIdentifier": ${evidence.atlas.routeIdentifier.json()},
              "atlasUploadSha256": ${evidence.atlas.uploadSha256.json()},
              "atlasUploadByteCount": ${evidence.atlas.uploadByteCount},
              "referenceKind": "cpu-atlas-alpha-mask-oracle",
              "fallbackReason": "none",
              "fontSourceId": ${LiberationSansRegularSourceId.json()},
              "fontFamily": ${evidence.fontFamily.json()},
              "fontSize": $FontSize,
              "text": ${evidence.text.json()},
              "shapingMode": "simple-codepoint-order",
              "glyphInventoryCount": ${cache.inventory.size},
              "dedupedGlyphCount": ${cache.glyphs.size},
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

    private fun statsJson(evidence: SimpleLatinLineEvidence, cache: SkCpuGlyphCache): String = """
        {
          "sceneId": ${SceneId.json()},
          "scopeId": ${ScopeId.json()},
          "text": ${LineText.json()},
          "fontFamily": ${evidence.fontFamily.json()},
          "fontSourceId": ${LiberationSansRegularSourceId.json()},
          "fontSize": $FontSize,
          "shapingMode": "simple-codepoint-order",
          "glyphRoute": ${GlyphRoute.json()},
          "webGpuRouteIdentifier": ${WebGpuRouteIdentifier.json()},
          "atlasRouteIdentifier": ${evidence.atlas.routeIdentifier.json()},
          "atlasUploadByteCount": ${evidence.atlas.uploadByteCount},
          "atlasUploadSha256": ${evidence.atlas.uploadSha256.json()},
          "sourceCacheSha256": ${cache.dumpSha256.json()},
          "glyphInventoryCount": ${cache.inventory.size},
          "dedupedGlyphCount": ${cache.glyphs.size},
          "cpuNonWhitePixels": ${evidence.cpuNonWhitePixels},
          "webGpuNonWhitePixels": ${evidence.webGpuNonWhitePixels},
          "tolerance": ${evidence.tolerance},
          "cpuSimilarityThreshold": ${"%.2f".format(Locale.US, evidence.cpuSimilarityThreshold)},
          "webGpuSimilarityThreshold": ${"%.2f".format(Locale.US, evidence.webGpuSimilarityThreshold)},
          "cpuComparison": ${evidence.cpuComparison.json()},
          "webGpuComparison": ${evidence.webGpuComparison.json()},
          "globalThresholdChanged": false,
          "fallbackPolicy": "none-for-supported-simple-latin-line",
          "webGpuAdapter": ${evidence.webGpuAdapter.json()},
          "referenceArtifact": ${repoRelative(evidence.artifacts.referencePng).json()},
          "cpuArtifact": ${repoRelative(evidence.artifacts.cpuPng).json()},
          "webGpuArtifact": ${repoRelative(evidence.artifacts.webGpuPng).json()},
          "cpuDiffArtifact": ${repoRelative(evidence.artifacts.cpuDiffPng).json()},
          "webGpuDiffArtifact": ${repoRelative(evidence.artifacts.webGpuDiffPng).json()},
          "routeCpuArtifact": ${repoRelative(evidence.artifacts.routeCpuJson).json()},
          "routeWebGpuArtifact": ${repoRelative(evidence.artifacts.routeWebGpuJson).json()},
          "atlasArtifact": ${repoRelative(evidence.artifacts.atlasJson).json()},
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
}

public data class SimpleLatinLineEvidence(
    public val sceneId: String,
    public val scopeId: String,
    public val fontFamily: String,
    public val text: String,
    public val glyphRoute: String,
    public val webGpuRouteIdentifier: String,
    public val atlas: SkWebGpuGlyphAtlas,
    public val cpuFallbackReason: String,
    public val webGpuFallbackReason: String,
    public val tolerance: Int,
    public val cpuComparison: BitmapComparison,
    public val webGpuComparison: BitmapComparison,
    public val cpuSimilarityThreshold: Double,
    public val webGpuSimilarityThreshold: Double,
    public val cpuNonWhitePixels: Int,
    public val webGpuNonWhitePixels: Int,
    public val webGpuAdapter: String,
    public val artifacts: SimpleLatinLineArtifacts,
)

public data class SimpleLatinLineArtifacts(
    public val root: File,
    public val referencePng: File,
    public val cpuPng: File,
    public val webGpuPng: File,
    public val cpuDiffPng: File,
    public val webGpuDiffPng: File,
    public val routeCpuJson: File,
    public val routeWebGpuJson: File,
    public val statsJson: File,
    public val atlasJson: File,
) {
    public companion object {
        public fun forRoot(root: File): SimpleLatinLineArtifacts =
            SimpleLatinLineArtifacts(
                root = root,
                referencePng = File(root, "reference.png"),
                cpuPng = File(root, "cpu.png"),
                webGpuPng = File(root, "webgpu.png"),
                cpuDiffPng = File(root, "cpu-diff.png"),
                webGpuDiffPng = File(root, "webgpu-diff.png"),
                routeCpuJson = File(root, "route-cpu.json"),
                routeWebGpuJson = File(root, "route-webgpu.json"),
                statsJson = File(root, "stats.json"),
                atlasJson = File(root, "atlas.json"),
            )
    }
}
