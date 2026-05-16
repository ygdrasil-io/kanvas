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
    
    init {
        require(colors.size >= 2) { "Linear gradient requires at least 2 colors" }
        if (positions != null) {
            require(positions.size == colors.size) { "Positions must match colors count" }
        }
    }
    
    override fun shade(x: Float, y: Float): Color {
        // Calculate the position along the gradient line (0 to 1)
        val dx = end.x - start.x
        val dy = end.y - start.y
        val lengthSquared = dx * dx + dy * dy
        
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
        }
        
        // Find which color segment we're in
        val pos = positions ?: colors.indices.map { it.toFloat() / (colors.size - 1) }
        
        for (i in 0 until pos.size - 1) {
            if (t <= pos[i + 1]) {
                val segmentT = (t - pos[i]) / (pos[i + 1] - pos[i])
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
    
    init {
        require(colors.size >= 2) { "Radial gradient requires at least 2 colors" }
        require(radius > 0) { "Radius must be positive" }
        if (positions != null) {
            require(positions.size == colors.size) { "Positions must match colors count" }
        }
    }
    
    override fun shade(x: Float, y: Float): Color {
        // Calculate distance from center
        val dx = x - center.x
        val dy = y - center.y
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
        
        var t = distance / radius
        
        t = when (tileMode) {
            TileMode.CLAMP -> t.coerceIn(0f, 1f)
            TileMode.REPEAT -> t % 1f
            TileMode.MIRROR -> {
                val mod = t % 2f
                if (mod > 1f) 2f - mod else mod
            }
        }
        
        // Find which color segment we're in
        val pos = positions ?: colors.indices.map { it.toFloat() / (colors.size - 1) }
        
        for (i in 0 until pos.size - 1) {
            if (t <= pos[i + 1]) {
                val segmentT = (t - pos[i]) / (pos[i + 1] - pos[i])
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
    
    override fun shade(x: Float, y: Float): Color {
        val width = bitmap.getWidth()
        val height = bitmap.getHeight()
        
        if (width == 0 || height == 0) return Color.TRANSPARENT
        
        // Calculate texture coordinates
        var u = x % width
        var v = y % height
        
        // Handle tile modes
        u = when (tileModeX) {
            TileMode.CLAMP -> u.coerceIn(0f, width.toFloat() - 1)
            TileMode.REPEAT -> if (u < 0) u + width else u
            TileMode.MIRROR -> {
                val mod = u % (2 * width)
                if (mod > width) 2 * width - mod else mod
            }
        }
        
        v = when (tileModeY) {
            TileMode.CLAMP -> v.coerceIn(0f, height.toFloat() - 1)
            TileMode.REPEAT -> if (v < 0) v + height else v
            TileMode.MIRROR -> {
                val mod = v % (2 * height)
                if (mod > height) 2 * height - mod else mod
            }
        }
        
        return bitmap.getPixel(u.toInt(), v.toInt())
    }
}

/**
 * Tile modes for shader behavior at edges
 */
enum class TileMode {
    CLAMP,    // Clamp to edge colors
    REPEAT,   // Repeat the pattern
    MIRROR    // Mirror the pattern
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