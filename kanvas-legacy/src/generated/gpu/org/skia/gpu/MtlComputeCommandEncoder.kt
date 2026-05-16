package org.skia.gpu

import NSString
import kotlin.Char
import kotlin.Int
import kotlin.IntArray
import undefined.NSUInteger

/**
 * C++ original:
 * ```cpp
 * class MtlComputeCommandEncoder : public Resource {
 * public:
 *     static sk_sp<MtlComputeCommandEncoder> Make(const SharedContext* sharedContext,
 *                                                 id<MTLCommandBuffer> commandBuffer) {
 *         // Inserting a pool here so the autorelease occurs when we return and the
 *         // only remaining ref is the retain below.
 *         @autoreleasepool {
 *             // Adding a retain here to keep our own ref separate from the autorelease pool
 *             sk_cfp<id<MTLComputeCommandEncoder>> encoder =
 *                     sk_ret_cfp([commandBuffer computeCommandEncoder]);
 *
 *             // TODO(armansito): Support concurrent dispatch of compute passes using
 *             // MTLDispatchTypeConcurrent on macOS 10.14+ and iOS 12.0+.
 *             return sk_sp<MtlComputeCommandEncoder>(
 *                     new MtlComputeCommandEncoder(sharedContext, std::move(encoder)));
 *         }
 *     }
 *
 *     const char* getResourceType() const override { return "Metal Compute Command Encoder"; }
 *
 *     void setLabel(NSString* label) { [(*fCommandEncoder) setLabel:label]; }
 *
 *     void pushDebugGroup(NSString* string) { [(*fCommandEncoder) pushDebugGroup:string]; }
 *     void popDebugGroup() { [(*fCommandEncoder) popDebugGroup]; }
 *     void insertDebugSignpost(NSString* string) { [(*fCommandEncoder) insertDebugSignpost:string]; }
 *
 *     void setComputePipelineState(id<MTLComputePipelineState> pso) {
 *         if (fCurrentComputePipelineState != pso) {
 *             [(*fCommandEncoder) setComputePipelineState:pso];
 *             fCurrentComputePipelineState = pso;
 *         }
 *     }
 *
 *     void setBuffer(id<MTLBuffer> buffer, NSUInteger offset, NSUInteger index) {
 *         SkASSERT(buffer != nil);
 *         SkASSERT(index < kMaxExpectedBuffers);
 *         if (@available(macOS 10.11, iOS 8.3, tvOS 9.0, *)) {
 *             if (fBuffers[index] == buffer) {
 *                 this->setBufferOffset(offset, index);
 *                 return;
 *             }
 *         }
 *         if (fBuffers[index] != buffer || fBufferOffsets[index] != offset) {
 *             [(*fCommandEncoder) setBuffer:buffer offset:offset atIndex:index];
 *             fBuffers[index] = buffer;
 *             fBufferOffsets[index] = offset;
 *         }
 *     }
 *
 *     void setBufferOffset(NSUInteger offset, NSUInteger index)
 *             SK_API_AVAILABLE(macos(10.11), ios(8.3), tvos(9.0)) {
 *         SkASSERT(index < kMaxExpectedBuffers);
 *         if (fBufferOffsets[index] != offset) {
 *             [(*fCommandEncoder) setBufferOffset:offset atIndex:index];
 *             fBufferOffsets[index] = offset;
 *         }
 *     }
 *
 *     void setTexture(id<MTLTexture> texture, NSUInteger index) {
 *         SkASSERT(index < kMaxExpectedTextures);
 *         if (fTextures[index] != texture) {
 *             [(*fCommandEncoder) setTexture:texture atIndex:index];
 *             fTextures[index] = texture;
 *         }
 *     }
 *
 *     void setSamplerState(id<MTLSamplerState> sampler, NSUInteger index) {
 *         SkASSERT(index < kMaxExpectedTextures);
 *         if (fSamplers[index] != sampler) {
 *             [(*fCommandEncoder) setSamplerState:sampler atIndex:index];
 *             fSamplers[index] = sampler;
 *         }
 *     }
 *
 *     // `length` must be 16-byte aligned
 *     void setThreadgroupMemoryLength(NSUInteger length, NSUInteger index) {
 *         SkASSERT(length % 16 == 0);
 *         [(*fCommandEncoder) setThreadgroupMemoryLength:length atIndex:index];
 *     }
 *
 *     void dispatchThreadgroups(const WorkgroupSize& globalSize, const WorkgroupSize& localSize) {
 *         MTLSize threadgroupCount =
 *                 MTLSizeMake(globalSize.fWidth, globalSize.fHeight, globalSize.fDepth);
 *         MTLSize threadsPerThreadgroup =
 *                 MTLSizeMake(localSize.fWidth, localSize.fHeight, localSize.fDepth);
 *         [(*fCommandEncoder) dispatchThreadgroups:threadgroupCount
 *                            threadsPerThreadgroup:threadsPerThreadgroup];
 *     }
 *
 *     void dispatchThreadgroupsWithIndirectBuffer(id<MTLBuffer> indirectBuffer,
 *                                                 NSUInteger offset,
 *                                                 const WorkgroupSize& localSize) {
 *         MTLSize threadsPerThreadgroup =
 *                 MTLSizeMake(localSize.fWidth, localSize.fHeight, localSize.fDepth);
 *         [(*fCommandEncoder) dispatchThreadgroupsWithIndirectBuffer:indirectBuffer
 *                                               indirectBufferOffset:offset
 *                                              threadsPerThreadgroup:threadsPerThreadgroup];
 *     }
 *
 *     void endEncoding() { [(*fCommandEncoder) endEncoding]; }
 *
 * private:
 *     inline static constexpr int kMaxExpectedBuffers = 16;
 *     inline static constexpr int kMaxExpectedTextures = 16;
 *
 *     MtlComputeCommandEncoder(const SharedContext* sharedContext,
 *                              sk_cfp<id<MTLComputeCommandEncoder>> encoder)
 *             : Resource(sharedContext,
 *                        Ownership::kOwned,
 *                        /*gpuMemorySize=*/0)
 *             , fCommandEncoder(std::move(encoder)) {
 *         for (int i = 0; i < kMaxExpectedBuffers; i++) {
 *             fBuffers[i] = nil;
 *         }
 *         for (int i = 0; i < kMaxExpectedTextures; i++) {
 *             fTextures[i] = nil;
 *             fSamplers[i] = nil;
 *         }
 *     }
 *
 *     void freeGpuData() override { fCommandEncoder.reset(); }
 *
 *     sk_cfp<id<MTLComputeCommandEncoder>> fCommandEncoder;
 *
 *     id<MTLComputePipelineState> fCurrentComputePipelineState = nil;
 *
 *     id<MTLBuffer> fBuffers[kMaxExpectedBuffers];
 *     NSUInteger    fBufferOffsets[kMaxExpectedBuffers];
 *
 *     id<MTLTexture>      fTextures[kMaxExpectedTextures];
 *     id<MTLSamplerState> fSamplers[kMaxExpectedTextures];
 * }
 * ```
 */
public open class MtlComputeCommandEncoder public constructor(
  sharedContext: SharedContext?,
) : Resource(TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kMaxExpectedTextures = 16
   * ```
   */
  private var fCommandEncoder: Int = TODO("Initialize fCommandEncoder")

  /**
   * C++ original:
   * ```cpp
   * sk_cfp<id<MTLComputeCommandEncoder>> fCommandEncoder
   * ```
   */
  private var fCurrentComputePipelineState: Int = TODO("Initialize fCurrentComputePipelineState")

  /**
   * C++ original:
   * ```cpp
   * id<MTLComputePipelineState> fCurrentComputePipelineState
   * ```
   */
  private var fBuffers: Int = TODO("Initialize fBuffers")

  /**
   * C++ original:
   * ```cpp
   * id<MTLBuffer> fBuffers
   * ```
   */
  private var fBufferOffsets: Int = TODO("Initialize fBufferOffsets")

  /**
   * C++ original:
   * ```cpp
   * NSUInteger    fBufferOffsets
   * ```
   */
  private var fTextures: IntArray = TODO("Initialize fTextures")

  /**
   * C++ original:
   * ```cpp
   * id<MTLTexture>      fTextures[kMaxExpectedTextures]
   * ```
   */
  private var fSamplers: IntArray = TODO("Initialize fSamplers")

  /**
   * C++ original:
   * ```cpp
   * const char* getResourceType() const override { return "Metal Compute Command Encoder"; }
   * ```
   */
  public override fun getResourceType(): Char {
    TODO("Implement getResourceType")
  }

  /**
   * C++ original:
   * ```cpp
   * void setLabel(NSString* label) { [(*fCommandEncoder) setLabel:label]; }
   * ```
   */
  public override fun setLabel(label: NSString?) {
    TODO("Implement setLabel")
  }

  /**
   * C++ original:
   * ```cpp
   * void pushDebugGroup(NSString* string) { [(*fCommandEncoder) pushDebugGroup:string]; }
   * ```
   */
  public fun pushDebugGroup(string: NSString?) {
    TODO("Implement pushDebugGroup")
  }

  /**
   * C++ original:
   * ```cpp
   * void popDebugGroup() { [(*fCommandEncoder) popDebugGroup]; }
   * ```
   */
  public fun popDebugGroup() {
    TODO("Implement popDebugGroup")
  }

  /**
   * C++ original:
   * ```cpp
   * void insertDebugSignpost(NSString* string) { [(*fCommandEncoder) insertDebugSignpost:string]; }
   * ```
   */
  public fun insertDebugSignpost(string: NSString?) {
    TODO("Implement insertDebugSignpost")
  }

  /**
   * C++ original:
   * ```cpp
   * void setComputePipelineState(id<MTLComputePipelineState> pso) {
   *         if (fCurrentComputePipelineState != pso) {
   *             [(*fCommandEncoder) setComputePipelineState:pso];
   *             fCurrentComputePipelineState = pso;
   *         }
   *     }
   * ```
   */
  public fun setComputePipelineState() {
    TODO("Implement setComputePipelineState")
  }

  /**
   * C++ original:
   * ```cpp
   * void setBuffer(id<MTLBuffer> buffer, NSUInteger offset, NSUInteger index) {
   *         SkASSERT(buffer != nil);
   *         SkASSERT(index < kMaxExpectedBuffers);
   *         if (@available(macOS 10.11, iOS 8.3, tvOS 9.0, *)) {
   *             if (fBuffers[index] == buffer) {
   *                 this->setBufferOffset(offset, index);
   *                 return;
   *             }
   *         }
   *         if (fBuffers[index] != buffer || fBufferOffsets[index] != offset) {
   *             [(*fCommandEncoder) setBuffer:buffer offset:offset atIndex:index];
   *             fBuffers[index] = buffer;
   *             fBufferOffsets[index] = offset;
   *         }
   *     }
   * ```
   */
  public fun setBuffer(offset: NSUInteger, index: NSUInteger) {
    TODO("Implement setBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void setBufferOffset(NSUInteger offset, NSUInteger index)
   * ```
   */
  public fun setBufferOffset(offset: NSUInteger, index: NSUInteger) {
    TODO("Implement setBufferOffset")
  }

  /**
   * C++ original:
   * ```cpp
   * void freeGpuData() override { fCommandEncoder.reset(); }
   * ```
   */
  public override fun freeGpuData() {
    TODO("Implement freeGpuData")
  }

  public companion object {
    private val kMaxExpectedTextures: Int = TODO("Initialize kMaxExpectedTextures")

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<MtlComputeCommandEncoder> Make(const SharedContext* sharedContext,
     *                                                 id<MTLCommandBuffer> commandBuffer) {
     *         // Inserting a pool here so the autorelease occurs when we return and the
     *         // only remaining ref is the retain below.
     *         @autoreleasepool {
     *             // Adding a retain here to keep our own ref separate from the autorelease pool
     *             sk_cfp<id<MTLComputeCommandEncoder>> encoder =
     *                     sk_ret_cfp([commandBuffer computeCommandEncoder]);
     *
     *             // TODO(armansito): Support concurrent dispatch of compute passes using
     *             // MTLDispatchTypeConcurrent on macOS 10.14+ and iOS 12.0+.
     *             return sk_sp<MtlComputeCommandEncoder>(
     *                     new MtlComputeCommandEncoder(sharedContext, std::move(encoder)));
     *         }
     *     }
     * ```
     */
    public fun make(sharedContext: SharedContext?): Int {
      TODO("Implement make")
    }
  }
}
