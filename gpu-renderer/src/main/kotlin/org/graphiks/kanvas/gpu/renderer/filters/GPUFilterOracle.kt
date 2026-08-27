package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.math.ceil
import kotlin.math.exp

data class Rgba8Bitmap(val width: Int, val height: Int, val pixels: FloatArray) {
    init {
        require(width > 0 && height > 0) { "Rgba8Bitmap dimensions must be positive" }
        require(pixels.size == width * height * 4) {
            "Rgba8Bitmap: pixels.size=${pixels.size} != $width * $height * 4"
        }
    }

    fun getPixel(x: Int, y: Int, rgba: FloatArray = FloatArray(4)): FloatArray {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            rgba.fill(0f)
            return rgba
        }
        val idx = (y * width + x) * 4
        rgba[0] = pixels[idx]
        rgba[1] = pixels[idx + 1]
        rgba[2] = pixels[idx + 2]
        rgba[3] = pixels[idx + 3]
        return rgba
    }

    fun setPixel(x: Int, y: Int, r: Float, g: Float, b: Float, a: Float) {
        if (x < 0 || x >= width || y < 0 || y >= height) return
        val idx = (y * width + x) * 4
        pixels[idx] = r.coerceIn(0f, 1f)
        pixels[idx + 1] = g.coerceIn(0f, 1f)
        pixels[idx + 2] = b.coerceIn(0f, 1f)
        pixels[idx + 3] = a.coerceIn(0f, 1f)
    }

    fun copy(): Rgba8Bitmap = Rgba8Bitmap(width, height, pixels.copyOf())
}

object GPUFilterOracle {

    fun apply(
        source: Rgba8Bitmap,
        filter: GPUPreparedFilterNode,
        inputs: Map<GPUPreparedFilterNodeId, Rgba8Bitmap>,
    ): Rgba8Bitmap {
        return when (filter.kind) {
            GPUPreparedFilterKind.Blur -> {
                val params = filter.parameters as BlurParams
                applyBlur(source, params.sigmaX, params.sigmaY, params.tileMode)
            }
            GPUPreparedFilterKind.ColorFilter -> {
                val params = filter.parameters as ColorFilterParams
                applyColorFilter(source, params.descriptor)
            }
            GPUPreparedFilterKind.Offset -> {
                val params = filter.parameters as OffsetParams
                applyOffset(source, params.dx, params.dy)
            }
            GPUPreparedFilterKind.Crop -> {
                val params = filter.parameters as CropParams
                applyCrop(source, params.x, params.y, params.width, params.height)
            }
            GPUPreparedFilterKind.DropShadow -> {
                val params = filter.parameters as DropShadowParams
                applyDropShadow(source, params, inputs)
            }
            else -> error("Filter ${filter.kind} not supported in GPUFilterOracle")
        }
    }

    // --- Blur ---

    private fun applyBlur(
        source: Rgba8Bitmap,
        sigmaX: Float,
        sigmaY: Float,
        tileMode: GPUTileMode,
    ): Rgba8Bitmap {
        val intermediate = convolveSeparable(source, sigmaX, horizontal = true, tileMode)
        return convolveSeparable(intermediate, sigmaY, horizontal = false, tileMode)
    }

    private fun convolveSeparable(
        source: Rgba8Bitmap,
        sigma: Float,
        horizontal: Boolean,
        tileMode: GPUTileMode,
    ): Rgba8Bitmap {
        if (sigma <= 0f) return source.copy()

        val kernel = gaussianKernel(sigma)
        val radius = kernel.size / 2
        val dst = Rgba8Bitmap(source.width, source.height, FloatArray(source.width * source.height * 4))

        if (horizontal) {
            for (y in 0 until source.height) {
                for (x in 0 until source.width) {
                    var r = 0f; var g = 0f; var b = 0f; var a = 0f
                    for (k in kernel.indices) {
                        val sx = clampCoordOrNull(x + k - radius, 0, source.width - 1, tileMode) ?: continue
                        val idx = (y * source.width + sx) * 4
                        val w = kernel[k]
                        r += source.pixels[idx] * w
                        g += source.pixels[idx + 1] * w
                        b += source.pixels[idx + 2] * w
                        a += source.pixels[idx + 3] * w
                    }
                    val di = (y * source.width + x) * 4
                    dst.pixels[di] = r
                    dst.pixels[di + 1] = g
                    dst.pixels[di + 2] = b
                    dst.pixels[di + 3] = a
                }
            }
        } else {
            for (y in 0 until source.height) {
                for (x in 0 until source.width) {
                    var r = 0f; var g = 0f; var b = 0f; var a = 0f
                    for (k in kernel.indices) {
                        val sy = clampCoordOrNull(y + k - radius, 0, source.height - 1, tileMode) ?: continue
                        val idx = (sy * source.width + x) * 4
                        val w = kernel[k]
                        r += source.pixels[idx] * w
                        g += source.pixels[idx + 1] * w
                        b += source.pixels[idx + 2] * w
                        a += source.pixels[idx + 3] * w
                    }
                    val di = (y * source.width + x) * 4
                    dst.pixels[di] = r
                    dst.pixels[di + 1] = g
                    dst.pixels[di + 2] = b
                    dst.pixels[di + 3] = a
                }
            }
        }
        return dst
    }

