package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkSurface
import org.skia.foundation.SkSp
import org.skia.gpu.Recorder

/**
 * C++ original:
 * ```cpp
 * class SurfaceMutator : public Mutator {
 * public:
 *     static std::unique_ptr<Mutator> Make(skiatest::Reporter* reporter,
 *                                          Recorder* recorder,
 *                                          bool withMips) {
 *         return std::make_unique<SurfaceMutator>(reporter, recorder, withMips);
 *     }
 *
 *     SurfaceMutator(skiatest::Reporter* reporter, Recorder* recorder, bool withMips)
 *             : Mutator(reporter, recorder, withMips) {
 *     }
 *
 *     std::unique_ptr<Recording> init(const Caps* /* caps */) override {
 *         SkImageInfo ii = SkImageInfo::Make(kImageSize, kRGBA_8888_SkColorType, kPremul_SkAlphaType);
 *
 *         fMutatingSurface = SkSurfaces::RenderTarget(
 *                 fRecorder, ii, fWithMips ? Mipmapped::kYes : Mipmapped::kNo);
 *         REPORTER_ASSERT(fReporter, fMutatingSurface);
 *
 *         fMutatingSurface->getCanvas()->clear(kInitialColor);
 *
 *         fMutatingImg = SkSurfaces::AsImage(fMutatingSurface);
 *         REPORTER_ASSERT(fReporter, fMutatingImg);
 *
 *         return fRecorder->snap();
 *     }
 *
 *     std::unique_ptr<Recording> mutate(int mutationIndex) override {
 *         fMutatingSurface->getCanvas()->clear(kMutationColors[mutationIndex]);
 *         return fRecorder->snap();
 *     }
 *
 *     int getCase() const override { return 3; }
 *
 * private:
 *     sk_sp<SkSurface> fMutatingSurface;
 * }
 * ```
 */
public open class SurfaceMutator public constructor(
  reporter: Reporter?,
  recorder: Recorder?,
  withMips: Boolean,
) : Mutator(TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSurface> fMutatingSurface
   * ```
   */
  private var fMutatingSurface: SkSp<SkSurface> = TODO("Initialize fMutatingSurface")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<Recording> init(const Caps* /* caps */) override {
   *         SkImageInfo ii = SkImageInfo::Make(kImageSize, kRGBA_8888_SkColorType, kPremul_SkAlphaType);
   *
   *         fMutatingSurface = SkSurfaces::RenderTarget(
   *                 fRecorder, ii, fWithMips ? Mipmapped::kYes : Mipmapped::kNo);
   *         REPORTER_ASSERT(fReporter, fMutatingSurface);
   *
   *         fMutatingSurface->getCanvas()->clear(kInitialColor);
   *
   *         fMutatingImg = SkSurfaces::AsImage(fMutatingSurface);
   *         REPORTER_ASSERT(fReporter, fMutatingImg);
   *
   *         return fRecorder->snap();
   *     }
   * ```
   */
  public override fun `init`(param0: Int): Int {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<Recording> mutate(int mutationIndex) override {
   *         fMutatingSurface->getCanvas()->clear(kMutationColors[mutationIndex]);
   *         return fRecorder->snap();
   *     }
   * ```
   */
  public override fun mutate(mutationIndex: Int): Int {
    TODO("Implement mutate")
  }

  /**
   * C++ original:
   * ```cpp
   * int getCase() const override { return 3; }
   * ```
   */
  public override fun getCase(): Int {
    TODO("Implement getCase")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static std::unique_ptr<Mutator> Make(skiatest::Reporter* reporter,
     *                                          Recorder* recorder,
     *                                          bool withMips) {
     *         return std::make_unique<SurfaceMutator>(reporter, recorder, withMips);
     *     }
     * ```
     */
    public fun make(
      reporter: Reporter?,
      recorder: Recorder?,
      withMips: Boolean,
    ): Int {
      TODO("Implement make")
    }
  }
}
