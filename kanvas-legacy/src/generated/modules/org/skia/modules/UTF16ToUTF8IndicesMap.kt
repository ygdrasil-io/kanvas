package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * class UTF16ToUTF8IndicesMap {
 * public:
 *     /** Builds a UTF-16 to UTF-8 indices map; the text is not retained
 *      * @return true if successful
 *      */
 *     bool setUTF8(const char* utf8, size_t size) {
 *         SkASSERT(utf8 != nullptr);
 *
 *         if (!SkTFitsIn<int32_t>(size)) {
 *             SkDEBUGF("UTF16ToUTF8IndicesMap: text too long");
 *             return false;
 *         }
 *
 *         auto utf16Size = SkUTF::UTF8ToUTF16(nullptr, 0, utf8, size);
 *         if (utf16Size < 0) {
 *             SkDEBUGF("UTF16ToUTF8IndicesMap: Invalid utf8 input");
 *             return false;
 *         }
 *
 *         // utf16Size+1 to also store the size
 *         fUtf16ToUtf8Indices = std::vector<size_t>(utf16Size + 1);
 *         auto utf16 = fUtf16ToUtf8Indices.begin();
 *         auto utf8Begin = utf8, utf8End = utf8 + size;
 *         while (utf8Begin < utf8End) {
 *             *utf16 = utf8Begin - utf8;
 *             utf16 += SkUTF::ToUTF16(SkUTF::NextUTF8(&utf8Begin, utf8End), nullptr);
 *         }
 *         *utf16 = size;
 *
 *         return true;
 *     }
 *
 *     size_t mapIndex(size_t index) const {
 *         SkASSERT(index < fUtf16ToUtf8Indices.size());
 *         return fUtf16ToUtf8Indices[index];
 *     }
 *
 *     std::pair<size_t, size_t> mapRange(size_t start, size_t size) const {
 *         auto utf8Start = mapIndex(start);
 *         return {utf8Start, mapIndex(start + size) - utf8Start};
 *     }
 * private:
 *     std::vector<size_t> fUtf16ToUtf8Indices;
 * }
 * ```
 */
public data class UTF16ToUTF8IndicesMap public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::vector<size_t> fUtf16ToUtf8Indices
   * ```
   */
  private var fUtf16ToUtf8Indices: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool setUTF8(const char* utf8, size_t size) {
   *         SkASSERT(utf8 != nullptr);
   *
   *         if (!SkTFitsIn<int32_t>(size)) {
   *             SkDEBUGF("UTF16ToUTF8IndicesMap: text too long");
   *             return false;
   *         }
   *
   *         auto utf16Size = SkUTF::UTF8ToUTF16(nullptr, 0, utf8, size);
   *         if (utf16Size < 0) {
   *             SkDEBUGF("UTF16ToUTF8IndicesMap: Invalid utf8 input");
   *             return false;
   *         }
   *
   *         // utf16Size+1 to also store the size
   *         fUtf16ToUtf8Indices = std::vector<size_t>(utf16Size + 1);
   *         auto utf16 = fUtf16ToUtf8Indices.begin();
   *         auto utf8Begin = utf8, utf8End = utf8 + size;
   *         while (utf8Begin < utf8End) {
   *             *utf16 = utf8Begin - utf8;
   *             utf16 += SkUTF::ToUTF16(SkUTF::NextUTF8(&utf8Begin, utf8End), nullptr);
   *         }
   *         *utf16 = size;
   *
   *         return true;
   *     }
   * ```
   */
  public fun setUTF8(utf8: String?, size: ULong): Boolean {
    TODO("Implement setUTF8")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t mapIndex(size_t index) const {
   *         SkASSERT(index < fUtf16ToUtf8Indices.size());
   *         return fUtf16ToUtf8Indices[index];
   *     }
   * ```
   */
  public fun mapIndex(index: ULong): Int {
    TODO("Implement mapIndex")
  }

  /**
   * C++ original:
   * ```cpp
   * std::pair<size_t, size_t> mapRange(size_t start, size_t size) const {
   *         auto utf8Start = mapIndex(start);
   *         return {utf8Start, mapIndex(start + size) - utf8Start};
   *     }
   * ```
   */
  public fun mapRange(start: ULong, size: ULong): Int {
    TODO("Implement mapRange")
  }
}
