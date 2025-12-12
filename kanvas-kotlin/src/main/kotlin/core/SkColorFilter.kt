package com.kanvas.core

import com.kanvas.core.AlphaType.*
import com.kanvas.core.ColorType.*

/**
 * SkColorFilter is the base class for color filters in Kanvas.
 * Color filters transform colors during drawing operations.
 */
abstract class SkColorFilter {

    /**
     * Filters a single color.
     *
     * @param srcColor The source color to filter
     * @param dstColorSpace The destination color space (optional)
     * @return The filtered color
     */
    fun filterColor(srcColor: SkColor, dstColorSpace: SkColorSpace? = null): SkColor {
        val srcColor4f = SkColor4f(srcColor)
        val filtered = onFilterColor4f(srcColor4f, null, dstColorSpace)
        return filtered.toSkColor()
    }

    /**
     * Filters a color in SkColor4f format.
     *
     * @param srcColor The source color to filter
     * @param srcColorSpace The source color space (optional)
     * @param dstColorSpace The destination color space (optional)
     * @return The filtered color in SkColor4f format
     */
    fun filterColor4f(srcColor: SkColor4f, srcColorSpace: SkColorSpace? = null, dstColorSpace: SkColorSpace? = null): SkColor4f {
        return onFilterColor4f(srcColor, srcColorSpace, dstColorSpace)
    }

    /**
     * Returns true if this color filter can be represented as a simple color and blend mode.
     *
     * @param color Output parameter for the color
     * @param mode Output parameter for the blend mode
     * @return true if this filter can be represented as a color mode, false otherwise
     */
    fun asAColorMode(color: SkColor, mode: SkBlendMode): Boolean {
        return onAsAColorMode(color, mode)
    }

    /**
     * Returns true if this color filter can be represented as a color matrix.
     *
     * @param matrix Output parameter for the 4x5 color matrix
     * @return true if this filter can be represented as a color matrix, false otherwise
     */
    fun asAColorMatrix(matrix: FloatArray): Boolean {
        require(matrix.size >= 20) { "Matrix must have at least 20 elements" }
        return onAsAColorMatrix(matrix)
    }

    /**
     * Returns true if this color filter does not modify the alpha channel.
     *
     * @return true if alpha is unchanged, false otherwise
     */
    fun isAlphaUnchanged(): Boolean {
        return onIsAlphaUnchanged()
    }

    /**
     * Creates a new color filter that is the composition of this filter and another.
     *
     * @param outer The outer color filter
     * @return A new composed color filter
     */
    fun makeComposed(outer: SkColorFilter): SkColorFilter {
        return SkComposeColorFilter(this, outer)
    }

    /**
     * Creates a color filter with a working color space.
     *
     * @param workingColorSpace The working color space
     * @return A new color filter with the specified working color space
     */
    fun makeWithWorkingColorSpace(workingColorSpace: SkColorSpace): SkColorFilter {
        if (workingColorSpace == SkColorSpace.sRGB) {
            return this
        }
        return SkWorkingFormatColorFilter(this, workingColorSpace)
    }

    // Abstract methods to be implemented by subclasses

    /**
     * Filters a color in SkColor4f format.
     */
    protected abstract fun onFilterColor4f(srcColor: SkColor4f, srcColorSpace: SkColorSpace?, dstColorSpace: SkColorSpace?): SkColor4f

    /**
     * Returns true if this color filter can be represented as a simple color and blend mode.
     */
    protected abstract fun onAsAColorMode(color: SkColor, mode: SkBlendMode): Boolean

    /**
     * Returns true if this color filter can be represented as a color matrix.
     */
    protected abstract fun onAsAColorMatrix(matrix: FloatArray): Boolean

    /**
     * Returns true if this color filter does not modify the alpha channel.
     */
    protected abstract fun onIsAlphaUnchanged(): Boolean

