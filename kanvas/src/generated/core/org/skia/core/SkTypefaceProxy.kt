package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkDescriptor
import org.skia.foundation.SkFontArguments
import org.skia.foundation.SkFontDescriptor
import org.skia.foundation.SkFontParameters
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkFontTableTag
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkScalerContextEffects
import org.skia.foundation.SkScalerContextRec
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.foundation.SkTypeface
import org.skia.foundation.SkTypefaceID
import org.skia.foundation.SkUnichar
import org.skia.utils.SkStrikeClient

/**
 * C++ original:
 * ```cpp
 * class SkTypefaceProxy : public SkTypeface {
 * public:
 *     SkTypefaceProxy(const SkTypefaceProxyPrototype& prototype,
 *                     sk_sp<SkStrikeClient::DiscardableHandleManager> manager,
 *                     bool isLogging = true);
 *
 *     SkTypefaceProxy(SkTypefaceID typefaceID,
 *                     int glyphCount,
 *                     const SkFontStyle& style,
 *                     bool isFixedPitch,
 *                     bool glyphMaskNeedsCurrentColor,
 *                     sk_sp<SkStrikeClient::DiscardableHandleManager> manager,
 *                     bool isLogging = true);
 *
 *     SkTypefaceID remoteTypefaceID() const {return fTypefaceID;}
 *
 *     int glyphCount() const {return fGlyphCount;}
 *
 *     bool isLogging() const {return fIsLogging;}
 *
 * protected:
 *     int onGetUPEM() const override { SK_ABORT("Should never be called."); }
 *     std::unique_ptr<SkStreamAsset> onOpenStream(int* ttcIndex) const override {
 *         SK_ABORT("Should never be called.");
 *     }
 *     sk_sp<SkTypeface> onMakeClone(const SkFontArguments& args) const override {
 *         SK_ABORT("Should never be called.");
 *     }
 *     bool onGlyphMaskNeedsCurrentColor() const override {
 *         return fGlyphMaskNeedsCurrentColor;
 *     }
 *     int onGetVariationDesignPosition(
 *                          SkSpan<SkFontArguments::VariationPosition::Coordinate>) const override {
 *         SK_ABORT("Should never be called.");
 *     }
 *     int onGetVariationDesignParameters(SkSpan<SkFontParameters::Variation::Axis>) const override {
 *         SK_ABORT("Should never be called.");
 *     }
 *     void onGetFamilyName(SkString* familyName) const override {
 *         // Used by SkStrikeCache::DumpMemoryStatistics.
 *         *familyName = "";
 *     }
 *     bool onGetPostScriptName(SkString*) const override {
 *         SK_ABORT("Should never be called.");
 *     }
 *     SkTypeface::LocalizedStrings* onCreateFamilyNameIterator() const override {
 *         SK_ABORT("Should never be called.");
 *     }
 *     int onGetTableTags(SkSpan<SkFontTableTag>) const override {
 *         SK_ABORT("Should never be called.");
 *     }
 *     size_t onGetTableData(SkFontTableTag, size_t offset, size_t length, void* data) const override {
 *         SK_ABORT("Should never be called.");
 *     }
 *     std::unique_ptr<SkScalerContext> onCreateScalerContext(
 *         const SkScalerContextEffects& effects, const SkDescriptor* desc) const override
 *     {
 *         return std::make_unique<SkScalerContextProxy>(
 *                 *const_cast<SkTypefaceProxy*>(this), effects, desc, fDiscardableManager);
 *     }
 *     void onFilterRec(SkScalerContextRec* rec) const override {
 *         // The rec filtering is already applied by the server when generating
 *         // the glyphs.
 *     }
 *     void onGetFontDescriptor(SkFontDescriptor*, bool*) const override {
 *         SK_ABORT("Should never be called.");
 *     }
 *     void getGlyphToUnicodeMap(SkSpan<SkUnichar>) const override {
 *         SK_ABORT("Should never be called.");
 *     }
 *
 *     void getPostScriptGlyphNames(SkString*) const override {
 *         SK_ABORT("Should never be called.");
 *     }
 *
 *     std::unique_ptr<SkAdvancedTypefaceMetrics> onGetAdvancedMetrics() const override {
 *         SK_ABORT("Should never be called.");
 *     }
 *     void onCharsToGlyphs(SkSpan<const SkUnichar>, SkSpan<SkGlyphID>) const override {
 *         SK_ABORT("Should never be called.");
 *     }
 *     int onCountGlyphs() const override {
 *         return this->glyphCount();
 *     }
 *
 *     void* onGetCTFontRef() const override {
 *         SK_ABORT("Should never be called.");
 *     }
 *
 * private:
 *     const SkTypefaceID                              fTypefaceID;
 *     const int                                       fGlyphCount;
 *     const bool                                      fIsLogging;
 *     const bool                                      fGlyphMaskNeedsCurrentColor;
 *     sk_sp<SkStrikeClient::DiscardableHandleManager> fDiscardableManager;
 * }
 * ```
 */
