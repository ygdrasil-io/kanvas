package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class ResourceFontCollection : public FontCollection {
 *     static const std::vector<sk_sp<SkTypeface>>& getTypefaces() {
 *         static std::vector<sk_sp<SkTypeface>> typefaces = []() -> std::vector<sk_sp<SkTypeface>> {
 *             if (FLAGS_paragraph_fonts.size() == 0) {
 *                 return {};
 *             }
 *             TArray<SkString> paths;
 *             {
 *                 SkString fontResources = GetResourcePath(FLAGS_paragraph_fonts[0]);
 *                 const char* fontDir = fontResources.c_str();
 *                 SkOSFile::Iter iter(fontDir);
 *                 SkString path;
 *                 while (iter.next(&path)) {
 *                     if ((false)) {
 *                         SkDebugf("Found font file: %s\n", path.c_str());
 *                     }
 *                     SkString fullPath;
 *                     fullPath.printf("%s/%s", fontDir, path.c_str());
 *                     paths.emplace_back(fullPath);
 *                 }
 *                 if (paths.size()) {
 *                     SkTQSort(paths.begin(), paths.end(),
 *                              [](const SkString& a, const SkString& b) {
 *                                  return strcmp(a.c_str(), b.c_str()) < 0;
 *                              });
 *                 }
 *             }
 *
 *             sk_sp<SkFontMgr> mgr = ToolUtils::TestFontMgr();
 *             std::vector<sk_sp<SkTypeface>> typefaces;
 *             bool fontsFound = false;
 *             for (auto&& path : paths) {
 *                 if ((false)) {
 *                     SkDebugf("Reading font: %s\n", path.c_str());
 *                 }
 *                 auto stream = SkStream::MakeFromFile(path.c_str());
 *                 SkASSERTF(stream, "%s not readable", path.c_str());
 *                 sk_sp<SkTypeface> typeface = mgr->makeFromStream(std::move(stream), {});
 *                 // Without --nativeFonts, DM will use the portable test font manager which does
 *                 // not know how to read in fonts from bytes.
 *                 if (typeface) {
 *                     if ((false)) {
 *                         SkString familyName;
 *                         typeface->getFamilyName(&familyName);
 *                         SkDebugf("Creating: %s size: %zu\n",
 *                                  familyName.c_str(),
 *                                  typeface->openExistingStream(nullptr)->getLength());
 *                     }
 *                     if (path.endsWith("Roboto-Italic.ttf")) {
 *                         fontsFound = true;
 *                     }
 *                     typefaces.emplace_back(std::move(typeface));
 *                 } else {
 *                     SkDEBUGF("%s was not turned into a Typeface. Did you set --nativeFonts?\n",
 *                              path.c_str());
 *                 }
 *             }
 *             SkASSERTF_RELEASE(typefaces.size(), "--paragraph_fonts set but no fonts found."
 *                                                 "Did you set --nativeFonts?");
 *             SkASSERTF_RELEASE(fontsFound, "--paragraph_fonts set but Roboto-Italic.ttf not found");
 *             return typefaces;
 *         }();
 *         return typefaces;
 *     }
 * public:
 *     ResourceFontCollection(bool testOnly = false)
 *             : fFontsFound(false)
 *             , fResolvedFonts(0)
 *             , fFontProvider(sk_make_sp<TypefaceFontProvider>()) {
 *         const std::vector<sk_sp<SkTypeface>>& typefaces = getTypefaces();
 *         fFontsFound = !typefaces.empty();
 *         for (auto&& typeface : typefaces) {
 *             fFontProvider->registerTypeface(typeface);
 *         }
 *
 *         if (testOnly) {
 *             this->setTestFontManager(std::move(fFontProvider));
 *         } else {
 *             this->setAssetFontManager(std::move(fFontProvider));
 *         }
 *         this->disableFontFallback();
 *     }
 *
 *     size_t resolvedFonts() const { return fResolvedFonts; }
 *
 *     // TODO: temp solution until we check in fonts
 *     bool fontsFound() const { return fFontsFound; }
 *
 * private:
 *     bool fFontsFound;
 *     size_t fResolvedFonts;
 *     sk_sp<TypefaceFontProvider> fFontProvider;
 * }
 * ```
 */
public open class ResourceFontCollection public constructor(
  testOnly: Boolean = TODO(),
) : FontCollection() {
  /**
   * C++ original:
   * ```cpp
   * bool fFontsFound
   * ```
   */
  private var fFontsFound: Boolean = TODO("Initialize fFontsFound")

  /**
   * C++ original:
   * ```cpp
   * size_t fResolvedFonts
   * ```
   */
  private var fResolvedFonts: ULong = TODO("Initialize fResolvedFonts")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<TypefaceFontProvider> fFontProvider
   * ```
   */
  private var fFontProvider: SkSp<TypefaceFontProvider> = TODO("Initialize fFontProvider")

  /**
   * C++ original:
   * ```cpp
   * size_t resolvedFonts() const { return fResolvedFonts; }
   * ```
   */
  public fun resolvedFonts(): ULong {
    TODO("Implement resolvedFonts")
  }

  /**
   * C++ original:
   * ```cpp
   * bool fontsFound() const { return fFontsFound; }
   * ```
   */
  public fun fontsFound(): Boolean {
    TODO("Implement fontsFound")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static const std::vector<sk_sp<SkTypeface>>& getTypefaces() {
     *         static std::vector<sk_sp<SkTypeface>> typefaces = []() -> std::vector<sk_sp<SkTypeface>> {
     *             if (FLAGS_paragraph_fonts.size() == 0) {
     *                 return {};
     *             }
     *             TArray<SkString> paths;
     *             {
     *                 SkString fontResources = GetResourcePath(FLAGS_paragraph_fonts[0]);
     *                 const char* fontDir = fontResources.c_str();
     *                 SkOSFile::Iter iter(fontDir);
     *                 SkString path;
     *                 while (iter.next(&path)) {
     *                     if ((false)) {
     *                         SkDebugf("Found font file: %s\n", path.c_str());
     *                     }
     *                     SkString fullPath;
     *                     fullPath.printf("%s/%s", fontDir, path.c_str());
     *                     paths.emplace_back(fullPath);
     *                 }
     *                 if (paths.size()) {
     *                     SkTQSort(paths.begin(), paths.end(),
     *                              [](const SkString& a, const SkString& b) {
     *                                  return strcmp(a.c_str(), b.c_str()) < 0;
     *                              });
     *                 }
     *             }
     *
     *             sk_sp<SkFontMgr> mgr = ToolUtils::TestFontMgr();
     *             std::vector<sk_sp<SkTypeface>> typefaces;
     *             bool fontsFound = false;
     *             for (auto&& path : paths) {
     *                 if ((false)) {
     *                     SkDebugf("Reading font: %s\n", path.c_str());
     *                 }
     *                 auto stream = SkStream::MakeFromFile(path.c_str());
     *                 SkASSERTF(stream, "%s not readable", path.c_str());
     *                 sk_sp<SkTypeface> typeface = mgr->makeFromStream(std::move(stream), {});
     *                 // Without --nativeFonts, DM will use the portable test font manager which does
     *                 // not know how to read in fonts from bytes.
     *                 if (typeface) {
     *                     if ((false)) {
     *                         SkString familyName;
     *                         typeface->getFamilyName(&familyName);
     *                         SkDebugf("Creating: %s size: %zu\n",
     *                                  familyName.c_str(),
     *                                  typeface->openExistingStream(nullptr)->getLength());
     *                     }
     *                     if (path.endsWith("Roboto-Italic.ttf")) {
     *                         fontsFound = true;
     *                     }
     *                     typefaces.emplace_back(std::move(typeface));
     *                 } else {
     *                     SkDEBUGF("%s was not turned into a Typeface. Did you set --nativeFonts?\n",
     *                              path.c_str());
     *                 }
     *             }
     *             SkASSERTF_RELEASE(typefaces.size(), "--paragraph_fonts set but no fonts found."
     *                                                 "Did you set --nativeFonts?");
     *             SkASSERTF_RELEASE(fontsFound, "--paragraph_fonts set but Roboto-Italic.ttf not found");
     *             return typefaces;
     *         }();
     *         return typefaces;
     *     }
     * ```
     */
    private fun getTypefaces(): Int {
      TODO("Implement getTypefaces")
    }
  }
}
