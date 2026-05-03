package org.skia.core

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class GlyphRunList {
 *     SkSpan<const GlyphRun> fGlyphRuns;
 *
 * public:
 *     // Blob maybe null.
 *     GlyphRunList(const SkTextBlob* blob,
 *                  SkRect bounds,
 *                  SkPoint origin,
 *                  SkSpan<const GlyphRun> glyphRunList,
 *                  GlyphRunBuilder* builder);
 *
 *     GlyphRunList(const GlyphRun& glyphRun,
 *                  const SkRect& bounds,
 *                  SkPoint origin,
 *                  GlyphRunBuilder* builder);
 *     uint64_t uniqueID() const;
 *     bool anyRunsLCD() const;
 *     void temporaryShuntBlobNotifyAddedToCache(uint32_t cacheID, SkTextBlob::PurgeDelegate) const;
 *
 *     bool canCache() const { return fOriginalTextBlob != nullptr; }
 *     size_t runCount() const { return fGlyphRuns.size(); }
 *     size_t totalGlyphCount() const {
 *         size_t glyphCount = 0;
 *         for (const GlyphRun& run : *this) {
 *             glyphCount += run.runSize();
 *         }
 *         return glyphCount;
 *     }
 *     size_t maxGlyphRunSize() const {
 *         size_t size = 0;
 *         for (const GlyphRun& run : *this) {
 *             size = std::max(run.runSize(), size);
 *         }
 *         return size;
 *     }
 *
 *     bool hasRSXForm() const {
 *         for (const GlyphRun& run : *this) {
 *             if (!run.scaledRotations().empty()) { return true; }
 *         }
 *         return false;
 *     }
 *
 *     sk_sp<SkTextBlob> makeBlob() const;
 *
 *     SkPoint origin() const { return fOrigin; }
 *     SkRect sourceBounds() const { return fSourceBounds; }
 *     SkRect sourceBoundsWithOrigin() const { return fSourceBounds.makeOffset(fOrigin); }
 *     const SkTextBlob* blob() const { return fOriginalTextBlob; }
 *     GlyphRunBuilder* builder() const { return fBuilder; }
 *
 *     auto begin() -> decltype(fGlyphRuns.begin())               { return fGlyphRuns.begin();      }
 *     auto end()   -> decltype(fGlyphRuns.end())                 { return fGlyphRuns.end();        }
 *     auto begin() const -> decltype(std::cbegin(fGlyphRuns))    { return std::cbegin(fGlyphRuns); }
 *     auto end()   const -> decltype(std::cend(fGlyphRuns))      { return std::cend(fGlyphRuns);   }
 *     auto size()  const -> decltype(fGlyphRuns.size())          { return fGlyphRuns.size();       }
 *     auto empty() const -> decltype(fGlyphRuns.empty())         { return fGlyphRuns.empty();      }
 *     auto operator [] (size_t i) const -> decltype(fGlyphRuns[i]) { return fGlyphRuns[i];         }
 *
 * private:
 *     // The text blob is needed to hook up the call back that the SkTextBlob destructor calls. It
 *     // should be used for nothing else.
 *     const SkTextBlob* fOriginalTextBlob{nullptr};
 *     const SkRect fSourceBounds{SkRect::MakeEmpty()};
 *     const SkPoint fOrigin = {0, 0};
 *     GlyphRunBuilder* const fBuilder;
 * }
 * ```
 */
public data class GlyphRunList public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkSpan<const GlyphRun> fGlyphRuns
   * ```
   */
  private val fGlyphRuns: SkSpan<GlyphRun>,
  /**
   * C++ original:
   * ```cpp
   * const SkTextBlob* fOriginalTextBlob{nullptr}
   * ```
   */
  private val fOriginalTextBlob: SkTextBlob?,
  /**
   * C++ original:
   * ```cpp
   * const SkRect fSourceBounds{SkRect::MakeEmpty()}
   * ```
   */
  private val fSourceBounds: SkRect,
  /**
   * C++ original:
   * ```cpp
   * const SkPoint fOrigin = {0, 0}
   * ```
   */
  private val fOrigin: SkPoint,
  /**
   * C++ original:
   * ```cpp
   * GlyphRunBuilder* const fBuilder
   * ```
   */
  private val fBuilder: GlyphRunBuilder?,
) {
  /**
   * C++ original:
   * ```cpp
   * uint64_t GlyphRunList::uniqueID() const {
   *     return fOriginalTextBlob != nullptr ? fOriginalTextBlob->uniqueID()
   *                                         : SK_InvalidUniqueID;
   * }
   * ```
   */
  public fun uniqueID(): Int {
    TODO("Implement uniqueID")
  }

  /**
   * C++ original:
   * ```cpp
   * bool GlyphRunList::anyRunsLCD() const {
   *     for (const auto& r : fGlyphRuns) {
   *         if (r.font().getEdging() == SkFont::Edging::kSubpixelAntiAlias) {
   *             return true;
   *         }
   *     }
   *     return false;
   * }
   * ```
   */
  public fun anyRunsLCD(): Boolean {
    TODO("Implement anyRunsLCD")
  }

  /**
   * C++ original:
   * ```cpp
   * void GlyphRunList::temporaryShuntBlobNotifyAddedToCache(uint32_t cacheID,
   *                                                         SkTextBlob::PurgeDelegate pd) const {
   *     SkASSERT(fOriginalTextBlob != nullptr);
   *     SkASSERT(pd != nullptr);
   *     fOriginalTextBlob->notifyAddedToCache(cacheID, pd);
   * }
   * ```
   */
  public fun temporaryShuntBlobNotifyAddedToCache(cacheID: UInt, pd: SkTextBlobPurgeDelegate) {
    TODO("Implement temporaryShuntBlobNotifyAddedToCache")
  }

  /**
   * C++ original:
   * ```cpp
   * bool canCache() const { return fOriginalTextBlob != nullptr; }
   * ```
   */
  public fun canCache(): Boolean {
    TODO("Implement canCache")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t runCount() const { return fGlyphRuns.size(); }
   * ```
   */
  public fun runCount(): Int {
    TODO("Implement runCount")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t totalGlyphCount() const {
   *         size_t glyphCount = 0;
   *         for (const GlyphRun& run : *this) {
   *             glyphCount += run.runSize();
   *         }
   *         return glyphCount;
   *     }
   * ```
   */
  public fun totalGlyphCount(): Int {
    TODO("Implement totalGlyphCount")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t maxGlyphRunSize() const {
   *         size_t size = 0;
   *         for (const GlyphRun& run : *this) {
   *             size = std::max(run.runSize(), size);
   *         }
   *         return size;
   *     }
   * ```
   */
  public fun maxGlyphRunSize(): Int {
    TODO("Implement maxGlyphRunSize")
  }

  /**
   * C++ original:
   * ```cpp
   * bool hasRSXForm() const {
   *         for (const GlyphRun& run : *this) {
   *             if (!run.scaledRotations().empty()) { return true; }
   *         }
   *         return false;
   *     }
   * ```
   */
  public fun hasRSXForm(): Boolean {
    TODO("Implement hasRSXForm")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTextBlob> GlyphRunList::makeBlob() const {
   *     SkTextBlobBuilder builder;
   *     for (auto& run : *this) {
   *         SkTextBlobBuilder::RunBuffer buffer;
   *         if (run.scaledRotations().empty()) {
   *             if (run.text().empty()) {
   *                 buffer = builder.allocRunPos(run.font(), run.runSize(), nullptr);
   *             } else {
   *                 buffer = builder.allocRunTextPos(run.font(), run.runSize(), run.text().size(), nullptr);
   *                 auto text = run.text();
   *                 memcpy(buffer.utf8text, text.data(), text.size_bytes());
   *                 auto clusters = run.clusters();
   *                 memcpy(buffer.clusters, clusters.data(), clusters.size_bytes());
   *             }
   *             auto positions = run.positions();
   *             memcpy(buffer.points(), positions.data(), positions.size_bytes());
   *         } else {
   *             buffer = builder.allocRunRSXform(run.font(), run.runSize());
   *             for (auto [xform, pos, sr] : SkMakeZip(buffer.xforms(),
   *                                                    run.positions(),
   *                                                    run.scaledRotations())) {
   *                 xform = SkRSXform::Make(sr.x(), sr.y(), pos.x(), pos.y());
   *             }
   *         }
   *         auto glyphIDs = run.glyphsIDs();
   *         memcpy(buffer.glyphs, glyphIDs.data(), glyphIDs.size_bytes());
   *     }
   *     return builder.make();
   * }
   * ```
   */
  public fun makeBlob(): SkSp<SkTextBlob> {
    TODO("Implement makeBlob")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint origin() const { return fOrigin; }
   * ```
   */
  public fun origin(): SkPoint {
    TODO("Implement origin")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect sourceBounds() const { return fSourceBounds; }
   * ```
   */
  public fun sourceBounds(): SkRect {
    TODO("Implement sourceBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect sourceBoundsWithOrigin() const { return fSourceBounds.makeOffset(fOrigin); }
   * ```
   */
  public fun sourceBoundsWithOrigin(): SkRect {
    TODO("Implement sourceBoundsWithOrigin")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkTextBlob* blob() const { return fOriginalTextBlob; }
   * ```
   */
  public fun blob(): SkTextBlob {
    TODO("Implement blob")
  }

  /**
   * C++ original:
   * ```cpp
   * GlyphRunBuilder* builder() const { return fBuilder; }
   * ```
   */
  public fun builder(): GlyphRunBuilder {
    TODO("Implement builder")
  }

  /**
   * C++ original:
   * ```cpp
   * auto begin() -> decltype(fGlyphRuns.begin())               { return fGlyphRuns.begin();      }
   * ```
   */
  public fun begin(): Any {
    TODO("Implement begin")
  }

  /**
   * C++ original:
   * ```cpp
   * auto end()   -> decltype(fGlyphRuns.end())                 { return fGlyphRuns.end();        }
   * ```
   */
  public fun end(): Any {
    TODO("Implement end")
  }

  /**
   * C++ original:
   * ```cpp
   * auto begin() const -> decltype(std::cbegin(fGlyphRuns))    { return std::cbegin(fGlyphRuns); }
   * ```
   */
  public fun size(): Any {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * auto end()   const -> decltype(std::cend(fGlyphRuns))      { return std::cend(fGlyphRuns);   }
   * ```
   */
  public fun empty(): Any {
    TODO("Implement empty")
  }

  /**
   * C++ original:
   * ```cpp
   * auto size()  const -> decltype(fGlyphRuns.size())          { return fGlyphRuns.size();       }
   * ```
   */
  public operator fun `get`(i: ULong): Any {
    TODO("Implement get")
  }
}
