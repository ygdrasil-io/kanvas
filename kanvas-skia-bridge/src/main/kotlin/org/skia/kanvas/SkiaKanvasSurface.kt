package org.skia.kanvas

import org.graphiks.kanvas.Canvas
import org.graphiks.kanvas.Frame
import org.graphiks.kanvas.PixelFormat
import org.graphiks.kanvas.Surface
import org.skia.core.SkSurface
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.graphiks.math.SkRect

private const val PROPERTY_KEY = "kanvas.renderer"
private const val NATIVE_MODE = "native"

fun isKanvasRendererEnabled(): Boolean =
    System.getProperty(PROPERTY_KEY) == NATIVE_MODE

class SkiaKanvasSurface internal constructor(
    val skSurface: SkSurface,
) {
    val kanvasSurface: Surface = Surface(
        width = skSurface.width,
        height = skSurface.height,
        format = PixelFormat.RGBA8,
    )

    val kanvasCanvas: Canvas = Canvas(kanvasSurface)

    fun drawRect(rect: SkRect, paint: SkPaint) {
        kanvasCanvas.drawRect(rect.toKanvasRect(), paint.toKanvasPaint())
    }

    fun drawPath(path: SkPath, paint: SkPaint) {
        kanvasCanvas.drawPath(path.toKanvasPath(), paint.toKanvasPaint())
    }

    fun drawImage(image: SkImage, rect: SkRect, paint: SkPaint?) {
        kanvasCanvas.drawImage(
            image = image.toKanvasImage(),
            rect = rect.toKanvasRect(),
            paint = paint?.toKanvasPaint(),
        )
    }

    fun flush(): Frame = kanvasSurface.flush()

    companion object {
        @JvmStatic
        fun wrap(skSurface: SkSurface): SkiaKanvasSurface =
            SkiaKanvasSurface(skSurface)

        @JvmStatic
        fun wrapIfEnabled(skSurface: SkSurface): SkiaKanvasSurface? =
            if (isKanvasRendererEnabled()) wrap(skSurface) else null
    }
}
