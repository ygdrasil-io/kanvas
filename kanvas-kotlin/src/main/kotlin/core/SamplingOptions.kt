package com.kanvas.core

/**
 * Sampling options for image filtering and scaling
 * Inspired by Skia's SkSamplingOptions
 * 
 * @param filterMode The filtering mode to use (NEAREST, LINEAR, or CUBIC)
 * @param cubicResampler The cubic resampler to use when filterMode is CUBIC
 * @param useMipMaps Whether to use mipmapping for better quality at reduced sizes
 * @param dither Whether to apply dithering to reduce banding artifacts
 */
data class SamplingOptions(
    val filterMode: FilterMode = FilterMode.LINEAR,
    val cubicResampler: CubicResampler = CubicResampler.Mitchell,
    val useMipMaps: Boolean = false,
    val dither: Boolean = false
) {
    companion object {
        /**
         * Default sampling options with linear filtering
         */
        val DEFAULT = SamplingOptions()

        /**
         * Nearest neighbor sampling (fast, pixelated)
         */
        fun nearest(): SamplingOptions {
            return SamplingOptions(FilterMode.NEAREST)
        }

        /**
         * Linear sampling (smooth, basic quality)
         */
        fun linear(): SamplingOptions {
            return SamplingOptions(FilterMode.LINEAR)
        }

        /**
         * Cubic sampling with specific resampler
         */
        fun cubic(resampler: CubicResampler = CubicResampler.Mitchell): SamplingOptions {
            return SamplingOptions(FilterMode.CUBIC, resampler)
        }

        /**
         * Cubic sampling with Mitchell resampler (balanced quality)
         */
        fun mitchell(): SamplingOptions {
            return cubic(CubicResampler.Mitchell)
        }

        /**
         * Cubic sampling with Catmull-Rom resampler (sharper)
         */
        fun catmullRom(): SamplingOptions {
            return cubic(CubicResampler.CatmullRom)
        }

        /**
         * Cubic sampling with B-Spline resampler (smoother)
         */
        fun bSpline(): SamplingOptions {
            return cubic(CubicResampler.BSpline)
        }
    }

    /**
     * Apply sampling to a bitmap when scaling
     * @param bitmap Source bitmap
     * @param newWidth Target width
     * @param newHeight Target height
     * @return Scaled bitmap with applied sampling
     */
    fun applyToBitmap(bitmap: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
        return when (filterMode) {
            FilterMode.NEAREST -> scaleNearest(bitmap, newWidth, newHeight)
            FilterMode.LINEAR -> scaleLinear(bitmap, newWidth, newHeight)
            FilterMode.CUBIC -> scaleCubic(bitmap, newWidth, newHeight, cubicResampler)
        }
    }

    private fun scaleNearest(bitmap: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
        val result = Bitmap(newWidth, newHeight, bitmap.getConfig())
        
        val srcWidth = bitmap.getWidth()
        val srcHeight = bitmap.getHeight()
        
        for (y in 0 until newHeight) {
            for (x in 0 until newWidth) {
                // Nearest neighbor - simple rounding
                val srcX = (x * srcWidth / newWidth).coerceIn(0, srcWidth - 1)
                val srcY = (y * srcHeight / newHeight).coerceIn(0, srcHeight - 1)
                
                val srcColor = bitmap.getPixel(srcX, srcY)
                result.setPixel(x, y, srcColor)
            }
        }
        
        return result
    }

    private fun scaleLinear(bitmap: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
        val result = Bitmap(newWidth, newHeight, bitmap.getConfig())
        
        val srcWidth = bitmap.getWidth()
        val srcHeight = bitmap.getHeight()
        
        for (y in 0 until newHeight) {
            for (x in 0 until newWidth) {
                // Calculate source coordinates with fractional parts
                val srcX = x * (srcWidth - 1) / (newWidth - 1).toFloat()
                val srcY = y * (srcHeight - 1) / (newHeight - 1).toFloat()
                
                val x0 = srcX.toInt()
                val y0 = srcY.toInt()
                val x1 = minOf(x0 + 1, srcWidth - 1)
                val y1 = minOf(y0 + 1, srcHeight - 1)
                
                val dx = srcX - x0
                val dy = srcY - y0
                
                // Get the 4 surrounding pixels
                val c00 = bitmap.getPixel(x0, y0).toArgb()
                val c01 = bitmap.getPixel(x1, y0).toArgb()
                val c10 = bitmap.getPixel(x0, y1).toArgb()
                val c11 = bitmap.getPixel(x1, y1).toArgb()
                
                // Bilinear interpolation
                val interpolated = interpolateColors(
                    interpolateColors(c00, c01, dx),
                    interpolateColors(c10, c11, dx),
                    dy
                )
                
                result.setPixel(x, y, Color.fromArgb(interpolated))
            }
        }
        
        return result
    }

    private fun scaleCubic(bitmap: Bitmap, newWidth: Int, newHeight: Int, resampler: CubicResampler): Bitmap {
        val result = Bitmap(newWidth, newHeight, bitmap.getConfig())
        
        val srcWidth = bitmap.getWidth()
        val srcHeight = bitmap.getHeight()
        
        for (y in 0 until newHeight) {
            for (x in 0 until newWidth) {
                // Calculate source coordinates with fractional parts
                val srcX = x * (srcWidth - 1) / (newWidth - 1).toFloat()
                val srcY = y * (srcHeight - 1) / (newHeight - 1).toFloat()
                
                // Bicubic interpolation uses 4x4 neighborhood
                val x0 = maxOf(0, srcX.toInt() - 1)
                val y0 = maxOf(0, srcY.toInt() - 1)
                
                val weightsX = resampler.getWeights(srcX - x0)
                val weightsY = resampler.getWeights(srcY - y0)
                
                // Get the 16 surrounding pixels (4x4 grid)
                val colors = Array(4) { Array(4) { 0 } }
                for (dy in 0 until 4) {
                    for (dx in 0 until 4) {
                        val px = minOf(x0 + dx, srcWidth - 1)
                        val py = minOf(y0 + dy, srcHeight - 1)
                        colors[dy][dx] = bitmap.getPixel(px, py).toArgb()
                    }
                }
                
                // Apply bicubic interpolation
                var finalColor = 0
                for (dy in 0 until 4) {
                    var rowColor = 0
                    for (dx in 0 until 4) {
                        rowColor = interpolateColors(rowColor, colors[dy][dx], weightsX[dx])
                    }
                    finalColor = interpolateColors(finalColor, rowColor, weightsY[dy])
                }
                
                result.setPixel(x, y, Color.fromArgb(finalColor))
            }
        }
        
        return result
    }

    private fun interpolateColors(c1: Int, c2: Int, t: Float): Int {
        val a1 = (c1 shr 24) and 0xFF
        val r1 = (c1 shr 16) and 0xFF
        val g1 = (c1 shr 8) and 0xFF
        val b1 = c1 and 0xFF
        
        val a2 = (c2 shr 24) and 0xFF
        val r2 = (c2 shr 16) and 0xFF
        val g2 = (c2 shr 8) and 0xFF
        val b2 = c2 and 0xFF
        
        val a = ((a1 * (1 - t) + a2 * t)).toInt() and 0xFF
        val r = ((r1 * (1 - t) + r2 * t)).toInt() and 0xFF
        val g = ((g1 * (1 - t) + g2 * t)).toInt() and 0xFF
        val b = ((b1 * (1 - t) + b2 * t)).toInt() and 0xFF
        
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}