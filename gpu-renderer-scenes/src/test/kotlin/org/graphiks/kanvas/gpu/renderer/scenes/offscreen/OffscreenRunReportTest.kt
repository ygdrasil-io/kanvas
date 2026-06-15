package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

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
}