    /**
     * Maps a sample coordinate into [lo, hi] per [tileMode]. Returns null when the
     * coordinate falls outside the source for [GPUTileMode.Decal] — the caller skips
     * the tap, so out-of-bounds pixels contribute nothing (transparent outside the
     * source). Skipped taps are intentionally NOT renormalized: Decal borders fade
     * out as the kernel loses weight.
     */
    private fun clampCoordOrNull(v: Int, lo: Int, hi: Int, tileMode: GPUTileMode): Int? = when (tileMode) {
        GPUTileMode.Clamp -> v.coerceIn(lo, hi)
        GPUTileMode.Repeat -> {
            val range = hi - lo + 1
            lo + ((v - lo) % range + range) % range
        }
        GPUTileMode.Mirror -> {
            val range = hi - lo + 1
            if (range <= 1) lo
            else {
                val period = 2 * range - 2
                val t = ((v - lo) % period + period) % period
                lo + if (t < range) t else period - t
            }
        }
        GPUTileMode.Decal -> if (v in lo..hi) v else null
    }

    private fun gaussianKernel(sigma: Float): FloatArray {
        if (sigma <= 0f) return floatArrayOf(1f)
        val radius = ceil(sigma * 3f).toInt().coerceAtLeast(1)
        val size = 2 * radius + 1
        val weights = FloatArray(size) { i ->
            val x = (i - radius).toFloat()
            exp(-(x * x) / (2f * sigma * sigma))
        }
        val sum = weights.sum()
        if (sum > 0f) {
            for (i in weights.indices) weights[i] /= sum
        }
        return weights
    }

    // --- ColorFilter ---

    /**
     * Applies the same bounded descriptor contract as the registered native
     * ColorMatrix route: straight encoded sRGB -> linear matrix -> encoded
     * premultiplied attachment pixels. No direct-matrix oracle is retained.
     */
    private fun applyColorFilter(
        source: Rgba8Bitmap,
        descriptor: SrgbMatrixColorFilterDescriptor,
    ): Rgba8Bitmap {
        val filter = SrgbMatrixColorFilter(descriptor)
        val dst = Rgba8Bitmap(source.width, source.height, FloatArray(source.width * source.height * 4))
        for (i in source.pixels.indices step 4) {
            val filtered = filter.applyEncodedStraightRgba(
                source.pixels[i],
                source.pixels[i + 1],
                source.pixels[i + 2],
                source.pixels[i + 3],
            )
            filtered.copyInto(dst.pixels, destinationOffset = i)
        }
        return dst
    }

    // --- Offset ---

