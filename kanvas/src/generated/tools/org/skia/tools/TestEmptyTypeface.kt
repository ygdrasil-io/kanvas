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
import org.skia.foundation.SkScalerContextEffects
import org.skia.foundation.SkScalerContextRec
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.foundation.SkTypeface
import org.skia.foundation.SkUnichar

/**
 * C++ original:
 * ```cpp
 * class TestEmptyTypeface : public SkTypeface {
 * public:
 *     static sk_sp<SkTypeface> Make() { return sk_sp<SkTypeface>(new TestEmptyTypeface); }
 *
 * protected:
 *     TestEmptyTypeface() : SkTypeface(SkFontStyle(), true) {}
 *
 *     std::unique_ptr<SkStreamAsset> onOpenStream(int* ttcIndex) const override { return nullptr; }
 *     sk_sp<SkTypeface> onMakeClone(const SkFontArguments& args) const override {
 *         return sk_ref_sp(this);
 *     }
 *     std::unique_ptr<SkScalerContext> onCreateScalerContext(
 *         const SkScalerContextEffects& effects, const SkDescriptor* desc) const override
 *     {
 *         return SkScalerContext::MakeEmpty(*const_cast<TestEmptyTypeface*>(this), effects, desc);
 *     }
 *     void onFilterRec(SkScalerContextRec*) const override {}
 *     std::unique_ptr<SkAdvancedTypefaceMetrics> onGetAdvancedMetrics() const override {
 *         return nullptr;
 *     }
 *     void onGetFontDescriptor(SkFontDescriptor*, bool*) const override {}
 *     void onCharsToGlyphs(SkSpan<const SkUnichar>, SkSpan<SkGlyphID> glyphs) const override {
 *         sk_bzero(glyphs.data(), glyphs.size_bytes());
 *     }
 *     int onCountGlyphs() const override { return 0; }
 *     void getPostScriptGlyphNames(SkString*) const override {}
 *     void getGlyphToUnicodeMap(SkSpan<SkUnichar>) const override {}
 *     int onGetUPEM() const override { return 0; }
 *     class EmptyLocalizedStrings : public SkTypeface::LocalizedStrings {
 *     public:
 *         bool next(SkTypeface::LocalizedString*) override { return false; }
 *     };
 *     void onGetFamilyName(SkString* familyName) const override { familyName->reset(); }
 *     bool onGetPostScriptName(SkString*) const override { return false; }
 *     SkTypeface::LocalizedStrings* onCreateFamilyNameIterator() const override {
 *         return new EmptyLocalizedStrings;
 *     }
 *     bool onGlyphMaskNeedsCurrentColor() const override { return false; }
 *     int onGetVariationDesignPosition(
 *                          SkSpan<SkFontArguments::VariationPosition::Coordinate>) const override {
 *         return 0;
 *     }
 *     int onGetVariationDesignParameters(SkSpan<SkFontParameters::Variation::Axis>) const override {
 *         return 0;
 *     }
 *     int    onGetTableTags(SkSpan<SkFontTableTag>) const override { return 0; }
 *     size_t onGetTableData(SkFontTableTag, size_t, size_t, void*) const override { return 0; }
 * }
 * ```
 */
