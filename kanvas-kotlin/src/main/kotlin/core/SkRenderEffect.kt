package com.kanvas.core

/**
 * SkRenderEffect interface inspired by Skia's SkRuntimeEffect
 * Represents various rendering effects that can be applied during drawing
 */
interface SkRenderEffect {
    
    /**
     * Applies this effect to the given shader
     * @param shader The base shader to apply the effect to
     * @return A new shader with the effect applied
     */
    fun applyToShader(shader: Shader): Shader
    
    /**
     * Applies this effect directly to a color
     * @param color The base color
     * @param x The x coordinate
     * @param y The y coordinate
     * @return The color with the effect applied
     */
    fun applyToColor(color: Color, x: Float, y: Float): Color
    
    /**
     * Checks if this effect can be applied as a GPU operation
     */
    fun canApplyAsGPUOperation(): Boolean
    
    /**
     * Gets the effect's bounding rectangle
     */
    fun getEffectBounds(src: Rect, dst: Rect): Rect
    
    /**
     * Creates a new effect that combines this effect with another
     */
    fun makeComposed(outer: SkRenderEffect): SkRenderEffect
    
    /**
     * Creates a new effect with additional children
     */
    fun makeWithChildren(children: List<SkRenderEffect>): SkRenderEffect
}

/**
 * Base class for simple render effects
 */
abstract class BaseRenderEffect : SkRenderEffect {
    override fun canApplyAsGPUOperation(): Boolean = false
    
    override fun getEffectBounds(src: Rect, dst: Rect): Rect {
        return dst
    }
    
    override fun makeComposed(outer: SkRenderEffect): SkRenderEffect {
        return ComposedRenderEffect(listOf(this, outer))
    }
    
    override fun makeWithChildren(children: List<SkRenderEffect>): SkRenderEffect {
        val allEffects = mutableListOf<SkRenderEffect>()
        allEffects.add(this)
        allEffects.addAll(children)
        return ComposedRenderEffect(allEffects)
    }
}

/**
 * Blur effect - applies Gaussian blur to the rendered content
 */
class BlurEffect(private val radius: Float) : BaseRenderEffect() {
    override fun applyToShader(shader: Shader): Shader {
        // For now, return the original shader
        // In a real implementation, this would create a blur shader
        return shader
    }
    
    override fun applyToColor(color: Color, x: Float, y: Float): Color {
        // For now, return the original color
        // In a real implementation, this would apply blur
        return color
    }
    
    override fun getEffectBounds(src: Rect, dst: Rect): Rect {
        // Expand bounds by blur radius
        return Rect(
            dst.left - radius,
            dst.top - radius,
            dst.right + radius,
            dst.bottom + radius
        )
    }
}

/**
 * Color filter effect - applies a color filter to the rendered content
 */
class ColorFilterEffect(private val colorFilter: ColorFilter) : BaseRenderEffect() {
    override fun applyToShader(shader: Shader): Shader {
        return ColorFilterShader(shader, colorFilter)
    }
    
    override fun applyToColor(color: Color, x: Float, y: Float): Color {
        return colorFilter.apply(color)
    }
}

/**
 * Combines multiple render effects into a single effect
 */
class ComposedRenderEffect(private val effects: List<SkRenderEffect>) : BaseRenderEffect() {
    override fun applyToShader(shader: Shader): Shader {
        var currentShader = shader
        for (effect in effects) {
            currentShader = effect.applyToShader(currentShader)
        }
        return currentShader
    }
    
    override fun applyToColor(color: Color, x: Float, y: Float): Color {
        var currentColor = color
        for (effect in effects) {
            currentColor = effect.applyToColor(currentColor, x, y)
        }
        return currentColor
    }
    
    override fun getEffectBounds(src: Rect, dst: Rect): Rect {
        var currentBounds = dst
        for (effect in effects) {
            currentBounds = effect.getEffectBounds(src, currentBounds)
        }
        return currentBounds
    }
}

/**
 * Shader-based render effect
 */
class ShaderEffect(private val shader: Shader) : BaseRenderEffect() {
    override fun applyToShader(baseShader: Shader): Shader {
        return shader
    }
    
    override fun applyToColor(color: Color, x: Float, y: Float): Color {
        return shader.shade(x, y).blendWith(color, BlendMode.SRC_OVER)
    }
}

/**
 * Matrix transformation effect
 */
class MatrixEffect(private val matrix: Matrix) : BaseRenderEffect() {
    override fun applyToShader(shader: Shader): Shader {
        return MatrixShader(shader, matrix)
    }
    
    override fun applyToColor(color: Color, x: Float, y: Float): Color {
        // Apply matrix transformation to coordinates
        val transformedPoint = matrix.mapPoint(x, y)
        return shader.shade(transformedPoint.x, transformedPoint.y).blendWith(color, BlendMode.SRC_OVER)
    }
}

/**
 * Factory methods for creating common render effects
 */
object RenderEffects {
    
    /**
     * Creates a blur effect
     */
    fun makeBlur(radius: Float): SkRenderEffect {
        return BlurEffect(radius)
    }
    
    /**
     * Creates a color filter effect
     */
    fun makeColorFilter(colorFilter: ColorFilter): SkRenderEffect {
        return ColorFilterEffect(colorFilter)
    }
    
    /**
     * Creates a shader effect
     */
    fun makeShader(shader: Shader): SkRenderEffect {
        return ShaderEffect(shader)
    }
    
    /**
     * Creates a matrix transformation effect
     */
    fun makeMatrix(matrix: Matrix): SkRenderEffect {
        return MatrixEffect(matrix)
    }
    
    /**
     * Creates a composed effect from multiple effects
     */
    fun makeComposed(effects: List<SkRenderEffect>): SkRenderEffect {
        return ComposedRenderEffect(effects)
    }
    
    /**
     * Creates a drop shadow effect
     */
    fun makeDropShadow(dx: Float, dy: Float, blurRadius: Float, color: Color): SkRenderEffect {
        // Combine offset and blur
        val offsetMatrix = Matrix().apply {
            transX = dx
            transY = dy
        }
        val blur = BlurEffect(blurRadius)
        val colorFilter = ColorFilterEffect(ColorFilter.makeBlend(color, BlendMode.SRC_OVER))
        
        return makeComposed(listOf(
            MatrixEffect(offsetMatrix),
            blur,
            colorFilter
        ))
    }
    
    /**
     * Creates an inner shadow effect
     */
    fun makeInnerShadow(dx: Float, dy: Float, blurRadius: Float, color: Color): SkRenderEffect {
        // Similar to drop shadow but typically used for inner shadows
        return makeDropShadow(dx, dy, blurRadius, color)
    }
}

/**
 * Shader that applies a color filter to another shader
 */
private class ColorFilterShader(private val baseShader: Shader, private val colorFilter: ColorFilter) : Shader {
    override fun shade(x: Float, y: Float): Color {
        return colorFilter.apply(baseShader.shade(x, y))
    }
}

/**
 * Shader that applies a matrix transformation to another shader
 */
private class MatrixShader(private val baseShader: Shader, private val matrix: Matrix) : Shader {
    override fun shade(x: Float, y: Float): Color {
        val transformedPoint = matrix.mapPoint(x, y)
        return baseShader.shade(transformedPoint.x, transformedPoint.y)
    }
}