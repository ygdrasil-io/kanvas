package org.skia.gpu.webgpu

import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assumptions
import org.skia.core.SkCanvas
import org.skia.core.SrcRectConstraint
import org.skia.encode.SkPngEncoder
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.gpu.webgpu.testing.CrossBackendHarness
import org.skia.testing.BitmapComparison
import org.skia.testing.TestUtils

public object SimpleBitmapRectSceneEvidence {
    public const val SceneId: String = "paint.bitmap-rect.nearest.fixture.v1"
    public const val ScopeId: String = "bitmap-rect.nearest.fixture.simple.v1"
    public const val FixtureId: String = "kanvas-fixture-checker-8x6-rgba8888-v1"
    public const val CpuRouteIdentifier: String = "cpu.paint.bitmap-rect.nearest.fixture"
    public const val WebGpuRouteIdentifier: String = "webgpu.image.bitmap-rect.nearest.fixture"
    public const val ArtifactDirectory: String =
        "reports/wgsl-pipeline/scenes/artifacts/kan-014-bitmap-rect"
    public const val WriteEvidenceProperty: String = "kanvas.sceneEvidence.write"
    public const val ColorSpacePolicy: String = "srgb-unmanaged-fixture-oracle"

    private const val Width = 160
    private const val Height = 96
    private const val FixtureWidth = 8
    private const val FixtureHeight = 6
    private const val SrcLeft = 1f
    private const val SrcTop = 1f
    private const val SrcRight = 7f
    private const val SrcBottom = 5f
    private const val DstLeft = 28f
    private const val DstTop = 22f
    private const val DstRight = 124f
    private const val DstBottom = 70f
    private const val Tolerance = 0
    private const val Threshold = 99.0

    private val NonClaims = listOf(
        "no-broad-image-claim",
        "no-codec-decode-claim",
        "no-arbitrary-texture-claim",
        "no-mipmap-claim",
        "no-tile-mode-claim",
        "no-color-managed-decode-claim",
        "no-texture-atlas-claim",
        "no-perspective-transform-claim",
    )

