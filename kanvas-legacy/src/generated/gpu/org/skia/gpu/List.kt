package org.skia.gpu

import kotlin.Array
import kotlin.Float
import kotlin.Int
import kotlin.UInt
import org.skia.math.SkIRect
import undefined.Args

/**
 * C++ original:
 * ```cpp
 * class List {
 * public:
 *     List() = default;
 *     ~List() = default;
 *
 *     int count() const { return fCommands.count(); }
 *
 *     void bindGraphicsPipeline(uint32_t pipelineIndex) {
 *         this->add<BindGraphicsPipeline>(pipelineIndex);
 *     }
 *
 *     void setBlendConstants(std::array<float, 4>  blendConstants) {
 *         this->add<SetBlendConstants>(blendConstants);
 *     }
 *
 *     void bindUniformBuffer(BindBufferInfo info, UniformSlot slot) {
 *         this->add<BindUniformBuffer>(info, slot);
 *     }
 *
 *     // Caller must write 'numTexSamplers' textures and sampler descriptions into the two returned
 *     // arrays. The texture proxies must have refs held for the lifetime of this command list.
 *     std::pair<const TextureProxy**, SamplerDesc*>
 *     bindDeferredTexturesAndSamplers(int numTexSamplers) {
 *         const TextureProxy** textures = fAlloc.makeArrayDefault<const TextureProxy*>(numTexSamplers);
 *         SamplerDesc* samplers = fAlloc.makeArrayDefault<SamplerDesc>(numTexSamplers);
 *         this->add<BindTexturesAndSamplers>(numTexSamplers, textures, samplers);
 *         return {textures, samplers};
 *     }
 *
 *     void setScissor(SkIRect scissor) {
 *         this->add<SetScissor>(Scissor(scissor));
 *     }
 *
 *     void bindStaticDataBuffer(BindBufferInfo staticAttribs) {
 *         this->add<BindStaticDataBuffer>(staticAttribs);
 *     }
 *
 *     void bindAppendDataBuffer(BindBufferInfo appendAttribs) {
 *         this->add<BindAppendDataBuffer>(appendAttribs);
 *     }
 *
 *     void bindIndexBuffer(BindBufferInfo indices) {
 *         this->add<BindIndexBuffer>(indices);
 *     }
 *
 *     void bindIndirectBuffer(BindBufferInfo indirect) {
 *         this->add<BindIndirectBuffer>(indirect);
 *     }
 *
 *     void draw(PrimitiveType type, unsigned int baseVertex, unsigned int vertexCount) {
 *         this->add<Draw>(type, baseVertex, vertexCount);
 *     }
 *
 *     void drawIndexed(PrimitiveType type, unsigned int baseIndex,
 *                      unsigned int indexCount, unsigned int baseVertex) {
 *         this->add<DrawIndexed>(type, baseIndex, indexCount, baseVertex);
 *     }
 *
 *     void drawInstanced(PrimitiveType type,
 *                        unsigned int baseVertex, unsigned int vertexCount,
 *                        unsigned int baseInstance, unsigned int instanceCount) {
 *         this->add<DrawInstanced>(type, baseVertex, vertexCount, baseInstance, instanceCount);
 *     }
 *
 *     void drawIndexedInstanced(PrimitiveType type,
 *                               unsigned int baseIndex, unsigned int indexCount,
 *                               unsigned int baseVertex, unsigned int baseInstance,
 *                               unsigned int instanceCount) {
 *         this->add<DrawIndexedInstanced>(type,
 *                                         baseIndex,
 *                                         indexCount,
 *                                         baseVertex,
 *                                         baseInstance,
 *                                         instanceCount);
 *     }
 *
 *     void drawIndirect(PrimitiveType type) {
 *         this->add<DrawIndirect>(type);
 *     }
 *
 *     void drawIndexedIndirect(PrimitiveType type) {
 *         this->add<DrawIndexedIndirect>(type);
 *     }
 *
 *     void addBarrier(BarrierType type) {
 *         this->add<AddBarrier>(type);
 *     }
 *
 *     using Command = std::pair<Type, void*>;
 *     using Iter = SkTBlockList<Command, 16>::CIter;
 *     Iter commands() const { return fCommands.items(); }
 *
 * private:
 *     template <typename T, typename... Args>
 *     void add(Args&&... args) {
 *         T* cmd = fAlloc.make<T>(T{std::forward<Args>(args)...});
 *         fCommands.push_back(std::make_pair(T::kType, cmd));
 *     }
 *
 *     SkTBlockList<Command, 16> fCommands{SkBlockAllocator::GrowthPolicy::kFibonacci};
 *
 *     // fAlloc needs to be a data structure which can append variable length data in contiguous
 *     // chunks, returning a stable handle to that data for later retrieval.
 *     SkArenaAlloc fAlloc{256};
 * }
 * ```
 */
