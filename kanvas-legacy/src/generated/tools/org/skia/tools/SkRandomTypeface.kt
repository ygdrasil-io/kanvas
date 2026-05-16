package org.skia.tools

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkDescriptor
import org.skia.foundation.SkFontArguments
import org.skia.foundation.SkFontDescriptor
import org.skia.foundation.SkFontParameters
import org.skia.foundation.SkFontTableTag
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkPaint
import org.skia.foundation.SkScalerContextEffects
import org.skia.foundation.SkScalerContextRec
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.foundation.SkTypeface
import org.skia.foundation.SkUnichar

/**
 * C++ original:
 * ```cpp
 * class SkRandomTypeface : public SkTypeface {
 * public:
 *     SkRandomTypeface(sk_sp<SkTypeface> proxy, const SkPaint&, bool fakeit);
 *
 *     SkTypeface* proxy() const { return fProxy.get(); }
 *     const SkPaint& paint() const { return fPaint; }
 *
 * protected:
 *     std::unique_ptr<SkScalerContext> onCreateScalerContext(const SkScalerContextEffects&,
 *                                                            const SkDescriptor*) const override;
 *     void onFilterRec(SkScalerContextRec*) const override;
 *     void getGlyphToUnicodeMap(SkSpan<SkUnichar>) const override;
 *     std::unique_ptr<SkAdvancedTypefaceMetrics> onGetAdvancedMetrics() const override;
 *     std::unique_ptr<SkStreamAsset> onOpenStream(int* ttcIndex) const override;
 *     sk_sp<SkTypeface> onMakeClone(const SkFontArguments& args) const override;
 *     void onGetFontDescriptor(SkFontDescriptor*, bool* isLocal) const override;
 *
 *     void onCharsToGlyphs(SkSpan<const SkUnichar>, SkSpan<SkGlyphID>) const override;
 *     int onCountGlyphs() const override;
 *     int onGetUPEM() const override;
 *
 *     void onGetFamilyName(SkString* familyName) const override;
 *     bool onGetPostScriptName(SkString*) const override;
 *     SkTypeface::LocalizedStrings* onCreateFamilyNameIterator() const override;
 *
 *     void getPostScriptGlyphNames(SkString*) const override;
 *
 *     bool onGlyphMaskNeedsCurrentColor() const override;
 *     int onGetVariationDesignPosition(
 *                              SkSpan<SkFontArguments::VariationPosition::Coordinate>) const override;
 *     int onGetVariationDesignParameters(SkSpan<SkFontParameters::Variation::Axis>) const override;
 *     int onGetTableTags(SkSpan<SkFontTableTag>) const override;
 *     size_t onGetTableData(SkFontTableTag, size_t offset, size_t length, void* data) const override;
 *
 * private:
 *     sk_sp<SkTypeface> fProxy;
 *     SkPaint           fPaint;
 *     bool              fFakeIt;
 * }
 * ```
 */
