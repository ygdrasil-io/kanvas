package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class ScopedDrawBuilder {
 * public:
 *     explicit ScopedDrawBuilder(Recorder* recorder)
 *             : fRecorder(recorder),
 *               fKeyAndDataBuilder(fRecorder->priv().popOrCreateKeyAndDataBuilder()) {
 *         SkASSERT(fKeyAndDataBuilder);
 *         SkDEBUGCODE(this->gatherer()->checkReset());
 *         SkDEBUGCODE(this->builder()->checkReset());
 *     }
 *
 *     ~ScopedDrawBuilder() {
 *         SkASSERT(fKeyAndDataBuilder && fRecorder);
 *         // The PipelineDataGatherer must be reset before being returned to the pool for reuse.
 *         this->gatherer()->resetForDraw();
 *         fRecorder->priv().pushKeyAndDataBuilder(std::move(fKeyAndDataBuilder));
 *     }
 *
 *     PipelineDataGatherer* gatherer() { return &fKeyAndDataBuilder->first; }
 *     PaintParamsKeyBuilder* builder() { return &fKeyAndDataBuilder->second; }
 *
 *     ScopedDrawBuilder(const ScopedDrawBuilder&) = delete;
 *     ScopedDrawBuilder& operator=(const ScopedDrawBuilder&) = delete;
 *
 * private:
 *     Recorder* fRecorder;
 *     std::unique_ptr<KeyAndDataBuilder> fKeyAndDataBuilder;
 * }
 * ```
 */
public data class ScopedDrawBuilder public constructor(
  /**
   * C++ original:
   * ```cpp
   * Recorder* fRecorder
   * ```
   */
  private var fRecorder: Recorder?,
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<KeyAndDataBuilder> fKeyAndDataBuilder
   * ```
   */
  private var fKeyAndDataBuilder: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * PipelineDataGatherer* gatherer() { return &fKeyAndDataBuilder->first; }
   * ```
   */
  public fun gatherer(): PipelineDataGatherer {
    TODO("Implement gatherer")
  }

  /**
   * C++ original:
   * ```cpp
   * PaintParamsKeyBuilder* builder() { return &fKeyAndDataBuilder->second; }
   * ```
   */
  public fun builder(): PaintParamsKeyBuilder {
    TODO("Implement builder")
  }

  /**
   * C++ original:
   * ```cpp
   * ScopedDrawBuilder& operator=(const ScopedDrawBuilder&) = delete
   * ```
   */
  public fun assign(param0: ScopedDrawBuilder) {
    TODO("Implement assign")
  }
}