    companion object {
        /**
         * Creates a color filter that applies a matrix transformation.
         *
         * @param matrix The 4x5 color matrix
         * @param domain The color domain (RGBA or HSLA)
         * @return A new matrix color filter
         */
        fun makeMatrix(matrix: FloatArray, domain: MatrixDomain = MatrixDomain.kRGBA): SkColorFilter {
            return SkMatrixColorFilter(matrix, domain)
        }

        /**
         * Creates a color filter that applies a linear to sRGB gamma transformation.
         *
         * @return A new linear to sRGB gamma color filter
         */
        fun makeLinearToSRGBGamma(): SkColorFilter {
            return SkLinearToSRGBGammaColorFilter()
        }

        /**
         * Creates a color filter that applies an sRGB to linear gamma transformation.
         *
         * @return A new sRGB to linear gamma color filter
         */
        fun makeSRGBToLinearGamma(): SkColorFilter {
            return SkSRGBToLinearGammaColorFilter()
        }

        /**
         * Creates a color filter that applies a color space transformation.
         *
         * @param srcColorSpace The source color space
         * @param dstColorSpace The destination color space
         * @return A new color space transformation color filter
         */
        fun makeColorSpace(srcColorSpace: SkColorSpace, dstColorSpace: SkColorSpace): SkColorFilter {
            return SkColorSpaceColorFilter(srcColorSpace, dstColorSpace)
        }

        /**
         * Creates a color filter that applies a blend mode with a constant color.
         *
         * @param color The constant color
         * @param mode The blend mode
         * @return A new blend mode color filter
         */
        fun makeBlendMode(color: SkColor, mode: SkBlendMode): SkColorFilter {
            return SkBlendModeColorFilter(color, mode)
        }

        /**
         * Creates a color filter that applies a blend mode with a constant color in SkColor4f format.
         *
         * @param color The constant color in SkColor4f format
         * @param mode The blend mode
         * @return A new blend mode color filter
         */
        fun makeBlendMode(color: SkColor4f, mode: SkBlendMode): SkColorFilter {
            return SkBlendModeColorFilter(color, mode)
        }

        /**
         * Creates a color filter that applies a table-based transformation.
         *
         * @param table The lookup table for each color channel
         * @return A new table color filter
         */
        fun makeTable(table: FloatArray): SkColorFilter {
            return SkTableColorFilter(table)
        }

        /**
         * Creates a color filter that applies a table-based transformation with separate tables for each channel.
         *
         * @param aTable The lookup table for the alpha channel
         * @param rTable The lookup table for the red channel
         * @param gTable The lookup table for the green channel
         * @param bTable The lookup table for the blue channel
         * @return A new table color filter
         */
        fun makeTable(aTable: FloatArray, rTable: FloatArray, gTable: FloatArray, bTable: FloatArray): SkColorFilter {
            return SkTableColorFilter(aTable, rTable, gTable, bTable)
        }

        /**
         * Creates a color filter that applies a table-based transformation with a single table for all channels.
         *
         * @param table The lookup table for all channels
         * @return A new table color filter
         */
        fun makeTableARGB(table: FloatArray): SkColorFilter {
            return SkTableColorFilter(table)
        }

        /**
         * Creates a color filter that applies a Gaussian blur effect.
         *
         * @return A new Gaussian color filter
         */
        fun makeGaussian(): SkColorFilter {
            return SkGaussianColorFilter()
        }
    }

    /**
     * Domain for matrix color filters.
     */
    enum class MatrixDomain {
        kRGBA,
        kHSLA
    }

    /**
     * Clamping behavior for matrix color filters.
     */
    enum class Clamp {
        kNone,
        kNormalized
    }
}

/**
 * Concrete implementation of SkColorFilter that applies a matrix transformation.
 */
class SkMatrixColorFilter(private val matrix: FloatArray, private val domain: SkColorFilter.MatrixDomain) : SkColorFilter() {

    init {
        require(matrix.size >= 20) { "Matrix must have at least 20 elements" }
    }

    override fun onFilterColor4f(srcColor: SkColor4f, srcColorSpace: SkColorSpace?, dstColorSpace: SkColorSpace?): SkColor4f {
        // Apply matrix transformation
        val result = when (domain) {
            SkColorFilter.MatrixDomain.kRGBA -> applyRGBAMatrix(srcColor)
            SkColorFilter.MatrixDomain.kHSLA -> applyHSLAMatrix(srcColor)
        }
        return result.pinAlpha()
    }

    override fun onAsAColorMode(color: SkColor, mode: SkBlendMode): Boolean {
        return false // Matrix filters are not simple color modes
    }

    override fun onAsAColorMatrix(matrix: FloatArray): Boolean {
        System.arraycopy(this.matrix, 0, matrix, 0, 20)
        return true
    }

