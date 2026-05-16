package org.skia.modules

import kotlin.Array
import kotlin.UByte
import undefined.ScalarValue

/**
 * C++ original:
 * ```cpp
 * struct ChannelMapper {
 *     ScalarValue fInBlack  = 0,
 *                 fInWhite  = 1,
 *                 fOutBlack = 0,
 *                 fOutWhite = 1,
 *                 fGamma    = 1;
 *
 *     const uint8_t* build_lut(std::array<uint8_t, 256>& lut_storage,
 *                              const ClipInfo& clip_info) const {
 *         auto in_0 = fInBlack,
 *              in_1 = fInWhite,
 *             out_0 = fOutBlack,
 *             out_1 = fOutWhite,
 *                 g = sk_ieee_float_divide(1, std::max(fGamma, 0.0f));
 *
 *         float clip[] = {0, 1};
 *         const auto kLottieDoClip = 1;
 *         if (SkScalarTruncToInt(clip_info.fClipBlack) == kLottieDoClip) {
 *             const auto idx = fOutBlack <= fOutWhite ? 0 : 1;
 *             clip[idx] = SkTPin(out_0, 0.0f, 1.0f);
 *         }
 *         if (SkScalarTruncToInt(clip_info.fClipWhite) == kLottieDoClip) {
 *             const auto idx = fOutBlack <= fOutWhite ? 1 : 0;
 *             clip[idx] = SkTPin(out_1, 0.0f, 1.0f);
 *         }
 *         SkASSERT(clip[0] <= clip[1]);
 *
 *         if (SkScalarNearlyEqual(in_0, out_0) &&
 *             SkScalarNearlyEqual(in_1, out_1) &&
 *             SkScalarNearlyEqual(g, 1)) {
 *             // no-op
 *             return nullptr;
 *         }
 *
 *         auto dIn  =  in_1 -  in_0,
 *              dOut = out_1 - out_0;
 *
 *         if (SkScalarNearlyZero(dIn)) {
 *             // Degenerate dIn == 0 makes the arithmetic below explode.
 *             //
 *             // We could specialize the builder to deal with that case, or we could just
 *             // nudge by epsilon to make it all work.  The latter approach is simpler
 *             // and doesn't have any noticeable downsides.
 *             //
 *             // Also nudge in_0 towards 0.5, in case it was sqashed against an extremity.
 *             // This allows for some abrupt transition when the output interval is not
 *             // collapsed, and produces results closer to AE.
 *             static constexpr auto kEpsilon = 2 * SK_ScalarNearlyZero;
 *             dIn  += std::copysign(kEpsilon, dIn);
 *             in_0 += std::copysign(kEpsilon, .5f - in_0);
 *             SkASSERT(!SkScalarNearlyZero(dIn));
 *         }
 *
 *         auto t =      -in_0 / dIn,
 *             dT = 1 / 255.0f / dIn;
 *
 *         for (size_t i = 0; i < 256; ++i) {
 *             const auto out = out_0 + dOut * std::pow(std::max(t, 0.0f), g);
 *             SkASSERT(!SkIsNaN(out));
 *
 *             lut_storage[i] = static_cast<uint8_t>(std::round(SkTPin(out, clip[0], clip[1]) * 255));
 *
 *             t += dT;
 *         }
 *
 *         return lut_storage.data();
 *     }
 * }
 * ```
 */
public data class ChannelMapper public constructor(
  /**
   * C++ original:
   * ```cpp
   * ScalarValue fInBlack  = 0
   * ```
   */
  public var fInBlack: ScalarValue,
  /**
   * C++ original:
   * ```cpp
   * ScalarValue fInBlack  = 0,
   *                 fInWhite  = 1
   * ```
   */
  public var fInWhite: ScalarValue,
  /**
   * C++ original:
   * ```cpp
   * ScalarValue fInBlack  = 0,
   *                 fInWhite  = 1,
   *                 fOutBlack = 0
   * ```
   */
  public var fOutBlack: ScalarValue,
  /**
   * C++ original:
   * ```cpp
   * ScalarValue fInBlack  = 0,
   *                 fInWhite  = 1,
   *                 fOutBlack = 0,
   *                 fOutWhite = 1
   * ```
   */
  public var fOutWhite: ScalarValue,
  /**
   * C++ original:
   * ```cpp
   * ScalarValue fInBlack  = 0,
   *                 fInWhite  = 1,
   *                 fOutBlack = 0,
   *                 fOutWhite = 1,
   *                 fGamma    = 1
   * ```
   */
  public var fGamma: ScalarValue,
) {
  /**
   * C++ original:
   * ```cpp
   * const uint8_t* build_lut(std::array<uint8_t, 256>& lut_storage,
   *                              const ClipInfo& clip_info) const {
   *         auto in_0 = fInBlack,
   *              in_1 = fInWhite,
   *             out_0 = fOutBlack,
   *             out_1 = fOutWhite,
   *                 g = sk_ieee_float_divide(1, std::max(fGamma, 0.0f));
   *
   *         float clip[] = {0, 1};
   *         const auto kLottieDoClip = 1;
   *         if (SkScalarTruncToInt(clip_info.fClipBlack) == kLottieDoClip) {
   *             const auto idx = fOutBlack <= fOutWhite ? 0 : 1;
   *             clip[idx] = SkTPin(out_0, 0.0f, 1.0f);
   *         }
   *         if (SkScalarTruncToInt(clip_info.fClipWhite) == kLottieDoClip) {
   *             const auto idx = fOutBlack <= fOutWhite ? 1 : 0;
   *             clip[idx] = SkTPin(out_1, 0.0f, 1.0f);
   *         }
   *         SkASSERT(clip[0] <= clip[1]);
   *
   *         if (SkScalarNearlyEqual(in_0, out_0) &&
   *             SkScalarNearlyEqual(in_1, out_1) &&
   *             SkScalarNearlyEqual(g, 1)) {
   *             // no-op
   *             return nullptr;
   *         }
   *
   *         auto dIn  =  in_1 -  in_0,
   *              dOut = out_1 - out_0;
   *
   *         if (SkScalarNearlyZero(dIn)) {
   *             // Degenerate dIn == 0 makes the arithmetic below explode.
   *             //
   *             // We could specialize the builder to deal with that case, or we could just
   *             // nudge by epsilon to make it all work.  The latter approach is simpler
   *             // and doesn't have any noticeable downsides.
   *             //
   *             // Also nudge in_0 towards 0.5, in case it was sqashed against an extremity.
   *             // This allows for some abrupt transition when the output interval is not
   *             // collapsed, and produces results closer to AE.
   *             static constexpr auto kEpsilon = 2 * SK_ScalarNearlyZero;
   *             dIn  += std::copysign(kEpsilon, dIn);
   *             in_0 += std::copysign(kEpsilon, .5f - in_0);
   *             SkASSERT(!SkScalarNearlyZero(dIn));
   *         }
   *
   *         auto t =      -in_0 / dIn,
   *             dT = 1 / 255.0f / dIn;
   *
   *         for (size_t i = 0; i < 256; ++i) {
   *             const auto out = out_0 + dOut * std::pow(std::max(t, 0.0f), g);
   *             SkASSERT(!SkIsNaN(out));
   *
   *             lut_storage[i] = static_cast<uint8_t>(std::round(SkTPin(out, clip[0], clip[1]) * 255));
   *
   *             t += dT;
   *         }
   *
   *         return lut_storage.data();
   *     }
   * ```
   */
  public fun buildLut(lutStorage: Array<UByte>, clipInfo: ClipInfo): UByte {
    TODO("Implement buildLut")
  }
}
