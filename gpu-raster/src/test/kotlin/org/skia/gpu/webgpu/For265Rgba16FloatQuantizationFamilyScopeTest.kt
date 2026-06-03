package org.skia.gpu.webgpu

import io.ygdrasil.webgpu.GPUTextureFormat
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.pow
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorType
import org.skia.foundation.SkPaint
import org.skia.gpu.webgpu.tools.GeneratedSolidRectWgsl
import org.skia.testing.TestUtils
import org.skia.tests.DrawBitmapRectSkbug4734GM
import org.skia.tests.GM
import org.skia.tests.SimpleOffsetImageFilterGM

class For265Rgba16FloatQuantizationFamilyScopeTest {
    @Test
    fun `FOR-265 RGBA16Float quantization family scope stays diagnostic`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val simpleOffsetGm = SimpleOffsetImageFilterGM()
            val simpleOffsetReference = TestUtils.loadReferenceBitmap(simpleOffsetGm.name())
                ?: error("original-888/${simpleOffsetGm.name()}.png missing")
            val bitmapGm = DrawBitmapRectSkbug4734GM()
            val neutralAaGm = NeutralAaCoverageGM()

            val cases = listOf(
                buildResidualCase(
                    context = ctx,
                    id = "residual-route.simple-offsetimagefilter",
                    sceneId = "simple-offsetimagefilter",
                    route = "webgpu.image-filter.offset-crop-prepass-and-src-over",
                    reference = simpleOffsetReference,
                    gm = simpleOffsetGm,
                    shaderSideXy = intArrayOf(40, 40),
                    expectedStoreRgbaFloat = floatArrayOf(0.7597656f, 0.35961914f, 0.5996094f, 1.0f),
                    expectedPresentedRgba = intArrayOf(157, 90, 138, 255),
                    simulatedQuantizedPresentRgba = intArrayOf(158, 90, 139, 255),
                ),
                buildResidualCase(
                    context = ctx,
                    id = "residual-route.bitmap-rect-nearest",
                    sceneId = "bitmap-rect-nearest",
                    route = "webgpu.image-rect.strict-nearest",
                    reference = readEvidencePng("reports/wgsl-pipeline/scenes/generated/artifacts/bitmap-rect-nearest/skia.png"),
                    gm = bitmapGm,
                    shaderSideXy = intArrayOf(8, 24),
                    expectedStoreRgbaFloat = floatArrayOf(0.47045898f, 0.7998047f, 0.8388672f, 1.0f),
                    expectedPresentedRgba = intArrayOf(148, 193, 207, 255),
                    simulatedQuantizedPresentRgba = intArrayOf(149, 193, 207, 255),
                ),
                buildExactControlCase(ctx),
                buildTargetBlendSensitiveCase(ctx, neutralAaGm),
                buildBlendCoverageDashboardCase(ctx),
            )
            val probe = For265AuditProbe(cases = cases)

            assertEquals("KEEP_DIAGNOSTIC", probe.supportDecision)
            assertFalse(probe.correctionApplied)
            assertEquals(5, probe.caseCount)
            assertTrue(probe.residualCasesCompared)
            assertTrue(probe.bitmapImageRectObserved)
            assertTrue(probe.exactControlPreserved)
            assertTrue(probe.targetColorSpaceBlendSignalPreserved)
            assertTrue(probe.dashboardBlendCoverageControlObserved)
            assertFalse(probe.safeCorrectionProven)
            assertEquals(
                "image-filter.crop-input-nonnull-prepass-required",
                probe.preservedUnsupportedReason,
                "FOR-265: Crop(input = nonNull) fallback reason must stay preserved",
            )

