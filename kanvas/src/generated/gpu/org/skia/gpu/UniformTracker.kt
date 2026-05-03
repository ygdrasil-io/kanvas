package org.skia.gpu

import DrawPassCommands.List
import kotlin.Boolean
import kotlin.Int
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * class UniformTracker {
 * public:
 *     UniformTracker(bool useStorageBuffers) : fUseStorageBuffers(useStorageBuffers) {}
 *
 *     bool writeUniforms(UniformDataCache& uniformCache,
 *                        DrawBufferManager* bufferMgr,
 *                        UniformDataCache::Index index) {
 *         if (index >= UniformDataCache::kInvalidIndex) {
 *             return false;
 *         }
 *
 *         if (index == fLastIndex) {
 *             return false;
 *         }
 *         fLastIndex = index;
 *
 *         UniformDataCache::Entry& uniformData = uniformCache.lookup(index);
 *         const size_t uniformDataSize = uniformData.fCpuData.size();
 *
 *         // Upload the uniform data if we haven't already.
 *         // Alternatively, re-upload the uniform data to avoid a rebind if we're using storage
 *         // buffers. This will result in more data uploaded, but the tradeoff seems worthwhile.
 *         if (!uniformData.fBufferBinding.fBuffer ||
 *             (fUseStorageBuffers && uniformData.fBufferBinding.fBuffer != fLastBinding.fBuffer)) {
 *             BufferWriter writer;
 *             std::tie(writer, uniformData.fBufferBinding) =
 *                     fCurrentBuffer.getMappedSubrange(1, uniformDataSize);
 *             if (!writer) {
 *                 // Allocate a new buffer
 *                 std::tie(writer, uniformData.fBufferBinding, fCurrentBuffer) =
 *                         fUseStorageBuffers ? bufferMgr->getMappedStorageBuffer(1, uniformDataSize)
 *                                            : bufferMgr->getMappedUniformBuffer(1, uniformDataSize);
 *                 if (!writer) {
 *                     return {}; // Allocation failed so early out
 *                 }
 *             }
 *
 *             writer.write(uniformData.fCpuData.data(), uniformDataSize);
 *
 *             if (fUseStorageBuffers) {
 *                 // When using storage buffers, store the SSBO index in the binding's offset field
 *                 // and always use the entire buffer's size in the size field.
 *                 SkASSERT(uniformData.fBufferBinding.fOffset % uniformDataSize == 0);
 *                 uniformData.fBufferBinding.fOffset /= uniformDataSize;
 *                 uniformData.fBufferBinding.fSize = uniformData.fBufferBinding.fBuffer->size();
 *             } else {
 *                 // Every new set of uniform data has to be bound, this ensures its aligned correctly
 *                 fCurrentBuffer.resetForNewBinding();
 *             }
 *         }
 *
 *         const bool needsRebind =
 *                 uniformData.fBufferBinding.fBuffer != fLastBinding.fBuffer ||
 *                 (!fUseStorageBuffers && uniformData.fBufferBinding.fOffset != fLastBinding.fOffset);
 *
 *         fLastBinding = uniformData.fBufferBinding;
 *
 *         return needsRebind;
 *     }
 *
 *     void bindUniforms(UniformSlot slot, DrawPassCommands::List* commandList) {
 *         BindBufferInfo binding = fLastBinding;
 *         if (fUseStorageBuffers) {
 *             // Track the SSBO index in fLastBinding, but set offset = 0 in the actual used binding.
 *             binding.fOffset = 0;
 *         }
 *         commandList->bindUniformBuffer(binding, slot);
 *     }
 *
 *     uint32_t ssboIndex() const {
 *         // The SSBO index for the last-bound storage buffer is stored in the binding's offset field.
 *         return fLastBinding.fOffset;
 *     }
 *
 * private:
 *     // The GPU buffer data is being written into; for SSBOs, Graphite will only record a bind
 *     // command when this changes. Sub-allocations will be aligned such that they can be randomly
 *     // accessed even if the data is heterogenous. UBOs will always have to issue binding commands
 *     // when a draw needs to use a different set of uniform values.
 *     BufferSubAllocator fCurrentBuffer;
 *
 *     // Internally track the last binding returned, so that we know whether new uploads or rebindings
 *     // are necessary. If we're using SSBOs, this is treated specially -- the fOffset field holds the
 *     // index in the storage buffer of the last-written uniforms, and the offsets used for actual
 *     // bindings are always zero.
 *     BindBufferInfo fLastBinding;
 *
 *     // This keeps track of the last index used for writing uniforms from a provided uniform cache.
 *     // If a provided index matches the last index, the uniforms are assumed to already be written
 *     // and no additional uploading is performed. This assumes a UniformTracker will always be
 *     // provided with the same uniform cache.
 *     UniformDataCache::Index fLastIndex = UniformDataCache::kInvalidIndex;
 *
 *     const bool fUseStorageBuffers;
 * }
 * ```
 */
public data class UniformTracker public constructor(
  /**
   * C++ original:
   * ```cpp
   * BufferSubAllocator fCurrentBuffer
   * ```
   */
  private var fCurrentBuffer: BufferSubAllocator,
  /**
   * C++ original:
   * ```cpp
   * BindBufferInfo fLastBinding
   * ```
   */
  private var fLastBinding: BindBufferInfo,
  /**
   * C++ original:
   * ```cpp
   * UniformDataCache::Index fLastIndex
   * ```
   */
  private var fLastIndex: Int,
  /**
   * C++ original:
   * ```cpp
   * const bool fUseStorageBuffers
   * ```
   */
  private val fUseStorageBuffers: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * bool writeUniforms(UniformDataCache& uniformCache,
   *                        DrawBufferManager* bufferMgr,
   *                        UniformDataCache::Index index) {
   *         if (index >= UniformDataCache::kInvalidIndex) {
   *             return false;
   *         }
   *
   *         if (index == fLastIndex) {
   *             return false;
   *         }
   *         fLastIndex = index;
   *
   *         UniformDataCache::Entry& uniformData = uniformCache.lookup(index);
   *         const size_t uniformDataSize = uniformData.fCpuData.size();
   *
   *         // Upload the uniform data if we haven't already.
   *         // Alternatively, re-upload the uniform data to avoid a rebind if we're using storage
   *         // buffers. This will result in more data uploaded, but the tradeoff seems worthwhile.
   *         if (!uniformData.fBufferBinding.fBuffer ||
   *             (fUseStorageBuffers && uniformData.fBufferBinding.fBuffer != fLastBinding.fBuffer)) {
   *             BufferWriter writer;
   *             std::tie(writer, uniformData.fBufferBinding) =
   *                     fCurrentBuffer.getMappedSubrange(1, uniformDataSize);
   *             if (!writer) {
   *                 // Allocate a new buffer
   *                 std::tie(writer, uniformData.fBufferBinding, fCurrentBuffer) =
   *                         fUseStorageBuffers ? bufferMgr->getMappedStorageBuffer(1, uniformDataSize)
   *                                            : bufferMgr->getMappedUniformBuffer(1, uniformDataSize);
   *                 if (!writer) {
   *                     return {}; // Allocation failed so early out
   *                 }
   *             }
   *
   *             writer.write(uniformData.fCpuData.data(), uniformDataSize);
   *
   *             if (fUseStorageBuffers) {
   *                 // When using storage buffers, store the SSBO index in the binding's offset field
   *                 // and always use the entire buffer's size in the size field.
   *                 SkASSERT(uniformData.fBufferBinding.fOffset % uniformDataSize == 0);
   *                 uniformData.fBufferBinding.fOffset /= uniformDataSize;
   *                 uniformData.fBufferBinding.fSize = uniformData.fBufferBinding.fBuffer->size();
   *             } else {
   *                 // Every new set of uniform data has to be bound, this ensures its aligned correctly
   *                 fCurrentBuffer.resetForNewBinding();
   *             }
   *         }
   *
   *         const bool needsRebind =
   *                 uniformData.fBufferBinding.fBuffer != fLastBinding.fBuffer ||
   *                 (!fUseStorageBuffers && uniformData.fBufferBinding.fOffset != fLastBinding.fOffset);
   *
   *         fLastBinding = uniformData.fBufferBinding;
   *
   *         return needsRebind;
   *     }
   * ```
   */
  public fun writeUniforms(
    uniformCache: UniformDataCache,
    bufferMgr: DrawBufferManager?,
    index: UniformDataCache.Index,
  ): Boolean {
    TODO("Implement writeUniforms")
  }

  /**
   * C++ original:
   * ```cpp
   * void bindUniforms(UniformSlot slot, DrawPassCommands::List* commandList) {
   *         BindBufferInfo binding = fLastBinding;
   *         if (fUseStorageBuffers) {
   *             // Track the SSBO index in fLastBinding, but set offset = 0 in the actual used binding.
   *             binding.fOffset = 0;
   *         }
   *         commandList->bindUniformBuffer(binding, slot);
   *     }
   * ```
   */
  public fun bindUniforms(slot: UniformSlot, commandList: List?) {
    TODO("Implement bindUniforms")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t ssboIndex() const {
   *         // The SSBO index for the last-bound storage buffer is stored in the binding's offset field.
   *         return fLastBinding.fOffset;
   *     }
   * ```
   */
  public fun ssboIndex(): UInt {
    TODO("Implement ssboIndex")
  }
}
