package org.skia.core

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkWriteBuffer

/**
 * C++ original:
 * ```cpp
 * class SkDescriptor : SkNoncopyable {
 * public:
 *     static size_t ComputeOverhead(int entryCount) {
 *         SkASSERT(entryCount >= 0);
 *         return sizeof(SkDescriptor) + entryCount * sizeof(Entry);
 *     }
 *
 *     static std::unique_ptr<SkDescriptor> Alloc(size_t length);
 *
 *     //
 *     // Ensure the unsized delete is called.
 *     void operator delete(void* p);
 *     void* operator new(size_t);
 *     void* operator new(size_t, void* p) { return p; }
 *
 *     void flatten(SkWriteBuffer& buffer) const;
 *
 *     uint32_t getLength() const { return fLength; }
 *     void* addEntry(uint32_t tag, size_t length, const void* data = nullptr);
 *     void computeChecksum();
 *
 *     // Assumes that getLength <= capacity of this SkDescriptor.
 *     bool isValid() const;
 *
 * #ifdef SK_DEBUG
 *     void assertChecksum() const {
 *         SkASSERT(SkDescriptor::ComputeChecksum(this) == fChecksum);
 *     }
 * #endif
 *
 *     const void* findEntry(uint32_t tag, uint32_t* length) const;
 *
 *     std::unique_ptr<SkDescriptor> copy() const;
 *
 *     // This assumes that all memory added has a length that is a multiple of 4. This is checked
 *     // by the assert in addEntry.
 *     bool operator==(const SkDescriptor& other) const;
 *     bool operator!=(const SkDescriptor& other) const { return !(*this == other); }
 *
 *     uint32_t getChecksum() const { return fChecksum; }
 *
 *     struct Entry {
 *         uint32_t fTag;
 *         uint32_t fLen;
 *     };
 *
 *     uint32_t getCount() const { return fCount; }
 *
 *     SkString dumpRec() const;
 *
 * private:
 *     SkDescriptor() = default;
 *     friend class SkDescriptorTestHelper;
 *     friend class SkAutoDescriptor;
 *
 *     static uint32_t ComputeChecksum(const SkDescriptor* desc);
 *
 *     uint32_t fChecksum{0};  // must be first
 *     uint32_t fLength{sizeof(SkDescriptor)};    // must be second
 *     uint32_t fCount{0};
 * }
 * ```
 */
public open class SkDescriptor public constructor() : SkNoncopyable() {
  /**
   * C++ original:
   * ```cpp
   * uint32_t fChecksum{0}
   * ```
   */
  private var fChecksum: UInt = TODO("Initialize fChecksum")

  /**
   * C++ original:
   * ```cpp
   * uint32_t fLength{sizeof(SkDescriptor)}
   * ```
   */
  private var fLength: UInt = TODO("Initialize fLength")

  /**
   * C++ original:
   * ```cpp
   * uint32_t fCount{0}
   * ```
   */
  private var fCount: UInt = TODO("Initialize fCount")

  /**
   * C++ original:
   * ```cpp
   * void SkDescriptor::operator delete(void* p) { ::operator delete(p); }
   * ```
   */
  public fun toDelete(p: Unit?) {
    TODO("Implement toDelete")
  }

  /**
   * C++ original:
   * ```cpp
   * void* SkDescriptor::operator new(size_t) {
   *     SK_ABORT("Descriptors are created with placement new.");
   * }
   * ```
   */
  public fun toNew(param0: ULong) {
    TODO("Implement toNew")
  }

