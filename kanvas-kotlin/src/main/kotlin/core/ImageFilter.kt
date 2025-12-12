package com.kanvas.core

/**
 * ImageFilter interface for applying various filters to images
 * This is a base interface that can be extended for specific filter types
 */
interface ImageFilter {
    
    /**
     * Applies the filter to the specified bitmap
     * @param bitmap The bitmap to filter
     * @return A new bitmap with the filter applied
     */
    fun apply(bitmap: Bitmap): Bitmap
    
    /**
     * Gets the filter's bounding rectangle
     * @param src The source rectangle
     * @param dst The destination rectangle
     * @param paint The paint to use (if applicable)
     * @return The filtered bounding rectangle
     */
    fun getFilterBounds(src: Rect, dst: Rect, paint: Paint): Rect {
        // Default implementation returns the destination rectangle
        return dst
    }
    
    /**
     * Checks if this filter can be applied as a GPU operation
     */
    fun canApplyAsGPUOperation(): Boolean {
        return false // Default implementation is CPU-based
    }
}

/**
 * Basic image filters
 */

/**
 * A blur filter
 */
class BlurFilter(private val radius: Float) : ImageFilter {
    override fun apply(bitmap: Bitmap): Bitmap {
        // Simple blur implementation - in a real implementation, this would use
        // a proper blur algorithm (Gaussian blur, etc.)
        val result = bitmap.copy()
        
        // For now, we'll just return a copy
        // TODO: Implement actual blur algorithm
        
        return result
    }
}

/**
 * A color filter wrapper
 */
class ColorFilterImageFilter(private val colorFilter: ColorFilter) : ImageFilter {
    override fun apply(bitmap: Bitmap): Bitmap {
        return bitmap.applyColorFilter(colorFilter)
    }
}

/**
 * A matrix filter (for transformations)
 */
class MatrixFilter(private val matrix: Matrix) : ImageFilter {
    override fun apply(bitmap: Bitmap): Bitmap {
        // Apply matrix transformation to the bitmap
        // This is a simplified version - real implementation would handle
        // various matrix operations properly
        
        // For now, we'll just scale if it's a simple scale matrix
        if (matrix.skewX == 0f && matrix.skewY == 0f && matrix.persp0 == 0f && matrix.persp1 == 0f && matrix.persp2 == 1f) {
            val newWidth = (bitmap.getWidth() * matrix.scaleX).toInt()
            val newHeight = (bitmap.getHeight() * matrix.scaleY).toInt()
            return bitmap.scale(newWidth, newHeight)
        }
        
        return bitmap.copy()
    }
}

/**
 * A composite filter that applies multiple filters in sequence
 */
class CompositeFilter(private val filters: List<ImageFilter>) : ImageFilter {
    override fun apply(bitmap: Bitmap): Bitmap {
        var result = bitmap
        for (filter in filters) {
            result = filter.apply(result)
        }
        return result
    }
}