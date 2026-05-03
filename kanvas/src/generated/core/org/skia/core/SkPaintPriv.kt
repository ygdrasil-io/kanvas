package org.skia.core

import kotlin.Boolean
import org.skia.foundation.SkColor
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkWriteBuffer

/**
 * C++ original:
 * ```cpp
 * class SkPaintPriv {
 * public:
 *     enum ShaderOverrideOpacity {
 *         kNone_ShaderOverrideOpacity,        //!< there is no overriding shader (bitmap or image)
 *         kOpaque_ShaderOverrideOpacity,      //!< the overriding shader is opaque
 *         kNotOpaque_ShaderOverrideOpacity,   //!< the overriding shader may not be opaque
 *     };
 *
 *     /**
 *      *  Returns true if drawing with this paint (or nullptr) will ovewrite all affected pixels.
 *      *
 *      *  Note: returns conservative true, meaning it may return false even though the paint might
 *      *        in fact overwrite its pixels.
 *      */
 *     static bool Overwrites(const SkPaint* paint, ShaderOverrideOpacity);
 *
 *     static bool ShouldDither(const SkPaint&, SkColorType);
 *
 *     /*
 *      * The luminance color is used to determine which Gamma Canonical color to map to.  This is
 *      * really only used by backends which want to cache glyph masks, and need some way to know if
 *      * they need to generate new masks based off a given color.
 *      */
 *     static SkColor ComputeLuminanceColor(const SkPaint&);
 *
 *     /** Serializes SkPaint into a buffer. A companion unflatten() call
 *     can reconstitute the paint at a later time.
 *
 *     @param buffer  SkWriteBuffer receiving the flattened SkPaint data
 *     */
 *     static void Flatten(const SkPaint& paint, SkWriteBuffer& buffer);
 *
 *     /** Populates SkPaint, typically from a serialized stream, created by calling
 *         flatten() at an earlier time.
 *     */
 *     static SkPaint Unflatten(SkReadBuffer& buffer);
 *
 *     // If this paint has any color filter, fold it into the shader and/or paint color
 *     // so that it draws the same but getColorFilter() returns nullptr.
 *     //
 *     // Since we may be filtering now, we need to know what color space to filter in,
 *     // typically the color space of the device we're drawing into.
 *     static void RemoveColorFilter(SkPaint*, SkColorSpace* dstCS);
 *
 * }
 * ```
 */
public open class SkPaintPriv {
  public enum class ShaderOverrideOpacity {
    kNone_ShaderOverrideOpacity,
    kOpaque_ShaderOverrideOpacity,
    kNotOpaque_ShaderOverrideOpacity,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * bool SkPaintPriv::Overwrites(const SkPaint* paint, ShaderOverrideOpacity overrideOpacity) {
     *     if (!paint) {
     *         // No paint means we default to SRC_OVER, so we overwrite iff our shader-override
     *         // is opaque, or we don't have one.
     *         return overrideOpacity != kNotOpaque_ShaderOverrideOpacity;
     *     }
     *
     *     SrcColorOpacity opacityType = kUnknown_SrcColorOpacity;
     *
     *     if (!changes_alpha(*paint)) {
     *         const unsigned paintAlpha = paint->getAlpha();
     *         if (0xff == paintAlpha && overrideOpacity != kNotOpaque_ShaderOverrideOpacity &&
     *             (!paint->getShader() || paint->getShader()->isOpaque())) {
     *             opacityType = kOpaque_SrcColorOpacity;
     *         } else if (0 == paintAlpha) {
     *             if (overrideOpacity == kNone_ShaderOverrideOpacity && !paint->getShader()) {
     *                 opacityType = kTransparentBlack_SrcColorOpacity;
     *             } else {
     *                 opacityType = kTransparentAlpha_SrcColorOpacity;
     *             }
     *         }
     *     }
     *
     *     const auto bm = paint->asBlendMode();
     *     if (!bm) {
     *         return false;   // don't know for sure, so we play it safe and return false.
     *     }
     *     return blend_mode_is_opaque(bm.value(), opacityType);
     * }
     * ```
     */
    public fun overwrites(paint: SkPaint?, overrideOpacity: ShaderOverrideOpacity): Boolean {
      TODO("Implement overwrites")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkPaintPriv::ShouldDither(const SkPaint& p, SkColorType dstCT) {
     *     // The paint dither flag can veto.
     *     if (!p.isDither()) {
     *         return false;
     *     }
     *
     *     if (dstCT == kUnknown_SkColorType) {
     *         return false;
     *     }
     *
     *     // We always dither 565 or 4444 when requested.
     *     if (dstCT == kRGB_565_SkColorType || dstCT == kARGB_4444_SkColorType) {
     *         return true;
     *     }
     *
     *     // Otherwise, dither is only needed for non-const paints.
     *     return p.getImageFilter() || p.getMaskFilter() ||
     *            (p.getShader() && !as_SB(p.getShader())->isConstant());
     * }
     * ```
     */
    public fun shouldDither(p: SkPaint, dstCT: SkColorType): Boolean {
      TODO("Implement shouldDither")
    }

    /**
     * C++ original:
     * ```cpp
     * SkColor SkPaintPriv::ComputeLuminanceColor(const SkPaint& paint) {
     *     SkColor4f c;
     *     if (!just_a_color(paint, &c)) {
     *         c = { 0.5f, 0.5f, 0.5f, 1.0f};
     *     }
     *     return c.toSkColor();
     * }
     * ```
     */
    public fun computeLuminanceColor(paint: SkPaint): SkColor {
      TODO("Implement computeLuminanceColor")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkPaintPriv::Flatten(const SkPaint& paint, SkWriteBuffer& buffer) {
     *     uint8_t flatFlags = 0;
     *
     *     if (paint.getPathEffect() ||
     *         paint.getShader() ||
     *         paint.getMaskFilter() ||
     *         paint.getColorFilter() ||
     *         paint.getImageFilter() ||
     *         !paint.asBlendMode()) {
     *         flatFlags |= kHasEffects_FlatFlag;
     *     }
     *
     *     buffer.writeScalar(paint.getStrokeWidth());
     *     buffer.writeScalar(paint.getStrokeMiter());
     *     buffer.writeColor4f(paint.getColor4f());
     *
     *     buffer.write32(pack_v68(paint, flatFlags));
     *
     *     if (flatFlags & kHasEffects_FlatFlag) {
     *         buffer.writeFlattenable(paint.getPathEffect());
     *         buffer.writeFlattenable(paint.getShader());
     *         buffer.writeFlattenable(paint.getMaskFilter());
     *         buffer.writeFlattenable(paint.getColorFilter());
     *         buffer.writeFlattenable(paint.getImageFilter());
     *         buffer.writeFlattenable(paint.getBlender());
     *     }
     * }
     * ```
     */
    public fun flatten(paint: SkPaint, buffer: SkWriteBuffer) {
      TODO("Implement flatten")
    }

    /**
     * C++ original:
     * ```cpp
     * SkPaint SkPaintPriv::Unflatten(SkReadBuffer& buffer) {
     *     SkPaint paint;
     *
     *     paint.setStrokeWidth(buffer.readScalar());
     *     paint.setStrokeMiter(buffer.readScalar());
     *     {
     *         SkColor4f color;
     *         buffer.readColor4f(&color);
     *         paint.setColor(color, sk_srgb_singleton());
     *     }
     *
     *     SkSafeRange safe;
     *     unsigned flatFlags = unpack_v68(&paint, buffer.readUInt(), safe);
     *
     *     if (!(flatFlags & kHasEffects_FlatFlag)) {
     *         // This is a simple SkPaint without any effects, so clear all the effect-related fields.
     *         paint.setPathEffect(nullptr);
     *         paint.setShader(nullptr);
     *         paint.setMaskFilter(nullptr);
     *         paint.setColorFilter(nullptr);
     *         paint.setImageFilter(nullptr);
     *     } else if (buffer.isVersionLT(SkPicturePriv::kSkBlenderInSkPaint)) {
     *         // This paint predates the introduction of user blend functions (via SkBlender).
     *         paint.setPathEffect(buffer.readPathEffect());
     *         paint.setShader(buffer.readShader());
     *         paint.setMaskFilter(buffer.readMaskFilter());
     *         paint.setColorFilter(buffer.readColorFilter());
     *         (void)buffer.read32();  // was drawLooper (now deprecated)
     *         paint.setImageFilter(buffer.readImageFilter());
     *     } else {
     *         paint.setPathEffect(buffer.readPathEffect());
     *         paint.setShader(buffer.readShader());
     *         paint.setMaskFilter(buffer.readMaskFilter());
     *         paint.setColorFilter(buffer.readColorFilter());
     *         paint.setImageFilter(buffer.readImageFilter());
     *         paint.setBlender(buffer.readBlender());
     *     }
     *
     *     if (!buffer.validate(safe.ok())) {
     *         paint.reset();
     *     }
     *     return paint;
     * }
     * ```
     */
    public fun unflatten(buffer: SkReadBuffer): SkPaint {
      TODO("Implement unflatten")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkPaintPriv::RemoveColorFilter(SkPaint* p, SkColorSpace* dstCS) {
     *     if (SkColorFilter* filter = p->getColorFilter()) {
     *         if (SkShader* shader = p->getShader()) {
     *             // SkColorFilterShader will modulate the shader color by paint alpha
     *             // before applying the filter, so we'll reset it to opaque.
     *             p->setShader(SkColorFilterShader::Make(sk_ref_sp(shader),
     *                                                    p->getAlphaf(),
     *                                                    sk_ref_sp(filter)));
     *             p->setAlphaf(1.0f);
     *         } else {
     *             p->setColor(filter->filterColor4f(p->getColor4f(), sk_srgb_singleton(), dstCS), dstCS);
     *         }
     *         p->setColorFilter(nullptr);
     *     }
     * }
     * ```
     */
    public fun removeColorFilter(p: SkPaint?, dstCS: SkColorSpace?) {
      TODO("Implement removeColorFilter")
    }
  }
}
