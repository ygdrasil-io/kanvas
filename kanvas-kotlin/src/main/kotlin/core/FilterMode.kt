package com.kanvas.core

/**
 * Filter modes for image sampling, inspired by Skia's SkFilterMode
 * Controls how pixels are interpolated when scaling or transforming images
 */
enum class FilterMode {
    /**
     * Nearest neighbor filtering - fast but pixelated
     * Uses the closest pixel value without interpolation
     */
    NEAREST,

    /**
     * Bilinear filtering - smooth interpolation between 4 nearest pixels
     * Provides basic anti-aliasing for scaled images
     */
    LINEAR,

    /**
     * Bicubic filtering - high-quality interpolation using 16 nearest pixels
     * Uses cubic convolution for smoother results
     */
    CUBIC
}