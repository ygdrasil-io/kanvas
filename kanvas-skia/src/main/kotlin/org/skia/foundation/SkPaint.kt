package org.skia.foundation

public class SkPaint() {
    public enum class Style { kFill_Style, kStroke_Style, kStrokeAndFill_Style }

    /** Mirrors Skia's `SkPaint::Cap`. Phase 3c implements `kButt_Cap` only. */
    public enum class Cap { kButt_Cap, kRound_Cap, kSquare_Cap }

    /** Mirrors Skia's `SkPaint::Join`. Phase 3c implements `kMiter_Join` (with bevel fallback) only. */
    public enum class Join { kMiter_Join, kRound_Join, kBevel_Join }

    public var color: SkColor = SK_ColorBLACK
    public var style: Style = Style.kFill_Style
    public var strokeWidth: Float = 0f
    public var strokeCap: Cap = Cap.kButt_Cap
    public var strokeJoin: Join = Join.kMiter_Join
    /** Mirrors Skia's default `SkPaint::kDefault_MiterLimit = 4`. */
    public var strokeMiter: Float = 4f
    public var isAntiAlias: Boolean = false

    /**
     * Phase 5a: a [SkShader] (linear / radial gradient, future bitmap
     * shader) that supplies the source colour per pixel. When non-`null`,
     * [color] is ignored (matches Skia's `SkPaint::setShader`).
     */
    public var shader: SkShader? = null

    public constructor(color: SkColor) : this() {
        this.color = color
    }

    public fun copy(): SkPaint = SkPaint().also {
        it.color = color
        it.style = style
        it.strokeWidth = strokeWidth
        it.strokeCap = strokeCap
        it.strokeJoin = strokeJoin
        it.strokeMiter = strokeMiter
        it.isAntiAlias = isAntiAlias
        it.shader = shader
    }
}
