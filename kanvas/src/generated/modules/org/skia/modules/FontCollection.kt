package org.skia.modules

import kotlin.Any
import kotlin.Boolean
import kotlin.CharArray
import kotlin.Int
import kotlin.String
import kotlin.ULong
import kotlin.collections.List
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSp
import org.skia.foundation.SkUnichar

/**
 * C++ original:
 * ```cpp
 * class FontCollection : public SkRefCnt {
 * public:
 *     FontCollection();
 *
 *     size_t getFontManagersCount() const;
 *
 *     void setAssetFontManager(sk_sp<SkFontMgr> fontManager);
 *     void setDynamicFontManager(sk_sp<SkFontMgr> fontManager);
 *     void setTestFontManager(sk_sp<SkFontMgr> fontManager);
 *     void setDefaultFontManager(sk_sp<SkFontMgr> fontManager);
 *     void setDefaultFontManager(sk_sp<SkFontMgr> fontManager, const char defaultFamilyName[]);
 *     void setDefaultFontManager(sk_sp<SkFontMgr> fontManager, const std::vector<SkString>& defaultFamilyNames);
 *
 *     sk_sp<SkFontMgr> getFallbackManager() const { return fDefaultFontManager; }
 *
 *     std::vector<sk_sp<SkTypeface>> findTypefaces(const std::vector<SkString>& familyNames, SkFontStyle fontStyle);
 *     std::vector<sk_sp<SkTypeface>> findTypefaces(const std::vector<SkString>& familyNames, SkFontStyle fontStyle, const std::optional<FontArguments>& fontArgs);
 *
 *     sk_sp<SkTypeface> defaultFallback(SkUnichar unicode, const std::vector<SkString>& families,
 *                                       SkFontStyle fontStyle, const SkString& locale,
 *                                       const std::optional<FontArguments>& fontArgs);
 *     sk_sp<SkTypeface> defaultEmojiFallback(SkUnichar emojiStart, SkFontStyle fontStyle, const SkString& locale);
 *     sk_sp<SkTypeface> defaultFallback();
 *
 *     void disableFontFallback();
 *     void enableFontFallback();
 *     bool fontFallbackEnabled() { return fEnableFontFallback; }
 *
 *     ParagraphCache* getParagraphCache() { return &fParagraphCache; }
 *
 *     void clearCaches();
 *
 * private:
 *     std::vector<sk_sp<SkFontMgr>> getFontManagerOrder() const;
 *
 *     sk_sp<SkTypeface> matchTypeface(const SkString& familyName, SkFontStyle fontStyle);
 *
 *     struct FamilyKey {
 *         FamilyKey(const std::vector<SkString>& familyNames, SkFontStyle style, const std::optional<FontArguments>& args)
 *                 : fFamilyNames(familyNames), fFontStyle(style), fFontArguments(args) {}
 *
 *         FamilyKey() {}
 *
 *         std::vector<SkString> fFamilyNames;
 *         SkFontStyle fFontStyle;
 *         std::optional<FontArguments> fFontArguments;
 *
 *         bool operator==(const FamilyKey& other) const;
 *
 *         struct Hasher {
 *             size_t operator()(const FamilyKey& key) const;
 *         };
 *     };
 *
 *     bool fEnableFontFallback;
 *     skia_private::THashMap<FamilyKey, std::vector<sk_sp<SkTypeface>>, FamilyKey::Hasher> fTypefaces;
 *     sk_sp<SkFontMgr> fDefaultFontManager;
 *     sk_sp<SkFontMgr> fAssetFontManager;
 *     sk_sp<SkFontMgr> fDynamicFontManager;
 *     sk_sp<SkFontMgr> fTestFontManager;
 *
 *     std::vector<SkString> fDefaultFamilyNames;
 *     ParagraphCache fParagraphCache;
 * }
 * ```
 */
