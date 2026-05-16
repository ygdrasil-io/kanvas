package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.ULong
import org.skia.tests.Reporter
import undefined.RunInfo

/**
 * C++ original:
 * ```cpp
 * struct RunHandler final : public SkShaper::RunHandler {
 *     const char* fResource;
 *     skiatest::Reporter* fReporter;
 *     const char* fUtf8;
 *     size_t fUtf8Size;
 *     std::unique_ptr<SkGlyphID[]> fGlyphs;
 *     std::unique_ptr<SkPoint[]> fPositions;
 *     std::unique_ptr<uint32_t[]> fClusters;
 *     SkShaper::RunHandler::Range fRange;
 *     unsigned fGlyphCount = 0;
 *
 *     bool fBeginLine = false;
 *     bool fCommitRunInfo = false;
 *     bool fCommitLine = false;
 *
 *     RunHandler(const char* resource, skiatest::Reporter* reporter, const char* utf8,size_t utf8Size)
 *         : fResource(resource), fReporter(reporter), fUtf8(utf8), fUtf8Size(utf8Size) {}
 *
 *     void beginLine() override { fBeginLine = true;}
 *     void runInfo(const SkShaper::RunHandler::RunInfo& info) override {}
 *     void commitRunInfo() override { fCommitRunInfo = true; }
 *     SkShaper::RunHandler::Buffer runBuffer(const SkShaper::RunHandler::RunInfo& info) override {
 *         fGlyphCount = SkToUInt(info.glyphCount);
 *         fRange = info.utf8Range;
 *         fGlyphs = std::make_unique<SkGlyphID[]>(info.glyphCount);
 *         fPositions = std::make_unique<SkPoint[]>(info.glyphCount);
 *         fClusters = std::make_unique<uint32_t[]>(info.glyphCount);
 *         return SkShaper::RunHandler::Buffer{fGlyphs.get(),
 *                                             fPositions.get(),
 *                                             nullptr,
 *                                             fClusters.get(),
 *                                             {0, 0}};
 *     }
 *     void commitRunBuffer(const RunInfo& info) override {
 *         REPORTER_ASSERT(fReporter, fGlyphCount == info.glyphCount, "%s", fResource);
 *         REPORTER_ASSERT(fReporter, fRange.begin() == info.utf8Range.begin(), "%s", fResource);
 *         REPORTER_ASSERT(fReporter, fRange.size() == info.utf8Range.size(), "%s", fResource);
 *         if (!(fRange.begin() + fRange.size() <= fUtf8Size)) {
 *             REPORTER_ASSERT(fReporter, fRange.begin() + fRange.size() <= fUtf8Size, "%s",fResource);
 *             return;
 *         }
 *
 *         if ((false)) {
 *             SkString familyName;
 *             SkString postscriptName;
 *             SkTypeface* typeface = info.fFont.getTypeface();
 *             int ttcIndex = 0;
 *             size_t fontSize = 0;
 *             if (typeface) {
 *                 typeface->getFamilyName(&familyName);
 *                 typeface->getPostScriptName(&postscriptName);
 *                 std::unique_ptr<SkStreamAsset> stream = typeface->openStream(&ttcIndex);
 *                 if (stream) {
 *                     fontSize = stream->getLength();
 *                 }
 *             }
 *             SkString glyphs;
 *             for (auto&& [glyph, cluster] : SkZip(info.glyphCount, fGlyphs.get(), fClusters.get())) {
 *                 glyphs.appendU32(glyph);
 *                 glyphs.append(":");
 *                 glyphs.appendU32(cluster);
 *                 glyphs.append(" ");
 *             }
 *             SkString chars;
 *             for (const char c : SkSpan(fUtf8 + fRange.begin(), fRange.size())) {
 *                 chars.appendHex((unsigned char)c, 2);
 *                 chars.append(" ");
 *             }
 *             SkDebugf(
 *                 "%s range: %zu-%zu(%zu) glyphCount:%u font: \"%s\" \"%s\" #%d %zuB\n"
 *                 "rangeText: \"%.*s\"\n"
 *                 "rangeBytes: %s\n"
 *                 "glyphs:%s\n\n",
 *                 fResource, fRange.begin(), fRange.end(), fRange.size(), fGlyphCount,
 *                 familyName.c_str(), postscriptName.c_str(), ttcIndex, fontSize,
 *                 (int)fRange.size(), fUtf8 + fRange.begin(),
 *                 chars.c_str(),
 *                 glyphs.c_str());
 *         }
 *
 *         for (unsigned i = 0; i < fGlyphCount; ++i) {
 *             REPORTER_ASSERT(fReporter, fClusters[i] >= fRange.begin(),
 *                             "%" PRIu32 " >= %zu %s i:%u glyphCount:%u",
 *                             fClusters[i], fRange.begin(), fResource, i, fGlyphCount);
 *             REPORTER_ASSERT(fReporter, fClusters[i] < fRange.end(),
 *                             "%" PRIu32 " < %zu %s i:%u glyphCount:%u",
 *                             fClusters[i], fRange.end(), fResource, i, fGlyphCount);
 *         }
 *     }
 *     void commitLine() override { fCommitLine = true; }
 * }
 * ```
 */
