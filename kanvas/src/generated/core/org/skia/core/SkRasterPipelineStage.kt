package org.skia.core

import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * struct SkRasterPipelineStage {
 *     // `fn` holds a function pointer from `ops_lowp` or `ops_highp` in SkOpts.cpp. These functions
 *     // correspond to operations from the SkRasterPipelineOp enum in SkRasterPipelineOpList.h. The
 *     // exact function pointer type varies depending on architecture (specifically, look for `using
 *     // Stage =` in SkRasterPipeline_opts.h).
 *     void (*fn)();
 *
 *     // `ctx` holds data used by the stage function.
 *     // Most context structures are declared in SkRasterPipelineOpContexts.h, and have names ending
 *     // in Ctx (e.g. "SkRasterPipelineContexts::SamplerCtx"). Some Raster Pipeline stages pack
 *     // non-pointer data into this field using `SkRPCtxUtils::Pack`.
 *     void* ctx;
 * }
 * ```
 */
public data class SkRasterPipelineStage public constructor(
  /**
   * C++ original:
   * ```cpp
   * void (*fn)()
   * ```
   */
  public var fn: () -> Unit,
  /**
   * C++ original:
   * ```cpp
   * void* ctx
   * ```
   */
  public var ctx: Unit?,
)
