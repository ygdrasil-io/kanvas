package com.kanvas.core

/**
 * Paint holds the style and color information about how to draw geometries, text and bitmaps.
 */
class Paint {
    
    // Basic properties
    var color: Color = Color.BLACK
    var alpha: Int = 255
    var style: PaintStyle = PaintStyle.FILL
    var strokeWidth: Float = 1f
    var strokeCap: StrokeCap = StrokeCap.BUTT
    var strokeJoin: StrokeJoin = StrokeJoin.MITER
    var strokeMiter: Float = 4f
    
    // Text properties
    var textSize: Float = 12f
    var typeface: Typeface? = null
    var textAlign: TextAlign = TextAlign.LEFT
    var textEncoding: TextEncoding = TextEncoding.UTF8
    
    // Advanced properties
    var shader: Shader? = null
    var colorFilter: ColorFilter? = null
    var maskFilter: MaskFilter? = null
    var pathEffect: PathEffect? = null
    var rasterizer: Rasterizer? = null
    
    // Blending properties
    var blendMode: BlendMode = BlendMode.SRC_OVER
    var isAntiAlias: Boolean = true
    var isDither: Boolean = false
    var isSubpixelText: Boolean = false
    var isLCDRenderText: Boolean = false
    
    /**
     * Creates a copy of this paint
     */
    fun copy(): Paint {
        val newPaint = Paint()
        newPaint.color = this.color.copy()
        newPaint.alpha = this.alpha
        newPaint.style = this.style
        newPaint.strokeWidth = this.strokeWidth
        newPaint.strokeCap = this.strokeCap
        newPaint.strokeJoin = this.strokeJoin
        newPaint.strokeMiter = this.strokeMiter
        newPaint.textSize = this.textSize
        newPaint.typeface = this.typeface
        newPaint.textAlign = this.textAlign
        newPaint.textEncoding = this.textEncoding
        newPaint.shader = this.shader
        newPaint.colorFilter = this.colorFilter
        newPaint.maskFilter = this.maskFilter
        newPaint.pathEffect = this.pathEffect
        newPaint.rasterizer = this.rasterizer
        newPaint.blendMode = this.blendMode
        newPaint.isAntiAlias = this.isAntiAlias
        newPaint.isDither = this.isDither
        newPaint.isSubpixelText = this.isSubpixelText
        newPaint.isLCDRenderText = this.isLCDRenderText
        return newPaint
    }
    
    override fun toString(): String {
        return "Paint(color=$color, style=$style, strokeWidth=$strokeWidth, " +
               "textSize=$textSize, blendMode=$blendMode, isAntiAlias=$isAntiAlias)"
    }
}

/**
 * Painting style for geometry drawing
 */
enum class PaintStyle {
    FILL,        // Fill the geometry
    STROKE,      // Stroke the geometry
    FILL_AND_STROKE  // Both fill and stroke
}

/**
 * Cap style for stroke ends
 */
enum class StrokeCap {
    BUTT,    // No extension beyond the endpoints
    ROUND,   // Semi-circle extension
    SQUARE   // Square extension
}

/**
 * Join style for stroke corners
 */
enum class StrokeJoin {
    MITER,    // Pointed corners
    ROUND,    // Rounded corners
    BEVEL     // Beveled corners
}

/**
 * Text alignment options
 */
enum class TextAlign {
    LEFT,    // Align text to the left
    CENTER,  // Center text horizontally
    RIGHT    // Align text to the right
}

/**
 * Text encoding options
 */
enum class TextEncoding {
    UTF8,     // UTF-8 encoding
    UTF16,    // UTF-16 encoding
    UTF32     // UTF-32 encoding
}

/**
 * Blending modes for compositing
 */