public class RunHandler public constructor(
  /**
   * C++ original:
   * ```cpp
   * const char* fResource
   * ```
   */
  public val fResource: String?,
  /**
   * C++ original:
   * ```cpp
   * skiatest::Reporter* fReporter
   * ```
   */
  public var fReporter: Reporter?,
  /**
   * C++ original:
   * ```cpp
   * const char* fUtf8
   * ```
   */
  public val fUtf8: String?,
  /**
   * C++ original:
   * ```cpp
   * size_t fUtf8Size
   * ```
   */
  public var fUtf8Size: Int,
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkGlyphID[]> fGlyphs
   * ```
   */
  public var fGlyphs: Int,
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkPoint[]> fPositions
   * ```
   */
  public var fPositions: Int,
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<uint32_t[]> fClusters
   * ```
   */
  public var fClusters: Int,
  /**
   * C++ original:
   * ```cpp
   * SkShaper::RunHandler::Range fRange
   * ```
   */
  public var fRange: RunHandler.Range,
  /**
   * C++ original:
   * ```cpp
   * unsigned fGlyphCount = 0
   * ```
   */
  public var fGlyphCount: UInt,
  /**
   * C++ original:
   * ```cpp
   * bool fBeginLine = false
   * ```
   */
  public var fBeginLine: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fCommitRunInfo = false
   * ```
   */
  public var fCommitRunInfo: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fCommitLine = false
   * ```
   */
  public var fCommitLine: Boolean,
) : SkShaper.RunHandler(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * RunHandler(const char* resource, skiatest::Reporter* reporter, const char* utf8,size_t utf8Size)
   *         : fResource(resource), fReporter(reporter), fUtf8(utf8), fUtf8Size(utf8Size) {}
   * ```
   */
  public constructor(
    resource: String?,
    reporter: Reporter?,
    utf8: String?,
    utf8Size: ULong,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * void beginLine() override { fBeginLine = true;}
   * ```
   */
  public override fun beginLine() {
    TODO("Implement beginLine")
  }

  /**
   * C++ original:
   * ```cpp
   * void runInfo(const SkShaper::RunHandler::RunInfo& info) override {}
   * ```
   */
  public override fun runInfo(info: RunHandler.RunInfo) {
    TODO("Implement runInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * void commitRunInfo() override { fCommitRunInfo = true; }
   * ```
   */
  public override fun commitRunInfo() {
    TODO("Implement commitRunInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkShaper::RunHandler::Buffer runBuffer(const SkShaper::RunHandler::RunInfo& info) override {
   *         fGlyphCount = SkToUInt(info.glyphCount);
   *         fRange = info.utf8Range;
   *         fGlyphs = std::make_unique<SkGlyphID[]>(info.glyphCount);
   *         fPositions = std::make_unique<SkPoint[]>(info.glyphCount);
   *         fClusters = std::make_unique<uint32_t[]>(info.glyphCount);
   *         return SkShaper::RunHandler::Buffer{fGlyphs.get(),
   *                                             fPositions.get(),
   *                                             nullptr,
   *                                             fClusters.get(),
   *                                             {0, 0}};
   *     }
   * ```
   */
  public override fun runBuffer(info: RunHandler.RunInfo): RunHandler.Buffer {
    TODO("Implement runBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void commitRunBuffer(const RunInfo& info) override {
   *         REPORTER_ASSERT(fReporter, fGlyphCount == info.glyphCount, "%s", fResource);
   *         REPORTER_ASSERT(fReporter, fRange.begin() == info.utf8Range.begin(), "%s", fResource);
   *         REPORTER_ASSERT(fReporter, fRange.size() == info.utf8Range.size(), "%s", fResource);
   *         if (!(fRange.begin() + fRange.size() <= fUtf8Size)) {
   *             REPORTER_ASSERT(fReporter, fRange.begin() + fRange.size() <= fUtf8Size, "%s",fResource);
   *             return;
   *         }
   *
   *         if ((false)) {
   *             SkString familyName;
   *             SkString postscriptName;
   *             SkTypeface* typeface = info.fFont.getTypeface();
   *             int ttcIndex = 0;
   *             size_t fontSize = 0;
   *             if (typeface) {
   *                 typeface->getFamilyName(&familyName);
   *                 typeface->getPostScriptName(&postscriptName);
   *                 std::unique_ptr<SkStreamAsset> stream = typeface->openStream(&ttcIndex);
   *                 if (stream) {
   *                     fontSize = stream->getLength();
   *                 }
   *             }
   *             SkString glyphs;
   *             for (auto&& [glyph, cluster] : SkZip(info.glyphCount, fGlyphs.get(), fClusters.get())) {
   *                 glyphs.appendU32(glyph);
   *                 glyphs.append(":");
   *                 glyphs.appendU32(cluster);
   *                 glyphs.append(" ");
   *             }
   *             SkString chars;
   *             for (const char c : SkSpan(fUtf8 + fRange.begin(), fRange.size())) {
   *                 chars.appendHex((unsigned char)c, 2);
   *                 chars.append(" ");
   *             }
   *             SkDebugf(
   *                 "%s range: %zu-%zu(%zu) glyphCount:%u font: \"%s\" \"%s\" #%d %zuB\n"
   *                 "rangeText: \"%.*s\"\n"
   *                 "rangeBytes: %s\n"
   *                 "glyphs:%s\n\n",
   *                 fResource, fRange.begin(), fRange.end(), fRange.size(), fGlyphCount,
   *                 familyName.c_str(), postscriptName.c_str(), ttcIndex, fontSize,
   *                 (int)fRange.size(), fUtf8 + fRange.begin(),
   *                 chars.c_str(),
   *                 glyphs.c_str());
   *         }
   *
   *         for (unsigned i = 0; i < fGlyphCount; ++i) {
   *             REPORTER_ASSERT(fReporter, fClusters[i] >= fRange.begin(),
   *                             "%" PRIu32 " >= %zu %s i:%u glyphCount:%u",
   *                             fClusters[i], fRange.begin(), fResource, i, fGlyphCount);
   *             REPORTER_ASSERT(fReporter, fClusters[i] < fRange.end(),
   *                             "%" PRIu32 " < %zu %s i:%u glyphCount:%u",
   *                             fClusters[i], fRange.end(), fResource, i, fGlyphCount);
   *         }
   *     }
   * ```
   */
  public override fun commitRunBuffer(info: RunInfo) {
    TODO("Implement commitRunBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void commitLine() override { fCommitLine = true; }
   * ```
   */
  public override fun commitLine() {
    TODO("Implement commitLine")
  }
}
