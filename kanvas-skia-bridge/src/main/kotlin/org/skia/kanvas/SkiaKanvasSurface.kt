package org.skia.kanvas

import org.graphiks.kanvas.Canvas
import org.graphiks.kanvas.Frame
import org.graphiks.kanvas.PixelFormat
import org.graphiks.kanvas.Surface
import org.skia.core.SkSurface
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.foundation.SkTextBlob
import org.graphiks.math.SkRect

@Volatile
private var activationDiagnosticEmitted = false

fun isKanvasRendererEnabled(): Boolean =
    !RollbackConfig.useLegacyGpuRaster

internal fun emitRouteMigratedDiagnostic() {
    if (!activationDiagnosticEmitted) {
        activationDiagnosticEmitted = true
        emitBridgeDiagnostic(
            code = "route-migrated-to-kanvas",
            message = "SkSurface rendering routed through Kanvas native pipeline (SkiaKanvasSurface). " +
                "Set -Dkanvas.rollback.legacy-gpu-raster=true for emergency rollback to gpu-raster.",
        )
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

    fun flush(): Frame = kanvasSurface.flush()

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
