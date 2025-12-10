package com.kanvas.core

/**
 * Shader interface inspired by Skia's SkShader
 * Represents a source of color for filling shapes
 */
interface Shader {
    /**
     * Compute the color at the given coordinates
     */
    fun shade(x: Float, y: Float): Color
    
    /**
     * Apply the shader to a color
     */
    fun applyToColor(color: Color, x: Float, y: Float): Color {
        return shade(x, y).blendWith(color, BlendMode.SRC_OVER)
    }
}

/**
 * Solid color shader - fills with a single color
 */
class ColorShader(private val color: Color) : Shader {
    override fun shade(x: Float, y: Float): Color = color
}

/**
 * Linear gradient shader inspired by Skia's SkGradientShader
 */
class LinearGradientShader(
    private val colors: List<Color>,
    private val positions: List<Float>? = null,
    private val start: Point,
    private val end: Point,
    private val tileMode: TileMode = TileMode.CLAMP
) : Shader {
    
    private val dx: Float
    private val dy: Float
    private val lengthSquared: Float
    private val colorPositions: List<Float>
    
    init {
        require(colors.size >= 2) { "Linear gradient requires at least 2 colors" }
        if (positions != null) {
            require(positions.size == colors.size) { "Positions must match colors count" }
        }
        
        // Pre-calculate values for performance
        dx = end.x - start.x
        dy = end.y - start.y
        lengthSquared = dx * dx + dy * dy
        colorPositions = positions ?: colors.indices.map { it.toFloat() / (colors.size - 1) }
    }
    
    override fun shade(x: Float, y: Float): Color {
        // Early exit if the gradient line has zero length
        if (lengthSquared == 0f) return colors[0]
        
        // Project the point onto the gradient line
        val px = x - start.x
        val py = y - start.y
        val projection = (px * dx + py * dy) / lengthSquared
        
        var t = when (tileMode) {
            TileMode.CLAMP -> projection.coerceIn(0f, 1f)
            TileMode.REPEAT -> projection % 1f
            TileMode.MIRROR -> {
                val mod = projection % 2f
                if (mod > 1f) 2f - mod else mod
            }
            TileMode.DECAL -> projection.coerceIn(0f, 1f)
        }
        
        // Find which color segment we're in
        for (i in 0 until colorPositions.size - 1) {
            if (t <= colorPositions[i + 1]) {
                val segmentT = (t - colorPositions[i]) / (colorPositions[i + 1] - colorPositions[i])
                return colors[i].lerp(colors[i + 1], segmentT)
            }
        }
        
        return colors.last()
    }
}

/**
 * Radial gradient shader inspired by Skia's SkRadialGradient
 */
class RadialGradientShader(
    private val colors: List<Color>,
    private val positions: List<Float>? = null,
    private val center: Point,
    private val radius: Float,
    private val tileMode: TileMode = TileMode.CLAMP
) : Shader {
    
    private val colorPositions: List<Float>
    private val radiusSquared: Float
    
    init {
        require(colors.size >= 2) { "Radial gradient requires at least 2 colors" }
        require(radius > 0) { "Radius must be positive" }
        if (positions != null) {
            require(positions.size == colors.size) { "Positions must match colors count" }
        }
        
        // Pre-calculate values for performance
        colorPositions = positions ?: colors.indices.map { it.toFloat() / (colors.size - 1) }
        radiusSquared = radius * radius
    }
    
    override fun shade(x: Float, y: Float): Color {
        // Calculate squared distance from center for performance
        val dx = x - center.x
        val dy = y - center.y
        val distanceSquared = dx * dx + dy * dy
        
        // Early exit if outside radius and using CLAMP or DECAL mode
        if (distanceSquared >= radiusSquared && (tileMode == TileMode.CLAMP || tileMode == TileMode.DECAL)) {
            return colors.last()
        }
        
        val distance = kotlin.math.sqrt(distanceSquared)
        var t = distance / radius
        
        t = when (tileMode) {
            TileMode.CLAMP -> t.coerceIn(0f, 1f)
            TileMode.REPEAT -> t % 1f
            TileMode.MIRROR -> {
                val mod = t % 2f
                if (mod > 1f) 2f - mod else mod
            }
            TileMode.DECAL -> t.coerceIn(0f, 1f)
        }
        
        // Find which color segment we're in
        for (i in 0 until colorPositions.size - 1) {
            if (t <= colorPositions[i + 1]) {
                val segmentT = (t - colorPositions[i]) / (colorPositions[i + 1] - colorPositions[i])
                return colors[i].lerp(colors[i + 1], segmentT)
            }
        }
        
        return colors.last()
    }
}

