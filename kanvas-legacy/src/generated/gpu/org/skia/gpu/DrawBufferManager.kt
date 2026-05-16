package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * class DrawBufferManager {
 * public:
 *     struct Options {
 *         Options() = default;
 *
 *         uint32_t fVertexBufferMinSize  = 16 << 10; // 16 KB;
 *         uint32_t fVertexBufferMaxSize  = 1 << 20;  // 1  MB
 *         uint32_t fIndexBufferSize      = 2 << 10;  // 2  KB
 *         uint32_t fStorageBufferMinSize = 2 << 10;  // 2  KB;
 *         uint32_t fStorageBufferMaxSize = 1 << 20;  // 1  MB;
 *
 * #if defined(GPU_TEST_UTILS)
 *         bool     fUseExactBuffSizes    = false; // Disables automatic buffer growth
 *         bool     fAllowCopyingGpuOnly  = false; // Adds kCopySrc to GPU-only buffer usage
 * #endif
 *     };
 *
 *     DrawBufferManager(ResourceProvider* resourceProvider, const Caps* caps,
 *                       UploadBufferManager* uploadManager,
 *                       Options dbmOpts);
 *     ~DrawBufferManager();
 *
 *     // Let possible users check if the manager is already in a bad mapping state and skip any extra
 *     // work that will be wasted because the next Recording snap will fail.
 *     bool hasMappingFailed() const { return fMappingFailed; }
 *
 *     // Return a BufferWriter to write to the count*dataStride bytes of the GPU buffer subrange
 *     // represented by the returned BindBufferInfo. The returned BufferSubAllocator represents the
 *     // entire GPU buffer that the mapped subrange belongs to; it can be used to get additional
 *     // mapped suballocations, which when successful are guaranteed to be in the same buffer. This
 *     // allows callers to more easily manage when buffers must be bound.
 *     //
 *     // The returned {BufferWriter, BindBufferInfo} are effectively an automatic call to
 *     // BufferSubAllocator.getMappedSubrange(count, stride, reservedCount). The offset of this first
 *     // allocation will be aligned to the LCM of `stride` and the minimum required alignment for the
 *     // buffer type. For function variants that take an extra `alignment`, the initial suballocation
 *     // will also be aligned to that, equivalent to if resetForNewBinding(alignment) had been called
 *     // before. Subsequent suballocations from the returned allocator will only be aligned to their
 *     // requested stride unless resetForNewBinding() was called.
 *     //
 *     // When the returned BufferSubAllocator goes out of scope, any remaining bytes that were never
 *     // returned from either this function or later calls to getMappedSubrange() can be used to
 *     // satisfy a future call to getMapped[X]Buffer.
 *     using MappedAllocationInfo = std::tuple<BufferWriter, BindBufferInfo, BufferSubAllocator>;
 *
 *     MappedAllocationInfo getMappedVertexBuffer(size_t count, size_t stride,
 *                                                size_t reservedCount=0, size_t alignment=1) {
 *         return this->getMappedBuffer(kVertexBufferIndex, count, stride, reservedCount, alignment);
 *     }
 *     MappedAllocationInfo getMappedIndexBuffer(size_t count) {
 *         return this->getMappedBuffer(kIndexBufferIndex, count, sizeof(uint16_t));
 *     }
 *     MappedAllocationInfo getMappedUniformBuffer(size_t count, size_t stride) {
 *         return this->getMappedBuffer(kUniformBufferIndex, count, stride);
 *     }
 *     MappedAllocationInfo getMappedStorageBuffer(size_t count, size_t stride) {
 *         return this->getMappedBuffer(kStorageBufferIndex, count, stride);
 *     }
 *
 *     // The remaining writers and buffer allocator functions assume that byte counts are safely
 *     // calculated by the caller (e.g. Vello).
 *
 *     // Utilities that return an unmapped buffer suballocation for a particular usage. These buffers
 *     // are intended to be only accessed by the GPU and are not intended for CPU data uploads.
 *     BindBufferInfo getStorage(size_t requiredBytes, ClearBuffer cleared = ClearBuffer::kNo) {
 *         return this->getBinding(kGpuOnlyStorageBufferIndex, requiredBytes, cleared);
 *     }
 *     BindBufferInfo getVertexStorage(size_t requiredBytes) {
 *         return this->getBinding(kVertexStorageBufferIndex, requiredBytes, ClearBuffer::kNo);
 *     }
 *     BindBufferInfo getIndexStorage(size_t requiredBytes) {
 *         return this->getBinding(kIndexStorageBufferIndex, requiredBytes, ClearBuffer::kNo);
 *     }
 *     BindBufferInfo getIndirectStorage(size_t requiredBytes, ClearBuffer cleared=ClearBuffer::kNo) {
 *         return this->getBinding(kIndirectStorageBufferIndex, requiredBytes, cleared);
 *     }
 *
 *     // Returns an entire storage buffer object that is large enough to fit `requiredBytes`. The
 *     // returned BufferSubAllocator can be used to sub-allocate one or more storage buffer bindings
 *     // that reference the same buffer object.
 *     //
 *     // When the BufferSubAllocator goes out of scope, the buffer object gets added to an internal
 *     // pool and is available for immediate reuse. getScratchStorage() returns buffers from this pool
 *     // if possible. A BufferSubAllocator can be explicitly returned to the pool by calling
 *     // `returnToPool()`.
 *     //
 *     // Returning a BufferSubAllocator back to the buffer too early can result in validation failures
 *     // and/or data races. It is the callers responsibility to manage reuse within a Recording and
 *     // guarantee synchronized access to buffer bindings.
 *     //
 *     // This type of usage is currently limited to GPU-only storage buffers.
 *     BufferSubAllocator getScratchStorage(size_t requiredBytes) {
 *         return this->getBuffer(kGpuOnlyStorageBufferIndex, requiredBytes,
 *                                /*stride=*/1, /*xtraAlignment=*/1,
 *                                ClearBuffer::kNo, Shareable::kScratch);
 *     }
 *
 *     // Finalizes all buffers and transfers ownership of them to a Recording. Returns true on success
 *     // and false if a mapping had previously failed.
 *     //
 *     // Regardless of success or failure, the DrawBufferManager is reset to a valid initial state
 *     // for recording buffer data for the next Recording.
 *     [[nodiscard]] bool transferToRecording(Recording*);
 *
 * private:
 *     friend class BufferSubAllocator;
 *
 *     struct BufferState {
 *         const BufferType    fType;
 *         const AccessPattern fAccessPattern;
 *         const bool          fUseTransferBuffer;
 *         const char*         fLabel;
 *
 *         const uint32_t fMinAlignment; // guaranteed power of two, required for binding
 *         const uint32_t fMinBlockSize;
 *         const uint32_t fMaxBlockSize;
 *
 *         BufferSubAllocator fAvailableBuffer;
 *
 *         // Buffers held in this array are owned by still-alive BufferSubAllocators that were created
 *         // with Shareable::kScratch. This is compatible with ResourceCache::ScratchResourceSet.
 *         skia_private::THashSet<const Resource*> fUnavailableScratchBuffers;
 *
 *         // The size of the last allocated Buffer, pinned to min/max block size, for amortizing the
 *         // number of buffer allocations for large Recordings.
 *         uint32_t fLastBufferSize = 0;
 *
 *         BufferState(BufferType, const char* label, bool isGpuOnly,
 *                     const Options&, const Caps* caps);
 *
 *         sk_sp<Buffer> findOrCreateBuffer(ResourceProvider*, Shareable, uint32_t byteCount);
 *     };
 *
 *     // The returned sub allocator will have an offset that is aligned to `stride`, `xtraAlignment`
 *     // and the minimum alignment for `stateIndex`. Its `availableWithStride()` will be >= `count`.
 *     BufferSubAllocator getBuffer(int stateIndex,
 *                                  size_t count,
 *                                  size_t stride,
 *                                  size_t xtraAlignment,
 *                                  ClearBuffer cleared,
 *                                  Shareable shareable);
 *
 *     MappedAllocationInfo getMappedBuffer(int stateIndex, size_t count, size_t stride,
 *                                          size_t reservedCount=0, size_t xtraAlignment=1) {
 *         BufferSubAllocator buffer = this->getBuffer(stateIndex,
 *                                                     std::max(count, reservedCount),
 *                                                     stride,
 *                                                     xtraAlignment,
 *                                                     ClearBuffer::kNo,
 *                                                     Shareable::kNo);
 *         auto [writer, binding] = buffer.getMappedSubrange(count, stride);
 *         return {std::move(writer), binding, std::move(buffer)};
 *     }
 *
 *     // Helper method for the public GPU-only BufferBindInfo methods
 *     BindBufferInfo getBinding(int stateIndex, size_t requiredBytes, ClearBuffer cleared) {
 *         auto alloc = this->getBuffer(stateIndex, requiredBytes,
 *                                      /*stride=*/1, /*xtraAlignment=*/1,
 *                                      cleared, Shareable::kNo);
 *         // `alloc` goes out of scope when this returns, but that is okay because it is only used
 *         // for GPU-only, non-shareable buffers. The returned BindBufferInfo will be unique still.
 *         return alloc.getSubrange(requiredBytes, /*stride=*/1);
 *     }
 *
 *     // Marks manager in a failed state, unmaps any previously collected buffers.
 *     void onFailedBuffer();
 *
 *     ResourceProvider* const fResourceProvider;
 *     const Caps* const fCaps;
 *     UploadBufferManager* fUploadManager;
 *
 *     static constexpr size_t kVertexBufferIndex          = 0;
 *     static constexpr size_t kIndexBufferIndex           = 1;
 *     static constexpr size_t kUniformBufferIndex         = 2;
 *     static constexpr size_t kStorageBufferIndex         = 3;
 *     static constexpr size_t kGpuOnlyStorageBufferIndex  = 4;
 *     static constexpr size_t kVertexStorageBufferIndex   = 5;
 *     static constexpr size_t kIndexStorageBufferIndex    = 6;
 *     static constexpr size_t kIndirectStorageBufferIndex = 7;
 *     std::array<BufferState, 8> fCurrentBuffers;
 *
 *     // Vector of buffer and transfer buffer pairs.
 *     skia_private::TArray<std::pair<sk_sp<Buffer>, BindBufferInfo>> fUsedBuffers;
 *
 *     // List of buffer regions that were requested to be cleared at the time of allocation.
 *     skia_private::TArray<BindBufferInfo> fClearList;
 *
 *     // If mapping failed on Buffers created/managed by this DrawBufferManager or by the mapped
 *     // transfer buffers from the UploadManager, remember so that the next Recording will fail.
 *     bool fMappingFailed = false;
 * }
 * ```
 */
public abstract class DrawBufferManager public constructor(
  resourceProvider: ResourceProvider?,
  caps: Caps?,
  uploadManager: UploadBufferManager?,
  dbmOpts: Options,
) {
  /**
   * C++ original:
   * ```cpp
   * ResourceProvider* const fResourceProvider
   * ```
   */
  private val fResourceProvider: ResourceProvider? = TODO("Initialize fResourceProvider")

  /**
   * C++ original:
   * ```cpp
   * const Caps* const fCaps
   * ```
   */
  private val fCaps: Caps? = TODO("Initialize fCaps")

  /**
   * C++ original:
   * ```cpp
   * UploadBufferManager* fUploadManager
   * ```
   */
  private var fUploadManager: Int? = TODO("Initialize fUploadManager")

  /**
   * C++ original:
   * ```cpp
   * static constexpr size_t kVertexBufferIndex          = 0
   * ```
   */
  private var fCurrentBuffers: Int = TODO("Initialize fCurrentBuffers")

  /**
   * C++ original:
   * ```cpp
   * static constexpr size_t kIndexBufferIndex           = 1
   * ```
   */
  private var fClearList: Int = TODO("Initialize fClearList")

  /**
   * C++ original:
   * ```cpp
   * static constexpr size_t kUniformBufferIndex         = 2
   * ```
   */
  private var fMappingFailed: Boolean = TODO("Initialize fMappingFailed")

  /**
   * C++ original:
   * ```cpp
   * bool hasMappingFailed() const { return fMappingFailed; }
   * ```
   */
  public fun hasMappingFailed(): Boolean {
    TODO("Implement hasMappingFailed")
  }

  /**
   * C++ original:
   * ```cpp
   * MappedAllocationInfo getMappedVertexBuffer(size_t count, size_t stride,
   *                                                size_t reservedCount=0, size_t alignment=1) {
   *         return this->getMappedBuffer(kVertexBufferIndex, count, stride, reservedCount, alignment);
   *     }
   * ```
   */
  public abstract fun getMappedVertexBuffer(
    count: ULong,
    stride: ULong,
    reservedCount: ULong = TODO(),
    alignment: ULong = TODO(),
  ): Int

  /**
   * C++ original:
   * ```cpp
   * MappedAllocationInfo getMappedIndexBuffer(size_t count) {
   *         return this->getMappedBuffer(kIndexBufferIndex, count, sizeof(uint16_t));
   *     }
   * ```
   */
  public fun getMappedIndexBuffer(count: ULong): Int {
    TODO("Implement getMappedIndexBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * MappedAllocationInfo getMappedUniformBuffer(size_t count, size_t stride) {
   *         return this->getMappedBuffer(kUniformBufferIndex, count, stride);
   *     }
   * ```
   */
  public fun getMappedUniformBuffer(count: ULong, stride: ULong): Int {
    TODO("Implement getMappedUniformBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * MappedAllocationInfo getMappedStorageBuffer(size_t count, size_t stride) {
   *         return this->getMappedBuffer(kStorageBufferIndex, count, stride);
   *     }
   * ```
   */
  public fun getMappedStorageBuffer(count: ULong, stride: ULong): Int {
    TODO("Implement getMappedStorageBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * BindBufferInfo getStorage(size_t requiredBytes, ClearBuffer cleared = ClearBuffer::kNo) {
   *         return this->getBinding(kGpuOnlyStorageBufferIndex, requiredBytes, cleared);
   *     }
   * ```
   */
  public fun getStorage(requiredBytes: ULong, cleared: ClearBuffer = TODO()): Int {
    TODO("Implement getStorage")
  }

  /**
   * C++ original:
   * ```cpp
   * BindBufferInfo getVertexStorage(size_t requiredBytes) {
   *         return this->getBinding(kVertexStorageBufferIndex, requiredBytes, ClearBuffer::kNo);
   *     }
   * ```
   */
  public fun getVertexStorage(requiredBytes: ULong): Int {
    TODO("Implement getVertexStorage")
  }

  /**
   * C++ original:
   * ```cpp
   * BindBufferInfo getIndexStorage(size_t requiredBytes) {
   *         return this->getBinding(kIndexStorageBufferIndex, requiredBytes, ClearBuffer::kNo);
   *     }
   * ```
   */
  public fun getIndexStorage(requiredBytes: ULong): Int {
    TODO("Implement getIndexStorage")
  }

  /**
   * C++ original:
   * ```cpp
   * BindBufferInfo getIndirectStorage(size_t requiredBytes, ClearBuffer cleared=ClearBuffer::kNo) {
   *         return this->getBinding(kIndirectStorageBufferIndex, requiredBytes, cleared);
   *     }
   * ```
   */
  public fun getIndirectStorage(requiredBytes: ULong, cleared: ClearBuffer = TODO()): Int {
    TODO("Implement getIndirectStorage")
  }

  /**
   * C++ original:
   * ```cpp
   * BufferSubAllocator getScratchStorage(size_t requiredBytes) {
   *         return this->getBuffer(kGpuOnlyStorageBufferIndex, requiredBytes,
   *                                /*stride=*/1, /*xtraAlignment=*/1,
   *                                ClearBuffer::kNo, Shareable::kScratch);
   *     }
   * ```
   */
  public fun getScratchStorage(requiredBytes: ULong): BufferSubAllocator {
    TODO("Implement getScratchStorage")
  }

  /**
   * C++ original:
   * ```cpp
   * bool DrawBufferManager::transferToRecording(Recording* recording) {
   *     if (fMappingFailed) {
   *         // All state should have been reset by onFailedBuffer() except for this error flag.
   *         SkASSERT(fUsedBuffers.empty() && fClearList.empty());
   * #if defined(SK_DEBUG)
   *         for (const auto& state : fCurrentBuffers) {
   *             SkASSERT(!SkToBool(state.fAvailableBuffer));
   *             SkASSERT(state.fUnavailableScratchBuffers.empty());
   *         }
   * #endif
   *
   *         fMappingFailed = false;
   *         return false;
   *     }
   *
   *     for (auto& state : fCurrentBuffers) {
   *         // Reset all available buffer sub allocators since they won't be allocatable anymore.
   *         // This pushes the underlying resource and transfer range to fUsedBuffers
   *         state.fAvailableBuffer.reset();
   *         // BufferSubAllocators should have gone out of scope well before Recorder::snap() is called.
   *         SkASSERT(state.fUnavailableScratchBuffers.empty());
   *
   *         // We reset the last buffer size back to 0 to keep the buffer growth behavior the same
   *         // across calls to snap(). If we knew every snap() would be approximately the same workload,
   *         // we could choose to keep the last alloc size as-is so that subsequent frames create
   *         // fewer buffers. We choose *not* to do this because:
   *         //  - Chrome often snaps Recordings with disparate workloads within a frame (e.g. tile vs
   *         //    canvas2d) and we don't want to overallocate on a small recording.
   *         //  - It obfuscates the performance cost of the first frame if we reach a steady state that
   *         //    requires no additional buffer allocations.
   *         // We could choose to reduce fLastBufferSize (e.g. halve it) to get a head start and reduce
   *         // the potential for over-allocation, but in performance measurements on buffer-heavy scenes
   *         // this did not lead to measurable improvements. Thus, we reset so every frame is the same.
   *         state.fLastBufferSize = 0;
   *     }
   *
   *     if (!fClearList.empty()) {
   *         recording->priv().taskList()->add(ClearBuffersTask::Make(std::move(fClearList)));
   *     }
   *
   *     for (auto& [buffer, transferBuffer] : fUsedBuffers) {
   *         if (transferBuffer) {
   *             SkASSERT(buffer);
   *             SkASSERT(!fCaps->drawBufferCanBeMapped());
   *             // Since the transfer buffer is managed by the UploadManager, we don't manually unmap
   *             // it here or need to pass a ref into CopyBufferToBufferTask.
   *             size_t copySize = buffer->size();
   *             recording->priv().taskList()->add(
   *                     CopyBufferToBufferTask::Make(transferBuffer.fBuffer,
   *                                                  transferBuffer.fOffset,
   *                                                  std::move(buffer),
   *                                                  /*dstOffset=*/0,
   *                                                  copySize));
   *         } else {
   *             if (buffer->isMapped()) {
   *                 buffer->unmap();
   *             }
   *             recording->priv().addResourceRef(std::move(buffer));
   *         }
   *     }
   *
   *     fUsedBuffers.clear();
   *
   *     return true;
   * }
   * ```
   */
  public fun transferToRecording(recording: Recording?): Boolean {
    TODO("Implement transferToRecording")
  }

  /**
   * C++ original:
   * ```cpp
   * BufferSubAllocator DrawBufferManager::getBuffer(
   *         int stateIndex,
   *         size_t count,
   *         size_t stride,
   *         size_t xtraAlignment,
   *         ClearBuffer cleared,
   *         Shareable shareable) {
   *     BufferState& state = fCurrentBuffers[stateIndex];
   *     // The size for a buffer is aligned to the minimum block size for better resource reuse, which
   *     // is more conservative than fMinAlignment.
   *     uint32_t requiredBytes32 = validate_count_and_stride(count, stride, state.fMinBlockSize);
   *     if (fMappingFailed || !requiredBytes32) {
   *         return {};
   *     }
   *
   *     const bool supportCpuUpload = state.fAccessPattern == AccessPattern::kHostVisible ||
   *                                   state.fUseTransferBuffer;
   *     // Shareable buffers must be GPU-only to actually share effectively.
   *     SkASSERT(shareable == Shareable::kNo || !supportCpuUpload);
   *
   *     // For non-shareable buffers, we keep the largest relinquished non-shareable buffer in case it
   *     // has room leftover to be used by future allocations. Scratch buffer ownership is entirely
   *     // managed by the caller, so always create a new BufferSubAllocator.
   *     if (shareable == Shareable::kNo) {
   *         state.fAvailableBuffer.resetForNewBinding(); // ensure we include min binding alignment
   *         state.fAvailableBuffer.prepForStride(stride, xtraAlignment, count);
   *         if (state.fAvailableBuffer.availableWithStride() >= count) {
   *             SkASSERT(state.fAvailableBuffer.fBuffer);
   *             SkASSERT(state.fAvailableBuffer.fBuffer->shareable() == shareable);
   *             SkASSERT(SkToBool(state.fAvailableBuffer.fMappedPtr) == supportCpuUpload);
   *             return std::move(state.fAvailableBuffer);
   *         }
   *
   *         // Not enough room in the available buffer so release it and create a new buffer.
   *         state.fAvailableBuffer.reset();
   *     }
   *
   *     // Create the next buffer by doubling the size of the previous buffer and clamping to be within
   *     // the min and max block sizes if `requiredBytes` is less than the max. Otherwise, create a
   *     // buffer large enough to satisfy `requiredBytes` but align it to minBlockSize.
   *     uint32_t bufferSize = SkAlignTo(requiredBytes32, state.fMinBlockSize);
   *     if (bufferSize < state.fMaxBlockSize) {
   *         // fMaxBlockSize should be sufficiently small that there's no risk of overflowing here.
   *         SkASSERT(std::numeric_limits<uint32_t>::max() /2 > state.fLastBufferSize);
   *         bufferSize = std::max(bufferSize, std::min(state.fLastBufferSize * 2, state.fMaxBlockSize));
   *         state.fLastBufferSize = bufferSize;
   *         SkASSERT(bufferSize <= state.fMaxBlockSize);
   *     } else {
   *         // Jump to the max block size for subsequent amortized allocations if we get a really big
   *         // buffer request.
   *         state.fLastBufferSize = state.fMaxBlockSize;
   *     }
   *     SkASSERT(bufferSize >= requiredBytes32 && bufferSize >= state.fMinBlockSize);
   *
   *     sk_sp<Buffer> buffer = state.findOrCreateBuffer(fResourceProvider, shareable, bufferSize);
   *     if (!buffer) {
   *         this->onFailedBuffer();
   *         return {};
   *     }
   *
   *     BindBufferInfo transferBuffer;
   *     void* mappedPtr = nullptr;
   *     if (supportCpuUpload) {
   *         if (state.fUseTransferBuffer) {
   *             std::tie(mappedPtr, transferBuffer) = fUploadManager->makeBindInfo(buffer->size(),
   *                     fCaps->requiredTransferBufferAlignment(), "TransferForDataBuffer");
   *         } else {
   *             mappedPtr = buffer->map();
   *         }
   *
   *         if (!mappedPtr) {
   *             this->onFailedBuffer(); // Either transfer buffer failed or direct mapping failed
   *             return {};
   *         }
   *     }
   *
   *     if (cleared == ClearBuffer::kYes) {
   *         fClearList.push_back(BindBufferInfo{buffer.get(), 0, bufferSize});
   *     }
   *
   *     // The returned buffer is not set to fAvailableBuffer because it is going to be passed up to
   *     // the caller for their use first. Since a new BufferSubAllocator starts at offset 0, there's no
   *     // need to account for `xtraAlignment`.
   *     return BufferSubAllocator(this, stateIndex, std::move(buffer),
   *                               transferBuffer, mappedPtr, stride);
   * }
   * ```
   */
  private fun getBuffer(
    stateIndex: Int,
    count: ULong,
    stride: ULong,
    xtraAlignment: ULong,
    cleared: ClearBuffer,
    shareable: Shareable,
  ): BufferSubAllocator {
    TODO("Implement getBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * MappedAllocationInfo getMappedBuffer(int stateIndex, size_t count, size_t stride,
   *                                          size_t reservedCount=0, size_t xtraAlignment=1) {
   *         BufferSubAllocator buffer = this->getBuffer(stateIndex,
   *                                                     std::max(count, reservedCount),
   *                                                     stride,
   *                                                     xtraAlignment,
   *                                                     ClearBuffer::kNo,
   *                                                     Shareable::kNo);
   *         auto [writer, binding] = buffer.getMappedSubrange(count, stride);
   *         return {std::move(writer), binding, std::move(buffer)};
   *     }
   * ```
   */
  private abstract fun getMappedBuffer(
    stateIndex: Int,
    count: ULong,
    stride: ULong,
    reservedCount: ULong = TODO(),
    xtraAlignment: ULong = TODO(),
  ): Int

  /**
   * C++ original:
   * ```cpp
   * BindBufferInfo getBinding(int stateIndex, size_t requiredBytes, ClearBuffer cleared) {
   *         auto alloc = this->getBuffer(stateIndex, requiredBytes,
   *                                      /*stride=*/1, /*xtraAlignment=*/1,
   *                                      cleared, Shareable::kNo);
   *         // `alloc` goes out of scope when this returns, but that is okay because it is only used
   *         // for GPU-only, non-shareable buffers. The returned BindBufferInfo will be unique still.
   *         return alloc.getSubrange(requiredBytes, /*stride=*/1);
   *     }
   * ```
   */
  private fun getBinding(
    stateIndex: Int,
    requiredBytes: ULong,
    cleared: ClearBuffer,
  ): Int {
    TODO("Implement getBinding")
  }

  /**
   * C++ original:
   * ```cpp
   * void DrawBufferManager::onFailedBuffer() {
   *     fMappingFailed = true;
   *
   *     // Clean up and unmap everything now
   *     fClearList.clear();
   *     for (auto& state : fCurrentBuffers) {
   *         state.fAvailableBuffer.reset();
   *          // We aren't allocating anything anymore so don't maintain this list. Their outstanding
   *          // BufferSubAllocators will have a no-op when they get reset.
   *         state.fUnavailableScratchBuffers.reset();
   *         state.fLastBufferSize = 0;
   *     }
   *
   *     for (auto& [buffer, _] : fUsedBuffers) {
   *         if (buffer->isMapped()) {
   *             buffer->unmap();
   *         }
   *     }
   *     fUsedBuffers.clear();
   * }
   * ```
   */
  private fun onFailedBuffer() {
    TODO("Implement onFailedBuffer")
  }

  public data class Options public constructor(
    public var fVertexBufferMinSize: Int,
    public var fVertexBufferMaxSize: Int,
    public var fIndexBufferSize: Int,
    public var fStorageBufferMinSize: Int,
    public var fStorageBufferMaxSize: Int,
    public var fUseExactBuffSizes: Boolean,
    public var fAllowCopyingGpuOnly: Boolean,
  )

  public data class BufferState public constructor(
    public val fType: Int,
    public val fAccessPattern: Int,
    public val fUseTransferBuffer: Boolean,
    public val fLabel: String?,
    public val fMinAlignment: Int,
    public val fMinBlockSize: Int,
    public val fMaxBlockSize: Int,
    public var fAvailableBuffer: BufferSubAllocator,
    public var fUnavailableScratchBuffers: Int,
    public var fLastBufferSize: Int,
  ) {
    public fun findOrCreateBuffer(
      provider: ResourceProvider?,
      shareable: Shareable,
      byteCount: UInt,
    ): Int {
      TODO("Implement findOrCreateBuffer")
    }
  }

  public companion object {
    private val kVertexBufferIndex: Int = TODO("Initialize kVertexBufferIndex")

    private val kIndexBufferIndex: Int = TODO("Initialize kIndexBufferIndex")

    private val kUniformBufferIndex: Int = TODO("Initialize kUniformBufferIndex")

    private val kStorageBufferIndex: Int = TODO("Initialize kStorageBufferIndex")

    private val kGpuOnlyStorageBufferIndex: Int = TODO("Initialize kGpuOnlyStorageBufferIndex")

    private val kVertexStorageBufferIndex: Int = TODO("Initialize kVertexStorageBufferIndex")

    private val kIndexStorageBufferIndex: Int = TODO("Initialize kIndexStorageBufferIndex")

    private val kIndirectStorageBufferIndex: Int = TODO("Initialize kIndirectStorageBufferIndex")
  }
}
