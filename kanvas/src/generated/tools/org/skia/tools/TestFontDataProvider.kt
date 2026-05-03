package org.skia.tools

import kotlin.Boolean
import kotlin.Int
import org.skia.modules.ArrayValue

/**
 * C++ original:
 * ```cpp
 * class TestFontDataProvider {
 * public:
 *     struct LangSample {
 *         SkString langTag;
 *         SkString sampleShort;
 *         SkString sampleLong;
 *     };
 *
 *     struct TestSet {
 *         SkString fontName;
 *         SkString fontFilename;
 *         std::vector<LangSample> langSamples;
 *     };
 *
 *     TestFontDataProvider(const std::string& fontFilterRegexp, const std::string& langFilterRegexp);
 *
 *     bool next(TestSet* testSet);
 *
 *     void rewind();
 *
 * private:
 *     std::vector<LangSample> getLanguageSamples(const skjson::ArrayValue* languages);
 *     std::regex fFontFilter;
 *     std::regex fLangFilter;
 *     size_t fFontsIndex = 0;
 *     std::unique_ptr<skjson::DOM> fJsonDom;
 *     const skjson::ArrayValue* fFonts = nullptr;
 *     const skjson::ObjectValue* fSamples = nullptr;
 * }
 * ```
 */
public data class TestFontDataProvider public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::regex fFontFilter
   * ```
   */
  private var fFontFilter: Int,
  /**
   * C++ original:
   * ```cpp
   * std::regex fLangFilter
   * ```
   */
  private var fLangFilter: Int,
  /**
   * C++ original:
   * ```cpp
   * size_t fFontsIndex
   * ```
   */
  private var fFontsIndex: Int,
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<skjson::DOM> fJsonDom
   * ```
   */
  private var fJsonDom: Int,
  /**
   * C++ original:
   * ```cpp
   * const skjson::ArrayValue* fFonts
   * ```
   */
  private val fFonts: Int?,
  /**
   * C++ original:
   * ```cpp
   * const skjson::ObjectValue* fSamples
   * ```
   */
  private val fSamples: Int?,
) {
  /**
   * C++ original:
   * ```cpp
   * bool TestFontDataProvider::next(TestSet* testSet) {
   *     while (testSet && fFonts && fFontsIndex < fFonts->size()) {
   *         const skjson::ObjectValue* fontsEntry = (*fFonts)[fFontsIndex++];
   *         SkASSERT(fontsEntry);
   *         const skjson::StringValue* fontName = (*fontsEntry)["name"];
   *         SkASSERT(fontName);
   *         std::smatch match;
   *         std::string fontNameStr(fontName->str());
   *         if (std::regex_match(fontNameStr, match, fFontFilter)) {
   *             testSet->fontName = SkString(fontNameStr);
   *             const skjson::StringValue* fontFilename = (*fontsEntry)["path"];
   *             testSet->fontFilename = prefixWithFontsPath(
   *                     SkString(fontFilename->str().data(), fontFilename->str().size()));
   *             testSet->langSamples =
   *                     getLanguageSamples((*fontsEntry)["languages"].as<skjson::ArrayValue>());
   *             return true;
   *         }
   *     }
   *     return false;
   * }
   * ```
   */
  public fun next(testSet: TestSet?): Boolean {
    TODO("Implement next")
  }

  /**
   * C++ original:
   * ```cpp
   * void TestFontDataProvider::rewind() { fFontsIndex = 0; }
   * ```
   */
  public fun rewind() {
    TODO("Implement rewind")
  }

  /**
   * C++ original:
   * ```cpp
   * std::vector<TestFontDataProvider::LangSample> TestFontDataProvider::getLanguageSamples(
   *         const skjson::ArrayValue* languages) {
   *     std::vector<LangSample> samples;
   *     for (size_t i = 0; i < languages->size(); ++i) {
   *         const skjson::StringValue* langTag = (*languages)[i];
   *         std::string langTagStr(langTag->str());
   *         std::smatch match;
   *         if (std::regex_match(langTagStr, match, fLangFilter)) {
   *             const skjson::ObjectValue* sample = (*fSamples)[langTagStr.c_str()];
   *             const skjson::StringValue* shortSample = (*sample)["short_sample"];
   *             const skjson::StringValue* longSample = (*sample)["long_sample"];
   *             SkString sampleShort(shortSample->str().data(), shortSample->str().size());
   *             SkString sampleLong(longSample->str().data(), longSample->str().size());
   *             samples.push_back({SkString(langTagStr), sampleShort, sampleLong});
   *         }
   *     }
   *     SkASSERT_RELEASE(samples.size());
   *     return samples;
   * }
   * ```
   */
  private fun getLanguageSamples(languages: ArrayValue?): Int {
    TODO("Implement getLanguageSamples")
  }

  public data class LangSample public constructor(
    public var langTag: Int,
    public var sampleShort: Int,
    public var sampleLong: Int,
  )

  public data class TestSet public constructor(
    public var fontName: Int,
    public var fontFilename: Int,
    public var langSamples: Int,
  )
}