/**
 * Bitmap shader inspired by Skia's SkBitmapShader
 */
class BitmapShader(
    private val bitmap: Bitmap,
    private val tileModeX: TileMode = TileMode.CLAMP,
    private val tileModeY: TileMode = TileMode.CLAMP
) : Shader {
    
    private val width: Int
    private val height: Int
    
    init {
        width = bitmap.getWidth()
        height = bitmap.getHeight()
    }
    
    override fun shade(x: Float, y: Float): Color {
        if (width == 0 || height == 0) return Color.TRANSPARENT
        
        // Calculate texture coordinates
        var u = x
        var v = y
        
        // Handle tile modes
        when (tileModeX) {
            TileMode.CLAMP -> u = u.coerceIn(0f, width.toFloat() - 1)
            TileMode.REPEAT -> {
                u = u % width
                if (u < 0) u += width
            }
            TileMode.MIRROR -> {
                val mod = u % (2 * width)
                u = if (mod > width) 2 * width - mod else mod
            }
            TileMode.DECAL -> u = u.coerceIn(0f, width.toFloat() - 1)
        }
        
        when (tileModeY) {
            TileMode.CLAMP -> v = v.coerceIn(0f, height.toFloat() - 1)
            TileMode.REPEAT -> {
                v = v % height
                if (v < 0) v += height
            }
            TileMode.MIRROR -> {
                val mod = v % (2 * height)
                v = if (mod > height) 2 * height - mod else mod
            }
            TileMode.DECAL -> v = v.coerceIn(0f, height.toFloat() - 1)
        }
        
        return bitmap.getPixel(u.toInt(), v.toInt())
    }
}

/**
 * Tile modes for shader behavior at edges
 * 
 * Differences and alignments with Skia:
 * - CLAMP: Similar to Skia's kClamp_TileMode, clamps values to the [0,1] range.
 * - REPEAT: Similar to Skia's kRepeat_TileMode, repeats the pattern using modulo operation.
 * - MIRROR: Similar to Skia's kMirror_TileMode, mirrors the pattern.
 * - DECAL: Similar to Skia's kDecal_TileMode, clamps values to the [0,1] range but with different edge behavior.
 * 
 * Note: The DECAL mode in Kanvas currently behaves identically to CLAMP, as the edge behavior differences
 * are more complex and may require additional implementation to fully match Skia's behavior.
 */
enum class TileMode {
    CLAMP,    // Clamp to edge colors
    REPEAT,   // Repeat the pattern
    MIRROR,   // Mirror the pattern
    DECAL     // Similar to CLAMP but with different edge behavior (clamps to [0,1] range)
}

/**
 * Shader factory methods inspired by Skia
 */
object Shaders {
    
    fun makeColorShader(color: Color): Shader = ColorShader(color)
    
    fun makeLinearGradient(
        colors: List<Color>,
        positions: List<Float>? = null,
        start: Point,
        end: Point,
        tileMode: TileMode = TileMode.CLAMP
    ): Shader = LinearGradientShader(colors, positions, start, end, tileMode)
    
    fun makeRadialGradient(
        colors: List<Color>,
        positions: List<Float>? = null,
        center: Point,
        radius: Float,
        tileMode: TileMode = TileMode.CLAMP
    ): Shader = RadialGradientShader(colors, positions, center, radius, tileMode)
    
    fun makeBitmapShader(
        bitmap: Bitmap,
        tileModeX: TileMode = TileMode.CLAMP,
        tileModeY: TileMode = TileMode.CLAMP
    ): Shader = BitmapShader(bitmap, tileModeX, tileModeY)
}