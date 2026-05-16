package org.skia.gpu

import DrawPassCommands.List
import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import org.skia.core.SkEnumBitMask
import undefined.Appender

/**
 * C++ original:
 * ```cpp
 * class DrawWriter {
 * public:
 *     // NOTE: This constructor creates a writer that defaults 0 vertex and instance stride, so
 *     // 'newPipelineState()' must be called once the pipeline properties are known before it's used.
 *     DrawWriter(DrawPassCommands::List*, DrawBufferManager*);
 *
 *     // Cannot move or copy
 *     DrawWriter(const DrawWriter&) = delete;
 *     DrawWriter(DrawWriter&&) = delete;
 *
 *     // flush() should be called before the writer is destroyed
 *     ~DrawWriter() { SkASSERT(fPendingCount == 0); }
 *
 *     DrawBufferManager* bufferManager() { return fManager; }
 *
 *     // Issue draw calls for any pending vertex and instance data collected by the writer.
 *     // Use either flush() or newDynamicState() based on context and readability.
 *     void flush() { this->flushInternal(this->getDefaultAppendBinding()); }
 *     void newDynamicState() { this->flush(); }
 *
 *     // Notify the DrawWriter that a new pipeline needs to be bound, providing the primitive type,
 *     // attribute strides, and render state of the new pipeline. This issues draw calls for pending
 *     // data that relied on the old pipeline, so this must be called *before* binding new pipeline.
 *     void newPipelineState(PrimitiveType type,
 *                           size_t staticStride,
 *                           size_t appendStride,
 *                           SkEnumBitMask<RenderStateFlags> newRenderState,
 *                           BarrierType barrierType) {
 *         this->flush();
 *
 *         // Once flushed, any pending data must have been drawn.
 *         SkASSERT(fPendingCount == 0);
 *
 *         fPrimitiveType = type;
 *         fStaticStride = SkTo<uint32_t>(staticStride);
 *         fAppendStride = SkTo<uint32_t>(appendStride);
 *         fRenderState = newRenderState;
 *
 *         // ARM hardware(b/399631317): On a new pipeline, the initial offset when appending
 *         // vertices must be 4-count aligned, otherwise align to the stride so that access can use
 *         // the baseInstance parameter of draw calls.
 *         const uint32_t baseAlign = newRenderState & RenderStateFlags::kAppendVertices ?
 *                 4 * fAppendStride : fAppendStride;
 *
 *         // Initializes fAppend to hold the aligned offset within fCurrentBuffer and prepares
 *         // fCurrentBuffer for later reservations. Passing count=0 here means no actual space
 *         // (besides alignment padding) is used up until appendMappedWithStride is called.
 *         std::tie(std::ignore, fAppend) = fCurrentBuffer.getMappedSubrange(/*count=*/0,
 *                                                                           fAppendStride,
 *                                                                           baseAlign);
 *
 *
 *         // Assign the barrier type. If a valid value, then the DrawWriter will append
 *         // AddBarrier commands of the indicated type prior to appending any draw commands used with
 *         // this pipeline.
 *         fBarrierToIssueBeforeDraws = barrierType;
 *     }
 *
 * #ifdef SK_DEBUG
 *     // Query current pipeline state for validation
 *     uint32_t      appendStride()  const { return fAppendStride;  }
 *     uint32_t      staticStride()  const { return fStaticStride;  }
 *     PrimitiveType primitiveType() const { return fPrimitiveType; }
 * #endif
 *
 *     // Collects new vertex data for a call to CommandBuffer::draw(). Automatically accumulates
 *     // vertex data into a buffer, issuing draw and bind calls as needed when a new buffer is
 *     // required, so that it is seamless to the caller. The draws do not use instances or indices.
 *     //
 *     // Usage (assuming writer has already had 'newPipelineState()' called with correct strides):
 *     //    DrawWriter::Vertices verts{writer};
 *     //    verts.append(n) << x << y << ...;
 *     //
 *     // This should not be used when the vertex stride is 0.
 *     class Vertices;
 *
 *     // Collects new instance data for a call to CommandBuffer::drawInstanced() or
 *     // drawIndexedInstanced(). The specific draw call that's issued depends on if a non-null index
 *     // buffer is provided for the template. Like DrawWriter::Vertices, this automatically merges
 *     // the appended data into as few buffer binds and draw calls as possible, while remaining
 *     // seamless to the caller.
 *     //
 *     // Usage for drawInstanced (assuming writer has correct strides):
 *     //    DrawWriter::Instances instances{writer, fixedVerts, {}, fixedVertexCount};
 *     //    instances.append(n) << foo << bar << ...;
 *     //
 *     // Usage for drawIndexedInstanced:
 *     //    DrawWriter::Instances instances{writer, fixedVerts, fixedIndices, fixedIndexCount};
 *     //    instances.append(n) << foo << bar << ...;
 *     //
 *     // This should not be used when the instance stride is 0. However, the fixed vertex buffer can
 *     // be null (or have a stride of 0) if the vertex shader only relies on the vertex ID and no
 *     // other per-vertex data.
 *     class Instances;
 *
 *     // Collects new instance data for a call to CommandBuffer::drawInstanced() or
 *     // drawIndexedInstanced() (depending on presence of index data in the template). Unlike the
 *     // Instances mode, the template's index or vertex count is not provided at the time of creation.
 *     // Instead, DynamicInstances can be used with pipeline programs that can have a flexible number
 *     // of vertices per instance. Appended instances specify a proxy object that can be converted
 *     // to the minimum index/vertex count they must be drawn with; but if they are later batched with
 *     // instances that would use more, the pipeline's vertex shader knows how to handle it.
 *     //
 *     // The proxy object serves as a useful point of indirection when the actual index count is
 *     // expensive to compute, but can be derived from correlated geometric properties. The proxy
 *     // can store those properties and accumulate a "worst-case" and then calculate the index count
 *     // when DrawWriter has to flush.
 *     //
 *     // The VertexCountProxy type must provide:
 *     //  - a default constructor and copy assignment, where the initial value represents the minimum
 *     //    supported vertex count.
 *     //  - an 'unsigned int' operator that converts the proxy to the actual index count that is
 *     //    needed in order to dispatch a draw call.
 *     //  - operator <<(const V&) where V is any type the caller wants to pass to append() that
 *     //    represents the proxy for the about-to-be-written instances. This operator then updates its
 *     //    internal state to represent the worst case between what had previously been recorded and
 *     //    the latest V value.
 *     //
 *     // Usage for drawInstanced (fixedIndices == {}) or drawIndexedInstanced:
 *     //    DrawWriter::DynamicInstances<ProxyType> instances(writer, fixedVerts, fixedIndices);
 *     //    instances.append(minIndexProxy1, n1) << ...;
 *     //    instances.append(minIndexProxy2, n2) << ...;
 *     //
 *     // In this example, if the two sets of instances were contiguous, a single draw call with
 *     // (n1 + n2) instances would still be made using max(minIndexCount1, minIndexCount2) as the
 *     // index/vertex count, 'minIndexCountX' was derived from 'minIndexProxyX'. If the available
 *     // vertex data from the DrawBufferManager forced a flush after the first, then the second would
 *     // use minIndexCount2 unless a subsequent compatible DynamicInstances template appended more
 *     // contiguous data.
 *     template <typename VertexCountProxy>
 *     class DynamicInstances;
 *
 *     // Issues draws with fully specified data. This can be used when all instance data has already
 *     // been written to known buffers, or when the vertex shader only depends on the vertex or
 *     // instance IDs.
 *     //
 *     // This will not merge with any already appended instance or vertex data, pending data is issued
 *     // in its own draw call first. These are currently unused.
 *     void draw(BindBufferInfo vertices, unsigned int vertexCount) {
 *         this->bindAndFlush({}, {}, vertices, 0, vertexCount);
 *     }
 *     void drawIndexed(BindBufferInfo vertices, BindBufferInfo indices, unsigned int indexCount) {
 *         this->bindAndFlush(vertices, indices, {}, 0, indexCount);
 *     }
 *     void drawInstanced(BindBufferInfo vertices, unsigned int vertexCount,
 *                        BindBufferInfo instances, unsigned int instanceCount) {
 *         SkASSERT(vertexCount > 0);
 *         this->bindAndFlush(vertices, {}, instances, vertexCount, instanceCount);
 *     }
 *     void drawIndexedInstanced(BindBufferInfo vertices, BindBufferInfo indices,
 *                               unsigned int indexCount, BindBufferInfo instances,
 *                               unsigned int instanceCount) {
 *         SkASSERT(indexCount > 0);
 *         this->bindAndFlush(vertices, indices, instances, indexCount, instanceCount);
 *     }
 *
 * #if defined(GPU_TEST_UTILS)
 *     BindBufferInfo getLastAppendedBuffer() { return this->getDefaultAppendBinding(); }
 * #endif
 *
 * private:
 *     // Both of these pointers must outlive the DrawWriter.
 *     DrawPassCommands::List* fCommandList;
 *     DrawBufferManager* fManager;
 *
 *     BufferSubAllocator fCurrentBuffer;
 *     SkAutoMalloc fFailureStorage; // storage address for VertexWriter when GPU buffer mapping fails
 *
 *     // Current operating mode of the DrawWriter, dictating how draw data is provided and
 *     // interpreted. Determines whether fPendingCount refers to vertices or instances, and which
 *     // buffer (fVertices or fInstances) serves as the append target. Set via newPipelineState().
 *     SkEnumBitMask<RenderStateFlags> fRenderState;
 *     PrimitiveType fPrimitiveType;
 *     BarrierType fBarrierToIssueBeforeDraws = BarrierType::kNone;
 *     uint32_t fStaticStride;
 *     uint32_t fAppendStride;
 *
 *     // - fAppend: Holds buffer information for data that is generated and appended during the
 *     //            drawPass. The data can be either vertex (kAppendVertices) or instance
 *     //            (kAppendInstances/kAppendDynamicInstances) data.
 *     // - fStatic: Holds buffer information that does not change between invocations of a renderstep.
 *     //            Currently this only holds vertex data, but this could change in the future.
 *     // - Indices: Defines the (for now static) buffer used for any kind of index drawing. A
 *     //            renderstep with a valid index buffer implies that it will be performing indexed
 *     //            drawing.
 *     BindBufferInfo fAppend;
 *     BindBufferInfo fStatic;
 *     BindBufferInfo fIndices;
 *     // These track the buffers *last bound* by the command list. Used to ensure minimal binding.
 *     BindBufferInfo fBoundAppend;
 *     BindBufferInfo fBoundStatic;
 *     BindBufferInfo fBoundIndices;
 *
 *     // Per-instance count for instanced draws (vertex count if no index buffer, index count
 *     // otherwise).
 *     // - For fixed instancing (kAppendInstances): Represents the constant vertex/index count per
 *     //   instance.
 *     // - For dynamic instancing (kAppendDynamicInstances): Represents the *maximum* vertex/index
 *     //   count required across the currently accumulated batch of instances (updated via max()).
 *     // - Not used (remains 0) for non-instanced draws (kAppendVertices) or direct draw calls.
 *     uint32_t fTemplateCount;
 *
 *     // Number of items (vertices or instances, depending on fRenderState) that have been appended
 *     // via an Appender (Vertices, Instances, DynamicInstances) but not yet issued in a draw call.
 *     // Reset to 0 after a flush().
 *     uint32_t fPendingCount;
 *
 *     BindBufferInfo getDefaultAppendBinding() const {
 *         return {fAppend.fBuffer, fAppend.fOffset, fPendingCount*fAppendStride};
 *     }
 *
 *     void setTemplate(BindBufferInfo staticData, BindBufferInfo indices, uint32_t templateCount) {
 *         if (fPendingCount == 0) {
 *             // A pendingCount of zero indicates that a either a newPipelineState() or dynamicState()
 *             // triggered a flush, so we want to update the incoming member buffers.
 *             fStatic = staticData;
 *             fIndices = indices;
 *             fTemplateCount = templateCount;
 *         } else {
 *             // IF non-zero pending count, then we must not have flushed, so we cannot have called a
 *             // new pipeline yet. So we know the buffers MUST be the same.
 *             // IF a new buffer is acquired in reserve(), it calls a flush on the previous binding.
 *             // The flush sets the pendingCount to zero, skipping this code path.
 *             SkASSERT(fStatic == staticData && fIndices == indices);
 *             SkASSERT(fAppendStride == 0 || fAppend.fOffset % fAppendStride == 0);
 *             SkASSERT((templateCount == 0 &&
 *                       (fRenderState & RenderStateFlags::kAppendDynamicInstances)) ||
 *                       fTemplateCount == templateCount);
 *         }
 *     }
 *
 *     void bindAndFlush(BindBufferInfo staticData, BindBufferInfo indices, BindBufferInfo appendData,
 *                       uint32_t templateCount, unsigned int drawCount) {
 *         SkASSERT(drawCount > 0);
 *         SkASSERT(!fAppender); // Shouldn't be appending and manually drawing at the same time.
 *         SkASSERT(fPendingCount == 0); // Any prior appends must have been flushed by now.
 *         // CAUTION: If appending vertices, we make NO checks here to ensure that the initial offset
 *         // is four count aligned or that the data is padded. Caller MUST ensure any unaligned data
 *         // is safe.
 *         this->setTemplate(staticData, indices, templateCount);
 *         fPendingCount = drawCount;
 *         this->flushInternal(appendData);
 *     }
 *
 *     void flushInternal(BindBufferInfo appendData);
 *
 *     // RAII - Sets the DrawWriter's template and marks the writer in append mode (disabling direct
 *     // draws until the Appender is destructed).
 *     class Appender;
 *     SkDEBUGCODE(const Appender* fAppender = nullptr;)
 *
 *     // Helper functions for Appender implementations:
 *
 *     // Reallocates at least `count` blocks of the current `fAppendStride` so that later `append()`
 *     // calls with the same or smaller count should succeed. Flushes pending draws if needed.
 *     template <bool AppendVertices>
 *     void realloc(unsigned int count) {
 *         this->flush();
 *
 *         // For vertex rendering (ARM bug b/399631317), kBaseMultiple should be 4, otherwise 1.
 *         SkASSERT(AppendVertices == SkToBool(fRenderState & RenderStateFlags::kAppendVertices));
 *         static constexpr unsigned int kBaseMultiple = AppendVertices ? 4 : 1;
 *
 *         // We pass 0 as the count parameter to getMappedVertexBuffer and our `count` param as
 *         // the `reservedCount`, allowing the next call to append() to flexibly use less than our
 *         // reserved count. Because of this we ignore the BufferWriter, which points to an empty
 *         // range. `fAppend` will also have an empty size, but that will get patched as we append
 *         // data and increase fPendingCount (see getDefaultAppendBinding()).
 *         std::tie(std::ignore, fAppend, fCurrentBuffer) =
 *                 fManager->getMappedVertexBuffer(/*count=*/0,
 *                                                 /*stride=*/fAppendStride,
 *                                                 /*reservedCount=*/SkAlignTo(count, kBaseMultiple),
 *                                                 /*alignment=*/kBaseMultiple * fAppendStride);
 *     }
 *
 *     // Append `count` fAppendStride-sized blocks to be drawn in the next flush. This requires that
 *     // there be at least `count` left in the buffer, or that GPU buffer allocation failed, e.g.
 *     // caller is responsible for calling realloc when necessary.
 *     SK_ALWAYS_INLINE VertexWriter append(unsigned int count) {
 *         SkASSERT(count > 0);
 *         // realloc() should have been called first, so either we have a failed BufferSubAllocator
 *         // or we have enough space for a successsful suballocation.
 *         SkASSERT(!fCurrentBuffer || fCurrentBuffer.availableWithStride() >= count);
 *
 *         // For vertex rendering (ARM bug b/399631317), there should still be enough room to zero up
 *         // to a multiple of 4 vertices.
 *         SkASSERT(!SkToBool(fRenderState & RenderStateFlags::kAppendVertices) ||
 *                  (count + fPendingCount) <= SkAlign4(count + fPendingCount));
 *
 *         // Attempt suballocation from the current buffer. The reserve() call and newPipelineState()
 *         // configure the current buffer to append in units of fAppendStride. Assuming reserve()
 *         // succeeded (or had a larger reservation from before), this will succeed. reserve() handles
 *         // moving to a new Buffer, so if this is still invalid, there is a larger problem.
 *         BufferWriter writer = fCurrentBuffer.appendMappedWithStride(count);
 *         if (!writer) SK_UNLIKELY {
 *             // If the GPU mapped buffer failed, ensure we have a sufficiently large CPU address to
 *             // write to so that RenderSteps don't have to worry about error handling. The Recording
 *             // will fail since the map failure is tracked by BufferManager.
 *             // Since one of the reasons for GPU mapping failure is that count*stride does not fit
 *             // in 32-bits, we calculate the CPU-side size carefully.
 *             uint64_t size = (uint64_t)count * (uint64_t)fAppendStride;
 *             if (!SkTFitsIn<size_t>(size)) {
 *                 sk_report_container_overflow_and_die();
 *             }
 *             return VertexWriter(fFailureStorage.reset(size, SkAutoMalloc::kReuse_OnShrink),
 *                                 SkTo<size_t>(size));
 *         }
 *
 *         fPendingCount += count;
 *         return VertexWriter(std::move(writer));
 *     }
 * }
 * ```
 */
public data class DrawWriter public constructor(
  /**
   * C++ original:
   * ```cpp
   * DrawPassCommands::List* fCommandList
   * ```
   */
  private var fCommandList: List?,
  /**
   * C++ original:
   * ```cpp
   * DrawBufferManager* fManager
   * ```
   */
  private var fManager: Int?,
  /**
   * C++ original:
   * ```cpp
   * BufferSubAllocator fCurrentBuffer
   * ```
   */
  private var fCurrentBuffer: Int,
  /**
   * C++ original:
   * ```cpp
   * SkAutoMalloc fFailureStorage
   * ```
   */
  private var fFailureStorage: Int,
  /**
   * C++ original:
   * ```cpp
   * SkEnumBitMask<RenderStateFlags> fRenderState
   * ```
   */
  private var fRenderState: Int,
  /**
   * C++ original:
   * ```cpp
   * PrimitiveType fPrimitiveType
   * ```
   */
  private var fPrimitiveType: Int,
  /**
   * C++ original:
   * ```cpp
   * BarrierType fBarrierToIssueBeforeDraws
   * ```
   */
  private var fBarrierToIssueBeforeDraws: Int,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fStaticStride
   * ```
   */
  private var fStaticStride: Int,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fAppendStride
   * ```
   */
  private var fAppendStride: Int,
  /**
   * C++ original:
   * ```cpp
   * BindBufferInfo fAppend
   * ```
   */
  private var fAppend: Int,
  /**
   * C++ original:
   * ```cpp
   * BindBufferInfo fStatic
   * ```
   */
  private var fStatic: Int,
  /**
   * C++ original:
   * ```cpp
   * BindBufferInfo fIndices
   * ```
   */
  private var fIndices: Int,
  /**
   * C++ original:
   * ```cpp
   * BindBufferInfo fBoundAppend
   * ```
   */
  private var fBoundAppend: Int,
  /**
   * C++ original:
   * ```cpp
   * BindBufferInfo fBoundStatic
   * ```
   */
  private var fBoundStatic: Int,
  /**
   * C++ original:
   * ```cpp
   * BindBufferInfo fBoundIndices
   * ```
   */
  private var fBoundIndices: Int,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fTemplateCount
   * ```
   */
  private var fTemplateCount: Int,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fPendingCount
   * ```
   */
  private var fPendingCount: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * DrawBufferManager* bufferManager() { return fManager; }
   * ```
   */
  public fun bufferManager(): Int {
    TODO("Implement bufferManager")
  }

  /**
   * C++ original:
   * ```cpp
   * void flush() { this->flushInternal(this->getDefaultAppendBinding()); }
   * ```
   */
  public fun flush() {
    TODO("Implement flush")
  }

  /**
   * C++ original:
   * ```cpp
   * void newDynamicState() { this->flush(); }
   * ```
   */
  public fun newDynamicState() {
    TODO("Implement newDynamicState")
  }

  /**
   * C++ original:
   * ```cpp
   * void newPipelineState(PrimitiveType type,
   *                           size_t staticStride,
   *                           size_t appendStride,
   *                           SkEnumBitMask<RenderStateFlags> newRenderState,
   *                           BarrierType barrierType) {
   *         this->flush();
   *
   *         // Once flushed, any pending data must have been drawn.
   *         SkASSERT(fPendingCount == 0);
   *
   *         fPrimitiveType = type;
   *         fStaticStride = SkTo<uint32_t>(staticStride);
   *         fAppendStride = SkTo<uint32_t>(appendStride);
   *         fRenderState = newRenderState;
   *
   *         // ARM hardware(b/399631317): On a new pipeline, the initial offset when appending
   *         // vertices must be 4-count aligned, otherwise align to the stride so that access can use
   *         // the baseInstance parameter of draw calls.
   *         const uint32_t baseAlign = newRenderState & RenderStateFlags::kAppendVertices ?
   *                 4 * fAppendStride : fAppendStride;
   *
   *         // Initializes fAppend to hold the aligned offset within fCurrentBuffer and prepares
   *         // fCurrentBuffer for later reservations. Passing count=0 here means no actual space
   *         // (besides alignment padding) is used up until appendMappedWithStride is called.
   *         std::tie(std::ignore, fAppend) = fCurrentBuffer.getMappedSubrange(/*count=*/0,
   *                                                                           fAppendStride,
   *                                                                           baseAlign);
   *
   *
   *         // Assign the barrier type. If a valid value, then the DrawWriter will append
   *         // AddBarrier commands of the indicated type prior to appending any draw commands used with
   *         // this pipeline.
   *         fBarrierToIssueBeforeDraws = barrierType;
   *     }
   * ```
   */
  public fun newPipelineState(
    type: PrimitiveType,
    staticStride: ULong,
    appendStride: ULong,
    newRenderState: SkEnumBitMask<RenderStateFlags>,
    barrierType: BarrierType,
  ) {
    TODO("Implement newPipelineState")
  }

  /**
   * C++ original:
   * ```cpp
   * void draw(BindBufferInfo vertices, unsigned int vertexCount) {
   *         this->bindAndFlush({}, {}, vertices, 0, vertexCount);
   *     }
   * ```
   */
  public fun draw(vertices: BindBufferInfo, vertexCount: UInt) {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawIndexed(BindBufferInfo vertices, BindBufferInfo indices, unsigned int indexCount) {
   *         this->bindAndFlush(vertices, indices, {}, 0, indexCount);
   *     }
   * ```
   */
  public fun drawIndexed(
    vertices: BindBufferInfo,
    indices: BindBufferInfo,
    indexCount: UInt,
  ) {
    TODO("Implement drawIndexed")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawInstanced(BindBufferInfo vertices, unsigned int vertexCount,
   *                        BindBufferInfo instances, unsigned int instanceCount) {
   *         SkASSERT(vertexCount > 0);
   *         this->bindAndFlush(vertices, {}, instances, vertexCount, instanceCount);
   *     }
   * ```
   */
  public fun drawInstanced(
    vertices: BindBufferInfo,
    vertexCount: UInt,
    instances: BindBufferInfo,
    instanceCount: UInt,
  ) {
    TODO("Implement drawInstanced")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawIndexedInstanced(BindBufferInfo vertices, BindBufferInfo indices,
   *                               unsigned int indexCount, BindBufferInfo instances,
   *                               unsigned int instanceCount) {
   *         SkASSERT(indexCount > 0);
   *         this->bindAndFlush(vertices, indices, instances, indexCount, instanceCount);
   *     }
   * ```
   */
  public fun drawIndexedInstanced(
    vertices: BindBufferInfo,
    indices: BindBufferInfo,
    indexCount: UInt,
    instances: BindBufferInfo,
    instanceCount: UInt,
  ) {
    TODO("Implement drawIndexedInstanced")
  }

  /**
   * C++ original:
   * ```cpp
   * BindBufferInfo getLastAppendedBuffer() { return this->getDefaultAppendBinding(); }
   * ```
   */
  public fun getLastAppendedBuffer(): Int {
    TODO("Implement getLastAppendedBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * BindBufferInfo getDefaultAppendBinding() const {
   *         return {fAppend.fBuffer, fAppend.fOffset, fPendingCount*fAppendStride};
   *     }
   * ```
   */
  private fun getDefaultAppendBinding(): Int {
    TODO("Implement getDefaultAppendBinding")
  }

  /**
   * C++ original:
   * ```cpp
   * void setTemplate(BindBufferInfo staticData, BindBufferInfo indices, uint32_t templateCount) {
   *         if (fPendingCount == 0) {
   *             // A pendingCount of zero indicates that a either a newPipelineState() or dynamicState()
   *             // triggered a flush, so we want to update the incoming member buffers.
   *             fStatic = staticData;
   *             fIndices = indices;
   *             fTemplateCount = templateCount;
   *         } else {
   *             // IF non-zero pending count, then we must not have flushed, so we cannot have called a
   *             // new pipeline yet. So we know the buffers MUST be the same.
   *             // IF a new buffer is acquired in reserve(), it calls a flush on the previous binding.
   *             // The flush sets the pendingCount to zero, skipping this code path.
   *             SkASSERT(fStatic == staticData && fIndices == indices);
   *             SkASSERT(fAppendStride == 0 || fAppend.fOffset % fAppendStride == 0);
   *             SkASSERT((templateCount == 0 &&
   *                       (fRenderState & RenderStateFlags::kAppendDynamicInstances)) ||
   *                       fTemplateCount == templateCount);
   *         }
   *     }
   * ```
   */
  private fun setTemplate(
    staticData: BindBufferInfo,
    indices: BindBufferInfo,
    templateCount: UInt,
  ) {
    TODO("Implement setTemplate")
  }

  /**
   * C++ original:
   * ```cpp
   * void bindAndFlush(BindBufferInfo staticData, BindBufferInfo indices, BindBufferInfo appendData,
   *                       uint32_t templateCount, unsigned int drawCount) {
   *         SkASSERT(drawCount > 0);
   *         SkASSERT(!fAppender); // Shouldn't be appending and manually drawing at the same time.
   *         SkASSERT(fPendingCount == 0); // Any prior appends must have been flushed by now.
   *         // CAUTION: If appending vertices, we make NO checks here to ensure that the initial offset
   *         // is four count aligned or that the data is padded. Caller MUST ensure any unaligned data
   *         // is safe.
   *         this->setTemplate(staticData, indices, templateCount);
   *         fPendingCount = drawCount;
   *         this->flushInternal(appendData);
   *     }
   * ```
   */
  private fun bindAndFlush(
    staticData: BindBufferInfo,
    indices: BindBufferInfo,
    appendData: BindBufferInfo,
    templateCount: UInt,
    drawCount: UInt,
  ) {
    TODO("Implement bindAndFlush")
  }

  /**
   * C++ original:
   * ```cpp
   * void DrawWriter::flushInternal(BindBufferInfo appendData) {
   *     // Skip flush if no items appended, or dynamic instances resolved to zero count.
   *     if (fPendingCount == 0 ||
   *         ((fRenderState & RenderStateFlags::kAppendDynamicInstances) && fTemplateCount == 0)) {
   *         return;
   *     }
   *
   *     // How much to advance fAppend.fOffset when the flush is completed
   *     uint32_t advanceOffset = fPendingCount;
   *
   *     // ARM hardware (b/399631317): Unreferenced vertices in sequential indexes of 4 will be
   *     // speculatively executed. To work around this, we pad the buffer by requesting additional
   *     // space, and then ensure valid, minimally deleterious data by memsetting the padding to zero.
   *     if (fRenderState & RenderStateFlags::kAppendVertices) {
   *         const uint32_t countDiff = (SkAlign4(fPendingCount) - fPendingCount);
   *         if (countDiff) {
   *             BufferWriter zWriter = fCurrentBuffer.appendMappedWithStride(countDiff);
   *             SkASSERT(zWriter); // The buffer should have been sized to hold an aligned total
   *             zWriter.zeroBytes(countDiff * fAppendStride);
   *             advanceOffset += countDiff;
   *         }
   *     }
   *
   *     // Calculate base offsets from buffer info for the draw commands.
   *     // - If a valid bufferFoo exists and  matches the current stride, use pendingBaseFoo as a
   *     //   pseudo-alias for offset and reset the offset and size before assigning to boundBufferFoo.
   *     // - If a valid bufferFoo does not exist, or is not stride aligned, then draw from the start of
   *     //   the offset with pendingBaseFoo = 0 and assign the current buffer.
   *     auto bind = [](const BindBufferInfo& buffer, uint32_t stride, BindBufferInfo& boundBuffer,
   *                    uint32_t& pendingBase) -> bool {
   *         bool shouldBind = false;
   *         if (buffer.fBuffer) {
   *             BindBufferInfo newBinding = buffer;
   *             if (buffer.fOffset % stride == 0) {
   *                 pendingBase = buffer.fOffset / stride;
   *                 newBinding = {buffer.fBuffer, 0, SkTo<uint32_t>(buffer.fBuffer->size())};
   *             }
   *             shouldBind = boundBuffer != newBinding;
   *             boundBuffer = newBinding;
   *         }
   *         return shouldBind;
   *     };
   *
   *     uint32_t pendingBaseAppend = 0;
   *     uint32_t pendingBaseStatic = 0;
   *     uint32_t pendingBaseIndices = 0;
   *     if (bind(appendData, fAppendStride, fBoundAppend, pendingBaseAppend)) {
   *         fCommandList->bindAppendDataBuffer(fBoundAppend);
   *     }
   *     if (bind(fStatic, fStaticStride, fBoundStatic, pendingBaseStatic)) {
   *         fCommandList->bindStaticDataBuffer(fBoundStatic);
   *     }
   *     if (bind(fIndices, sizeof(uint16_t), fBoundIndices, pendingBaseIndices)) {
   *         fCommandList->bindIndexBuffer(fBoundIndices);
   *     }
   *
   *     // Before any draw commands are added, check if the DrawWriter has an assigned barrier type
   *     // to issue prior to draw calls.
   *     if (fBarrierToIssueBeforeDraws != BarrierType::kNone) {
   *         fCommandList->addBarrier(fBarrierToIssueBeforeDraws);
   *     }
   *
   *     // Issue the appropriate draw call (instanced vs. non-instanced) based on the current
   *     // fTemplateCount. Because of the initial AppendDynamicInstances && fTemplateCount check, any
   *     // DynamicInstance render step must have valid (non-zero) templateCount data at this point.
   *     if (fTemplateCount) {
   *         SkASSERT((pendingBaseAppend + fPendingCount)*fAppendStride <= fBoundAppend.fSize);
   *         if (fIndices) {
   *             // It's not possible to validate that the indices stored in fIndices access only valid
   *             // data within fVertices. Simply validate that fIndices holds enough data for the
   *             // vertex count that's drawn.
   *             SkASSERT(fTemplateCount*sizeof(uint16_t) <= fIndices.fSize);
   *             fCommandList->drawIndexedInstanced(fPrimitiveType,
   *                                                pendingBaseIndices,
   *                                                fTemplateCount,
   *                                                pendingBaseStatic,
   *                                                pendingBaseAppend,
   *                                                fPendingCount);
   *
   *         } else {
   *             SkASSERT(fTemplateCount*fStaticStride <= fStatic.fSize);
   *             fCommandList->drawInstanced(fPrimitiveType, pendingBaseStatic, fTemplateCount,
   *                                         pendingBaseAppend, fPendingCount);
   *         }
   *
   *         // Clear instancing template state after the draw is recorded for non-Fixed RenderState
   *         if (fRenderState & RenderStateFlags::kAppendDynamicInstances) {
   *             fTemplateCount = 0;
   *         }
   *     } else {
   *         // Issue non-instanced draw call (indexed or non-indexed).
   *         if (fIndices) {
   *             // As before, just validate there is sufficient index data
   *             SkASSERT(fPendingCount*sizeof(uint16_t) <= fIndices.fSize);
   *             fCommandList->drawIndexed(fPrimitiveType,
   *                                       pendingBaseIndices,
   *                                       fPendingCount,
   *                                       pendingBaseAppend);
   *         } else {
   *             SkASSERT((pendingBaseAppend + fPendingCount)*fStaticStride <= fBoundAppend.fSize);
   *             fCommandList->draw(fPrimitiveType, pendingBaseAppend, fPendingCount);
   *         }
   *     }
   *
   *     // Mark all appended items as drawn, advance base offset which is normally set as part of a new
   *     // pipeline state or flushing for a new buffer during appending data. But if we flushed because
   *     // of other dynamic state changes, there might not be interaction with the BufferManager.
   *     fAppend.fOffset += advanceOffset * fAppendStride;
   *     fPendingCount = 0;
   * }
   * ```
   */
  private fun flushInternal(appendData: BindBufferInfo) {
    TODO("Implement flushInternal")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDEBUGCODE(const Appender* fAppender = nullptr;)
   * ```
   */
  private fun skDEBUGCODE(param0: Appender?): Int {
    TODO("Implement skDEBUGCODE")
  }
}
