package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkColor
import org.skia.foundation.SkRefCnt
import org.skia.math.SkScalar
import undefined.PreBlend

/**
 * C++ original:
 * ```cpp
 * class SkTMaskGamma : public SkRefCnt {
 *
 * public:
 *
 *     /** Creates a linear SkTMaskGamma. */
 *     constexpr SkTMaskGamma() {}
 *
 *     /**
 *      * Creates tables to convert linear alpha values to gamma correcting alpha
 *      * values.
 *      *
 *      * @param contrast A value in the range [0.0, 1.0] which indicates the
 *      *                 amount of artificial contrast to add.
 *      * @param device The color space of the target device.
 *      */
 *     SkTMaskGamma(SkScalar contrast, SkScalar deviceGamma)
 *         : fGammaTables(std::make_unique<uint8_t[]>(kTableNumElements))
 *     {
 *         const SkColorSpaceLuminance& deviceConvert = SkColorSpaceLuminance::Fetch(deviceGamma);
 *         for (U8CPU i = 0; i < kNumTables; ++i) {
 *             U8CPU lum = sk_t_scale255<kMaxLumBits>(i);
 *             SkTMaskGamma_build_correcting_lut(&fGammaTables[i * kTableWidth], lum, contrast,
 *                                               deviceConvert, deviceGamma);
 *         }
 *     }
 *
 *     /** Given a color, returns the closest canonical color. */
 *     static SkColor CanonicalColor(SkColor color) {
 *         return SkColorSetRGB(
 *                    sk_t_scale255<R_LUM_BITS>(SkColorGetR(color) >> (8 - R_LUM_BITS)),
 *                    sk_t_scale255<G_LUM_BITS>(SkColorGetG(color) >> (8 - G_LUM_BITS)),
 *                    sk_t_scale255<B_LUM_BITS>(SkColorGetB(color) >> (8 - B_LUM_BITS)));
 *     }
 *
 *     /** The type of the mask pre-blend which will be returned from preBlend(SkColor). */
 *     typedef SkTMaskPreBlend<R_LUM_BITS, G_LUM_BITS, B_LUM_BITS> PreBlend;
 *
 *     /**
 *      * Provides access to the tables appropriate for converting linear alpha
 *      * values into gamma correcting alpha values when drawing the given color
 *      * through the mask. The destination color will be approximated.
 *      */
 *     PreBlend preBlend(SkColor color) const;
 *
 *     /**
 *      * Get dimensions for the full table set, so it can be allocated as a block. Linear
 *      * tables should report the full table size.
 *      */
 *     void getGammaTableDimensions(int* tableWidth, int* numTables) const {
 *         *tableWidth = kTableWidth;
 *         *numTables = kNumTables;
 *     }
 *
 *     /**
 *      * Returns the size for the full table set in bytes, so it can be allocated as a block.
 *      * Linear tables should report the full table size.
 *      */
 *     constexpr size_t getGammaTableSizeInBytes() const {
 *         return kTableNumElements * sizeof(uint8_t);
 *     }
 *
 *     /**
 *      * Provides direct access to the full table set, so it can be uploaded
 *      * into a texture or analyzed in other ways.
 *      * Returns nullptr if fGammaTables hasn't been initialized.
 *      */
 *     const uint8_t* getGammaTables() const {
 *         return fGammaTables.get();
 *     }
 *
 * private:
 *     static constexpr int kMaxLumBits = std::max({B_LUM_BITS, R_LUM_BITS, G_LUM_BITS});
 *     static constexpr size_t kNumTables = 1 << kMaxLumBits;
 *     static constexpr size_t kTableWidth = 256;
 *     static constexpr size_t kTableNumElements = kNumTables * kTableWidth;
 *
 *     constexpr bool isLinear() const {
 *         return fGammaTables == nullptr;
 *     }
 *
 *     /**
 *      * fGammaTables is a flattened 2-D array. Accessing rows requires accounting
 *      * for the width dimension (via kTableWidth).
 *      */
 *     std::unique_ptr<uint8_t[]> fGammaTables;
 *
 *     using INHERITED = SkRefCnt;
 * }
 * ```
 */
public open class SkTMaskGamma333 public constructor() : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * static constexpr int kMaxLumBits
   * ```
   */
  private var fGammaTables: Int = TODO("Initialize fGammaTables")

  /**
   * C++ original:
   * ```cpp
   * constexpr SkTMaskGamma() {}
   * ```
   */
  public constructor(contrast: SkScalar, deviceGamma: SkScalar) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * template <int R_LUM_BITS, int G_LUM_BITS, int B_LUM_BITS>
   * SkTMaskPreBlend<R_LUM_BITS, G_LUM_BITS, B_LUM_BITS>
   * SkTMaskGamma<R_LUM_BITS, G_LUM_BITS, B_LUM_BITS>::preBlend(SkColor color) const {
   *     if (isLinear()) {
   *         return SkTMaskPreBlend<R_LUM_BITS, G_LUM_BITS, B_LUM_BITS>();
   *     }
   *     constexpr size_t lum_shift = 8 - kMaxLumBits;
   *     const size_t r_index = (SkColorGetR(color) >> lum_shift) * kTableWidth;
   *     const size_t g_index = (SkColorGetG(color) >> lum_shift) * kTableWidth;
   *     const size_t b_index = (SkColorGetB(color) >> lum_shift) * kTableWidth;
   *     SkASSERT(r_index < kTableNumElements &&
   *              g_index < kTableNumElements &&
   *              b_index < kTableNumElements);
   *     return SkTMaskPreBlend<R_LUM_BITS, G_LUM_BITS, B_LUM_BITS>(sk_ref_sp(this),
   *                          &fGammaTables[r_index],
   *                          &fGammaTables[g_index],
   *                          &fGammaTables[b_index]);
   * }
   * ```
   */
  public fun preBlend(color: SkColor): PreBlend {
    TODO("Implement preBlend")
  }

  /**
   * C++ original:
   * ```cpp
   * void getGammaTableDimensions(int* tableWidth, int* numTables) const {
   *         *tableWidth = kTableWidth;
   *         *numTables = kNumTables;
   *     }
   * ```
   */
  public fun getGammaTableDimensions(tableWidth: Int?, numTables: Int?) {
    TODO("Implement getGammaTableDimensions")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr size_t getGammaTableSizeInBytes() const {
   *         return kTableNumElements * sizeof(uint8_t);
   *     }
   * ```
   */
  public fun getGammaTableSizeInBytes(): Int {
    TODO("Implement getGammaTableSizeInBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * const uint8_t* getGammaTables() const {
   *         return fGammaTables.get();
   *     }
   * ```
   */
  public fun getGammaTables(): Int {
    TODO("Implement getGammaTables")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr bool isLinear() const {
   *         return fGammaTables == nullptr;
   *     }
   * ```
   */
  private fun isLinear(): Boolean {
    TODO("Implement isLinear")
  }

  public companion object {
    private val kMaxLumBits: Int = TODO("Initialize kMaxLumBits")

    private val kNumTables: Int = TODO("Initialize kNumTables")

    private val kTableWidth: Int = TODO("Initialize kTableWidth")

    private val kTableNumElements: Int = TODO("Initialize kTableNumElements")

    /**
     * C++ original:
     * ```cpp
     * static SkColor CanonicalColor(SkColor color) {
     *         return SkColorSetRGB(
     *                    sk_t_scale255<R_LUM_BITS>(SkColorGetR(color) >> (8 - R_LUM_BITS)),
     *                    sk_t_scale255<G_LUM_BITS>(SkColorGetG(color) >> (8 - G_LUM_BITS)),
     *                    sk_t_scale255<B_LUM_BITS>(SkColorGetB(color) >> (8 - B_LUM_BITS)));
     *     }
     * ```
     */
    public fun canonicalColor(color: SkColor): SkColor {
      TODO("Implement canonicalColor")
    }
  }
}

public typealias SkMaskGamma = SkTMaskGamma333
