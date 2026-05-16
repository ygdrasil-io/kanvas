package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import org.skia.gpu.BackendTexture
import org.skia.gpu.Caps
import org.skia.gpu.Recorder

/**
 * C++ original:
 * ```cpp
 * class UpdateBackendTextureMutator : public Mutator {
 * public:
 *     static std::unique_ptr<Mutator> Make(skiatest::Reporter* reporter,
 *                                          Recorder* recorder,
 *                                          bool withMips) {
 *         return std::make_unique<UpdateBackendTextureMutator>(reporter, recorder, withMips);
 *     }
 *
 *     UpdateBackendTextureMutator(skiatest::Reporter* reporter, Recorder* recorder, bool withMips)
 *             : Mutator(reporter, recorder, withMips) {
 *     }
 *     ~UpdateBackendTextureMutator() override {
 *         fRecorder->deleteBackendTexture(fBETexture);
 *     }
 *
 *     std::unique_ptr<Recording> init(const Caps* caps) override {
 *         skgpu::Protected isProtected = skgpu::Protected(caps->protectedSupport());
 *
 *         // Note: not renderable
 *         TextureInfo info = caps->getDefaultSampledTextureInfo(kRGBA_8888_SkColorType,
 *                                                               fWithMips ? Mipmapped::kYes
 *                                                                         : Mipmapped::kNo,
 *                                                               isProtected,
 *                                                               skgpu::Renderable::kNo);
 *         REPORTER_ASSERT(fReporter, info.isValid());
 *
 *         fBETexture = fRecorder->createBackendTexture(kImageSize, info);
 *         REPORTER_ASSERT(fReporter, fBETexture.isValid());
 *
 *         update_backend_texture(fReporter, fRecorder, fBETexture, kRGBA_8888_SkColorType,
 *                                fWithMips, kInitialColor);
 *
 *         fMutatingImg = SkImages::WrapTexture(fRecorder,
 *                                              fBETexture,
 *                                              kRGBA_8888_SkColorType,
 *                                              kPremul_SkAlphaType,
 *                                              /* colorSpace= */ nullptr);
 *         REPORTER_ASSERT(fReporter, fMutatingImg);
 *
 *         return fRecorder->snap();
 *     }
 *
 *     std::unique_ptr<Recording> mutate(int mutationIndex) override {
 *         update_backend_texture(fReporter, fRecorder, fBETexture, kRGBA_8888_SkColorType,
 *                                fWithMips, kMutationColors[mutationIndex]);
 *         return fRecorder->snap();
 *     }
 *
 *     int getCase() const override { return 1; }
 *
 * private:
 *     BackendTexture fBETexture;
 * }
 * ```
 */
public open class UpdateBackendTextureMutator public constructor(
  reporter: Reporter?,
  recorder: Recorder?,
  withMips: Boolean,
) : Mutator(TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * BackendTexture fBETexture
   * ```
   */
  private var fBETexture: BackendTexture = TODO("Initialize fBETexture")

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<Recording> init(const Caps* caps) override {
   *         skgpu::Protected isProtected = skgpu::Protected(caps->protectedSupport());
   *
   *         // Note: not renderable
   *         TextureInfo info = caps->getDefaultSampledTextureInfo(kRGBA_8888_SkColorType,
   *                                                               fWithMips ? Mipmapped::kYes
   *                                                                         : Mipmapped::kNo,
   *                                                               isProtected,
   *                                                               skgpu::Renderable::kNo);
   *         REPORTER_ASSERT(fReporter, info.isValid());
   *
   *         fBETexture = fRecorder->createBackendTexture(kImageSize, info);
   *         REPORTER_ASSERT(fReporter, fBETexture.isValid());
   *
   *         update_backend_texture(fReporter, fRecorder, fBETexture, kRGBA_8888_SkColorType,
   *                                fWithMips, kInitialColor);
   *
   *         fMutatingImg = SkImages::WrapTexture(fRecorder,
   *                                              fBETexture,
   *                                              kRGBA_8888_SkColorType,
   *                                              kPremul_SkAlphaType,
   *                                              /* colorSpace= */ nullptr);
   *         REPORTER_ASSERT(fReporter, fMutatingImg);
   *
   *         return fRecorder->snap();
   *     }
   * ```
   */
  public override fun `init`(caps: Caps?): Int {
    TODO("Implement init")
  }

  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<Recording> mutate(int mutationIndex) override {
   *         update_backend_texture(fReporter, fRecorder, fBETexture, kRGBA_8888_SkColorType,
   *                                fWithMips, kMutationColors[mutationIndex]);
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
   * int getCase() const override { return 1; }
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
     *         return std::make_unique<UpdateBackendTextureMutator>(reporter, recorder, withMips);
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
