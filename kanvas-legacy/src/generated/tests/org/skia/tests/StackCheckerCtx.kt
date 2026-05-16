package org.skia.tests

import kotlin.Int
import org.skia.core.CallbackCtx

/**
 * C++ original:
 * ```cpp
 * class StackCheckerCtx : SkRasterPipelineContexts::CallbackCtx {
 * public:
 *     StackCheckerCtx() {
 *         this->fn = [](SkRasterPipelineContexts::CallbackCtx* self, int active_pixels) {
 *             auto ctx = (StackCheckerCtx*)self;
 *             ctx->fStackAddrs.push_back(&active_pixels);
 *         };
 *     }
 *
 *     enum class Behavior {
 *         kGrowth,
 *         kBaseline,
 *         kUnknown,
 *     };
 *
 *     static Behavior GrowthBehavior() {
 *         // Only some stages use the musttail attribute, so we have no way of knowing what's going to
 *         // happen. In release builds, it's likely that the compiler will apply tail-call
 *         // optimization. Even in some debug builds (on Windows), we don't see stack growth.
 *         return Behavior::kUnknown;
 *     }
 *
 *     // Call one of these two each time the checker callback is added:
 *     StackCheckerCtx* expectGrowth() {
 *         fExpectedBehavior.push_back(GrowthBehavior());
 *         return this;
 *     }
 *
 *     StackCheckerCtx* expectBaseline() {
 *         fExpectedBehavior.push_back(Behavior::kBaseline);
 *         return this;
 *     }
 *
 *     void validate(skiatest::Reporter* r) {
 *         REPORTER_ASSERT(r, fStackAddrs.size() == fExpectedBehavior.size());
 *
 *         // This test is storing and comparing stack pointers (to dead stack frames) as a way of
 *         // measuring stack usage. Unsurprisingly, ASAN doesn't like that. HWASAN actually inserts
 *         // tag bytes in the pointers, causing them not to match. Newer versions of vanilla ASAN
 *         // also appear to salt the stack slightly, causing repeated calls to scrape different
 *         // addresses, even though $rsp is identical on each invocation of the lambda.
 * #if !defined(SK_SANITIZE_ADDRESS)
 *         void* baseline = fStackAddrs[0];
 *         for (size_t i = 1; i < fStackAddrs.size(); i++) {
 *             if (fExpectedBehavior[i] == Behavior::kGrowth) {
 *                 REPORTER_ASSERT(r, fStackAddrs[i] != baseline);
 *             } else if (fExpectedBehavior[i] == Behavior::kBaseline) {
 *                 REPORTER_ASSERT(r, fStackAddrs[i] == baseline);
 *             } else {
 *                 // Unknown behavior, nothing we can assert here
 *             }
 *         }
 * #endif
 *     }
 *
 * private:
 *     std::vector<void*>    fStackAddrs;
 *     std::vector<Behavior> fExpectedBehavior;
 * }
 * ```
 */
public open class StackCheckerCtx public constructor() : CallbackCtx() {
  /**
   * C++ original:
   * ```cpp
   * std::vector<void*>    fStackAddrs
   * ```
   */
  private var fStackAddrs: Int = TODO("Initialize fStackAddrs")

  /**
   * C++ original:
   * ```cpp
   * std::vector<Behavior> fExpectedBehavior
   * ```
   */
  private var fExpectedBehavior: Int = TODO("Initialize fExpectedBehavior")

  /**
   * C++ original:
   * ```cpp
   * StackCheckerCtx* expectGrowth() {
   *         fExpectedBehavior.push_back(GrowthBehavior());
   *         return this;
   *     }
   * ```
   */
  public fun expectGrowth(): StackCheckerCtx {
    TODO("Implement expectGrowth")
  }

  /**
   * C++ original:
   * ```cpp
   * StackCheckerCtx* expectBaseline() {
   *         fExpectedBehavior.push_back(Behavior::kBaseline);
   *         return this;
   *     }
   * ```
   */
  public fun expectBaseline(): StackCheckerCtx {
    TODO("Implement expectBaseline")
  }

  /**
   * C++ original:
   * ```cpp
   * void validate(skiatest::Reporter* r) {
   *         REPORTER_ASSERT(r, fStackAddrs.size() == fExpectedBehavior.size());
   *
   *         // This test is storing and comparing stack pointers (to dead stack frames) as a way of
   *         // measuring stack usage. Unsurprisingly, ASAN doesn't like that. HWASAN actually inserts
   *         // tag bytes in the pointers, causing them not to match. Newer versions of vanilla ASAN
   *         // also appear to salt the stack slightly, causing repeated calls to scrape different
   *         // addresses, even though $rsp is identical on each invocation of the lambda.
   * #if !defined(SK_SANITIZE_ADDRESS)
   *         void* baseline = fStackAddrs[0];
   *         for (size_t i = 1; i < fStackAddrs.size(); i++) {
   *             if (fExpectedBehavior[i] == Behavior::kGrowth) {
   *                 REPORTER_ASSERT(r, fStackAddrs[i] != baseline);
   *             } else if (fExpectedBehavior[i] == Behavior::kBaseline) {
   *                 REPORTER_ASSERT(r, fStackAddrs[i] == baseline);
   *             } else {
   *                 // Unknown behavior, nothing we can assert here
   *             }
   *         }
   * #endif
   *     }
   * ```
   */
  public fun validate(r: Reporter?) {
    TODO("Implement validate")
  }

  public enum class Behavior {
    kGrowth,
    kBaseline,
    kUnknown,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static Behavior GrowthBehavior() {
     *         // Only some stages use the musttail attribute, so we have no way of knowing what's going to
     *         // happen. In release builds, it's likely that the compiler will apply tail-call
     *         // optimization. Even in some debug builds (on Windows), we don't see stack growth.
     *         return Behavior::kUnknown;
     *     }
     * ```
     */
    public fun growthBehavior(): Behavior {
      TODO("Implement growthBehavior")
    }
  }
}
