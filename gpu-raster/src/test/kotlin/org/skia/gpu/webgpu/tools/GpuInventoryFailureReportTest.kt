package org.skia.gpu.webgpu.tools

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class GpuInventoryFailureReportTest {
    @Test
    fun `classifies required gpu inventory failure categories from junit xml`() {
        val testRoot = Files.createTempDirectory("gpu-inventory-report-test")
        val xml = testRoot.resolve("TEST-org.skia.gpu.webgpu.InventorySample.xml")
        Files.writeString(
            xml,
            """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<testsuite name="org.skia.gpu.webgpu.InventorySample" tests="6" failures="4" skipped="2" errors="0">
            |  <testcase classname="org.skia.gpu.webgpu.PathCase" name="edgeOverflow">
            |    <failure message="refused path">SkWebGpuDevice refused route=webgpu.coverage.refuse diagnostic=backend=GPU,reason=coverage.edge-count-exceeded,action=RefuseDiagnostic(coverage.edge-count-exceeded)</failure>
            |  </testcase>
            |  <testcase classname="org.skia.gpu.webgpu.BitmapCase" name="similarityFloor">
            |    <failure message="floor">DrawBitmapRect3 regressed below floor : 97,15% &lt; 99,95%. See gpu-raster/build/debug-images/draw_bitmap_rect3-{raster,gpu,diff}.png.</failure>
            |  </testcase>
            |  <testcase classname="org.skia.gpu.webgpu.FilterCase" name="cropNonNull">
            |    <failure message="crop">reason=image-filter.crop-input-nonnull-prepass-required: SkImageFilters.Crop(input = nonNull) with a non-null child filter needs a pre-pass.</failure>
            |  </testcase>
            |  <testcase classname="org.skia.gpu.webgpu.SkipCase" name="adapterMissing">
            |    <skipped message="No WebGPU adapter"/>
            |  </testcase>
            |  <testcase classname="org.skia.gpu.webgpu.SkipCase" name="adapterSkippedForPolicy">
            |    <skipped message="Adapter precondition not met"/>
            |  </testcase>
            |  <testcase classname="org.skia.gpu.webgpu.OtherCase" name="unknownFailure">
            |    <failure message="boom">java.lang.IllegalStateException: something else failed</failure>
            |  </testcase>
            |</testsuite>
            """.trimMargin(),
        )

        val summary = GpuInventoryFailureReport.run(testRoot)
        assertEquals(6, summary.total)
        assertEquals(1, summary.byCategory.getValue(GpuInventoryFailureCategory.ExpectedUnsupportedDiagnostic))
        assertEquals(1, summary.byCategory.getValue(GpuInventoryFailureCategory.SimilarityRegression))
        assertEquals(1, summary.byCategory.getValue(GpuInventoryFailureCategory.UnsupportedImageFilter))
        assertEquals(1, summary.byCategory.getValue(GpuInventoryFailureCategory.AdapterMissing))
        assertEquals(1, summary.byCategory.getValue(GpuInventoryFailureCategory.AdapterSkip))
        assertEquals(1, summary.byCategory.getValue(GpuInventoryFailureCategory.UnexpectedException))

        val similarity = summary.records.first { it.category == GpuInventoryFailureCategory.SimilarityRegression }
        assertEquals(97.15, similarity.actualSimilarityPercent)
        assertEquals(99.95, similarity.floorSimilarityPercent)
        assertEquals("gpu-raster/build/debug-images/draw_bitmap_rect3-{raster,gpu,diff}.png", similarity.artifactPath)
        val cropNonNull = summary.records.first { it.category == GpuInventoryFailureCategory.UnsupportedImageFilter }
        assertEquals("image-filter.crop-input-nonnull-prepass-required", cropNonNull.reason)
    }

    @Test
    fun `writes machine readable and pm readable inventory artifacts`() {
        val testRoot = Files.createTempDirectory("gpu-inventory-report-out")
        val xml = testRoot.resolve("TEST-org.skia.gpu.webgpu.InventorySample.xml")
        Files.writeString(
            xml,
            """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<testsuite name="org.skia.gpu.webgpu.InventorySample" tests="1" failures="1" skipped="0" errors="0">
            |  <testcase classname="org.skia.gpu.webgpu.PathCase" name="edgeOverflow">
            |    <failure message="refused path">coverage.edge-count-exceeded</failure>
            |  </testcase>
            |</testsuite>
            """.trimMargin(),
        )

        val outputDir = Files.createTempDirectory("gpu-inventory-report-write")
        val summary = GpuInventoryFailureReport.run(testRoot)
        val (markdownPath, jsonPath) = GpuInventoryFailureReport.writeOutputs(summary, outputDir)

        val markdown = Files.readString(markdownPath)
        val json = Files.readString(jsonPath)
        assertTrue(markdown.contains("GPU Inventory Failure Classification"))
        assertTrue(markdown.contains("| `expected-unsupported-diagnostic` | 1 |"))
        assertTrue(markdown.contains("Expected Unsupported Reason Catalog"))
        assertTrue(markdown.contains("| `coverage.edge-count-exceeded` | `GRA-70"))
        assertTrue(markdown.contains("Unsupported Image-Filter Reason Catalog"))
        assertTrue(markdown.contains("image-filter.crop-input-nonnull-prepass-required"))
        assertTrue(markdown.contains("expected-unsupported-diagnostic"))
        assertTrue(json.contains("\"records\""))
        assertTrue(json.contains("\"expected-unsupported-diagnostic\""))
    }

    @Test
    fun `lists exact crop non null failing tests in expected unsupported inventory section`() {
        val testRoot = Files.createTempDirectory("gpu-inventory-crop-tests")
        val xml = testRoot.resolve("TEST-org.skia.gpu.webgpu.InventoryCrop.xml")
        Files.writeString(
            xml,
            """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<testsuite name="org.skia.gpu.webgpu.InventoryCrop" tests="2" failures="2" skipped="0" errors="0">
            |  <testcase classname="org.skia.gpu.webgpu.SimpleOffsetImageFilterWebGpuTest" name="SimpleOffsetImageFilterGM renders close to reference PNG on the GPU backend()">
            |    <failure message="crop">reason=image-filter.crop-input-nonnull-prepass-required: SkImageFilters.Crop(input = nonNull) with a non-null child filter needs a pre-pass.</failure>
            |  </testcase>
            |  <testcase classname="org.skia.gpu.webgpu.crossbackend.SimpleOffsetImageFilterCrossBackendTest" name="SimpleOffsetImageFilterGM matches reference on raster and GPU backends()">
            |    <failure message="crop">reason=image-filter.crop-input-nonnull-prepass-required: SkImageFilters.Crop(input = nonNull) with a non-null child filter needs a pre-pass.</failure>
            |  </testcase>
            |</testsuite>
            """.trimMargin(),
        )

        val summary = GpuInventoryFailureReport.run(testRoot)
        val markdown = GpuInventoryFailureReport.toMarkdown(summary)

        assertTrue(markdown.contains("Crop(input = nonNull) Expected Unsupported Inventory Tests"))
        assertTrue(
            markdown.contains(
                "org.skia.gpu.webgpu.SimpleOffsetImageFilterWebGpuTest#SimpleOffsetImageFilterGM renders close to reference PNG on the GPU backend()",
            ),
        )
        assertTrue(
            markdown.contains(
                "org.skia.gpu.webgpu.crossbackend.SimpleOffsetImageFilterCrossBackendTest#SimpleOffsetImageFilterGM matches reference on raster and GPU backends()",
            ),
        )
    }
}
