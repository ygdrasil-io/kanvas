package org.skia.foundation

import org.skia.math.SkScalar

/**
 * Iso-aligned port of Skia's `SkPaint`
 * ([include/core/SkPaint.h](https://github.com/google/skia/blob/main/include/core/SkPaint.h)).
 *
 * Holds drawing-style state: color, fill/stroke style, stroke parameters,
 * AA flag, blend mode, optional shader. Skia exposes its API as
 * `getColor()`/`setColor()` etc.; the Kotlin port keeps those at the
 * JVM bytecode level via `var` properties — `paint.color` from Kotlin,
 * `paint.getColor()` / `paint.setColor()` from Java code.
 *
 * **Out of scope** (not yet ported): `SkColorFilter`, `SkPathEffect`,
 * `SkMaskFilter`, `SkImageFilter`, `SkBlender`, `Cap::kRound_Cap`
 * already shipped, `kRound_Join` already shipped. Stroker honours the
 * full Cap/Join enum.
 */
public class SkPaint() {
    public enum class Style { kFill_Style, kStroke_Style, kStrokeAndFill_Style }

    public enum class Cap { kButt_Cap, kRound_Cap, kSquare_Cap }

    public enum class Join { kMiter_Join, kRound_Join, kBevel_Join }

    public var color: SkColor = SK_ColorBLACK
    public var style: Style = Style.kFill_Style
    public var strokeWidth: SkScalar = 0f
    public var strokeCap: Cap = Cap.kButt_Cap
    public var strokeJoin: Join = Join.kMiter_Join
    /** Mirrors Skia's `SkPaint::kDefault_MiterLimit = 4`. */
    public var strokeMiter: SkScalar = 4f
    public var isAntiAlias: Boolean = false

    /** Iso-aligned — Skia's `setDither`. Does not currently affect rendering. */
    public var isDither: Boolean = false

    /**
     * Phase 5a: a [SkShader] (linear / radial gradient, future bitmap
     * shader) supplying source colour per pixel. When non-`null`,
     * [color] is ignored (matches Skia's `SkPaint::setShader`).
     */
    public var shader: SkShader? = null

    /**
     * Phase 6 entry: compositing rule. Defaults to [SkBlendMode.kSrcOver].
     */
    public var blendMode: SkBlendMode = SkBlendMode.kSrcOver

    public constructor(color: SkColor) : this() { this.color = color }

    // ─── Alpha helpers ───────────────────────────────────────────────

    /** Alpha component as a byte `[0, 255]` derived from [color]. */
    public var alpha: Int
        get() = SkColorGetA(color)
        set(value) { color = SkColorSetA(color, value and 0xFF) }

    /** Alpha component as a float `[0, 1]` derived from [color]. */
    public var alphaf: Float
        get() = alpha / 255f
        set(value) {
            val byte = (value.coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)
            alpha = byte
        }

    // ─── Color helpers ───────────────────────────────────────────────

    /** Read [color] as a normalised float quadruple. */
    public var color4f: SkColor4f
        get() = SkColor4f.FromColor(color)
        set(value) { color = value.toSkColor() }

    /** Set color from packed ARGB byte components. */
    public fun setARGB(a: Int, r: Int, g: Int, b: Int) {
        color = SkColorSetARGB(a, r, g, b)
    }

    // ─── Style helpers ───────────────────────────────────────────────

    /** Convenience for `style = if (stroke) kStroke_Style else kFill_Style`. */
    public fun setStroke(stroke: Boolean) {
        style = if (stroke) Style.kStroke_Style else Style.kFill_Style
    }

    // ─── Blend ──────────────────────────────────────────────────────

    /** `true` if [blendMode] is the default `kSrcOver`. */
    public fun isSrcOver(): Boolean = blendMode == SkBlendMode.kSrcOver

    /**
     * Returns `true` when the paint is a no-op draw: alpha 0 + a blend
     * mode where transparent source produces transparent dest. Mirrors
     * a subset of Skia's `SkPaint::nothingToDraw`. Conservative: returns
     * `true` only for the cases we can prove statically; real Skia also
     * checks color filters / image filters which the Kotlin port does
     * not yet model.
     */
    public fun nothingToDraw(): Boolean {
        if (shader != null) return false
        if (alpha != 0) return false
        return when (blendMode) {
            SkBlendMode.kSrcOver, SkBlendMode.kDstOver, SkBlendMode.kSrcATop,
            SkBlendMode.kDstOut, SkBlendMode.kXor, SkBlendMode.kPlus,
            -> true
            else -> false
        }
    }

    // ─── Lifecycle ──────────────────────────────────────────────────

    /** Reset to default-constructed state. Mirrors Skia's `SkPaint::reset`. */
    public fun reset() {
        color = SK_ColorBLACK
        style = Style.kFill_Style
        strokeWidth = 0f
        strokeCap = Cap.kButt_Cap
        strokeJoin = Join.kMiter_Join
        strokeMiter = 4f
        isAntiAlias = false
        isDither = false
        shader = null
        blendMode = SkBlendMode.kSrcOver
    }

    public fun copy(): SkPaint = SkPaint().also {
        it.color = color
        it.style = style
        it.strokeWidth = strokeWidth
        it.strokeCap = strokeCap
        it.strokeJoin = strokeJoin
        it.strokeMiter = strokeMiter
        it.isAntiAlias = isAntiAlias
        it.isDither = isDither
        it.shader = shader
        it.blendMode = blendMode
    }

    // ─── Equality ───────────────────────────────────────────────────

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SkPaint) return false
        return color == other.color &&
            style == other.style &&
            strokeWidth == other.strokeWidth &&
            strokeCap == other.strokeCap &&
            strokeJoin == other.strokeJoin &&
            strokeMiter == other.strokeMiter &&
            isAntiAlias == other.isAntiAlias &&
            isDither == other.isDither &&
            shader === other.shader &&
            blendMode == other.blendMode
    }

    override fun hashCode(): Int {
        var result = color
        result = 31 * result + style.hashCode()
        result = 31 * result + strokeWidth.hashCode()
        result = 31 * result + strokeCap.hashCode()
        result = 31 * result + strokeJoin.hashCode()
        result = 31 * result + strokeMiter.hashCode()
        result = 31 * result + isAntiAlias.hashCode()
        result = 31 * result + isDither.hashCode()
        result = 31 * result + (shader?.hashCode() ?: 0)
        result = 31 * result + blendMode.hashCode()
        return result
    }
}
