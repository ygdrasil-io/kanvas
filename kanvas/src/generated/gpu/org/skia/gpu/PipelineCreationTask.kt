package org.skia.gpu

import kotlin.Int
import org.skia.core.SkEnumBitMask
import org.skia.foundation.SkRefCnt

/**
 * C++ original:
 * ```cpp
 * class PipelineCreationTask : public SkRefCnt {
 * private:
 *     friend class PipelineManager; // for entire API and fPipeline
 *
 *     PipelineCreationTask(const UniqueKey& pipelineKey,
 *                          const GraphicsPipelineDesc& graphicsPipelineDesc,
 *                          const RenderPassDesc& renderPassDesc,
 *                          SkEnumBitMask<PipelineCreationFlags> pipelineCreationFlags)
 *             : fPipelineKey(pipelineKey)
 *             , fGraphicsPipelineDesc(graphicsPipelineDesc)
 *             , fRenderPassDesc(renderPassDesc)
 *             , fPipelineCreationFlags(pipelineCreationFlags) {}
 *
 *     const UniqueKey fPipelineKey;  // used to track this task in the PipelineManager
 *     const GraphicsPipelineDesc fGraphicsPipelineDesc;
 *     const RenderPassDesc fRenderPassDesc;
 *     const SkEnumBitMask<PipelineCreationFlags> fPipelineCreationFlags;
 *
 *     // Once completed, this task will have filled in 'fPipeline' (if compilation succeeded).
 *     // This also serves to lock the pipeline in the cache.
 *     sk_sp<GraphicsPipeline> fPipeline;
 *
 *     std::atomic<bool> fCompleted = false;
 * }
 * ```
 */
public open class PipelineCreationTask public constructor(
  pipelineKey: UniqueKey,
  graphicsPipelineDesc: GraphicsPipelineDesc,
  renderPassDesc: RenderPassDesc,
  pipelineCreationFlags: SkEnumBitMask<PipelineCreationFlags>,
) : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * const UniqueKey fPipelineKey
   * ```
   */
  private val fPipelineKey: Int = TODO("Initialize fPipelineKey")

  /**
   * C++ original:
   * ```cpp
   * const GraphicsPipelineDesc fGraphicsPipelineDesc
   * ```
   */
  private val fGraphicsPipelineDesc: Int = TODO("Initialize fGraphicsPipelineDesc")

  /**
   * C++ original:
   * ```cpp
   * const RenderPassDesc fRenderPassDesc
   * ```
   */
  private val fRenderPassDesc: Int = TODO("Initialize fRenderPassDesc")

  /**
   * C++ original:
   * ```cpp
   * const SkEnumBitMask<PipelineCreationFlags> fPipelineCreationFlags
   * ```
   */
  private val fPipelineCreationFlags: Int = TODO("Initialize fPipelineCreationFlags")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<GraphicsPipeline> fPipeline
   * ```
   */
  private var fPipeline: Int = TODO("Initialize fPipeline")

  /**
   * C++ original:
   * ```cpp
   * std::atomic<bool> fCompleted
   * ```
   */
  private var fCompleted: Int = TODO("Initialize fCompleted")
}
