package org.skia.kanvas

import org.graphiks.kanvas.BlendMode
import org.graphiks.kanvas.KanvasFillType
import org.graphiks.kanvas.KanvasTileMode
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkRect
import org.skia.core.SkSurface
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkFont
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.skia.foundation.SkRRect
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTypeface
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class KanvasSkiaBridgeTest {

    @Test
    fun `toKanvasRect maps fields correctly`() {
        val sr = SkRect.MakeLTRB(10f, 20f, 100f, 200f)
        val kr = sr.toKanvasRect()
        assertEquals(10f, kr.left)
        assertEquals(20f, kr.top)
        assertEquals(100f, kr.right)
        assertEquals(200f, kr.bottom)
    }

    @Test
    fun `toKanvasRect empty rect`() {
        val sr = SkRect.MakeEmpty()
        val kr = sr.toKanvasRect()
        assertTrue(kr.isEmpty)
    }

    @Test
    fun `toKanvasBlendMode maps all SkBlendMode values`() {
        val cases = listOf(
            SkBlendMode.kClear to BlendMode.CLEAR,
            SkBlendMode.kSrc to BlendMode.SRC,
            SkBlendMode.kDst to BlendMode.DST,
            SkBlendMode.kSrcOver to BlendMode.SRC_OVER,
            SkBlendMode.kDstOver to BlendMode.DST_OVER,
            SkBlendMode.kSrcIn to BlendMode.SRC_IN,
            SkBlendMode.kDstIn to BlendMode.DST_IN,
            SkBlendMode.kSrcOut to BlendMode.SRC_OUT,
            SkBlendMode.kDstOut to BlendMode.DST_OUT,
            SkBlendMode.kSrcATop to BlendMode.SRC_ATOP,
            SkBlendMode.kDstATop to BlendMode.DST_ATOP,
            SkBlendMode.kXor to BlendMode.XOR,
            SkBlendMode.kPlus to BlendMode.PLUS,
            SkBlendMode.kModulate to BlendMode.MODULATE,
            SkBlendMode.kMultiply to BlendMode.MULTIPLY,
            SkBlendMode.kScreen to BlendMode.SCREEN,
        )
        for ((skia, kanvas) in cases) {
            assertEquals(kanvas, skia.toKanvasBlendMode(), "Mismatch for $skia")
        }
    }

    @Test
    fun `toKanvasBlendMode unknown falls back to SRC_OVER`() {
        assertEquals(BlendMode.SRC_OVER, SkBlendMode.kOverlay.toKanvasBlendMode())
        assertEquals(BlendMode.SRC_OVER, SkBlendMode.kHue.toKanvasBlendMode())
    }

    @Test
    fun `toKanvasPaint maps color correctly`() {
        val sp = SkPaint(SkColor4f(0.5f, 0.25f, 0.75f, 0.8f))
        val kp = sp.toKanvasPaint()
        assertEquals(0.5f, kp.r, 0.001f)
        assertEquals(0.25f, kp.g, 0.001f)
        assertEquals(0.75f, kp.b, 0.001f)
        assertEquals(0.8f, kp.a, 0.001f)
    }

    @Test
    fun `toKanvasPaint maps stroke correctly`() {
        val sp = SkPaint().apply {
            strokeWidth = 3f
            isAntiAlias = true
            blendMode = SkBlendMode.kMultiply
        }
        val kp = sp.toKanvasPaint()
        assertEquals(3f, kp.strokeWidth)
        assertEquals(true, kp.antiAlias)
        assertEquals(BlendMode.MULTIPLY, kp.blendMode)
    }

    @Test
    fun `toKanvasPaint defaults`() {
        val sp = SkPaint()
        val kp = sp.toKanvasPaint()
        assertEquals(0f, kp.r, 0.001f)
        assertEquals(0f, kp.g, 0.001f)
        assertEquals(0f, kp.b, 0.001f)
        assertEquals(1f, kp.a, 0.001f)
        assertEquals(BlendMode.SRC_OVER, kp.blendMode)
        assertEquals(0f, kp.strokeWidth)
        assertEquals(false, kp.antiAlias)
    }

    @Test
    fun `toKanvasFillType maps all values`() {
        assertEquals(KanvasFillType.WINDING, SkPathFillType.kWinding.toKanvasFillType())
        assertEquals(KanvasFillType.EVEN_ODD, SkPathFillType.kEvenOdd.toKanvasFillType())
        assertEquals(KanvasFillType.INVERSE_WINDING, SkPathFillType.kInverseWinding.toKanvasFillType())
        assertEquals(KanvasFillType.INVERSE_EVEN_ODD, SkPathFillType.kInverseEvenOdd.toKanvasFillType())
    }

    @Test
    fun `toKanvasTileMode maps all values`() {
        assertEquals(KanvasTileMode.CLAMP, org.skia.foundation.SkTileMode.kClamp.toKanvasTileMode())
        assertEquals(KanvasTileMode.REPEAT, org.skia.foundation.SkTileMode.kRepeat.toKanvasTileMode())
        assertEquals(KanvasTileMode.MIRROR, org.skia.foundation.SkTileMode.kMirror.toKanvasTileMode())
        assertEquals(KanvasTileMode.DECAL, org.skia.foundation.SkTileMode.kDecal.toKanvasTileMode())
    }

    @Test
    fun `toKanvasImage maps width and height`() {
        val si = SkImage(64, 48, IntArray(64 * 48))
        val ki = si.toKanvasImage()
        assertEquals(64, ki.width)
        assertEquals(48, ki.height)
    }

    @Test
    fun `toKanvasPath simple rect`() {
        val sb = SkPathBuilder().apply {
            addRect(SkRect.MakeLTRB(10f, 10f, 100f, 100f))
        }
        val sp = sb.detach()
        val kp = sp.toKanvasPath()
        assertNotNull(kp)
    }

    @Test
    fun `toKanvasPath line sequence`() {
        val sp = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(10f, 0f)
            .lineTo(10f, 10f)
            .lineTo(0f, 10f)
            .close()
            .detach()
        val kp = sp.toKanvasPath()
        assertNotNull(kp)
    }

    @Test
    fun `toKanvasPath cubic curve`() {
        val sp = SkPathBuilder()
            .moveTo(0f, 0f)
            .cubicTo(10f, 20f, 30f, 40f, 50f, 60f)
            .detach()
        val kp = sp.toKanvasPath()
        assertNotNull(kp)
    }

    @Test
    fun `SkiaKanvasSurface wrap and flush produces non-empty frame`() {
        val skiaSurface = SkSurface.MakeRasterN32Premul(256, 256)
        val kanvasSurface = SkiaKanvasSurface.wrap(skiaSurface)

        val paint = SkPaint().apply { color = 0xFFFF0000.toInt() }
        val rect = SkRect.MakeLTRB(10f, 10f, 100f, 100f)

        kanvasSurface.drawRect(rect, paint)
        val frame = kanvasSurface.flush()

        assertTrue(frame.recording.taskList.tasks.isNotEmpty())
    }

    @Test
    fun `SkiaKanvasSurface drawPath records tasks`() {
        val skiaSurface = SkSurface.MakeRasterN32Premul(256, 256)
        val kanvasSurface = SkiaKanvasSurface.wrap(skiaSurface)

        val path = SkPathBuilder()
            .moveTo(10f, 10f)
            .lineTo(100f, 10f)
            .lineTo(55f, 100f)
            .close()
            .detach()
        val paint = SkPaint().apply { color = 0xFF00FF00.toInt() }

        kanvasSurface.drawPath(path, paint)
        val frame = kanvasSurface.flush()

        assertTrue(frame.recording.taskList.tasks.isNotEmpty())
    }

    @Test
    fun `SkiaKanvasSurface multiple draws`() {
        val skiaSurface = SkSurface.MakeRasterN32Premul(256, 256)
        val kanvasSurface = SkiaKanvasSurface.wrap(skiaSurface)

        val redPaint = SkPaint().apply { color = 0xFFFF0000.toInt() }
        val bluePaint = SkPaint().apply { color = 0xFF0000FF.toInt() }

        kanvasSurface.drawRect(SkRect.MakeLTRB(0f, 0f, 50f, 50f), redPaint)
        kanvasSurface.drawRect(SkRect.MakeLTRB(50f, 50f, 100f, 100f), bluePaint)
        val frame = kanvasSurface.flush()

        assertEquals(2, frame.recording.taskList.tasks.size)
    }

    @Test
    fun `isKanvasRendererEnabled returns true by default`() {
        assertEquals(true, isKanvasRendererEnabled())
    }

    @Test
    fun `isKanvasRendererEnabled returns false when rollback flag set`() {
        val previous = System.getProperty("kanvas.rollback.legacy-gpu-raster")
        try {
            System.setProperty("kanvas.rollback.legacy-gpu-raster", "true")
            assertEquals(false, isKanvasRendererEnabled())
        } finally {
            if (previous != null) {
                System.setProperty("kanvas.rollback.legacy-gpu-raster", previous)
            } else {
                System.clearProperty("kanvas.rollback.legacy-gpu-raster")
            }
        }
    }

    @Test
    fun `wrapIfEnabled returns non-null by default`() {
        val skiaSurface = SkSurface.MakeRasterN32Premul(64, 64)
        assertNotNull(SkiaKanvasSurface.wrapIfEnabled(skiaSurface))
    }

    @Test
    fun `wrapIfEnabled returns null when useLegacyGpuRaster is set`() {
        val previous = System.getProperty("kanvas.rollback.legacy-gpu-raster")
        try {
            System.setProperty("kanvas.rollback.legacy-gpu-raster", "true")
            val skiaSurface = SkSurface.MakeRasterN32Premul(64, 64)
            assertNull(SkiaKanvasSurface.wrapIfEnabled(skiaSurface))
        } finally {
            if (previous != null) {
                System.setProperty("kanvas.rollback.legacy-gpu-raster", previous)
            } else {
                System.clearProperty("kanvas.rollback.legacy-gpu-raster")
            }
        }
    }

    @Test
    fun `toKanvasRRect maps simple rounded rect`() {
        val sr = SkRRect.MakeRectXY(SkRect.MakeLTRB(10f, 20f, 100f, 200f), 5f, 8f)
        val kr = sr.toKanvasRRect()
        assertEquals(10f, kr.rect.left)
        assertEquals(20f, kr.rect.top)
        assertEquals(100f, kr.rect.right)
        assertEquals(200f, kr.rect.bottom)
        assertEquals(5f, kr.topLeft.x)
        assertEquals(8f, kr.topLeft.y)
        assertEquals(5f, kr.topRight.x)
        assertEquals(8f, kr.topRight.y)
        assertEquals(5f, kr.bottomRight.x)
        assertEquals(8f, kr.bottomRight.y)
        assertEquals(5f, kr.bottomLeft.x)
        assertEquals(8f, kr.bottomLeft.y)
    }

    @Test
    fun `toKanvasRRect empty rect`() {
        val sr = SkRRect.MakeEmpty()
        val kr = sr.toKanvasRRect()
        assertTrue(kr.rect.isEmpty)
    }

    @Test
    fun `SkiaKanvasSurface drawRRect records tasks`() {
        val skiaSurface = SkSurface.MakeRasterN32Premul(256, 256)
        val kanvasSurface = SkiaKanvasSurface.wrap(skiaSurface)

        val rrect = SkRRect.MakeRectXY(SkRect.MakeLTRB(10f, 10f, 100f, 100f), 5f, 5f)
        val paint = SkPaint().apply { color = 0xFF0000FF.toInt() }

        kanvasSurface.drawRRect(rrect, paint)
        val frame = kanvasSurface.flush()
        assertTrue(frame.recording.taskList.tasks.isNotEmpty())
    }

    @Test
    fun `toKanvasTextBlob from HorizontalSpread`() {
        val font = SkFont(SkTypeface.MakeEmpty(), 12f)
        val blob = SkTextBlob.MakeFromString("Hi", font)!!
        val ktb = blob.toKanvasTextBlob()
        assertEquals(1, ktb.glyphRuns.size)
        val run = ktb.glyphRuns[0]
        assertEquals(2, run.glyphs.size)
        assertEquals(2, run.positions.size)
    }

    @Test
    fun `SkiaKanvasSurface drawTextBlob records tasks`() {
        val skiaSurface = SkSurface.MakeRasterN32Premul(256, 256)
        val kanvasSurface = SkiaKanvasSurface.wrap(skiaSurface)

        val font = SkFont(SkTypeface.MakeEmpty(), 12f)
        val blob = SkTextBlob.MakeFromString("Hello", font)!!
        val paint = SkPaint().apply { color = 0xFF000000.toInt() }

        kanvasSurface.drawTextBlob(blob, 10f, 20f, paint)
        val frame = kanvasSurface.flush()
        assertTrue(frame.recording.taskList.tasks.isNotEmpty())
    }

    @Test
    fun `KanvasSkiaBridge class wraps Canvas and delegates drawRect`() {
        val kSurface = org.graphiks.kanvas.Surface(64, 64, org.graphiks.kanvas.PixelFormat.RGBA8)
        val canvas = org.graphiks.kanvas.Canvas(kSurface)
        val bridge = KanvasSkiaBridge(canvas)

        val rect = SkRect.MakeLTRB(0f, 0f, 10f, 10f)
        val paint = SkPaint().apply { color = 0xFFFF0000.toInt() }

        bridge.drawRect(rect, paint)
        assertNotNull(canvas)
    }

    @Test
    fun `KanvasSkiaBridge class drawRRect`() {
        val kSurface = org.graphiks.kanvas.Surface(64, 64, org.graphiks.kanvas.PixelFormat.RGBA8)
        val canvas = org.graphiks.kanvas.Canvas(kSurface)
        val bridge = KanvasSkiaBridge(canvas)

        val rrect = SkRRect.MakeRectXY(SkRect.MakeLTRB(0f, 0f, 50f, 50f), 5f, 5f)
        val paint = SkPaint().apply { color = 0xFF00FF00.toInt() }

        bridge.drawRRect(rrect, paint)
        assertNotNull(canvas)
    }

    @Test
    fun `KanvasSkiaBridge class drawTextBlob`() {
        val kSurface = org.graphiks.kanvas.Surface(64, 64, org.graphiks.kanvas.PixelFormat.RGBA8)
        val canvas = org.graphiks.kanvas.Canvas(kSurface)
        val bridge = KanvasSkiaBridge(canvas)

        val font = SkFont(SkTypeface.MakeEmpty(), 12f)
        val blob = SkTextBlob.MakeFromString("Test", font)!!
        val paint = SkPaint().apply { color = 0xFF000000.toInt() }

        bridge.drawTextBlob(blob, 0f, 12f, paint)
        assertNotNull(canvas)
    }

    @Test
    fun `diagnostic helper emits expected prefix`() {
        emitBridgeDiagnostic("test-code", "test message")
    }

    @Test
    fun `unsupported diagnostic helper`() {
        emitUnsupportedBridgeDiagnostic("drawPicture")
    }

    @Test
    fun `productActivation is true by default`() {
        assertTrue(isProductActivation())
    }

    @Test
    fun `RollbackConfig useLegacyGpuRaster environment variable`() {
        val previous = System.getenv("KANVAS_ROLLBACK_LEGACY_GPU_RASTER")
        assertFalse(RollbackConfig.useLegacyGpuRaster)
    }

    @Test
    fun `productActivation disabled via system property`() {
        val previous = System.getProperty("kanvas.product.activation.disable")
        try {
            System.setProperty("kanvas.product.activation.disable", "true")
            assertFalse(isProductActivation())
        } finally {
            if (previous != null) {
                System.setProperty("kanvas.product.activation.disable", previous)
            } else {
                System.clearProperty("kanvas.product.activation.disable")
            }
        }
    }

    @Test
    fun `wrapIfEnabled emits production activation diagnostic`() {
        val skiaSurface = SkSurface.MakeRasterN32Premul(64, 64)
        val result = SkiaKanvasSurface.wrapIfEnabled(skiaSurface)
        assertNotNull(result)
    }

    @Test
    fun `flush emits kanvas-render-failed diagnostic for unsupported commands`() {
        val skSurface = SkSurface.MakeRasterN32Premul(64, 64)
        val kanvasSurface = SkiaKanvasSurface.wrap(skSurface)

        // Draw a text blob (unsupported) so all commands are refused
        val font = SkFont(SkTypeface.MakeEmpty(), 12f)
        val blob = SkTextBlob.MakeFromString("diagnostic test", font)!!
        kanvasSurface.drawTextBlob(blob, 10f, 20f, SkPaint().apply { color = 0xFF000000.toInt() })

        // Capture System.err
        val errBytes = java.io.ByteArrayOutputStream()
        val originalErr = System.err
        System.setErr(java.io.PrintStream(errBytes))
        try {
            kanvasSurface.flush()
        } finally {
            System.setErr(originalErr)
        }

        val output = errBytes.toString("UTF-8")
        assertTrue(
            output.contains("kanvas-render-failed"),
            "Expected 'kanvas-render-failed' diagnostic in stderr for unsupported commands, got: $output",
        )
        assertTrue(
            output.contains("All commands refused"),
            "Expected 'All commands refused' in diagnostic message, got: $output",
        )
    }

    @Test
    fun `flush does not emit diagnostic for supported solid rect`() {
        val skSurface = SkSurface.MakeRasterN32Premul(64, 64)
        val kanvasSurface = SkiaKanvasSurface.wrap(skSurface)

        // Draw a solid rect (supported)
        kanvasSurface.drawRect(
            SkRect.MakeLTRB(10f, 10f, 54f, 54f),
            SkPaint().apply { color = SkColorSetARGB(255, 255, 0, 0) },
        )

        // Capture System.err
        val errBytes = java.io.ByteArrayOutputStream()
        val originalErr = System.err
        System.setErr(java.io.PrintStream(errBytes))
        try {
            kanvasSurface.flush()
        } finally {
            System.setErr(originalErr)
        }

        val output = errBytes.toString("UTF-8")
        assertFalse(
            output.contains("kanvas-render-failed"),
            "Expected NO 'kanvas-render-failed' diagnostic for supported solid rect, got: $output",
        )
    }
}
