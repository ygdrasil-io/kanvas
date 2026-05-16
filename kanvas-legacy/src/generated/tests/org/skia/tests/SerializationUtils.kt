package org.skia.tests

import kotlin.Int
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkWriteBuffer

/**
 * C++ original:
 * ```cpp
 * struct SerializationUtils {
 *     // Generic case for flattenables
 *     static void Write(SkWriteBuffer& writer, const T* flattenable) {
 *         writer.writeFlattenable(flattenable);
 *     }
 *     static void Read(SkReadBuffer& reader, T** flattenable) {
 *         *flattenable = (T*)reader.readFlattenable(T::GetFlattenableType());
 *     }
 * }
 * ```
 */
public open class SerializationUtils<T> {
  /**
   * C++ original:
   * ```cpp
   * template<typename T> struct SerializationUtils {
   *     // Generic case for flattenables
   *     static void Write(SkWriteBuffer& writer, const T* flattenable) {
   *         writer.writeFlattenable(flattenable);
   *     }
   * ```
   */
  private fun <T> write(param0: SkWriteBuffer, param1: T) {
    TODO("Implement write")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static void Read(SkReadBuffer& reader, T** flattenable) {
     *         *flattenable = (T*)reader.readFlattenable(T::GetFlattenableType());
     *     }
     * ```
     */
    private fun read(reader: SkReadBuffer, flattenable: Int?) {
      TODO("Implement read")
    }
  }
}