    override fun onIsAlphaUnchanged(): Boolean {
        // Check if the matrix preserves alpha
        // For RGBA domain: matrix[15] should be 1.0 and matrix[16..19] should be 0.0
        // For HSLA domain: more complex check needed
        return when (domain) {
            SkColorFilter.MatrixDomain.kRGBA -> {
                matrix[15] == 1.0f && matrix[16] == 0.0f && matrix[17] == 0.0f && matrix[18] == 0.0f && matrix[19] == 0.0f
            }
            SkColorFilter.MatrixDomain.kHSLA -> {
                // HSLA matrix alpha preservation is more complex
                false
            }
        }
    }

    private fun applyRGBAMatrix(color: SkColor4f): SkColor4f {
        // Apply 4x5 matrix transformation in RGBA space
        val r = matrix[0] * color.fR + matrix[1] * color.fG + matrix[2] * color.fB + matrix[3] * color.fA + matrix[4]
        val g = matrix[5] * color.fR + matrix[6] * color.fG + matrix[7] * color.fB + matrix[8] * color.fA + matrix[9]
        val b = matrix[10] * color.fR + matrix[11] * color.fG + matrix[12] * color.fB + matrix[13] * color.fA + matrix[14]
        val a = matrix[15] * color.fR + matrix[16] * color.fG + matrix[17] * color.fB + matrix[18] * color.fA + matrix[19]
        return SkColor4f(r, g, b, a)
    }

    private fun applyHSLAMatrix(color: SkColor4f): SkColor4f {
        // Convert RGB to HSLA, apply matrix, then convert back to RGBA
        val hsla = color.toHSLA()
        val h = matrix[0] * hsla.h + matrix[1] * hsla.s + matrix[2] * hsla.l + matrix[3] * hsla.a + matrix[4]
        val s = matrix[5] * hsla.h + matrix[6] * hsla.s + matrix[7] * hsla.l + matrix[8] * hsla.a + matrix[9]
        val l = matrix[10] * hsla.h + matrix[11] * hsla.s + matrix[12] * hsla.l + matrix[13] * hsla.a + matrix[14]
        val a = matrix[15] * hsla.h + matrix[16] * hsla.s + matrix[17] * hsla.l + matrix[18] * hsla.a + matrix[19]
        return SkColor4f.fromHSLA(h, s, l, a)
    }

    data class HSLA(val h: Float, val s: Float, val l: Float, val a: Float)

    companion object {
        fun SkColor4f.toHSLA(): HSLA {
            // Simple RGB to HSLA conversion (simplified)
            val max = maxOf(fR, fG, fB)
            val min = minOf(fR, fG, fB)
            val delta = max - min
            
            var h = 0f
            var s = 0f
            val l = (max + min) / 2f
            
            if (delta != 0f) {
                s = if (l < 0.5f) delta / (max + min) else delta / (2 - max - min)
                
                h = when (max) {
                    fR -> ((fG - fB) / delta) % 6f
                    fG -> ((fB - fR) / delta) + 2f
                    fB -> ((fR - fG) / delta) + 4f
                    else -> 0f
                }
                h = (h * 60f) % 360f
                if (h < 0f) h += 360f
            }
            
            return HSLA(h, s, l, fA)
        }

        fun SkColor4f.Companion.fromHSLA(h: Float, s: Float, l: Float, a: Float): SkColor4f {
            // Simple HSLA to RGB conversion (simplified)
            val c = (1f - abs(2f * l - 1f)) * s
            val x = c * (1f - abs((h / 60f) % 2f - 1f))
            val m = l - c / 2f
            
            val (r, g, b) = when (h) {
                in 0f..60f -> Triple(c, x, 0f)
                in 60f..120f -> Triple(x, c, 0f)
                in 120f..180f -> Triple(0f, c, x)
                in 180f..240f -> Triple(0f, x, c)
                in 240f..300f -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
            
            return SkColor4f(r + m, g + m, b + m, a)
        }
    }
}

/**
 * Concrete implementation of SkColorFilter that applies a blend mode with a constant color.
 */
class SkBlendModeColorFilter(private val color: SkColor4f, private val mode: SkBlendMode) : SkColorFilter() {

    constructor(color: SkColor, mode: SkBlendMode) : this(SkColor4f(color), mode)

    override fun onFilterColor4f(srcColor: SkColor4f, srcColorSpace: SkColorSpace?, dstColorSpace: SkColorSpace?): SkColor4f {
        return mode.apply(color, srcColor)
    }

