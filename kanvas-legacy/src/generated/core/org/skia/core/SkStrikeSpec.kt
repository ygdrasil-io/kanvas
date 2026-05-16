package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkAutoDescriptor
import org.skia.foundation.SkDescriptor
import org.skia.foundation.SkFont
import org.skia.foundation.SkMaskFilter
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathEffect
import org.skia.foundation.SkSp
import org.skia.foundation.SkSurfaceProps
import org.skia.foundation.SkTypeface
import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * class SkStrikeSpec {
 * public:
 *     SkStrikeSpec(const SkDescriptor& descriptor, sk_sp<SkTypeface> typeface);
 *     SkStrikeSpec(const SkStrikeSpec&);
 *     SkStrikeSpec& operator=(const SkStrikeSpec&) = delete;
 *
 *     SkStrikeSpec(SkStrikeSpec&&);
 *     SkStrikeSpec& operator=(SkStrikeSpec&&) = delete;
 *
 *     ~SkStrikeSpec();
 *
 *     // Create a strike spec for mask style cache entries.
 *     static SkStrikeSpec MakeMask(
 *             const SkFont& font,
 *             const SkPaint& paint,
 *             const SkSurfaceProps& surfaceProps,
 *             SkScalerContextFlags scalerContextFlags,
 *             const SkMatrix& deviceMatrix);
 *
 *     // A strike for finding the max size for transforming masks. This is used to calculate the
 *     // maximum dimension of a SubRun of text.
 *     static SkStrikeSpec MakeTransformMask(
 *             const SkFont& font,
 *             const SkPaint& paint,
 *             const SkSurfaceProps& surfaceProps,
 *             SkScalerContextFlags scalerContextFlags,
 *             const SkMatrix& deviceMatrix);
 *
 *     // Create a strike spec for path style cache entries.
 *     static std::tuple<SkStrikeSpec, SkScalar> MakePath(
 *             const SkFont& font,
 *             const SkPaint& paint,
 *             const SkSurfaceProps& surfaceProps,
 *             SkScalerContextFlags scalerContextFlags);
 *
 *     // Create a canonical strike spec for device-less measurements.
 *     static std::tuple<SkStrikeSpec, SkScalar> MakeCanonicalized(
 *             const SkFont& font, const SkPaint* paint = nullptr);
 *
 *     // Create a strike spec without a device, and does not switch over to path for large sizes.
 *     static SkStrikeSpec MakeWithNoDevice(
 *         const SkFont& font, const SkPaint* paint = nullptr,
 *         SkScalerContextFlags flags = SkScalerContextFlags::kFakeGammaAndBoostContrast);
 *
 *     sk_sp<sktext::StrikeForGPU> findOrCreateScopedStrike(
 *             sktext::StrikeForGPUCacheInterface* cache) const;
 *
 *     sk_sp<SkStrike> findOrCreateStrike() const;
 *
 *     sk_sp<SkStrike> findOrCreateStrike(SkStrikeCache* cache) const;
 *
 *     std::unique_ptr<SkScalerContext> createScalerContext() const {
 *         SkScalerContextEffects effects{fPathEffect.get(), fMaskFilter.get()};
 *         return fTypeface->createScalerContext(effects, fAutoDescriptor.getDesc());
 *     }
 *
 *     const SkDescriptor& descriptor() const { return *fAutoDescriptor.getDesc(); }
 *     const SkTypeface& typeface() const { return *fTypeface; }
 *     static bool ShouldDrawAsPath(const SkPaint& paint, const SkFont& font, const SkMatrix& matrix);
 *     SkString dump() const;
 *
 * private:
 *     SkStrikeSpec(
 *             const SkFont& font,
 *             const SkPaint& paint,
 *             const SkSurfaceProps& surfaceProps,
 *             SkScalerContextFlags scalerContextFlags,
 *             const SkMatrix& deviceMatrix);
 *
 *     SkAutoDescriptor fAutoDescriptor;
 *     sk_sp<SkMaskFilter> fMaskFilter{nullptr};
 *     sk_sp<SkPathEffect> fPathEffect{nullptr};
 *     sk_sp<SkTypeface> fTypeface;
 * }
 * ```
 */
public data class SkStrikeSpec public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkAutoDescriptor fAutoDescriptor
   * ```
   */
  private var fAutoDescriptor: SkAutoDescriptor,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkMaskFilter> fMaskFilter
   * ```
   */
  private var fMaskFilter: SkSp<SkMaskFilter>,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPathEffect> fPathEffect
   * ```
   */
  private var fPathEffect: SkSp<SkPathEffect>,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> fTypeface
   * ```
   */
  private var fTypeface: SkSp<SkTypeface>,
) {
  /**
   * C++ original:
   * ```cpp
   * SkStrikeSpec& operator=(const SkStrikeSpec&) = delete
   * ```
   */
  public fun assign(param0: SkStrikeSpec) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * SkStrikeSpec& operator=(SkStrikeSpec&&) = delete
   * ```
   */
  public fun findOrCreateScopedStrike(cache: StrikeForGPUCacheInterface?): SkSp<StrikeForGPU> {
    TODO("Implement findOrCreateScopedStrike")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<sktext::StrikeForGPU> SkStrikeSpec::findOrCreateScopedStrike(
   *         sktext::StrikeForGPUCacheInterface* cache) const {
   *     return cache->findOrCreateScopedStrike(*this);
   * }
   * ```
   */
  public fun findOrCreateStrike(): SkSp<SkStrike> {
    TODO("Implement findOrCreateStrike")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkStrike> SkStrikeSpec::findOrCreateStrike() const {
   *     return SkStrikeCache::GlobalStrikeCache()->findOrCreateStrike(*this);
   * }
   * ```
   */
  public fun findOrCreateStrike(cache: SkStrikeCache?): SkSp<SkStrike> {
    TODO("Implement findOrCreateStrike")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkStrike> SkStrikeSpec::findOrCreateStrike(SkStrikeCache* cache) const {
   *     return cache->findOrCreateStrike(*this);
   * }
   * ```
   */
  public fun createScalerContext(): Int {
    TODO("Implement createScalerContext")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkScalerContext> createScalerContext() const {
   *         SkScalerContextEffects effects{fPathEffect.get(), fMaskFilter.get()};
   *         return fTypeface->createScalerContext(effects, fAutoDescriptor.getDesc());
   *     }
   * ```
   */
  public fun descriptor(): SkDescriptor {
    TODO("Implement descriptor")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkDescriptor& descriptor() const { return *fAutoDescriptor.getDesc(); }
   * ```
   */
  public fun typeface(): SkTypeface {
    TODO("Implement typeface")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkTypeface& typeface() const { return *fTypeface; }
   * ```
   */
  public fun dump(): String {
    TODO("Implement dump")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkStrikeSpec SkStrikeSpec::MakeMask(const SkFont& font, const SkPaint& paint,
     *                                     const SkSurfaceProps& surfaceProps,
     *                                     SkScalerContextFlags scalerContextFlags,
     *                                     const SkMatrix& deviceMatrix) {
     *
     *     return SkStrikeSpec(font, paint, surfaceProps, scalerContextFlags, deviceMatrix);
     * }
     * ```
     */
    public fun makeMask(
      font: SkFont,
      paint: SkPaint,
      surfaceProps: SkSurfaceProps,
      scalerContextFlags: SkScalerContextFlags,
      deviceMatrix: SkMatrix,
    ): SkStrikeSpec {
      TODO("Implement makeMask")
    }

    /**
     * C++ original:
     * ```cpp
     * SkStrikeSpec SkStrikeSpec::MakeTransformMask(const SkFont& font,
     *                                              const SkPaint& paint,
     *                                              const SkSurfaceProps& surfaceProps,
     *                                              SkScalerContextFlags scalerContextFlags,
     *                                              const SkMatrix& deviceMatrix) {
     *     SkFont sourceFont{font};
     *     sourceFont.setSubpixel(false);
     *     return SkStrikeSpec(sourceFont, paint, surfaceProps, scalerContextFlags, deviceMatrix);
     * }
     * ```
     */
    public fun makeTransformMask(
      font: SkFont,
      paint: SkPaint,
      surfaceProps: SkSurfaceProps,
      scalerContextFlags: SkScalerContextFlags,
      deviceMatrix: SkMatrix,
    ): SkStrikeSpec {
      TODO("Implement makeTransformMask")
    }

    /**
     * C++ original:
     * ```cpp
     * std::tuple<SkStrikeSpec, SkScalar> SkStrikeSpec::MakePath(
     *         const SkFont& font, const SkPaint& paint,
     *         const SkSurfaceProps& surfaceProps,
     *         SkScalerContextFlags scalerContextFlags) {
     *
     *     // setup our std runPaint, in hopes of getting hits in the cache
     *     SkPaint pathPaint{paint};
     *     SkFont pathFont{font};
     *
     *     // The sub-pixel position will always happen when transforming to the screen.
     *     pathFont.setSubpixel(false);
     *
     *     // The factor to get from the size stored in the strike to the size needed for
     *     // the source.
     *     SkScalar strikeToSourceScale = pathFont.setupForAsPaths(&pathPaint);
     *
     *     return {SkStrikeSpec(pathFont, pathPaint, surfaceProps, scalerContextFlags, SkMatrix::I()),
     *             strikeToSourceScale};
     * }
     * ```
     */
    public fun makePath(
      font: SkFont,
      paint: SkPaint,
      surfaceProps: SkSurfaceProps,
      scalerContextFlags: SkScalerContextFlags,
    ): Int {
      TODO("Implement makePath")
    }

    /**
     * C++ original:
     * ```cpp
     * std::tuple<SkStrikeSpec, SkScalar> SkStrikeSpec::MakeCanonicalized(
     *         const SkFont& font, const SkPaint* paint) {
     *     SkPaint canonicalizedPaint;
     *     if (paint != nullptr) {
     *         canonicalizedPaint = *paint;
     *     }
     *
     *     const SkFont* canonicalizedFont = &font;
     *     std::optional<SkFont> pathFont;
     *     SkScalar strikeToSourceScale = 1;
     *     if (ShouldDrawAsPath(canonicalizedPaint, font, SkMatrix::I())) {
     *         pathFont = font;
     *         canonicalizedFont = &pathFont.value();
     *         strikeToSourceScale = pathFont->setupForAsPaths(nullptr);
     *         canonicalizedPaint.reset();
     *     }
     *
     *     return {SkStrikeSpec(*canonicalizedFont, canonicalizedPaint, SkSurfaceProps(),
     *                          SkScalerContextFlags::kFakeGammaAndBoostContrast, SkMatrix::I()),
     *             strikeToSourceScale};
     * }
     * ```
     */
    public fun makeCanonicalized(font: SkFont, paint: SkPaint? = TODO()): Int {
      TODO("Implement makeCanonicalized")
    }

    /**
     * C++ original:
     * ```cpp
     * SkStrikeSpec SkStrikeSpec::MakeWithNoDevice(const SkFont& font, const SkPaint* paint,
     *                                             SkScalerContextFlags flags) {
     *     SkPaint setupPaint;
     *     if (paint != nullptr) {
     *         setupPaint = *paint;
     *     }
     *
     *     return SkStrikeSpec(font, setupPaint, SkSurfaceProps(), flags, SkMatrix::I());
     * }
     * ```
     */
    public fun makeWithNoDevice(
      font: SkFont,
      paint: SkPaint? = TODO(),
      flags: SkScalerContextFlags = TODO(),
    ): SkStrikeSpec {
      TODO("Implement makeWithNoDevice")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkStrikeSpec::ShouldDrawAsPath(
     *         const SkPaint& paint, const SkFont& font, const SkMatrix& viewMatrix) {
     *
     *     // hairline glyphs are fast enough, so we don't need to cache them
     *     if (SkPaint::kStroke_Style == paint.getStyle() && 0 == paint.getStrokeWidth()) {
     *         return true;
     *     }
     *
     *     // we don't cache perspective
     *     if (viewMatrix.hasPerspective()) {
     *         return true;
     *     }
     *
     *     SkMatrix textMatrix = SkFontPriv::MakeTextMatrix(font);
     *     textMatrix.postConcat(viewMatrix);
     *
     *     // we have a self-imposed maximum, just to limit memory-usage
     *     constexpr SkScalar memoryLimit = 256;
     *     constexpr SkScalar maxSizeSquared = memoryLimit * memoryLimit;
     *
     *     auto distance = [&textMatrix](int XIndex, int YIndex) {
     *         return textMatrix[XIndex] * textMatrix[XIndex] + textMatrix[YIndex] * textMatrix[YIndex];
     *     };
     *
     *     return distance(SkMatrix::kMScaleX, SkMatrix::kMSkewY ) > maxSizeSquared
     *         || distance(SkMatrix::kMSkewX,  SkMatrix::kMScaleY) > maxSizeSquared;
     * }
     * ```
     */
    public fun shouldDrawAsPath(
      paint: SkPaint,
      font: SkFont,
      matrix: SkMatrix,
    ): Boolean {
      TODO("Implement shouldDrawAsPath")
    }
  }
}