public open class FontCollection public constructor() : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * bool fEnableFontFallback
   * ```
   */
  private var fEnableFontFallback: Boolean = TODO("Initialize fEnableFontFallback")

  /**
   * C++ original:
   * ```cpp
   * skia_private::THashMap<FamilyKey, std::vector<sk_sp<SkTypeface>>, FamilyKey::Hasher> fTypefaces
   * ```
   */
  private var fTypefaces: Int = TODO("Initialize fTypefaces")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFontMgr> fDefaultFontManager
   * ```
   */
  private var fDefaultFontManager: Int = TODO("Initialize fDefaultFontManager")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFontMgr> fAssetFontManager
   * ```
   */
  private var fAssetFontManager: Int = TODO("Initialize fAssetFontManager")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFontMgr> fDynamicFontManager
   * ```
   */
  private var fDynamicFontManager: Int = TODO("Initialize fDynamicFontManager")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFontMgr> fTestFontManager
   * ```
   */
  private var fTestFontManager: Int = TODO("Initialize fTestFontManager")

  /**
   * C++ original:
   * ```cpp
   * std::vector<SkString> fDefaultFamilyNames
   * ```
   */
  private var fDefaultFamilyNames: Int = TODO("Initialize fDefaultFamilyNames")

  /**
   * C++ original:
   * ```cpp
   * ParagraphCache fParagraphCache
   * ```
   */
  private var fParagraphCache: Int = TODO("Initialize fParagraphCache")

  /**
   * C++ original:
   * ```cpp
   * size_t FontCollection::getFontManagersCount() const { return this->getFontManagerOrder().size(); }
   * ```
   */
  public fun getFontManagersCount(): ULong {
    TODO("Implement getFontManagersCount")
  }

  /**
   * C++ original:
   * ```cpp
   * void FontCollection::setAssetFontManager(sk_sp<SkFontMgr> font_manager) {
   *     fAssetFontManager = std::move(font_manager);
   * }
   * ```
   */
  public fun setAssetFontManager(fontManager: SkSp<SkFontMgr>) {
    TODO("Implement setAssetFontManager")
  }

  /**
   * C++ original:
   * ```cpp
   * void FontCollection::setDynamicFontManager(sk_sp<SkFontMgr> font_manager) {
   *     fDynamicFontManager = std::move(font_manager);
   * }
   * ```
   */
  public fun setDynamicFontManager(fontManager: SkSp<SkFontMgr>) {
    TODO("Implement setDynamicFontManager")
  }

  /**
   * C++ original:
   * ```cpp
   * void FontCollection::setTestFontManager(sk_sp<SkFontMgr> font_manager) {
   *     fTestFontManager = std::move(font_manager);
   * }
   * ```
   */
  public fun setTestFontManager(fontManager: SkSp<SkFontMgr>) {
    TODO("Implement setTestFontManager")
  }

  /**
   * C++ original:
   * ```cpp
   * void FontCollection::setDefaultFontManager(sk_sp<SkFontMgr> fontManager) {
   *     fDefaultFontManager = std::move(fontManager);
   * }
   * ```
   */
  public fun setDefaultFontManager(fontManager: SkSp<SkFontMgr>) {
    TODO("Implement setDefaultFontManager")
  }

  /**
   * C++ original:
   * ```cpp
   * void setDefaultFontManager(sk_sp<SkFontMgr> fontManager, const char defaultFamilyName[])
   * ```
   */
  public fun setDefaultFontManager(fontManager: SkSp<SkFontMgr>, defaultFamilyName: CharArray) {
    TODO("Implement setDefaultFontManager")
  }

  /**
   * C++ original:
   * ```cpp
   * void setDefaultFontManager(sk_sp<SkFontMgr> fontManager, const std::vector<SkString>& defaultFamilyNames)
   * ```
   */
  public fun setDefaultFontManager(fontManager: SkSp<SkFontMgr>, defaultFamilyNames: List<String>) {
    TODO("Implement setDefaultFontManager")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFontMgr> getFallbackManager() const { return fDefaultFontManager; }
   * ```
   */
  public fun getFallbackManager(): Int {
    TODO("Implement getFallbackManager")
  }

  /**
   * C++ original:
   * ```cpp
   * std::vector<sk_sp<SkTypeface>> FontCollection::findTypefaces(const std::vector<SkString>& familyNames, SkFontStyle fontStyle) {
   *     return findTypefaces(familyNames, fontStyle, std::nullopt);
   * }
   * ```
   */
  public fun findTypefaces(familyNames: List<String>, fontStyle: SkFontStyle): Int {
    TODO("Implement findTypefaces")
  }

  /**
   * C++ original:
   * ```cpp
   * std::vector<sk_sp<SkTypeface>> FontCollection::findTypefaces(const std::vector<SkString>& familyNames, SkFontStyle fontStyle, const std::optional<FontArguments>& fontArgs) {
   *     // Look inside the font collections cache first
   *     FamilyKey familyKey(familyNames, fontStyle, fontArgs);
   *     auto found = fTypefaces.find(familyKey);
   *     if (found) {
   *         return *found;
   *     }
   *
   *     std::vector<sk_sp<SkTypeface>> typefaces;
   *     for (const SkString& familyName : familyNames) {
   *         sk_sp<SkTypeface> match = matchTypeface(familyName, fontStyle);
   *         if (match && fontArgs) {
   *             match = fontArgs->CloneTypeface(match);
   *         }
   *         if (match) {
   *             typefaces.emplace_back(std::move(match));
   *         }
   *     }
   *
   *     if (typefaces.empty()) {
   *         sk_sp<SkTypeface> match;
   *         for (const SkString& familyName : fDefaultFamilyNames) {
   *             match = matchTypeface(familyName, fontStyle);
   *             if (match) {
   *                 break;
   *             }
   *         }
   *         if (!match) {
   *             for (const auto& manager : this->getFontManagerOrder()) {
   *                 match = manager->legacyMakeTypeface(nullptr, fontStyle);
   *                 if (match) {
   *                     break;
   *                 }
   *             }
   *         }
   *         if (match) {
   *             if (fontArgs) {
   *                 match = fontArgs->CloneTypeface(match);
   *             }
   *             typefaces.emplace_back(std::move(match));
   *         }
   *     }
   *
   *     fTypefaces.set(familyKey, typefaces);
   *     return typefaces;
   * }
   * ```
   */
  public fun findTypefaces(
    familyNames: List<String>,
    fontStyle: SkFontStyle,
    fontArgs: FontArguments?,
  ): Int {
    TODO("Implement findTypefaces")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> FontCollection::defaultFallback(SkUnichar unicode,
   *                                                   const std::vector<SkString>& families,
   *                                                   SkFontStyle fontStyle,
   *                                                   const SkString& locale,
   *                                                   const std::optional<FontArguments>& fontArgs) {
   *
   *     for (const auto& manager : this->getFontManagerOrder()) {
   *         std::vector<const char*> bcp47;
   *         if (!locale.isEmpty()) {
   *             bcp47.push_back(locale.c_str());
   *         }
   *         const char* familyName = families.empty() ? nullptr : families[0].c_str();
   *         sk_sp<SkTypeface> typeface(manager->matchFamilyStyleCharacter(
   *             familyName, fontStyle, bcp47.data(), bcp47.size(), unicode));
   *
   *         if (typeface != nullptr) {
   *             if (fontArgs) {
   *                 typeface = fontArgs->CloneTypeface(typeface);
   *             }
   *             return typeface;
   *         }
   *     }
   *     return nullptr;
   * }
   * ```
   */
  public fun defaultFallback(
    unicode: SkUnichar,
    families: List<String>,
    fontStyle: SkFontStyle,
    locale: String,
    fontArgs: FontArguments?,
  ): Int {
    TODO("Implement defaultFallback")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> FontCollection::defaultEmojiFallback(SkUnichar emojiStart,
   *                                                        SkFontStyle fontStyle,
   *                                                        const SkString& locale) {
   *
   *     for (const auto& manager : this->getFontManagerOrder()) {
   *         std::vector<const char*> bcp47;
   * #if defined(SK_BUILD_FOR_MAC) || defined(SK_BUILD_FOR_IOS)
   *         sk_sp<SkTypeface> emojiTypeface =
   *             fDefaultFontManager->matchFamilyStyle(kColorEmojiFontMac, SkFontStyle());
   *         if (emojiTypeface != nullptr) {
   *             return emojiTypeface;
   *         }
   * #else
   *           bcp47.push_back(kColorEmojiLocale);
   * #endif
   *         if (!locale.isEmpty()) {
   *             bcp47.push_back(locale.c_str());
   *         }
   *
   *         // Not really ideal since the first codepoint may not be the best one
   *         // but we start from a good colored emoji at least
   *         sk_sp<SkTypeface> typeface(manager->matchFamilyStyleCharacter(
   *             nullptr, fontStyle, bcp47.data(), bcp47.size(), emojiStart));
   *         if (typeface != nullptr) {
   *             // ... and stop as soon as we find something in hope it will work for all of them
   *             return typeface;
   *         }
   *     }
   *     return nullptr;
   * }
   * ```
   */
  public fun defaultEmojiFallback(
    emojiStart: SkUnichar,
    fontStyle: SkFontStyle,
    locale: String,
  ): Int {
    TODO("Implement defaultEmojiFallback")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> FontCollection::defaultFallback() {
   *     if (fDefaultFontManager == nullptr) {
   *         return nullptr;
   *     }
   *     for (const SkString& familyName : fDefaultFamilyNames) {
   *         sk_sp<SkTypeface> match = fDefaultFontManager->matchFamilyStyle(familyName.c_str(),
   *                                                                         SkFontStyle());
   *         if (match) {
   *             return match;
   *         }
   *     }
   *     return nullptr;
   * }
   * ```
   */
  public fun defaultFallback(): Int {
    TODO("Implement defaultFallback")
  }

  /**
   * C++ original:
   * ```cpp
   * void FontCollection::disableFontFallback() { fEnableFontFallback = false; }
   * ```
   */
  public fun disableFontFallback() {
    TODO("Implement disableFontFallback")
  }

  /**
   * C++ original:
   * ```cpp
   * void FontCollection::enableFontFallback() { fEnableFontFallback = true; }
   * ```
   */
  public fun enableFontFallback() {
    TODO("Implement enableFontFallback")
  }

  /**
   * C++ original:
   * ```cpp
   * bool fontFallbackEnabled() { return fEnableFontFallback; }
   * ```
   */
  public fun fontFallbackEnabled(): Boolean {
    TODO("Implement fontFallbackEnabled")
  }

  /**
   * C++ original:
   * ```cpp
   * ParagraphCache* getParagraphCache() { return &fParagraphCache; }
   * ```
   */
  public fun getParagraphCache(): Int {
    TODO("Implement getParagraphCache")
  }

  /**
   * C++ original:
   * ```cpp
   * void FontCollection::clearCaches() {
   *     fParagraphCache.reset();
   *     fTypefaces.reset();
   *     SkShapers::HB::PurgeCaches();
   * }
   * ```
   */
  public fun clearCaches() {
    TODO("Implement clearCaches")
  }

  /**
   * C++ original:
   * ```cpp
   * std::vector<sk_sp<SkFontMgr>> FontCollection::getFontManagerOrder() const {
   *     std::vector<sk_sp<SkFontMgr>> order;
   *     if (fDynamicFontManager) {
   *         order.push_back(fDynamicFontManager);
   *     }
   *     if (fAssetFontManager) {
   *         order.push_back(fAssetFontManager);
   *     }
   *     if (fTestFontManager) {
   *         order.push_back(fTestFontManager);
   *     }
   *     if (fDefaultFontManager && fEnableFontFallback) {
   *         order.push_back(fDefaultFontManager);
   *     }
   *     return order;
   * }
   * ```
   */
  private fun getFontManagerOrder(): Int {
    TODO("Implement getFontManagerOrder")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> FontCollection::matchTypeface(const SkString& familyName, SkFontStyle fontStyle) {
   *     for (const auto& manager : this->getFontManagerOrder()) {
   *         sk_sp<SkFontStyleSet> set(manager->matchFamily(familyName.c_str()));
   *         if (!set || set->count() == 0) {
   *             continue;
   *         }
   *
   *         sk_sp<SkTypeface> match(set->matchStyle(fontStyle));
   *         if (match) {
   *             return match;
   *         }
   *     }
   *
   *     return nullptr;
   * }
   * ```
   */
  private fun matchTypeface(familyName: String, fontStyle: SkFontStyle): Int {
    TODO("Implement matchTypeface")
  }

  public data class FamilyKey public constructor(
    public var fFamilyNames: Int,
    public var fFontStyle: Int,
    public var fFontArguments: Int,
  ) {
    public override operator fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }

    public open class Hasher {
      public operator fun invoke(key: undefined.FamilyKey): ULong {
        TODO("Implement invoke")
      }
    }
  }
}
