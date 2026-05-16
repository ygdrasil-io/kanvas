package org.skia.modules

import kotlin.Int
import kotlin.String
import kotlin.UInt
import org.skia.gpu.Buffer
import org.skia.math.SkPoint
import undefined.RunInfo

/**
 * C++ original:
 * ```cpp
 * class SKSHAPER_API SkTextBlobBuilderRunHandler final : public SkShaper::RunHandler {
 * public:
 *     SkTextBlobBuilderRunHandler(const char* utf8Text, SkPoint offset)
 *         : fUtf8Text(utf8Text)
 *         , fOffset(offset) {}
 *     sk_sp<SkTextBlob> makeBlob();
 *     SkPoint endPoint() { return fOffset; }
 *
 *     void beginLine() override;
 *     void runInfo(const RunInfo&) override;
 *     void commitRunInfo() override;
 *     Buffer runBuffer(const RunInfo&) override;
 *     void commitRunBuffer(const RunInfo&) override;
 *     void commitLine() override;
 *
 * private:
 *     SkTextBlobBuilder fBuilder;
 *     char const * const fUtf8Text;
 *     uint32_t* fClusters;
 *     int fClusterOffset;
 *     int fGlyphCount;
 *     SkScalar fMaxRunAscent;
 *     SkScalar fMaxRunDescent;
 *     SkScalar fMaxRunLeading;
 *     SkPoint fCurrentPosition;
 *     SkPoint fOffset;
 * }
 * ```
 */
public class SkTextBlobBuilderRunHandler public constructor(
  utf8Text: String?,
  offset: SkPoint,
) : SkShaper.RunHandler() {
  /**
   * C++ original:
   * ```cpp
   * SkTextBlobBuilder fBuilder
   * ```
   */
  private var fBuilder: Int = TODO("Initialize fBuilder")

  /**
   * C++ original:
   * ```cpp
   * char const * const fUtf8Text
   * ```
   */
  private val fUtf8Text: String? = TODO("Initialize fUtf8Text")

  /**
   * C++ original:
   * ```cpp
   * uint32_t* fClusters
   * ```
   */
  private var fClusters: UInt? = TODO("Initialize fClusters")

  /**
   * C++ original:
   * ```cpp
   * int fClusterOffset
   * ```
   */
  private var fClusterOffset: Int = TODO("Initialize fClusterOffset")

  /**
   * C++ original:
   * ```cpp
   * int fGlyphCount
   * ```
   */
  private var fGlyphCount: Int = TODO("Initialize fGlyphCount")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fMaxRunAscent
   * ```
   */
  private var fMaxRunAscent: Int = TODO("Initialize fMaxRunAscent")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fMaxRunDescent
   * ```
   */
  private var fMaxRunDescent: Int = TODO("Initialize fMaxRunDescent")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fMaxRunLeading
   * ```
   */
  private var fMaxRunLeading: Int = TODO("Initialize fMaxRunLeading")

  /**
   * C++ original:
   * ```cpp
   * SkPoint fCurrentPosition
   * ```
   */
  private var fCurrentPosition: Int = TODO("Initialize fCurrentPosition")

  /**
   * C++ original:
   * ```cpp
   * SkPoint fOffset
   * ```
   */
  private var fOffset: Int = TODO("Initialize fOffset")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTextBlob> SkTextBlobBuilderRunHandler::makeBlob() {
   *     return fBuilder.make();
   * }
   * ```
   */
  public fun makeBlob(): Int {
    TODO("Implement makeBlob")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint endPoint() { return fOffset; }
   * ```
   */
  public fun endPoint(): Int {
    TODO("Implement endPoint")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTextBlobBuilderRunHandler::beginLine() {
   *     fCurrentPosition = fOffset;
   *     fMaxRunAscent = 0;
   *     fMaxRunDescent = 0;
   *     fMaxRunLeading = 0;
   * }
   * ```
   */
  public override fun beginLine() {
    TODO("Implement beginLine")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTextBlobBuilderRunHandler::runInfo(const RunInfo& info) {
   *     SkFontMetrics metrics;
   *     info.fFont.getMetrics(&metrics);
   *     fMaxRunAscent = std::min(fMaxRunAscent, metrics.fAscent);
   *     fMaxRunDescent = std::max(fMaxRunDescent, metrics.fDescent);
   *     fMaxRunLeading = std::max(fMaxRunLeading, metrics.fLeading);
   * }
   * ```
   */
  public override fun runInfo(info: RunInfo) {
    TODO("Implement runInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTextBlobBuilderRunHandler::commitRunInfo() {
   *     fCurrentPosition.fY -= fMaxRunAscent;
   * }
   * ```
   */
  public override fun commitRunInfo() {
    TODO("Implement commitRunInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * SkShaper::RunHandler::Buffer SkTextBlobBuilderRunHandler::runBuffer(const RunInfo& info) {
   *     int glyphCount = SkTFitsIn<int>(info.glyphCount) ? info.glyphCount : INT_MAX;
   *     int utf8RangeSize = SkTFitsIn<int>(info.utf8Range.size()) ? info.utf8Range.size() : INT_MAX;
   *
   *     const auto& runBuffer = fBuilder.allocRunTextPos(info.fFont, glyphCount, utf8RangeSize);
   *     if (runBuffer.utf8text && fUtf8Text) {
   *         memcpy(runBuffer.utf8text, fUtf8Text + info.utf8Range.begin(), utf8RangeSize);
   *     }
   *     fClusters = runBuffer.clusters;
   *     fGlyphCount = glyphCount;
   *     fClusterOffset = info.utf8Range.begin();
   *
   *     return { runBuffer.glyphs,
   *              runBuffer.points(),
   *              nullptr,
   *              runBuffer.clusters,
   *              fCurrentPosition };
   * }
   * ```
   */
  public override fun runBuffer(info: RunInfo): Buffer {
    TODO("Implement runBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTextBlobBuilderRunHandler::commitRunBuffer(const RunInfo& info) {
   *     SkASSERT(0 <= fClusterOffset);
   *     for (int i = 0; i < fGlyphCount; ++i) {
   *         SkASSERT(fClusters[i] >= (unsigned)fClusterOffset);
   *         fClusters[i] -= fClusterOffset;
   *     }
   *     fCurrentPosition += info.fAdvance;
   * }
   * ```
   */
  public override fun commitRunBuffer(info: RunInfo) {
    TODO("Implement commitRunBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkTextBlobBuilderRunHandler::commitLine() {
   *     fOffset += { 0, fMaxRunDescent + fMaxRunLeading - fMaxRunAscent };
   * }
   * ```
   */
  public override fun commitLine() {
    TODO("Implement commitLine")
  }
}
