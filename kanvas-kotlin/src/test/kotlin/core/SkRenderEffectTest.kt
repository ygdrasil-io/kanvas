package core

import com.kanvas.core.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Test suite for SkRenderEffect implementation
 * Verifies that Kanvas render effects work correctly
 */
class SkRenderEffectTest {

    @Test
    fun `test SkRenderEffect interface`() {
        // Test that we can create basic effects
        val blurEffect = RenderEffects.makeBlur(5f)
        assertTrue(blurEffect is SkRenderEffect)
        
        val colorFilter = ColorFilter.makeBlend(Color.RED, BlendMode.SRC_OVER)
        val colorFilterEffect = RenderEffects.makeColorFilter(colorFilter)
        assertTrue(colorFilterEffect is SkRenderEffect)
        
        val shader = ColorShader(Color.BLUE)
        val shaderEffect = RenderEffects.makeShader(shader)
        assertTrue(shaderEffect is SkRenderEffect)
    }

    @Test
    fun `test blur effect`() {
        val blurEffect = RenderEffects.makeBlur(10f)
        
        // Test applyToShader
        val baseShader = ColorShader(Color.RED)
        val resultShader = blurEffect.applyToShader(baseShader)
        assertNotNull(resultShader)
        
        // Test applyToColor
        val baseColor = Color(255, 0, 0, 255)
        val resultColor = blurEffect.applyToColor(baseColor, 10f, 20f)
        // For now, blur effect returns the original color (placeholder implementation)
        assertEquals(baseColor, resultColor)
        
        // Test getEffectBounds
        val src = Rect(0f, 0f, 100f, 100f)
        val dst = Rect(10f, 10f, 90f, 90f)
        val bounds = blurEffect.getEffectBounds(src, dst)
        // Should be expanded by blur radius
        assertEquals(0f, bounds.left, 1e-5f)
        assertEquals(0f, bounds.top, 1e-5f)
        assertEquals(100f, bounds.right, 1e-5f)
        assertEquals(100f, bounds.bottom, 1e-5f)
    }

    @Test
    fun `test color filter effect`() {
        val colorFilter = ColorFilter.makeBlend(Color(0, 255, 0, 128), BlendMode.SRC_OVER)
        val effect = RenderEffects.makeColorFilter(colorFilter)
        
        // Test applyToShader
        val baseShader = ColorShader(Color.RED)
        val resultShader = effect.applyToShader(baseShader)
        assertTrue(resultShader is ColorFilterShader)
        
        // Test applyToColor
        val baseColor = Color(255, 0, 0, 255)
        val resultColor = effect.applyToColor(baseColor, 10f, 20f)
        // Should blend red with semi-transparent green
        assertNotEquals(baseColor, resultColor)
        
        // Test getEffectBounds (should return original bounds)
        val src = Rect(0f, 0f, 100f, 100f)
        val dst = Rect(10f, 10f, 90f, 90f)
        val bounds = effect.getEffectBounds(src, dst)
        assertEquals(dst, bounds)
    }

    @Test
    fun `test shader effect`() {
        val shader = LinearGradientShader(
            listOf(Color.RED, Color.BLUE),
            start = Point(0f, 0f),
            end = Point(100f, 100f)
        )
        val effect = RenderEffects.makeShader(shader)
        
        // Test applyToShader
        val baseShader = ColorShader(Color.GREEN)
        val resultShader = effect.applyToShader(baseShader)
        // Should return the gradient shader
        assertEquals(shader, resultShader)
        
        // Test applyToColor
        val color = effect.applyToColor(Color.WHITE, 50f, 50f)
        // Should be a blend of white and the gradient color at (50,50)
        assertNotEquals(Color.WHITE, color)
        
        // Test getEffectBounds
        val src = Rect(0f, 0f, 100f, 100f)
        val dst = Rect(10f, 10f, 90f, 90f)
        val bounds = effect.getEffectBounds(src, dst)
        assertEquals(dst, bounds)
    }

    @Test
    fun `test matrix effect`() {
        val matrix = Matrix().apply {
            scaleX = 2f
            scaleY = 2f
            transX = 10f
            transY = 20f
        }
        val effect = RenderEffects.makeMatrix(matrix)
        
        // Test applyToShader
        val baseShader = ColorShader(Color.RED)
        val resultShader = effect.applyToShader(baseShader)
        assertTrue(resultShader is MatrixShader)
        
        // Test applyToColor
        val baseColor = Color(255, 0, 0, 255)
        val resultColor = effect.applyToColor(baseColor, 0f, 0f)
        // Should apply matrix transformation to coordinates before shading
        assertEquals(baseColor, resultColor) // Matrix applied to (0,0) should still give red
        
        // Test getEffectBounds
        val src = Rect(0f, 0f, 100f, 100f)
        val dst = Rect(10f, 10f, 90f, 90f)
        val bounds = effect.getEffectBounds(src, dst)
        assertEquals(dst, bounds)
    }

