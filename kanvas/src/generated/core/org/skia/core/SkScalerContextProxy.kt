package org.skia.core

import kotlin.Int
import kotlin.Unit
import org.skia.foundation.SkDescriptor
import org.skia.foundation.SkScalerContext
import org.skia.foundation.SkScalerContextEffects
import org.skia.foundation.SkSp
import org.skia.foundation.SkTypeface
import org.skia.memory.SkArenaAlloc
import org.skia.utils.SkStrikeClient
import undefined.GlyphMetrics

/**
 * C++ original:
 * ```cpp
 * class SkScalerContextProxy : public SkScalerContext {
 * public:
 *     SkScalerContextProxy(SkTypeface& tf,
 *                          const SkScalerContextEffects& effects,
 *                          const SkDescriptor* desc,
 *                          sk_sp<SkStrikeClient::DiscardableHandleManager> manager);
 *
 * protected:
 *     GlyphMetrics generateMetrics(const SkGlyph&, SkArenaAlloc*) override;
 *     void generateImage(const SkGlyph&, void*) override;
 *     std::optional<GeneratedPath> generatePath(const SkGlyph&) override;
 *     sk_sp<SkDrawable> generateDrawable(const SkGlyph&) override;
 *     void generateFontMetrics(SkFontMetrics* metrics) override;
 *     SkTypefaceProxy* getProxyTypeface() const;
 *
 * private:
 *     sk_sp<SkStrikeClient::DiscardableHandleManager> fDiscardableManager;
 *     using INHERITED = SkScalerContext;
 * }
 * ```
 */
public open class SkScalerContextProxy public constructor(
  tf: SkTypeface,
  effects: SkScalerContextEffects,
  desc: SkDescriptor?,
  manager: SkSp<SkStrikeClient.DiscardableHandleManager>,
) : SkScalerContext(TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkStrikeClient::DiscardableHandleManager> fDiscardableManager
   * ```
   */
  private var fDiscardableManager: SkSp<SkStrikeClient.DiscardableHandleManager> =
      TODO("Initialize fDiscardableManager")

  /**
   * C++ original:
   * ```cpp
   * SkScalerContext::GlyphMetrics SkScalerContextProxy::generateMetrics(const SkGlyph& glyph,
   *                                                                     SkArenaAlloc*) {
   *     TRACE_EVENT1("skia", "generateMetrics", "rec", TRACE_STR_COPY(this->getRec().dump().c_str()));
   *     if (this->getProxyTypeface()->isLogging()) {
   *         SkDebugf("GlyphCacheMiss generateMetrics looking for glyph: %x\n  generateMetrics: %s\n",
   *                  glyph.getPackedID().value(), this->getRec().dump().c_str());
   *     }
   *
   *     fDiscardableManager->notifyCacheMiss(
   *                                          SkStrikeClient::CacheMissType::kGlyphMetrics, fRec.fTextSize);
   *
   *     return {glyph.maskFormat()};
   * }
   * ```
   */
  protected override fun generateMetrics(glyph: SkGlyph, param1: SkArenaAlloc?): GlyphMetrics {
    TODO("Implement generateMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkScalerContextProxy::generateImage(const SkGlyph& glyph, void*) {
   *     TRACE_EVENT1("skia", "generateImage", "rec", TRACE_STR_COPY(this->getRec().dump().c_str()));
   *     if (this->getProxyTypeface()->isLogging()) {
   *         SkDebugf("GlyphCacheMiss generateImage: %s\n", this->getRec().dump().c_str());
   *     }
   *
   *     // There is no desperation search here, because if there was an image to be found it was
   *     // copied over with the metrics search.
   *     fDiscardableManager->notifyCacheMiss(
   *             SkStrikeClient::CacheMissType::kGlyphImage, fRec.fTextSize);
   * }
   * ```
   */
  protected override fun generateImage(glyph: SkGlyph, param1: Unit?) {
    TODO("Implement generateImage")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkScalerContext::GeneratedPath> SkScalerContextProxy::generatePath(const SkGlyph&) {
   *     TRACE_EVENT1("skia", "generatePath", "rec", TRACE_STR_COPY(this->getRec().dump().c_str()));
   *     if (this->getProxyTypeface()->isLogging()) {
   *         SkDebugf("GlyphCacheMiss generatePath: %s\n", this->getRec().dump().c_str());
   *     }
   *
   *     fDiscardableManager->notifyCacheMiss(
   *             SkStrikeClient::CacheMissType::kGlyphPath, fRec.fTextSize);
   *     return {};
   * }
   * ```
   */
  protected override fun generatePath(param0: SkGlyph): Int {
    TODO("Implement generatePath")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkDrawable> SkScalerContextProxy::generateDrawable(const SkGlyph&) {
   *     TRACE_EVENT1("skia", "generateDrawable", "rec", TRACE_STR_COPY(this->getRec().dump().c_str()));
   *     if (this->getProxyTypeface()->isLogging()) {
   *         SkDebugf("GlyphCacheMiss generateDrawable: %s\n", this->getRec().dump().c_str());
   *     }
   *
   *     fDiscardableManager->notifyCacheMiss(
   *             SkStrikeClient::CacheMissType::kGlyphDrawable, fRec.fTextSize);
   *     return nullptr;
   * }
   * ```
   */
  protected override fun generateDrawable(param0: SkGlyph): SkSp<SkDrawable> {
    TODO("Implement generateDrawable")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkScalerContextProxy::generateFontMetrics(SkFontMetrics* metrics) {
   *     TRACE_EVENT1(
   *             "skia", "generateFontMetrics", "rec", TRACE_STR_COPY(this->getRec().dump().c_str()));
   *     if (this->getProxyTypeface()->isLogging()) {
   *         SkDebugf("GlyphCacheMiss generateFontMetrics: %s\n", this->getRec().dump().c_str());
   *     }
   *
   *     // Font metrics aren't really used for render, so just zero out the data and return.
   *     fDiscardableManager->notifyCacheMiss(
   *             SkStrikeClient::CacheMissType::kFontMetrics, fRec.fTextSize);
   *     sk_bzero(metrics, sizeof(*metrics));
   * }
   * ```
   */
  protected override fun generateFontMetrics(metrics: SkFontMetrics?) {
    TODO("Implement generateFontMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTypefaceProxy* SkScalerContextProxy::getProxyTypeface() const {
   *     return (SkTypefaceProxy*)this->getTypeface();
   * }
   * ```
   */
  protected fun getProxyTypeface(): SkTypefaceProxy {
    TODO("Implement getProxyTypeface")
  }
}
