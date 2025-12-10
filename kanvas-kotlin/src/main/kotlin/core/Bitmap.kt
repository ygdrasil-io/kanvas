package com.kanvas.core

import java.nio.ByteBuffer

/**
 * Bitmap represents a pixel-based image.
 */
class Bitmap(private val width: Int, private val height: Int, private val config: BitmapConfig) {
    
    private val pixels: IntArray
    private val rowBytes: Int
    
    // Color info for this bitmap
    val colorInfo: ColorInfo = ColorInfo(
        when (config) {
            BitmapConfig.ALPHA_8 -> ColorType.ALPHA_8
            BitmapConfig.RGB_565 -> ColorType.RGB_565
            BitmapConfig.ARGB_4444 -> ColorType.ARGB_4444
            BitmapConfig.ARGB_8888 -> ColorType.RGBA_8888
            BitmapConfig.RGBA_F16 -> ColorType.RGBA_F16
        },
        AlphaType.PREMUL,
        ColorSpace.SRGB
    )
    
    init {
        require(width > 0 && height > 0) { "Bitmap dimensions must be positive" }
        
        pixels = IntArray(width * height)
        rowBytes = width * config.bytesPerPixel
    }
    
    /**
     * Gets the width of the bitmap
     */
    fun getWidth(): Int = width
    
    /**
     * Gets the height of the bitmap
     */
    fun getHeight(): Int = height
    
    /**
     * Gets the bitmap configuration
     */
    fun getConfig(): BitmapConfig = config
    
    /**
     * Gets the row bytes (stride)
     */
    fun getRowBytes(): Int = rowBytes
    
    /**
     * Gets the pixel at the specified coordinates
     */
    fun getPixel(x: Int, y: Int): Color {
        require(x in 0 until width && y in 0 until height) { "Coordinates out of bounds" }
        
        val index = y * width + x
        val pixel = pixels[index]
        
        return Color(
            red = (pixel shr 16) and 0xFF,
            green = (pixel shr 8) and 0xFF,
            blue = pixel and 0xFF,
            alpha = (pixel shr 24) and 0xFF
        )
    }
    
    /**
     * Sets the pixel at the specified coordinates
     */
    fun setPixel(x: Int, y: Int, color: Color) {
        require(x in 0 until width && y in 0 until height) { "Coordinates out of bounds" }
        
        val index = y * width + x
        pixels[index] = (color.alpha shl 24) or (color.red shl 16) or (color.green shl 8) or color.blue
    }
    
    /**
     * Fills the bitmap with the specified color
     */
    fun eraseColor(color: Color) {
        val pixel = (color.alpha shl 24) or (color.red shl 16) or (color.green shl 8) or color.blue
        pixels.fill(pixel)
    }
    
    /**
     * Creates a copy of this bitmap
     */
    fun copy(): Bitmap {
        val newBitmap = Bitmap(width, height, config)
        System.arraycopy(pixels, 0, newBitmap.pixels, 0, pixels.size)
        return newBitmap
    }
    
    /**
     * Extracts a sub-rectangle from this bitmap
     */
    fun extractSubset(src: Rect): Bitmap {
        require(src.left >= 0 && src.top >= 0 && src.right <= width && src.bottom <= height) {
            "Subset rectangle out of bounds"
        }
        
        val subsetWidth = src.width.toInt()
        val subsetHeight = src.height.toInt()
        val subset = Bitmap(subsetWidth, subsetHeight, config)
        
        for (y in 0 until subsetHeight) {
            for (x in 0 until subsetWidth) {
                val color = getPixel(src.left.toInt() + x, src.top.toInt() + y)
                subset.setPixel(x, y, color)
            }
        }
        
        return subset
    }
    
    /**
     * Scales the bitmap to the specified dimensions
     */
    /**
     * Scale the bitmap using the specified sampling options
     * @param newWidth Target width
     * @param newHeight Target height
     * @param sampling Sampling options to use (default: linear)
     * @return Scaled bitmap
     */
    fun scale(newWidth: Int, newHeight: Int, sampling: SamplingOptions = SamplingOptions.DEFAULT): Bitmap {
        return sampling.applyToBitmap(this, newWidth, newHeight)
    }

    /**
     * Scale the bitmap using nearest neighbor sampling (fast, pixelated)
     */
    fun scaleNearest(newWidth: Int, newHeight: Int): Bitmap {
        return scale(newWidth, newHeight, SamplingOptions.nearest())
    }

