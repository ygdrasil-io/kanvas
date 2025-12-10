package com.kanvas.core

/**
 * Cubic resampler for high-quality image scaling
 * Inspired by Skia's SkCubicResampler
 */
sealed class CubicResampler {
    /**
     * Cubic B-spline resampler - smooth interpolation
     */
    object BSpline : CubicResampler()

    /**
     * Catmull-Rom resampler - sharper interpolation
     */
    object CatmullRom : CubicResampler()

    /**
     * Mitchell-Netravali resampler - balanced quality
     * Default choice for most use cases
     */
    object Mitchell : CubicResampler()

    /**
     * Get the cubic weights for interpolation
     * @param t Normalized distance from sample point (0 to 1)
     * @return Array of 4 weights for cubic interpolation
     */
    fun getWeights(t: Float): FloatArray {
        return when (this) {
            BSpline -> getBSplineWeights(t)
            CatmullRom -> getCatmullRomWeights(t)
            Mitchell -> getMitchellWeights(t)
        }
    }

    private fun getBSplineWeights(t: Float): FloatArray {
        val t2 = t * t
        val t3 = t2 * t
        
        val w0 = (1.0f - 3.0f * t + 3.0f * t2 - t3) / 6.0f
        val w1 = (4.0f - 6.0f * t2 + 3.0f * t3) / 6.0f
        val w2 = (1.0f + 3.0f * t + 3.0f * t2 - 3.0f * t3) / 6.0f
        val w3 = t3 / 6.0f
        
        return floatArrayOf(w0, w1, w2, w3)
    }

    private fun getCatmullRomWeights(t: Float): FloatArray {
        val t2 = t * t
        val t3 = t2 * t
        
        val w0 = (-0.5f * t + 1.0f * t2 - 0.5f * t3)
        val w1 = (1.0f - 2.5f * t2 + 1.5f * t3)
        val w2 = (0.5f * t + 2.0f * t2 - 1.5f * t3)
        val w3 = (-0.5f * t2 + 0.5f * t3)
        
        return floatArrayOf(w0, w1, w2, w3)
    }

    private fun getMitchellWeights(t: Float): FloatArray {
        val B = 1.0f / 3.0f  // Balance parameter
        val C = 1.0f / 3.0f  // Sharpness parameter
        
        val t2 = t * t
        val t3 = t2 * t
        
        val w0 = ((12.0f - 9.0f * B - 6.0f * C) * t3 + 
                 (-18.0f + 12.0f * B + 6.0f * C) * t2 + 
                 (6.0f - 2.0f * B)) / 6.0f
        val w1 = ((-1.0f * B - 6.0f * C) * t3 + 
                 (6.0f * B + 30.0f * C) * t2 + 
                 (-12.0f * B - 48.0f * C) * t + 
                 (8.0f * B + 24.0f * C)) / 6.0f
        val w2 = ((-1.0f * B - 6.0f * C) * t3 + 
                 (6.0f * B + 30.0f * C) * t2 + 
                 (-12.0f * B - 48.0f * C) * t + 
                 (8.0f * B + 30.0f * C)) / 6.0f
        val w3 = ((12.0f - 9.0f * B - 6.0f * C) * t3 + 
                 (-18.0f + 12.0f * B + 6.0f * C) * t2 + 
                 (6.0f - 2.0f * B)) / 6.0f
        
        return floatArrayOf(w0, w1, w2, w3)
    }
}