  /**
   * C++ original:
   * ```cpp
   * void* operator new(size_t, void* p) { return p; }
   * ```
   */
  public fun toNew(param0: ULong, p: Unit?) {
    TODO("Implement toNew")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDescriptor::flatten(SkWriteBuffer& buffer) const {
   *     buffer.writePad32(static_cast<const void*>(this), this->fLength);
   * }
   * ```
   */
  public fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t getLength() const { return fLength; }
   * ```
   */
  public fun getLength(): UInt {
    TODO("Implement getLength")
  }

  /**
   * C++ original:
   * ```cpp
   * void* SkDescriptor::addEntry(uint32_t tag, size_t length, const void* data) {
   *     SkASSERT(tag);
   *     SkASSERT(SkAlign4(length) == length);
   *     SkASSERT(this->findEntry(tag, nullptr) == nullptr);
   *
   *     Entry* entry = (Entry*)((char*)this + fLength);
   *     entry->fTag = tag;
   *     entry->fLen = SkToU32(length);
   *     if (data) {
   *         memcpy(entry + 1, data, length);
   *     }
   *
   *     fCount += 1;
   *     fLength = SkToU32(fLength + sizeof(Entry) + length);
   *     return (entry + 1);  // return its data
   * }
   * ```
   */
  public fun addEntry(
    tag: UInt,
    length: ULong,
    `data`: Unit? = null,
  ) {
    TODO("Implement addEntry")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkDescriptor::computeChecksum() {
   *     fChecksum = SkDescriptor::ComputeChecksum(this);
   * }
   * ```
   */
  public fun computeChecksum() {
    TODO("Implement computeChecksum")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkDescriptor::isValid() const {
   *     uint32_t count = fCount;
   *     size_t lengthRemaining = this->fLength;
   *     if (lengthRemaining < sizeof(SkDescriptor)) {
   *         return false;
   *     }
   *     lengthRemaining -= sizeof(SkDescriptor);
   *     size_t offset = sizeof(SkDescriptor);
   *
   *     while (lengthRemaining > 0 && count > 0) {
   *         if (lengthRemaining < sizeof(Entry)) {
   *             return false;
   *         }
   *         lengthRemaining -= sizeof(Entry);
   *
   *         const Entry* entry = (const Entry*)(reinterpret_cast<const char*>(this) + offset);
   *
   *         if (lengthRemaining < entry->fLen) {
   *             return false;
   *         }
   *         lengthRemaining -= entry->fLen;
   *
   *         // rec tags are always a known size.
   *         if (entry->fTag == kRec_SkDescriptorTag && entry->fLen != sizeof(SkScalerContextRec)) {
   *             return false;
   *         }
   *
   *         offset += sizeof(Entry) + entry->fLen;
   *         count--;
   *     }
   *     return lengthRemaining == 0 && count == 0;
   * }
   * ```
   */
  public fun isValid(): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * void assertChecksum() const {
   *         SkASSERT(SkDescriptor::ComputeChecksum(this) == fChecksum);
   *     }
   * ```
   */
  public fun assertChecksum() {
    TODO("Implement assertChecksum")
  }

  /**
   * C++ original:
   * ```cpp
   * const void* SkDescriptor::findEntry(uint32_t tag, uint32_t* length) const {
   *     const Entry* entry = (const Entry*)(this + 1);
   *     int count = fCount;
   *
   *     while (--count >= 0) {
   *         if (entry->fTag == tag) {
   *             if (length) {
   *                 *length = entry->fLen;
   *             }
   *             return entry + 1;
   *         }
   *         entry = (const Entry*)((const char*)(entry + 1) + entry->fLen);
   *     }
   *     return nullptr;
   * }
   * ```
   */
  public fun findEntry(tag: UInt, length: UInt?) {
    TODO("Implement findEntry")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<SkDescriptor> SkDescriptor::copy() const {
   *     std::unique_ptr<SkDescriptor> desc = SkDescriptor::Alloc(fLength);
   *     memcpy(desc.get(), this, fLength);
   *     return desc;
   * }
   * ```
   */
  public fun copy(): Int {
    TODO("Implement copy")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkDescriptor::operator==(const SkDescriptor& other) const {
   *     // the first value we should look at is the checksum, so this loop
   *     // should terminate early if they descriptors are different.
   *     // NOTE: if we wrote a sentinel value at the end of each, we could
   *     //       remove the aa < stop test in the loop...
   *     const uint32_t* aa = (const uint32_t*)this;
   *     const uint32_t* bb = (const uint32_t*)&other;
   *     const uint32_t* stop = (const uint32_t*)((const char*)aa + fLength);
   *     do {
   *         if (*aa++ != *bb++)
   *             return false;
   *     } while (aa < stop);
   *     return true;
   * }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SkDescriptor& other) const { return !(*this == other); }
   * ```
   */
  public fun getChecksum(): UInt {
    TODO("Implement getChecksum")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t getChecksum() const { return fChecksum; }
   * ```
   */
  public fun getCount(): UInt {
    TODO("Implement getCount")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t getCount() const { return fCount; }
   * ```
   */
  public fun dumpRec(): String {
    TODO("Implement dumpRec")
  }

  public open class Entry public constructor(
    public var fTag: UInt,
    public var fLen: UInt,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static size_t ComputeOverhead(int entryCount) {
     *         SkASSERT(entryCount >= 0);
     *         return sizeof(SkDescriptor) + entryCount * sizeof(Entry);
     *     }
     * ```
     */
    public fun computeOverhead(entryCount: Int): ULong {
      TODO("Implement computeOverhead")
    }

    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<SkDescriptor> SkDescriptor::Alloc(size_t length) {
     *     SkASSERT(length >= sizeof(SkDescriptor) && SkAlign4(length) == length);
     *     void* allocation = ::operator new(length);
     *     return std::unique_ptr<SkDescriptor>(new (allocation) SkDescriptor{});
     * }
     * ```
     */
    public fun alloc(length: ULong): Int {
      TODO("Implement alloc")
    }

    /**
     * C++ original:
     * ```cpp
     * uint32_t SkDescriptor::ComputeChecksum(const SkDescriptor* desc) {
     *     const uint32_t* ptr = (const uint32_t*)desc + 1;  // skip the checksum field
     *     size_t len = desc->fLength - sizeof(uint32_t);
     *     return SkChecksum::Hash32(ptr, len);
     * }
     * ```
     */
    private fun computeChecksum(desc: SkDescriptor?): UInt {
      TODO("Implement computeChecksum")
    }
  }
}
