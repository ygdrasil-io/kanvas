package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class OffscreenRunReportTest {
    @Test
    fun `not yet rendered is a runner status not a product refusal`() {
        val report = OffscreenRunReport.notYetRendered(sceneId = "rounded-panel-gradient", reason = "runner-subset")
        assertEquals("not-yet-rendered", report.status)
        assertEquals(false, report.productRefusal)
        assertContains(report.toJson(), "\"status\": \"not-yet-rendered\"")
    }

    @Test
    fun `report writer creates run json and diagnostics`() {
        val root = Files.createTempDirectory("gpu-renderer-scenes-offscreen")
        val report = OffscreenRunReport.notYetRendered(sceneId = "mesh-ribbon", reason = "runner-subset")
        report.writeTo(root)
        assertContains(root.resolve("run.json").readText(), "\"sceneId\": \"mesh-ribbon\"")
        assertContains(root.resolve("diagnostics.txt").readText(), "runner-subset")
    }

    @Test
    fun `render failed is a runner status not a product refusal`() {
        val report = OffscreenRunReport.failed(
            sceneId = "mesh-ribbon",
            reason = "surface unavailable",
            backend = "test-backend",
        )

        assertEquals("render-failed", report.status)
        assertEquals(false, report.productRefusal)
        assertEquals("test-backend", report.backend)
        assertNull(report.imagePath)
        assertNull(report.width)
        assertNull(report.height)
        assertNull(report.byteCount)
        assertNull(report.nonTransparentPixels)
        assertContains(report.toJson(), "\"status\": \"render-failed\"")
        assertContains(report.toJson(), "\"productRefusal\": false")
        assertContains(report.toJson(), "surface unavailable")
    }

    @Test
    fun `rendered is a runner status not a product refusal`() {
        val report = OffscreenRunReport.rendered(
            sceneId = "solid-card-stack",
            imagePath = "render.png",
            width = 320,
            height = 200,
            byteCount = 256000,
            nonTransparentPixels = 64000,
            diagnostics = listOf("rendered solid-card-stack via WebGPU offscreen"),
        )

        assertEquals("rendered", report.status)
        assertEquals(false, report.productRefusal)
        assertEquals("render.png", report.imagePath)
        assertEquals(320, report.width)
        assertEquals(200, report.height)
        assertEquals(256000L, report.byteCount)
        assertEquals(64000, report.nonTransparentPixels)
        assertContains(report.toJson(), "\"status\": \"rendered\"")
        assertContains(report.toJson(), "\"productRefusal\": false")
        assertContains(report.toJson(), "\"imagePath\": \"render.png\"")
        assertContains(report.toJson(), "\"nonTransparentPixels\": 64000")
    }

    @Test
    fun `factories reject blank reasons`() {
        assertFailsWith<IllegalArgumentException> {
            OffscreenRunReport.notYetRendered(sceneId = "rounded-panel-gradient", reason = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            OffscreenRunReport.failed(sceneId = "mesh-ribbon", reason = "\t")
        }
    }

    @Test
    fun `rendered reports reject invalid rendered metrics`() {
        assertFailsWith<IllegalArgumentException> {
            OffscreenRunReport.rendered(
                sceneId = "solid-card-stack",
                imagePath = " ",
                width = 320,
                height = 200,
                byteCount = 1834,
                nonTransparentPixels = 64000,
                diagnostics = listOf("rendered"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            OffscreenRunReport.rendered(
                sceneId = "solid-card-stack",
                imagePath = "render.png",
                width = 0,
                height = 200,
                byteCount = 1834,
                nonTransparentPixels = 64000,
                diagnostics = listOf("rendered"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            OffscreenRunReport.rendered(
                sceneId = "solid-card-stack",
                imagePath = "render.png",
                width = 320,
                height = -1,
                byteCount = 1834,
                nonTransparentPixels = 64000,
                diagnostics = listOf("rendered"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            OffscreenRunReport.rendered(
                sceneId = "solid-card-stack",
                imagePath = "render.png",
                width = 320,
                height = 200,
                byteCount = 0,
                nonTransparentPixels = 64000,
                diagnostics = listOf("rendered"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            OffscreenRunReport.rendered(
                sceneId = "solid-card-stack",
                imagePath = "render.png",
                width = 320,
                height = 200,
                byteCount = 1834,
                nonTransparentPixels = 0,
                diagnostics = listOf("rendered"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            OffscreenRunReport.rendered(
                sceneId = "solid-card-stack",
                imagePath = "render.png",
                width = 320,
                height = 200,
                byteCount = 1834,
                nonTransparentPixels = 64000,
                diagnostics = listOf(" "),
            )
        }
    }

    @Test
    fun `rendered reports reject byte counts that do not match raw rgba dimensions`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            OffscreenRunReport.rendered(
                sceneId = "solid-card-stack",
                imagePath = "render.png",
                width = 2,
                height = 2,
                byteCount = 15,
                nonTransparentPixels = 1,
                diagnostics = listOf("rendered"),
            )
        }

        assertContains(failure.message ?: "", "raw RGBA byteCount")
    }

    @Test
    fun `rendered reports reject non transparent pixels beyond pixel count`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            OffscreenRunReport.rendered(
                sceneId = "solid-card-stack",
                imagePath = "render.png",
                width = 2,
                height = 2,
                byteCount = 16,
                nonTransparentPixels = 5,
                diagnostics = listOf("rendered"),
            )
        }

        assertContains(failure.message ?: "", "nonTransparentPixels")
    }

    @Test
    fun `constructor rejects empty or blank diagnostics`() {
        assertFailsWith<IllegalArgumentException> {
            OffscreenRunReport(
                sceneId = "empty-diagnostics",
                runStatus = OffscreenRunStatus.NotYetRendered,
                backend = "webgpu-offscreen",
                imagePath = null,
                width = null,
                height = null,
                byteCount = null,
                nonTransparentPixels = null,
                diagnostics = emptyList(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            OffscreenRunReport(
                sceneId = "blank-diagnostic",
                runStatus = OffscreenRunStatus.RenderFailed,
                backend = "webgpu-offscreen",
                imagePath = null,
                width = null,
                height = null,
                byteCount = null,
                nonTransparentPixels = null,
                diagnostics = listOf("surface unavailable", " "),
            )
        }
    }

    @Test
    fun `constructor rejects blank scene ids and backends`() {
        assertFailsWith<IllegalArgumentException> {
            OffscreenRunReport(
                sceneId = " ",
                runStatus = OffscreenRunStatus.NotYetRendered,
                backend = "webgpu-offscreen",
                imagePath = null,
                width = null,
                height = null,
                byteCount = null,
                nonTransparentPixels = null,
                diagnostics = listOf("runner-subset"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            OffscreenRunReport(
                sceneId = "mesh-ribbon",
                runStatus = OffscreenRunStatus.RenderFailed,
                backend = "\n",
                imagePath = null,
                width = null,
                height = null,
                byteCount = null,
                nonTransparentPixels = null,
                diagnostics = listOf("surface unavailable"),
            )
        }
    }

    @Test
    fun `failure and non rendered statuses reject image outputs and metrics`() {
        assertFailsWith<IllegalArgumentException> {
            OffscreenRunReport(
                sceneId = "failed-with-image",
                runStatus = OffscreenRunStatus.RenderFailed,
                backend = "webgpu-offscreen",
                imagePath = "failed.png",
                width = null,
                height = null,
                byteCount = null,
                nonTransparentPixels = null,
                diagnostics = listOf("surface unavailable"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            OffscreenRunReport(
                sceneId = "failed-with-metrics",
                runStatus = OffscreenRunStatus.RenderFailed,
                backend = "webgpu-offscreen",
                imagePath = null,
                width = 64,
                height = 64,
                byteCount = 4096,
                nonTransparentPixels = 12,
                diagnostics = listOf("surface unavailable"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            OffscreenRunReport(
                sceneId = "not-rendered-with-image",
                runStatus = OffscreenRunStatus.NotYetRendered,
                backend = "webgpu-offscreen",
                imagePath = "not-rendered.png",
                width = null,
                height = null,
                byteCount = null,
                nonTransparentPixels = null,
                diagnostics = listOf("runner-subset"),
            )
        }
    }

    @Test
    fun `diagnostics are immutable after construction`() {
        val diagnostics = mutableListOf("runner-subset")
        val report = OffscreenRunReport(
            sceneId = "mutable-diagnostics",
            runStatus = OffscreenRunStatus.NotYetRendered,
            backend = "webgpu-offscreen",
            imagePath = null,
            width = null,
            height = null,
            byteCount = null,
            nonTransparentPixels = null,
            diagnostics = diagnostics,
        )

        diagnostics.clear()
        diagnostics += "mutated after construction"
        diagnostics += " "

        assertEquals(listOf("runner-subset"), report.diagnostics)
        assertContains(report.toJson(), "runner-subset")
        assertFalse(report.toJson().contains("mutated after construction"))
    }
}
