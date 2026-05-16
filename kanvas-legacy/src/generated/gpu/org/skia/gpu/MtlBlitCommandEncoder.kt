package org.skia.gpu

import NSString
import kotlin.Char
import kotlin.Int
import kotlin.UByte
import kotlin.UInt
import kotlin.ULong
import org.skia.math.SkIPoint
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * class MtlBlitCommandEncoder : public Resource {
 * public:
 *     static sk_sp<MtlBlitCommandEncoder> Make(const SharedContext* sharedContext,
 *                                              id<MTLCommandBuffer> commandBuffer) {
 *         @autoreleasepool {
 *             // Adding a retain here to keep our own ref separate from the autorelease pool
 *             sk_cfp<id<MTLBlitCommandEncoder>> encoder =
 *                     sk_ret_cfp<id<MTLBlitCommandEncoder>>([commandBuffer blitCommandEncoder]);
 *             return sk_sp<MtlBlitCommandEncoder>(new MtlBlitCommandEncoder(sharedContext,
 *                                                                           std::move(encoder)));
 *         }
 *     }
 *
 *     const char* getResourceType() const override { return "Metal Blit Command Encoder"; }
 *
 *     void pushDebugGroup(NSString* string) {
 *         [(*fCommandEncoder) pushDebugGroup:string];
 *     }
 *     void popDebugGroup() {
 *         [(*fCommandEncoder) popDebugGroup];
 *     }
 * #ifdef SK_BUILD_FOR_MAC
 *     void synchronizeResource(id<MTLBuffer> buffer) {
 *         [(*fCommandEncoder) synchronizeResource: buffer];
 *     }
 * #endif
 *
 *     void fillBuffer(id<MTLBuffer> buffer, size_t bufferOffset, size_t bytes, uint8_t value) {
 *         [(*fCommandEncoder) fillBuffer:buffer
 *                                  range:NSMakeRange(bufferOffset, bytes)
 *                                  value:value];
 *     }
 *
 *     void copyFromTexture(id<MTLTexture> texture,
 *                          SkIRect srcRect,
 *                          id<MTLBuffer> buffer,
 *                          size_t bufferOffset,
 *                          size_t bufferRowBytes) {
 *         [(*fCommandEncoder) copyFromTexture: texture
 *                                 sourceSlice: 0
 *                                 sourceLevel: 0
 *                                sourceOrigin: MTLOriginMake(srcRect.left(), srcRect.top(), 0)
 *                                  sourceSize: MTLSizeMake(srcRect.width(), srcRect.height(), 1)
 *                                    toBuffer: buffer
 *                           destinationOffset: bufferOffset
 *                      destinationBytesPerRow: bufferRowBytes
 *                    destinationBytesPerImage: bufferRowBytes * srcRect.height()];
 *     }
 *
 *     void copyFromBuffer(id<MTLBuffer> buffer,
 *                         size_t bufferOffset,
 *                         size_t bufferRowBytes,
 *                         id<MTLTexture> texture,
 *                         SkIRect dstRect,
 *                         unsigned int dstLevel) {
 *         [(*fCommandEncoder) copyFromBuffer: buffer
 *                               sourceOffset: bufferOffset
 *                          sourceBytesPerRow: bufferRowBytes
 *                        sourceBytesPerImage: bufferRowBytes * dstRect.height()
 *                                 sourceSize: MTLSizeMake(dstRect.width(), dstRect.height(), 1)
 *                                  toTexture: texture
 *                           destinationSlice: 0
 *                           destinationLevel: dstLevel
 *                          destinationOrigin: MTLOriginMake(dstRect.left(), dstRect.top(), 0)];
 *     }
 *
 *     void copyTextureToTexture(id<MTLTexture> srcTexture,
 *                               SkIRect srcRect,
 *                               id<MTLTexture> dstTexture,
 *                               SkIPoint dstPoint,
 *                               int mipLevel) {
 *         [(*fCommandEncoder) copyFromTexture: srcTexture
 *                                 sourceSlice: 0
 *                                 sourceLevel: 0
 *                                sourceOrigin: MTLOriginMake(srcRect.x(), srcRect.y(), 0)
 *                                  sourceSize: MTLSizeMake(srcRect.width(), srcRect.height(), 1)
 *                                   toTexture: dstTexture
 *                            destinationSlice: 0
 *                            destinationLevel: mipLevel
 *                           destinationOrigin: MTLOriginMake(dstPoint.fX, dstPoint.fY, 0)];
 *     }
 *
 *     void copyBufferToBuffer(id<MTLBuffer> srcBuffer,
 *                             size_t srcOffset,
 *                             id<MTLBuffer> dstBuffer,
 *                             size_t dstOffset,
 *                             size_t size) {
 *         [(*fCommandEncoder) copyFromBuffer: srcBuffer
 *                               sourceOffset: srcOffset
 *                                   toBuffer: dstBuffer
 *                          destinationOffset: dstOffset
 *                                       size: size];
 *     }
 *
 *     void endEncoding() {
 *         [(*fCommandEncoder) endEncoding];
 *     }
 *
 * private:
 *     MtlBlitCommandEncoder(const SharedContext* sharedContext,
 *                           sk_cfp<id<MTLBlitCommandEncoder>> encoder)
 *             : Resource(sharedContext,
 *                        Ownership::kOwned,
 *                        /*gpuMemorySize=*/0)
 *             , fCommandEncoder(std::move(encoder)) {}
 *
 *     void freeGpuData() override {
 *         fCommandEncoder.reset();
 *     }
 *
 *     sk_cfp<id<MTLBlitCommandEncoder>> fCommandEncoder;
 * }
 * ```
 */
public open class MtlBlitCommandEncoder public constructor(
  sharedContext: SharedContext?,
) : Resource(TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_cfp<id<MTLBlitCommandEncoder>> fCommandEncoder
   * ```
   */
  private var fCommandEncoder: Int = TODO("Initialize fCommandEncoder")

  /**
   * C++ original:
   * ```cpp
   * const char* getResourceType() const override { return "Metal Blit Command Encoder"; }
   * ```
   */
  public override fun getResourceType(): Char {
    TODO("Implement getResourceType")
  }

  /**
   * C++ original:
   * ```cpp
   * void pushDebugGroup(NSString* string) {
   *         [(*fCommandEncoder) pushDebugGroup:string];
   *     }
   * ```
   */
  public fun pushDebugGroup(string: NSString?) {
    TODO("Implement pushDebugGroup")
  }

  /**
   * C++ original:
   * ```cpp
   * void popDebugGroup() {
   *         [(*fCommandEncoder) popDebugGroup];
   *     }
   * ```
   */
  public fun popDebugGroup() {
    TODO("Implement popDebugGroup")
  }

  /**
   * C++ original:
   * ```cpp
   * void fillBuffer(id<MTLBuffer> buffer, size_t bufferOffset, size_t bytes, uint8_t value) {
   *         [(*fCommandEncoder) fillBuffer:buffer
   *                                  range:NSMakeRange(bufferOffset, bytes)
   *                                  value:value];
   *     }
   * ```
   */
  public fun fillBuffer(
    bufferOffset: ULong,
    bytes: ULong,
    `value`: UByte,
  ) {
    TODO("Implement fillBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void copyFromTexture(id<MTLTexture> texture,
   *                          SkIRect srcRect,
   *                          id<MTLBuffer> buffer,
   *                          size_t bufferOffset,
   *                          size_t bufferRowBytes) {
   *         [(*fCommandEncoder) copyFromTexture: texture
   *                                 sourceSlice: 0
   *                                 sourceLevel: 0
   *                                sourceOrigin: MTLOriginMake(srcRect.left(), srcRect.top(), 0)
   *                                  sourceSize: MTLSizeMake(srcRect.width(), srcRect.height(), 1)
   *                                    toBuffer: buffer
   *                           destinationOffset: bufferOffset
   *                      destinationBytesPerRow: bufferRowBytes
   *                    destinationBytesPerImage: bufferRowBytes * srcRect.height()];
   *     }
   * ```
   */
  public fun copyFromTexture(
    srcRect: SkIRect,
    bufferOffset: ULong,
    bufferRowBytes: ULong,
  ) {
    TODO("Implement copyFromTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * void copyFromBuffer(id<MTLBuffer> buffer,
   *                         size_t bufferOffset,
   *                         size_t bufferRowBytes,
   *                         id<MTLTexture> texture,
   *                         SkIRect dstRect,
   *                         unsigned int dstLevel) {
   *         [(*fCommandEncoder) copyFromBuffer: buffer
   *                               sourceOffset: bufferOffset
   *                          sourceBytesPerRow: bufferRowBytes
   *                        sourceBytesPerImage: bufferRowBytes * dstRect.height()
   *                                 sourceSize: MTLSizeMake(dstRect.width(), dstRect.height(), 1)
   *                                  toTexture: texture
   *                           destinationSlice: 0
   *                           destinationLevel: dstLevel
   *                          destinationOrigin: MTLOriginMake(dstRect.left(), dstRect.top(), 0)];
   *     }
   * ```
   */
  public fun copyFromBuffer(
    bufferOffset: ULong,
    bufferRowBytes: ULong,
    dstRect: SkIRect,
    dstLevel: UInt,
  ) {
    TODO("Implement copyFromBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void copyTextureToTexture(id<MTLTexture> srcTexture,
   *                               SkIRect srcRect,
   *                               id<MTLTexture> dstTexture,
   *                               SkIPoint dstPoint,
   *                               int mipLevel) {
   *         [(*fCommandEncoder) copyFromTexture: srcTexture
   *                                 sourceSlice: 0
   *                                 sourceLevel: 0
   *                                sourceOrigin: MTLOriginMake(srcRect.x(), srcRect.y(), 0)
   *                                  sourceSize: MTLSizeMake(srcRect.width(), srcRect.height(), 1)
   *                                   toTexture: dstTexture
   *                            destinationSlice: 0
   *                            destinationLevel: mipLevel
   *                           destinationOrigin: MTLOriginMake(dstPoint.fX, dstPoint.fY, 0)];
   *     }
   * ```
   */
  public fun copyTextureToTexture(
    srcRect: SkIRect,
    dstPoint: SkIPoint,
    mipLevel: Int,
  ) {
    TODO("Implement copyTextureToTexture")
  }

  /**
   * C++ original:
   * ```cpp
   * void copyBufferToBuffer(id<MTLBuffer> srcBuffer,
   *                             size_t srcOffset,
   *                             id<MTLBuffer> dstBuffer,
   *                             size_t dstOffset,
   *                             size_t size) {
   *         [(*fCommandEncoder) copyFromBuffer: srcBuffer
   *                               sourceOffset: srcOffset
   *                                   toBuffer: dstBuffer
   *                          destinationOffset: dstOffset
   *                                       size: size];
   *     }
   * ```
   */
  public fun copyBufferToBuffer(
    srcOffset: ULong,
    dstOffset: ULong,
    size: ULong,
  ) {
    TODO("Implement copyBufferToBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void endEncoding() {
   *         [(*fCommandEncoder) endEncoding];
   *     }
   * ```
   */
  public fun endEncoding() {
    TODO("Implement endEncoding")
  }

  /**
   * C++ original:
   * ```cpp
   * void freeGpuData() override {
   *         fCommandEncoder.reset();
   *     }
   * ```
   */
  public override fun freeGpuData() {
    TODO("Implement freeGpuData")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<MtlBlitCommandEncoder> Make(const SharedContext* sharedContext,
     *                                              id<MTLCommandBuffer> commandBuffer) {
     *         @autoreleasepool {
     *             // Adding a retain here to keep our own ref separate from the autorelease pool
     *             sk_cfp<id<MTLBlitCommandEncoder>> encoder =
     *                     sk_ret_cfp<id<MTLBlitCommandEncoder>>([commandBuffer blitCommandEncoder]);
     *             return sk_sp<MtlBlitCommandEncoder>(new MtlBlitCommandEncoder(sharedContext,
     *                                                                           std::move(encoder)));
     *         }
     *     }
     * ```
     */
    public fun make(sharedContext: SharedContext?): Int {
      TODO("Implement make")
    }
  }
}
