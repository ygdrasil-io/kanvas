package org.skia.foundation

import kotlin.Int
import kotlin.ULong
import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * class SkConvertToUTF32 {
 * public:
 *     SkConvertToUTF32() {}
 *
 *     const SkUnichar* convert(const void* text, size_t byteLength, SkTextEncoding encoding) {
 *         const SkUnichar* uni;
 *         switch (encoding) {
 *             case SkTextEncoding::kUTF8: {
 *                 uni = fStorage.reset(byteLength);
 *                 const char* ptr = (const char*)text;
 *                 const char* end = ptr + byteLength;
 *                 for (int i = 0; ptr < end; ++i) {
 *                     fStorage[i] = SkUTF::NextUTF8(&ptr, end);
 *                 }
 *             } break;
 *             case SkTextEncoding::kUTF16: {
 *                 uni = fStorage.reset(byteLength);
 *                 const uint16_t* ptr = (const uint16_t*)text;
 *                 const uint16_t* end = ptr + (byteLength >> 1);
 *                 for (int i = 0; ptr < end; ++i) {
 *                     fStorage[i] = SkUTF::NextUTF16(&ptr, end);
 *                 }
 *             } break;
 *             case SkTextEncoding::kUTF32:
 *                 uni = (const SkUnichar*)text;
 *                 break;
 *             default:
 *                 SK_ABORT("unexpected enum");
 *         }
 *         return uni;
 *     }
 *
 * private:
 *     AutoSTMalloc<256, SkUnichar> fStorage;
 * }
 * ```
 */
public data class SkConvertToUTF32 public constructor(
  /**
   * C++ original:
   * ```cpp
   * AutoSTMalloc<256, SkUnichar> fStorage
   * ```
   */
  private var fStorage: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * const SkUnichar* convert(const void* text, size_t byteLength, SkTextEncoding encoding) {
   *         const SkUnichar* uni;
   *         switch (encoding) {
   *             case SkTextEncoding::kUTF8: {
   *                 uni = fStorage.reset(byteLength);
   *                 const char* ptr = (const char*)text;
   *                 const char* end = ptr + byteLength;
   *                 for (int i = 0; ptr < end; ++i) {
   *                     fStorage[i] = SkUTF::NextUTF8(&ptr, end);
   *                 }
   *             } break;
   *             case SkTextEncoding::kUTF16: {
   *                 uni = fStorage.reset(byteLength);
   *                 const uint16_t* ptr = (const uint16_t*)text;
   *                 const uint16_t* end = ptr + (byteLength >> 1);
   *                 for (int i = 0; ptr < end; ++i) {
   *                     fStorage[i] = SkUTF::NextUTF16(&ptr, end);
   *                 }
   *             } break;
   *             case SkTextEncoding::kUTF32:
   *                 uni = (const SkUnichar*)text;
   *                 break;
   *             default:
   *                 SK_ABORT("unexpected enum");
   *         }
   *         return uni;
   *     }
   * ```
   */
  public fun convert(
    text: Unit?,
    byteLength: ULong,
    encoding: SkTextEncoding,
  ): SkUnichar {
    TODO("Implement convert")
  }
}
