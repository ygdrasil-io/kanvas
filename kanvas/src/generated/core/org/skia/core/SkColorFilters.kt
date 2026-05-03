package org.skia.core

import SkColor4f
import kotlin.Array
import kotlin.Float
import kotlin.FloatArray
import kotlin.Int
import kotlin.UByte
import org.skia.effects.SkColorMatrix
import org.skia.foundation.SkColor
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SK_API SkColorFilters {
 * public:
 *     static sk_sp<SkColorFilter> Compose(const sk_sp<SkColorFilter>& outer,
 *                                         sk_sp<SkColorFilter> inner) {
 *         return outer ? outer->makeComposed(std::move(inner))
 *                      : std::move(inner);
 *     }
 *
 *     // Blends between the constant color (src) and input color (dst) based on the SkBlendMode.
 *     // If the color space is null, the constant color is assumed to be defined in sRGB.
 *     static sk_sp<SkColorFilter> Blend(const SkColor4f& c, sk_sp<SkColorSpace>, SkBlendMode mode);
 *     static sk_sp<SkColorFilter> Blend(SkColor c, SkBlendMode mode);
 *
 *     enum class Clamp : bool { kNo, kYes };
 *
 *     static sk_sp<SkColorFilter> Matrix(const SkColorMatrix&, Clamp clamp = Clamp::kYes);
 *     static sk_sp<SkColorFilter> Matrix(const float rowMajor[20], Clamp clamp = Clamp::kYes);
 *
 *     // A version of Matrix which operates in HSLA space instead of RGBA.
 *     // I.e. HSLA-to-RGBA(Matrix(RGBA-to-HSLA(input))).
 *     static sk_sp<SkColorFilter> HSLAMatrix(const SkColorMatrix&);
 *     static sk_sp<SkColorFilter> HSLAMatrix(const float rowMajor[20]);
 *
 *     static sk_sp<SkColorFilter> LinearToSRGBGamma();
 *     static sk_sp<SkColorFilter> SRGBToLinearGamma();
 *     static sk_sp<SkColorFilter> Lerp(float t, sk_sp<SkColorFilter> dst, sk_sp<SkColorFilter> src);
 *
 *     /**
 *      *  Create a table colorfilter, copying the table into the filter, and
 *      *  applying it to all 4 components.
 *      *      a' = table[a];
 *      *      r' = table[r];
 *      *      g' = table[g];
 *      *      b' = table[b];
 *      *  Components are operated on in unpremultiplied space. If the incomming
 *      *  colors are premultiplied, they are temporarily unpremultiplied, then
 *      *  the table is applied, and then the result is remultiplied.
 *      */
 *     static sk_sp<SkColorFilter> Table(const uint8_t table[256]);
 *
 *     /**
 *      *  Create a table colorfilter, with a different table for each
 *      *  component [A, R, G, B]. If a given table is NULL, then it is
 *      *  treated as identity, with the component left unchanged. If a table
 *      *  is not null, then its contents are copied into the filter.
 *      */
 *     static sk_sp<SkColorFilter> TableARGB(const uint8_t tableA[256],
 *                                           const uint8_t tableR[256],
 *                                           const uint8_t tableG[256],
 *                                           const uint8_t tableB[256]);
 *
 *     /**
 *      * Create a table colorfilter that holds a ref to the shared color table.
 *      */
 *     static sk_sp<SkColorFilter> Table(sk_sp<SkColorTable> table);
 *
 *     /**
 *      *  Create a colorfilter that multiplies the RGB channels by one color, and
 *      *  then adds a second color, pinning the result for each component to
 *      *  [0..255]. The alpha components of the mul and add arguments
 *      *  are ignored.
 *      */
 *     static sk_sp<SkColorFilter> Lighting(SkColor mul, SkColor add);
 *
 * private:
 *     SkColorFilters() = delete;
 * }
 * ```
 */
public open class SkColorFilters public constructor() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilter> SkColorFilters::Matrix(const float array[20], Clamp clamp) {
   *     return MakeMatrix(array, SkMatrixColorFilter::Domain::kRGBA, clamp);
   * }
   * ```
   */
  public fun matrix(array: FloatArray, clamp: Clamp): SkSp<SkColorFilter> {
    TODO("Implement matrix")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilter> SkColorFilters::Matrix(const SkColorMatrix& cm, Clamp clamp) {
   *     return MakeMatrix(cm.fMat.data(), SkMatrixColorFilter::Domain::kRGBA, clamp);
   * }
   * ```
   */
  public fun matrix(cm: SkColorMatrix, clamp: Clamp): SkSp<SkColorFilter> {
    TODO("Implement matrix")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilter> SkColorFilters::HSLAMatrix(const float array[20]) {
   *     return MakeMatrix(array, SkMatrixColorFilter::Domain::kHSLA, Clamp::kYes);
   * }
   * ```
   */
  public fun hSLAMatrix(array: FloatArray): SkSp<SkColorFilter> {
    TODO("Implement hSLAMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilter> SkColorFilters::HSLAMatrix(const SkColorMatrix& cm) {
   *     return MakeMatrix(cm.fMat.data(), SkMatrixColorFilter::Domain::kHSLA, Clamp::kYes);
   * }
   * ```
   */
  public fun hSLAMatrix(cm: SkColorMatrix): SkSp<SkColorFilter> {
    TODO("Implement hSLAMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilter> SkColorFilters::Table(const uint8_t table[256]) {
   *     return SkColorFilters::Table(SkColorTable::Make(table));
   * }
   * ```
   */
  public fun table(table: Array<UByte>): SkSp<SkColorFilter> {
    TODO("Implement table")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilter> SkColorFilters::Table(sk_sp<SkColorTable> table) {
   *     if (!table) {
   *         return nullptr;
   *     }
   *     return sk_make_sp<SkTableColorFilter>(std::move(table));
   * }
   * ```
   */
  public fun table(table: SkSp<SkColorTable>): SkSp<SkColorFilter> {
    TODO("Implement table")
  }

  public enum class Clamp {
    kNo,
    kYes,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkColorFilter> Compose(const sk_sp<SkColorFilter>& outer,
     *                                         sk_sp<SkColorFilter> inner) {
     *         return outer ? outer->makeComposed(std::move(inner))
     *                      : std::move(inner);
     *     }
     * ```
     */
    public fun compose(outer: SkSp<SkColorFilter>, `inner`: SkSp<SkColorFilter>): Int {
      TODO("Implement compose")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkColorFilter> SkColorFilters::Blend(const SkColor4f& color,
     *                                            sk_sp<SkColorSpace> colorSpace,
     *                                            SkBlendMode mode) {
     *     if (!SkIsValidMode(mode)) {
     *         return nullptr;
     *     }
     *
     *     // First map to sRGB to simplify storage in the actual SkColorFilter instance, staying unpremul
     *     // until the final dst color space is known when actually filtering. Also pin the alpha to [0,1]
     *     SkColor4f srgb = color.pinAlpha();
     *     SkColorSpaceXformSteps(colorSpace.get(),    kUnpremul_SkAlphaType,
     *                            sk_srgb_singleton(), kUnpremul_SkAlphaType).apply(srgb.vec());
     *
     *     // Next collapse some modes if possible
     *     float alpha = srgb.fA;
     *     if (SkBlendMode::kClear == mode) {
     *         srgb = SkColors::kTransparent;
     *         mode = SkBlendMode::kSrc;
     *     } else if (SkBlendMode::kSrcOver == mode) {
     *         if (0.f == alpha) {
     *             mode = SkBlendMode::kDst;
     *         } else if (1.f == alpha) {
     *             mode = SkBlendMode::kSrc;
     *         }
     *         // else just stay srcover
     *     }
     *
     *     // Finally weed out combinations that are noops, and just return null
     *     if (SkBlendMode::kDst == mode ||
     *         (0.f == alpha && (SkBlendMode::kSrcOver == mode ||
     *                           SkBlendMode::kDstOver == mode ||
     *                           SkBlendMode::kDstOut == mode ||
     *                           SkBlendMode::kSrcATop == mode ||
     *                           SkBlendMode::kXor == mode ||
     *                           SkBlendMode::kDarken == mode)) ||
     *             (1.f == alpha && SkBlendMode::kDstIn == mode)) {
     *         return nullptr;
     *     }
     *
     *     return sk_sp<SkColorFilter>(new SkBlendModeColorFilter(srgb, mode));
     * }
     * ```
     */
    public fun blend(
      c: SkColor4f,
      colorSpace: SkSp<SkColorSpace>,
      mode: SkBlendMode,
    ): Int {
      TODO("Implement blend")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkColorFilter> SkColorFilters::Blend(SkColor color, SkBlendMode mode) {
     *     return Blend(SkColor4f::FromColor(color), /*sRGB*/ nullptr, mode);
     * }
     * ```
     */
    public fun blend(c: SkColor, mode: SkBlendMode): Int {
      TODO("Implement blend")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkColorFilter> Matrix(const SkColorMatrix&, Clamp clamp = Clamp::kYes)
     * ```
     */
    public fun matrix(param0: SkColorMatrix, clamp: Clamp = TODO()): Int {
      TODO("Implement matrix")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkColorFilter> Matrix(const float rowMajor[20], Clamp clamp = Clamp::kYes)
     * ```
     */
    public fun matrix(rowMajor: FloatArray, clamp: Clamp = TODO()): Int {
      TODO("Implement matrix")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkColorFilter> HSLAMatrix(const SkColorMatrix&)
     * ```
     */
    public fun hSLAMatrix(param0: SkColorMatrix): Int {
      TODO("Implement hSLAMatrix")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkColorFilter> HSLAMatrix(const float rowMajor[20])
     * ```
     */
    public fun hSLAMatrix(rowMajor: FloatArray): Int {
      TODO("Implement hSLAMatrix")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkColorFilter> SkColorFilters::LinearToSRGBGamma() {
     *     static SkNoDestructor<SkColorSpaceXformColorFilter> gSingleton(SkColorSpace::MakeSRGBLinear(),
     *                                                                    SkColorSpace::MakeSRGB());
     *     return sk_ref_sp(gSingleton.get());
     * }
     * ```
     */
    public fun linearToSRGBGamma(): Int {
      TODO("Implement linearToSRGBGamma")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkColorFilter> SkColorFilters::SRGBToLinearGamma() {
     *     static SkNoDestructor<SkColorSpaceXformColorFilter> gSingleton(SkColorSpace::MakeSRGB(),
     *                                                                    SkColorSpace::MakeSRGBLinear());
     *     return sk_ref_sp(gSingleton.get());
     * }
     * ```
     */
    public fun sRGBToLinearGamma(): Int {
      TODO("Implement sRGBToLinearGamma")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkColorFilter> SkColorFilters::Lerp(float weight, sk_sp<SkColorFilter> cf0,
     *                                                         sk_sp<SkColorFilter> cf1) {
     *     using namespace SkKnownRuntimeEffects;
     *
     *     if (!cf0 && !cf1) {
     *         return nullptr;
     *     }
     *     if (SkIsNaN(weight)) {
     *         return nullptr;
     *     }
     *
     *     if (cf0 == cf1) {
     *         return cf0; // or cf1
     *     }
     *
     *     if (weight <= 0) {
     *         return cf0;
     *     }
     *     if (weight >= 1) {
     *         return cf1;
     *     }
     *
     *     const SkRuntimeEffect* lerpEffect = GetKnownRuntimeEffect(StableKey::kLerp);
     *
     *     sk_sp<SkColorFilter> inputs[] = {cf0,cf1};
     *     return lerpEffect->makeColorFilter(SkData::MakeWithCopy(&weight, sizeof(weight)),
     *                                        inputs, std::size(inputs));
     * }
     * ```
     */
    public fun lerp(
      t: Float,
      dst: SkSp<SkColorFilter>,
      src: SkSp<SkColorFilter>,
    ): Int {
      TODO("Implement lerp")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkColorFilter> Table(const uint8_t table[256])
     * ```
     */
    public fun table(table: Array<UByte>): Int {
      TODO("Implement table")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkColorFilter> SkColorFilters::TableARGB(const uint8_t tableA[256],
     *                                                const uint8_t tableR[256],
     *                                                const uint8_t tableG[256],
     *                                                const uint8_t tableB[256]) {
     *     return SkColorFilters::Table(SkColorTable::Make(tableA, tableR, tableG, tableB));
     * }
     * ```
     */
    public fun tableARGB(
      tableA: Array<UByte>,
      tableR: Array<UByte>,
      tableG: Array<UByte>,
      tableB: Array<UByte>,
    ): Int {
      TODO("Implement tableARGB")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkColorFilter> Table(sk_sp<SkColorTable> table)
     * ```
     */
    public fun table(table: SkSp<SkColorTable>): Int {
      TODO("Implement table")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkColorFilter> SkColorFilters::Lighting(SkColor mul, SkColor add) {
     *     const SkColor opaqueAlphaMask = SK_ColorBLACK;
     *     // omit the alpha and compare only the RGB values
     *     if (0 == (add & ~opaqueAlphaMask)) {
     *         return SkColorFilters::Blend(mul | opaqueAlphaMask, SkBlendMode::kModulate);
     *     }
     *
     *     SkColorMatrix matrix;
     *     matrix.setScale(byte_to_unit_float(SkColorGetR(mul)),
     *                     byte_to_unit_float(SkColorGetG(mul)),
     *                     byte_to_unit_float(SkColorGetB(mul)),
     *                     1);
     *     matrix.postTranslate(byte_to_unit_float(SkColorGetR(add)),
     *                          byte_to_unit_float(SkColorGetG(add)),
     *                          byte_to_unit_float(SkColorGetB(add)),
     *                          0);
     *     return SkColorFilters::Matrix(matrix);
     * }
     * ```
     */
    public fun lighting(mul: SkColor, add: SkColor): Int {
      TODO("Implement lighting")
    }
  }
}