public open class SkRandomTypeface public constructor(
  proxy: SkSp<SkTypeface>,
  paint: SkPaint,
  fakeit: Boolean,
) : SkTypeface(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> fProxy
   * ```
   */
  private var fProxy: SkSp<SkTypeface> = TODO("Initialize fProxy")

  /**
   * C++ original:
   * ```cpp
   * SkPaint           fPaint
   * ```
   */
  private var fPaint: SkPaint = TODO("Initialize fPaint")

  /**
   * C++ original:
   * ```cpp
   * bool              fFakeIt
   * ```
   */
  private var fFakeIt: Boolean = TODO("Initialize fFakeIt")

  /**
   * C++ original:
   * ```cpp
   * SkTypeface* proxy() const { return fProxy.get(); }
   * ```
   */
  public fun proxy(): SkTypeface {
    TODO("Implement proxy")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkPaint& paint() const { return fPaint; }
   * ```
   */
  public fun paint(): SkPaint {
    TODO("Implement paint")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkScalerContext> SkRandomTypeface::onCreateScalerContext(
   *     const SkScalerContextEffects& effects, const SkDescriptor* desc) const
   * {
   *     return std::make_unique<RandomScalerContext>(
   *             *const_cast<SkRandomTypeface*>(this), effects, desc, fFakeIt);
   * }
   * ```
   */
  protected override fun onCreateScalerContext(effects: SkScalerContextEffects, desc: SkDescriptor?): Int {
    TODO("Implement onCreateScalerContext")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRandomTypeface::onFilterRec(SkScalerContextRec* rec) const {
   *     fProxy->filterRec(rec);
   *     rec->setHinting(SkFontHinting::kNone);
   *     rec->fMaskFormat = SkMask::kARGB32_Format;
   * }
   * ```
   */
  protected override fun onFilterRec(rec: SkScalerContextRec?) {
    TODO("Implement onFilterRec")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRandomTypeface::getGlyphToUnicodeMap(SkSpan<SkUnichar> glyphToUnicode) const {
   *     fProxy->getGlyphToUnicodeMap(glyphToUnicode);
   * }
   * ```
   */
  protected override fun getGlyphToUnicodeMap(glyphToUnicode: SkSpan<SkUnichar>) {
    TODO("Implement getGlyphToUnicodeMap")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkAdvancedTypefaceMetrics> SkRandomTypeface::onGetAdvancedMetrics() const {
   *     return fProxy->getAdvancedMetrics();
   * }
   * ```
   */
  protected override fun onGetAdvancedMetrics(): Int {
    TODO("Implement onGetAdvancedMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkStreamAsset> SkRandomTypeface::onOpenStream(int* ttcIndex) const {
   *     return fProxy->openStream(ttcIndex);
   * }
   * ```
   */
  protected override fun onOpenStream(ttcIndex: Int?): Int {
    TODO("Implement onOpenStream")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> SkRandomTypeface::onMakeClone(const SkFontArguments& args) const {
   *     sk_sp<SkTypeface> proxy = fProxy->makeClone(args);
   *     if (!proxy) {
   *         return nullptr;
   *     }
   *     return sk_make_sp<SkRandomTypeface>(proxy, fPaint, fFakeIt);
   * }
   * ```
   */
  protected override fun onMakeClone(args: SkFontArguments): SkSp<SkTypeface> {
    TODO("Implement onMakeClone")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRandomTypeface::onGetFontDescriptor(SkFontDescriptor* desc, bool* isLocal) const {
   *     // TODO: anything that uses this typeface isn't correctly serializable, since this typeface
   *     // cannot be deserialized.
   *     fProxy->getFontDescriptor(desc, isLocal);
   * }
   * ```
   */
  protected override fun onGetFontDescriptor(desc: SkFontDescriptor?, isLocal: Boolean?) {
    TODO("Implement onGetFontDescriptor")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRandomTypeface::onCharsToGlyphs(SkSpan<const SkUnichar> uni,
   *                                        SkSpan<SkGlyphID> glyphs) const {
   *     fProxy->unicharsToGlyphs(uni, glyphs);
   * }
   * ```
   */
  protected override fun onCharsToGlyphs(uni: SkSpan<SkUnichar>, glyphs: SkSpan<SkGlyphID>) {
    TODO("Implement onCharsToGlyphs")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkRandomTypeface::onCountGlyphs() const { return fProxy->countGlyphs(); }
   * ```
   */
  protected override fun onCountGlyphs(): Int {
    TODO("Implement onCountGlyphs")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkRandomTypeface::onGetUPEM() const { return fProxy->getUnitsPerEm(); }
   * ```
   */
  protected override fun onGetUPEM(): Int {
    TODO("Implement onGetUPEM")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRandomTypeface::onGetFamilyName(SkString* familyName) const {
   *     fProxy->getFamilyName(familyName);
   * }
   * ```
   */
  protected override fun onGetFamilyName(familyName: String?) {
    TODO("Implement onGetFamilyName")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRandomTypeface::onGetPostScriptName(SkString* postScriptName) const {
   *     return fProxy->getPostScriptName(postScriptName);
   * }
   * ```
   */
  protected override fun onGetPostScriptName(postScriptName: String?): Boolean {
    TODO("Implement onGetPostScriptName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTypeface::LocalizedStrings* SkRandomTypeface::onCreateFamilyNameIterator() const {
   *     return fProxy->createFamilyNameIterator();
   * }
   * ```
   */
  protected override fun onCreateFamilyNameIterator(): SkTypeface.LocalizedStrings {
    TODO("Implement onCreateFamilyNameIterator")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRandomTypeface::getPostScriptGlyphNames(SkString* names) const {
   *     return fProxy->getPostScriptGlyphNames(names);
   * }
   * ```
   */
  protected override fun getPostScriptGlyphNames(names: String?) {
    TODO("Implement getPostScriptGlyphNames")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRandomTypeface::onGlyphMaskNeedsCurrentColor() const {
   *     return fProxy->glyphMaskNeedsCurrentColor();
   * }
   * ```
   */
  protected override fun onGlyphMaskNeedsCurrentColor(): Boolean {
    TODO("Implement onGlyphMaskNeedsCurrentColor")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkRandomTypeface::onGetVariationDesignPosition(
   *                        SkSpan<SkFontArguments::VariationPosition::Coordinate> coordinates) const {
   *     return fProxy->onGetVariationDesignPosition(coordinates);
   * }
   * ```
   */
  protected override fun onGetVariationDesignPosition(coordinates: SkSpan<SkFontArguments.VariationPosition.Coordinate>): Int {
    TODO("Implement onGetVariationDesignPosition")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkRandomTypeface::onGetVariationDesignParameters(
   *                                      SkSpan<SkFontParameters::Variation::Axis> parameters) const {
   *     return fProxy->onGetVariationDesignParameters(parameters);
   * }
   * ```
   */
  protected override fun onGetVariationDesignParameters(param0: SkSpan<SkFontParameters.Variation.Axis>): Int {
    TODO("Implement onGetVariationDesignParameters")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkRandomTypeface::onGetTableTags(SkSpan<SkFontTableTag> tags) const {
   *     return fProxy->readTableTags(tags);
   * }
   * ```
   */
  protected override fun onGetTableTags(tags: SkSpan<SkFontTableTag>): Int {
    TODO("Implement onGetTableTags")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkRandomTypeface::onGetTableData(SkFontTableTag tag,
   *                                         size_t         offset,
   *                                         size_t         length,
   *                                         void*          data) const {
   *     return fProxy->getTableData(tag, offset, length, data);
   * }
   * ```
   */
  protected override fun onGetTableData(
    tag: SkFontTableTag,
    offset: ULong,
    length: ULong,
    `data`: Unit?,
  ): ULong {
    TODO("Implement onGetTableData")
  }
}
