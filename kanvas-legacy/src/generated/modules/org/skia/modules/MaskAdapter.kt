package org.skia.modules

import kotlin.Boolean
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkSp
import undefined.ScalarValue
import undefined.Vec2Value

/**
 * C++ original:
 * ```cpp
 * class MaskAdapter final : public AnimatablePropertyContainer {
 * public:
 *     MaskAdapter(const skjson::ObjectValue& jmask, const AnimationBuilder& abuilder, SkBlendMode bm)
 *         : fMaskPaint(sksg::Color::Make(SK_ColorBLACK))
 *         , fBlendMode(bm)
 *     {
 *         fMaskPaint->setAntiAlias(true);
 *         if (!this->requires_isolation()) {
 *             // We can mask at draw time.
 *             fMaskPaint->setBlendMode(bm);
 *         }
 *
 *         this->bind(abuilder, jmask["o"], fOpacity);
 *
 *         if (this->bind(abuilder, jmask["f"], fFeather)) {
 *             fMaskFilter = sksg::BlurImageFilter::Make();
 *             // Mask feathers don't repeat edge pixels.
 *             fMaskFilter->setTileMode(SkTileMode::kDecal);
 *         }
 *     }
 *
 *     bool hasEffect() const {
 *         return !this->isStatic()
 *             || fOpacity < 100
 *             || fFeather != SkV2{0,0};
 *     }
 *
 *     sk_sp<sksg::RenderNode> makeMask(sk_sp<sksg::Path> mask_path) const {
 *         sk_sp<sksg::RenderNode> mask = sksg::Draw::Make(std::move(mask_path), fMaskPaint);
 *
 *         // Optional mask blur (feather).
 *         mask = sksg::ImageFilterEffect::Make(std::move(mask), fMaskFilter);
 *
 *         if (this->requires_isolation()) {
 *             mask = sksg::LayerEffect::Make(std::move(mask), fBlendMode);
 *         }
 *
 *         return mask;
 *     }
 *
 * private:
 *     void onSync() override {
 *         fMaskPaint->setOpacity(fOpacity * 0.01f);
 *         if (fMaskFilter) {
 *             // Close enough to AE.
 *             static constexpr SkScalar kFeatherToSigma = 0.38f;
 *             fMaskFilter->setSigma({fFeather.x * kFeatherToSigma,
 *                                    fFeather.y * kFeatherToSigma});
 *         }
 *     }
 *
 *     bool requires_isolation() const {
 *         SkASSERT(fBlendMode == SkBlendMode::kSrc     ||
 *                  fBlendMode == SkBlendMode::kSrcOver ||
 *                  fBlendMode == SkBlendMode::kSrcIn   ||
 *                  fBlendMode == SkBlendMode::kDstOut  ||
 *                  fBlendMode == SkBlendMode::kXor);
 *
 *         // Some mask modes touch pixels outside the immediate draw geometry.
 *         // These require a layer.
 *         switch (fBlendMode) {
 *             case (SkBlendMode::kSrcIn): return true;
 *             default                   : return false;
 *         }
 *         SkUNREACHABLE;
 *     }
 *
 *     const sk_sp<sksg::PaintNode> fMaskPaint;
 *     const SkBlendMode            fBlendMode;
 *     sk_sp<sksg::BlurImageFilter> fMaskFilter; // optional "feather"
 *
 *     Vec2Value   fFeather = {0,0};
 *     ScalarValue fOpacity = 100;
 * }
 * ```
 */
public class MaskAdapter public constructor(
  jmask: ObjectValue,
  abuilder: AnimationBuilder,
  bm: SkBlendMode,
) : AnimatablePropertyContainer() {
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<sksg::PaintNode> fMaskPaint
   * ```
   */
  private val fMaskPaint: SkSp<PaintNode> = TODO("Initialize fMaskPaint")

  /**
   * C++ original:
   * ```cpp
   * const SkBlendMode            fBlendMode
   * ```
   */
  private val fBlendMode: SkBlendMode = TODO("Initialize fBlendMode")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::BlurImageFilter> fMaskFilter
   * ```
   */
  private var fMaskFilter: SkSp<BlurImageFilter> = TODO("Initialize fMaskFilter")

  /**
   * C++ original:
   * ```cpp
   * Vec2Value   fFeather = {0,0}
   * ```
   */
  private var fFeather: Vec2Value = TODO("Initialize fFeather")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fOpacity = 100
   * ```
   */
  private var fOpacity: ScalarValue = TODO("Initialize fOpacity")

  /**
   * C++ original:
   * ```cpp
   * bool hasEffect() const {
   *         return !this->isStatic()
   *             || fOpacity < 100
   *             || fFeather != SkV2{0,0};
   *     }
   * ```
   */
  public fun hasEffect(): Boolean {
    TODO("Implement hasEffect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sksg::RenderNode> makeMask(sk_sp<sksg::Path> mask_path) const {
   *         sk_sp<sksg::RenderNode> mask = sksg::Draw::Make(std::move(mask_path), fMaskPaint);
   *
   *         // Optional mask blur (feather).
   *         mask = sksg::ImageFilterEffect::Make(std::move(mask), fMaskFilter);
   *
   *         if (this->requires_isolation()) {
   *             mask = sksg::LayerEffect::Make(std::move(mask), fBlendMode);
   *         }
   *
   *         return mask;
   *     }
   * ```
   */
  public fun makeMask(maskPath: SkSp<Path>): SkSp<RenderNode> {
    TODO("Implement makeMask")
  }

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         fMaskPaint->setOpacity(fOpacity * 0.01f);
   *         if (fMaskFilter) {
   *             // Close enough to AE.
   *             static constexpr SkScalar kFeatherToSigma = 0.38f;
   *             fMaskFilter->setSigma({fFeather.x * kFeatherToSigma,
   *                                    fFeather.y * kFeatherToSigma});
   *         }
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }

  /**
   * C++ original:
   * ```cpp
   * bool requires_isolation() const {
   *         SkASSERT(fBlendMode == SkBlendMode::kSrc     ||
   *                  fBlendMode == SkBlendMode::kSrcOver ||
   *                  fBlendMode == SkBlendMode::kSrcIn   ||
   *                  fBlendMode == SkBlendMode::kDstOut  ||
   *                  fBlendMode == SkBlendMode::kXor);
   *
   *         // Some mask modes touch pixels outside the immediate draw geometry.
   *         // These require a layer.
   *         switch (fBlendMode) {
   *             case (SkBlendMode::kSrcIn): return true;
   *             default                   : return false;
   *         }
   *         SkUNREACHABLE;
   *     }
   * ```
   */
  private fun requiresIsolation(): Boolean {
    TODO("Implement requiresIsolation")
  }
}
