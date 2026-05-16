package org.skia.core

import kotlin.Int
import org.skia.foundation.SkReadBuffer
import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * class SkGradientScope {
 * public:
 *     std::optional<SkGradient> unflatten(SkReadBuffer&, SkMatrix* legacyLocalMatrix);
 * private:
 *     skia_private::STArray<16, SkColor4f> fColorStorage;
 *     skia_private::STArray<16, SkScalar> fPositionStorage;
 * }
 * ```
 */
public data class SkGradientScope public constructor(
  /**
   * C++ original:
   * ```cpp
   * skia_private::STArray<16, SkColor4f> fColorStorage
   * ```
   */
  private var fColorStorage: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::STArray<16, SkScalar> fPositionStorage
   * ```
   */
  private var fPositionStorage: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * std::optional<SkGradient> SkGradientScope::unflatten(SkReadBuffer& buffer,
   *                                                      SkMatrix* legacyLocalMatrix) {
   *     // New gradient format. Includes floating point color, color space, densely packed flags
   *     uint32_t flags = buffer.readUInt();
   *
   *     auto tm = (SkTileMode)((flags >> kTileModeShift_GSF) & kTileModeMask_GSF);
   *
   *     auto cs = (SkGradient::Interpolation::ColorSpace)(
   *             (flags >> kInterpolationColorSpaceShift_GSF) & kInterpolationColorSpaceMask_GSF);
   *     auto hm = (SkGradient::Interpolation::HueMethod)(
   *             (flags >> kInterpolationHueMethodShift_GSF) & kInterpolationHueMethodMask_GSF);
   *     auto pm = (flags & kInterpolationInPremul_GSF) ? SkGradient::Interpolation::InPremul::kYes
   *                                                    : SkGradient::Interpolation::InPremul::kNo;
   *
   *     const size_t count = buffer.getArrayCount();
   *
   *     if (!(validate_array(buffer, count, &fColorStorage) &&
   *           buffer.readColor4fArray({fColorStorage.data(), count}))) {
   *         return {};
   *     }
   *     SkSpan<const SkColor4f> colors = {fColorStorage.begin(), count};
   *
   *     sk_sp<SkColorSpace> colorSpace;
   *     if (flags & kHasColorSpace_GSF) {
   *         if (auto data = buffer.readByteArrayAsData()) {
   *             colorSpace = SkColorSpace::Deserialize(data->data(), data->size());
   *         }
   *     }
   *
   *     SkSpan<const float> pos;
   *     if (flags & kHasPosition_GSF) {
   *         if (!(validate_array(buffer, count, &fPositionStorage) &&
   *               buffer.readScalarArray({fPositionStorage.data(), count}))) {
   *             return {};
   *         }
   *         pos = {fPositionStorage.begin(), count};
   *     }
   *
   *     if (flags & kHasLegacyLocalMatrix_GSF) {
   *         SkASSERT(buffer.isVersionLT(SkPicturePriv::Version::kNoShaderLocalMatrix));
   *         buffer.readMatrix(legacyLocalMatrix);
   *     } else {
   *         *legacyLocalMatrix = SkMatrix::I();
   *     }
   *
   *     if (buffer.isValid()) {
   *         return SkGradient({colors, pos, tm, std::move(colorSpace)},
   *                           {pm, cs, hm});
   *     }
   *     return {};
   * }
   * ```
   */
  public fun unflatten(buffer: SkReadBuffer, legacyLocalMatrix: SkMatrix?): Int {
    TODO("Implement unflatten")
  }
}
