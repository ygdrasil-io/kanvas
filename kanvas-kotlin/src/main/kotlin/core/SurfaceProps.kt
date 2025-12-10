package com.kanvas.core

/**
 * Surface properties for devices, inspired by Skia's SkSurfaceProps
 * Represents properties of a drawing surface that affect rendering quality and behavior
 */
data class SurfaceProps(
    /**
     * Pixel geometry describes how pixels are arranged on the device
     */
    val pixelGeometry: PixelGeometry = PixelGeometry.UNKNOWN,
    
    /**
     * Flags that affect rendering behavior
     */
    val flags: Int = 0
) {
    companion object {
        /**
         * Default surface properties for typical displays
         */
        fun default(): SurfaceProps {
            return SurfaceProps(
                pixelGeometry = PixelGeometry.RGB_H,
                flags = 0
            )
        }
    }
}

/**
 * Pixel geometry types, inspired by Skia's SkPixelGeometry
 * Describes how RGB pixels are arranged in a device
 */
enum class PixelGeometry {
    /**
     * Unknown or unspecified pixel geometry
     */
    UNKNOWN,
    
    /**
     * RGB pixels arranged horizontally (common for LCD screens)
     */
    RGB_H,
    
    /**
     * RGB pixels arranged vertically
     */
    RGB_V,
    
    /**
     * BGR pixels arranged horizontally
     */
    BGR_H,
    
    /**
     * BGR pixels arranged vertically
     */
    BGR_V
}