package org.skia.gpu

import kotlin.Int
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class ContextCtorAccessor {
 * public:
 *     static std::unique_ptr<Context> MakeContext(sk_sp<SharedContext>,
 *                                                 std::unique_ptr<QueueManager>,
 *                                                 const ContextOptions&);
 * }
 * ```
 */
public open class ContextCtorAccessor {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<Context> ContextCtorAccessor::MakeContext(
     *         sk_sp<SharedContext> sharedContext,
     *         std::unique_ptr<QueueManager> queueManager,
     *         const ContextOptions& options) {
     *     auto context = std::unique_ptr<Context>(new Context(std::move(sharedContext),
     *                                                         std::move(queueManager),
     *                                                         options));
     *     if (context && context->finishInitialization()) {
     *         return context;
     *     } else {
     *         return nullptr;
     *     }
     * }
     * ```
     */
    public fun makeContext(
      sharedContext: SkSp<SharedContext>,
      queueManager: QueueManager?,
      options: ContextOptions,
    ): Int {
      TODO("Implement makeContext")
    }
  }
}
