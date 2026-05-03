package org.skia.core

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SfntHeader {
 *     SfntHeader() : fCount(0), fDir(nullptr) {}
 *     ~SfntHeader() { sk_free(fDir); }
 *
 *     /** If it returns true, then fCount and fDir are properly initialized.
 *         Note: fDir will point to the raw array of SkSFNTDirEntry values,
 *         meaning they will still be in the file's native endianness (BE).
 *
 *         fDir will be automatically freed when this object is destroyed
 *      */
 *     bool init(SkStream* stream, int ttcIndex) {
 *         stream->rewind();
 *
 *         size_t offsetToDir;
 *         fCount = count_tables(stream, ttcIndex, &offsetToDir);
 *         if (0 == fCount) {
 *             return false;
 *         }
 *
 *         stream->rewind();
 *         if (!skip(stream, offsetToDir)) {
 *             return false;
 *         }
 *
 *         size_t size = fCount * sizeof(SkSFNTDirEntry);
 *         fDir = reinterpret_cast<SkSFNTDirEntry*>(sk_malloc_throw(size));
 *         return read(stream, fDir, size);
 *     }
 *
 *     int             fCount;
 *     SkSFNTDirEntry* fDir;
 * }
 * ```
 */
public data class SfntHeader public constructor(
  /**
   * C++ original:
   * ```cpp
   * int             fCount
   * ```
   */
  public var fCount: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSFNTDirEntry* fDir
   * ```
   */
  public var fDir: SkSFNTDirEntry?,
) {
  /**
   * C++ original:
   * ```cpp
   * bool init(SkStream* stream, int ttcIndex) {
   *         stream->rewind();
   *
   *         size_t offsetToDir;
   *         fCount = count_tables(stream, ttcIndex, &offsetToDir);
   *         if (0 == fCount) {
   *             return false;
   *         }
   *
   *         stream->rewind();
   *         if (!skip(stream, offsetToDir)) {
   *             return false;
   *         }
   *
   *         size_t size = fCount * sizeof(SkSFNTDirEntry);
   *         fDir = reinterpret_cast<SkSFNTDirEntry*>(sk_malloc_throw(size));
   *         return read(stream, fDir, size);
   *     }
   * ```
   */
  public fun `init`(stream: SkStream?, ttcIndex: Int): Boolean {
    TODO("Implement init")
  }
}
