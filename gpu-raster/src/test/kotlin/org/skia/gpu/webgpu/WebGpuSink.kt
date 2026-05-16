package org.skia.gpu.webgpu

import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.tests.GM

/**
 * GPU equivalent of [org.skia.dm.RasterSinkF16] — runs a [GM] through
 * an [SkWebGpuDevice] sized to the GM's preferred size, then converts
 * the raw RGBA readback into an [SkBitmap] suitable for
 * [org.skia.testing.TestUtils.compareBitmapsDetailed] against the
 * reference PNG in `original-888/`.
 *
 * **Premul vs non-premul.** The GPU readback bytes are premultiplied
 * (consequence of the premul fragment output + SrcOver pipeline, see
 * G2.1 in [MIGRATION_PLAN_GPU_WEBGPU.md]). [SkBitmap] with
 * `kRGBA_8888` is non-premul by convention. For GMs that only use
 * opaque source colours (e.g. `ThinRectsGM`, `BigRectGM`,
 * `ClipStrokeRectGM`), `premul == non-premul` byte-for-byte, so a
 * direct copy is correct. GMs that paint translucent sources will
 * need a present-pass that divides by alpha — work scheduled for G6
 * alongside the linear-Rec.2020 working-space convergence.
 */
public object WebGpuSink {

    /**
     * Render [gm] through an [SkWebGpuDevice] backed by [context], then
     * return the resulting bitmap in `kRGBA_8888`. Caller owns the
     * context's lifecycle ; the device + its GPU resources are closed
     * before return.
     */
    public fun draw(context: WebGpuContext, gm: GM): SkBitmap {
        val size = gm.size()
        val w = size.width
        val h = size.height
        SkWebGpuDevice(context, w, h).use { device ->
            device.setBackground(gm.bgColor())
            val canvas = SkCanvas(device)
            gm.draw(canvas)
            val rgba = device.flush()
            return rgbaBytesToBitmap(rgba, w, h)
        }
    }

    /**
     * Pack a row-major RGBA byte stream (the output of
     * [SkWebGpuDevice.flush]) into an [SkBitmap.pixels8888] ARGB int
     * array. Layout convention :
     * - input bytes : `R, G, B, A` per pixel
     * - output ints : `0xAARRGGBB` per pixel
     */
    private fun rgbaBytesToBitmap(rgba: ByteArray, w: Int, h: Int): SkBitmap {
        require(rgba.size == w * h * 4) {
            "RGBA buffer size mismatch : expected ${w * h * 4} bytes for $w x $h, got ${rgba.size}"
        }
        val bitmap = SkBitmap(w, h, colorType = SkColorType.kRGBA_8888)
        for (i in 0 until w * h) {
            val base = i * 4
            val r = rgba[base].toInt() and 0xFF
            val g = rgba[base + 1].toInt() and 0xFF
            val b = rgba[base + 2].toInt() and 0xFF
            val a = rgba[base + 3].toInt() and 0xFF
            bitmap.pixels8888[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        return bitmap
    }
}