    override fun onAsAColorMode(color: SkColor, mode: SkBlendMode): Boolean {
        color.set(this.color.toSkColor())
        mode.set(this.mode)
        return true
    }

    override fun onAsAColorMatrix(matrix: FloatArray): Boolean {
        return false // Blend mode filters are not matrix filters
    }

    override fun onIsAlphaUnchanged(): Boolean {
        return mode.isAlphaUnchanged()
    }
}

/**
 * Concrete implementation of SkColorFilter that applies a table-based transformation.
 */
class SkTableColorFilter(private val tables: Array<FloatArray>) : SkColorFilter() {

    constructor(table: FloatArray) : this(arrayOf(table, table, table, table))
    constructor(aTable: FloatArray, rTable: FloatArray, gTable: FloatArray, bTable: FloatArray) : this(arrayOf(aTable, rTable, gTable, bTable))

    override fun onFilterColor4f(srcColor: SkColor4f, srcColorSpace: SkColorSpace?, dstColorSpace: SkColorSpace?): SkColor4f {
        val a = applyTable(srcColor.fA, tables[0])
        val r = applyTable(srcColor.fR, tables[1])
        val g = applyTable(srcColor.fG, tables[2])
        val b = applyTable(srcColor.fB, tables[3])
        return SkColor4f(r, g, b, a)
    }

    override fun onAsAColorMode(color: SkColor, mode: SkBlendMode): Boolean {
        return false // Table filters are not simple color modes
    }

    override fun onAsAColorMatrix(matrix: FloatArray): Boolean {
        return false // Table filters are not matrix filters
    }

    override fun onIsAlphaUnchanged(): Boolean {
        // Check if alpha table is identity
        val alphaTable = tables[0]
        for (i in alphaTable.indices) {
            if (alphaTable[i] != i / (alphaTable.size - 1).toFloat()) {
                return false
            }
        }
        return true
    }

    private fun applyTable(value: Float, table: FloatArray): Float {
        // Clamp value to [0, 1] range
        val clamped = value.coerceIn(0f, 1f)
        // Scale to table indices
        val index = (clamped * (table.size - 1)).toInt().coerceIn(0, table.size - 1)
        return table[index]
    }
}

/**
 * Concrete implementation of SkColorFilter that applies a color space transformation.
 */
class SkColorSpaceColorFilter(private val srcColorSpace: SkColorSpace, private val dstColorSpace: SkColorSpace) : SkColorFilter() {

    override fun onFilterColor4f(srcColor: SkColor4f, srcColorSpace: SkColorSpace?, dstColorSpace: SkColorSpace?): SkColor4f {
        // Apply color space transformation
        val effectiveSrcSpace = srcColorSpace ?: this.srcColorSpace
        val effectiveDstSpace = dstColorSpace ?: this.dstColorSpace
        
        return effectiveSrcSpace.toXYZD50(srcColor).let { xyz ->
            effectiveDstSpace.fromXYZD50(xyz)
        }
    }

    override fun onAsAColorMode(color: SkColor, mode: SkBlendMode): Boolean {
        return false // Color space filters are not simple color modes
    }

    override fun onAsAColorMatrix(matrix: FloatArray): Boolean {
        // Could potentially return a matrix for color space conversion
        return false
    }

    override fun onIsAlphaUnchanged(): Boolean {
        return true // Color space transformations don't affect alpha
    }
}

/**
 * Concrete implementation of SkColorFilter that applies a linear to sRGB gamma transformation.
 */
class SkLinearToSRGBGammaColorFilter : SkColorFilter() {

    override fun onFilterColor4f(srcColor: SkColor4f, srcColorSpace: SkColorSpace?, dstColorSpace: SkColorSpace?): SkColor4f {
        // Apply linear to sRGB gamma transformation
        val r = linearToSRGB(srcColor.fR)
        val g = linearToSRGB(srcColor.fG)
        val b = linearToSRGB(srcColor.fB)
        return SkColor4f(r, g, b, srcColor.fA)
    }

    override fun onAsAColorMode(color: SkColor, mode: SkBlendMode): Boolean {
        return false
    }

    override fun onAsAColorMatrix(matrix: FloatArray): Boolean {
        return false
    }

    override fun onIsAlphaUnchanged(): Boolean {
        return true
    }

