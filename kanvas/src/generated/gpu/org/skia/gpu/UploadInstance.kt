package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkColorInfo
import org.skia.foundation.SkSp
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * class UploadInstance {
 * public:
 *     static UploadInstance Make(Recorder*,
 *                                sk_sp<TextureProxy> textureProxy,
 *                                const SkColorInfo& srcColorInfo,
 *                                const SkColorInfo& dstColorInfo,
 *                                const UploadSource& source,
 *                                const SkIRect& dstRect,
 *                                std::unique_ptr<ConditionalUploadContext>);
 *     static UploadInstance MakeCompressed(Recorder*,
 *                                          sk_sp<TextureProxy> textureProxy,
 *                                          const UploadSource& source);
 *
 *     static UploadInstance Invalid() { return {}; }
 *
 *     UploadInstance(UploadInstance&&);
 *     UploadInstance& operator=(UploadInstance&&);
 *     ~UploadInstance();
 *
 *     bool isValid() const { return fBuffer != nullptr && fTextureProxy != nullptr; }
 *
 *     bool prepareResources(ResourceProvider*);
 *
 *     // Adds upload command to the given CommandBuffer, returns false if the instance should be
 *     // discarded.
 *     Task::Status addCommand(Context*, CommandBuffer*, Task::ReplayTargetData) const;
 *
 * private:
 *     friend class UploadTask;
 *
 *     UploadInstance();
 *     // Copy data is appended directly after the object is created
 *     UploadInstance(const Buffer*,
 *                    size_t bytesPerPixel,
 *                    sk_sp<TextureProxy>,
 *                    std::unique_ptr<ConditionalUploadContext> = nullptr);
 *
 *     const Buffer* fBuffer;
 *     size_t fBytesPerPixel;
 *     sk_sp<TextureProxy> fTextureProxy;
 *     skia_private::STArray<1, BufferTextureCopyData> fCopyData;
 *     std::unique_ptr<ConditionalUploadContext> fConditionalContext;
 * }
 * ```
 */
public data class UploadInstance public constructor(
  /**
   * C++ original:
   * ```cpp
   * const Buffer* fBuffer
   * ```
   */
  private val fBuffer: Buffer?,
  /**
   * C++ original:
   * ```cpp
   * size_t fBytesPerPixel
   * ```
   */
  private var fBytesPerPixel: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<TextureProxy> fTextureProxy
   * ```
   */
  private var fTextureProxy: Int,
  /**
   * C++ original:
   * ```cpp
   * skia_private::STArray<1, BufferTextureCopyData> fCopyData
   * ```
   */
  private var fCopyData: Int,
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<ConditionalUploadContext> fConditionalContext
   * ```
   */
  private var fConditionalContext: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * UploadInstance& UploadInstance::operator=(UploadInstance&&)
   * ```
   */
  public fun assign(param0: UploadInstance) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isValid() const { return fBuffer != nullptr && fTextureProxy != nullptr; }
   * ```
   */
  public fun isValid(): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * bool UploadInstance::prepareResources(ResourceProvider* resourceProvider) {
   *     // While most uploads are to already instantiated proxies (e.g. for client-created texture
   *     // images) it is possible that writePixels() was issued as the first operation on a scratch
   *     // Device, or that this is the first upload to the raster or text atlas proxies.
   *     // TODO: Determine how to instantatiate textues in this case; atlas proxies shouldn't really be
   *     // "scratch" because they aren't going to be reused for anything else in a Recording. At the
   *     // same time, it could still go through the ScratchResourceManager and just never return them,
   *     // which is no different from instantiating them directly with the ResourceProvider.
   *     if (!TextureProxy::InstantiateIfNotLazy(resourceProvider, fTextureProxy.get())) {
   *         SKGPU_LOG_E("Could not instantiate texture proxy for UploadTask!");
   *         return false;
   *     }
   *     return true;
   * }
   * ```
   */
  public fun prepareResources(resourceProvider: ResourceProvider?): Boolean {
    TODO("Implement prepareResources")
  }

  /**
   * C++ original:
   * ```cpp
   * Task::Status UploadInstance::addCommand(Context* context,
   *                                         CommandBuffer* commandBuffer,
   *                                         Task::ReplayTargetData replayData) const {
   *     using Status = Task::Status;
   *     SkASSERT(fTextureProxy && fTextureProxy->isInstantiated());
   *
   *     if (fConditionalContext && !fConditionalContext->needsUpload(context)) {
   *         // Assume that if a conditional context says to dynamically not upload that another
   *         // time through the tasks should try to upload again.
   *         return Status::kSuccess;
   *     }
   *
   *     if (fTextureProxy->texture() != replayData.fTarget) {
   *         // The CommandBuffer doesn't take ownership of the upload buffer here; it's owned by
   *         // UploadBufferManager, which will transfer ownership in transferToCommandBuffer.
   *         if (!commandBuffer->copyBufferToTexture(fBuffer,
   *                                                 fTextureProxy->refTexture(),
   *                                                 fCopyData.data(),
   *                                                 fCopyData.size())) {
   *             return Status::kFail;
   *         }
   *     } else {
   *         // Here we assume that multiple copies in a single UploadInstance are always used for
   *         // mipmaps of a single image, and that we won't ever upload to a replay target's mipmaps
   *         // directly.
   *         SkASSERT(fCopyData.size() == 1);
   *         const BufferTextureCopyData& copyData = fCopyData[0];
   *         SkIRect dstRect = copyData.fRect;
   *         dstRect.offset(replayData.fTranslation);
   *         SkIRect croppedDstRect = dstRect;
   *
   *         if (!replayData.fClip.isEmpty()) {
   *             SkIRect dstClip = replayData.fClip;
   *             dstClip.offset(replayData.fTranslation);
   *             if (!croppedDstRect.intersect(dstClip)) {
   *                 // The replay clip can change on each insert, so subsequent replays may actually
   *                 // intersect the copy rect.
   *                 return Status::kSuccess;
   *             }
   *         }
   *
   *         if (!croppedDstRect.intersect(SkIRect::MakeSize(fTextureProxy->dimensions()))) {
   *             // The replay translation can change on each insert, so subsequent replays may
   *             // actually intersect the copy rect.
   *             return Status::kSuccess;
   *         }
   *
   *         BufferTextureCopyData transformedCopyData = copyData;
   *         transformedCopyData.fBufferOffset +=
   *                 (croppedDstRect.y() - dstRect.y()) * copyData.fBufferRowBytes +
   *                 (croppedDstRect.x() - dstRect.x()) * fBytesPerPixel;
   *         transformedCopyData.fRect = croppedDstRect;
   *
   *         if (!commandBuffer->copyBufferToTexture(fBuffer,
   *                                                 fTextureProxy->refTexture(),
   *                                                 &transformedCopyData, 1)) {
   *             return Status::kFail;
   *         }
   *     }
   *
   *     // The conditional context will return false if the upload should not happen anymore. If there's
   *     // no context assume that the upload should always be executed on replay.
   *     if (!fConditionalContext || fConditionalContext->uploadSubmitted()) {
   *         return Status::kSuccess;
   *     } else {
   *         return Status::kDiscard;
   *     }
   * }
   * ```
   */
  public fun addCommand(
    context: Context?,
    commandBuffer: CommandBuffer?,
    replayData: Task.ReplayTargetData,
  ): Int {
    TODO("Implement addCommand")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * UploadInstance UploadInstance::Make(Recorder* recorder,
     *                                     sk_sp<TextureProxy> textureProxy,
     *                                     const SkColorInfo& srcColorInfo,
     *                                     const SkColorInfo& dstColorInfo,
     *                                     const UploadSource& source,
     *                                     const SkIRect& dstRect,
     *                                     std::unique_ptr<ConditionalUploadContext> condContext) {
     *     const Caps* caps = recorder->priv().caps();
     *     SkSpan<const MipLevel> levels = source.levels();
     *     uint32_t mipLevelCount = static_cast<uint32_t>(levels.size());
     *
     *     TArray<std::pair<size_t, size_t>> levelOffsetsAndRowBytes(mipLevelCount);
     *
     *     auto [combinedBufferSize, minAlignment] =
     *             compute_combined_buffer_size(caps,
     *                                          mipLevelCount,
     *                                          source.bytesPerPixel(),
     *                                          dstRect.size(),
     *                                          source.compression(),
     *                                          &levelOffsetsAndRowBytes);
     *     SkASSERT(combinedBufferSize);
     *
     *     UploadBufferManager* bufferMgr = recorder->priv().uploadBufferManager();
     *     auto [writer, bufferInfo] = bufferMgr->getTextureUploadWriter(combinedBufferSize, minAlignment);
     *     if (!writer) {
     *         SKGPU_LOG_W("Failed to get write-mapped buffer for texture upload of size %zu",
     *                     combinedBufferSize);
     *         return Invalid();
     *     }
     *
     *     UploadInstance upload{bufferInfo.fBuffer,
     *                           source.bytesPerPixel(),
     *                           std::move(textureProxy),
     *                           std::move(condContext)};
     *
     *     // Fill in copy data
     *     int32_t currentWidth = dstRect.width();
     *     int32_t currentHeight = dstRect.height();
     *     bool needsConversion = (srcColorInfo != dstColorInfo);
     *     for (uint32_t currentMipLevel = 0; currentMipLevel < mipLevelCount; currentMipLevel++) {
     *         const size_t trimRowBytes = currentWidth * source.bytesPerPixel();
     *         const size_t srcRowBytes = levels[currentMipLevel].fRowBytes;
     *         const auto [mipOffset, dstRowBytes] = levelOffsetsAndRowBytes[currentMipLevel];
     *
     *         // copy data into the buffer, skipping any trailing bytes
     *         const char* src = (const char*)levels[currentMipLevel].fPixels;
     *
     *         if (source.isRGB888Format()) {
     *             SkISize dims = {currentWidth, currentHeight};
     *             SkImageInfo srcImageInfo = SkImageInfo::Make(dims, srcColorInfo);
     *             SkImageInfo dstImageInfo = SkImageInfo::Make(dims, dstColorInfo);
     *
     *             const void* rgbConvertSrc = src;
     *             size_t rgbSrcRowBytes = srcRowBytes;
     *             SkAutoPixmapStorage temp;
     *             if (needsConversion) {
     *                 temp.alloc(dstImageInfo);
     *                 SkAssertResult(SkConvertPixels(dstImageInfo,
     *                                                temp.writable_addr(),
     *                                                temp.rowBytes(),
     *                                                srcImageInfo,
     *                                                src,
     *                                                srcRowBytes));
     *                 rgbConvertSrc = temp.addr();
     *                 rgbSrcRowBytes = temp.rowBytes();
     *             }
     *             writer.writeRGBFromRGBx(mipOffset,
     *                                     rgbConvertSrc,
     *                                     rgbSrcRowBytes,
     *                                     dstRowBytes,
     *                                     currentWidth,
     *                                     currentHeight);
     *         } else if (needsConversion) {
     *             SkISize dims = {currentWidth, currentHeight};
     *             SkImageInfo srcImageInfo = SkImageInfo::Make(dims, srcColorInfo);
     *             SkImageInfo dstImageInfo = SkImageInfo::Make(dims, dstColorInfo);
     *
     *             writer.convertAndWrite(
     *                     mipOffset, srcImageInfo, src, srcRowBytes, dstImageInfo, dstRowBytes);
     *         } else {
     *             writer.write(mipOffset, src, srcRowBytes, dstRowBytes, trimRowBytes, currentHeight);
     *         }
     *
     *         // For mipped data, the dstRect is always the full texture so we don't need to worry about
     *         // modifying the TL coord as it will always be 0,0,for all levels.
     *         upload.fCopyData.push_back({
     *             /*fBufferOffset=*/bufferInfo.fOffset + mipOffset,
     *             /*fBufferRowBytes=*/dstRowBytes,
     *             /*fRect=*/SkIRect::MakeXYWH(dstRect.left(), dstRect.top(), currentWidth, currentHeight),
     *             /*fMipLevel=*/currentMipLevel
     *         });
     *
     *         currentWidth = std::max(1, currentWidth / 2);
     *         currentHeight = std::max(1, currentHeight / 2);
     *     }
     *
     *     ATRACE_ANDROID_FRAMEWORK("Upload %sTexture [%dx%d]",
     *                              mipLevelCount > 1 ? "MipMap " : "",
     *                              dstRect.width(), dstRect.height());
     *
     *     return upload;
     * }
     * ```
     */
    public fun make(
      recorder: Recorder?,
      textureProxy: SkSp<TextureProxy>,
      srcColorInfo: SkColorInfo,
      dstColorInfo: SkColorInfo,
      source: UploadSource,
      dstRect: SkIRect,
      condContext: ConditionalUploadContext?,
    ): UploadInstance {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * UploadInstance UploadInstance::MakeCompressed(Recorder* recorder,
     *                                               sk_sp<TextureProxy> textureProxy,
     *                                               const UploadSource& source) {
     *     const Caps* caps = recorder->priv().caps();
     *     const SkISize dimensions = textureProxy->dimensions();
     *     SkSpan<const MipLevel> levels = source.levels();
     *     uint32_t mipLevelCount = static_cast<uint32_t>(levels.size());
     *
     *     TArray<std::pair<size_t, size_t>> levelOffsetsAndRowBytes(mipLevelCount);
     *     auto [combinedBufferSize, minAlignment] =
     *             compute_combined_buffer_size(caps,
     *                                          mipLevelCount,
     *                                          source.bytesPerPixel(),
     *                                          dimensions,
     *                                          source.compression(),
     *                                          &levelOffsetsAndRowBytes);
     *     SkASSERT(combinedBufferSize);
     *
     *     UploadBufferManager* bufferMgr = recorder->priv().uploadBufferManager();
     *     auto [writer, bufferInfo] = bufferMgr->getTextureUploadWriter(combinedBufferSize, minAlignment);
     *
     *     std::vector<BufferTextureCopyData> copyData(mipLevelCount);
     *
     *     if (!bufferInfo.fBuffer) {
     *         SKGPU_LOG_W("Failed to get write-mapped buffer for texture upload of size %zu",
     *                     combinedBufferSize);
     *         return Invalid();
     *     }
     *
     *     UploadInstance upload{bufferInfo.fBuffer, source.bytesPerPixel(), std::move(textureProxy)};
     *
     *     // Fill in copy data
     *     int32_t currentWidth = dimensions.width();
     *     int32_t currentHeight = dimensions.height();
     *     for (uint32_t currentMipLevel = 0; currentMipLevel < mipLevelCount; currentMipLevel++) {
     *         SkISize blockDimensions =
     *                 CompressedDimensionsInBlocks(source.compression(), {currentWidth, currentHeight});
     *         int32_t blockHeight = blockDimensions.height();
     *
     *         const size_t trimRowBytes = CompressedRowBytes(source.compression(), currentWidth);
     *         const size_t srcRowBytes = trimRowBytes;
     *         const auto [dstMipOffset, dstRowBytes] = levelOffsetsAndRowBytes[currentMipLevel];
     *
     *         // copy data into the buffer, skipping any trailing bytes
     *         const void* src = levels[currentMipLevel].fPixels;
     *
     *         writer.write(dstMipOffset, src, srcRowBytes, dstRowBytes, trimRowBytes, blockHeight);
     *
     *         int32_t copyWidth = currentWidth;
     *         int32_t copyHeight = currentHeight;
     *         if (caps->fullCompressedUploadSizeMustAlignToBlockDims()) {
     *             SkISize oneBlockDims = CompressedDimensions(source.compression(), {1, 1});
     *             copyWidth = SkAlignTo(copyWidth, oneBlockDims.fWidth);
     *             copyHeight = SkAlignTo(copyHeight, oneBlockDims.fHeight);
     *         }
     *
     *         upload.fCopyData.push_back({
     *             /*fBufferOffset=*/bufferInfo.fOffset + dstMipOffset,
     *             /*fBufferRowBytes=*/dstRowBytes,
     *             /*fRect=*/SkIRect::MakeXYWH(0, 0, copyWidth, copyHeight),
     *             /*fMipLevel=*/currentMipLevel
     *         });
     *
     *         currentWidth = std::max(1, currentWidth / 2);
     *         currentHeight = std::max(1, currentHeight / 2);
     *     }
     *
     *     ATRACE_ANDROID_FRAMEWORK("Upload Compressed %sTexture [%dx%d]",
     *                              mipLevelCount > 1 ? "MipMap " : "",
     *                              dimensions.width(),
     *                              dimensions.height());
     *
     *     return upload;
     * }
     * ```
     */
    public fun makeCompressed(
      recorder: Recorder?,
      textureProxy: SkSp<TextureProxy>,
      source: UploadSource,
    ): UploadInstance {
      TODO("Implement makeCompressed")
    }

    /**
     * C++ original:
     * ```cpp
     * static UploadInstance Invalid() { return {}; }
     * ```
     */
    public fun invalid(): UploadInstance {
      TODO("Implement invalid")
    }
  }
}
