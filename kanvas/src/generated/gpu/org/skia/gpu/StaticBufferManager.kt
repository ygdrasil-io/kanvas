package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * class StaticBufferManager {
 * public:
 *     StaticBufferManager(ResourceProvider*, const Caps*);
 *     ~StaticBufferManager();
 *
 *     // The passed in BindBufferInfos are updated when finalize() is later called, to point to the
 *     // packed, GPU-private buffer at the appropriate offset. The data written to the returned Writer
 *     // is copied to the private buffer at that offset. 'binding' must live until finalize() returns.
 *
 *     // For the vertex writer, the count and stride of the buffer is passed to allow alignment of
 *     // future vertices.
 *     VertexWriter getVertexWriter(size_t count, size_t stride, BindBufferInfo* binding);
 *     // TODO: Update the tessellation index buffer generation functions to use an IndexWriter so this
 *     // can return an IndexWriter vs. a VertexWriter that happens to just write uint16s...
 *     VertexWriter getIndexWriter(size_t size, BindBufferInfo* binding);
 *
 *     enum class FinishResult : int {
 *         kFailure, // Unable to create or copy static buffers
 *         kSuccess, // Successfully created static buffers and added GPU tasks to the queue
 *         kNoWork   // No static buffers required, no GPU tasks add to the queue
 *     };
 *
 *     // Finalizes all buffers and records a copy task to compact and privatize static data. The
 *     // final static buffers will become owned by the Context's GlobalCache.
 *     FinishResult finalize(Context*, QueueManager*, GlobalCache*);
 *
 * private:
 *     struct CopyRange {
 *         BindBufferInfo  fSource;            // The CPU-to-GPU buffer and offset for the source of the copy
 *         BindBufferInfo* fTarget;            // The late-assigned destination of the copy
 *         uint32_t        fRequiredAlignment; // The requested stride of the data.
 * #if defined(GPU_TEST_UTILS)
 *         uint32_t        fUnalignedSize;     // The requested size without count-4 alignment
 * #endif
 *     };
 *     struct BufferState {
 *         BufferState(BufferType type, const Caps* caps);
 *
 *         bool createAndUpdateBindings(ResourceProvider*, Context*, QueueManager*, GlobalCache*,
 *                                      std::string_view label) const;
 *         void reset() {
 *             fData.clear();
 *             fTotalRequiredBytes = 0;
 *         }
 *
 *         const BufferType fBufferType;
 *         // This is the lcm of the alignment requirement of the buffer type and the transfer buffer
 *         // alignment requirement.
 *         const uint32_t fMinimumAlignment;
 *
 *         skia_private::TArray<CopyRange> fData;
 *         uint32_t fTotalRequiredBytes;
 *     };
 *
 *     void* prepareStaticData(BufferState* info,
 *                             size_t requiredBytes,
 *                             size_t requiredAlignment,
 *                             BindBufferInfo* target);
 *
 *     ResourceProvider* const fResourceProvider;
 *     UploadBufferManager fUploadManager;
 *     const uint32_t fRequiredTransferAlignment;
 *
 *     // The source data that's copied into a final GPU-private buffer
 *     BufferState fVertexBufferState;
 *     BufferState fIndexBufferState;
 *
 *     // If mapping failed on Buffers created/managed by this StaticBufferManager or by the mapped
 *     // transfer buffers from the UploadManager, remember so that finalize() will fail.
 *     bool fMappingFailed = false;
 * }
 * ```
 */
public data class StaticBufferManager public constructor(
  /**
   * C++ original:
   * ```cpp
   * ResourceProvider* const fResourceProvider
   * ```
   */
  private val fResourceProvider: ResourceProvider?,
  /**
   * C++ original:
   * ```cpp
   * UploadBufferManager fUploadManager
   * ```
   */
  private var fUploadManager: Int,
  /**
   * C++ original:
   * ```cpp
   * const uint32_t fRequiredTransferAlignment
   * ```
   */
  private val fRequiredTransferAlignment: Int,
  /**
   * C++ original:
   * ```cpp
   * BufferState fVertexBufferState
   * ```
   */
  private var fVertexBufferState: BufferState,
  /**
   * C++ original:
   * ```cpp
   * BufferState fIndexBufferState
   * ```
   */
  private var fIndexBufferState: BufferState,
  /**
   * C++ original:
   * ```cpp
   * bool fMappingFailed = false
   * ```
   */
  private var fMappingFailed: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * VertexWriter StaticBufferManager::getVertexWriter(size_t count,
   *                                                   size_t stride,
   *                                                   BindBufferInfo* binding) {
   *     const size_t size = count * stride;
   *     const size_t alignedCount = SkAlign4(count);
   *     void* data = this->prepareStaticData(&fVertexBufferState, size, stride * 4, binding);
   *     if (alignedCount > count) {
   *         const uint32_t byteDiff = (alignedCount - count) * stride;
   *         void* zPtr = SkTAddOffset<void>(data, count * stride);
   *         memset(zPtr, 0, byteDiff);
   *     }
   *     return VertexWriter{data, size};
   * }
   * ```
   */
  public fun getVertexWriter(
    count: ULong,
    stride: ULong,
    binding: BindBufferInfo?,
  ): Int {
    TODO("Implement getVertexWriter")
  }

  /**
   * C++ original:
   * ```cpp
   * VertexWriter StaticBufferManager::getIndexWriter(size_t size, BindBufferInfo* binding) {
   *     // The index writer does not have the same alignment requirements as a vertex, so we simply pass
   *     // in the minimum alignment as the required alignment
   *     void* data = this->prepareStaticData(&fIndexBufferState,
   *                                          size,
   *                                          fIndexBufferState.fMinimumAlignment,
   *                                          binding);
   *     return VertexWriter{data, size};
   * }
   * ```
   */
  public fun getIndexWriter(size: ULong, binding: BindBufferInfo?): Int {
    TODO("Implement getIndexWriter")
  }

  /**
   * C++ original:
   * ```cpp
   * StaticBufferManager::FinishResult StaticBufferManager::finalize(Context* context,
   *                                                                 QueueManager* queueManager,
   *                                                                 GlobalCache* globalCache) {
   *     if (fMappingFailed) {
   *         return FinishResult::kFailure;
   *     }
   *
   *     const size_t totalRequiredBytes = fVertexBufferState.fTotalRequiredBytes +
   *                                       fIndexBufferState.fTotalRequiredBytes;
   *     SkASSERT(totalRequiredBytes <= kMaxStaticDataSize);
   *     if (!totalRequiredBytes) {
   *         return FinishResult::kNoWork;
   *     }
   *
   *     if (!fVertexBufferState.createAndUpdateBindings(fResourceProvider,
   *                                                    context,
   *                                                    queueManager,
   *                                                    globalCache,
   *                                                    "StaticVertexBuffer")) {
   *         return FinishResult::kFailure;
   *     }
   *
   * #if defined(GPU_TEST_UTILS)
   *     skia_private::TArray<GlobalCache::StaticVertexCopyRanges> statVertCopy;
   *     for (const CopyRange& data : fVertexBufferState.fData) {
   *         statVertCopy.push_back({data.fTarget->fOffset,
   *                                 data.fUnalignedSize,
   *                                 data.fTarget->fSize,
   *                                 data.fRequiredAlignment});
   *     }
   *     globalCache->testingOnly_SetStaticVertexInfo(
   *             statVertCopy,
   *             fVertexBufferState.fData[0].fTarget->fBuffer);
   * #endif
   *
   *     if (!fIndexBufferState.createAndUpdateBindings(fResourceProvider,
   *                                                    context,
   *                                                    queueManager,
   *                                                    globalCache,
   *                                                    "StaticIndexBuffer")) {
   *         return FinishResult::kFailure;
   *     }
   *     queueManager->addUploadBufferManagerRefs(&fUploadManager);
   *
   *     // Reset the static buffer manager since the Recording's copy tasks now manage ownership of
   *     // the transfer buffers and the GlobalCache owns the final static buffers.
   *     fVertexBufferState.reset();
   *     fIndexBufferState.reset();
   *
   *     return FinishResult::kSuccess;
   * }
   * ```
   */
  public fun finalize(
    context: Context?,
    queueManager: QueueManager?,
    globalCache: GlobalCache?,
  ): FinishResult {
    TODO("Implement finalize")
  }

  /**
   * C++ original:
   * ```cpp
   * void* StaticBufferManager::prepareStaticData(BufferState* state,
   *                                              size_t requiredBytes,
   *                                              size_t requiredAlignment,
   *                                              BindBufferInfo* target) {
   *     // Zero-out the target binding in the event of any failure in actually transfering data later.
   *     // Unlike in BufferSubAllocator::reserve(), we do use SkTo<uint32_t> to check
   *     // `requiredAlignment`. This is not dynamic data and is fully controlled by Graphite, so if it
   *     // asserts, then there is a bug in the static data for a Renderer that must be fixed.
   *     const uint32_t align32 = lcm_alignment(state->fMinimumAlignment,
   *                                            SkTo<uint32_t>(requiredAlignment));
   *
   *     SkASSERT(target);
   *     *target = {nullptr, 0};
   *     uint32_t size32 = validate_count_and_stride(requiredBytes, /*stride=*/1, align32);
   *     if (!size32 || fMappingFailed) {
   *         return nullptr;
   *     }
   *
   *     // Copy data must be aligned to the transfer alignment, so align the reserved size to the LCM
   *     // of the minimum alignment (already net buffer and transfer alignment) and the required
   *     // alignment stride.
   *     size32 = SkAlignNonPow2(size32, align32);
   *     auto [transferMapPtr, transferBindInfo] =
   *             fUploadManager.makeBindInfo(size32,
   *                                         fRequiredTransferAlignment,
   *                                         "TransferForStaticBuffer");
   *     if (!transferMapPtr) {
   *         SKGPU_LOG_E("Failed to create or map transfer buffer that initializes static GPU data.");
   *         fMappingFailed = true;
   *         return nullptr;
   *     }
   *
   *     state->fData.push_back(
   *             {transferBindInfo,
   *              target,
   *              SkTo<uint32_t>(requiredAlignment),
   * #if defined(GPU_TEST_UTILS)
   *              SkTo<uint32_t>(requiredBytes)
   * #endif
   *             });
   *
   *     state->fTotalRequiredBytes = SkAlignNonPow2(state->fTotalRequiredBytes, align32) + size32;
   *
   *     return transferMapPtr;
   * }
   * ```
   */
  private fun prepareStaticData(
    info: BufferState?,
    requiredBytes: ULong,
    requiredAlignment: ULong,
    target: BindBufferInfo?,
  ) {
    TODO("Implement prepareStaticData")
  }

  public data class CopyRange public constructor(
    public var fSource: Int,
    public var fTarget: Int?,
    public var fRequiredAlignment: Int,
    public var fUnalignedSize: Int,
  )

  public data class BufferState public constructor(
    public val fBufferType: Int,
    public val fMinimumAlignment: Int,
    public var fData: Int,
    public var fTotalRequiredBytes: Int,
  ) {
    public fun createAndUpdateBindings(
      resourceProvider: ResourceProvider?,
      context: Context?,
      queueManager: QueueManager?,
      globalCache: GlobalCache?,
      label: String,
    ): Boolean {
      TODO("Implement createAndUpdateBindings")
    }

    public fun reset() {
      TODO("Implement reset")
    }
  }

  public enum class FinishResult {
    kFailure,
    kSuccess,
    kNoWork,
  }
}