    /**
     * Scale the bitmap using linear sampling (smooth, basic quality)
     */
    fun scaleLinear(newWidth: Int, newHeight: Int): Bitmap {
        return scale(newWidth, newHeight, SamplingOptions.linear())
    }

    /**
     * Scale the bitmap using cubic sampling (high quality)
     */
    fun scaleCubic(newWidth: Int, newHeight: Int, resampler: CubicResampler = CubicResampler.Mitchell): Bitmap {
        return scale(newWidth, newHeight, SamplingOptions.cubic(resampler))
    }
    
    /**
     * Applies a color filter to the bitmap
     */
    fun applyColorFilter(filter: ColorFilter): Bitmap {
        val filtered = Bitmap(width, height, config)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val originalColor = getPixel(x, y)
                val filteredColor = filter.apply(originalColor)
                filtered.setPixel(x, y, filteredColor)
            }
        }
        
        return filtered
    }
    
    /**
     * Checks if the bitmap contains any non-transparent pixels
     */
    fun hasNonTransparentPixels(): Boolean {
        for (pixel in pixels) {
            val alpha = (pixel shr 24) and 0xFF
            if (alpha > 0) {
                return true
            }
        }
        return false
    }
    
    /**
     * Gets the bitmap as a ByteBuffer for interop
     */
    fun toByteBuffer(): ByteBuffer {
        val buffer = ByteBuffer.allocate(pixels.size * 4)
        for (pixel in pixels) {
            buffer.putInt(pixel)
        }
        buffer.flip()
        return buffer
    }
    
    companion object {
        /**
         * Creates a bitmap from raw pixel data
         */
        fun createFromPixels(pixels: IntArray, width: Int, height: Int, config: BitmapConfig): Bitmap {
            require(pixels.size >= width * height) { "Pixel array too small" }
            
            val bitmap = Bitmap(width, height, config)
            System.arraycopy(pixels, 0, bitmap.pixels, 0, width * height)
            return bitmap
        }
        
        /**
         * Creates an empty bitmap
         */
        fun create(width: Int, height: Int, config: BitmapConfig = BitmapConfig.ARGB_8888): Bitmap {
            return Bitmap(width, height, config)
        }
    }
}

/**
 * Bitmap configuration specifying pixel format
 */
enum class BitmapConfig(val bytesPerPixel: Int) {
    ALPHA_8(1),      // 8-bit alpha only
    RGB_565(2),      // 16-bit RGB
    ARGB_4444(2),    // 16-bit ARGB
    ARGB_8888(4),    // 32-bit ARGB
    RGBA_F16(8)      // 64-bit floating point RGBA
}

/**
 * Bitmap utility functions
 */
object BitmapUtils {
    
    /**
     * Creates a bitmap from a color
     */
    fun createFromColor(width: Int, height: Int, color: Color, config: BitmapConfig = BitmapConfig.ARGB_8888): Bitmap {
        val bitmap = Bitmap(width, height, config)
        bitmap.eraseColor(color)
        return bitmap
    }
    
    /**
     * Blends two bitmaps together
     */
    fun blend(src: Bitmap, dst: Bitmap, blendMode: BlendMode): Bitmap {
        require(src.getWidth() == dst.getWidth() && src.getHeight() == dst.getHeight()) {
            "Bitmaps must have the same dimensions"
        }
        
        val result = Bitmap(src.getWidth(), src.getHeight(), src.getConfig())
        
        for (y in 0 until src.getHeight()) {
            for (x in 0 until src.getWidth()) {
                val srcColor = src.getPixel(x, y)
                val dstColor = dst.getPixel(x, y)
                val blendedColor = blendColors(srcColor, dstColor, blendMode)
                result.setPixel(x, y, blendedColor)
            }
        }
        
        return result
    }
    
    internal fun blendColors(src: Color, dst: Color, mode: BlendMode): Color {
        val sa = src.alpha / 255f
        val da = dst.alpha / 255f
        val sr = src.red / 255f
        val sg = src.green / 255f
        val sb = src.blue / 255f
        val dr = dst.red / 255f
        val dg = dst.green / 255f
        val db = dst.blue / 255f
        
        return when (mode) {
            BlendMode.SRC_OVER -> {
                val a = sa + da * (1 - sa)
                val r = (sr + dr * (1 - sa)) / a
                val g = (sg + dg * (1 - sa)) / a
                val b = (sb + db * (1 - sa)) / a
                Color((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt(), (a * 255).toInt())
            }
            // TODO: Implement other blend modes
            else -> src // Fallback to source for unimplemented modes
        }
    }
}