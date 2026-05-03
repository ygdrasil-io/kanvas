package org.skia.gpu

import MTLRenderPassDescriptor
import NSString
import kotlin.Char
import kotlin.Int
import kotlin.IntArray
import kotlin.Unit
import undefined.MTLTriangleFillMode
import undefined.MTLViewport
import undefined.MTLWinding
import undefined.NSUInteger

/**
 * C++ original:
 * ```cpp
 * class MtlRenderCommandEncoder : public Resource {
 * public:
 *     static sk_sp<MtlRenderCommandEncoder> Make(const SharedContext* sharedContext,
 *                                                id<MTLCommandBuffer> commandBuffer,
 *                                                MTLRenderPassDescriptor* descriptor) {
 *         // Inserting a pool here so the autorelease occurs when we return and the
 *         // only remaining ref is the retain below.
 *         @autoreleasepool {
 *             // Adding a retain here to keep our own ref separate from the autorelease pool
 *             sk_cfp<id<MTLRenderCommandEncoder>> encoder =
 *                     sk_ret_cfp([commandBuffer renderCommandEncoderWithDescriptor:descriptor]);
 *             return sk_sp<MtlRenderCommandEncoder>(new MtlRenderCommandEncoder(sharedContext,
 *                                                                               std::move(encoder)));
 *         }
 *     }
 *
 *     const char* getResourceType() const override { return "Metal Render Command Encoder"; }
 *
 *     void setLabel(NSString* label) {
 *         [(*fCommandEncoder) setLabel:label];
 *     }
 *
 *     void pushDebugGroup(NSString* string) {
 *         [(*fCommandEncoder) pushDebugGroup:string];
 *     }
 *     void popDebugGroup() {
 *         [(*fCommandEncoder) popDebugGroup];
 *     }
 *     void insertDebugSignpost(NSString* string) {
 *         [(*fCommandEncoder) insertDebugSignpost:string];
 *     }
 *
 *     void setRenderPipelineState(id<MTLRenderPipelineState> pso) {
 *         if (fCurrentRenderPipelineState != pso) {
 *             [(*fCommandEncoder) setRenderPipelineState:pso];
 *             fCurrentRenderPipelineState = pso;
 *         }
 *     }
 *
 *     void setTriangleFillMode(MTLTriangleFillMode fillMode) {
 *         if (fCurrentTriangleFillMode != fillMode) {
 *             [(*fCommandEncoder) setTriangleFillMode:fillMode];
 *             fCurrentTriangleFillMode = fillMode;
 *         }
 *     }
 *
 *     void setFrontFacingWinding(MTLWinding winding) {
 *         [(*fCommandEncoder) setFrontFacingWinding:winding];
 *     }
 *
 *     void setViewport(const MTLViewport& viewport) {
 *         [(*fCommandEncoder) setViewport:viewport];
 *     }
 *
 *     void setVertexBuffer(id<MTLBuffer> buffer, NSUInteger offset, NSUInteger index) {
 *         SkASSERT(buffer != nil);
 *         SkASSERT(index < kMaxExpectedBuffers);
 *         [(*fCommandEncoder) setVertexBuffer:buffer
 *                                      offset:offset
 *                                     atIndex:index];
 *     }
 *
 *     void setFragmentBuffer(id<MTLBuffer> buffer, NSUInteger offset, NSUInteger index) {
 *         SkASSERT(buffer != nil);
 *         SkASSERT(index < kMaxExpectedBuffers);
 *         [(*fCommandEncoder) setFragmentBuffer:buffer
 *                                        offset:offset
 *                                       atIndex:index];
 *     }
 *
 *     void setVertexBytes(const void* bytes, NSUInteger length, NSUInteger index)
 *             SK_API_AVAILABLE(macos(10.11), ios(8.3), tvos(9.0)) {
 *         [(*fCommandEncoder) setVertexBytes:bytes
 *                                     length:length
 *                                    atIndex:index];
 *     }
 *     void setFragmentBytes(const void* bytes, NSUInteger length, NSUInteger index)
 *             SK_API_AVAILABLE(macos(10.11), ios(8.3), tvos(9.0)) {
 *         [(*fCommandEncoder) setFragmentBytes:bytes
 *                                       length:length
 *                                      atIndex:index];
 *     }
 *
 *     void setFragmentTexture(id<MTLTexture> texture, NSUInteger index) {
 *         SkASSERT(index < kMaxExpectedTextures);
 *         if (fCurrentTexture[index] != texture) {
 *             [(*fCommandEncoder) setFragmentTexture:texture
 *                                            atIndex:index];
 *             fCurrentTexture[index] = texture;
 *         }
 *     }
 *     void setFragmentSamplerState(id<MTLSamplerState> sampler, NSUInteger index) {
 *         SkASSERT(index < kMaxExpectedTextures);
 *         if (fCurrentSampler[index] != sampler) {
 *             [(*fCommandEncoder) setFragmentSamplerState:sampler
 *                                                 atIndex:index];
 *             fCurrentSampler[index] = sampler;
 *         }
 *     }
 *
 *     void setBlendColor(std::array<float, 4> blendConst) {
 *         [(*fCommandEncoder) setBlendColorRed: blendConst[0]
 *                                        green: blendConst[1]
 *                                         blue: blendConst[2]
 *                                        alpha: blendConst[3]];
 *     }
 *
 *     void setStencilReferenceValue(uint32_t referenceValue) {
 *         if (referenceValue != fCurrentStencilReferenceValue) {
 *             [(*fCommandEncoder) setStencilReferenceValue:referenceValue];
 *             fCurrentStencilReferenceValue = referenceValue;
 *         }
 *     }
 *     void setDepthStencilState(id<MTLDepthStencilState> depthStencilState) {
 *         if (depthStencilState != fCurrentDepthStencilState) {
 *             [(*fCommandEncoder) setDepthStencilState:depthStencilState];
 *             fCurrentDepthStencilState = depthStencilState;
 *         }
 *     }
 *
 *     void setScissorRect(const MTLScissorRect& scissorRect) {
 *         if (fCurrentScissorRect.x != scissorRect.x ||
 *             fCurrentScissorRect.y != scissorRect.y ||
 *             fCurrentScissorRect.width != scissorRect.width ||
 *             fCurrentScissorRect.height != scissorRect.height) {
 *             [(*fCommandEncoder) setScissorRect:scissorRect];
 *             fCurrentScissorRect = scissorRect;
 *         }
 *     }
 *
 *     void drawPrimitives(MTLPrimitiveType primitiveType, NSUInteger vertexStart,
 *                         NSUInteger vertexCount) {
 *         [(*fCommandEncoder) drawPrimitives:primitiveType
 *                                vertexStart:vertexStart
 *                                vertexCount:vertexCount];
 *     }
 *     void drawPrimitives(MTLPrimitiveType primitiveType, NSUInteger vertexStart,
 *                         NSUInteger vertexCount, NSUInteger instanceCount,
 *                         NSUInteger baseInstance)
 *             SK_API_AVAILABLE(macos(10.11), ios(9.0), tvos(9.0)) {
 *         [(*fCommandEncoder) drawPrimitives:primitiveType
 *                             vertexStart:vertexStart
 *                             vertexCount:vertexCount
 *                           instanceCount:instanceCount
 *                            baseInstance:baseInstance];
 *     }
 *     void drawPrimitives(MTLPrimitiveType primitiveType, id<MTLBuffer> indirectBuffer,
 *                         NSUInteger indirectBufferOffset)
 *             SK_API_AVAILABLE(macos(10.11), ios(9.0), tvos(9.0)) {
 *         [(*fCommandEncoder) drawPrimitives:primitiveType
 *                             indirectBuffer:indirectBuffer
 *                       indirectBufferOffset:indirectBufferOffset];
 *     }
 *
 *     void drawIndexedPrimitives(MTLPrimitiveType primitiveType, NSUInteger indexCount,
 *                                MTLIndexType indexType, id<MTLBuffer> indexBuffer,
 *                                NSUInteger indexBufferOffset) {
 *         [(*fCommandEncoder) drawIndexedPrimitives:primitiveType
 *                                     indexCount:indexCount
 *                                      indexType:indexType
 *                                    indexBuffer:indexBuffer
 *                              indexBufferOffset:indexBufferOffset];
 *     }
 *     void drawIndexedPrimitives(MTLPrimitiveType primitiveType, NSUInteger indexCount,
 *                                MTLIndexType indexType, id<MTLBuffer> indexBuffer,
 *                                NSUInteger indexBufferOffset,
 *                                NSUInteger instanceCount,
 *                                NSInteger baseVertex,
 *                                NSUInteger baseInstance)
 *             SK_API_AVAILABLE(macos(10.11), ios(9.0), tvos(9.0)) {
 *         [(*fCommandEncoder) drawIndexedPrimitives:primitiveType
 *                                     indexCount:indexCount
 *                                      indexType:indexType
 *                                    indexBuffer:indexBuffer
 *                              indexBufferOffset:indexBufferOffset
 *                                  instanceCount:instanceCount
 *                                     baseVertex:baseVertex
 *                                   baseInstance:baseInstance];
 *     }
 *     void drawIndexedPrimitives(MTLPrimitiveType primitiveType,
 *                                MTLIndexType indexType, id<MTLBuffer> indexBuffer,
 *                                NSUInteger indexBufferOffset, id<MTLBuffer> indirectBuffer,
 *                                NSUInteger indirectBufferOffset)
 *             SK_API_AVAILABLE(macos(10.11), ios(9.0), tvos(9.0)) {
 *         [(*fCommandEncoder) drawIndexedPrimitives:primitiveType
 *                                         indexType:indexType
 *                                       indexBuffer:indexBuffer
 *                                 indexBufferOffset:indexBufferOffset
 *                                    indirectBuffer:indirectBuffer
 *                              indirectBufferOffset:indirectBufferOffset];
 *     }
 *
 *     void endEncoding() {
 *         [(*fCommandEncoder) endEncoding];
 *     }
 *
 * private:
 *     inline static constexpr int kMaxExpectedBuffers = 6;
 *     inline static constexpr int kMaxExpectedTextures = 16;
 *
 *     MtlRenderCommandEncoder(const SharedContext* sharedContext,
 *                             sk_cfp<id<MTLRenderCommandEncoder>> encoder)
 *             : Resource(sharedContext,
 *                        Ownership::kOwned,
 *                        /*gpuMemorySize=*/0)
 *             , fCommandEncoder(std::move(encoder)) {
 *         for (int i = 0; i < kMaxExpectedTextures; i++) {
 *             fCurrentTexture[i] = nil;
 *             fCurrentSampler[i] = nil;
 *         }
 *     }
 *
 *     void freeGpuData() override {
 *         fCommandEncoder.reset();
 *     }
 *
 *     sk_cfp<id<MTLRenderCommandEncoder>> fCommandEncoder;
 *
 *     id<MTLRenderPipelineState> fCurrentRenderPipelineState = nil;
 *     id<MTLDepthStencilState> fCurrentDepthStencilState = nil;
 *     uint32_t fCurrentStencilReferenceValue = 0; // Metal default value
 *
 *     id<MTLTexture> fCurrentTexture[kMaxExpectedTextures];
 *     id<MTLSamplerState> fCurrentSampler[kMaxExpectedTextures];
 *
 *     MTLScissorRect fCurrentScissorRect = { 0, 0, 0, 0 };
 *     MTLTriangleFillMode fCurrentTriangleFillMode = (MTLTriangleFillMode)-1;
 * }
 * ```
 */
public open class MtlRenderCommandEncoder public constructor(
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
   * sk_cfp<id<MTLRenderCommandEncoder>> fCommandEncoder
   * ```
   */
  private var fCurrentRenderPipelineState: Int = TODO("Initialize fCurrentRenderPipelineState")

  /**
   * C++ original:
   * ```cpp
   * id<MTLRenderPipelineState> fCurrentRenderPipelineState
   * ```
   */
  private var fCurrentDepthStencilState: Int = TODO("Initialize fCurrentDepthStencilState")

  /**
   * C++ original:
   * ```cpp
   * id<MTLDepthStencilState> fCurrentDepthStencilState
   * ```
   */
  private var fCurrentStencilReferenceValue: Int = TODO("Initialize fCurrentStencilReferenceValue")

  /**
   * C++ original:
   * ```cpp
   * uint32_t fCurrentStencilReferenceValue
   * ```
   */
  private var fCurrentTexture: IntArray = TODO("Initialize fCurrentTexture")

  /**
   * C++ original:
   * ```cpp
   * id<MTLTexture> fCurrentTexture[kMaxExpectedTextures]
   * ```
   */
  private var fCurrentSampler: IntArray = TODO("Initialize fCurrentSampler")

  /**
   * C++ original:
   * ```cpp
   * id<MTLSamplerState> fCurrentSampler[kMaxExpectedTextures]
   * ```
   */
  private var fCurrentScissorRect: Int = TODO("Initialize fCurrentScissorRect")

  /**
   * C++ original:
   * ```cpp
   * MTLScissorRect fCurrentScissorRect
   * ```
   */
  private var fCurrentTriangleFillMode: Int = TODO("Initialize fCurrentTriangleFillMode")

  /**
   * C++ original:
   * ```cpp
   * const char* getResourceType() const override { return "Metal Render Command Encoder"; }
   * ```
   */
  public override fun getResourceType(): Char {
    TODO("Implement getResourceType")
  }

  /**
   * C++ original:
   * ```cpp
   * void setLabel(NSString* label) {
   *         [(*fCommandEncoder) setLabel:label];
   *     }
   * ```
   */
  public override fun setLabel(label: NSString?) {
    TODO("Implement setLabel")
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
   * void insertDebugSignpost(NSString* string) {
   *         [(*fCommandEncoder) insertDebugSignpost:string];
   *     }
   * ```
   */
  public fun insertDebugSignpost(string: NSString?) {
    TODO("Implement insertDebugSignpost")
  }

  /**
   * C++ original:
   * ```cpp
   * void setRenderPipelineState(id<MTLRenderPipelineState> pso) {
   *         if (fCurrentRenderPipelineState != pso) {
   *             [(*fCommandEncoder) setRenderPipelineState:pso];
   *             fCurrentRenderPipelineState = pso;
   *         }
   *     }
   * ```
   */
  public fun setRenderPipelineState() {
    TODO("Implement setRenderPipelineState")
  }

  /**
   * C++ original:
   * ```cpp
   * void setTriangleFillMode(MTLTriangleFillMode fillMode) {
   *         if (fCurrentTriangleFillMode != fillMode) {
   *             [(*fCommandEncoder) setTriangleFillMode:fillMode];
   *             fCurrentTriangleFillMode = fillMode;
   *         }
   *     }
   * ```
   */
  public fun setTriangleFillMode(fillMode: MTLTriangleFillMode) {
    TODO("Implement setTriangleFillMode")
  }

  /**
   * C++ original:
   * ```cpp
   * void setFrontFacingWinding(MTLWinding winding) {
   *         [(*fCommandEncoder) setFrontFacingWinding:winding];
   *     }
   * ```
   */
  public fun setFrontFacingWinding(winding: MTLWinding) {
    TODO("Implement setFrontFacingWinding")
  }

  /**
   * C++ original:
   * ```cpp
   * void setViewport(const MTLViewport& viewport) {
   *         [(*fCommandEncoder) setViewport:viewport];
   *     }
   * ```
   */
  public fun setViewport(viewport: MTLViewport) {
    TODO("Implement setViewport")
  }

  /**
   * C++ original:
   * ```cpp
   * void setVertexBuffer(id<MTLBuffer> buffer, NSUInteger offset, NSUInteger index) {
   *         SkASSERT(buffer != nil);
   *         SkASSERT(index < kMaxExpectedBuffers);
   *         [(*fCommandEncoder) setVertexBuffer:buffer
   *                                      offset:offset
   *                                     atIndex:index];
   *     }
   * ```
   */
  public fun setVertexBuffer(offset: NSUInteger, index: NSUInteger) {
    TODO("Implement setVertexBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void setFragmentBuffer(id<MTLBuffer> buffer, NSUInteger offset, NSUInteger index) {
   *         SkASSERT(buffer != nil);
   *         SkASSERT(index < kMaxExpectedBuffers);
   *         [(*fCommandEncoder) setFragmentBuffer:buffer
   *                                        offset:offset
   *                                       atIndex:index];
   *     }
   * ```
   */
  public fun setFragmentBuffer(offset: NSUInteger, index: NSUInteger) {
    TODO("Implement setFragmentBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void setVertexBytes(const void* bytes, NSUInteger length, NSUInteger index)
   * ```
   */
  public fun setVertexBytes(
    bytes: Unit?,
    length: NSUInteger,
    index: NSUInteger,
  ) {
    TODO("Implement setVertexBytes")
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
    private val kMaxExpectedTextures: Int = TODO("Initialize kMaxExpectedTextures")

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<MtlRenderCommandEncoder> Make(const SharedContext* sharedContext,
     *                                                id<MTLCommandBuffer> commandBuffer,
     *                                                MTLRenderPassDescriptor* descriptor) {
     *         // Inserting a pool here so the autorelease occurs when we return and the
     *         // only remaining ref is the retain below.
     *         @autoreleasepool {
     *             // Adding a retain here to keep our own ref separate from the autorelease pool
     *             sk_cfp<id<MTLRenderCommandEncoder>> encoder =
     *                     sk_ret_cfp([commandBuffer renderCommandEncoderWithDescriptor:descriptor]);
     *             return sk_sp<MtlRenderCommandEncoder>(new MtlRenderCommandEncoder(sharedContext,
     *                                                                               std::move(encoder)));
     *         }
     *     }
     * ```
     */
    public fun make(sharedContext: SharedContext?, descriptor: MTLRenderPassDescriptor?): Int {
      TODO("Implement make")
    }
  }
}
