package org.skia.tests

import kotlin.Char
import kotlin.Int
import org.skia.tools.SkCommandLineConfigGraphite
import org.skia.tools.TestOptions

/**
 * C++ original:
 * ```cpp
 * class GraphitePipelineTrackingSink : public GraphiteSink {
 * public:
 *     GraphitePipelineTrackingSink(const SkCommandLineConfigGraphite*,
 *                                  const skiatest::graphite::TestOptions&);
 *
 *     void done() const override;
 *
 *     const char* fileExtension() const override {
 *         // Suppress writing out results from this config - we just want to do our matching test
 *         return nullptr;
 *     }
 *
 * private:
 *     std::unique_ptr<skiatools::graphite::PipelineCallBackHandler> fPipelineHandler;
 * }
 * ```
 */
public open class GraphitePipelineTrackingSink public constructor(
  param0: SkCommandLineConfigGraphite,
  param1: TestOptions,
) : GraphiteSink() {
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<skiatools::graphite::PipelineCallBackHandler> fPipelineHandler
   * ```
   */
  private var fPipelineHandler: Int = TODO("Initialize fPipelineHandler")

  /**
   * C++ original:
   * ```cpp
   * GraphitePipelineTrackingSink(const SkCommandLineConfigGraphite*,
   *                                  const skiatest::graphite::TestOptions&)
   * ```
   */
  public constructor(config: SkCommandLineConfigGraphite?, options: TestOptions) : this(TODO(), TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * void GraphitePipelineTrackingSink::done() const {
   *     fPipelineHandler->report();
   * }
   * ```
   */
  public override fun done() {
    TODO("Implement done")
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
