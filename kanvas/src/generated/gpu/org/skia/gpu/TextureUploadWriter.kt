package org.skia.gpu

import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkImageInfo

/**
 * C++ original:
 * ```cpp
 * struct TextureUploadWriter : private BufferWriter {
 *     BUFFER_WRITER_OVERLOADS(TextureUploadWriter)
 *
 *     // TODO(michaelludwig): This API doesn't prevent the underlying buffer from being written
 *     // multiple times, which would be nice to do.
 *
 *     // Writes a block of image data to the upload buffer, starting at `offset`. The source image is
 *     // `srcRowBytes` wide, and the written block is `dstRowBytes` wide and `rowCount` bytes tall.
 *     void write(size_t offset, const void* src, size_t srcRowBytes, size_t dstRowBytes,
 *                size_t trimRowBytes, int rowCount) {
 *         this->validate(offset + dstRowBytes * rowCount);
 *         void* dst = SkTAddOffset<void>(fPtr, offset);
 *         SkRectMemcpy(dst, dstRowBytes, src, srcRowBytes, trimRowBytes, rowCount);
 *     }
 *
 *     void convertAndWrite(size_t offset,
 *                          const SkImageInfo& srcInfo, const void* src, size_t srcRowBytes,
 *                          const SkImageInfo& dstInfo, size_t dstRowBytes) {
 *         SkASSERT(srcInfo.width() == dstInfo.width() && srcInfo.height() == dstInfo.height());
 *         this->validate(offset + dstRowBytes * dstInfo.height());
 *         void* dst = SkTAddOffset<void>(fPtr, offset);
 *         SkAssertResult(SkConvertPixels(dstInfo, dst, dstRowBytes, srcInfo, src, srcRowBytes));
 *     }
 *
 *     // Writes a block of image data to the upload buffer. It converts src data of RGB_888x
 *     // colorType into a 3 channel RGB_888 format.
 *     void writeRGBFromRGBx(size_t offset, const void* src, size_t srcRowBytes, size_t dstRowBytes,
 *                           int rowPixels, int rowCount) {
 *         this->validate(offset + dstRowBytes * rowCount);
 *         void* dst = SkTAddOffset<void>(fPtr, offset);
 *         auto* sRow = reinterpret_cast<const char*>(src);
 *         auto* dRow = reinterpret_cast<char*>(dst);
 *
 *         for (int y = 0; y < rowCount; ++y) {
 *             for (int x = 0; x < rowPixels; ++x) {
 *                 memcpy(dRow + 3*x, sRow+4*x, 3);
 *             }
 *             sRow += srcRowBytes;
 *             dRow += dstRowBytes;
 *         }
 *     }
 * }
 * ```
 */
public open class TextureUploadWriter : BufferWriter() {
  /**
   * C++ original:
   * ```cpp
   * void write(size_t offset, const void* src, size_t srcRowBytes, size_t dstRowBytes,
   *                size_t trimRowBytes, int rowCount) {
   *         this->validate(offset + dstRowBytes * rowCount);
   *         void* dst = SkTAddOffset<void>(fPtr, offset);
   *         SkRectMemcpy(dst, dstRowBytes, src, srcRowBytes, trimRowBytes, rowCount);
   *     }
   * ```
   */
  public fun write(
    offset: ULong,
    src: Unit?,
    srcRowBytes: ULong,
    dstRowBytes: ULong,
    trimRowBytes: ULong,
    rowCount: Int,
  ) {
    TODO("Implement write")
  }

  /**
   * C++ original:
   * ```cpp
   * void convertAndWrite(size_t offset,
   *                          const SkImageInfo& srcInfo, const void* src, size_t srcRowBytes,
   *                          const SkImageInfo& dstInfo, size_t dstRowBytes) {
   *         SkASSERT(srcInfo.width() == dstInfo.width() && srcInfo.height() == dstInfo.height());
   *         this->validate(offset + dstRowBytes * dstInfo.height());
   *         void* dst = SkTAddOffset<void>(fPtr, offset);
   *         SkAssertResult(SkConvertPixels(dstInfo, dst, dstRowBytes, srcInfo, src, srcRowBytes));
   *     }
   * ```
   */
  public fun convertAndWrite(
    offset: ULong,
    srcInfo: SkImageInfo,
    src: Unit?,
    srcRowBytes: ULong,
    dstInfo: SkImageInfo,
    dstRowBytes: ULong,
  ) {
    TODO("Implement convertAndWrite")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeRGBFromRGBx(size_t offset, const void* src, size_t srcRowBytes, size_t dstRowBytes,
   *                           int rowPixels, int rowCount) {
   *         this->validate(offset + dstRowBytes * rowCount);
   *         void* dst = SkTAddOffset<void>(fPtr, offset);
   *         auto* sRow = reinterpret_cast<const char*>(src);
   *         auto* dRow = reinterpret_cast<char*>(dst);
   *
   *         for (int y = 0; y < rowCount; ++y) {
   *             for (int x = 0; x < rowPixels; ++x) {
   *                 memcpy(dRow + 3*x, sRow+4*x, 3);
   *             }
   *             sRow += srcRowBytes;
   *             dRow += dstRowBytes;
   *         }
   *     }
   * ```
   */
  public fun writeRGBFromRGBx(
    offset: ULong,
    src: Unit?,
    srcRowBytes: ULong,
    dstRowBytes: ULong,
    rowPixels: Int,
    rowCount: Int,
  ) {
    TODO("Implement writeRGBFromRGBx")
  }
}