    public fun capture(writeArtifacts: Boolean = false): SimpleBitmapRectEvidence {
        val fixture = fixtureImage()
        val fixtureSha256 = fixture.pixels.sha256()
        val reference = renderAnalyticReference(fixture)
        val cpu = renderCpu(fixture)
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            val adapter = ctx.adapterInfo ?: "unknown-adapter"
            val webGpu = renderWebGpu(ctx, fixture)
            val cpuComparison = TestUtils.compareBitmapsDetailed(
                cpu,
                reference,
                tolerance = Tolerance,
            )
            val webGpuComparison = TestUtils.compareBitmapsDetailed(
                webGpu,
                reference,
                tolerance = Tolerance,
            )
            val artifacts = SimpleBitmapRectArtifacts.forRoot(repoFile(ArtifactDirectory))
            val evidence = SimpleBitmapRectEvidence(
                sceneId = SceneId,
                scopeId = ScopeId,
                fixtureId = FixtureId,
                fixtureSha256 = fixtureSha256,
                sampler = "nearest",
                tileMode = "kClamp",
                srcRectConstraint = "kStrict",
                blendMode = "kSrcOver",
                cpuRouteIdentifier = CpuRouteIdentifier,
                webGpuRouteIdentifier = WebGpuRouteIdentifier,
                cpuFallbackReason = "none",
                webGpuFallbackReason = "none",
                tolerance = Tolerance,
                cpuComparison = cpuComparison,
                webGpuComparison = webGpuComparison,
                cpuSimilarityThreshold = Threshold,
                webGpuSimilarityThreshold = Threshold,
                cpuSampledPixels = sampledPixelCount(),
                webGpuSampledPixels = sampledPixelCount(),
                webGpuAdapter = adapter,
                artifacts = artifacts,
            )
            if (writeArtifacts || System.getProperty(WriteEvidenceProperty) == "true") {
                writeEvidence(evidence, reference, cpu, webGpu)
            }
            println(
                "[SimpleBitmapRectSceneEvidence] adapter=$adapter " +
                    "cpu=${"%.2f".format(Locale.US, cpuComparison.similarity)}%, " +
                    "webgpu=${"%.2f".format(Locale.US, webGpuComparison.similarity)}%, " +
                    "sampledPixels=${sampledPixelCount()}",
            )
            return evidence
        }
    }

    private fun fixtureImage(): SkImage =
        SkImage(FixtureWidth, FixtureHeight, fixturePixels())

    private fun fixturePixels(): IntArray {
        val colors = intArrayOf(
            color(255, 36, 88, 196),
            color(255, 238, 92, 52),
            color(255, 28, 154, 92),
            color(255, 248, 205, 64),
            color(255, 105, 48, 162),
            color(255, 40, 190, 202),
        )
        return IntArray(FixtureWidth * FixtureHeight) { index ->
            val x = index % FixtureWidth
            val y = index / FixtureWidth
            colors[(x * 3 + y * 5 + (x / 2)) % colors.size]
        }
    }

    private fun renderAnalyticReference(image: SkImage): SkBitmap {
        val bitmap = SkBitmap(Width, Height, colorType = SkColorType.kRGBA_8888)
        bitmap.eraseColor(SK_ColorWHITE)
        val ix0 = pixelEdge(DstLeft).coerceIn(0, Width)
        val iy0 = pixelEdge(DstTop).coerceIn(0, Height)
        val ix1 = pixelEdge(DstRight).coerceIn(ix0, Width)
        val iy1 = pixelEdge(DstBottom).coerceIn(iy0, Height)
        val scaleX = (SrcRight - SrcLeft) / (DstRight - DstLeft)
        val scaleY = (SrcBottom - SrcTop) / (DstBottom - DstTop)
        val maxX = image.width - 1
        val maxY = image.height - 1
        val strictMinX = strictSampleMin(SrcLeft, maxX)
        val strictMaxX = strictSampleMax(SrcRight, strictMinX, maxX)
        val strictMinY = strictSampleMin(SrcTop, maxY)
        val strictMaxY = strictSampleMax(SrcBottom, strictMinY, maxY)
        for (py in iy0 until iy1) {
            val srcYc = SrcTop + (py + 0.5f - DstTop) * scaleY
            val iy = floor(srcYc).toInt().coerceIn(strictMinY, strictMaxY)
            for (px in ix0 until ix1) {
                val srcXc = SrcLeft + (px + 0.5f - DstLeft) * scaleX
                val ix = floor(srcXc).toInt().coerceIn(strictMinX, strictMaxX)
                bitmap.pixels8888[py * Width + px] = image.peekPixel(ix, iy)
            }
        }
        return bitmap
    }

    private fun renderCpu(image: SkImage): SkBitmap {
        val bitmap = SkBitmap(Width, Height, colorType = SkColorType.kRGBA_8888)
        bitmap.eraseColor(SK_ColorWHITE)
        SkCanvas(bitmap).drawImageRect(
            image = image,
            src = sourceRect(),
            dst = destinationRect(),
            sampling = SkSamplingOptions.nearest(),
            paint = SkPaint().apply { isAntiAlias = false },
            constraint = SrcRectConstraint.kStrict,
        )
        return bitmap
    }

    private fun renderWebGpu(context: WebGpuContext, image: SkImage): SkBitmap {
        SkWebGpuDevice(
            context,
            Width,
            Height,
            applyColorspaceTransform = false,
        ).use { device ->
            device.setBackground(SK_ColorWHITE)
            SkCanvas(device).drawImageRect(
                image = image,
                src = sourceRect(),
                dst = destinationRect(),
                sampling = SkSamplingOptions.nearest(),
                paint = SkPaint().apply { isAntiAlias = false },
                constraint = SrcRectConstraint.kStrict,
            )
            return rgbaBytesToBitmap(device.flush(), Width, Height)
        }
    }

    private fun sourceRect(): SkRect =
        SkRect.MakeLTRB(SrcLeft, SrcTop, SrcRight, SrcBottom)

    private fun destinationRect(): SkRect =
        SkRect.MakeLTRB(DstLeft, DstTop, DstRight, DstBottom)

    private fun sampledPixelCount(): Int =
        (pixelEdge(DstRight) - pixelEdge(DstLeft)) * (pixelEdge(DstBottom) - pixelEdge(DstTop))

    private fun writeEvidence(
        evidence: SimpleBitmapRectEvidence,
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

    private fun writePng(file: File, bitmap: SkBitmap) {
        file.parentFile.mkdirs()
        file.writeBytes(requireNotNull(SkPngEncoder.Encode(bitmap)) { "PNG encoding failed for ${file.path}" })
    }

    private fun routeJson(evidence: SimpleBitmapRectEvidence, backend: String): String {
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
              "drawKind": "SkCanvas.drawImageRect",
              "status": "pass",
              "supportScope": "simple-fixture-backed-bitmap-rect-nearest",
              "supportClaim": true,
              "selectedRoute": ${selectedRoute.json()},
              "fixtureBacked": true,
              "fixtureId": ${FixtureId.json()},
              "fixtureSha256": ${evidence.fixtureSha256.json()},
              "sampler": "nearest",
              "filterMode": ${SkFilterMode.kNearest.name.json()},
              "tileMode": ${evidence.tileMode.json()},
              "srcRectConstraint": ${evidence.srcRectConstraint.json()},
              "blendMode": ${evidence.blendMode.json()},
              "pipelineKey": "shaderFamily=bitmapShader sampling=nearest tile=kClamp state=[blendMode=kSrcOver]",
              "sourceRect": ${rectJson(SrcLeft, SrcTop, SrcRight, SrcBottom)},
              "destinationRect": ${rectJson(DstLeft, DstTop, DstRight, DstBottom)},
              "referenceKind": "analytic-strict-nearest-fixture-oracle",
              "fallbackReason": ${(if (isGpu) evidence.webGpuFallbackReason else evidence.cpuFallbackReason).json()},
              "colorSpacePolicy": ${ColorSpacePolicy.json()},
              "sampledPixels": ${if (isGpu) evidence.webGpuSampledPixels else evidence.cpuSampledPixels},
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

    private fun statsJson(evidence: SimpleBitmapRectEvidence): String = """
        {
          "sceneId": ${SceneId.json()},
          "scopeId": ${ScopeId.json()},
          "status": "pass",
          "supportScope": "simple-fixture-backed-bitmap-rect-nearest",
          "supportClaim": true,
          "fixtureBacked": true,
          "fixtureId": ${FixtureId.json()},
          "fixtureSha256": ${evidence.fixtureSha256.json()},
          "cpuRouteIdentifier": ${CpuRouteIdentifier.json()},
          "webGpuRouteIdentifier": ${WebGpuRouteIdentifier.json()},
          "sampler": "nearest",
          "filterMode": ${SkFilterMode.kNearest.name.json()},
          "tileMode": ${evidence.tileMode.json()},
          "srcRectConstraint": ${evidence.srcRectConstraint.json()},
          "blendMode": ${evidence.blendMode.json()},
          "colorSpacePolicy": ${ColorSpacePolicy.json()},
          "cpuSampledPixels": ${evidence.cpuSampledPixels},
          "webGpuSampledPixels": ${evidence.webGpuSampledPixels},
          "tolerance": ${evidence.tolerance},
          "cpuSimilarityThreshold": ${"%.2f".format(Locale.US, evidence.cpuSimilarityThreshold)},
          "webGpuSimilarityThreshold": ${"%.2f".format(Locale.US, evidence.webGpuSimilarityThreshold)},
          "cpuComparison": ${evidence.cpuComparison.json()},
          "webGpuComparison": ${evidence.webGpuComparison.json()},
          "globalThresholdChanged": false,
          "fallbackPolicy": "none-for-supported-simple-fixture-backed-nearest-bitmap-rect",
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

    private fun rectJson(left: Float, top: Float, right: Float, bottom: Float): String = """
        {
          "left": ${"%.1f".format(Locale.US, left)},
          "top": ${"%.1f".format(Locale.US, top)},
          "right": ${"%.1f".format(Locale.US, right)},
          "bottom": ${"%.1f".format(Locale.US, bottom)}
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

    private fun pixelEdge(v: Float): Int = floor(v + 0.5f).toInt()

    private fun strictSampleMin(edge: Float, max: Int): Int =
        ceil(edge - 0.5f).toInt().coerceIn(0, max)

    private fun strictSampleMax(edge: Float, min: Int, max: Int): Int =
        floor(edge - 0.5f).toInt().coerceIn(min, max)

    private fun color(a: Int, r: Int, g: Int, b: Int): Int =
        SkColorSetARGB(a, r, g, b)

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

    private fun IntArray.sha256(): String {
        val bytes = ByteArray(size * 4)
        forEachIndexed { index, value ->
            val base = index * 4
            bytes[base] = (value ushr 24).toByte()
            bytes[base + 1] = (value ushr 16).toByte()
            bytes[base + 2] = (value ushr 8).toByte()
            bytes[base + 3] = value.toByte()
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
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

public data class SimpleBitmapRectEvidence(
    public val sceneId: String,
    public val scopeId: String,
    public val fixtureId: String,
    public val fixtureSha256: String,
    public val sampler: String,
    public val tileMode: String,
    public val srcRectConstraint: String,
    public val blendMode: String,
    public val cpuRouteIdentifier: String,
    public val webGpuRouteIdentifier: String,
    public val cpuFallbackReason: String,
    public val webGpuFallbackReason: String,
    public val tolerance: Int,
    public val cpuComparison: BitmapComparison,
    public val webGpuComparison: BitmapComparison,
    public val cpuSimilarityThreshold: Double,
    public val webGpuSimilarityThreshold: Double,
    public val cpuSampledPixels: Int,
    public val webGpuSampledPixels: Int,
    public val webGpuAdapter: String,
    public val artifacts: SimpleBitmapRectArtifacts,
)

public data class SimpleBitmapRectArtifacts(
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
        public fun forRoot(root: File): SimpleBitmapRectArtifacts =
            SimpleBitmapRectArtifacts(
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
