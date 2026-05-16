package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * class BufferSubAllocator final {
 * public:
 *     BufferSubAllocator() = default;
 *
 *     // Disallow copy
 *     BufferSubAllocator(const BufferSubAllocator&) = delete;
 *     BufferSubAllocator& operator=(const BufferSubAllocator&) = delete;
 *
 *     // Allow move
 *     BufferSubAllocator(BufferSubAllocator&& b) { *this = std::move(b); }
 *     BufferSubAllocator& operator=(BufferSubAllocator&&);
 *
 *     ~BufferSubAllocator() { this->reset(); }
 *
 *     // Returns false if the underlying buffer has been returned to the reuse pool or moved.
 *     bool isValid() const { return SkToBool(fBuffer); }
 *     explicit operator bool() const { return this->isValid(); }
 *
 *     // Returns the underlying buffer object back to the pool and invalidates this allocator.
 *     // Depending on the GPU buffer's Shareable value, either:
 *     //  - kNo: The remaining space that hasn't been written to can be used by another allocator,
 *     //    but it will assume that use will involve a new buffer binding command.
 *     //  - kScratch: The entire buffer can be overwritten by another allocator.
 *     void reset();
 *
 *     // Causes the next call to get[Mapped]Subrange() to also align with the minimum binding
 *     // requirement for the buffer's type. It resets any remaining strided blocks following the
 *     // last suballocation or appended range.
 *     void resetForNewBinding() {
 *         fRemaining = 0;
 *         fStride = 0; // signals prepForStride() to factor in minimum binding alignment next time.
 *     }
 *
 *     // Returns the number of remaining bytes in the GPU buffer, assuming an alignment of 1.
 *     uint32_t remainingBytes() const {
 *         return fBuffer ? SkTo<uint32_t>(fBuffer->size()) - fOffset : 0;
 *     }
 *
 *     /**
 *      * Suballocate `count*stride` bytes and a pointer (wrapped in a BufferWriter) to the mapped
 *      * range and the BindBufferInfo defining that range in a GPU-backed Buffer. The returned
 *      * subrange will be aligned according to the following rules:
 *      *  - The first suballocation, or the first after resetForNewBinding(), will be aligned to the
 *      *    lowest common multiple of `stride`, the binding's required alignment, and any extra base
 *      *    alignment passed in as `align`.
 *      *  - Subsequent suballocations will be aligned to just `stride` and any extra base alignment.
 *      *
 *      * It is assumed the caller will write all `count*stride` bytes to the returned address. If
 *      * `reservedCount` is greater than `count`, the suballocation will only succeed if the buffer
 *      * has room for an aligned `reservedCount*stride` bytes. The returned pointer can still only
 *      * write `count*stride` bytes, the remaining `reservedCount-count` is available for future
 *      * suballocations guaranteed to then fit within the same buffer (assuming the same or lower
 *      * alignment).
 *      *
 *      * It is acceptable to pass `count=0` to this function, which will still succeed if there is
 *      * space to align at least one more aligned `stride` block. In this case, the BufferWriter will
 *      * be null but the BindBufferInfo will have a valid Buffer and byte offset (just a zero size).
 *      * This then represents the start of the binding range for future calls to appendMappedStride().
 *      *
 *      * An invalid BufferWriter and empty BindBufferInfo are returned if the buffer does not have
 *      * enough room remaining to fulfill the suballocation in this buffer.
 *      */
 *      std::pair<BufferWriter, BindBufferInfo> getMappedSubrange(size_t count,
 *                                                                size_t stride,
 *                                                                size_t align=1) {
 *         SkASSERT(fMappedPtr || !fBuffer); // Writing should have checked validity of allocator first
 *         this->prepForStride(stride, align, count);
 *         return this->reserve(count, &BufferSubAllocator::getWriterAndBinding);
 *     }
 *
 *     // Sub-allocate a slice within the scratch buffer object. This variation should be used when the
 *     // returned range will be written to by the GPU as part of executing a command buffer.
 *     //
 *     // Other than returning just a buffer slice to be written to later by a GPU task, the
 *     // suballocation behaves identically to getMappedSubrange().
 *     BindBufferInfo getSubrange(size_t count, size_t stride, size_t align=1) {
 *         SkASSERT(!fMappedPtr); // Should not be used when data is intended to be written by CPU
 *         this->prepForStride(stride, align, count);
 *         return this->reserve(count, &BufferSubAllocator::binding);
 *     }
 *
 *     // Returns the number of `stride` blocks left in the buffer, where `stride` was the last
 *     // value passed into get[Mapped]Subrange().
 *     uint32_t availableWithStride() const { return fRemaining; }
 *
 *     /**
 *      * Returns a buffer writer for `count*stride` bytes, where `stride` was the last value passed
 *      * to getMappedSubrange.
 *      *
 *      * If `count <= this->availableWithStride()`, then the appended range will be contiguous with
 *      * the BindBufferInfo returned from the last call to getMappedSubrange (and/or other calls to
 *      * appendMappedStride). The binding's size should be increased by count*stride, which is the
 *      * caller's responsibility.
 *      *
 *      * Otherwise, a null BufferWriter will be returned and no change is made to the allocator.
 *      */
 *     BufferWriter appendMappedWithStride(size_t count) {
 *         SkASSERT(count > 0 && (fMappedPtr || fRemaining == 0));
 *         return this->reserve(count, &BufferSubAllocator::getWriter);
 *     }
 *
 * private:
 *     friend class DrawBufferManager;
 *
 *     BufferSubAllocator(DrawBufferManager* owner,
 *                        int stateIndex,
 *                        sk_sp<Buffer> buffer,
 *                        BindBufferInfo transferBuffer, // optional (when direct mapping unavailable)
 *                        void* mappedPtr, // `buffer` or `transferBuffer`'s ptr, or null if GPU-only
 *                        size_t stride);  // initial stride to compute `fRemaining`
 *
 *     // If minCount*stride bytes fit when aligned to the LCM of stride, align, and possibly the
 *     // binding alignment (when fStride == 0), then fOffset is updated and fRemaining is set to the
 *     // number of stride units that fit in the rest of the buffer after fOffset. If not, fRemaining
 *     // is set to 0 and fOffset is unmodified.
 *     void prepForStride(size_t stride, size_t align, size_t minCount);
 *
 *     template <typename T>
 *     T reserve(size_t count, T (BufferSubAllocator::*create)(uint32_t offset, uint32_t size) const) {
 *         if (!fBuffer || count > fRemaining) {
 *             return T();
 *         }
 *
 *         SkASSERT(fStride > 0); // fRemaining should be set to 0 if fStride is 0
 *         // fRemaining and fStride were already validated in prepForStride() so we can cast `count`
 *         const uint32_t requiredBytes32 = SkTo<uint32_t>(count) * fStride;
 *
 *         uint32_t offset = fOffset;
 *         fOffset += requiredBytes32;
 *         fRemaining -= count;
 *         return (this->*create)(offset, requiredBytes32);
 *     }
 *
 *     BindBufferInfo binding(uint32_t offset, uint32_t size) const {
 *         SkASSERT(fBuffer);
 *         SkASSERT(fOffset >= size && fOffset - size >= offset);
 *         return BindBufferInfo{fBuffer.get(), offset, size};
 *     }
 *
 *     BufferWriter getWriter(uint32_t offset, uint32_t size) const {
 *         SkASSERT(fMappedPtr);
 *         SkASSERT(fBuffer);
 *         SkASSERT(fOffset >= size && fOffset - size >= offset);
 *         return BufferWriter(SkTAddOffset<void>(fMappedPtr, offset), size);
 *     }
 *
 *     std::pair<BufferWriter, BindBufferInfo> getWriterAndBinding(
 *                 uint32_t offset, uint32_t size) const {
 *         return {this->getWriter(offset, size), this->binding(offset, size)};
 *     }
 *
 *     // Non-null when valid and not already returned to the pool
 *     DrawBufferManager* fOwner = nullptr;
 *     int fStateIndex = 0;
 *
 *     sk_sp<Buffer> fBuffer;
 *     BindBufferInfo fTransferBuffer;
 *
 *      // If mapped for writing, this is the CPU address of offset 0 of the buffer. When a mapped
 *      // buffer is returned to the DrawBufferManager, only the bytes after fOffset can be reused.
 *      // If there is no mapped buffer pointer, it's assumed the GPU buffer is reusable for another
 *      // BufferSubAllocator instance (this default reuse policy can be revisited if needed).
 *     void* fMappedPtr = nullptr;
 *     uint32_t fOffset = 0;    // Next suballocation can start at fOffset at the earliest
 *     uint32_t fStride = 1;    // The byte count of blocks for the optimized append() function
 *     uint32_t fRemaining = 0; // The number of `fStride` contiguous blocks fitting after fOffset
 * }
 * ```
 */
public data class BufferSubAllocator public constructor(
  /**
   * C++ original:
   * ```cpp
   * DrawBufferManager* fOwner = nullptr
   * ```
   */
  private var fOwner: DrawBufferManager?,
  /**
   * C++ original:
   * ```cpp
   * int fStateIndex = 0
   * ```
   */
  private var fStateIndex: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<Buffer> fBuffer
   * ```
   */
  private var fBuffer: Int,
  /**
   * C++ original:
   * ```cpp
   * BindBufferInfo fTransferBuffer
   * ```
   */
  private var fTransferBuffer: Int,
  /**
   * C++ original:
   * ```cpp
   * void* fMappedPtr = nullptr
   * ```
   */
  private var fMappedPtr: Unit?,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fOffset
   * ```
   */
  private var fOffset: Int,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fStride
   * ```
   */
  private var fStride: Int,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fRemaining
   * ```
   */
  private var fRemaining: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * BufferSubAllocator& operator=(const BufferSubAllocator&) = delete
   * ```
   */
  public fun assign(param0: BufferSubAllocator) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * BufferSubAllocator& BufferSubAllocator::operator=(BufferSubAllocator&& other) {
   *     if (this == &other) {
   *         return *this; // no-op moving into itself
   *     }
   *
   *     // Reset the destination allocator first since other's contents will overwrite whatever came
   *     // beforehand and that must go back to the manager.
   *     this->reset();
   *
   *     // Copy fields
   *     fOwner = other.fOwner;
   *     fStateIndex = other.fStateIndex;
   *     fTransferBuffer = other.fTransferBuffer;
   *     fMappedPtr = other.fMappedPtr;
   *     fOffset = other.fOffset;
   *     fStride = other.fStride;
   *     fRemaining = other.fRemaining;
   *
   *     // Move buffer (leaving other in an invalid state)
   *     fBuffer = std::move(other.fBuffer);
   *     SkASSERT(!other);
   *     return *this;
   * }
   * ```
   */
  public fun isValid(): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isValid() const { return SkToBool(fBuffer); }
   * ```
   */
  public fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void BufferSubAllocator::reset() {
   *     if (fBuffer) {
   *         SkASSERT(fOwner);
   *
   *         DrawBufferManager::BufferState& state = fOwner->fCurrentBuffers[fStateIndex];
   *         if (fBuffer->shareable() == Shareable::kScratch) {
   *             // TODO: Merge this reuse of scratch resources with the ScratchResourceManager, but
   *             // currently this is resolved outside of Task::prepareResources().
   *
   *             // The scratch buffer's availability for reuse (scoped to the owning DrawBufferManager)
   *             // was tied to this BufferSubAllocator, so when that is reset, we just remove the buffer
   *             // from the set of unavailable buffers.
   *             SkASSERT((fOwner->fMappingFailed && state.fUnavailableScratchBuffers.empty()) ||
   *                      state.fUnavailableScratchBuffers.contains(fBuffer.get()));
   *             if (!fOwner->fMappingFailed) {
   *                 state.fUnavailableScratchBuffers.remove(fBuffer.get());
   *             }
   *
   *             SkASSERT(!fTransferBuffer); // Scratch buffers shouldn't be using transfer buffers
   *             fOwner->fUsedBuffers.emplace_back(std::move(fBuffer), BindBufferInfo{});
   *         } else if (state.fAvailableBuffer.fBuffer.get() == fBuffer.get() || // can't stash itself
   *                    this->remainingBytes() < state.fAvailableBuffer.remainingBytes() || // too small
   *                    this->remainingBytes() < state.fMinAlignment) { // basically empty
   *             // Transfer ownership of the buffer (and any transfer buffer) back to the manager, using
   *             // the current offset as a more restricted limit for copying.
   *             if (fTransferBuffer) {
   *                 // This alignment ensures we are copying a subset that still respects xfer alignment
   *                 fTransferBuffer.fSize = SkAlignTo(fOffset, state.fMinAlignment);
   *             }
   *             fOwner->fUsedBuffers.emplace_back(std::move(fBuffer), fTransferBuffer);
   *         } else {
   *             // Save this buffer for later, which leaves this instance empty and resets the prior
   *             // value of fAvailableBuffer (which then goes through the true branch of this if).
   *             state.fAvailableBuffer = std::move(*this);
   *         }
   *
   *         fRemaining = 0;
   *         SkASSERT(!fBuffer);
   *     } // else nothing to reset
   * }
   * ```
   */
  public fun resetForNewBinding() {
    TODO("Implement resetForNewBinding")
  }

  /**
   * C++ original:
   * ```cpp
   * void resetForNewBinding() {
   *         fRemaining = 0;
   *         fStride = 0; // signals prepForStride() to factor in minimum binding alignment next time.
   *     }
   * ```
   */
  public fun remainingBytes(): Int {
    TODO("Implement remainingBytes")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t remainingBytes() const {
   *         return fBuffer ? SkTo<uint32_t>(fBuffer->size()) - fOffset : 0;
   *     }
   * ```
   */
  public fun getMappedSubrange(
    count: ULong,
    stride: ULong,
    align: ULong = TODO(),
  ): Int {
    TODO("Implement getMappedSubrange")
  }

  /**
   * C++ original:
   * ```cpp
   * std::pair<BufferWriter, BindBufferInfo> getMappedSubrange(size_t count,
   *                                                                size_t stride,
   *                                                                size_t align=1) {
   *         SkASSERT(fMappedPtr || !fBuffer); // Writing should have checked validity of allocator first
   *         this->prepForStride(stride, align, count);
   *         return this->reserve(count, &BufferSubAllocator::getWriterAndBinding);
   *     }
   * ```
   */
  public fun getSubrange(
    count: ULong,
    stride: ULong,
    align: ULong = TODO(),
  ): Int {
    TODO("Implement getSubrange")
  }

  /**
   * C++ original:
   * ```cpp
   * BindBufferInfo getSubrange(size_t count, size_t stride, size_t align=1) {
   *         SkASSERT(!fMappedPtr); // Should not be used when data is intended to be written by CPU
   *         this->prepForStride(stride, align, count);
   *         return this->reserve(count, &BufferSubAllocator::binding);
   *     }
   * ```
   */
  public fun availableWithStride(): Int {
    TODO("Implement availableWithStride")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t availableWithStride() const { return fRemaining; }
   * ```
   */
  public fun appendMappedWithStride(count: ULong): Int {
    TODO("Implement appendMappedWithStride")
  }

  /**
   * C++ original:
   * ```cpp
   * BufferWriter appendMappedWithStride(size_t count) {
   *         SkASSERT(count > 0 && (fMappedPtr || fRemaining == 0));
   *         return this->reserve(count, &BufferSubAllocator::getWriter);
   *     }
   * ```
   */
  private fun prepForStride(
    stride: ULong,
    align: ULong,
    minCount: ULong,
  ) {
    TODO("Implement prepForStride")
  }

  /**
   * C++ original:
   * ```cpp
   * void BufferSubAllocator::prepForStride(size_t stride, size_t align, size_t minCount) {
   *     SkASSERT(stride > 0 && align > 0); // Expect valid inputs
   *     if (fBuffer) {
   *         if (fStride == stride && (align == 1 || align == stride)) {
   *             // Shortcut if we're already aligned with the last call to prepForStride().
   *             // Leave fRemaining alone, it's either enough for minCount or not, but reserve() will
   *             // do the right thing regardless.
   *             SkASSERT(fOffset % align == 0);
   *             SkASSERT(fOffset % stride == 0);
   *             return;
   *         }
   *
   *         // On re-aligning to a new stride, the offset needs to be aligned to the LCM of `align` and
   *         // `stride` so that repeated suballocations of `stride` can be performed by simply adding to
   *         // fOffset without additional instructions. If `fStride == 0`, it's a signal that the first
   *         // offset also needs to be aligned to the minimum binding requirement.
   *         uint32_t align32 = lcm_alignment(SkTo<uint32_t>(align), SkTo<uint32_t>(stride));
   *         if (fStride == 0) {
   *             const uint32_t minAlignment = fOwner->fCurrentBuffers[fStateIndex].fMinAlignment;
   *             align32 = lcm_alignment(minAlignment, align32);
   *         }
   *
   *         // Ensures we won't overflow fOffset past buffer size once we align it
   *         if (this->remainingBytes() >= align32 - 1) {
   *             const uint32_t offset = SkAlignNonPow2(fOffset, align32);
   *             SkASSERT(offset <= fBuffer->size());
   *             fStride = SkTo<uint32_t>(stride);
   *             fRemaining = (SkTo<uint32_t>(fBuffer->size()) - offset) / fStride;
   *             if (fRemaining > 0 && fRemaining >= minCount) {
   *                 // Successful prep, so preserve the aligned offset
   *                 fOffset = offset;
   *                 return;
   *             }
   *         }
   *     }
   *
   *     // If we've reached here, there wasn't a buffer or enough room to align, or enough room to
   *     // satisfy minCount, so set fRemaining=0 to fail subsequent reservations.
   *     fRemaining = 0;
   * }
   * ```
   */
  private fun <T> reserve(count: ULong): T {
    TODO("Implement reserve")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     T reserve(size_t count, T (BufferSubAllocator::*create)(uint32_t offset, uint32_t size) const) {
   *         if (!fBuffer || count > fRemaining) {
   *             return T();
   *         }
   *
   *         SkASSERT(fStride > 0); // fRemaining should be set to 0 if fStride is 0
   *         // fRemaining and fStride were already validated in prepForStride() so we can cast `count`
   *         const uint32_t requiredBytes32 = SkTo<uint32_t>(count) * fStride;
   *
   *         uint32_t offset = fOffset;
   *         fOffset += requiredBytes32;
   *         fRemaining -= count;
   *         return (this->*create)(offset, requiredBytes32);
   *     }
   * ```
   */
  private fun binding(offset: UInt, size: UInt): Int {
    TODO("Implement binding")
  }

  /**
   * C++ original:
   * ```cpp
   * BindBufferInfo binding(uint32_t offset, uint32_t size) const {
   *         SkASSERT(fBuffer);
   *         SkASSERT(fOffset >= size && fOffset - size >= offset);
   *         return BindBufferInfo{fBuffer.get(), offset, size};
   *     }
   * ```
   */
  private fun getWriter(offset: UInt, size: UInt): Int {
    TODO("Implement getWriter")
  }

  /**
   * C++ original:
   * ```cpp
   * BufferWriter getWriter(uint32_t offset, uint32_t size) const {
   *         SkASSERT(fMappedPtr);
   *         SkASSERT(fBuffer);
   *         SkASSERT(fOffset >= size && fOffset - size >= offset);
   *         return BufferWriter(SkTAddOffset<void>(fMappedPtr, offset), size);
   *     }
   * ```
   */
  private fun getWriterAndBinding(offset: UInt, size: UInt): Int {
    TODO("Implement getWriterAndBinding")
  }
}