            if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
                writeFor265AuditJson(probe)
                writeFor265AuditReport(probe)
            }
        }
    }

    private fun buildResidualCase(
        context: WebGpuContext,
        id: String,
        sceneId: String,
        route: String,
        reference: Any,
        gm: GM,
        shaderSideXy: IntArray,
        expectedStoreRgbaFloat: FloatArray,
        expectedPresentedRgba: IntArray,
        simulatedQuantizedPresentRgba: IntArray,
    ): For265Case {
        val actual = renderBitmap(
            context = context,
            gm = gm,
            intermediateFormat = GPUTextureFormat.RGBA16Float,
            targetColorSpaceBlend = false,
        )
        val stats = compareReference(reference, actual)
        val currentPresent = reconstructPresentFromShaderSideRgba(expectedStoreRgbaFloat)
        require(currentPresent.contentEquals(expectedPresentedRgba)) {
            "FOR-265 expected $sceneId RGBA16Float reconstruction ${jsonArray(expectedPresentedRgba)}, got ${jsonArray(currentPresent)}"
        }
        val rgba8Store = quantizeToRgba8StoreFloat(expectedStoreRgbaFloat)
        val rgba8StoreBytes = quantizeRgbaFloatsToBytes(rgba8Store)
        val rgba8Present = reconstructPresentFromShaderSideRgba(rgba8Store)
        require(rgba8Present.contentEquals(simulatedQuantizedPresentRgba)) {
            "FOR-265 expected $sceneId simulated quantized present ${jsonArray(simulatedQuantizedPresentRgba)}, got ${jsonArray(rgba8Present)}"
        }
        return For265Case(
            id = id,
            sceneId = sceneId,
            kind = "for261-residual-rgba16float-present-boundary",
            routeDiagnostics = route,
            current = For265PolicyStats.fromImageStats(
                stats = stats,
                policy = "current-rgba16float-intermediate",
                route = route,
                classification = "rgba16float-intermediate-store-to-present-byte-quantization-policy",
            ),
            targetBlendControl = null,
            boundary = For265BoundaryComparison(
                shaderSideXy = shaderSideXy,
                intermediateFormat = "RGBA16Float",
                intermediateStoreExpectedRgbaFloat = expectedStoreRgbaFloat,
                intermediateStoreExpectedRgba8 = quantizeRgbaFloatsToBytes(expectedStoreRgbaFloat),
                presentedObservedRgba = expectedPresentedRgba,
                presentedReconstructedFromIntermediateRgba = currentPresent,
                simulatedQuantizationPolicy = "round-half-up RGBA8Unorm store/load before existing present pass",
                simulatedQuantizedStoreRgbaFloat = rgba8Store,
                simulatedQuantizedStoreRgba8 = rgba8StoreBytes,
                simulatedQuantizedPresentRgba = rgba8Present,
                observedPolicy = "RGBA16Float textureLoad in present pass followed by byte output quantization",
                boundaryClassification = "rgba16float-present-byte-quantization-residual",
            ),
            correctionStatus = "CORRECTION_SIGNAL_DIAGNOSTIC_ONLY",
            admissibility = "KEEP_DIAGNOSTIC",
            verdict = "current RGBA16Float intermediate reconstruction matches the presented byte residual, while the bounded RGBA8 store/load simulation reaches the reference-side byte; no normal-path precision policy changed",
        )
    }

    private fun buildExactControlCase(context: WebGpuContext): For265Case {
        val gm = BlackWhiteRectGM()
        val reference = TestUtils.runGmTest(gm)
        val actual = renderBitmap(
            context = context,
            gm = gm,
            intermediateFormat = GPUTextureFormat.RGBA16Float,
            targetColorSpaceBlend = false,
        )
        val stats = compareImages(reference, actual)
        require(stats.exactSimilarity == 100.0 && stats.maxDelta == 0) {
            "FOR-265 exact control must remain byte-exact"
        }
        val observed = stats.representative.gpu
        val store = FloatArray(4) { channel -> observed[channel] / 255f }
        return For265Case(
            id = "exact-control.black-white-rect",
            sceneId = "black-white-rect",
            kind = "exact-control-already-100-percent",
            routeDiagnostics = "webgpu.coverage.analytic-rect",
            current = For265PolicyStats.fromImageStats(
                stats = stats,
                policy = "current-rgba16float-intermediate",
                route = "webgpu.coverage.analytic-rect",
                classification = "none-needed",
            ),
            targetBlendControl = null,
            boundary = For265BoundaryComparison(
                shaderSideXy = intArrayOf(0, 0),
                intermediateFormat = "RGBA16Float",
                intermediateStoreExpectedRgbaFloat = store,
                intermediateStoreExpectedRgba8 = observed,
                presentedObservedRgba = observed,
                presentedReconstructedFromIntermediateRgba = observed,
                simulatedQuantizationPolicy = "identity byte-exact control; no residual to correct",
                simulatedQuantizedStoreRgbaFloat = store,
                simulatedQuantizedStoreRgba8 = observed,
                simulatedQuantizedPresentRgba = observed,
                observedPolicy = "RGBA16Float normal path presents byte-exact output",
                boundaryClassification = "already-exact-no-quantization-correction-needed",
            ),
            correctionStatus = "UNCHANGED",
            admissibility = "REFUSED_NOT_NEEDED",
            verdict = "the control is already 100% exact under the normal RGBA16Float policy, so FOR-265 must not infer a precision correction from it",
        )
    }

    private fun buildTargetBlendSensitiveCase(context: WebGpuContext, gm: GM): For265Case {
        val reference = TestUtils.runGmTest(gm)
        val current = renderBitmap(
            context = context,
            gm = gm,
            intermediateFormat = GPUTextureFormat.RGBA16Float,
            targetColorSpaceBlend = false,
        )
        val targetBlend = renderBitmap(
            context = context,
            gm = gm,
            intermediateFormat = GPUTextureFormat.RGBA16Float,
            targetColorSpaceBlend = true,
        )
        val currentStats = compareImages(reference, current)
        val targetBlendStats = compareImages(reference, targetBlend)
        val store = floatArrayOf(0.5f, 0.5f, 0.5f, 1.0f)
        val reconstructed = reconstructPresentFromShaderSideRgba(store)
        require(currentStats.representative.gpu.contentEquals(reconstructed)) {
            "FOR-265 target-blend fixture expected current reconstruction ${jsonArray(reconstructed)}, got ${jsonArray(currentStats.representative.gpu)}"
        }
        require(targetBlendStats.exactSimilarity == 100.0) {
            "FOR-265 targetColorSpaceBlend control must stay exact"
        }
        return For265Case(
            id = "target-blend-sensitive.m60-target-colorspace-neutral-aa",
            sceneId = "m60-target-colorspace-neutral-aa",
            kind = "targetColorSpaceBlend-sensitive-control",
            routeDiagnostics = "webgpu.present-pass.srgb-to-rec2020-after-blend",
            current = For265PolicyStats.fromImageStats(
                stats = currentStats,
                policy = "targetBlend-false-rgba16float",
                route = "webgpu.present-pass.srgb-to-rec2020-after-blend",
                classification = "targetColorSpaceBlend-not-present-quantization",
            ),
            targetBlendControl = For265PolicyStats.fromImageStats(
                stats = targetBlendStats,
                policy = "targetBlend-true-rgba16float",
                route = "webgpu.target-colorspace-blend.solid-coverage",
                classification = "targetColorSpaceBlend-not-present-quantization",
            ),
            boundary = For265BoundaryComparison(
                shaderSideXy = intArrayOf(0, 0),
                intermediateFormat = "RGBA16Float",
                intermediateStoreExpectedRgbaFloat = store,
                intermediateStoreExpectedRgba8 = quantizeRgbaFloatsToBytes(store),
                presentedObservedRgba = reconstructed,
                presentedReconstructedFromIntermediateRgba = reconstructed,
                simulatedQuantizationPolicy = "RGBA16Float store value quantizes to the same post-present byte; targetColorSpaceBlend is the correcting dimension",
                simulatedQuantizedStoreRgbaFloat = quantizeToRgba8StoreFloat(store),
                simulatedQuantizedStoreRgba8 = quantizeRgbaFloatsToBytes(store),
                simulatedQuantizedPresentRgba = reconstructPresentFromShaderSideRgba(quantizeToRgba8StoreFloat(store)),
                observedPolicy = "normal targetColorSpaceBlend=false present pass converts sRGB-coded intermediate after blending",
                boundaryClassification = "targetColorSpaceBlend-required-not-quantization",
            ),
            correctionStatus = "UNCHANGED_BY_QUANTIZATION_TARGET_BLEND_CORRECTS",
            admissibility = "KEEP_DIAGNOSTIC",
            verdict = "RGBA16Float present quantization reproduces the [115,115,115,255] residual, while targetColorSpaceBlend=true reaches exact [128,128,128,255]; quantization must not mask the color-space correction",
        )
    }

    private fun buildBlendCoverageDashboardCase(context: WebGpuContext): For265Case {
        val routePath = "reports/wgsl-pipeline/scenes/artifacts/src-over-stack/route-gpu.json"
        val routeDiagnostics = repoFile(routePath).readText()
        require(routeDiagnostics.contains("webgpu.blend.src-over.fixed-function")) {
            "FOR-265 expected src-over-stack dashboard route to stay fixed-function SrcOver"
        }
        val reference = readEvidencePng("reports/wgsl-pipeline/scenes/artifacts/src-over-stack/skia.png")
        val actual = renderSrcOverStackBitmap(context)
        val stats = compareImages(reference, actual).copy(
            representative = pixelDelta(reference, actual, x = 24, y = 24),
        )
        require(stats.exactSimilarity == 100.0 && stats.maxDelta == 0) {
            "FOR-265 dashboard blend/coverage control must remain byte-exact"
        }
        val observed = stats.representative.gpu
        val store = FloatArray(4) { channel -> observed[channel] / 255f }
        return For265Case(
            id = "dashboard-control.src-over-stack",
            sceneId = "src-over-stack",
            kind = "dashboard-blend-coverage-exact-control",
            routeDiagnostics = "webgpu.blend.src-over.fixed-function",
            current = For265PolicyStats.fromImageStats(
                stats = stats,
                policy = "current-rgba16float-intermediate",
                route = "webgpu.blend.src-over.fixed-function",
                classification = "already-exact-blend-coverage-dashboard-control",
            ),
            targetBlendControl = null,
            boundary = For265BoundaryComparison(
                shaderSideXy = intArrayOf(24, 24),
                intermediateFormat = "RGBA16Float",
                intermediateStoreExpectedRgbaFloat = store,
                intermediateStoreExpectedRgba8 = observed,
                presentedObservedRgba = observed,
                presentedReconstructedFromIntermediateRgba = observed,
                simulatedQuantizationPolicy = "identity byte-exact dashboard blend/coverage control; no residual to correct",
                simulatedQuantizedStoreRgbaFloat = store,
                simulatedQuantizedStoreRgba8 = observed,
                simulatedQuantizedPresentRgba = observed,
                observedPolicy = "RGBA16Float normal fixed-function SrcOver route presents byte-exact dashboard output",
                boundaryClassification = "already-exact-blend-coverage-dashboard-control",
            ),
            correctionStatus = "UNCHANGED",
            admissibility = "REFUSED_NOT_NEEDED",
            verdict = "src-over-stack is already 100% exact on the dashboard fixed-function blend/coverage route, so byte quantization has no admissible correction scope here",
        )
    }

    private fun renderBitmap(
        context: WebGpuContext,
        gm: GM,
        intermediateFormat: GPUTextureFormat,
        targetColorSpaceBlend: Boolean,
    ): SkBitmap {
        val size = gm.size()
        SkWebGpuDevice(
            context,
            size.width,
            size.height,
            applyColorspaceTransform = true,
            targetColorSpaceBlend = targetColorSpaceBlend,
            intermediateFormat = intermediateFormat,
        ).use { device ->
            device.setBackground(gm.bgColor())
            gm.draw(SkCanvas(device))
            return rgbaBytesToBitmap(device.flush(), size.width, size.height)
        }
    }

    private fun renderSrcOverStackBitmap(context: WebGpuContext): SkBitmap =
        withGeneratedSolidRectDisabled {
            SkWebGpuDevice(
                context,
                SRC_OVER_STACK_SIZE,
                SRC_OVER_STACK_SIZE,
                intermediateFormat = GPUTextureFormat.RGBA16Float,
            ).use { device ->
                device.setBackground(SkColorSetARGB(0, 0, 0, 0))
                drawSrcOverStack(SkCanvas(device))
                rgbaBytesToBitmap(device.flush(), SRC_OVER_STACK_SIZE, SRC_OVER_STACK_SIZE)
            }
        }

    private fun drawSrcOverStack(canvas: SkCanvas) {
        canvas.drawRect(
            SkRect.MakeLTRB(8f, 8f, 40f, 40f),
            SkPaint().apply {
                color = SkColorSetARGB(0x80, 0xFF, 0x00, 0x00)
                blendMode = SkBlendMode.kSrc
            },
        )
        canvas.drawRect(
            SkRect.MakeLTRB(16f, 16f, 48f, 48f),
            SkPaint().apply {
                color = SkColorSetARGB(0x80, 0x00, 0x00, 0xFF)
                blendMode = SkBlendMode.kSrcOver
            },
        )
    }

    private fun <T> withGeneratedSolidRectDisabled(block: () -> T): T {
        val previous = System.getProperty(GeneratedSolidRectWgsl.FEATURE_FLAG)
        System.setProperty(GeneratedSolidRectWgsl.FEATURE_FLAG, "false")
        return try {
            block()
        } finally {
            if (previous == null) {
                System.clearProperty(GeneratedSolidRectWgsl.FEATURE_FLAG)
            } else {
                System.setProperty(GeneratedSolidRectWgsl.FEATURE_FLAG, previous)
            }
        }
    }

    private fun compareReference(reference: Any, actual: SkBitmap): For265ImageStats =
        when (reference) {
            is SkBitmap -> compareImages(reference, actual)
            is BufferedImage -> compareImages(reference, actual)
            else -> error("unsupported FOR-265 reference type: ${reference::class}")
        }

    private fun compareImages(reference: SkBitmap, gpu: SkBitmap): For265ImageStats {
        require(reference.width == gpu.width && reference.height == gpu.height) {
            "FOR-265 requires same-size SkBitmap evidence"
        }
        return comparePixels(reference.width, reference.height) { x, y -> pixelDelta(reference, gpu, x, y) }
    }

    private fun compareImages(reference: BufferedImage, gpu: SkBitmap): For265ImageStats {
        require(reference.width == gpu.width && reference.height == gpu.height) {
            "FOR-265 requires same-size BufferedImage/SkBitmap evidence"
        }
        return comparePixels(reference.width, reference.height) { x, y -> pixelDelta(reference, gpu, x, y) }
    }

    private fun comparePixels(width: Int, height: Int, sample: (Int, Int) -> PixelDelta): For265ImageStats {
        var matching = 0
        var maxDelta = 0
        var representative: PixelDelta? = null
        for (y in 0 until height) {
            for (x in 0 until width) {
                val delta = sample(x, y)
                if (delta.maxChannelDelta == 0) {
                    matching += 1
                } else if (representative == null) {
                    representative = delta
                }
                maxDelta = maxOf(maxDelta, delta.maxChannelDelta)
            }
        }
        val total = width * height
        return For265ImageStats(
            totalPixels = total,
            matchingPixels = matching,
            exactSimilarity = matching * 100.0 / total,
            maxDelta = maxDelta,
            representative = representative ?: sample(0, 0),
        )
    }

    private fun pixelDelta(reference: SkBitmap, gpu: SkBitmap, x: Int, y: Int): PixelDelta {
        val referenceRgba = rgbaAt(reference, x, y)
        val gpuRgba = rgbaAt(gpu, x, y)
        return PixelDelta(x = x, y = y, reference = referenceRgba, gpu = gpuRgba)
    }

    private fun pixelDelta(reference: BufferedImage, gpu: SkBitmap, x: Int, y: Int): PixelDelta {
        val referenceRgba = rgbaAt(reference, x, y)
        val gpuRgba = rgbaAt(gpu, x, y)
        return PixelDelta(x = x, y = y, reference = referenceRgba, gpu = gpuRgba)
    }

    private fun rgbaAt(bitmap: SkBitmap, x: Int, y: Int): IntArray {
        val pixel = bitmap.getPixel(x, y)
        return intArrayOf(
            (pixel ushr 16) and 0xFF,
            (pixel ushr 8) and 0xFF,
            pixel and 0xFF,
            (pixel ushr 24) and 0xFF,
        )
    }

    private fun rgbaAt(image: BufferedImage, x: Int, y: Int): IntArray {
        val pixel = image.getRGB(x, y)
        return intArrayOf(
            (pixel ushr 16) and 0xFF,
            (pixel ushr 8) and 0xFF,
            pixel and 0xFF,
            (pixel ushr 24) and 0xFF,
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
            bitmap.pixels8888[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return bitmap
    }

    private fun reconstructPresentFromShaderSideRgba(rgba: FloatArray): IntArray {
        val alpha = rgba[3].coerceIn(0f, 1f).toDouble()
        val rgbUnpremul = DoubleArray(3)
        if (alpha > 0.0) {
            val invAlpha = 1.0 / minOf(rgba[3].toDouble(), 1.0)
            for (channel in 0 until 3) {
                rgbUnpremul[channel] = (rgba[channel].toDouble() * invAlpha).coerceIn(0.0, 1.0)
            }
        }
        val encoded = srgbToRec2020Encoded(rgbUnpremul[0], rgbUnpremul[1], rgbUnpremul[2])
        return intArrayOf(
            (encoded[0] * 255.0 + 0.5).toInt().coerceIn(0, 255),
            (encoded[1] * 255.0 + 0.5).toInt().coerceIn(0, 255),
            (encoded[2] * 255.0 + 0.5).toInt().coerceIn(0, 255),
            (alpha * 255.0 + 0.5).toInt().coerceIn(0, 255),
        )
    }

    private fun quantizeToRgba8StoreFloat(rgba: FloatArray): FloatArray {
        val rgba8 = quantizeRgbaFloatsToBytes(rgba)
        return FloatArray(4) { channel -> rgba8[channel] / 255f }
    }

    private fun quantizeRgbaFloatsToBytes(rgba: FloatArray): IntArray =
        IntArray(4) { channel -> ((rgba[channel] * 255f) + 0.5f).toInt().coerceIn(0, 255) }

    private fun srgbToRec2020Encoded(r: Double, g: Double, b: Double): DoubleArray {
        val lr = srgbToLinear(r)
        val lg = srgbToLinear(g)
        val lb = srgbToLinear(b)
        val rr = 0.62740 * lr + 0.32928 * lg + 0.04338 * lb
        val rg = 0.06909 * lr + 0.91954 * lg + 0.01136 * lb
        val rb = 0.01639 * lr + 0.08801 * lg + 0.89559 * lb
        return doubleArrayOf(
            rec2020Encode(rr).coerceIn(0.0, 1.0),
            rec2020Encode(rg).coerceIn(0.0, 1.0),
            rec2020Encode(rb).coerceIn(0.0, 1.0),
        )
    }

    private fun srgbToLinear(v: Double): Double =
        if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)

    private fun rec2020Encode(v: Double): Double {
        val c = maxOf(v, 0.0)
        return if (c < 0.0181) 4.5 * c else 1.0993 * c.pow(0.45) - 0.0993
    }

    private fun readEvidencePng(path: String): BufferedImage {
        val file = repoFile(path)
        require(file.isFile) { "FOR-265 missing evidence PNG: $path" }
        return ImageIO.read(file) ?: error("FOR-265 failed to read evidence PNG: $path")
    }

    private fun writeFor265AuditJson(probe: For265AuditProbe) {
        val contents = """
            {
              "backend": "WebGPU",
              "referenceBackend": "mixed Skia GM references, FOR-259 shader-side representative samples, and live whole-scene WebGPU renders",
              "linear": "FOR-265",
              "probe": "rgba16float-present-byte-quantization-family-scope",
              "deltaDefinition": "signed channel delta is presented WebGPU byte output minus reference",
              "newRendererProperty": "none",
              "defaultEnabled": false,
              "runtimeSnapshotsEnabled": false,
              "normalRenderingChanged": false,
              "normalShadersChanged": false,
              "normalThresholdsChanged": false,
              "cropPolicyChanged": false,
              "fallbackPolicyChanged": false,
              "intermediateFormatPolicyChanged": false,
              "targetColorSpaceBlendGloballyEnabled": false,
              "currentPolicy": "${probe.currentPolicy}",
              "caseCount": ${probe.caseCount},
              "observedBoundaries": {
                "rgba16FloatIntermediateObserved": ${probe.rgba16FloatIntermediateObserved},
                "presentByteOutputObserved": ${probe.presentByteOutputObserved},
                "for261ResidualCasesCompared": ${probe.residualCasesCompared},
                "bitmapImageRectObserved": ${probe.bitmapImageRectObserved},
                "exactControlObserved": ${probe.exactControlPreserved},
                "targetColorSpaceBlendSensitiveCaseObserved": ${probe.targetColorSpaceBlendSignalPreserved},
                "dashboardBlendCoverageControlObserved": ${probe.dashboardBlendCoverageControlObserved}
              },
              "cases": [
            ${probe.cases.joinToString(",\n") { it.toJson(indent = "                ") }}
              ],
              "summary": {
                "residualCasesCompared": ${probe.residualCasesCompared},
                "bitmapImageRectObserved": ${probe.bitmapImageRectObserved},
                "exactControlPreserved": ${probe.exactControlPreserved},
                "targetColorSpaceBlendSignalPreserved": ${probe.targetColorSpaceBlendSignalPreserved},
                "dashboardBlendCoverageControlObserved": ${probe.dashboardBlendCoverageControlObserved},
                "exactControlsRegressed": ${probe.exactControlsRegressed},
                "quantizationExplainsAllRequiredCases": ${probe.quantizationExplainsAllRequiredCases},
                "safeCorrectionProven": ${probe.safeCorrectionProven}
              },
              "finding": "${probe.finding}",
              "admissibleCorrection": "${probe.admissibleCorrection}",
              "missingCondition": "${probe.missingCondition}",
              "remainingBoundary": "${probe.remainingBoundary}",
              "interpretation": "FOR-265 expands the RGBA16Float intermediate-to-present byte-boundary audit to residual, bitmap/image-rect, opaque exact, targetColorSpaceBlend-sensitive, and dashboard blend/coverage families without changing production behavior. Exact controls remain exact, but the targetColorSpaceBlend-sensitive fixture and residual-only representatives prevent a safe family correction.",
              "observationMethod": "test-only audit using live SkWebGpuDevice renders plus fixed FOR-259 shader-side representative store samples and existing dashboard route evidence; no renderer property, no default switch, no normal shader change, no threshold change, no Crop correction, no fallback-policy change, and no global targetColorSpaceBlend change",
              "supportDecision": "${probe.supportDecision}",
              "correctionApplied": ${probe.correctionApplied},
              "preservedUnsupportedReason": "${probe.preservedUnsupportedReason}"
            }
            """.trimIndent() + "\n"
        listOf(
            "reports/wgsl-pipeline/scenes/generated/artifacts/rgba16float-quantization-family-scope-for265",
            "reports/wgsl-pipeline/scenes/artifacts/rgba16float-quantization-family-scope-for265",
        ).forEach { path ->
            val dir = repoFile(path).apply { mkdirs() }
            File(dir, "rgba16float-quantization-family-scope-for265.json").writeText(contents)
        }
    }

    private fun writeFor265AuditReport(probe: For265AuditProbe) {
        val report = buildString {
            appendLine("# FOR-265 RGBA16Float Quantization Family Scope")
            appendLine()
            appendLine("Date: 2026-06-03")
            appendLine()
            appendLine("Decision: `KEEP_DIAGNOSTIC`")
            appendLine()
            appendLine("FOR-265 compares the normal `RGBA16Float` intermediate store boundary")
            appendLine("against the presented byte output for a family covering FOR-261/FOR-264")
            appendLine("residuals, bitmap/image-rect, an exact opaque control, a")
            appendLine("`targetColorSpaceBlend`-sensitive control, and a dashboard")
            appendLine("blend/coverage control. It does not change production defaults,")
            appendLine("shaders, thresholds, Crop policy, fallback policy, or global renderer")
            appendLine("properties.")
            appendLine()
            appendLine("Preserved fallback reason:")
            appendLine()
            appendLine("```text")
            appendLine(probe.preservedUnsupportedReason)
            appendLine("```")
            appendLine()
            appendLine("## Artifacts")
            appendLine()
            appendLine("- `reports/wgsl-pipeline/scenes/generated/artifacts/rgba16float-quantization-family-scope-for265/rgba16float-quantization-family-scope-for265.json`")
            appendLine("- `reports/wgsl-pipeline/scenes/artifacts/rgba16float-quantization-family-scope-for265/rgba16float-quantization-family-scope-for265.json`")
            appendLine()
            appendLine("## Cases")
            appendLine()
            appendLine("| Case | Exact similarity | Max delta | Matching pixels | Format | Quantization policy | Route diagnostics | Classification |")
            appendLine("|---|---:|---:|---:|---|---|---|---|")
            for (case in probe.cases) {
                appendLine(
                    "| `${case.id}` | ${case.current.exactSimilarity.reportValue()} | ${case.current.maxDelta} | " +
                        "${case.current.matchingPixels}/${case.current.totalPixels} | `${case.boundary.intermediateFormat}` | " +
                        "`${case.boundary.observedPolicy}` | `${case.routeDiagnostics}` | `${case.boundary.boundaryClassification}` |",
                )
                case.targetBlendControl?.let { target ->
                    appendLine(
                        "| `${case.id}.targetBlendControl` | ${target.exactSimilarity.reportValue()} | ${target.maxDelta} | " +
                            "${target.matchingPixels}/${target.totalPixels} | `${case.boundary.intermediateFormat}` | " +
                            "`targetColorSpaceBlend=true control` | `${target.routeDiagnostics}` | `${target.responsibilityClassification}` |",
                    )
                }
            }
            appendLine()
            appendLine("## Conclusion")
            appendLine()
            appendLine(probe.admissibleCorrection)
            appendLine()
            appendLine("No exact control regressed: `${!probe.exactControlsRegressed}`.")
            appendLine()
            appendLine("Missing condition: `${probe.missingCondition}`.")
            appendLine()
            appendLine("The remaining boundary stays `${probe.remainingBoundary}`.")
            appendLine()
            appendLine("## Validation")
            appendLine()
            appendLine("```text")
            appendLine("rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests '*FOR-265*'")
            appendLine("rtk python3 scripts/validate_for265_rgba16float_quantization_family_scope.py")
            appendLine("rtk python3 scripts/validate_for264_rgba16float_present_quantization_audit.py")
            appendLine("rtk python3 scripts/validate_for263_target_blend_intermediate_matrix_audit.py")
            appendLine("rtk python3 scripts/validate_for261_whole_scene_rgba8_intermediate_audit.py")
            appendLine("rtk ./gradlew --no-daemon pipelineSceneDashboardGate")
            appendLine("rtk git diff --check")
            appendLine("```")
        }
        repoFile("reports/wgsl-pipeline/2026-06-03-for-265-rgba16float-quantization-family-scope.md")
            .writeText(report)
    }

    private fun For265Case.toJson(indent: String): String =
        """
        {
          "id": ${id.jsonString()},
          "sceneId": ${sceneId.jsonString()},
          "kind": ${kind.jsonString()},
          "routeDiagnostics": ${routeDiagnostics.jsonString()},
          "current": ${current.toJson(indent = "$indent  ").trimStart()},
          "targetBlendControl": ${targetBlendControl?.toJson(indent = "$indent  ")?.trimStart() ?: "null"},
          "boundary": ${boundary.toJson(indent = "$indent  ").trimStart()},
          "correctionStatus": ${correctionStatus.jsonString()},
          "admissibility": ${admissibility.jsonString()},
          "verdict": ${verdict.jsonString()}
        }
        """.trimIndent().prependIndent(indent)

    private fun For265PolicyStats.toJson(indent: String): String =
        """
        {
          "policy": ${policy.jsonString()},
          "evaluationKind": ${evaluationKind.jsonString()},
          "totalPixels": $totalPixels,
          "matchingPixels": $matchingPixels,
          "exactSimilarity": $exactSimilarity,
          "maxDelta": $maxDelta,
          "referenceRgba": ${jsonArray(referenceRgba)},
          "observedRgba": ${jsonArray(observedRgba)},
          "signedDeltaRgba": ${jsonArray(signedDeltaRgba)},
          "intermediateFormat": ${intermediateFormat.jsonString()},
          "routeDiagnostics": ${routeDiagnostics.jsonString()},
          "routeDiagnosticsRationale": ${routeDiagnosticsRationale.jsonString()},
          "responsibilityClassification": ${responsibilityClassification.jsonString()}
        }
        """.trimIndent().prependIndent(indent)

    private fun For265BoundaryComparison.toJson(indent: String): String =
        """
        {
          "shaderSideXy": ${jsonArray(shaderSideXy)},
          "intermediateFormat": ${intermediateFormat.jsonString()},
          "intermediateStoreExpectedRgbaFloat": ${jsonArray(intermediateStoreExpectedRgbaFloat)},
          "intermediateStoreExpectedRgba8": ${jsonArray(intermediateStoreExpectedRgba8)},
          "presentedObservedRgba": ${jsonArray(presentedObservedRgba)},
          "presentedReconstructedFromIntermediateRgba": ${jsonArray(presentedReconstructedFromIntermediateRgba)},
          "simulatedQuantizationPolicy": ${simulatedQuantizationPolicy.jsonString()},
          "simulatedQuantizedStoreRgbaFloat": ${jsonArray(simulatedQuantizedStoreRgbaFloat)},
          "simulatedQuantizedStoreRgba8": ${jsonArray(simulatedQuantizedStoreRgba8)},
          "simulatedQuantizedPresentRgba": ${jsonArray(simulatedQuantizedPresentRgba)},
          "observedPolicy": ${observedPolicy.jsonString()},
          "boundaryClassification": ${boundaryClassification.jsonString()}
        }
        """.trimIndent().prependIndent(indent)

    private fun jsonArray(values: IntArray): String = values.joinToString(prefix = "[", postfix = "]")

    private fun jsonArray(values: FloatArray): String =
        values.joinToString(prefix = "[", postfix = "]") { value ->
            String.format(Locale.US, "%.8f", value).trimEnd('0').trimEnd('.')
        }

    private fun Double.reportValue(): String =
        String.format(Locale.US, "%.6f", this).trimEnd('0').trimEnd('.')

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

    private fun repoFile(path: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        return File(dir, path)
    }

    private class NeutralAaCoverageGM : GM() {
        override fun getName(): String = "m60_neutral_aa_coverage"
        override fun getISize(): SkISize = SkISize.Make(4, 1)

        override fun onDraw(canvas: SkCanvas?) {
            val c = canvas ?: return
            c.drawColor(SK_ColorWHITE)
            c.drawRect(
                SkRect.MakeLTRB(0.5f, 0f, 1.5f, 1f),
                SkPaint().apply {
                    color = SK_ColorBLACK
                    isAntiAlias = true
                    style = SkPaint.Style.kFill_Style
                },
            )
        }
    }

    private class BlackWhiteRectGM : GM() {
        override fun getName(): String = "for265_black_white_rect"
        override fun getISize(): SkISize = SkISize.Make(8, 8)

        override fun onDraw(canvas: SkCanvas?) {
            val c = canvas ?: return
            c.drawColor(SK_ColorWHITE)
            c.drawRect(
                SkRect.MakeLTRB(2f, 1f, 7f, 6f),
                SkPaint().apply {
                    color = SK_ColorBLACK
                    isAntiAlias = false
                    style = SkPaint.Style.kFill_Style
                },
            )
        }
    }

    private data class PixelDelta(
        val x: Int,
        val y: Int,
        val reference: IntArray,
        val gpu: IntArray,
    ) {
        val delta: IntArray = IntArray(4) { channel -> kotlin.math.abs(reference[channel] - gpu[channel]) }
        val signedDelta: IntArray = IntArray(4) { channel -> gpu[channel] - reference[channel] }
        val maxChannelDelta: Int = delta.maxOrNull() ?: 0
    }

    private data class For265ImageStats(
        val totalPixels: Int,
        val matchingPixels: Int,
        val exactSimilarity: Double,
        val maxDelta: Int,
        val representative: PixelDelta,
    )

    private data class For265PolicyStats(
        val policy: String,
        val evaluationKind: String,
        val totalPixels: Int,
        val matchingPixels: Int,
        val exactSimilarity: Double,
        val maxDelta: Int,
        val referenceRgba: IntArray,
        val observedRgba: IntArray,
        val signedDeltaRgba: IntArray,
        val intermediateFormat: String,
        val routeDiagnostics: String,
        val routeDiagnosticsRationale: String,
        val responsibilityClassification: String,
    ) {
        companion object {
            fun fromImageStats(
                stats: For265ImageStats,
                policy: String,
                route: String,
                classification: String,
            ): For265PolicyStats =
                For265PolicyStats(
                    policy = policy,
                    evaluationKind = "whole-scene-reference-vs-live-webgpu",
                    totalPixels = stats.totalPixels,
                    matchingPixels = stats.matchingPixels,
                    exactSimilarity = stats.exactSimilarity,
                    maxDelta = stats.maxDelta,
                    referenceRgba = stats.representative.reference,
                    observedRgba = stats.representative.gpu,
                    signedDeltaRgba = stats.representative.signedDelta,
                    intermediateFormat = "RGBA16Float",
                    routeDiagnostics = route,
                    routeDiagnosticsRationale = "Live WebGPU render with test-only audit observation; default targetColorSpaceBlend=false and intermediateFormat=RGBA16Float remain unchanged unless this policy explicitly says targetBlend=true.",
                    responsibilityClassification = classification,
                )
        }
    }

    private data class For265BoundaryComparison(
        val shaderSideXy: IntArray,
        val intermediateFormat: String,
        val intermediateStoreExpectedRgbaFloat: FloatArray,
        val intermediateStoreExpectedRgba8: IntArray,
        val presentedObservedRgba: IntArray,
        val presentedReconstructedFromIntermediateRgba: IntArray,
        val simulatedQuantizationPolicy: String,
        val simulatedQuantizedStoreRgbaFloat: FloatArray,
        val simulatedQuantizedStoreRgba8: IntArray,
        val simulatedQuantizedPresentRgba: IntArray,
        val observedPolicy: String,
        val boundaryClassification: String,
    )

    private data class For265Case(
        val id: String,
        val sceneId: String,
        val kind: String,
        val routeDiagnostics: String,
        val current: For265PolicyStats,
        val targetBlendControl: For265PolicyStats?,
        val boundary: For265BoundaryComparison,
        val correctionStatus: String,
        val admissibility: String,
        val verdict: String,
    )

    private data class For265AuditProbe(
        val cases: List<For265Case>,
    ) {
        val currentPolicy: String = "targetColorSpaceBlend=false with normal RGBA16Float intermediate"
        val caseCount: Int = cases.size
        val rgba16FloatIntermediateObserved: Boolean = cases.all { it.boundary.intermediateFormat == "RGBA16Float" }
        val presentByteOutputObserved: Boolean =
            cases.all { it.boundary.presentedObservedRgba.contentEquals(it.boundary.presentedReconstructedFromIntermediateRgba) }
        val residualCasesCompared: Boolean =
            cases.count { it.kind == "for261-residual-rgba16float-present-boundary" } >= 2
        val bitmapImageRectObserved: Boolean =
            cases.any { it.sceneId == "bitmap-rect-nearest" && it.routeDiagnostics == "webgpu.image-rect.strict-nearest" }
        val exactControlPreserved: Boolean =
            cases.any { it.kind == "exact-control-already-100-percent" && it.current.exactSimilarity == 100.0 }
        val targetColorSpaceBlendSignalPreserved: Boolean =
            cases.any {
                it.kind == "targetColorSpaceBlend-sensitive-control" &&
                    it.current.exactSimilarity < 100.0 &&
                    it.targetBlendControl?.exactSimilarity == 100.0
            }
        val dashboardBlendCoverageControlObserved: Boolean =
            cases.any {
                it.kind == "dashboard-blend-coverage-exact-control" &&
                    it.routeDiagnostics == "webgpu.blend.src-over.fixed-function" &&
                    it.current.exactSimilarity == 100.0
            }
        val exactControlsRegressed: Boolean =
            cases.filter {
                it.kind == "exact-control-already-100-percent" ||
                    it.kind == "dashboard-blend-coverage-exact-control"
            }.any { it.current.exactSimilarity < 100.0 || it.current.maxDelta != 0 }
        val quantizationExplainsAllRequiredCases: Boolean = false
        val safeCorrectionProven: Boolean = false
        val finding: String =
            "rgba16float_present_quantization_family_scope_not_proven_targetColorSpaceBlend_boundary_stays_separate"
        val admissibleCorrection: String =
            "none_applied: RGBA16Float present-byte quantization remains diagnostic because the residual representatives are bounded samples, exact opaque and blend/coverage controls need no correction, and the targetColorSpaceBlend-sensitive fixture is corrected only by targetColorSpaceBlend=true"
        val missingCondition: String =
            "missing_family_bound_proof_that_rgba16float_present_byte_quantization_is_safe_without_targetColorSpaceBlend"
        val remainingBoundary: String =
            "rgba16float-intermediate-store-to-present-byte-quantization-policy"
        val supportDecision: String = "KEEP_DIAGNOSTIC"
        val correctionApplied: Boolean = false
        val preservedUnsupportedReason: String = "image-filter.crop-input-nonnull-prepass-required"
    }

    private companion object {
        const val WRITE_EVIDENCE_PROPERTY: String = "kanvas.sceneEvidence.write"
        const val SRC_OVER_STACK_SIZE: Int = 64
    }
}