public open class SkTypefaceProxy public constructor(
  prototype: SkTypefaceProxyPrototype,
  manager: SkSp<SkStrikeClient.DiscardableHandleManager>,
  isLogging: Boolean = TODO(),
) : SkTypeface(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * const SkTypefaceID                              fTypefaceID
   * ```
   */
  private val fTypefaceID: SkTypeface = TODO("Initialize fTypefaceID")

  /**
   * C++ original:
   * ```cpp
   * const int                                       fGlyphCount
   * ```
   */
  private val fGlyphCount: Int = TODO("Initialize fGlyphCount")

  /**
   * C++ original:
   * ```cpp
   * const bool                                      fIsLogging
   * ```
   */
  private val fIsLogging: Boolean = TODO("Initialize fIsLogging")

  /**
   * C++ original:
   * ```cpp
   * const bool                                      fGlyphMaskNeedsCurrentColor
   * ```
   */
  private val fGlyphMaskNeedsCurrentColor: Boolean = TODO("Initialize fGlyphMaskNeedsCurrentColor")

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
   * SkTypefaceProxy::SkTypefaceProxy(const SkTypefaceProxyPrototype& prototype,
   *                                  sk_sp<SkStrikeClient::DiscardableHandleManager> manager,
   *                                  bool isLogging)
   *         : SkTypeface{prototype.style(), prototype.fIsFixedPitch}
   *         , fTypefaceID{prototype.fServerTypefaceID}
   *         , fGlyphCount{prototype.fGlyphCount}
   *         , fIsLogging{isLogging}
   *         , fGlyphMaskNeedsCurrentColor{prototype.fGlyphMaskNeedsCurrentColor}
   *         , fDiscardableManager{std::move(manager)} {}
   * ```
   */
  public constructor(
    typefaceID: SkTypefaceID,
    glyphCount: Int,
    style: SkFontStyle,
    isFixedPitch: Boolean,
    glyphMaskNeedsCurrentColor: Boolean,
    manager: SkSp<SkStrikeClient.DiscardableHandleManager>,
    isLogging: Boolean = TODO(),
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTypefaceID remoteTypefaceID() const {return fTypefaceID;}
   * ```
   */
  public fun remoteTypefaceID(): SkTypeface {
    TODO("Implement remoteTypefaceID")
  }

  /**
   * C++ original:
   * ```cpp
   * int glyphCount() const {return fGlyphCount;}
   * ```
   */
  public fun glyphCount(): Int {
    TODO("Implement glyphCount")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isLogging() const {return fIsLogging;}
   * ```
   */
  public fun isLogging(): Boolean {
    TODO("Implement isLogging")
  }

  /**
   * C++ original:
   * ```cpp
   * int onGetUPEM() const override { SK_ABORT("Should never be called."); }
   * ```
   */
  protected override fun onGetUPEM(): Int {
    TODO("Implement onGetUPEM")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkStreamAsset> onOpenStream(int* ttcIndex) const override {
   *         SK_ABORT("Should never be called.");
   *     }
   * ```
   */
  protected override fun onOpenStream(ttcIndex: Int?): Int {
    TODO("Implement onOpenStream")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> onMakeClone(const SkFontArguments& args) const override {
   *         SK_ABORT("Should never be called.");
   *     }
   * ```
   */
  protected override fun onMakeClone(args: SkFontArguments): SkSp<SkTypeface> {
    TODO("Implement onMakeClone")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onGlyphMaskNeedsCurrentColor() const override {
   *         return fGlyphMaskNeedsCurrentColor;
   *     }
   * ```
   */
  protected override fun onGlyphMaskNeedsCurrentColor(): Boolean {
    TODO("Implement onGlyphMaskNeedsCurrentColor")
  }

  /**
   * C++ original:
   * ```cpp
   * int onGetVariationDesignPosition(
   *                          SkSpan<SkFontArguments::VariationPosition::Coordinate>) const override {
   *         SK_ABORT("Should never be called.");
   *     }
   * ```
   */
  protected override fun onGetVariationDesignPosition(param0: SkSpan<SkFontArguments.VariationPosition.Coordinate>): Int {
    TODO("Implement onGetVariationDesignPosition")
  }

  /**
   * C++ original:
   * ```cpp
   * int onGetVariationDesignParameters(SkSpan<SkFontParameters::Variation::Axis>) const override {
   *         SK_ABORT("Should never be called.");
   *     }
   * ```
   */
  protected override fun onGetVariationDesignParameters(param0: SkSpan<SkFontParameters.Variation.Axis>): Int {
    TODO("Implement onGetVariationDesignParameters")
  }

  /**
   * C++ original:
   * ```cpp
   * void onGetFamilyName(SkString* familyName) const override {
   *         // Used by SkStrikeCache::DumpMemoryStatistics.
   *         *familyName = "";
   *     }
   * ```
   */
  protected override fun onGetFamilyName(familyName: String?) {
    TODO("Implement onGetFamilyName")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onGetPostScriptName(SkString*) const override {
   *         SK_ABORT("Should never be called.");
   *     }
   * ```
   */
  protected override fun onGetPostScriptName(param0: String?): Boolean {
    TODO("Implement onGetPostScriptName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTypeface::LocalizedStrings* onCreateFamilyNameIterator() const override {
   *         SK_ABORT("Should never be called.");
   *     }
   * ```
   */
  protected override fun onCreateFamilyNameIterator(): SkTypeface.LocalizedStrings {
    TODO("Implement onCreateFamilyNameIterator")
  }

  /**
   * C++ original:
   * ```cpp
   * int onGetTableTags(SkSpan<SkFontTableTag>) const override {
   *         SK_ABORT("Should never be called.");
   *     }
   * ```
   */
  protected override fun onGetTableTags(param0: SkSpan<SkFontTableTag>): Int {
    TODO("Implement onGetTableTags")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t onGetTableData(SkFontTableTag, size_t offset, size_t length, void* data) const override {
   *         SK_ABORT("Should never be called.");
   *     }
   * ```
   */
  protected override fun onGetTableData(
    param0: SkFontTableTag,
    offset: ULong,
    length: ULong,
    `data`: Unit?,
  ): ULong {
    TODO("Implement onGetTableData")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkScalerContext> onCreateScalerContext(
   *         const SkScalerContextEffects& effects, const SkDescriptor* desc) const override
   *     {
   *         return std::make_unique<SkScalerContextProxy>(
   *                 *const_cast<SkTypefaceProxy*>(this), effects, desc, fDiscardableManager);
   *     }
   * ```
   */
  protected override fun onCreateScalerContext(effects: SkScalerContextEffects, desc: SkDescriptor?): Int {
    TODO("Implement onCreateScalerContext")
  }

  /**
   * C++ original:
   * ```cpp
   * void onFilterRec(SkScalerContextRec* rec) const override {
   *         // The rec filtering is already applied by the server when generating
   *         // the glyphs.
   *     }
   * ```
   */
  protected override fun onFilterRec(rec: SkScalerContextRec?) {
    TODO("Implement onFilterRec")
  }

  /**
   * C++ original:
   * ```cpp
   * void onGetFontDescriptor(SkFontDescriptor*, bool*) const override {
   *         SK_ABORT("Should never be called.");
   *     }
   * ```
   */
  protected override fun onGetFontDescriptor(param0: SkFontDescriptor?, param1: Boolean?) {
    TODO("Implement onGetFontDescriptor")
  }

  /**
   * C++ original:
   * ```cpp
   * void getGlyphToUnicodeMap(SkSpan<SkUnichar>) const override {
   *         SK_ABORT("Should never be called.");
   *     }
   * ```
   */
  protected override fun getGlyphToUnicodeMap(param0: SkSpan<SkUnichar>) {
    TODO("Implement getGlyphToUnicodeMap")
  }

  /**
   * C++ original:
   * ```cpp
   * void getPostScriptGlyphNames(SkString*) const override {
   *         SK_ABORT("Should never be called.");
   *     }
   * ```
   */
  protected override fun getPostScriptGlyphNames(param0: String?) {
    TODO("Implement getPostScriptGlyphNames")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkAdvancedTypefaceMetrics> onGetAdvancedMetrics() const override {
   *         SK_ABORT("Should never be called.");
   *     }
   * ```
   */
  protected override fun onGetAdvancedMetrics(): Int {
    TODO("Implement onGetAdvancedMetrics")
  }

  /**
   * C++ original:
   * ```cpp
   * void onCharsToGlyphs(SkSpan<const SkUnichar>, SkSpan<SkGlyphID>) const override {
   *         SK_ABORT("Should never be called.");
   *     }
   * ```
   */
  protected override fun onCharsToGlyphs(param0: SkSpan<SkUnichar>, param1: SkSpan<SkGlyphID>) {
    TODO("Implement onCharsToGlyphs")
  }

  /**
   * C++ original:
   * ```cpp
   * int onCountGlyphs() const override {
   *         return this->glyphCount();
   *     }
   * ```
   */
  protected override fun onCountGlyphs(): Int {
    TODO("Implement onCountGlyphs")
  }

  /**
   * C++ original:
   * ```cpp
   * void* onGetCTFontRef() const override {
   *         SK_ABORT("Should never be called.");
   *     }
   * ```
   */
  protected override fun onGetCTFontRef() {
    TODO("Implement onGetCTFontRef")
  }
}
