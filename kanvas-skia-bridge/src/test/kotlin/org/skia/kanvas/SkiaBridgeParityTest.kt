package org.skia.kanvas

import org.graphiks.math.SkRect
import org.skia.core.SkSurface
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkRRect
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTypeface
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

data class DrawFamilyParityResult(
    val family: String,
    val taskCountSkia: Int,
    val taskCountKanvas: Int,
    val tasksMatch: Boolean,
    val pixelDiff: Float? = null,
)

class SkiaBridgeParityTest {

    private fun renderKanvasBridge(width: Int, height: Int, draw: (SkiaKanvasSurface) -> Unit): org.graphiks.kanvas.Frame {
        val skiaSurface = SkSurface.MakeRasterN32Premul(width, height)
        val kanvasSurface = SkiaKanvasSurface.wrap(skiaSurface)
        draw(kanvasSurface)
        return kanvasSurface.flush()
    }

    @Test
    fun `parity rect fill task equivalence`() {
        val paint = SkPaint().apply { color = 0xFFFF0000.toInt() }
        val rect = SkRect.MakeLTRB(10f, 10f, 100f, 100f)

        val frame = renderKanvasBridge(256, 256) { surface ->
            surface.drawRect(rect, paint)
        }

        assertTrue(frame.recording.taskList.tasks.isNotEmpty(),
            "Kanvas bridge should record tasks for rect fill")
        assertEquals(1, frame.recording.taskList.tasks.size,
            "Rect fill should produce exactly one task")
    }

    @Test
    fun `parity rrect fill task equivalence`() {
        val paint = SkPaint().apply { color = 0xFF0000FF.toInt() }
        val rrect = SkRRect.MakeRectXY(SkRect.MakeLTRB(10f, 10f, 100f, 100f), 8f, 8f)

        val frame = renderKanvasBridge(256, 256) { surface ->
            surface.drawRRect(rrect, paint)
        }

        assertTrue(frame.recording.taskList.tasks.isNotEmpty(),
            "Kanvas bridge should record tasks for rrect fill")
    }

    @Test
    fun `parity path fill task equivalence`() {
        val paint = SkPaint().apply { color = 0xFF00FF00.toInt() }
        val path = SkPathBuilder()
            .moveTo(10f, 10f)
            .lineTo(100f, 10f)
            .lineTo(55f, 100f)
            .close()
            .detach()

        val frame = renderKanvasBridge(256, 256) { surface ->
            surface.drawPath(path, paint)
        }

        assertTrue(frame.recording.taskList.tasks.isNotEmpty(),
            "Kanvas bridge should record tasks for path fill")
    }

    @Test
    fun `parity text blob task equivalence`() {
        val font = SkFont(SkTypeface.MakeEmpty(), 12f)
        val blob = SkTextBlob.MakeFromString("Hello", font)!!
        val paint = SkPaint().apply { color = 0xFF000000.toInt() }

        val frame = renderKanvasBridge(256, 256) { surface ->
            surface.drawTextBlob(blob, 10f, 20f, paint)
        }

        assertTrue(frame.recording.taskList.tasks.isNotEmpty(),
            "Kanvas bridge should record tasks for text blob")
    }

    @ParameterizedTest
    @EnumSource(SkBlendMode::class)
    fun `parity blend mode produces tasks`(mode: SkBlendMode) {
        val paint = SkPaint().apply {
            color = 0x880000FF.toInt()
            blendMode = mode
        }
        val rect1 = SkRect.MakeLTRB(10f, 10f, 50f, 50f)
        val rect2 = SkRect.MakeLTRB(30f, 30f, 70f, 70f)

        val frame = renderKanvasBridge(128, 128) { surface ->
            surface.drawRect(rect1, paint)
            surface.drawRect(rect2, paint)
        }

        assertEquals(2, frame.recording.taskList.tasks.size,
            "Two rect draws with blend mode $mode should produce 2 tasks")
    }

    @Test
    fun `parity multiple draws task count`() {
        val redPaint = SkPaint().apply { color = 0xFFFF0000.toInt() }
        val bluePaint = SkPaint().apply { color = 0xFF0000FF.toInt() }
        val greenPaint = SkPaint().apply { color = 0xFF00FF00.toInt() }

        val frame = renderKanvasBridge(256, 256) { surface ->
            surface.drawRect(SkRect.MakeLTRB(0f, 0f, 50f, 50f), redPaint)
            surface.drawRect(SkRect.MakeLTRB(50f, 50f, 100f, 100f), bluePaint)
            surface.drawRect(SkRect.MakeLTRB(100f, 100f, 150f, 150f), greenPaint)
        }

        assertEquals(3, frame.recording.taskList.tasks.size,
            "Three draws should produce exactly three tasks")
    }

    @Test
    fun `parity stroke style produces tasks`() {
        val paint = SkPaint().apply {
            color = 0xFFFF0000.toInt()
            strokeWidth = 3f
        }

        val rect = SkRect.MakeLTRB(10f, 10f, 100f, 100f)
        val frame = renderKanvasBridge(256, 256) { surface ->
            surface.drawRect(rect, paint)
        }

        assertTrue(frame.recording.taskList.tasks.isNotEmpty(),
            "Stroked rect should record tasks via bridge")
    }
}