enum class BlendMode {
    CLEAR,        // [0, 0]
    SRC,          // [Sa, Sc]
    DST,          // [Da, Dc]
    SRC_OVER,     // [Sa + Da*(1-Sa), Sc + Dc*(1-Sa)]
    DST_OVER,     // [Sa + Da*(1-Da), Sc*(1-Da) + Dc]
    SRC_IN,       // [Sa*Da, Sc*Da]
    DST_IN,       // [Sa*Da, Dc*Sa]
    SRC_OUT,      // [Sa*(1-Da), Sc*(1-Da)]
    DST_OUT,      // [Da*(1-Sa), Dc*(1-Sa)]
    SRC_ATOP,     // [Da, Sc*Da + Dc*(1-Sa)]
    DST_ATOP,     // [Sa, Sc*(1-Da) + Dc]
    XOR,          // [Sa + Da - 2*Sa*Da, Sc*(1-Da) + Dc*(1-Sa)]
    PLUS,         // [Sa + Da, Sc + Dc] (saturating)
    MODULATE,     // [Sa*Da, Sc*Dc]
    SCREEN,       // [Sa + Da - Sa*Da, Sc + Dc - Sc*Dc]
    OVERLAY,      // [Sa + Da - Sa*Da, Sc*(1-Da) + Dc*(1-Sa) + 2*Sc*Dc*min(Sa, Da)]
    DARKEN,       // [Sa + Da - Sa*Da, min(Sc*(1-Da) + Dc, Sc + Dc*(1-Sa))]
    LIGHTEN,      // [Sa + Da - Sa*Da, max(Sc*(1-Da) + Dc, Sc + Dc*(1-Sa))]
    COLOR_DODGE,  // [Sa + Da - Sa*Da, Sc + Dc*(1-Sa)/(1-min(1, Sc/Dc))]
    COLOR_BURN,   // [Sa + Da - Sa*Da, Sc*(1-Da)/(1-min(1, (1-Sc)/Dc)) + Dc*(1-Sa)]
    HARD_LIGHT,   // [Sa + Da - Sa*Da, Sc*(1-Da) + Dc*(1-Sa) + 2*Sc*Dc*(Sa > 0.5 ? 1-Da : Da)]
    SOFT_LIGHT,   // Complex formula
    DIFFERENCE,   // [Sa + Da - Sa*Da, |Sc - Dc|]
    EXCLUSION,    // [Sa + Da - Sa*Da, Sc + Dc - 2*Sc*Dc]
    MULTIPLY,     // [Sa + Da - Sa*Da, Sc*Dc]
    HUE,          // [Sa + Da - Sa*Da, set hue of Sc to Dc]
    SATURATION,   // [Sa + Da - Sa*Da, set saturation of Sc to Dc]
    COLOR,        // [Sa + Da - Sa*Da, set color of Sc to Dc]
    LUMINOSITY    // [Sa + Da - Sa*Da, set luminosity of Sc to Dc]
}

/**
 * Typeface representation
 */
class Typeface {
    // TODO: Implement typeface loading and management
}

/**
 * Base class for shaders
 */
abstract class Shader {
    abstract fun apply(paint: Paint, matrix: Matrix? = null)
}

/**
 * Base class for color filters
 */
abstract class ColorFilter {
    abstract fun apply(color: Color): Color
}

/**
 * Factory object for creating color filters
 */
object ColorFilters {
    fun matrix(matrix: FloatArray): ColorFilter {
        return object : ColorFilter() {
            override fun apply(color: Color): Color {
                // Simplified matrix application
                val r = color.red / 255f
                val g = color.green / 255f
                val b = color.blue / 255f
                val a = color.alpha / 255f
                
                // Apply 4x5 matrix (simplified for example)
                val newR = r * matrix[0] + g * matrix[1] + b * matrix[2] + a * matrix[3] + matrix[4]
                val newG = r * matrix[5] + g * matrix[6] + b * matrix[7] + a * matrix[8] + matrix[9]
                val newB = r * matrix[10] + g * matrix[11] + b * matrix[12] + a * matrix[13] + matrix[14]
                val newA = r * matrix[15] + g * matrix[16] + b * matrix[17] + a * matrix[18] + matrix[19]
                
                return Color(
                    (newR.coerceIn(0f, 1f) * 255).toInt(),
                    (newG.coerceIn(0f, 1f) * 255).toInt(),
                    (newB.coerceIn(0f, 1f) * 255).toInt(),
                    (newA.coerceIn(0f, 1f) * 255).toInt()
                )
            }
        }
    }
    
    fun lighting(mul: Color, add: Color): ColorFilter {
        return object : ColorFilter() {
            override fun apply(color: Color): Color {
                return Color(
                    (color.red * mul.red / 255 + add.red).coerceIn(0, 255),
                    (color.green * mul.green / 255 + add.green).coerceIn(0, 255),
                    (color.blue * mul.blue / 255 + add.blue).coerceIn(0, 255),
                    color.alpha
                )
            }
        }
    }
}

/**
 * Base class for mask filters
 */
abstract class MaskFilter {
    abstract fun apply(mask: ByteArray): ByteArray
}

/**
 * Base class for path effects
 */
abstract class PathEffect {
    abstract fun apply(path: Path): Path
}

/**
 * Base class for rasterizers
 */
abstract class Rasterizer {
    abstract fun rasterize(path: Path): Bitmap
}