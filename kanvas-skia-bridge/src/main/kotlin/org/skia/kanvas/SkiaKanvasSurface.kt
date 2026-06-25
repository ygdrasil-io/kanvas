package org.skia.kanvas

import org.graphiks.kanvas.Canvas
import org.graphiks.kanvas.Frame
import org.graphiks.kanvas.PixelFormat
import org.graphiks.kanvas.Surface
import org.graphiks.kanvas.SurfaceRenderResult
import org.skia.core.SkSurface
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.foundation.SkTextBlob
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkRect

@Volatile
private var activationDiagnosticEmitted = false

fun isKanvasRendererEnabled(): Boolean =
    !RollbackConfig.useLegacyGpuRaster

fun isProductActivation(): Boolean =
    RollbackConfig.productActivation

internal fun emitRouteMigratedDiagnostic() {
    if (!activationDiagnosticEmitted) {
        activationDiagnosticEmitted = true
        emitBridgeDiagnostic(
            code = "route-migrated-to-kanvas",
            message = "SkSurface rendering routed through Kanvas native pipeline (SkiaKanvasSurface). " +
                "Set -Dkanvas.rollback.legacy-gpu-raster=true for emergency rollback to gpu-raster.",
        )
        if (isProductActivation()) {
            emitBridgeDiagnostic(
                code = "renderer-activated-kanvas-production",
                message = "Kanvas native pipeline is the production default renderer. " +
                    "product_activation=true. " +
                    "Set -Dkanvas.product.activation.disable=true to disable.",
            )
        }
    }
}

class SkiaKanvasSurface internal constructor(
    val skSurface: SkSurface,
) {
    val kanvasSurface: Surface = Surface(
        width = skSurface.width,
        height = skSurface.height,
        format = PixelFormat.RGBA8,
    )

    val kanvasCanvas: Canvas = Canvas(kanvasSurface)

    val bridge: KanvasSkiaBridge = KanvasSkiaBridge(kanvasCanvas)

    fun drawRect(rect: SkRect, paint: SkPaint) {
        bridge.drawRect(rect, paint)
    }

    fun drawRRect(rrect: SkRRect, paint: SkPaint) {
        bridge.drawRRect(rrect, paint)
    }

    fun drawPath(path: SkPath, paint: SkPaint) {
        bridge.drawPath(path, paint)
    }

    fun drawImage(image: SkImage, rect: SkRect, paint: SkPaint?) {
        bridge.drawImage(image, rect, paint)
    }

    fun drawTextBlob(blob: SkTextBlob, x: Float, y: Float, paint: SkPaint) {
        bridge.drawTextBlob(blob, x, y, paint)
    }

    fun flush(): Frame {
        val recording = kanvasSurface.flush()
        if (isKanvasRendererEnabled() && !recording.isEmpty) {
            runCatching {
                val result = kanvasSurface.renderToRgba()
                writeToSkSurface(result.rgba)
            }.onFailure { /* GPU unavailable or all commands refused — SkSurface stays blank */ }
        }
        return recording
    }

    fun renderToRgba(): SurfaceRenderResult = kanvasSurface.renderToRgba()

    fun flushAndRenderToSkSurface(): SurfaceRenderResult {
        val result = renderToRgba()
        writeToSkSurface(result.rgba)
        return result
    }

    private fun writeToSkSurface(rgba: ByteArray) {
        val pixelCount = skSurface.width * skSurface.height
        require(rgba.size == pixelCount * 4) {
            "RGBA buffer size mismatch: expected ${pixelCount * 4}, got ${rgba.size}"
        }
        val argb = IntArray(pixelCount)
        for (i in 0 until pixelCount) {
            val base = i * 4
            val r = rgba[base].toInt() and 0xFF
            val g = rgba[base + 1].toInt() and 0xFF
            val b = rgba[base + 2].toInt() and 0xFF
            val a = rgba[base + 3].toInt() and 0xFF
            argb[i] = SkColorSetARGB(a, r, g, b)
        }
        val src = SkBitmap(skSurface.width, skSurface.height)
        System.arraycopy(argb, 0, src.pixels8888, 0, argb.size)
        skSurface.canvas.writePixels(src, 0, 0)
    }

    companion object {
        @JvmStatic
        fun wrap(skSurface: SkSurface): SkiaKanvasSurface =
            SkiaKanvasSurface(skSurface)

        @JvmStatic
        fun wrapIfEnabled(skSurface: SkSurface): SkiaKanvasSurface? {
            if (!isKanvasRendererEnabled()) return null
            emitRouteMigratedDiagnostic()
            return wrap(skSurface)
        }
    }
}
