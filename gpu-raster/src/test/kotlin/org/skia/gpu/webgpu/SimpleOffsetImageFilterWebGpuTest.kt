package org.skia.gpu.webgpu

import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import org.skia.foundation.SkSamplingOptions
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.testing.TestUtils
import org.skia.tests.SimpleOffsetImageFilterGM
import java.io.File
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * O6 cross-test : `SimpleOffsetImageFilterGM`
 * (`simple-offsetimagefilter`, 640x200) on the GPU backend.
 * Exercises `SkImageFilters.Offset` with assorted cropRect / clipRect
 * combos. FOR-242 ratchets the bounded WebGPU pre-pass above the old
 * diagnostic score without claiming strict Skia fidelity.
 */
class SimpleOffsetImageFilterWebGpuTest {
    @Test
    fun `SimpleOffsetImageFilterGM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(SimpleOffsetImageFilterGM(), floor = 98.43)
    }

    @Test
    fun `FOR-247 crop offset prepass scratch pixel matches source local probe`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val oldProbe = System.getProperty(FOR247_PROBE_PROPERTY)
        try {
            System.setProperty(FOR247_PROBE_PROPERTY, "true")
            context!!.use { ctx ->
                val bitmap = WebGpuSink.draw(ctx, SimpleOffsetImageFilterGM())
                val sourceLocal = rgbaAt(bitmap, x = 0, y = 0)
                val scratch = rgbaAt(bitmap, x = 1, y = 0)
                assertArrayEquals(
                    sourceLocal,
                    scratch,
                    "FOR-247: scratchPixel(45,5) must match sourceLocal(5,5) " +
                        "before the final Crop composite",
                )
                if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
                    writeFor247ScratchProbeJson(sourceLocal, scratch)
                }
            }
        } finally {
            if (oldProbe == null) {
                System.clearProperty(FOR247_PROBE_PROPERTY)
            } else {
                System.setProperty(FOR247_PROBE_PROPERTY, oldProbe)
            }
        }
    }

    @Test
    fun `FOR-248 final crop composite maps target fragment to scratch pixel probe`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val oldProbe = System.getProperty(FOR248_PROBE_PROPERTY)
        try {
            System.setProperty(FOR248_PROBE_PROPERTY, "true")
            context!!.use { ctx ->
                val bitmap = WebGpuSink.draw(ctx, SimpleOffsetImageFilterGM())
                val directScratch = rgbaAt(bitmap, x = 2, y = 0)
                val finalCropComposite = rgbaAt(bitmap, x = 3, y = 0)
                val finalFragment = rgbaAt(bitmap, x = 385, y = 125)
                assertArrayEquals(
                    intArrayOf(202, 59, 19, 255),
                    directScratch,
                    "FOR-248: direct diagnostic read of scratchPixel(45,5) changed",
                )
                assertArrayEquals(
                    directScratch.copyOfRange(0, 3),
                    finalCropComposite.copyOfRange(0, 3),
                    "FOR-248: final Crop composite remap must preserve scratchPixel(45,5) RGB",
                )
                assertArrayEquals(
                    intArrayOf(102),
                    intArrayOf(finalCropComposite[3]),
                    "FOR-248: final Crop composite remap must apply the GM paint alpha " +
                        "instead of decal-transparentizing the fragment",
                )
                if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
                    writeFor248FinalCropCompositeProbeJson(
                        directScratch = directScratch,
                        finalCropComposite = finalCropComposite,
                        finalFragment = finalFragment,
                    )
                }
            }
        } finally {
            if (oldProbe == null) {
                System.clearProperty(FOR248_PROBE_PROPERTY)
            } else {
                System.setProperty(FOR248_PROBE_PROPERTY, oldProbe)
            }
        }
    }

    @Test
    fun `FOR-249 residual window compares reference and GPU around crop clip dst fragment`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val gm = SimpleOffsetImageFilterGM()
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "original-888/${gm.name()}.png missing")

        context!!.use { ctx ->
            val gpu = WebGpuSink.draw(ctx, gm)
            val probe = buildFor249ResidualProbe(reference!!, gpu)
            assertArrayEquals(
                intArrayOf(221, 153, 145, 255),
                probe.target.reference,
                "FOR-249: reference target fragment at (385,125) changed",
            )
            assertArrayEquals(
                intArrayOf(220, 153, 145, 255),
                probe.target.gpu,
                "FOR-249: GPU target fragment at (385,125) changed",
            )
            assertTrue(
                probe.windowMaxChannelDelta <= 1,
                "FOR-249: local 5x5 residual around (385,125) must stay <= 1 byte",
            )
            if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
                writeFor249ReferenceGpuResidualProbeJson(probe)
            }
        }
    }

    @Test
    fun `FOR-250 high delta scan classifies SimpleOffset residual pixels by cell`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val gm = SimpleOffsetImageFilterGM()
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "original-888/${gm.name()}.png missing")

        context!!.use { ctx ->
            val gpu = WebGpuSink.draw(ctx, gm)
            val probe = buildFor250HighDeltaScanProbe(reference!!, gpu)
            assertTrue(
                probe.totalPixelsAboveThreshold > 0,
                "FOR-250: scene-level residual scan must keep finding the high-delta tail",
            )
            assertTrue(
                probe.maxChannelDelta > HIGH_DELTA_THRESHOLD,
                "FOR-250: max delta unexpectedly collapsed; keep the scan diagnostic until root-cause proof",
            )
            assertArrayEquals(
                intArrayOf(0),
                intArrayOf(probe.totalPixelsAboveStrictHighDeltaThreshold),
                "FOR-250: no >8 high-delta pixel is expected; the residual is a diffuse byte-level tail",
            )
            assertTrue(
                probe.topPixels.isNotEmpty(),
                "FOR-250: high-delta scan must keep a stable top-pixel list",
            )
            assertTrue(
                probe.cellAggregates.any { it.pixelsAboveThreshold > 0 },
                "FOR-250: high-delta pixels should classify into SimpleOffset cells",
            )
            if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
                writeFor250HighDeltaScanJson(probe)
            }
        }
    }

    @Test
    fun `FOR-251 color premul audit classifies SimpleOffset byte residual`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val gm = SimpleOffsetImageFilterGM()
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "original-888/${gm.name()}.png missing")

        context!!.use { ctx ->
            val gpu = WebGpuSink.draw(ctx, gm)
            val probe = buildFor251ColorPremulAuditProbe(reference!!, gpu)
            assertArrayEquals(
                intArrayOf(6622),
                intArrayOf(probe.totalResidualPixels),
                "FOR-251: color audit must track the known FOR-250 byte-level residual population",
            )
            assertArrayEquals(
                intArrayOf(1),
                intArrayOf(probe.maxChannelDelta),
                "FOR-251: residual should remain a 1-byte color tail",
            )
            assertArrayEquals(
                intArrayOf(0),
                intArrayOf(probe.alphaDeltaNonZeroPixels),
                "FOR-251: alpha deltas would indicate a premultiplication/composite class change",
            )
            assertTrue(
                probe.dominantCellAggregates.all { it.residualPixels > 0 && it.maxChannelDelta == 1 },
                "FOR-251: dominant residual cells must stay byte-level and non-empty",
            )
            if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
                writeFor251ColorPremulAuditJson(probe)
            }
        }
    }

    @Test
    fun `FOR-252 color reference bias audit compares non image filter samples`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val gm = SimpleOffsetImageFilterGM()
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "original-888/${gm.name()}.png missing")

        context!!.use { ctx ->
            val gpu = WebGpuSink.draw(ctx, gm)
            val probe = buildFor252ColorReferenceBiasAuditProbe(reference!!, gpu)
            assertEquals(
                3,
                probe.sampleCount,
                "FOR-252: audit must cover SimpleOffset no-filter plus two non image-filter scenes",
            )
            assertTrue(
                probe.samples.all { !it.imageFilterInPath },
                "FOR-252: every audited sample must stay outside image-filter routing",
            )
            assertEquals(
                1600,
                probe.samples.single { it.id == "simple-offsetimagefilter.row1-col0-no-filter" }.residualPixels,
                "FOR-252: SimpleOffset no-filter cell must keep reproducing the FOR-251 RGB byte tail",
            )
            assertEquals(
                1,
                probe.maxChannelDelta,
                "FOR-252: non image-filter audit should not find a larger bounded renderer bug",
            )
            assertEquals(
                0,
                probe.alphaDeltaNonZeroPixels,
                "FOR-252: alpha deltas would change the premultiplication diagnosis",
            )
            if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
                writeFor252ColorReferenceBiasAuditJson(probe)
            }
        }
    }

    @Test
    fun `FOR-253 bitmap source rounding audit isolates residual stage`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val gm = SimpleOffsetImageFilterGM()
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "original-888/${gm.name()}.png missing")

        context!!.use { ctx ->
            val gpu = WebGpuSink.draw(ctx, gm)
            val probe = buildFor253BitmapSourceRoundingAuditProbe(reference!!, gpu)
            assertEquals(
                3,
                probe.microCaseCount,
                "FOR-253: audit must cover source-color, bitmap nearest, and gradient control cases",
            )
            assertTrue(
                probe.appearsBeforeBitmapSampling,
                "FOR-253: source-color residual must prove the bias exists without bitmap sampling",
            )
            assertTrue(
                probe.appearsDuringBitmapNearestPath,
                "FOR-253: bitmap-nearest residual must keep reproducing the source/input color tail",
            )
            assertEquals(
                false,
                probe.onlyFinalPackOrStore,
                "FOR-253: exact gradient control rules out a global final pack/store-only defect",
            )
            assertEquals(
                "KEEP_DIAGNOSTIC",
                probe.supportDecision,
                "FOR-253: no bounded correction is proven by this audit",
            )
            if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
                writeFor253BitmapSourceRoundingAuditJson(probe)
            }
        }
    }

    @Test
    fun `FOR-254 source uniform and bitmap texel rounding audit compares input paths`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val gm = SimpleOffsetImageFilterGM()
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "original-888/${gm.name()}.png missing")

        context!!.use { ctx ->
            val gpu = WebGpuSink.draw(ctx, gm)
            val probe = buildFor254SourceTexelRoundingAuditProbe(reference!!, gpu)
            assertEquals(
                4,
                probe.pathCount,
                "FOR-254: audit must compare source uniform, generated solid, generated gradient, and bitmap texel paths",
            )
            assertEquals(
                "KEEP_DIAGNOSTIC",
                probe.supportDecision,
                "FOR-254: no bounded correction is proven without raw uniform/texel sentinels",
            )
            assertEquals(
                true,
                probe.legacySourceUniformHasResidual,
                "FOR-254: legacy source-color uniform path must keep reproducing the RGB byte tail",
            )
            assertEquals(
                true,
                probe.bitmapTexelPathHasResidual,
                "FOR-254: bitmap upload/sample path must keep reproducing the RGB byte tail",
            )
            assertEquals(
                listOf("generated-solid-control", "generated-gradient-control"),
                probe.exactControlKinds,
                "FOR-254: generated solid and gradient controls must remain exact",
            )
            if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
                writeFor254SourceTexelRoundingAuditJson(probe)
            }
        }
    }

    @Test
    fun `FOR-255 raw color sentinels capture uniform and texel input boundary`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val gm = SimpleOffsetImageFilterGM()
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "original-888/${gm.name()}.png missing")

        val oldSentinels = System.getProperty(FOR255_RAW_SENTINELS_PROPERTY)
        try {
            System.setProperty(FOR255_RAW_SENTINELS_PROPERTY, "true")
            context!!.use { ctx ->
                val simpleOffset = renderSimpleOffsetWithRawColorSentinels(ctx, gm)
                val bitmapTexel = renderBitmapUploadRawColorSentinel(ctx)
                val for254 = buildFor254SourceTexelRoundingAuditProbe(reference!!, simpleOffset.bitmap)
                val probe = buildFor255RawColorSentinelProbe(
                    sourceUniformSnapshot = simpleOffset.sentinels,
                    bitmapUploadSnapshot = bitmapTexel.sentinels,
                    for254 = for254,
                )
                assertEquals(
                    FOR255_RAW_SENTINELS_PROPERTY,
                    probe.propertyName,
                    "FOR-255: opt-in property name changed",
                )
                assertEquals(
                    false,
                    probe.defaultEnabled,
                    "FOR-255: raw sentinels must stay disabled by default",
                )
                assertEquals(
                    intArrayOf(255, 0, 0, 102).toList(),
                    probe.legacySourceUniform.rawWriteObservedRgba8.toList(),
                    "FOR-255: legacy source-color uniform raw bytes changed",
                )
                assertEquals(
                    listOf("3f800000", "00000000", "00000000", "3ecccccd"),
                    probe.legacySourceUniform.rawUniformWordHex,
                    "FOR-255: legacy source-color uniform raw float words changed",
                )
                assertEquals(
                    intArrayOf(149, 193, 207, 255).toList(),
                    probe.bitmapTexel.rawUploadObservedRgba8.toList(),
                    "FOR-255: raw uploaded RGBA8 texel bytes changed",
                )
                assertEquals(
                    "notCaptured",
                    probe.shaderObserved.status,
                    "FOR-255: shader-observed sentinel unexpectedly changed class",
                )
                assertEquals(
                    "KEEP_DIAGNOSTIC",
                    probe.supportDecision,
                    "FOR-255: raw sentinels do not yet prove a bounded correction",
                )
                if (System.getProperty(WRITE_EVIDENCE_PROPERTY) == "true") {
                    writeFor255RawColorSentinelAuditJson(probe)
                }
            }
        } finally {
            if (oldSentinels == null) {
                System.clearProperty(FOR255_RAW_SENTINELS_PROPERTY)
            } else {
                System.setProperty(FOR255_RAW_SENTINELS_PROPERTY, oldSentinels)
            }
        }
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

    private fun renderSimpleOffsetWithRawColorSentinels(
        context: WebGpuContext,
        gm: SimpleOffsetImageFilterGM,
    ): RawSentinelRenderResult {
        val size = gm.size()
        SkWebGpuDevice(
            context,
            size.width,
            size.height,
            applyColorspaceTransform = true,
        ).use { device ->
            device.setBackground(gm.bgColor())
            gm.draw(SkCanvas(device))
            val rgba = device.flush()
            return RawSentinelRenderResult(
                bitmap = rgbaBytesToBitmap(rgba, size.width, size.height),
                sentinels = device.rawColorSentinelSnapshot(),
            )
        }
    }

    private fun renderBitmapUploadRawColorSentinel(context: WebGpuContext): RawUploadSentinelRenderResult {
        val image = SkImage(
            width = 1,
            height = 1,
            pixels = intArrayOf((0xFF shl 24) or (149 shl 16) or (193 shl 8) or 207),
        )
        SkWebGpuDevice(context, width = 1, height = 1).use { device ->
            device.setBackground(0xFFFFFFFF.toInt())
            SkCanvas(device).drawImageRect(
                image,
                SkRect.MakeWH(1f, 1f),
                SkRect.MakeXYWH(0f, 0f, 1f, 1f),
                SkSamplingOptions.nearest(),
            )
            val rgba = device.flush()
            return RawUploadSentinelRenderResult(
                outputRgba = intArrayOf(
                    rgba[0].toInt() and 0xFF,
                    rgba[1].toInt() and 0xFF,
                    rgba[2].toInt() and 0xFF,
                    rgba[3].toInt() and 0xFF,
                ),
                sentinels = device.rawColorSentinelSnapshot(),
            )
        }
    }

    private fun rgbaBytesToBitmap(rgba: ByteArray, w: Int, h: Int): SkBitmap {
        require(rgba.size == w * h * 4) {
            "RGBA buffer size mismatch: expected ${w * h * 4} bytes for $w x $h, got ${rgba.size}"
        }
        val bitmap = SkBitmap(w, h, colorType = SkColorType.kRGBA_8888)
        for (i in 0 until w * h) {
            val base = i * 4
            val r = rgba[base].toInt() and 0xFF
            val g = rgba[base + 1].toInt() and 0xFF
            val b = rgba[base + 2].toInt() and 0xFF
            val a = rgba[base + 3].toInt() and 0xFF
            bitmap.pixels8888[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return bitmap
    }

    private fun buildFor255RawColorSentinelProbe(
        sourceUniformSnapshot: SkWebGpuDevice.RawColorSentinelSnapshot,
        bitmapUploadSnapshot: SkWebGpuDevice.RawColorSentinelSnapshot,
        for254: For254SourceTexelRoundingAuditProbe,
    ): For255RawColorSentinelProbe {
        val sourceCase = for254.cases.single { it.id == "legacy-source-color-uniform.simple-offset-row1-col0" }
        val bitmapCase = for254.cases.single { it.id == "bitmap-texel-upload-sample.bitmap-rect-nearest" }
        val sourceUniformWrite = sourceUniformSnapshot.rectUniformColorWrites.firstOrNull { write ->
            write.scissor.contentEquals(intArrayOf(40, 40, 40, 40)) &&
                write.sourceRgba8.contentEquals(intArrayOf(255, 0, 0, 102))
        } ?: error("FOR-255 missing raw source-color uniform sentinel for SimpleOffset row1-col0")
        val bitmapUpload = bitmapUploadSnapshot.rgba8TextureUploads.firstOrNull { upload ->
            upload.texelSamples.any { sample -> sample.rgba.contentEquals(intArrayOf(149, 193, 207, 255)) }
        } ?: error("FOR-255 missing raw uploaded RGBA8 texel sentinel")
        val bitmapTexel = bitmapUpload.texelSamples.first { sample ->
            sample.rgba.contentEquals(intArrayOf(149, 193, 207, 255))
        }
        return For255RawColorSentinelProbe(
            propertyName = FOR255_RAW_SENTINELS_PROPERTY,
            defaultEnabled = false,
            runtimeSnapshotsEnabled = sourceUniformSnapshot.enabled && bitmapUploadSnapshot.enabled,
            legacySourceUniform = For255UniformBoundary(
                id = sourceCase.id,
                hostObservedRgba8 = sourceCase.inputObservation.packedRgba,
                hostObservedNormalizedRgba = sourceCase.inputObservation.normalizedRgba,
                rawWriteObservedRgba8 = sourceUniformWrite.sourceRgba8,
                rawUniformWordBits = sourceUniformWrite.rawColorWordBits,
                rawUniformWordHex = sourceUniformWrite.rawColorWordHex,
                scissor = sourceUniformWrite.scissor,
                output = sourceCase.representativeOutput,
                verdict = "raw uniform write matches host-normalized legacy source color; the 1-byte RGB tail remains after uniform consumption/blend/reference comparison",
            ),
            bitmapTexel = For255BitmapBoundary(
                id = bitmapCase.id,
                hostObservedRgba8 = bitmapCase.inputObservation.packedRgba,
                rawUploadObservedRgba8 = bitmapTexel.rgba,
                uploadSize = intArrayOf(bitmapUpload.width, bitmapUpload.height),
                uploadTexelXy = intArrayOf(bitmapTexel.x, bitmapTexel.y),
                output = bitmapCase.representativeOutput,
                verdict = "raw RGBA8 upload bytes match the representative bitmap texel; the 1-byte RGB tail remains after texture normalization/sample/reference comparison",
            ),
            shaderObserved = For255ShaderObservedBoundary(
                status = "notCaptured",
                reason = "Capturing pre-blend shader values would require a diagnostic WGSL/storage-buffer or alternate render target path. That would change pipeline layout/shader code for this ticket; FOR-255 keeps normal rendering unchanged and records only already-computed host/write/upload sentinels.",
            ),
        )
    }

    private fun buildFor250HighDeltaScanProbe(reference: SkBitmap, gpu: SkBitmap): For250HighDeltaScanProbe {
        require(reference.width == gpu.width && reference.height == gpu.height) {
            "FOR-250 requires same-size reference/GPU bitmaps"
        }
        val highDeltaPixels = mutableListOf<ClassifiedPixelDelta>()
        var maxDelta = 0
        var strictHighDeltaCount = 0
        for (y in 0 until reference.height) {
            for (x in 0 until reference.width) {
                val delta = pixelDelta(reference, gpu, x, y)
                maxDelta = maxOf(maxDelta, delta.maxChannelDelta)
                if (delta.maxChannelDelta > STRICT_HIGH_DELTA_THRESHOLD) {
                    strictHighDeltaCount += 1
                }
                if (delta.maxChannelDelta > HIGH_DELTA_THRESHOLD) {
                    highDeltaPixels += ClassifiedPixelDelta(
                        cellId = classifySimpleOffsetCell(x, y)?.id ?: OUTSIDE_CELLS_ID,
                        pixel = delta,
                    )
                }
            }
        }
        val topPixels = highDeltaPixels
            .sortedWith(
                compareByDescending<ClassifiedPixelDelta> { it.pixel.maxChannelDelta }
                    .thenBy { it.pixel.y }
                    .thenBy { it.pixel.x },
            )
            .take(HIGH_DELTA_TOP_N)
        val aggregates = SIMPLE_OFFSET_CELLS
            .map { cell -> buildCellAggregate(cell, highDeltaPixels.filter { it.cellId == cell.id }) }
        val outsidePixels = highDeltaPixels.filter { it.cellId == OUTSIDE_CELLS_ID }
        val outsideAggregate = SimpleOffsetResidualAggregate(
            id = OUTSIDE_CELLS_ID,
            label = "outside SimpleOffset inferred 80x80 cells",
            origin = intArrayOf(0, 0),
            bounds = intArrayOf(0, 0, reference.width, reference.height),
            case = "background or unclassified GM area",
            pixelsAboveThreshold = outsidePixels.size,
            maxChannelDelta = outsidePixels.maxOfOrNull { it.pixel.maxChannelDelta } ?: 0,
            topPixels = outsidePixels
                .sortedWith(
                    compareByDescending<ClassifiedPixelDelta> { it.pixel.maxChannelDelta }
                        .thenBy { it.pixel.y }
                        .thenBy { it.pixel.x },
                )
                .take(3),
        )
        return For250HighDeltaScanProbe(
            width = reference.width,
            height = reference.height,
            threshold = HIGH_DELTA_THRESHOLD,
            strictHighDeltaThreshold = STRICT_HIGH_DELTA_THRESHOLD,
            totalPixelsAboveThreshold = highDeltaPixels.size,
            totalPixelsAboveStrictHighDeltaThreshold = strictHighDeltaCount,
            maxChannelDelta = maxDelta,
            topPixels = topPixels,
            cellAggregates = aggregates + outsideAggregate,
        )
    }

    private fun buildFor251ColorPremulAuditProbe(reference: SkBitmap, gpu: SkBitmap): For251ColorPremulAuditProbe {
        require(reference.width == gpu.width && reference.height == gpu.height) {
            "FOR-251 requires same-size reference/GPU bitmaps"
        }
        val residualPixels = mutableListOf<ClassifiedPixelDelta>()
        var maxDelta = 0
        var alphaDeltaNonZero = 0
        for (y in 0 until reference.height) {
            for (x in 0 until reference.width) {
                val delta = pixelDelta(reference, gpu, x, y)
                maxDelta = maxOf(maxDelta, delta.maxChannelDelta)
                if (delta.maxChannelDelta > 0) {
                    if (delta.signedDelta[3] != 0) {
                        alphaDeltaNonZero += 1
                    }
                    residualPixels += ClassifiedPixelDelta(
                        cellId = classifySimpleOffsetCell(x, y)?.id ?: OUTSIDE_CELLS_ID,
                        pixel = delta,
                    )
                }
            }
        }
        val globalHistograms = signedDeltaHistograms(residualPixels.map { it.pixel })
        val dominantAggregates = DOMINANT_FOR251_CELL_IDS.map { cellId ->
            val cell = SIMPLE_OFFSET_CELLS.first { it.id == cellId }
            val pixels = residualPixels.filter { it.cellId == cellId }.map { it.pixel }
            ColorPremulCellAggregate(
                id = cell.id,
                label = cell.label,
                origin = intArrayOf(cell.originX, cell.originY),
                bounds = intArrayOf(cell.originX, cell.originY, cell.originX + CELL_EXTENT, cell.originY + CELL_EXTENT),
                case = cell.case,
                residualPixels = pixels.size,
                maxChannelDelta = pixels.maxOfOrNull { it.maxChannelDelta } ?: 0,
                alphaDeltaNonZeroPixels = pixels.count { it.signedDelta[3] != 0 },
                rgbOnlyResidualPixels = pixels.count { it.signedDelta[3] == 0 && it.maxChannelDelta > 0 },
                signedDeltaHistogram = signedDeltaHistograms(pixels),
                topColorPairs = topColorPairs(pixels, COLOR_PAIR_TOP_N),
            )
        }
        return For251ColorPremulAuditProbe(
            width = reference.width,
            height = reference.height,
            totalResidualPixels = residualPixels.size,
            maxChannelDelta = maxDelta,
            alphaDeltaNonZeroPixels = alphaDeltaNonZero,
            rgbOnlyResidualPixels = residualPixels.count {
                it.pixel.signedDelta[3] == 0 && it.pixel.maxChannelDelta > 0
            },
            signedDeltaHistogram = globalHistograms,
            topColorPairs = topColorPairs(residualPixels.map { it.pixel }, COLOR_PAIR_TOP_N),
            dominantCellAggregates = dominantAggregates,
        )
    }

    private fun buildFor252ColorReferenceBiasAuditProbe(
        simpleOffsetReference: SkBitmap,
        simpleOffsetGpu: SkBitmap,
    ): For252ColorReferenceBiasAuditProbe {
        val simpleOffsetCell = SIMPLE_OFFSET_CELLS.first { it.id == "row1-col0-no-filter" }
        val sampleResults = listOf(
            buildFor252SimpleOffsetCellSample(
                id = "simple-offsetimagefilter.row1-col0-no-filter",
                sceneId = "simple-offsetimagefilter",
                label = "SimpleOffset row1 col0 source-only cell",
                route = "webgpu.canvas.draw-rect.src-over",
                cell = simpleOffsetCell,
                reference = simpleOffsetReference,
                gpu = simpleOffsetGpu,
            ),
            buildFor252ArtifactSceneSample(
                id = "bitmap-rect-nearest.whole-scene",
                sceneId = "bitmap-rect-nearest",
                label = "bitmap-rect-nearest generated whole scene",
                route = "webgpu.image-rect.strict-nearest",
                referencePath = "reports/wgsl-pipeline/scenes/generated/artifacts/bitmap-rect-nearest/skia.png",
                gpuPath = "reports/wgsl-pipeline/scenes/generated/artifacts/bitmap-rect-nearest/gpu.png",
            ),
            buildFor252ArtifactSceneSample(
                id = "linear-gradient-rect.whole-scene",
                sceneId = "linear-gradient-rect",
                label = "linear-gradient-rect generated whole scene",
                route = "webgpu.generated.linear-gradient.rect",
                referencePath = "reports/wgsl-pipeline/scenes/generated/artifacts/linear-gradient-rect/skia.png",
                gpuPath = "reports/wgsl-pipeline/scenes/generated/artifacts/linear-gradient-rect/gpu.png",
            ),
        )
        val samples = sampleResults.map { it.sample }
        val residualPixels = sampleResults.flatMap { it.residualPixels }
        return For252ColorReferenceBiasAuditProbe(
            sampleCount = samples.size,
            samples = samples,
            totalPixels = samples.sumOf { it.totalPixels },
            totalResidualPixels = samples.sumOf { it.residualPixels },
            maxChannelDelta = samples.maxOf { it.maxChannelDelta },
            alphaDeltaNonZeroPixels = samples.sumOf { it.alphaDeltaNonZeroPixels },
            rgbOnlyResidualPixels = samples.sumOf { it.rgbOnlyResidualPixels },
            signedDeltaHistogram = signedDeltaHistograms(residualPixels),
            samplesWithRgbByteResidual = samples
                .filter { it.residualPixels > 0 && it.maxChannelDelta == 1 && it.alphaDeltaNonZeroPixels == 0 }
                .map { it.id },
            samplesWithoutResidual = samples.filter { it.residualPixels == 0 }.map { it.id },
        )
    }

    private fun buildFor253BitmapSourceRoundingAuditProbe(
        simpleOffsetReference: SkBitmap,
        simpleOffsetGpu: SkBitmap,
    ): For253BitmapSourceRoundingAuditProbe {
        val sourceColorCell = SIMPLE_OFFSET_CELLS.first { it.id == "row1-col0-no-filter" }
        val sourceColor = buildFor253SimpleOffsetCellCase(
            id = "source-color-constant.simple-offset-row1-col0",
            sceneId = "simple-offsetimagefilter",
            label = "source-color constant through SimpleOffset row1 col0",
            route = "webgpu.canvas.draw-rect.src-over",
            colorProductionStage = "source-color constant path before any bitmap sampling",
            stageIsolation = "appears-before-bitmap-sampling",
            cell = sourceColorCell,
            reference = simpleOffsetReference,
            gpu = simpleOffsetGpu,
        )
        val bitmapNearest = buildFor253ArtifactCase(
            id = "bitmap-nearest.generated-whole-scene",
            sceneId = "bitmap-rect-nearest",
            label = "bitmap nearest strict sampling generated scene",
            route = "webgpu.image-rect.strict-nearest",
            colorProductionStage = "bitmap texture sample and source texel normalization",
            stageIsolation = "appears-during-bitmap-nearest-path",
            referencePath = "reports/wgsl-pipeline/scenes/generated/artifacts/bitmap-rect-nearest/skia.png",
            gpuPath = "reports/wgsl-pipeline/scenes/generated/artifacts/bitmap-rect-nearest/gpu.png",
        )
        val linearGradient = buildFor253ArtifactCase(
            id = "linear-gradient.generated-whole-scene",
            sceneId = "linear-gradient-rect",
            label = "linear gradient generated WGSL control scene",
            route = "webgpu.generated.linear-gradient.rect",
            colorProductionStage = "generated gradient arithmetic plus final render-target store control",
            stageIsolation = "exact-final-pack-store-control",
            referencePath = "reports/wgsl-pipeline/scenes/generated/artifacts/linear-gradient-rect/skia.png",
            gpuPath = "reports/wgsl-pipeline/scenes/generated/artifacts/linear-gradient-rect/gpu.png",
        )
        val cases = listOf(sourceColor, bitmapNearest, linearGradient)
        val residualCases = cases.filter { it.residualPixels > 0 }
        val exactCases = cases.filter { it.residualPixels == 0 }
        val residualPixels = cases.flatMap { it.residualPixelDeltas }
        return For253BitmapSourceRoundingAuditProbe(
            microCases = cases,
            totalPixels = cases.sumOf { it.totalPixels },
            totalResidualPixels = cases.sumOf { it.residualPixels },
            maxChannelDelta = cases.maxOf { it.maxChannelDelta },
            alphaDeltaNonZeroPixels = cases.sumOf { it.alphaDeltaNonZeroPixels },
            rgbOnlyResidualPixels = cases.sumOf { it.rgbOnlyResidualPixels },
            signedDeltaHistogram = signedDeltaHistograms(residualPixels),
            residualCaseIds = residualCases.map { it.id },
            exactControlCaseIds = exactCases.map { it.id },
            appearsBeforeBitmapSampling = sourceColor.residualPixels > 0 &&
                sourceColor.alphaDeltaNonZeroPixels == 0 &&
                sourceColor.maxChannelDelta == 1,
            appearsDuringBitmapNearestPath = bitmapNearest.residualPixels > 0 &&
                bitmapNearest.alphaDeltaNonZeroPixels == 0 &&
                bitmapNearest.maxChannelDelta == 1,
            onlyFinalPackOrStore = linearGradient.residualPixels > 0,
        )
    }

    private fun buildFor254SourceTexelRoundingAuditProbe(
        simpleOffsetReference: SkBitmap,
        simpleOffsetGpu: SkBitmap,
    ): For254SourceTexelRoundingAuditProbe {
        val sourceColorCell = SIMPLE_OFFSET_CELLS.first { it.id == "row1-col0-no-filter" }
        val sourceUniform = buildFor254SimpleOffsetCellCase(
            id = "legacy-source-color-uniform.simple-offset-row1-col0",
            pathKind = "legacy-source-color-uniform",
            sceneId = "simple-offsetimagefilter",
            label = "legacy source-color uniform path through SimpleOffset row1 col0",
            route = "webgpu.canvas.draw-rect.src-over",
            inputObservation = For254InputObservation(
                source = "SimpleOffsetImageFilterGM paint colors",
                packedArgb = intArrayOf(0x66, 0xFF, 0x00, 0x00),
                packedRgba = intArrayOf(0xFF, 0x00, 0x00, 0x66),
                normalizedRgba = normalizedRgba(argb = 0x66FF0000),
                premulRgba = premulNormalizedRgba(argb = 0x66FF0000),
                observableStatus = "host-side normalization formula is visible in SkWebGpuDevice drawRect; raw uniform buffer bytes are not captured by this test",
            ),
            inputStageConclusion = "residual appears after legacy source-color normalization/uniform consumption; host-side color bytes are deducible, uniform write bytes are not directly observed",
            cell = sourceColorCell,
            reference = simpleOffsetReference,
            gpu = simpleOffsetGpu,
        )
        val generatedSolid = buildFor254ArtifactCase(
            id = "generated-solid-control.solid-rect",
            pathKind = "generated-solid-control",
            sceneId = "solid-rect",
            label = "generated solid rect exact control",
            route = "webgpu.generated.solid-rect.src-over",
            inputObservation = For254InputObservation(
                source = "SolidRectSceneCaptureTest FILL",
                packedArgb = intArrayOf(0xFF, 23, 33, 28),
                packedRgba = intArrayOf(23, 33, 28, 0xFF),
                normalizedRgba = normalizedRgba(argb = 0xFF171F1C.toInt()),
                premulRgba = premulNormalizedRgba(argb = 0xFF171F1C.toInt()),
                observableStatus = "source color is visible in test fixture and output is byte-exact; generated uniform buffer bytes are not captured here",
            ),
            inputStageConclusion = "generated solid path is byte-exact, so source-color uniforms are not universally biased",
            referencePath = "reports/wgsl-pipeline/scenes/artifacts/solid-rect/skia.png",
            gpuPath = "reports/wgsl-pipeline/scenes/artifacts/solid-rect/gpu.png",
            representativeX = 2,
            representativeY = 1,
        )
        val generatedGradient = buildFor254ArtifactCase(
            id = "generated-gradient-control.linear-gradient-rect",
            pathKind = "generated-gradient-control",
            sceneId = "linear-gradient-rect",
            label = "generated linear gradient exact control",
            route = "webgpu.generated.linear-gradient.rect",
            inputObservation = For254InputObservation(
                source = "generated linear-gradient scene oracle",
                packedArgb = intArrayOf(),
                packedRgba = intArrayOf(),
                normalizedRgba = floatArrayOf(),
                premulRgba = floatArrayOf(),
                observableStatus = "gradient stop uniforms are not dumped by this test; exact output constrains final store and generated arithmetic",
            ),
            inputStageConclusion = "generated gradient path is byte-exact, ruling out a global final pack/store or generated arithmetic bias",
            referencePath = "reports/wgsl-pipeline/scenes/generated/artifacts/linear-gradient-rect/skia.png",
            gpuPath = "reports/wgsl-pipeline/scenes/generated/artifacts/linear-gradient-rect/gpu.png",
            representativeX = 32,
            representativeY = 32,
        )
        val bitmapTexel = buildFor254ArtifactCase(
            id = "bitmap-texel-upload-sample.bitmap-rect-nearest",
            pathKind = "bitmap-texel-upload-sample",
            sceneId = "bitmap-rect-nearest",
            label = "bitmap texel upload/sample path",
            route = "webgpu.image-rect.strict-nearest",
            inputObservation = For254InputObservation(
                source = "bitmap-rect-nearest skia/gpu artifacts",
                packedArgb = intArrayOf(),
                packedRgba = intArrayOf(149, 193, 207, 0xFF),
                normalizedRgba = normalizedRgba(rgba = intArrayOf(149, 193, 207, 0xFF)),
                premulRgba = premulNormalizedRgba(rgba = intArrayOf(149, 193, 207, 0xFF)),
                observableStatus = "representative oracle texel/output color is visible from artifacts; raw texture-upload bytes and sampled shader value are not captured here",
            ),
            inputStageConclusion = "bitmap path has a 1-byte RGB tail after upload/sample, but the current evidence cannot distinguish upload normalization from sampler/readback arithmetic",
            referencePath = "reports/wgsl-pipeline/scenes/generated/artifacts/bitmap-rect-nearest/skia.png",
            gpuPath = "reports/wgsl-pipeline/scenes/generated/artifacts/bitmap-rect-nearest/gpu.png",
            representativeX = null,
            representativeY = null,
        )
        val cases = listOf(sourceUniform, generatedSolid, generatedGradient, bitmapTexel)
        val residualCases = cases.filter { it.residualPixels > 0 }
        val exactControls = cases.filter { it.residualPixels == 0 }.map { it.pathKind }
        val residualPixels = cases.flatMap { it.residualPixelDeltas }
        return For254SourceTexelRoundingAuditProbe(
            cases = cases,
            totalPixels = cases.sumOf { it.totalPixels },
            totalResidualPixels = cases.sumOf { it.residualPixels },
            maxChannelDelta = cases.maxOf { it.maxChannelDelta },
            alphaDeltaNonZeroPixels = cases.sumOf { it.alphaDeltaNonZeroPixels },
            rgbOnlyResidualPixels = cases.sumOf { it.rgbOnlyResidualPixels },
            signedDeltaHistogram = signedDeltaHistograms(residualPixels),
            residualCaseIds = residualCases.map { it.id },
            exactControlKinds = exactControls,
            legacySourceUniformHasResidual = sourceUniform.residualPixels > 0 &&
                sourceUniform.maxChannelDelta == 1 &&
                sourceUniform.alphaDeltaNonZeroPixels == 0,
            bitmapTexelPathHasResidual = bitmapTexel.residualPixels > 0 &&
                bitmapTexel.maxChannelDelta == 1 &&
                bitmapTexel.alphaDeltaNonZeroPixels == 0,
            generatedControlsExact = generatedSolid.residualPixels == 0 && generatedGradient.residualPixels == 0,
        )
    }

    private fun buildFor254SimpleOffsetCellCase(
        id: String,
        pathKind: String,
        sceneId: String,
        label: String,
        route: String,
        inputObservation: For254InputObservation,
        inputStageConclusion: String,
        cell: SimpleOffsetCell,
        reference: SkBitmap,
        gpu: SkBitmap,
    ): For254PathCase {
        val pixels = mutableListOf<PixelDelta>()
        for (y in cell.originY until cell.originY + CELL_EXTENT) {
            for (x in cell.originX until cell.originX + CELL_EXTENT) {
                pixels += pixelDelta(reference, gpu, x, y)
            }
        }
        return buildFor254PathCase(
            id = id,
            pathKind = pathKind,
            sceneId = sceneId,
            label = label,
            sourceKind = "skia-upstream-reference-vs-live-webgpu",
            route = route,
            referenceSource = "original-888/simple-offsetimagefilter.png",
            inputObservation = inputObservation,
            inputStageConclusion = inputStageConclusion,
            bounds = intArrayOf(cell.originX, cell.originY, cell.originX + CELL_EXTENT, cell.originY + CELL_EXTENT),
            totalPixels = CELL_EXTENT * CELL_EXTENT,
            pixels = pixels,
            representative = null,
        )
    }

    private fun buildFor254ArtifactCase(
        id: String,
        pathKind: String,
        sceneId: String,
        label: String,
        route: String,
        inputObservation: For254InputObservation,
        inputStageConclusion: String,
        referencePath: String,
        gpuPath: String,
        representativeX: Int?,
        representativeY: Int?,
    ): For254PathCase {
        val reference = readEvidencePng(referencePath)
        val gpu = readEvidencePng(gpuPath)
        require(reference.width == gpu.width && reference.height == gpu.height) {
            "FOR-254 requires same-size generated artifact bitmaps for $sceneId"
        }
        val pixels = mutableListOf<PixelDelta>()
        for (y in 0 until reference.height) {
            for (x in 0 until reference.width) {
                pixels += pixelDelta(reference, gpu, x, y)
            }
        }
        val representative = if (representativeX != null && representativeY != null) {
            pixelDelta(reference, gpu, representativeX, representativeY)
        } else {
            null
        }
        return buildFor254PathCase(
            id = id,
            pathKind = pathKind,
            sceneId = sceneId,
            label = label,
            sourceKind = "artifact-reference-oracle-vs-gpu",
            route = route,
            referenceSource = referencePath,
            inputObservation = inputObservation,
            inputStageConclusion = inputStageConclusion,
            bounds = intArrayOf(0, 0, reference.width, reference.height),
            totalPixels = reference.width * reference.height,
            pixels = pixels,
            representative = representative,
        )
    }

    private fun buildFor254PathCase(
        id: String,
        pathKind: String,
        sceneId: String,
        label: String,
        sourceKind: String,
        route: String,
        referenceSource: String,
        inputObservation: For254InputObservation,
        inputStageConclusion: String,
        bounds: IntArray,
        totalPixels: Int,
        pixels: List<PixelDelta>,
        representative: PixelDelta?,
    ): For254PathCase {
        val residualPixels = pixels.filter { it.maxChannelDelta > 0 }
        val topPairs = topColorPairs(residualPixels, COLOR_PAIR_TOP_N)
        val representativePixel = representative
            ?: residualPixels.firstOrNull { pixel ->
                topPairs.firstOrNull()?.let { pair ->
                    pixel.reference.contentEquals(pair.reference) && pixel.gpu.contentEquals(pair.gpu)
                } ?: false
            }
            ?: pixels.first()
        return For254PathCase(
            id = id,
            pathKind = pathKind,
            sceneId = sceneId,
            label = label,
            sourceKind = sourceKind,
            route = route,
            referenceSource = referenceSource,
            inputObservation = inputObservation,
            inputStageConclusion = inputStageConclusion,
            bounds = bounds,
            totalPixels = totalPixels,
            residualPixels = residualPixels.size,
            maxChannelDelta = pixels.maxOfOrNull { it.maxChannelDelta } ?: 0,
            alphaDeltaNonZeroPixels = residualPixels.count { it.signedDelta[3] != 0 },
            rgbOnlyResidualPixels = residualPixels.count { it.signedDelta[3] == 0 },
            representativeOutput = representativePixel,
            signedDeltaHistogram = signedDeltaHistograms(residualPixels),
            topColorPairs = topPairs,
            residualPixelDeltas = residualPixels,
        )
    }

    private fun normalizedRgba(argb: Int): FloatArray =
        floatArrayOf(
            ((argb ushr 16) and 0xFF) / 255f,
            ((argb ushr 8) and 0xFF) / 255f,
            (argb and 0xFF) / 255f,
            ((argb ushr 24) and 0xFF) / 255f,
        )

    private fun premulNormalizedRgba(argb: Int): FloatArray {
        val rgba = normalizedRgba(argb)
        return floatArrayOf(rgba[0] * rgba[3], rgba[1] * rgba[3], rgba[2] * rgba[3], rgba[3])
    }

    private fun normalizedRgba(rgba: IntArray): FloatArray =
        FloatArray(4) { channel -> rgba[channel] / 255f }

    private fun premulNormalizedRgba(rgba: IntArray): FloatArray {
        val normalized = normalizedRgba(rgba)
        return floatArrayOf(
            normalized[0] * normalized[3],
            normalized[1] * normalized[3],
            normalized[2] * normalized[3],
            normalized[3],
        )
    }

    private fun buildFor253SimpleOffsetCellCase(
        id: String,
        sceneId: String,
        label: String,
        route: String,
        colorProductionStage: String,
        stageIsolation: String,
        cell: SimpleOffsetCell,
        reference: SkBitmap,
        gpu: SkBitmap,
    ): For253MicroCase {
        val pixels = mutableListOf<PixelDelta>()
        for (y in cell.originY until cell.originY + CELL_EXTENT) {
            for (x in cell.originX until cell.originX + CELL_EXTENT) {
                pixels += pixelDelta(reference, gpu, x, y)
            }
        }
        return buildFor253MicroCase(
            id = id,
            sceneId = sceneId,
            label = label,
            sourceKind = "skia-upstream-reference-vs-live-webgpu",
            route = route,
            colorProductionStage = colorProductionStage,
            stageIsolation = stageIsolation,
            bounds = intArrayOf(cell.originX, cell.originY, cell.originX + CELL_EXTENT, cell.originY + CELL_EXTENT),
            totalPixels = CELL_EXTENT * CELL_EXTENT,
            pixels = pixels,
        )
    }

    private fun buildFor253ArtifactCase(
        id: String,
        sceneId: String,
        label: String,
        route: String,
        colorProductionStage: String,
        stageIsolation: String,
        referencePath: String,
        gpuPath: String,
    ): For253MicroCase {
        val reference = readEvidencePng(referencePath)
        val gpu = readEvidencePng(gpuPath)
        require(reference.width == gpu.width && reference.height == gpu.height) {
            "FOR-253 requires same-size generated artifact bitmaps for $sceneId"
        }
        val pixels = mutableListOf<PixelDelta>()
        for (y in 0 until reference.height) {
            for (x in 0 until reference.width) {
                pixels += pixelDelta(reference, gpu, x, y)
            }
        }
        return buildFor253MicroCase(
            id = id,
            sceneId = sceneId,
            label = label,
            sourceKind = "generated-scene-artifact-reference-vs-gpu",
            route = route,
            colorProductionStage = colorProductionStage,
            stageIsolation = stageIsolation,
            bounds = intArrayOf(0, 0, reference.width, reference.height),
            totalPixels = reference.width * reference.height,
            pixels = pixels,
        )
    }

    private fun buildFor253MicroCase(
        id: String,
        sceneId: String,
        label: String,
        sourceKind: String,
        route: String,
        colorProductionStage: String,
        stageIsolation: String,
        bounds: IntArray,
        totalPixels: Int,
        pixels: List<PixelDelta>,
    ): For253MicroCase {
        val residualPixels = pixels.filter { it.maxChannelDelta > 0 }
        val topPairs = topColorPairs(residualPixels, COLOR_PAIR_TOP_N)
        return For253MicroCase(
            id = id,
            sceneId = sceneId,
            label = label,
            sourceKind = sourceKind,
            route = route,
            colorProductionStage = colorProductionStage,
            stageIsolation = stageIsolation,
            bounds = bounds,
            totalPixels = totalPixels,
            residualPixels = residualPixels.size,
            maxChannelDelta = pixels.maxOfOrNull { it.maxChannelDelta } ?: 0,
            alphaDeltaNonZeroPixels = residualPixels.count { it.signedDelta[3] != 0 },
            rgbOnlyResidualPixels = residualPixels.count { it.signedDelta[3] == 0 },
            signedDeltaHistogram = signedDeltaHistograms(residualPixels),
            topColorPairs = topPairs,
            representativeReferenceRgba = topPairs.firstOrNull()?.reference ?: intArrayOf(),
            representativeGpuRgba = topPairs.firstOrNull()?.gpu ?: intArrayOf(),
            representativeSignedDeltaRgba = topPairs.firstOrNull()?.signedDelta ?: intArrayOf(),
            residualPixelDeltas = residualPixels,
        )
    }

    private fun buildFor252SimpleOffsetCellSample(
        id: String,
        sceneId: String,
        label: String,
        route: String,
        cell: SimpleOffsetCell,
        reference: SkBitmap,
        gpu: SkBitmap,
    ): For252SampleBuildResult {
        val pixels = mutableListOf<PixelDelta>()
        for (y in cell.originY until cell.originY + CELL_EXTENT) {
            for (x in cell.originX until cell.originX + CELL_EXTENT) {
                pixels += pixelDelta(reference, gpu, x, y)
            }
        }
        return buildFor252Sample(
            id = id,
            sceneId = sceneId,
            label = label,
            sourceKind = "skia-upstream-reference-vs-live-webgpu",
            route = route,
            bounds = intArrayOf(cell.originX, cell.originY, cell.originX + CELL_EXTENT, cell.originY + CELL_EXTENT),
            totalPixels = CELL_EXTENT * CELL_EXTENT,
            pixels = pixels,
        )
    }

    private fun buildFor252ArtifactSceneSample(
        id: String,
        sceneId: String,
        label: String,
        route: String,
        referencePath: String,
        gpuPath: String,
    ): For252SampleBuildResult {
        val reference = readEvidencePng(referencePath)
        val gpu = readEvidencePng(gpuPath)
        require(reference.width == gpu.width && reference.height == gpu.height) {
            "FOR-252 requires same-size generated artifact bitmaps for $sceneId"
        }
        val pixels = mutableListOf<PixelDelta>()
        for (y in 0 until reference.height) {
            for (x in 0 until reference.width) {
                pixels += pixelDelta(reference, gpu, x, y)
            }
        }
        return buildFor252Sample(
            id = id,
            sceneId = sceneId,
            label = label,
            sourceKind = "generated-scene-artifact-reference-vs-gpu",
            route = route,
            bounds = intArrayOf(0, 0, reference.width, reference.height),
            totalPixels = reference.width * reference.height,
            pixels = pixels,
        )
    }

    private fun buildFor252Sample(
        id: String,
        sceneId: String,
        label: String,
        sourceKind: String,
        route: String,
        bounds: IntArray,
        totalPixels: Int,
        pixels: List<PixelDelta>,
    ): For252SampleBuildResult {
        val residualPixels = pixels.filter { it.maxChannelDelta > 0 }
        return For252SampleBuildResult(
            sample = For252NonImageFilterSample(
                id = id,
                sceneId = sceneId,
                label = label,
                sourceKind = sourceKind,
                route = route,
                bounds = bounds,
                imageFilterInPath = false,
                totalPixels = totalPixels,
                residualPixels = residualPixels.size,
                maxChannelDelta = pixels.maxOfOrNull { it.maxChannelDelta } ?: 0,
                alphaDeltaNonZeroPixels = residualPixels.count { it.signedDelta[3] != 0 },
                rgbOnlyResidualPixels = residualPixels.count { it.signedDelta[3] == 0 },
                signedDeltaHistogram = signedDeltaHistograms(residualPixels),
                topColorPairs = topColorPairs(residualPixels, COLOR_PAIR_TOP_N),
            ),
            residualPixels = residualPixels,
        )
    }

    private fun buildCellAggregate(
        cell: SimpleOffsetCell,
        pixels: List<ClassifiedPixelDelta>,
    ): SimpleOffsetResidualAggregate =
        SimpleOffsetResidualAggregate(
            id = cell.id,
            label = cell.label,
            origin = intArrayOf(cell.originX, cell.originY),
            bounds = intArrayOf(cell.originX, cell.originY, cell.originX + CELL_EXTENT, cell.originY + CELL_EXTENT),
            case = cell.case,
            pixelsAboveThreshold = pixels.size,
            maxChannelDelta = pixels.maxOfOrNull { it.pixel.maxChannelDelta } ?: 0,
            topPixels = pixels
                .sortedWith(
                    compareByDescending<ClassifiedPixelDelta> { it.pixel.maxChannelDelta }
                        .thenBy { it.pixel.y }
                        .thenBy { it.pixel.x },
                )
                .take(3),
        )

    private fun classifySimpleOffsetCell(x: Int, y: Int): SimpleOffsetCell? =
        SIMPLE_OFFSET_CELLS.firstOrNull { cell ->
            x >= cell.originX &&
                x < cell.originX + CELL_EXTENT &&
                y >= cell.originY &&
                y < cell.originY + CELL_EXTENT
        }

    private fun buildFor249ResidualProbe(reference: SkBitmap, gpu: SkBitmap): For249ResidualProbe {
        require(reference.width == gpu.width && reference.height == gpu.height) {
            "FOR-249 requires same-size reference/GPU bitmaps"
        }
        val window = mutableListOf<PixelDelta>()
        for (y in TARGET_Y - WINDOW_RADIUS..TARGET_Y + WINDOW_RADIUS) {
            for (x in TARGET_X - WINDOW_RADIUS..TARGET_X + WINDOW_RADIUS) {
                window += pixelDelta(reference, gpu, x, y)
            }
        }
        val samples = listOf(
            NamedPixelDelta("cellOrigin", pixelDelta(reference, gpu, x = 340, y = 120)),
            NamedPixelDelta("cropStartInside", pixelDelta(reference, gpu, x = 380, y = 120)),
            NamedPixelDelta("targetFragment", pixelDelta(reference, gpu, x = TARGET_X, y = TARGET_Y)),
            NamedPixelDelta("cropEndInside", pixelDelta(reference, gpu, x = 399, y = 159)),
        )
        return For249ResidualProbe(
            target = pixelDelta(reference, gpu, x = TARGET_X, y = TARGET_Y),
            window = window,
            cellSamples = samples,
        )
    }

    private fun pixelDelta(reference: SkBitmap, gpu: SkBitmap, x: Int, y: Int): PixelDelta {
        val referenceRgba = rgbaAt(reference, x, y)
        val gpuRgba = rgbaAt(gpu, x, y)
        val delta = IntArray(4) { i -> kotlin.math.abs(referenceRgba[i] - gpuRgba[i]) }
        return PixelDelta(
            x = x,
            y = y,
            reference = referenceRgba,
            gpu = gpuRgba,
            delta = delta,
        )
    }

    private fun pixelDelta(reference: BufferedImage, gpu: BufferedImage, x: Int, y: Int): PixelDelta {
        val referenceRgba = rgbaAt(reference, x, y)
        val gpuRgba = rgbaAt(gpu, x, y)
        val delta = IntArray(4) { i -> kotlin.math.abs(referenceRgba[i] - gpuRgba[i]) }
        return PixelDelta(
            x = x,
            y = y,
            reference = referenceRgba,
            gpu = gpuRgba,
            delta = delta,
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

    private fun readEvidencePng(path: String): BufferedImage {
        val file = repoFile(path)
        require(file.isFile) { "FOR-252 missing evidence PNG: $path" }
        return ImageIO.read(file) ?: error("FOR-252 failed to read evidence PNG: $path")
    }

    private fun signedDeltaHistograms(pixels: List<PixelDelta>): List<Map<Int, Int>> =
        (0 until 4).map { channel ->
            pixels
                .groupingBy { it.signedDelta[channel] }
                .eachCount()
                .toSortedMap()
        }

    private fun topColorPairs(pixels: List<PixelDelta>, limit: Int): List<ColorPairGroup> {
        val groups = linkedMapOf<String, ColorPairAccumulator>()
        for (pixel in pixels) {
            val key = "${jsonArray(pixel.reference)}->${jsonArray(pixel.gpu)}"
            val accumulator = groups.getOrPut(key) {
                ColorPairAccumulator(
                    reference = pixel.reference,
                    gpu = pixel.gpu,
                    signedDelta = pixel.signedDelta,
                )
            }
            accumulator.count += 1
        }
        return groups.values
            .map {
                ColorPairGroup(
                    reference = it.reference,
                    gpu = it.gpu,
                    signedDelta = it.signedDelta,
                    count = it.count,
                )
            }
            .sortedWith(
                compareByDescending<ColorPairGroup> { it.count }
                    .thenBy { it.reference.joinToString(",") }
                    .thenBy { it.gpu.joinToString(",") },
            )
            .take(limit)
    }

    private fun writeFor247ScratchProbeJson(sourceLocal: IntArray, scratch: IntArray) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/generated/artifacts/crop-image-filter-nonnull-prepass",
        ).apply { mkdirs() }
        File(dir, "scratch-probe-for247.json").writeText(
            """
            {
              "backend": "WebGPU",
              "sceneId": "crop-image-filter-nonnull-prepass",
              "linear": "FOR-247",
              "probe": "crop-offset-pre-final-crop-scratch",
              "targetCell": "crop == clip == dst",
              "case": "Crop(kDecal, rect=(40,0,80,40), input=Offset(40,0,input=null))",
              "origin": [340, 120],
              "layerExtent": [80, 40],
              "offset": [40, 0],
              "sourceLocal": {
                "xy": [5, 5],
                "rgba": ${jsonArray(sourceLocal)}
              },
              "scratchPixel": {
                "xy": [45, 5],
                "rgba": ${jsonArray(scratch)}
              },
              "matchesSourceLocal": ${sourceLocal.contentEquals(scratch)},
              "observationMethod": "diagnostic WebGPU 1x1 kSrc copies before final Crop: sourceLocal(5,5) -> output(0,0), scratchPixel(45,5) -> output(1,0)",
              "selectedRoute": "webgpu.image-filter.crop-nonnull-offset-prepass.final-crop-composite",
              "fallbackReason": "none",
              "supportDecision": "KEEP_DIAGNOSTIC"
            }
            """.trimIndent() + "\n",
        )
    }

    private fun writeFor248FinalCropCompositeProbeJson(
        directScratch: IntArray,
        finalCropComposite: IntArray,
        finalFragment: IntArray,
    ) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/generated/artifacts/crop-image-filter-nonnull-prepass",
        ).apply { mkdirs() }
        val finalCompositeReadsScratch =
            directScratch.copyOfRange(0, 3).contentEquals(finalCropComposite.copyOfRange(0, 3)) &&
                finalCropComposite[3] == 102
        val finalFragmentWhite = finalFragment.contentEquals(intArrayOf(255, 255, 255, 255))
        File(dir, "final-crop-composite-probe-for248.json").writeText(
            """
            {
              "backend": "WebGPU",
              "sceneId": "crop-image-filter-nonnull-prepass",
              "linear": "FOR-248",
              "probe": "crop-offset-final-crop-composite",
              "targetCell": "crop == clip == dst",
              "case": "Crop(kDecal, rect=(40,0,80,40), input=Offset(40,0,input=null))",
              "origin": [340, 120],
              "layerExtent": [80, 40],
              "offset": [40, 0],
              "cropRect": [40, 0, 80, 40],
              "tileMode": "kDecal",
              "finalFragment": {
                "xy": [385, 125],
                "dstOrigin": [340, 120],
                "preCropLayerPixel": [45, 5],
                "postCropScratchPixel": [45, 5],
                "insideFinalScissor": true,
                "insideCropRect": true,
                "decalTransparentAfterCropRemap": false,
                "rgba": ${jsonArray(finalFragment)}
              },
              "scratchPixel": {
                "xy": [45, 5],
                "sentinel": [2, 0],
                "rgba": ${jsonArray(directScratch)}
              },
              "finalCropCompositeShaderSample": {
                "sentinel": [3, 0],
                "forcedDstOrigin": [-42, -5],
                "preCropLayerPixel": [45, 5],
                "postCropScratchPixel": [45, 5],
                "rgba": ${jsonArray(finalCropComposite)}
              },
              "finalCompositeReadsScratchPixel45x5": $finalCompositeReadsScratch,
              "finalFragmentObservedWhite": $finalFragmentWhite,
              "observationMethod": "diagnostic WebGPU 1x1 kSrc copies after child materialization: scratchPixel(45,5) -> output(2,0), final Crop payload remap of layerPixel(45,5) -> output(3,0); normal final GM fragment read from output(385,125)",
              "selectedRoute": "webgpu.image-filter.crop-nonnull-offset-prepass.final-crop-composite",
              "fallbackReason": "none",
              "supportDecision": "KEEP_DIAGNOSTIC"
            }
            """.trimIndent() + "\n",
        )
    }

    private fun writeFor249ReferenceGpuResidualProbeJson(probe: For249ResidualProbe) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/generated/artifacts/crop-image-filter-nonnull-prepass",
        ).apply { mkdirs() }
        File(dir, "reference-gpu-residual-window-for249.json").writeText(
            """
            {
              "backend": "WebGPU",
              "referenceBackend": "skia-upstream",
              "sceneId": "crop-image-filter-nonnull-prepass",
              "linear": "FOR-249",
              "probe": "reference-gpu-residual-window",
              "targetCell": "crop == clip == dst",
              "case": "Crop(kDecal, rect=(40,0,80,40), input=Offset(40,0,input=null))",
              "origin": [340, 120],
              "layerExtent": [80, 40],
              "offset": [40, 0],
              "cropRect": [40, 0, 80, 40],
              "tileMode": "kDecal",
              "targetFragment": ${probe.target.toJson(indent = "              ")},
              "window": {
                "center": [385, 125],
                "radius": $WINDOW_RADIUS,
                "boundsInclusive": [${TARGET_X - WINDOW_RADIUS}, ${TARGET_Y - WINDOW_RADIUS}, ${TARGET_X + WINDOW_RADIUS}, ${TARGET_Y + WINDOW_RADIUS}],
                "maxChannelDelta": ${probe.windowMaxChannelDelta},
                "allPixelsWithinTolerance8": ${probe.window.all { it.maxChannelDelta <= 8 }},
                "pixels": [
            ${probe.window.joinToString(",\n") { it.toJson(indent = "                  ") }}
                ]
              },
              "cellSamples": [
            ${probe.cellSamples.joinToString(",\n") { it.toJson(indent = "                ") }}
              ],
              "maxObservedDelta": ${probe.maxObservedDelta},
              "interpretation": "The normal WebGPU fragment at (385,125) matches the Skia reference within 1 byte; the remaining scene-level residual is not explained by scratch materialization or final Crop UV remap and is outside this target 5x5 window.",
              "observationMethod": "test-side Skia reference PNG and normal WebGPU render readback; no renderer diagnostic property, no CPU/readback fallback in the render path",
              "selectedRoute": "webgpu.image-filter.crop-nonnull-offset-prepass.final-crop-composite",
              "fallbackReason": "none",
              "supportDecision": "KEEP_DIAGNOSTIC"
            }
            """.trimIndent() + "\n",
        )
    }

    private fun writeFor250HighDeltaScanJson(probe: For250HighDeltaScanProbe) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/generated/artifacts/crop-image-filter-nonnull-prepass",
        ).apply { mkdirs() }
        File(dir, "high-delta-scan-for250.json").writeText(
            """
            {
              "backend": "WebGPU",
              "referenceBackend": "skia-upstream",
              "sceneId": "crop-image-filter-nonnull-prepass",
              "linear": "FOR-250",
              "probe": "high-delta-scene-scan",
              "threshold": {
                "metric": "max absolute RGBA channel delta",
                "operator": ">",
                "value": ${probe.threshold}
              },
              "strictHighDeltaThreshold": {
                "metric": "max absolute RGBA channel delta",
                "operator": ">",
                "value": ${probe.strictHighDeltaThreshold},
                "totalPixelsAboveThreshold": ${probe.totalPixelsAboveStrictHighDeltaThreshold}
              },
              "imageSize": [${probe.width}, ${probe.height}],
              "totalPixelsAboveThreshold": ${probe.totalPixelsAboveThreshold},
              "maxChannelDelta": ${probe.maxChannelDelta},
              "topPixelLimit": $HIGH_DELTA_TOP_N,
              "topPixels": [
            ${probe.topPixels.joinToString(",\n") { it.toJson(indent = "                ") }}
              ],
              "simpleOffsetCellClassifier": {
                "source": "SimpleOffsetImageFilterGM explicit translate grid",
                "cellExtent": [$CELL_EXTENT, $CELL_EXTENT],
                "fallbackCell": "$OUTSIDE_CELLS_ID"
              },
              "cellAggregates": [
            ${probe.cellAggregates.joinToString(",\n") { it.toJson(indent = "                ") }}
              ],
              "interpretation": "The scene-level residual has no >8 high-delta pixels; the remaining score loss is a diffuse byte-level tail. FOR-250 records the non-identical pixel distribution by inferred SimpleOffset cell and keeps the renderer unchanged until a bounded error is proven.",
              "observationMethod": "test-side Skia reference PNG and normal WebGPU render readback; no renderer diagnostic property, no CPU/readback fallback in the render path",
              "selectedRoute": "webgpu.image-filter.crop-nonnull-offset-prepass.final-crop-composite",
              "fallbackReason": "none",
              "supportDecision": "KEEP_DIAGNOSTIC"
            }
            """.trimIndent() + "\n",
        )
    }

    private fun writeFor251ColorPremulAuditJson(probe: For251ColorPremulAuditProbe) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/generated/artifacts/crop-image-filter-nonnull-prepass",
        ).apply { mkdirs() }
        File(dir, "color-premul-audit-for251.json").writeText(
            """
            {
              "backend": "WebGPU",
              "referenceBackend": "skia-upstream",
              "sceneId": "crop-image-filter-nonnull-prepass",
              "linear": "FOR-251",
              "probe": "color-premul-byte-residual-audit",
              "imageSize": [${probe.width}, ${probe.height}],
              "deltaDefinition": "signed channel delta is GPU minus reference",
              "totalResidualPixels": ${probe.totalResidualPixels},
              "maxChannelDelta": ${probe.maxChannelDelta},
              "alphaDeltaNonZeroPixels": ${probe.alphaDeltaNonZeroPixels},
              "rgbOnlyResidualPixels": ${probe.rgbOnlyResidualPixels},
              "signedDeltaHistogram": ${signedDeltaHistogramToJson(probe.signedDeltaHistogram, indent = "              ")},
              "topColorPairLimit": $COLOR_PAIR_TOP_N,
              "topColorPairs": [
            ${probe.topColorPairs.joinToString(",\n") { it.toJson(indent = "                ") }}
              ],
              "dominantCellIds": [
            ${DOMINANT_FOR251_CELL_IDS.joinToString(",\n") { "                \"$it\"" }}
              ],
              "dominantCellAggregates": [
            ${probe.dominantCellAggregates.joinToString(",\n") { it.toJson(indent = "                ") }}
              ],
              "interpretation": "The SimpleOffset residual is an RGB-only byte-rounding tail: every non-identical pixel has maxChannelDelta=1 and alphaDeltaNonZeroPixels=0. The dominant cells include an unfiltered source-only cell, so the evidence does not justify a bounded Crop renderer correction or a threshold change.",
              "observationMethod": "test-side Skia reference PNG and normal WebGPU render readback; no renderer diagnostic property, no CPU/readback fallback in the render path",
              "selectedRoute": "webgpu.image-filter.crop-nonnull-offset-prepass.final-crop-composite",
              "fallbackReason": "none",
              "supportDecision": "KEEP_DIAGNOSTIC"
            }
            """.trimIndent() + "\n",
        )
    }

    private fun writeFor252ColorReferenceBiasAuditJson(probe: For252ColorReferenceBiasAuditProbe) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/generated/artifacts/color-reference-bias-audit-for252",
        ).apply { mkdirs() }
        File(dir, "color-reference-bias-audit-for252.json").writeText(
            """
            {
              "backend": "WebGPU",
              "referenceBackend": "mixed skia-upstream and generated-scene oracle",
              "linear": "FOR-252",
              "probe": "non-image-filter-color-reference-bias-audit",
              "deltaDefinition": "signed channel delta is GPU minus reference",
              "sampleCount": ${probe.sampleCount},
              "totalPixels": ${probe.totalPixels},
              "totalResidualPixels": ${probe.totalResidualPixels},
              "maxChannelDelta": ${probe.maxChannelDelta},
              "alphaDeltaNonZeroPixels": ${probe.alphaDeltaNonZeroPixels},
              "rgbOnlyResidualPixels": ${probe.rgbOnlyResidualPixels},
              "signedDeltaHistogram": ${signedDeltaHistogramToJson(probe.signedDeltaHistogram, indent = "              ")},
              "samplesWithRgbByteResidual": [
            ${probe.samplesWithRgbByteResidual.joinToString(",\n") { "                \"$it\"" }}
              ],
              "samplesWithoutResidual": [
            ${probe.samplesWithoutResidual.joinToString(",\n") { "                \"$it\"" }}
              ],
              "samples": [
            ${probe.samples.joinToString(",\n") { it.toJson(indent = "                ") }}
              ],
              "interpretation": "The RGB-only one-byte tail reproduces outside image-filter routing in SimpleOffset row1-col0-no-filter and bitmap-rect-nearest, while linear-gradient-rect remains byte-exact. This distinguishes the remaining SimpleOffset score loss from a bounded Crop renderer defect; no threshold, fallback, or Crop correction is justified.",
              "observationMethod": "test-side Skia reference PNG plus live WebGPU readback for the SimpleOffset source-only cell, and generated scene artifact skia.png/gpu.png comparisons for non image-filter baseline scenes; no renderer diagnostic property, no CPU/readback fallback in the render path",
              "supportDecision": "KEEP_DIAGNOSTIC",
              "correctionApplied": false,
              "preservedUnsupportedReason": "image-filter.crop-input-nonnull-prepass-required"
            }
            """.trimIndent() + "\n",
        )
    }

    private fun writeFor253BitmapSourceRoundingAuditJson(probe: For253BitmapSourceRoundingAuditProbe) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/generated/artifacts/bitmap-source-rounding-audit-for253",
        ).apply { mkdirs() }
        File(dir, "bitmap-source-rounding-audit-for253.json").writeText(
            """
            {
              "backend": "WebGPU",
              "referenceBackend": "mixed skia-upstream and generated-scene oracle",
              "linear": "FOR-253",
              "probe": "bitmap-source-rounding-stage-audit",
              "deltaDefinition": "signed channel delta is GPU minus reference",
              "microCaseCount": ${probe.microCaseCount},
              "totalPixels": ${probe.totalPixels},
              "totalResidualPixels": ${probe.totalResidualPixels},
              "maxChannelDelta": ${probe.maxChannelDelta},
              "alphaDeltaNonZeroPixels": ${probe.alphaDeltaNonZeroPixels},
              "rgbOnlyResidualPixels": ${probe.rgbOnlyResidualPixels},
              "signedDeltaHistogram": ${signedDeltaHistogramToJson(probe.signedDeltaHistogram, indent = "              ")},
              "residualCaseIds": [
            ${probe.residualCaseIds.joinToString(",\n") { "                \"$it\"" }}
              ],
              "exactControlCaseIds": [
            ${probe.exactControlCaseIds.joinToString(",\n") { "                \"$it\"" }}
              ],
              "stageIsolation": {
                "appearsBeforeBitmapSampling": ${probe.appearsBeforeBitmapSampling},
                "appearsDuringBitmapNearestPath": ${probe.appearsDuringBitmapNearestPath},
                "onlyFinalPackOrStore": ${probe.onlyFinalPackOrStore},
                "inferredProducer": "${probe.inferredProducer}",
                "nextSubProblem": "${probe.nextSubProblem}"
              },
              "microCases": [
            ${probe.microCases.joinToString(",\n") { it.toJson(indent = "                ") }}
              ],
              "interpretation": "The one-byte RGB tail appears in a source-color constant path before any bitmap sampling and also in bitmap-nearest texture sampling, while the generated linear-gradient control remains byte-exact. The evidence rules out Crop and a global final pack/store-only defect; the next bounded problem is input color normalization/rounding for legacy source-color uniforms and bitmap texel sampling/upload.",
              "observationMethod": "test-side Skia reference PNG plus live WebGPU readback for the SimpleOffset source-color cell, and generated scene artifact skia.png/gpu.png comparisons for bitmap-nearest and linear-gradient controls; no renderer diagnostic property, no CPU/readback fallback in the render path",
              "supportDecision": "${probe.supportDecision}",
              "correctionApplied": false,
              "preservedUnsupportedReason": "image-filter.crop-input-nonnull-prepass-required"
            }
            """.trimIndent() + "\n",
        )
    }

    private fun writeFor254SourceTexelRoundingAuditJson(probe: For254SourceTexelRoundingAuditProbe) {
        val dir = repoFile(
            "reports/wgsl-pipeline/scenes/generated/artifacts/source-texel-rounding-audit-for254",
        ).apply { mkdirs() }
        File(dir, "source-texel-rounding-audit-for254.json").writeText(
            """
            {
              "backend": "WebGPU",
              "referenceBackend": "mixed skia-upstream and artifact oracle PNGs",
              "linear": "FOR-254",
              "probe": "source-uniform-vs-bitmap-texel-rounding-audit",
              "deltaDefinition": "signed channel delta is GPU minus reference",
              "pathCount": ${probe.pathCount},
              "totalPixels": ${probe.totalPixels},
              "totalResidualPixels": ${probe.totalResidualPixels},
              "maxChannelDelta": ${probe.maxChannelDelta},
              "alphaDeltaNonZeroPixels": ${probe.alphaDeltaNonZeroPixels},
              "rgbOnlyResidualPixels": ${probe.rgbOnlyResidualPixels},
              "signedDeltaHistogram": ${signedDeltaHistogramToJson(probe.signedDeltaHistogram, indent = "              ")},
              "residualCaseIds": [
            ${probe.residualCaseIds.joinToString(",\n") { "                \"$it\"" }}
              ],
              "exactControlKinds": [
            ${probe.exactControlKinds.joinToString(",\n") { "                \"$it\"" }}
              ],
              "sourceTexelComparison": {
                "legacySourceUniformHasResidual": ${probe.legacySourceUniformHasResidual},
                "bitmapTexelPathHasResidual": ${probe.bitmapTexelPathHasResidual},
                "generatedControlsExact": ${probe.generatedControlsExact},
                "hostSideNormalizationVerdict": "${probe.hostSideNormalizationVerdict}",
                "uniformBufferWriteVerdict": "${probe.uniformBufferWriteVerdict}",
                "textureUploadSampleVerdict": "${probe.textureUploadSampleVerdict}",
                "correctionReadiness": "${probe.correctionReadiness}",
                "nextInstrumentation": "${probe.nextInstrumentation}"
              },
              "cases": [
            ${probe.cases.joinToString(",\n") { it.toJson(indent = "                ") }}
              ],
              "interpretation": "The RGB-only byte tail is present in the legacy source-color uniform path and bitmap texel upload/sample path, while generated solid and generated gradient controls are exact. The current proof narrows the remaining problem to an input normalization/consumption boundary, but raw uniform-buffer bytes and shader-observed sampled texels are not captured, so no bounded correction is proven.",
              "observationMethod": "test-side Skia reference PNG plus live WebGPU readback for the SimpleOffset source-color cell, and artifact oracle PNG comparisons for solid, gradient, and bitmap controls; no renderer diagnostic property, no CPU/readback fallback in the render path",
              "supportDecision": "${probe.supportDecision}",
              "correctionApplied": false,
              "preservedUnsupportedReason": "image-filter.crop-input-nonnull-prepass-required"
            }
            """.trimIndent() + "\n",
        )
    }

    private fun writeFor255RawColorSentinelAuditJson(probe: For255RawColorSentinelProbe) {
        val contents = """
            {
              "backend": "WebGPU",
              "referenceBackend": "mixed skia-upstream and artifact oracle PNGs",
              "linear": "FOR-255",
              "probe": "raw-color-sentinel-boundary-audit",
              "deltaDefinition": "signed channel delta is GPU minus reference",
              "propertyName": "${probe.propertyName}",
              "defaultEnabled": ${probe.defaultEnabled},
              "runtimeSnapshotsEnabled": ${probe.runtimeSnapshotsEnabled},
              "observedBoundaries": {
                "hostObserved": true,
                "uploadWriteObserved": true,
                "shaderObserved": "${probe.shaderObserved.status}",
                "outputReferenceGpuObserved": true
              },
              "legacySourceUniform": ${probe.legacySourceUniform.toJson(indent = "              ").trimStart()},
              "bitmapTexelUpload": ${probe.bitmapTexel.toJson(indent = "              ").trimStart()},
              "shaderObserved": ${probe.shaderObserved.toJson(indent = "              ").trimStart()},
              "interpretation": "The raw legacy source-color uniform words match the host-normalized RGBA input and the raw bitmap upload bytes match the representative RGBA8 texel. The remaining RGB-only 1-byte tail is therefore downstream of host packing/upload, or in reference/output comparison, but FOR-255 does not capture shader-observed pre-blend values without changing shader/pipeline layout.",
              "observationMethod": "opt-in SkWebGpuDevice raw sentinels enabled only by kanvas.webgpu.rawColorSentinels; records already-computed uniform floats before queue.writeBuffer and RGBA8 bytes before queue.writeTexture; normal render path, shaders, thresholds, fallback policy and CPU/readback behavior are unchanged",
              "supportDecision": "${probe.supportDecision}",
              "correctionApplied": false,
              "preservedUnsupportedReason": "image-filter.crop-input-nonnull-prepass-required"
            }
            """.trimIndent() + "\n"
        listOf(
            "reports/wgsl-pipeline/scenes/generated/artifacts/raw-color-sentinel-audit-for255",
            "reports/wgsl-pipeline/scenes/artifacts/raw-color-sentinel-audit-for255",
        ).forEach { path ->
            val dir = repoFile(path).apply { mkdirs() }
            File(dir, "raw-color-sentinel-audit-for255.json").writeText(contents)
        }
    }

    private fun PixelDelta.toJson(indent: String): String =
        """
        {
          "xy": [$x, $y],
          "referenceRgba": ${jsonArray(reference)},
          "gpuRgba": ${jsonArray(gpu)},
          "deltaRgba": ${jsonArray(delta)},
          "maxChannelDelta": $maxChannelDelta,
          "withinTolerance8": ${maxChannelDelta <= 8}
        }
        """.trimIndent().prependIndent(indent)

    private fun ColorPairGroup.toJson(indent: String): String =
        """
        {
          "referenceRgba": ${jsonArray(reference)},
          "gpuRgba": ${jsonArray(gpu)},
          "signedDeltaRgba": ${jsonArray(signedDelta)},
          "count": $count
        }
        """.trimIndent().prependIndent(indent)

    private fun ColorPremulCellAggregate.toJson(indent: String): String =
        """
        {
          "id": "$id",
          "label": "$label",
          "origin": ${jsonArray(origin)},
          "bounds": ${jsonArray(bounds)},
          "case": "$case",
          "residualPixels": $residualPixels,
          "maxChannelDelta": $maxChannelDelta,
          "alphaDeltaNonZeroPixels": $alphaDeltaNonZeroPixels,
          "rgbOnlyResidualPixels": $rgbOnlyResidualPixels,
          "signedDeltaHistogram": ${signedDeltaHistogramToJson(signedDeltaHistogram, indent = "$indent  ")},
          "topColorPairs": [
        ${topColorPairs.joinToString(",\n") { it.toJson(indent = "$indent    ") }}
          ]
        }
        """.trimIndent().prependIndent(indent)

    private fun For252NonImageFilterSample.toJson(indent: String): String =
        """
        {
          "id": "$id",
          "sceneId": "$sceneId",
          "label": "$label",
          "sourceKind": "$sourceKind",
          "route": "$route",
          "bounds": ${jsonArray(bounds)},
          "imageFilterInPath": $imageFilterInPath,
          "totalPixels": $totalPixels,
          "residualPixels": $residualPixels,
          "maxChannelDelta": $maxChannelDelta,
          "alphaDeltaNonZeroPixels": $alphaDeltaNonZeroPixels,
          "rgbOnlyResidualPixels": $rgbOnlyResidualPixels,
          "signedDeltaHistogram": ${signedDeltaHistogramToJson(signedDeltaHistogram, indent = "$indent  ")},
          "topColorPairs": [
        ${topColorPairs.joinToString(",\n") { it.toJson(indent = "$indent    ") }}
          ]
        }
        """.trimIndent().prependIndent(indent)

    private fun For253MicroCase.toJson(indent: String): String =
        """
        {
          "id": "$id",
          "sceneId": "$sceneId",
          "label": "$label",
          "sourceKind": "$sourceKind",
          "route": "$route",
          "colorProductionStage": "$colorProductionStage",
          "stageIsolation": "$stageIsolation",
          "bounds": ${jsonArray(bounds)},
          "totalPixels": $totalPixels,
          "residualPixels": $residualPixels,
          "maxChannelDelta": $maxChannelDelta,
          "alphaDeltaNonZeroPixels": $alphaDeltaNonZeroPixels,
          "rgbOnlyResidualPixels": $rgbOnlyResidualPixels,
          "representativeReferenceRgba": ${jsonArray(representativeReferenceRgba)},
          "representativeGpuRgba": ${jsonArray(representativeGpuRgba)},
          "representativeSignedDeltaRgba": ${jsonArray(representativeSignedDeltaRgba)},
          "signedDeltaHistogram": ${signedDeltaHistogramToJson(signedDeltaHistogram, indent = "$indent  ")},
          "topColorPairs": [
        ${topColorPairs.joinToString(",\n") { it.toJson(indent = "$indent    ") }}
          ]
        }
        """.trimIndent().prependIndent(indent)

    private fun For254InputObservation.toJson(indent: String): String =
        """
        {
          "source": "$source",
          "packedArgb": ${jsonArray(packedArgb)},
          "packedRgba": ${jsonArray(packedRgba)},
          "normalizedRgba": ${jsonArray(normalizedRgba)},
          "premulRgba": ${jsonArray(premulRgba)},
          "observableStatus": "$observableStatus"
        }
        """.trimIndent().prependIndent(indent)

    private fun For254PathCase.toJson(indent: String): String =
        """
        {
          "id": "$id",
          "pathKind": "$pathKind",
          "sceneId": "$sceneId",
          "label": "$label",
          "sourceKind": "$sourceKind",
          "route": "$route",
          "referenceSource": "$referenceSource",
          "inputObservation": ${inputObservation.toJson(indent = "$indent  ").trimStart()},
          "inputStageConclusion": "$inputStageConclusion",
          "bounds": ${jsonArray(bounds)},
          "totalPixels": $totalPixels,
          "residualPixels": $residualPixels,
          "maxChannelDelta": $maxChannelDelta,
          "alphaDeltaNonZeroPixels": $alphaDeltaNonZeroPixels,
          "rgbOnlyResidualPixels": $rgbOnlyResidualPixels,
          "representativeOutput": ${representativeOutput.toJson(indent = "$indent  ").trimStart()},
          "signedDeltaHistogram": ${signedDeltaHistogramToJson(signedDeltaHistogram, indent = "$indent  ")},
          "topColorPairs": [
        ${topColorPairs.joinToString(",\n") { it.toJson(indent = "$indent    ") }}
          ]
        }
        """.trimIndent().prependIndent(indent)

    private fun For255UniformBoundary.toJson(indent: String): String =
        """
        {
          "id": "$id",
          "hostObservedRgba8": ${jsonArray(hostObservedRgba8)},
          "hostObservedNormalizedRgba": ${jsonArray(hostObservedNormalizedRgba)},
          "rawWriteObservedRgba8": ${jsonArray(rawWriteObservedRgba8)},
          "rawUniformWordBits": ${jsonArray(rawUniformWordBits)},
          "rawUniformWordHex": [
        ${rawUniformWordHex.joinToString(",\n") { "$indent    \"$it\"" }}
          ],
          "scissor": ${jsonArray(scissor)},
          "outputReferenceGpu": ${output.toJson(indent = "$indent  ").trimStart()},
          "verdict": "$verdict"
        }
        """.trimIndent().prependIndent(indent)

    private fun For255BitmapBoundary.toJson(indent: String): String =
        """
        {
          "id": "$id",
          "hostObservedRgba8": ${jsonArray(hostObservedRgba8)},
          "rawUploadObservedRgba8": ${jsonArray(rawUploadObservedRgba8)},
          "uploadSize": ${jsonArray(uploadSize)},
          "uploadTexelXy": ${jsonArray(uploadTexelXy)},
          "outputReferenceGpu": ${output.toJson(indent = "$indent  ").trimStart()},
          "verdict": "$verdict"
        }
        """.trimIndent().prependIndent(indent)

    private fun For255ShaderObservedBoundary.toJson(indent: String): String =
        """
        {
          "status": "$status",
          "reason": "$reason"
        }
        """.trimIndent().prependIndent(indent)

    private fun signedDeltaHistogramToJson(histogram: List<Map<Int, Int>>, indent: String): String {
        val channels = listOf("r", "g", "b", "a")
        return channels.indices.joinToString(
            prefix = "{\n",
            separator = ",\n",
            postfix = "\n$indent}",
        ) { index ->
            val entries = histogram[index].entries.joinToString(
                prefix = "[",
                separator = ", ",
                postfix = "]",
            ) { entry -> """{"delta": ${entry.key}, "count": ${entry.value}}""" }
            """$indent  "${channels[index]}": $entries"""
        }
    }

    private fun ClassifiedPixelDelta.toJson(indent: String): String =
        """
        {
          "cellId": "$cellId",
          "pixel": ${pixel.toJson(indent = "$indent  ").trimStart()}
        }
        """.trimIndent().prependIndent(indent)

    private fun NamedPixelDelta.toJson(indent: String): String =
        """
        {
          "name": "$name",
          "sample": ${sample.toJson(indent = "$indent  ").trimStart()}
        }
        """.trimIndent().prependIndent(indent)

    private fun SimpleOffsetResidualAggregate.toJson(indent: String): String =
        """
        {
          "id": "$id",
          "label": "$label",
          "origin": ${jsonArray(origin)},
          "bounds": ${jsonArray(bounds)},
          "case": "$case",
          "pixelsAboveThreshold": $pixelsAboveThreshold,
          "maxChannelDelta": $maxChannelDelta,
          "topPixels": [
        ${topPixels.joinToString(",\n") { it.toJson(indent = "$indent    ") }}
          ]
        }
        """.trimIndent().prependIndent(indent)

    private fun jsonArray(values: IntArray): String = values.joinToString(
        prefix = "[",
        postfix = "]",
    )

    private fun jsonArray(values: FloatArray): String = values.joinToString(
        prefix = "[",
        postfix = "]",
    ) { value -> value.toString() }

    private fun repoFile(path: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        return File(dir, path)
    }

    private companion object {
        const val TARGET_X: Int = 385
        const val TARGET_Y: Int = 125
        const val WINDOW_RADIUS: Int = 2
        const val CELL_EXTENT: Int = 80
        const val HIGH_DELTA_THRESHOLD: Int = 0
        const val STRICT_HIGH_DELTA_THRESHOLD: Int = 8
        const val HIGH_DELTA_TOP_N: Int = 20
        const val COLOR_PAIR_TOP_N: Int = 16
        const val OUTSIDE_CELLS_ID: String = "outside-simple-offset-cells"
        const val FOR247_PROBE_PROPERTY: String = "kanvas.webgpu.for247.cropOffsetScratchProbe"
        const val FOR248_PROBE_PROPERTY: String = "kanvas.webgpu.for248.finalCropCompositeProbe"
        const val FOR255_RAW_SENTINELS_PROPERTY: String = "kanvas.webgpu.rawColorSentinels"
        const val WRITE_EVIDENCE_PROPERTY: String = "kanvas.sceneEvidence.write"
        val DOMINANT_FOR251_CELL_IDS: List<String> = listOf(
            "row1-col0-no-filter",
            "row1-col1-offset-no-crop",
            "row2-col3-crop-clip-dst",
        )
        val SIMPLE_OFFSET_CELLS: List<SimpleOffsetCell> = listOf(
            SimpleOffsetCell(
                id = "row1-col0-no-filter",
                label = "row 1 col 0: blue source only",
                originX = 40,
                originY = 40,
                case = "no image filter",
            ),
            SimpleOffsetCell(
                id = "row1-col1-offset-no-crop",
                label = "row 1 col 1: Offset(20,20), no crop, no clip",
                originX = 140,
                originY = 40,
                case = "Offset(20,20,input=null)",
            ),
            SimpleOffsetCell(
                id = "row1-col2-offset-crop-src",
                label = "row 1 col 2: Offset(20,20), crop == src",
                originX = 240,
                originY = 40,
                case = "Offset(20,20,input=null,cropRect=src)",
            ),
            SimpleOffsetCell(
                id = "row1-col3-offset-clip-src",
                label = "row 1 col 3: Offset(20,20), clip == src",
                originX = 340,
                originY = 40,
                case = "Offset(20,20,input=null), clip=src",
            ),
            SimpleOffsetCell(
                id = "row1-col4-offset-crop-20x20",
                label = "row 1 col 4: Offset(20,20), crop 20x20",
                originX = 440,
                originY = 40,
                case = "Offset(20,20,input=null,cropRect=20x20)",
            ),
            SimpleOffsetCell(
                id = "row1-col5-offset-clip-dst",
                label = "row 1 col 5: Offset(20,20), clip == dst",
                originX = 540,
                originY = 40,
                case = "Offset(20,20,input=null), clip=dst",
            ),
            SimpleOffsetCell(
                id = "row2-col0-crop-clip-src",
                label = "row 2 col 0: crop == clip == src",
                originX = 40,
                originY = 120,
                case = "Offset(40,0,input=null,cropRect=src), clip=src",
            ),
            SimpleOffsetCell(
                id = "row2-col1-crop-src-clip-dst",
                label = "row 2 col 1: crop == src, clip == dst",
                originX = 140,
                originY = 120,
                case = "Offset(40,0,input=null,cropRect=src), clip=dst",
            ),
            SimpleOffsetCell(
                id = "row2-col2-crop-dst-clip-src",
                label = "row 2 col 2: crop == dst, clip == src",
                originX = 240,
                originY = 120,
                case = "Offset(40,0,input=null,cropRect=dst), clip=src",
            ),
            SimpleOffsetCell(
                id = "row2-col3-crop-clip-dst",
                label = "row 2 col 3: crop == clip == dst",
                originX = 340,
                originY = 120,
                case = "Offset(40,0,input=null,cropRect=dst), clip=dst",
            ),
        )
    }

    private data class SimpleOffsetCell(
        val id: String,
        val label: String,
        val originX: Int,
        val originY: Int,
        val case: String,
    )

    private data class PixelDelta(
        val x: Int,
        val y: Int,
        val reference: IntArray,
        val gpu: IntArray,
        val delta: IntArray,
    ) {
        val maxChannelDelta: Int = delta.maxOrNull() ?: 0
        val signedDelta: IntArray = IntArray(4) { channel -> gpu[channel] - reference[channel] }
    }

    private data class NamedPixelDelta(
        val name: String,
        val sample: PixelDelta,
    )

    private data class ClassifiedPixelDelta(
        val cellId: String,
        val pixel: PixelDelta,
    )

    private data class For249ResidualProbe(
        val target: PixelDelta,
        val window: List<PixelDelta>,
        val cellSamples: List<NamedPixelDelta>,
    ) {
        val windowMaxChannelDelta: Int = window.maxOf { it.maxChannelDelta }
        val maxObservedDelta: Int = (window + cellSamples.map { it.sample }).maxOf { it.maxChannelDelta }
    }

    private data class SimpleOffsetResidualAggregate(
        val id: String,
        val label: String,
        val origin: IntArray,
        val bounds: IntArray,
        val case: String,
        val pixelsAboveThreshold: Int,
        val maxChannelDelta: Int,
        val topPixels: List<ClassifiedPixelDelta>,
    )

    private data class For250HighDeltaScanProbe(
        val width: Int,
        val height: Int,
        val threshold: Int,
        val strictHighDeltaThreshold: Int,
        val totalPixelsAboveThreshold: Int,
        val totalPixelsAboveStrictHighDeltaThreshold: Int,
        val maxChannelDelta: Int,
        val topPixels: List<ClassifiedPixelDelta>,
        val cellAggregates: List<SimpleOffsetResidualAggregate>,
    )

    private data class ColorPairAccumulator(
        val reference: IntArray,
        val gpu: IntArray,
        val signedDelta: IntArray,
        var count: Int = 0,
    )

    private data class ColorPairGroup(
        val reference: IntArray,
        val gpu: IntArray,
        val signedDelta: IntArray,
        val count: Int,
    )

    private data class ColorPremulCellAggregate(
        val id: String,
        val label: String,
        val origin: IntArray,
        val bounds: IntArray,
        val case: String,
        val residualPixels: Int,
        val maxChannelDelta: Int,
        val alphaDeltaNonZeroPixels: Int,
        val rgbOnlyResidualPixels: Int,
        val signedDeltaHistogram: List<Map<Int, Int>>,
        val topColorPairs: List<ColorPairGroup>,
    )

    private data class For251ColorPremulAuditProbe(
        val width: Int,
        val height: Int,
        val totalResidualPixels: Int,
        val maxChannelDelta: Int,
        val alphaDeltaNonZeroPixels: Int,
        val rgbOnlyResidualPixels: Int,
        val signedDeltaHistogram: List<Map<Int, Int>>,
        val topColorPairs: List<ColorPairGroup>,
        val dominantCellAggregates: List<ColorPremulCellAggregate>,
    )

    private data class For252SampleBuildResult(
        val sample: For252NonImageFilterSample,
        val residualPixels: List<PixelDelta>,
    )

    private data class For252NonImageFilterSample(
        val id: String,
        val sceneId: String,
        val label: String,
        val sourceKind: String,
        val route: String,
        val bounds: IntArray,
        val imageFilterInPath: Boolean,
        val totalPixels: Int,
        val residualPixels: Int,
        val maxChannelDelta: Int,
        val alphaDeltaNonZeroPixels: Int,
        val rgbOnlyResidualPixels: Int,
        val signedDeltaHistogram: List<Map<Int, Int>>,
        val topColorPairs: List<ColorPairGroup>,
    )

    private data class For252ColorReferenceBiasAuditProbe(
        val sampleCount: Int,
        val samples: List<For252NonImageFilterSample>,
        val totalPixels: Int,
        val totalResidualPixels: Int,
        val maxChannelDelta: Int,
        val alphaDeltaNonZeroPixels: Int,
        val rgbOnlyResidualPixels: Int,
        val signedDeltaHistogram: List<Map<Int, Int>>,
        val samplesWithRgbByteResidual: List<String>,
        val samplesWithoutResidual: List<String>,
    )

    private data class For253MicroCase(
        val id: String,
        val sceneId: String,
        val label: String,
        val sourceKind: String,
        val route: String,
        val colorProductionStage: String,
        val stageIsolation: String,
        val bounds: IntArray,
        val totalPixels: Int,
        val residualPixels: Int,
        val maxChannelDelta: Int,
        val alphaDeltaNonZeroPixels: Int,
        val rgbOnlyResidualPixels: Int,
        val signedDeltaHistogram: List<Map<Int, Int>>,
        val topColorPairs: List<ColorPairGroup>,
        val representativeReferenceRgba: IntArray,
        val representativeGpuRgba: IntArray,
        val representativeSignedDeltaRgba: IntArray,
        val residualPixelDeltas: List<PixelDelta>,
    )

    private data class For253BitmapSourceRoundingAuditProbe(
        val microCases: List<For253MicroCase>,
        val totalPixels: Int,
        val totalResidualPixels: Int,
        val maxChannelDelta: Int,
        val alphaDeltaNonZeroPixels: Int,
        val rgbOnlyResidualPixels: Int,
        val signedDeltaHistogram: List<Map<Int, Int>>,
        val residualCaseIds: List<String>,
        val exactControlCaseIds: List<String>,
        val appearsBeforeBitmapSampling: Boolean,
        val appearsDuringBitmapNearestPath: Boolean,
        val onlyFinalPackOrStore: Boolean,
    ) {
        val microCaseCount: Int = microCases.size
        val supportDecision: String = "KEEP_DIAGNOSTIC"
        val inferredProducer: String =
            "input color normalization/rounding before or at source-color uniform and bitmap texel sample, not Crop and not a global final pack/store-only stage"
        val nextSubProblem: String =
            "compare legacy source-color uniform packing and bitmap texture upload/sample rounding against generated solid/gradient exact controls"
    }

    private data class For254InputObservation(
        val source: String,
        val packedArgb: IntArray,
        val packedRgba: IntArray,
        val normalizedRgba: FloatArray,
        val premulRgba: FloatArray,
        val observableStatus: String,
    )

    private data class For254PathCase(
        val id: String,
        val pathKind: String,
        val sceneId: String,
        val label: String,
        val sourceKind: String,
        val route: String,
        val referenceSource: String,
        val inputObservation: For254InputObservation,
        val inputStageConclusion: String,
        val bounds: IntArray,
        val totalPixels: Int,
        val residualPixels: Int,
        val maxChannelDelta: Int,
        val alphaDeltaNonZeroPixels: Int,
        val rgbOnlyResidualPixels: Int,
        val representativeOutput: PixelDelta,
        val signedDeltaHistogram: List<Map<Int, Int>>,
        val topColorPairs: List<ColorPairGroup>,
        val residualPixelDeltas: List<PixelDelta>,
    )

    private data class For254SourceTexelRoundingAuditProbe(
        val cases: List<For254PathCase>,
        val totalPixels: Int,
        val totalResidualPixels: Int,
        val maxChannelDelta: Int,
        val alphaDeltaNonZeroPixels: Int,
        val rgbOnlyResidualPixels: Int,
        val signedDeltaHistogram: List<Map<Int, Int>>,
        val residualCaseIds: List<String>,
        val exactControlKinds: List<String>,
        val legacySourceUniformHasResidual: Boolean,
        val bitmapTexelPathHasResidual: Boolean,
        val generatedControlsExact: Boolean,
    ) {
        val pathCount: Int = cases.size
        val supportDecision: String = "KEEP_DIAGNOSTIC"
        val hostSideNormalizationVerdict: String =
            "source ARGB/RGBA bytes and /255f normalized floats are deducible for legacy source color and bitmap oracle texel, but this audit does not observe post-write GPU buffer/texture contents"
        val uniformBufferWriteVerdict: String =
            "not directly observable; source-color output residual may be in uniform write, uniform consumption, blend rounding, or reference conversion"
        val textureUploadSampleVerdict: String =
            "not directly observable; bitmap output residual may be in texture upload normalization, sampler read, shader arithmetic, or reference conversion"
        val correctionReadiness: String =
            "not ready: generated controls are exact but both legacy uniform and bitmap texel paths still need raw sentinels before a bounded correction"
        val nextInstrumentation: String =
            "add renderer diagnostic sentinels that dump raw uniform buffer color words, uploaded RGBA8 texel bytes, and shader-observed sampled color before blend/store"
    }

    private data class RawSentinelRenderResult(
        val bitmap: SkBitmap,
        val sentinels: SkWebGpuDevice.RawColorSentinelSnapshot,
    )

    private data class RawUploadSentinelRenderResult(
        val outputRgba: IntArray,
        val sentinels: SkWebGpuDevice.RawColorSentinelSnapshot,
    )

    private data class For255UniformBoundary(
        val id: String,
        val hostObservedRgba8: IntArray,
        val hostObservedNormalizedRgba: FloatArray,
        val rawWriteObservedRgba8: IntArray,
        val rawUniformWordBits: IntArray,
        val rawUniformWordHex: List<String>,
        val scissor: IntArray,
        val output: PixelDelta,
        val verdict: String,
    )

    private data class For255BitmapBoundary(
        val id: String,
        val hostObservedRgba8: IntArray,
        val rawUploadObservedRgba8: IntArray,
        val uploadSize: IntArray,
        val uploadTexelXy: IntArray,
        val output: PixelDelta,
        val verdict: String,
    )

    private data class For255ShaderObservedBoundary(
        val status: String,
        val reason: String,
    )

    private data class For255RawColorSentinelProbe(
        val propertyName: String,
        val defaultEnabled: Boolean,
        val runtimeSnapshotsEnabled: Boolean,
        val legacySourceUniform: For255UniformBoundary,
        val bitmapTexel: For255BitmapBoundary,
        val shaderObserved: For255ShaderObservedBoundary,
    ) {
        val supportDecision: String = "KEEP_DIAGNOSTIC"
    }
}
