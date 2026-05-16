package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * class TestFontCollection : public FontCollection {
 * public:
 *     // if load is true, will load the fonts (using Freetype, Core Text, or DirectWrite) from
 *     // resourceDir.
 *     TestFontCollection(const std::string& resourceDir, bool testOnly = false, bool loadFonts = true);
 *
 *     size_t fontsFound() const { return fFontsFound; }
 *     bool addFontFromFile(const std::string& path, const std::string& familyName = "");
 *
 * private:
 *     std::string fResourceDir;
 *     size_t fFontsFound;
 *     sk_sp<TypefaceFontProvider> fFontProvider;
 *     std::string fDirs;
 * }
 * ```
 */
public open class TestFontCollection public constructor(
  resourceDir: String,
  testOnly: Boolean = TODO(),
  loadFonts: Boolean = TODO(),
) : FontCollection() {
  /**
   * C++ original:
   * ```cpp
   * std::string fResourceDir
   * ```
   */
  private var fResourceDir: Int = TODO("Initialize fResourceDir")

  /**
   * C++ original:
   * ```cpp
   * size_t fFontsFound
   * ```
   */
  private var fFontsFound: Int = TODO("Initialize fFontsFound")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<TypefaceFontProvider> fFontProvider
   * ```
   */
  private var fFontProvider: Int = TODO("Initialize fFontProvider")

  /**
   * C++ original:
   * ```cpp
   * std::string fDirs
   * ```
   */
  private var fDirs: Int = TODO("Initialize fDirs")

  /**
   * C++ original:
   * ```cpp
   * size_t fontsFound() const { return fFontsFound; }
   * ```
   */
  public fun fontsFound(): Int {
    TODO("Implement fontsFound")
  }

  /**
   * C++ original:
   * ```cpp
   * bool TestFontCollection::addFontFromFile(const std::string& path, const std::string& familyName) {
   *
   *     SkString file_path;
   *     file_path.printf("%s/%s", fResourceDir.c_str(), path.c_str());
   *
   *     std::unique_ptr<SkStreamAsset> file = SkFILEStream::Make(file_path.c_str());
   *     if (!file) {
   *         return false;
   *     }
   * #if defined(SK_TYPEFACE_FACTORY_FREETYPE)
   *     sk_sp<SkTypeface> face =
   *             SkTypeface_FreeType::MakeFromStream(std::move(file), SkFontArguments());
   * #elif defined(SK_TYPEFACE_FACTORY_CORETEXT)
   *     sk_sp<SkTypeface> face = SkTypeface_Mac::MakeFromStream(std::move(file), SkFontArguments());
   * #elif defined(SK_TYPEFACE_FACTORY_DIRECTWRITE)
   *     sk_sp<SkTypeface> face = DWriteFontTypeface::MakeFromStream(std::move(file), SkFontArguments());
   * #else
   *     sk_sp<SkTypeface> face = nullptr;
   * #endif
   *     if (familyName.empty()) {
   *         fFontProvider->registerTypeface(std::move(face));
   *     } else {
   *         fFontProvider->registerTypeface(std::move(face), SkString(familyName.c_str()));
   *     }
   *
   *     return true;
   * }
   * ```
   */
  public fun addFontFromFile(path: String, familyName: String = TODO()): Boolean {
    TODO("Implement addFontFromFile")
  }
}
