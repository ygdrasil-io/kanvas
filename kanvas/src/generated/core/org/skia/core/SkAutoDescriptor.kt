package org.skia.core

import kotlin.CharArray
import kotlin.Int
import kotlin.ULong
import org.skia.foundation.SkReadBuffer

/**
 * C++ original:
 * ```cpp
 * class [[nodiscard]] SkAutoDescriptor {
 * public:
 *     SkAutoDescriptor();
 *     explicit SkAutoDescriptor(size_t size);
 *     explicit SkAutoDescriptor(const SkDescriptor&);
 *     SkAutoDescriptor(const SkAutoDescriptor&);
 *     SkAutoDescriptor& operator=(const SkAutoDescriptor&);
 *     SkAutoDescriptor(SkAutoDescriptor&&);
 *     SkAutoDescriptor& operator=(SkAutoDescriptor&&);
 *     ~SkAutoDescriptor();
 *
 *     // Returns no value if there is an error.
 *     static std::optional<SkAutoDescriptor> MakeFromBuffer(SkReadBuffer& buffer);
 *
 *     void reset(size_t size);
 *     void reset(const SkDescriptor& desc);
 *     SkDescriptor* getDesc() const { SkASSERT(fDesc); return fDesc; }
 *
 * private:
 *     void free();
 *     static constexpr size_t kStorageSize
 *             = sizeof(SkDescriptor)
 *               + sizeof(SkDescriptor::Entry) + sizeof(SkScalerContextRec) // for rec
 *               + sizeof(SkDescriptor::Entry) + sizeof(void*)              // for typeface
 *               + 32;   // slop for occasional small extras
 *
 *     SkDescriptor*   fDesc{nullptr};
 *     alignas(uint32_t) char fStorage[kStorageSize];
 * }
 * ```
 */
public data class SkAutoDescriptor public constructor(
  /**
   * C++ original:
   * ```cpp
   * static constexpr size_t kStorageSize
   *             = sizeof(SkDescriptor)
   *               + sizeof(SkDescriptor::Entry) + sizeof(SkScalerContextRec) // for rec
   *               + sizeof(SkDescriptor::Entry) + sizeof(void*)              // for typeface
   *               + 32
   * ```
   */
  private var fDesc: SkDescriptor?,
  /**
   * C++ original:
   * ```cpp
   * SkDescriptor*   fDesc{nullptr}
   * ```
   */
  private var fStorage: CharArray,
) {
  /**
   * C++ original:
   * ```cpp
   * SkAutoDescriptor& SkAutoDescriptor::operator=(const SkAutoDescriptor& that) {
   *     this->reset(*that.getDesc());
   *     return *this;
   * }
   * ```
   */
  public fun assign(that: SkAutoDescriptor) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * SkAutoDescriptor& SkAutoDescriptor::operator=(SkAutoDescriptor&& that) {
   *     if (that.fDesc == (SkDescriptor*)&that.fStorage) {
   *         this->reset(*that.getDesc());
   *     } else {
   *         this->free();
   *         fDesc = that.fDesc;
   *         that.fDesc = nullptr;
   *     }
   *     return *this;
   * }
   * ```
   */
  public fun reset(size: ULong) {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkAutoDescriptor::reset(size_t size) {
   *     this->free();
   *     if (size <= sizeof(fStorage)) {
   *         fDesc = new (&fStorage) SkDescriptor{};
   *     } else {
   *         fDesc = SkDescriptor::Alloc(size).release();
   *     }
   * }
   * ```
   */
  public fun reset(desc: SkDescriptor) {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkAutoDescriptor::reset(const SkDescriptor& desc) {
   *     size_t size = desc.getLength();
   *     this->reset(size);
   *     memcpy(fDesc, &desc, size);
   * }
   * ```
   */
  public fun getDesc(): SkDescriptor {
    TODO("Implement getDesc")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDescriptor* getDesc() const { SkASSERT(fDesc); return fDesc; }
   * ```
   */
  private fun free() {
    TODO("Implement free")
  }

  public companion object {
    private val kStorageSize: ULong = TODO("Initialize kStorageSize")

    /**
     * C++ original:
     * ```cpp
     * std::optional<SkAutoDescriptor> SkAutoDescriptor::MakeFromBuffer(SkReadBuffer& buffer) {
     *     SkDescriptor descriptorHeader;
     *     if (!buffer.readPad32(&descriptorHeader, sizeof(SkDescriptor))) { return {}; }
     *
     *     // Basic bounds check on header length to make sure that bodyLength calculation does not
     *     // underflow.
     *     if (descriptorHeader.getLength() < sizeof(SkDescriptor)) { return {}; }
     *     uint32_t bodyLength = descriptorHeader.getLength() - sizeof(SkDescriptor);
     *
     *     // Make sure the fLength makes sense with respect to the incoming data.
     *     if (bodyLength > buffer.available()) {
     *         return {};
     *     }
     *
     *     SkAutoDescriptor ad{descriptorHeader.getLength()};
     *     memcpy(ad.fDesc, &descriptorHeader, sizeof(SkDescriptor));
     *     if (!buffer.readPad32(SkTAddOffset<void>(ad.fDesc, sizeof(SkDescriptor)), bodyLength)) {
     *         return {};
     *     }
     *
     * // If the fuzzer produces data but the checksum does not match, let it continue. This will boost
     * // fuzzing speed. We leave the actual checksum computation in for fuzzing builds to make sure
     * // the ComputeChecksum function is covered.
     * #if defined(SK_BUILD_FOR_FUZZER)
     *     SkDescriptor::ComputeChecksum(ad.getDesc());
     * #else
     *     if (SkDescriptor::ComputeChecksum(ad.getDesc()) != ad.getDesc()->fChecksum) { return {}; }
     * #endif
     *     if (!ad.getDesc()->isValid()) { return {}; }
     *
     *     return {ad};
     * }
     * ```
     */
    public fun makeFromBuffer(buffer: SkReadBuffer): Int {
      TODO("Implement makeFromBuffer")
    }
  }
}
