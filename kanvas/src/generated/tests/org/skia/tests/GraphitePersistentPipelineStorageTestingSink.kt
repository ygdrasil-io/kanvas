package org.skia.tests

import kotlin.Char
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkWStream
import org.skia.tools.SkCommandLineConfigGraphite
import org.skia.tools.TestOptions

/**
 * C++ original:
 * ```cpp
 * class GraphitePersistentPipelineStorageTestingSink : public GraphiteSink {
 * public:
 *     GraphitePersistentPipelineStorageTestingSink(const SkCommandLineConfigGraphite*,
 *                                                  const skiatest::graphite::TestOptions&);
 *
 *     Result draw(const Src&, SkBitmap*, SkWStream*, SkString*) const override;
 *
 *     const char* fileExtension() const override {
 *         // Suppress writing out results from this config - we just want to do our matching test
 *         return nullptr;
 *     }
 *
 * private:
 *     mutable sk_gpu_test::GraphiteMemoryPipelineStorage fMemoryPipelineStorage;
 * }
 * ```
 */
public open class GraphitePersistentPipelineStorageTestingSink public constructor(
  param0: SkCommandLineConfigGraphite,
  param1: TestOptions,
) : GraphiteSink() {
  /**
   * C++ original:
   * ```cpp
   * mutable sk_gpu_test::GraphiteMemoryPipelineStorage fMemoryPipelineStorage
   * ```
   */
  private var fMemoryPipelineStorage: Int = TODO("Initialize fMemoryPipelineStorage")

  /**
   * C++ original:
   * ```cpp
   * GraphitePersistentPipelineStorageTestingSink(const SkCommandLineConfigGraphite*,
   *                                                  const skiatest::graphite::TestOptions&)
   * ```
   */
  public constructor(config: SkCommandLineConfigGraphite?, options: TestOptions) : this(TODO(), TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * Result GraphitePersistentPipelineStorageTestingSink::draw(const Src& src,
   *                                                           SkBitmap* dst,
   *                                                           SkWStream* wStream,
   *                                                           SkString* log) const {
   *     // Draw twice, once with a cold start, and again with a warm start.
   *     fMemoryPipelineStorage.reset();
   *
   *     Result result = this->GraphiteSink::draw(src, dst, wStream, log);
   *     if (!result.isOk() || !dst) {
   *         return result;
   *     }
   *
   *     // With the cold start there shouldn't anything to load but we should store the new pipelines.
   *     SkAssertResult(fMemoryPipelineStorage.numLoads() == 0);
   *     SkAssertResult(fMemoryPipelineStorage.numStores() == 1);
   *
   *     fMemoryPipelineStorage.resetCacheStats();
   *
   *     SkBitmap reference;
   *     SkString refLog;
   *     SkDynamicMemoryWStream refStream;
   *     Result refResult = this->GraphiteSink::draw(src, &reference, &refStream, &refLog);
   *     if (!refResult.isOk()) {
   *         return refResult;
   *     }
   *
   *     // With the warm start we should be able to load the prior pipelines and, thus, not need
   *     // to store any new ones.
   *     SkAssertResult(fMemoryPipelineStorage.numLoads() == 1);
   *     SkAssertResult(fMemoryPipelineStorage.numStores() == 0);
   *
   *     return compare_bitmaps(reference, *dst);
   * }
   * ```
   */
  public override fun draw(
    src: Src,
    dst: SkBitmap?,
    wStream: SkWStream?,
    log: String?,
  ): Result {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* fileExtension() const override {
   *         // Suppress writing out results from this config - we just want to do our matching test
   *         return nullptr;
   *     }
   * ```
   */
  public override fun fileExtension(): Char {
    TODO("Implement fileExtension")
  }
}
