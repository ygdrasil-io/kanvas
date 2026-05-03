package org.skia.modules

import undefined.ScalarValue

/**
 * C++ original:
 * ```cpp
 * class ShadowAdapter final : public DiscardableAdapterBase<ShadowAdapter,
 *                                                           sksg::ExternalImageFilter> {
 * public:
 *     enum Type {
 *         kDropShadow,
 *         kInnerShadow,
 *     };
 *
 *     ShadowAdapter(const skjson::ObjectValue& jstyle,
 *                   const AnimationBuilder& abuilder,
 *                   Type type)
 *         : fType(type) {
 *         this->bind(abuilder, jstyle["c"], fColor);
 *         this->bind(abuilder, jstyle["o"], fOpacity);
 *         this->bind(abuilder, jstyle["a"], fAngle);
 *         this->bind(abuilder, jstyle["s"], fSize);
 *         this->bind(abuilder, jstyle["d"], fDistance);
 *     }
 *
 * private:
 *     void onSync() override {
 *         const auto    rad = SkDegreesToRadians(180 + fAngle), // 0deg -> left (style)
 *                     sigma = fSize * kBlurSizeToSigma,
 *                   opacity = SkTPin(fOpacity / 100, 0.0f, 1.0f);
 *         const auto  color = static_cast<SkColor4f>(fColor);
 *         const auto offset = SkV2{ fDistance * SkScalarCos(rad),
 *                                  -fDistance * SkScalarSin(rad)};
 *
 *         // Shadow effects largely follow the feDropShadow spec [1]:
 *         //
 *         //   1) isolate source alpha
 *         //   2) apply a gaussian blur
 *         //   3) apply an offset
 *         //   4) modulate with a flood/color generator
 *         //   5) composite with the source
 *         //
 *         // Note: as an optimization, we can fold #1 and #4 into a single color matrix filter.
 *         //
 *         // Inner shadow differences:
 *         //
 *         //   a) operates on the inverse of source alpha
 *         //   b) the result is masked against the source
 *         //   c) composited on top of source
 *         //
 *         // [1] https://drafts.fxtf.org/filter-effects/#feDropShadowElement
 *
 *         // Select and colorize the source alpha channel.
 *         SkColorMatrix cm{0, 0, 0,                  0, color.fR,
 *                          0, 0, 0,                  0, color.fG,
 *                          0, 0, 0,                  0, color.fB,
 *                          0, 0, 0, opacity * color.fA,        0};
 *
 *         // Inner shadows use the alpha inverse.
 *         if (fType == Type::kInnerShadow) {
 *             cm.preConcat({1, 0, 0, 0, 0,
 *                           0, 1, 0, 0, 0,
 *                           0, 0, 1, 0, 0,
 *                           0, 0, 0,-1, 1});
 *         }
 *         auto f = SkImageFilters::ColorFilter(SkColorFilters::Matrix(cm), nullptr);
 *
 *         if (sigma > 0) {
 *             f = SkImageFilters::Blur(sigma, sigma, std::move(f));
 *         }
 *
 *         if (!SkScalarNearlyZero(offset.x) || !SkScalarNearlyZero(offset.y)) {
 *             f = SkImageFilters::Offset(offset.x, offset.y, std::move(f));
 *         }
 *
 *         sk_sp<SkImageFilter> source;
 *
 *         if (fType == Type::kInnerShadow) {
 *             // Inner shadows draw on top of, and are masked with, the source.
 *             f = SkImageFilters::Blend(SkBlendMode::kDstIn, std::move(f));
 *
 *             std::swap(source, f);
 *         }
 *
 *         this->node()->setImageFilter(SkImageFilters::Merge(std::move(f),
 *                                                            std::move(source)));
 *     }
 *
 *     const Type fType;
 *
 *     ColorValue  fColor;
 *     ScalarValue fOpacity  = 100, // percentage
 *                 fAngle    =   0, // degrees
 *                 fSize     =   0,
 *                 fDistance =   0;
 *
 *     using INHERITED = DiscardableAdapterBase<ShadowAdapter, sksg::ExternalImageFilter>;
 * }
 * ```
 */
