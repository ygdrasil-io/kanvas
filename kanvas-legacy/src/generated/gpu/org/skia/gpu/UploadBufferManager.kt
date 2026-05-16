package org.skia.gpu

import kotlin.Int
import kotlin.String
import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * class UploadBufferManager {
 * public:
 *     UploadBufferManager(ResourceProvider*, const Caps*);
 *     ~UploadBufferManager();
 *
 *     std::tuple<TextureUploadWriter, BindBufferInfo> getTextureUploadWriter(
 *             size_t requiredBytes, size_t requiredAlignment);
 *
 *     // Finalizes all buffers and transfers ownership of them to a Recording.
 *     void transferToRecording(Recording*);
 *     void transferToCommandBuffer(CommandBuffer*);
 *
 * private:
 *     friend class DrawBufferManager; // to access makeBindInfo
 *     friend class StaticBufferManager; // to access makeBindInfo
 *
 *     std::tuple<void* /*mappedPtr*/, BindBufferInfo> makeBindInfo(size_t requiredBytes,
 *                                                                  size_t requiredAlignment,
 *                                                                  std::string_view label);
 *
 *     ResourceProvider* fResourceProvider;
 *
 *     sk_sp<Buffer> fReusedBuffer;
 *     const uint32_t fMinAlignment;
 *     uint32_t fReusedBufferOffset = 0;
 *
 *     std::vector<sk_sp<Buffer>> fUsedBuffers;
 * }
 * ```
 */
public data class UploadBufferManager public constructor(
  /**
   * C++ original:
   * ```cpp
   * ResourceProvider* fResourceProvider
   * ```
   */
  private var fResourceProvider: ResourceProvider?,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<Buffer> fReusedBuffer
   * ```
   */
  private var fReusedBuffer: Int,
  /**
   * C++ original:
   * ```cpp
   * const uint32_t fMinAlignment
   * ```
   */
  private val fMinAlignment: Int,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fReusedBufferOffset
   * ```
   */
  private var fReusedBufferOffset: Int,
  /**
   * C++ original:
   * ```cpp
   * std::vector<sk_sp<Buffer>> fUsedBuffers
   * ```
   */
  private var fUsedBuffers: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * std::tuple<TextureUploadWriter, BindBufferInfo> UploadBufferManager::getTextureUploadWriter(
   *         size_t requiredBytes, size_t requiredAlignment) {
   *     auto[bufferMapPtr, bindInfo] = this->makeBindInfo(requiredBytes,
   *                                                       requiredAlignment,
   *                                                       "TextureUploadBuffer");
   *     if (!bufferMapPtr) {
   *         return {TextureUploadWriter(), BindBufferInfo()};
   *     }
   *
   *     return {TextureUploadWriter(bufferMapPtr, requiredBytes), bindInfo};
   * }
   * ```
   */
  public fun getTextureUploadWriter(requiredBytes: ULong, requiredAlignment: ULong): Int {
    TODO("Implement getTextureUploadWriter")
  }

  /**
   * C++ original:
   * ```cpp
   * void UploadBufferManager::transferToRecording(Recording* recording) {
   *     for (sk_sp<Buffer>& buffer : fUsedBuffers) {
   *         buffer->unmap();
   *         recording->priv().addResourceRef(std::move(buffer));
   *     }
   *     fUsedBuffers.clear();
   *
   *     if (fReusedBuffer) {
   *         fReusedBuffer->unmap();
   *         recording->priv().addResourceRef(std::move(fReusedBuffer));
   *     }
   * }
   * ```
   */
  public fun transferToRecording(recording: Recording?) {
    TODO("Implement transferToRecording")
  }

  /**
   * C++ original:
   * ```cpp
   * void UploadBufferManager::transferToCommandBuffer(CommandBuffer* commandBuffer) {
   *     for (sk_sp<Buffer>& buffer : fUsedBuffers) {
   *         buffer->unmap();
   *         commandBuffer->trackCommandBufferResource(std::move(buffer));
   *     }
   *     fUsedBuffers.clear();
   *
   *     if (fReusedBuffer) {
   *         fReusedBuffer->unmap();
   *         commandBuffer->trackCommandBufferResource(std::move(fReusedBuffer));
   *     }
   * }
   * ```
   */
  public fun transferToCommandBuffer(commandBuffer: CommandBuffer?) {
    TODO("Implement transferToCommandBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * std::tuple<void* /*mappedPtr*/, BindBufferInfo> UploadBufferManager::makeBindInfo(
   *         size_t requiredBytes, size_t requiredAlignment, std::string_view label) {
   *     if (!SkTFitsIn<uint32_t>(requiredBytes)) {
   *         return {nullptr, BindBufferInfo()};
   *     }
   *
   *     uint32_t requiredAlignment32 = std::max(SkTo<uint32_t>(requiredAlignment), fMinAlignment);
   *     uint32_t requiredBytes32 = SkAlignTo(SkTo<uint32_t>(requiredBytes), requiredAlignment32);
   *     if (requiredBytes32 > kReusedBufferSize) {
   *         // Create a dedicated buffer for this request.
   *         sk_sp<Buffer> buffer = fResourceProvider->findOrCreateNonShareableBuffer(
   *                 requiredBytes32,
   *                 BufferType::kXferCpuToGpu,
   *                 AccessPattern::kHostVisible,
   *                 std::move(label));
   *         void* bufferMapPtr = buffer ? buffer->map() : nullptr;
   *         if (!bufferMapPtr) {
   *             // Unlike [Draw|Static]BufferManager, the UploadManager does not track if any buffer
   *             // mapping has failed. This is because it's common for uploads to be scoped to a
   *             // specific image creation. In that case, the image can be returned as null to signal a
   *             // very isolated failure instead of taking down the entire Recording. For the other
   *             // managers, failures to map buffers creates unrecoverable scenarios.
   *             return {nullptr, BindBufferInfo()};
   *         }
   *
   *         BindBufferInfo bindInfo;
   *         bindInfo.fBuffer = buffer.get();
   *         bindInfo.fOffset = 0;
   *         bindInfo.fSize = requiredBytes32;
   *
   *         fUsedBuffers.push_back(std::move(buffer));
   *         return {bufferMapPtr, bindInfo};
   *     }
   *
   *     // Try to reuse an already-allocated buffer.
   *     fReusedBufferOffset = SkAlignTo(fReusedBufferOffset, requiredAlignment32);
   *     if (fReusedBuffer && requiredBytes32 > fReusedBuffer->size() - fReusedBufferOffset) {
   *         fUsedBuffers.push_back(std::move(fReusedBuffer));
   *     }
   *
   *     if (!fReusedBuffer) {
   *         fReusedBuffer = fResourceProvider->findOrCreateNonShareableBuffer(
   *                 kReusedBufferSize,
   *                 BufferType::kXferCpuToGpu,
   *                 AccessPattern::kHostVisible,
   *                 std::move(label));
   *         fReusedBufferOffset = 0;
   *         if (!fReusedBuffer || !fReusedBuffer->map()) {
   *             fReusedBuffer = nullptr;
   *             return {nullptr, BindBufferInfo()};
   *         }
   *     }
   *
   *     BindBufferInfo bindInfo;
   *     bindInfo.fBuffer = fReusedBuffer.get();
   *     bindInfo.fOffset = fReusedBufferOffset;
   *     bindInfo.fSize = requiredBytes32;
   *
   *     void* bufferMapPtr = fReusedBuffer->map();
   *     SkASSERT(bufferMapPtr); // Should have been validated when it was created
   *     bufferMapPtr = SkTAddOffset<void>(bufferMapPtr, fReusedBufferOffset);
   *
   *     fReusedBufferOffset += requiredBytes32;
   *
   *     return {bufferMapPtr, bindInfo};
   * }
   * ```
   */
  private fun makeBindInfo(
    requiredBytes: ULong,
    requiredAlignment: ULong,
    label: String,
  ): Int {
    TODO("Implement makeBindInfo")
  }
}