    private fun applyOffset(source: Rgba8Bitmap, dx: Float, dy: Float): Rgba8Bitmap {
        val idx = dx.toInt()
        val idy = dy.toInt()
        if (idx == 0 && idy == 0) return source.copy()

        val newW = source.width + kotlin.math.abs(idx)
        val newH = source.height + kotlin.math.abs(idy)
        val dst = Rgba8Bitmap(newW, newH, FloatArray(newW * newH * 4))

        val offsetX = kotlin.math.abs(idx)
        val offsetY = kotlin.math.abs(idy)

        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val sp = (y * source.width + x) * 4
                val dp = ((y + offsetY) * newW + (x + offsetX)) * 4
                for (c in 0..3) dst.pixels[dp + c] = source.pixels[sp + c]
            }
        }
        return dst
    }

    // --- Crop ---

    private fun applyCrop(source: Rgba8Bitmap, x: Float, y: Float, w: Float, h: Float): Rgba8Bitmap {
        val cropW = w.toInt().coerceIn(1, source.width)
        val cropH = h.toInt().coerceIn(1, source.height)
        val cropX = x.toInt().coerceIn(0, source.width - 1)
        val cropY = y.toInt().coerceIn(0, source.height - 1)

        val safeW = minOf(cropW, source.width - cropX)
        val safeH = minOf(cropH, source.height - cropY)

        val dst = Rgba8Bitmap(safeW, safeH, FloatArray(safeW * safeH * 4))
        for (dy in 0 until safeH) {
            for (dx in 0 until safeW) {
                val sp = ((cropY + dy) * source.width + (cropX + dx)) * 4
                val dp = (dy * safeW + dx) * 4
                for (c in 0..3) dst.pixels[dp + c] = source.pixels[sp + c]
            }
        }
        return dst
    }

    // --- DropShadow ---

    private fun applyDropShadow(
        source: Rgba8Bitmap,
        params: DropShadowParams,
        inputs: Map<GPUPreparedFilterNodeId, Rgba8Bitmap>,
    ): Rgba8Bitmap {
        val shadowColor = params.color
        val dx = params.dx
        val dy = params.dy
        val sigmaX = params.sigmaX
        val sigmaY = params.sigmaY

        // 1. Build shadow mask from source alpha, tinted with shadow color
        val shadow = Rgba8Bitmap(source.width, source.height, FloatArray(source.width * source.height * 4))
        for (i in source.pixels.indices step 4) {
            val srcAlpha = source.pixels[i + 3]
            shadow.pixels[i] = shadowColor[0] * srcAlpha
            shadow.pixels[i + 1] = shadowColor[1] * srcAlpha
            shadow.pixels[i + 2] = shadowColor[2] * srcAlpha
            shadow.pixels[i + 3] = shadowColor[3] * srcAlpha
        }

        // 2. Blur the shadow
        val blurredShadow = applyBlur(shadow, sigmaX, sigmaY, GPUTileMode.Clamp)

        // 3. Offset the blurred shadow
        val offsetShadow = if (dx != 0f || dy != 0f) {
            applyOffset(blurredShadow, dx, dy)
        } else {
            blurredShadow
        }

        // 4. Compute output bounds (union of source and offset shadow)
        val dxInt = dx.toInt()
        val dyInt = dy.toInt()
        val srcLeft = maxOf(0, -dxInt)
        val srcTop = maxOf(0, -dyInt)
        val shadowLeft = minOf(0, dxInt)
        val shadowTop = minOf(0, dyInt)

        val outW = source.width + kotlin.math.abs(dxInt)
        val outH = source.height + kotlin.math.abs(dyInt)

        val dst = Rgba8Bitmap(outW, outH, FloatArray(outW * outH * 4))
        val rgba = FloatArray(4)

        // Render shadow first
        for (y in 0 until offsetShadow.height) {
            for (x in 0 until offsetShadow.width) {
                val ox = shadowLeft + x
                val oy = shadowTop + y
                if (ox >= 0 && oy >= 0 && ox < outW && oy < outH) {
                    offsetShadow.getPixel(x, y, rgba)
                    val dp = (oy * outW + ox) * 4
                    dst.pixels[dp] = rgba[0]
                    dst.pixels[dp + 1] = rgba[1]
                    dst.pixels[dp + 2] = rgba[2]
                    dst.pixels[dp + 3] = rgba[3]
                }
            }
        }

        // Render source over the shadow
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                source.getPixel(x, y, rgba)
                val ox = srcLeft + x
                val oy = srcTop + y
                val dp = (oy * outW + ox) * 4
                val srcR = rgba[0]
                val srcG = rgba[1]
                val srcB = rgba[2]
                val srcA = rgba[3]
                val oneMinusSrcA = 1f - srcA
                // source-over compositing
                dst.pixels[dp] = srcR + oneMinusSrcA * dst.pixels[dp]
                dst.pixels[dp + 1] = srcG + oneMinusSrcA * dst.pixels[dp + 1]
                dst.pixels[dp + 2] = srcB + oneMinusSrcA * dst.pixels[dp + 2]
                dst.pixels[dp + 3] = srcA + oneMinusSrcA * dst.pixels[dp + 3]
            }
        }

        return dst
    }
}