public data class List public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkTBlockList<Command, 16> fCommands
   * ```
   */
  private var fCommands: Int,
  /**
   * C++ original:
   * ```cpp
   * SkArenaAlloc fAlloc
   * ```
   */
  private var fAlloc: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * int count() const { return fCommands.count(); }
   * ```
   */
  public fun count(): Int {
    TODO("Implement count")
  }

  /**
   * C++ original:
   * ```cpp
   * void bindGraphicsPipeline(uint32_t pipelineIndex) {
   *         this->add<BindGraphicsPipeline>(pipelineIndex);
   *     }
   * ```
   */
  public fun bindGraphicsPipeline(pipelineIndex: UInt) {
    TODO("Implement bindGraphicsPipeline")
  }

  /**
   * C++ original:
   * ```cpp
   * void setBlendConstants(std::array<float, 4>  blendConstants) {
   *         this->add<SetBlendConstants>(blendConstants);
   *     }
   * ```
   */
  public fun setBlendConstants(blendConstants: Array<Float>) {
    TODO("Implement setBlendConstants")
  }

  /**
   * C++ original:
   * ```cpp
   * void bindUniformBuffer(BindBufferInfo info, UniformSlot slot) {
   *         this->add<BindUniformBuffer>(info, slot);
   *     }
   * ```
   */
  public fun bindUniformBuffer(info: BindBufferInfo, slot: UniformSlot) {
    TODO("Implement bindUniformBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * std::pair<const TextureProxy**, SamplerDesc*>
   *     bindDeferredTexturesAndSamplers(int numTexSamplers) {
   *         const TextureProxy** textures = fAlloc.makeArrayDefault<const TextureProxy*>(numTexSamplers);
   *         SamplerDesc* samplers = fAlloc.makeArrayDefault<SamplerDesc>(numTexSamplers);
   *         this->add<BindTexturesAndSamplers>(numTexSamplers, textures, samplers);
   *         return {textures, samplers};
   *     }
   * ```
   */
  public fun bindDeferredTexturesAndSamplers(numTexSamplers: Int): Int {
    TODO("Implement bindDeferredTexturesAndSamplers")
  }

  /**
   * C++ original:
   * ```cpp
   * void setScissor(SkIRect scissor) {
   *         this->add<SetScissor>(Scissor(scissor));
   *     }
   * ```
   */
  public fun setScissor(scissor: SkIRect) {
    TODO("Implement setScissor")
  }

  /**
   * C++ original:
   * ```cpp
   * void bindStaticDataBuffer(BindBufferInfo staticAttribs) {
   *         this->add<BindStaticDataBuffer>(staticAttribs);
   *     }
   * ```
   */
  public fun bindStaticDataBuffer(staticAttribs: BindBufferInfo) {
    TODO("Implement bindStaticDataBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void bindAppendDataBuffer(BindBufferInfo appendAttribs) {
   *         this->add<BindAppendDataBuffer>(appendAttribs);
   *     }
   * ```
   */
  public fun bindAppendDataBuffer(appendAttribs: BindBufferInfo) {
    TODO("Implement bindAppendDataBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void bindIndexBuffer(BindBufferInfo indices) {
   *         this->add<BindIndexBuffer>(indices);
   *     }
   * ```
   */
  public fun bindIndexBuffer(indices: BindBufferInfo) {
    TODO("Implement bindIndexBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void bindIndirectBuffer(BindBufferInfo indirect) {
   *         this->add<BindIndirectBuffer>(indirect);
   *     }
   * ```
   */
  public fun bindIndirectBuffer(indirect: BindBufferInfo) {
    TODO("Implement bindIndirectBuffer")
  }

  /**
   * C++ original:
   * ```cpp
   * void draw(PrimitiveType type, unsigned int baseVertex, unsigned int vertexCount) {
   *         this->add<Draw>(type, baseVertex, vertexCount);
   *     }
   * ```
   */
  public fun draw(
    type: PrimitiveType,
    baseVertex: UInt,
    vertexCount: UInt,
  ) {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawIndexed(PrimitiveType type, unsigned int baseIndex,
   *                      unsigned int indexCount, unsigned int baseVertex) {
   *         this->add<DrawIndexed>(type, baseIndex, indexCount, baseVertex);
   *     }
   * ```
   */
  public fun drawIndexed(
    type: PrimitiveType,
    baseIndex: UInt,
    indexCount: UInt,
    baseVertex: UInt,
  ) {
    TODO("Implement drawIndexed")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawInstanced(PrimitiveType type,
   *                        unsigned int baseVertex, unsigned int vertexCount,
   *                        unsigned int baseInstance, unsigned int instanceCount) {
   *         this->add<DrawInstanced>(type, baseVertex, vertexCount, baseInstance, instanceCount);
   *     }
   * ```
   */
  public fun drawInstanced(
    type: PrimitiveType,
    baseVertex: UInt,
    vertexCount: UInt,
    baseInstance: UInt,
    instanceCount: UInt,
  ) {
    TODO("Implement drawInstanced")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawIndexedInstanced(PrimitiveType type,
   *                               unsigned int baseIndex, unsigned int indexCount,
   *                               unsigned int baseVertex, unsigned int baseInstance,
   *                               unsigned int instanceCount) {
   *         this->add<DrawIndexedInstanced>(type,
   *                                         baseIndex,
   *                                         indexCount,
   *                                         baseVertex,
   *                                         baseInstance,
   *                                         instanceCount);
   *     }
   * ```
   */
  public fun drawIndexedInstanced(
    type: PrimitiveType,
    baseIndex: UInt,
    indexCount: UInt,
    baseVertex: UInt,
    baseInstance: UInt,
    instanceCount: UInt,
  ) {
    TODO("Implement drawIndexedInstanced")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawIndirect(PrimitiveType type) {
   *         this->add<DrawIndirect>(type);
   *     }
   * ```
   */
  public fun drawIndirect(type: PrimitiveType) {
    TODO("Implement drawIndirect")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawIndexedIndirect(PrimitiveType type) {
   *         this->add<DrawIndexedIndirect>(type);
   *     }
   * ```
   */
  public fun drawIndexedIndirect(type: PrimitiveType) {
    TODO("Implement drawIndexedIndirect")
  }

  /**
   * C++ original:
   * ```cpp
   * void addBarrier(BarrierType type) {
   *         this->add<AddBarrier>(type);
   *     }
   * ```
   */
  public fun addBarrier(type: BarrierType) {
    TODO("Implement addBarrier")
  }

  /**
   * C++ original:
   * ```cpp
   * Iter commands() const { return fCommands.items(); }
   * ```
   */
  public fun commands(): Int {
    TODO("Implement commands")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename T, typename... Args>
   *     void add(Args&&... args) {
   *         T* cmd = fAlloc.make<T>(T{std::forward<Args>(args)...});
   *         fCommands.push_back(std::make_pair(T::kType, cmd));
   *     }
   * ```
   */
  private fun <T, Args> add(args: Args) {
    TODO("Implement add")
  }
}
