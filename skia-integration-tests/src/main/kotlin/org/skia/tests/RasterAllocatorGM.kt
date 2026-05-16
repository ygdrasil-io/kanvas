package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorGREEN
import org.skia.math.SK_ColorRED
import org.skia.math.SK_ColorWHITE
import org.skia.math.SkColor
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRasterHandleAllocator
import org.skia.math.SkISize
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/rasterhandleallocator.cpp::DEF_SIMPLE_GM(rasterallocator)`
 * (600 × 300).
 *
 * The upstream GM exercises [SkRasterHandleAllocator] indirectly : it
 * defines a `GraphicsPort` abstraction (with a Skia-only and a CG /
 * GDI-backed concrete implementation depending on the build platform)
 * that each `drawRect` / `saveLayer` / `clip` call delegates to. Both
 * ports render the same scene (a red square + a blue inset square + a
 * white oval + a 50%-alpha green save-layered square + a small
 * grey rect clipped to a vertical strip) into an
 * [SkRasterHandleAllocator]-allocated canvas. Only the
 * "platform-native" port path is exercised on the GM canvas — the
 * resulting bitmap is blitted to `(280, 0)` of the GM canvas.
 *
 * **Kanvas-skia port** : we keep the [GraphicsPort] indirection (so the
 * structure mirrors the upstream control flow) but ship only the
 * `SkiaGraphicsPort` flavour — kanvas-skia is raster-only and has no
 * CG / GDI handle. The result is byte-identical to upstream's
 * `__APPLE__`-disabled / `_WIN32`-disabled raster fallback (`MyPort =
 * SkiaGraphicsPort`).
 *
 * C++ source : see `gm/rasterhandleallocator.cpp`. Reference:
 * `rasterallocator.png`.
 */
public class RasterAllocatorGM : GM() {

    override fun getName(): String = "rasterallocator"

    override fun getISize(): SkISize = SkISize.Make(600, 300)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val info = SkImageInfo.MakeN32Premul(256, 256)
        val nativeCanvas = SkRasterHandleAllocator.MakeCanvas(SkiaAllocator(), info) ?: return

        val nativePort = SkiaGraphicsPort(nativeCanvas)
        doDraw(nativePort)

        // Blit the allocator-backed canvas into our top-level GM canvas.
        c.drawImage(nativeCanvas.bitmap.asImage(), 280f, 0f)
    }

    /**
     * Mirrors the lambda body in the C++ GM (`rasterhandleallocator.cpp:277`).
     * The upstream code uses `SkAutoCanvasRestore` to save/restore the
     * canvas state ; we expand that into explicit `save / restore`.
     */
    private fun doDraw(port: GraphicsPort) {
        val c = port.peekCanvas()
        val acr = c.save()

        port.drawRect(SkRect.MakeLTRB(0f, 0f, 256f, 256f), SK_ColorRED)
        port.save()
        port.translate(30f, 30f)
        port.drawRect(SkRect.MakeLTRB(0f, 0f, 30f, 30f), SK_ColorBLUE)
        port.drawOval(SkRect.MakeLTRB(10f, 10f, 20f, 20f), SK_ColorWHITE)
        port.restore()

        port.saveLayer(SkRect.MakeLTRB(50f, 50f, 100f, 100f), 0x80)
        port.drawRect(SkRect.MakeLTRB(55f, 55f, 95f, 95f), SK_ColorGREEN)
        port.restore()

        port.clip(SkRect.MakeLTRB(150f, 50f, 200f, 200f))
        port.drawRect(SkRect.MakeLTRB(0f, 0f, 256f, 256f), 0xFFCCCCCC.toInt())

        c.restoreToCount(acr)
    }

    /**
     * Bare-bones port of the upstream `GraphicsPort` interface
     * (`rasterhandleallocator.cpp:16`). All draw-and-state calls forward
     * through to a backing `SkCanvas` ; subclasses can intercept any
     * draw in a platform-native way.
     */
    private open class GraphicsPort(protected val canvas: SkCanvas) {
        fun peekCanvas(): SkCanvas = canvas

        fun save() { canvas.save() }
        fun saveLayer(bounds: SkRect, alpha: Int) {
            val p = SkPaint().apply { this.alpha = alpha }
            canvas.saveLayer(bounds, p)
        }
        fun restore() { canvas.restore() }

        fun translate(x: Float, y: Float) { canvas.translate(x, y) }
        fun clip(r: SkRect) { canvas.clipRect(r) }

        fun drawOval(r: SkRect, color: SkColor) {
            val p = SkPaint().apply { this.color = color }
            canvas.drawOval(r, p)
        }

        open fun drawRect(r: SkRect, color: SkColor) {
            val p = SkPaint().apply { this.color = color }
            canvas.drawRect(r, p)
        }
    }

    /**
     * Port of the upstream `SkiaGraphicsPort` (`rasterhandleallocator.cpp:49`).
     * The override pulls the top raster handle (`accessTopRasterHandle()`)
     * back out of the canvas — which in upstream Skia is a stashed
     * `SkCanvas*` from the allocator's `allocHandle`. Kanvas-skia's
     * raster-only allocator does not expose `accessTopRasterHandle`, so
     * the override falls through to the base class's `drawRect`.
     */
    private class SkiaGraphicsPort(c: SkCanvas) : GraphicsPort(c) {
        override fun drawRect(r: SkRect, color: SkColor) {
            // Upstream: `((SkCanvas*)fCanvas->accessTopRasterHandle())->drawRect(r, ...)`.
            // Kanvas-skia does not surface accessTopRasterHandle on its
            // raster allocator (R3.8 stub), so we just delegate.
            super.drawRect(r, color)
        }
    }

    /**
     * Stub allocator. The base layer is allocated by
     * [SkRasterHandleAllocator.MakeCanvas]'s fallback `SkBitmap.allocPixels`
     * path — `allocHandle` is left as a no-op (returns `false` so the
     * factory uses the fallback).
     */
    private class SkiaAllocator : SkRasterHandleAllocator() {
        override fun allocHandle(
            info: SkImageInfo,
            hndlMatrix: SkMatrix,
            rec: SkRasterHandleAllocator.Rec,
        ): Boolean = false

        override fun updateHandle(handle: Any, ctm: SkMatrix, clip: SkIRect) { /* no-op */ }
    }
}
