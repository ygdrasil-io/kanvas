package org.skia.gpu

import kotlin.Int
import kotlin.UShort
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * struct IndexWriter : private BufferWriter {
 *     BUFFER_WRITER_OVERLOADS(IndexWriter)
 *
 *     IndexWriter makeOffset(int numIndices) const {
 *         return this->BufferWriter::makeOffset<IndexWriter>(numIndices * sizeof(uint16_t));
 *     }
 *
 *     void writeArray(SkSpan<const uint16_t> indices) {
 *         this->write(indices);
 *     }
 *
 *     friend IndexWriter& operator<<(IndexWriter& w, uint16_t val);
 * }
 * ```
 */
public open class IndexWriter : BufferWriter() {
  /**
   * C++ original:
   * ```cpp
   * IndexWriter makeOffset(int numIndices) const {
   *         return this->BufferWriter::makeOffset<IndexWriter>(numIndices * sizeof(uint16_t));
   *     }
   * ```
   */
  public override fun makeOffset(numIndices: Int): IndexWriter {
    TODO("Implement makeOffset")
  }

  /**
   * C++ original:
   * ```cpp
   * void writeArray(SkSpan<const uint16_t> indices) {
   *         this->write(indices);
   *     }
   * ```
   */
  public fun writeArray(indices: SkSpan<UShort>) {
    TODO("Implement writeArray")
  }
}