    @Test
    fun `test composed effect`() {
        val blur = RenderEffects.makeBlur(5f)
        val colorFilter = RenderEffects.makeColorFilter(
            ColorFilter.makeBlend(Color.GREEN, BlendMode.SRC_OVER)
        )
        
        val composed = RenderEffects.makeComposed(listOf(blur, colorFilter))
        
        // Test applyToShader
        val baseShader = ColorShader(Color.RED)
        val resultShader = composed.applyToShader(baseShader)
        assertTrue(resultShader is ColorFilterShader)
        
        // Test applyToColor
        val baseColor = Color(255, 0, 0, 255)
        val resultColor = composed.applyToColor(baseColor, 10f, 20f)
        // Should apply both effects
        assertNotEquals(baseColor, resultColor)
        
        // Test getEffectBounds
        val src = Rect(0f, 0f, 100f, 100f)
        val dst = Rect(10f, 10f, 90f, 90f)
        val bounds = composed.getEffectBounds(src, dst)
        // Should be expanded by blur radius
        assertEquals(0f, bounds.left, 1e-5f)
        assertEquals(0f, bounds.top, 1e-5f)
        assertEquals(100f, bounds.right, 1e-5f)
        assertEquals(100f, bounds.bottom, 1e-5f)
    }

    @Test
    fun `test drop shadow effect`() {
        val dropShadow = RenderEffects.makeDropShadow(5f, 10f, 3f, Color.BLACK)
        
        // Test that it's a composed effect
        assertTrue(dropShadow is ComposedRenderEffect)
        
        // Test applyToShader
        val baseShader = ColorShader(Color.RED)
        val resultShader = dropShadow.applyToShader(baseShader)
        assertNotNull(resultShader)
        
        // Test getEffectBounds (should be expanded by blur + offset)
        val src = Rect(0f, 0f, 100f, 100f)
        val dst = Rect(10f, 10f, 90f, 90f)
        val bounds = dropShadow.getEffectBounds(src, dst)
        // Should account for offset and blur
        assertTrue(bounds.left <= 10f - 5f)
        assertTrue(bounds.top <= 10f - 3f)
        assertTrue(bounds.right >= 90f + 5f)
        assertTrue(bounds.bottom >= 90f + 3f)
    }

    @Test
    fun `test effect composition`() {
        val effect1 = RenderEffects.makeBlur(2f)
        val effect2 = RenderEffects.makeColorFilter(
            ColorFilter.makeBlend(Color.BLUE, BlendMode.SRC_OVER)
        )
        
        // Test makeComposed
        val composed = effect1.makeComposed(effect2)
        assertTrue(composed is ComposedRenderEffect)
        
        // Test makeWithChildren
        val withChildren = effect1.makeWithChildren(listOf(effect2))
        assertTrue(withChildren is ComposedRenderEffect)
        
        // Both should produce the same result
        val baseShader = ColorShader(Color.RED)
        val result1 = composed.applyToShader(baseShader)
        val result2 = withChildren.applyToShader(baseShader)
        assertEquals(result1, result2)
    }

    @Test
    fun `test GPU operation capability`() {
        val blur = RenderEffects.makeBlur(5f)
        val colorFilter = RenderEffects.makeColorFilter(
            ColorFilter.makeBlend(Color.GREEN, BlendMode.SRC_OVER)
        )
        val shader = RenderEffects.makeShader(ColorShader(Color.BLUE))
        
        // Currently, none of our effects can be applied as GPU operations
        assertFalse(blur.canApplyAsGPUOperation())
        assertFalse(colorFilter.canApplyAsGPUOperation())
        assertFalse(shader.canApplyAsGPUOperation())
    }

    @Test
    fun `test Skia compatibility patterns`() {
        // Test creating effects similar to Skia patterns
        
        // Gradient with color filter
        val gradient = LinearGradientShader(
            listOf(Color.RED, Color.BLUE),
            start = Point(0f, 0f),
            end = Point(100f, 100f)
        )
        val colorFilter = ColorFilter.makeBlend(Color(0, 255, 0, 128), BlendMode.SRC_OVER)
        val effect = RenderEffects.makeComposed(listOf(
            RenderEffects.makeShader(gradient),
            RenderEffects.makeColorFilter(colorFilter)
        ))
        
        // Test that it works
        val baseColor = Color.WHITE
        val resultColor = effect.applyToColor(baseColor, 50f, 50f)
        assertNotEquals(baseColor, resultColor)
        
        // Test inner shadow (similar to drop shadow)
        val innerShadow = RenderEffects.makeInnerShadow(2f, 2f, 1f, Color(0, 0, 0, 128))
        assertTrue(innerShadow is ComposedRenderEffect)
    }
}