public class ShadowAdapter public constructor(
  jstyle: ObjectValue,
  abuilder: AnimationBuilder,
  type: Type,
) : DiscardableAdapterBase(),
    ShadowAdapter,
    ExternalImageFilter {
  /**
   * C++ original:
   * ```cpp
   * const Type fType
   * ```
   */
  private val fType: Type = TODO("Initialize fType")

  /**
   * C++ original:
   * ```cpp
   * ColorValue  fColor
   * ```
   */
  private var fColor: ColorValue = TODO("Initialize fColor")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fOpacity  = 100
   * ```
   */
  private var fOpacity: ScalarValue = TODO("Initialize fOpacity")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fOpacity  = 100, // percentage
   *                 fAngle    =   0
   * ```
   */
  private var fAngle: ScalarValue = TODO("Initialize fAngle")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fOpacity  = 100, // percentage
   *                 fAngle    =   0, // degrees
   *                 fSize     =   0
   * ```
   */
  private var fSize: ScalarValue = TODO("Initialize fSize")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fOpacity  = 100, // percentage
   *                 fAngle    =   0, // degrees
   *                 fSize     =   0,
   *                 fDistance =   0
   * ```
   */
  private var fDistance: ScalarValue = TODO("Initialize fDistance")

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         const auto    rad = SkDegreesToRadians(180 + fAngle), // 0deg -> left (style)
   *                     sigma = fSize * kBlurSizeToSigma,
   *                   opacity = SkTPin(fOpacity / 100, 0.0f, 1.0f);
   *         const auto  color = static_cast<SkColor4f>(fColor);
   *         const auto offset = SkV2{ fDistance * SkScalarCos(rad),
   *                                  -fDistance * SkScalarSin(rad)};
   *
   *         // Shadow effects largely follow the feDropShadow spec [1]:
   *         //
   *         //   1) isolate source alpha
   *         //   2) apply a gaussian blur
   *         //   3) apply an offset
   *         //   4) modulate with a flood/color generator
   *         //   5) composite with the source
   *         //
   *         // Note: as an optimization, we can fold #1 and #4 into a single color matrix filter.
   *         //
   *         // Inner shadow differences:
   *         //
   *         //   a) operates on the inverse of source alpha
   *         //   b) the result is masked against the source
   *         //   c) composited on top of source
   *         //
   *         // [1] https://drafts.fxtf.org/filter-effects/#feDropShadowElement
   *
   *         // Select and colorize the source alpha channel.
   *         SkColorMatrix cm{0, 0, 0,                  0, color.fR,
   *                          0, 0, 0,                  0, color.fG,
   *                          0, 0, 0,                  0, color.fB,
   *                          0, 0, 0, opacity * color.fA,        0};
   *
   *         // Inner shadows use the alpha inverse.
   *         if (fType == Type::kInnerShadow) {
   *             cm.preConcat({1, 0, 0, 0, 0,
   *                           0, 1, 0, 0, 0,
   *                           0, 0, 1, 0, 0,
   *                           0, 0, 0,-1, 1});
   *         }
   *         auto f = SkImageFilters::ColorFilter(SkColorFilters::Matrix(cm), nullptr);
   *
   *         if (sigma > 0) {
   *             f = SkImageFilters::Blur(sigma, sigma, std::move(f));
   *         }
   *
   *         if (!SkScalarNearlyZero(offset.x) || !SkScalarNearlyZero(offset.y)) {
   *             f = SkImageFilters::Offset(offset.x, offset.y, std::move(f));
   *         }
   *
   *         sk_sp<SkImageFilter> source;
   *
   *         if (fType == Type::kInnerShadow) {
   *             // Inner shadows draw on top of, and are masked with, the source.
   *             f = SkImageFilters::Blend(SkBlendMode::kDstIn, std::move(f));
   *
   *             std::swap(source, f);
   *         }
   *
   *         this->node()->setImageFilter(SkImageFilters::Merge(std::move(f),
   *                                                            std::move(source)));
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }

  public enum class Type {
    kDropShadow,
    kInnerShadow,
  }
}
