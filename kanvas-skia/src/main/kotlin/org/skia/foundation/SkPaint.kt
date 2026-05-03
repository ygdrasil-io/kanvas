package org.skia.foundation

public class SkPaint() {
    public enum class Style { kFill_Style, kStroke_Style, kStrokeAndFill_Style }

    public var color: SkColor = SK_ColorBLACK
    public var style: Style = Style.kFill_Style
    public var strokeWidth: Float = 0f
    public var isAntiAlias: Boolean = false

    public constructor(color: SkColor) : this() {
        this.color = color
    }

    public fun copy(): SkPaint = SkPaint().also {
        it.color = color
        it.style = style
        it.strokeWidth = strokeWidth
        it.isAntiAlias = isAntiAlias
    }
}