    private fun linearToSRGB(linear: Float): Float {
        return if (linear <= 0.0031308f) {
            linear * 12.92f
        } else {
            1.055f * linear.pow(1f/2.4f) - 0.055f
        }
    }
}

/**
 * Concrete implementation of SkColorFilter that applies an sRGB to linear gamma transformation.
 */
class SkSRGBToLinearGammaColorFilter : SkColorFilter() {

    override fun onFilterColor4f(srcColor: SkColor4f, srcColorSpace: SkColorSpace?, dstColorSpace: SkColorSpace?): SkColor4f {
        // Apply sRGB to linear gamma transformation
        val r = sRGBToLinear(srcColor.fR)
        val g = sRGBToLinear(srcColor.fG)
        val b = sRGBToLinear(srcColor.fB)
        return SkColor4f(r, g, b, srcColor.fA)
    }

    override fun onAsAColorMode(color: SkColor, mode: SkBlendMode): Boolean {
        return false
    }

    override fun onAsAColorMatrix(matrix: FloatArray): Boolean {
        return false
    }

    override fun onIsAlphaUnchanged(): Boolean {
        return true
    }

    private fun sRGBToLinear(srgb: Float): Float {
        return if (srgb <= 0.04045f) {
            srgb / 12.92f
        } else {
            ((srgb + 0.055f) / 1.055f).pow(2.4f)
        }
    }
}

/**
 * Concrete implementation of SkColorFilter that applies a Gaussian blur effect.
 */
class SkGaussianColorFilter : SkColorFilter() {

    override fun onFilterColor4f(srcColor: SkColor4f, srcColorSpace: SkColorSpace?, dstColorSpace: SkColorSpace?): SkColor4f {
        // Gaussian filter would typically be applied in image space, not per-pixel
        // This is a placeholder implementation
        return srcColor
    }

    override fun onAsAColorMode(color: SkColor, mode: SkBlendMode): Boolean {
        return false
    }

    override fun onAsAColorMatrix(matrix: FloatArray): Boolean {
        return false
    }

    override fun onIsAlphaUnchanged(): Boolean {
        return true
    }
}

/**
 * Concrete implementation of SkColorFilter that composes two color filters.
 */
class SkComposeColorFilter(private val inner: SkColorFilter, private val outer: SkColorFilter) : SkColorFilter() {

    override fun onFilterColor4f(srcColor: SkColor4f, srcColorSpace: SkColorSpace?, dstColorSpace: SkColorSpace?): SkColor4f {
        val innerResult = inner.filterColor4f(srcColor, srcColorSpace, dstColorSpace)
        return outer.filterColor4f(innerResult, srcColorSpace, dstColorSpace)
    }

    override fun onAsAColorMode(color: SkColor, mode: SkBlendMode): Boolean {
        return false // Composed filters are not simple color modes
    }

    override fun onAsAColorMatrix(matrix: FloatArray): Boolean {
        // Could potentially compose matrices if both filters are matrix-based
        return false
    }

    override fun onIsAlphaUnchanged(): Boolean {
        return inner.isAlphaUnchanged() && outer.isAlphaUnchanged()
    }
}

/**
 * Concrete implementation of SkColorFilter that applies a working color format transformation.
 */
class SkWorkingFormatColorFilter(private val child: SkColorFilter, private val workingColorSpace: SkColorSpace) : SkColorFilter() {

    override fun onFilterColor4f(srcColor: SkColor4f, srcColorSpace: SkColorSpace?, dstColorSpace: SkColorSpace?): SkColor4f {
        // Convert to working color space, apply child filter, then convert back
        val workingColor = workingColorSpace.fromXYZD50(srcColorSpace?.toXYZD50(srcColor) ?: srcColor)
        val filtered = child.filterColor4f(workingColor, workingColorSpace, workingColorSpace)
        return dstColorSpace?.fromXYZD50(workingColorSpace.toXYZD50(filtered)) ?: filtered
    }

    override fun onAsAColorMode(color: SkColor, mode: SkBlendMode): Boolean {
        return child.onAsAColorMode(color, mode)
    }

    override fun onAsAColorMatrix(matrix: FloatArray): Boolean {
        return child.onAsAColorMatrix(matrix)
    }

    override fun onIsAlphaUnchanged(): Boolean {
        return child.isAlphaUnchanged()
    }
}