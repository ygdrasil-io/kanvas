package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.foundation.SkBitmap
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.testing.TestUtils
import org.skia.tests.SimpleOffsetImageFilterGM
import java.io.File

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

    private fun rgbaAt(bitmap: SkBitmap, x: Int, y: Int): IntArray {
        val pixel = bitmap.getPixel(x, y)
        return intArrayOf(
            (pixel ushr 16) and 0xFF,
            (pixel ushr 8) and 0xFF,
            pixel and 0xFF,
            (pixel ushr 24) and 0xFF,
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
        const val OUTSIDE_CELLS_ID: String = "outside-simple-offset-cells"
        const val FOR247_PROBE_PROPERTY: String = "kanvas.webgpu.for247.cropOffsetScratchProbe"
        const val FOR248_PROBE_PROPERTY: String = "kanvas.webgpu.for248.finalCropCompositeProbe"
        const val WRITE_EVIDENCE_PROPERTY: String = "kanvas.sceneEvidence.write"
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
}
