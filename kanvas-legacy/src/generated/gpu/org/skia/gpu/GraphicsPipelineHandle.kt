package org.skia.gpu

/**
 * C++ original:
 * ```cpp
 * class GraphicsPipelineHandle {
 * private:
 *     friend class PipelineManager;  // for ctors
 *
 *     GraphicsPipelineHandle(sk_sp<PipelineCreationTask> task);
 *
 *     GraphicsPipelineHandle(sk_sp<GraphicsPipeline> pipeline);
 *
 *     std::variant<sk_sp<PipelineCreationTask>, sk_sp<GraphicsPipeline>> fTaskOrPipeline;
 * }
 * ```
 */
public data class GraphicsPipelineHandle public constructor(
  /**
   * C++ original:
   * ```cpp
   * GraphicsPipelineHandle(sk_sp<PipelineCreationTask> task)
   * ```
   */
  private var skSp: GraphicsPipelineHandle,
)