public open class TestEmptyTypeface public constructor() : SkTypeface(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkStreamAsset> onOpenStream(int* ttcIndex) const override { return nullptr; }
   * ```
   */
  protected override fun onOpenStream(ttcIndex: Int?): Int {
    TODO("Implement onOpenStream")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> onMakeClone(const SkFontArguments& args) const override {
   *         return sk_ref_sp(this);
   *     }
   * ```
   */
  protected override fun onMakeClone(args: SkFontArguments): SkSp<SkTypeface> {
    TODO("Implement onMakeClone")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkScalerContext> onCreateScalerContext(
   *         const SkScalerContextEffects& effects, const SkDescriptor* desc) const override
   *     {
   *         return SkScalerContext::MakeEmpty(*const_cast<TestEmptyTypeface*>(this), effects, desc);
   *     }
   * ```
   */
  protected override fun onCreateScalerContext(effects: SkScalerContextEffects, desc: SkDescriptor?): Int {
    TODO("Implement onCreateScalerContext")
  }

  /**
   * C++ original:
   * ```cpp
   * void onFilterRec(SkScalerContextRec*) const override {}
   * ```
   */
  protected override fun onFilterRec(param0: SkScalerContextRec?) {
    TODO("Implement onFilterRec")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkAdvancedTypefaceMetrics> onGetAdvancedMetrics() const override {
   *         return nullptr;
   *     }
   * ```
   */
  protected override fun onGetAdvancedMetrics(): Int {
    TODO("Implement onGetAdvancedMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * void onGetFontDescriptor(SkFontDescriptor*, bool*) const override {}
   * ```
   */
  protected override fun onGetFontDescriptor(param0: SkFontDescriptor?, param1: Boolean?) {
    TODO("Implement onGetFontDescriptor")
  }

  /**
   * C++ original:
   * ```cpp
   * void onCharsToGlyphs(SkSpan<const SkUnichar>, SkSpan<SkGlyphID> glyphs) const override {
   *         sk_bzero(glyphs.data(), glyphs.size_bytes());
   *     }
   * ```
   */
  protected override fun onCharsToGlyphs(param0: SkSpan<SkUnichar>, glyphs: SkSpan<SkGlyphID>) {
    TODO("Implement onCharsToGlyphs")
  }

  /**
   * C++ original:
   * ```cpp
   * int onCountGlyphs() const override { return 0; }
   * ```
   */
  protected override fun onCountGlyphs(): Int {
    TODO("Implement onCountGlyphs")
  }

  /**
   * C++ original:
   * ```cpp
   * void getPostScriptGlyphNames(SkString*) const override {}
   * ```
   */
  protected override fun getPostScriptGlyphNames(param0: String?) {
    TODO("Implement getPostScriptGlyphNames")
  }

  /**
   * C++ original:
   * ```cpp
   * void getGlyphToUnicodeMap(SkSpan<SkUnichar>) const override {}
   * ```
   */
  protected override fun getGlyphToUnicodeMap(param0: SkSpan<SkUnichar>) {
    TODO("Implement getGlyphToUnicodeMap")
  }

  /**
   * C++ original:
   * ```cpp
   * int onGetUPEM() const override { return 0; }
   * ```
   */
  protected override fun onGetUPEM(): Int {
    TODO("Implement onGetUPEM")
  }

  /**
   * C++ original:
   * ```cpp
   * void onGetFamilyName(SkString* familyName) const override { familyName->reset(); }
   * ```
   */
  public override fun onGetFamilyName(familyName: String?) {
    TODO("Implement onGetFamilyName")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onGetPostScriptName(SkString*) const override { return false; }
   * ```
   */
  public override fun onGetPostScriptName(param0: String?): Boolean {
    TODO("Implement onGetPostScriptName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTypeface::LocalizedStrings* onCreateFamilyNameIterator() const override {
   *         return new EmptyLocalizedStrings;
   *     }
   * ```
   */
  public override fun onCreateFamilyNameIterator(): SkTypeface.LocalizedStrings {
    TODO("Implement onCreateFamilyNameIterator")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onGlyphMaskNeedsCurrentColor() const override { return false; }
   * ```
   */
  public override fun onGlyphMaskNeedsCurrentColor(): Boolean {
    TODO("Implement onGlyphMaskNeedsCurrentColor")
  }

  /**
   * C++ original:
   * ```cpp
   * int onGetVariationDesignPosition(
   *                          SkSpan<SkFontArguments::VariationPosition::Coordinate>) const override {
   *         return 0;
   *     }
   * ```
   */
  public override fun onGetVariationDesignPosition(param0: SkSpan<SkFontArguments.VariationPosition.Coordinate>): Int {
    TODO("Implement onGetVariationDesignPosition")
  }

  /**
   * C++ original:
   * ```cpp
   * int onGetVariationDesignParameters(SkSpan<SkFontParameters::Variation::Axis>) const override {
   *         return 0;
   *     }
   * ```
   */
  public override fun onGetVariationDesignParameters(param0: SkSpan<SkFontParameters.Variation.Axis>): Int {
    TODO("Implement onGetVariationDesignParameters")
  }

  /**
   * C++ original:
   * ```cpp
   * int    onGetTableTags(SkSpan<SkFontTableTag>) const override { return 0; }
   * ```
   */
  public override fun onGetTableTags(param0: SkSpan<SkFontTableTag>): Int {
    TODO("Implement onGetTableTags")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t onGetTableData(SkFontTableTag, size_t, size_t, void*) const override { return 0; }
   * ```
   */
  public override fun onGetTableData(
    param0: SkFontTableTag,
    param1: ULong,
    param2: ULong,
    param3: Unit?,
  ): ULong {
    TODO("Implement onGetTableData")
  }

  public open class EmptyLocalizedStrings : SkTypeface.LocalizedStrings() {
    public override fun next(param0: SkTypeface.LocalizedString?): Boolean {
      TODO("Implement next")
    }
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkTypeface> Make() { return sk_sp<SkTypeface>(new TestEmptyTypeface); }
     * ```
     */
    public fun make(): SkSp<SkTypeface> {
      TODO("Implement make")
    }
  }
}
