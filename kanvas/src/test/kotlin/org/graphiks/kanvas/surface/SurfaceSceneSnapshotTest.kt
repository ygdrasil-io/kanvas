package org.graphiks.kanvas.surface

import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.render.ir.SceneCaptureResult
import org.graphiks.kanvas.render.ir.SceneCaptureLimits
import org.graphiks.kanvas.render.ir.SceneCommand
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class SurfaceSceneSnapshotTest {
    @Test
    fun `snapshotScene captures recorded operations at surface extent without rendering`() {
        val surface = Surface(
            width = 37,
            height = 19,
            config = RenderConfig(maxPathVertices = UInt.MAX_VALUE),
        )
        surface.canvas { drawRect(RectF32.ofLTRB(1f, 2f, 8f, 9f), Paint.fill(ColorARGB.Red)) }

        val captured = assertInstanceOf(SceneCaptureResult.Captured::class.java, surface.snapshotScene()).scene

        assertEquals(37, captured.extent.width)
        assertEquals(19, captured.extent.height)
        assertEquals(ColorSpace.SRGB, captured.colorSpace)
        assertEquals(1, captured.commandCount)
        assertInstanceOf(SceneCommand.Draw::class.java, captured.commandAt(0))
    }

    @Test
    fun `snapshotScene is detached from later canvas mutations`() {
        val surface = Surface(16, 12)
        surface.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), Paint.fill(ColorARGB.Blue)) }

        val captured = assertInstanceOf(SceneCaptureResult.Captured::class.java, surface.snapshotScene()).scene
        surface.canvas { drawRect(RectF32.ofLTRB(4f, 4f, 8f, 8f), Paint.fill(ColorARGB.Green)) }

        assertEquals(1, captured.commandCount)
        assertEquals(2, assertInstanceOf(SceneCaptureResult.Captured::class.java, surface.snapshotScene()).scene.commandCount)
    }

    @Test
    fun `snapshotScene accepts an explicit capture budget for larger recordings`() {
        val surface = Surface(16, 12)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 4f, 4f), Paint.fill(ColorARGB.Blue))
            drawRect(RectF32.ofLTRB(4f, 4f, 8f, 8f), Paint.fill(ColorARGB.Green))
        }

        assertInstanceOf(SceneCaptureResult.Invalid::class.java, surface.snapshotScene(SceneCaptureLimits(maxNodes = 1)))
        val captured = assertInstanceOf(
            SceneCaptureResult.Captured::class.java,
            surface.snapshotScene(SceneCaptureLimits(maxNodes = 2)),
        ).scene

        assertEquals(2, captured.commandCount)
    }
}
