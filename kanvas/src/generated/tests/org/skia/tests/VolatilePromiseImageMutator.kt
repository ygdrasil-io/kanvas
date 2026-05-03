package org.skia.tests

import kotlin.Array
import kotlin.Boolean
import kotlin.BooleanArray
import kotlin.Int
import kotlin.Unit
import org.skia.gpu.BackendTexture
import org.skia.gpu.Caps
import org.skia.gpu.Recorder

/**
 * C++ original:
 * ```cpp
 * class VolatilePromiseImageMutator : public Mutator {
 * public:
 *     static std::unique_ptr<Mutator> Make(skiatest::Reporter* reporter,
 *                                          Recorder* recorder,
 *                                          bool withMips) {
 *         return std::make_unique<VolatilePromiseImageMutator>(reporter, recorder, withMips);
 *     }
 *
 *     VolatilePromiseImageMutator(skiatest::Reporter* reporter, Recorder* recorder, bool withMips)
 *             : Mutator(reporter, recorder, withMips) {
 *     }
 *
 *     ~VolatilePromiseImageMutator() override {
 *         // We need to delete the mutating image first since it holds onto the backend texture
 *         // that was last used to fulfill the volatile promise image.
 *         fMutatingImg.reset();
 *
 *         fCallbackTracker.finishedTest();
 *
 *         for (int i = 0; i < kNumMutations+1; ++i) {
 *             fRecorder->deleteBackendTexture(fBETextures[i]);
 *         }
 *     }
 *
 *     static std::tuple<BackendTexture, void*> fulfill(void* ctx) {
 *         VolatilePromiseImageMutator* mutator = reinterpret_cast<VolatilePromiseImageMutator*>(ctx);
 *
 *         int index = mutator->fCallbackTracker.onFulfillCB();
 *
 *         return { mutator->fBETextures[index], &mutator->fCallbackTracker };
 *     }
 *
 *     static void imageRelease(void* ctx) {
 *         VolatilePromiseImageMutator* mutator = reinterpret_cast<VolatilePromiseImageMutator*>(ctx);
 *
 *         mutator->fCallbackTracker.onImageReleaseCB();
 *     }
 *
 *     static void textureRelease(void* ctx) {
 *         CallbackTracker* callbackTracker = reinterpret_cast<CallbackTracker*>(ctx);
 *
 *         callbackTracker->onTextureReleaseCB();
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
 *         fBETextures[0] = fRecorder->createBackendTexture(kImageSize, info);
 *         REPORTER_ASSERT(fReporter, fBETextures[0].isValid());
 *
 *         update_backend_texture(fReporter, fRecorder, fBETextures[0], kRGBA_8888_SkColorType,
 *                                fWithMips, kInitialColor);
 *
 *         for (int i = 0; i < kNumMutations; ++i) {
 *             fBETextures[i+1] = fRecorder->createBackendTexture(kImageSize, info);
 *             REPORTER_ASSERT(fReporter, fBETextures[i+1].isValid());
 *
 *             update_backend_texture(fReporter, fRecorder, fBETextures[i+1], kRGBA_8888_SkColorType,
 *                                    fWithMips, kMutationColors[i]);
 *         }
 *
 *         fMutatingImg = SkImages::PromiseTextureFrom(fRecorder,
 *                                                     kImageSize,
 *                                                     info,
 *                                                     SkColorInfo(kRGBA_8888_SkColorType,
 *                                                                 kPremul_SkAlphaType,
 *                                                                 /* cs= */ nullptr),
 *                                                     Volatile::kYes,
 *                                                     fulfill,
 *                                                     imageRelease,
 *                                                     textureRelease,
 *                                                     this);
 *         REPORTER_ASSERT(fReporter, fMutatingImg);
 *
 *         return fRecorder->snap();
 *     }
 *
 *     std::unique_ptr<Recording> mutate(int mutationIndex) override {
 *         fCallbackTracker.onMutation();
 *         return nullptr;
 *     }
 *
 *     int getCase() const override { return 2; }
 *
 * private:
 *     class CallbackTracker {
 *     public:
 *         CallbackTracker() {
 *             for (int i = 0; i < kNumMutations+1; ++i) {
 *                 fFulfilled[i] = false;
 *                 fReleased[i] = false;
 *             }
 *         }
 *
 *         void onMutation() {
 *             // In this use case, the active mutation occurs in the volatile promise image callbacks.
 *             ++fMutationCount;
 *         }
 *
 *         int onFulfillCB() {
 *             SkASSERT(fMutationCount < kNumMutations+1);
 *             SkASSERT(fFulfilledCount == fMutationCount);
 *             // For this unit test we should only be fulfilling with each backend texture only once
 *             SkASSERT(!fFulfilled[fFulfilledCount]);
 *             SkASSERT(!fReleased[fFulfilledCount]);
 *
 *             fFulfilled[fFulfilledCount] = true;
 *             return fFulfilledCount++;
 *         }
 *
 *         void onImageReleaseCB() {
 *             SkASSERT(!fImageReleased);
 *             fImageReleased = true;
 *         }
 *
 *         void onTextureReleaseCB() {
 *             SkASSERT(fReleasedCount >= 0 && fReleasedCount < kNumMutations+1);
 *
 *             SkASSERT(fFulfilled[fReleasedCount]);
 *             SkASSERT(!fReleased[fReleasedCount]);
 *             fReleased[fReleasedCount] = true;
 *             fReleasedCount++;
 *         }
 *
 *         void finishedTest() const {
 *             SkASSERT(fMutationCount == kNumMutations);
 *             SkASSERT(fImageReleased);
 *
 *             for (int i = 0; i < kNumMutations+1; ++i) {
 *                 SkASSERT(fFulfilled[i]);
 *                 SkASSERT(fReleased[i]);
 *             }
 *         }
 *
 *     private:
 *         int fMutationCount = 0;
 *         int fFulfilledCount = 0;
 *         bool fImageReleased = false;
 *         int fReleasedCount = 0;
 *         bool fFulfilled[kNumMutations+1];
 *         bool fReleased[kNumMutations+1];
 *     };
 *
 *     CallbackTracker fCallbackTracker;
 *
 *     BackendTexture fBETextures[kNumMutations+1];
 * }
 * ```
 */
public open class VolatilePromiseImageMutator public constructor(
  reporter: Reporter?,
  recorder: Recorder?,
  withMips: Boolean,
) : Mutator(TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * CallbackTracker fCallbackTracker
   * ```
   */
  private var fCallbackTracker: CallbackTracker = TODO("Initialize fCallbackTracker")

  /**
   * C++ original:
   * ```cpp
   * BackendTexture fBETextures[kNumMutations+1]
   * ```
   */
  private var fBETextures: Array<BackendTexture> = TODO("Initialize fBETextures")

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
   *         fBETextures[0] = fRecorder->createBackendTexture(kImageSize, info);
   *         REPORTER_ASSERT(fReporter, fBETextures[0].isValid());
   *
   *         update_backend_texture(fReporter, fRecorder, fBETextures[0], kRGBA_8888_SkColorType,
   *                                fWithMips, kInitialColor);
   *
   *         for (int i = 0; i < kNumMutations; ++i) {
   *             fBETextures[i+1] = fRecorder->createBackendTexture(kImageSize, info);
   *             REPORTER_ASSERT(fReporter, fBETextures[i+1].isValid());
   *
   *             update_backend_texture(fReporter, fRecorder, fBETextures[i+1], kRGBA_8888_SkColorType,
   *                                    fWithMips, kMutationColors[i]);
   *         }
   *
   *         fMutatingImg = SkImages::PromiseTextureFrom(fRecorder,
   *                                                     kImageSize,
   *                                                     info,
   *                                                     SkColorInfo(kRGBA_8888_SkColorType,
   *                                                                 kPremul_SkAlphaType,
   *                                                                 /* cs= */ nullptr),
   *                                                     Volatile::kYes,
   *                                                     fulfill,
   *                                                     imageRelease,
   *                                                     textureRelease,
   *                                                     this);
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
   *         fCallbackTracker.onMutation();
   *         return nullptr;
   *     }
   * ```
   */
  public override fun mutate(mutationIndex: Int): Int {
    TODO("Implement mutate")
  }

  /**
   * C++ original:
   * ```cpp
   * int getCase() const override { return 2; }
   * ```
   */
  public override fun getCase(): Int {
    TODO("Implement getCase")
  }

  public data class CallbackTracker public constructor(
    private var fMutationCount: Int,
    private var fFulfilledCount: Int,
    private var fImageReleased: Boolean,
    private var fReleasedCount: Int,
    private var fFulfilled: BooleanArray,
    private var fReleased: BooleanArray,
  ) {
    public fun onMutation() {
      TODO("Implement onMutation")
    }

    public fun onFulfillCB(): Int {
      TODO("Implement onFulfillCB")
    }

    public fun onImageReleaseCB() {
      TODO("Implement onImageReleaseCB")
    }

    public fun onTextureReleaseCB() {
      TODO("Implement onTextureReleaseCB")
    }

    public fun finishedTest() {
      TODO("Implement finishedTest")
    }
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static std::unique_ptr<Mutator> Make(skiatest::Reporter* reporter,
     *                                          Recorder* recorder,
     *                                          bool withMips) {
     *         return std::make_unique<VolatilePromiseImageMutator>(reporter, recorder, withMips);
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

    /**
     * C++ original:
     * ```cpp
     * static std::tuple<BackendTexture, void*> fulfill(void* ctx) {
     *         VolatilePromiseImageMutator* mutator = reinterpret_cast<VolatilePromiseImageMutator*>(ctx);
     *
     *         int index = mutator->fCallbackTracker.onFulfillCB();
     *
     *         return { mutator->fBETextures[index], &mutator->fCallbackTracker };
     *     }
     * ```
     */
    public fun fulfill(ctx: Unit?): Int {
      TODO("Implement fulfill")
    }

    /**
     * C++ original:
     * ```cpp
     * static void imageRelease(void* ctx) {
     *         VolatilePromiseImageMutator* mutator = reinterpret_cast<VolatilePromiseImageMutator*>(ctx);
     *
     *         mutator->fCallbackTracker.onImageReleaseCB();
     *     }
     * ```
     */
    public fun imageRelease(ctx: Unit?) {
      TODO("Implement imageRelease")
    }

    /**
     * C++ original:
     * ```cpp
     * static void textureRelease(void* ctx) {
     *         CallbackTracker* callbackTracker = reinterpret_cast<CallbackTracker*>(ctx);
     *
     *         callbackTracker->onTextureReleaseCB();
     *     }
     * ```
     */
    public fun textureRelease(ctx: Unit?) {
      TODO("Implement textureRelease")
    }
  }
}
