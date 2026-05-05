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
 * **Storage** : colour is held internally as [SkColor4f] (4 floats) —
 * matches Skia's `fColor4f` field, which is the source of truth for
 * paint colour. The [color] / [alpha] / [alphaf] / [color4f] properties
 * all read from and write to that single backing field.
 * `setAlphaf(0.3f)` therefore preserves the float value exactly instead
 * of round-tripping through a packed byte (which would quantise to
 * `77/255 ≈ 0.30196`). See [MIGRATION_PLAN_PAINT_PARITY.md] Phase 2.
 *
 * **Out of scope** (not yet ported): `SkColorFilter`, `SkPathEffect`,
 * `SkMaskFilter`, `SkImageFilter`, `SkBlender`. Stroker honours the
 * full Cap/Join enum.
 */
public class SkPaint() {
    public enum class Style { kFill_Style, kStroke_Style, kStrokeAndFill_Style }

    public enum class Cap { kButt_Cap, kRound_Cap, kSquare_Cap }

    public enum class Join { kMiter_Join, kRound_Join, kBevel_Join }

    /**
     * Source-of-truth colour, mirrored on Skia's `SkPaint::fColor4f`
     * (`src/core/SkPaint.cpp`, default-init `{0, 0, 0, 1}` = opaque black).
     * Private — callers go through [color] / [color4f] / [alpha] / [alphaf].
     */
    private var fColor4f: SkColor4f = SkColor4f(0f, 0f, 0f, 1f)

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

    /** Mirrors Skia's `SkPaint(SkColor)` (kept as a Kotlin convenience). */
    public constructor(color: SkColor) : this() { this.color = color }

    /**
     * Mirrors Skia's `explicit SkPaint(const SkColor4f&, SkColorSpace*)`
     * (`src/core/SkPaint.cpp`). The colour-space transform is currently
     * a stub — see [setColor4f] (Slice 2.5 follow-up).
     */
    public constructor(color: SkColor4f, colorSpace: SkColorSpace? = null) : this() {
        setColor4f(color, colorSpace)
    }

    // ─── Colour access ──────────────────────────────────────────────

    /**
     * Packed ARGB byte colour. Mirrors Skia's `getColor()` (`fColor4f.toSkColor()`)
     * and `setColor(SkColor)` (`fColor4f = SkColor4f::FromColor(color)`).
     * The setter byte-quantises any prior float precision; callers wanting
     * full precision should use [color4f] / [alphaf] / [setColor4f].
     */
    public var color: SkColor
        get() = fColor4f.toSkColor()
        set(value) { fColor4f = SkColor4f.FromColor(value) }

    /**
     * Float-precision colour (4 channels, unpremul). Read returns a fresh
     * copy so external mutation cannot corrupt internal state; write copies
     * defensively, matching Skia's by-value semantics.
     */
    public var color4f: SkColor4f
        get() = fColor4f.copy()
        set(value) { fColor4f = value.copy() }

    /**
     * Mirrors Skia's `setColor(const SkColor4f&, SkColorSpace*)`
     * (`src/core/SkPaint.cpp`). Today the colour-space xform is a stub —
     * we keep the value as-is and pin alpha to `[0, 1]`. Once
     * [SkColorSpaceXformSteps.apply] is wired to a `FloatArray` view,
     * this should call `steps.apply(fColor4f.vec())` (Slice 2.5).
     */
    public fun setColor4f(color: SkColor4f, colorSpace: SkColorSpace? = null) {
        fColor4f = color.pinAlpha()
        // TODO Slice 2.5: apply SkColorSpaceXformSteps(colorSpace -> sRGB).
    }

    // ─── Alpha helpers ───────────────────────────────────────────────

    /**
     * Alpha component as a byte `[0, 255]` derived from [color4f].
     * Read mirrors Skia's `getAlpha()` (`round(getAlphaf() * 255)`);
     * write mirrors `setAlpha(U8CPU)` (`setAlphaf(a / 255)`).
     */
    public var alpha: Int
        get() = (fColor4f.fA * 255f + 0.5f).toInt().coerceIn(0, 255)
        set(value) { fColor4f.fA = (value and 0xFF) / 255f }

    /**
     * Alpha component as a float `[0, 1]`. Read returns the exact stored
     * float (no byte round-trip); write pins to `[0, 1]` and stores
     * directly in `fColor4f.fA`. Iso with Skia's `getAlphaf()` /
     * `setAlphaf(SkTPin(a, 0, 1))`.
     */
    public var alphaf: Float
        get() = fColor4f.fA
        set(value) { fColor4f.fA = value.coerceIn(0f, 1f) }

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
        fColor4f = SkColor4f(0f, 0f, 0f, 1f)
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
        it.fColor4f = fColor4f.copy()
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
        return fColor4f == other.fColor4f &&
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
        var result = fColor4f.hashCode()
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
