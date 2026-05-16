package org.skia.tools

import kotlin.Array
import kotlin.Unit
import org.skia.core.SkTextureCompressionType
import org.skia.foundation.SkData
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkNVRefCnt
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.gpu.BackendTexture
import org.skia.gpu.CallbackResult
import org.skia.gpu.Context
import org.skia.gpu.RefCntedCallback
import org.skia.gpu.ganesh.Mipmapped
import org.skia.gpu.ganesh.Protected
import org.skia.gpu.ganesh.Renderable
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ManagedGraphiteTexture : public SkNVRefCnt<ManagedGraphiteTexture> {
 * public:
 *     static sk_sp<ManagedGraphiteTexture> MakeUnInit(Recorder*,
 *                                                     const SkImageInfo&,
 *                                                     skgpu::Mipmapped,
 *                                                     skgpu::Renderable,
 *                                                     skgpu::Protected = skgpu::Protected::kNo);
 *
 *     static sk_sp<ManagedGraphiteTexture> MakeFromPixmap(Recorder*,
 *                                                         const SkPixmap&,
 *                                                         skgpu::Mipmapped,
 *                                                         skgpu::Renderable,
 *                                                         skgpu::Protected = skgpu::Protected::kNo);
 *
 *     static sk_sp<ManagedGraphiteTexture> MakeMipmappedFromPixmaps(
 *             Recorder*,
 *             SkSpan<const SkPixmap> levels,
 *             skgpu::Renderable,
 *             skgpu::Protected = skgpu::Protected::kNo);
 *
 *     static sk_sp<ManagedGraphiteTexture> MakeFromCompressedData(
 *             Recorder*,
 *             SkISize dimmensions,
 *             SkTextureCompressionType,
 *             sk_sp<SkData>,
 *             skgpu::Mipmapped,
 *             skgpu::Protected = skgpu::Protected::kNo);
 *
 *     /** finished and image/surface release procs */
 *     static void FinishedProc(void* context, skgpu::CallbackResult);
 *     static void ReleaseProc(void* context);
 *     static void ImageReleaseProc(void* context);
 *
 *     ~ManagedGraphiteTexture();
 *
 *     /**
 *      * The context to use with the ReleaseProcs. This adds a ref so it *must* be balanced by a call
 *      * to TextureReleaseProc or ImageReleaseProc.
 *      */
 *     void* releaseContext() const;
 *
 *     sk_sp<skgpu::RefCntedCallback> refCountedCallback() const;
 *
 *     /**
 *      * SkImage::MakeGraphiteFromYUVABackendTextures takes a single release proc that is called once
 *      * for all the textures. This makes a single release context for the group of textures. It's
 *      * used with the standard ReleaseProc. Like releaseContext(), it must be balanced by a
 *      * ReleaseProc call for proper ref counting.
 *      */
 *     static void* MakeYUVAReleaseContext(const sk_sp<ManagedGraphiteTexture>[SkYUVAInfo::kMaxPlanes]);
 *
 *     const skgpu::graphite::BackendTexture& texture() { return fTexture; }
 *
 * private:
 *     ManagedGraphiteTexture() = default;
 *     ManagedGraphiteTexture(const ManagedGraphiteTexture&) = delete;
 *     ManagedGraphiteTexture(ManagedGraphiteTexture&&) = delete;
 *
 *     skgpu::graphite::Context* fContext;
 *     skgpu::graphite::BackendTexture fTexture;
 * }
 * ```
 */
public open class ManagedGraphiteTexture public constructor() : SkNVRefCnt(),
    ManagedGraphiteTexture {
  /**
   * C++ original:
   * ```cpp
   * skgpu::graphite::Context* fContext
   * ```
   */
  private var fContext: Context? = TODO("Initialize fContext")

  /**
   * C++ original:
   * ```cpp
   * skgpu::graphite::BackendTexture fTexture
   * ```
   */
  private var fTexture: BackendTexture = TODO("Initialize fTexture")

  /**
   * C++ original:
   * ```cpp
   * ManagedGraphiteTexture() = default
   * ```
   */
  public constructor(param0: ManagedGraphiteTexture) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * void* ManagedGraphiteTexture::releaseContext() const {
   *     return new MBETContext{{sk_ref_sp(this)}};
   * }
   * ```
   */
  public override fun releaseContext() {
    TODO("Implement releaseContext")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<skgpu::RefCntedCallback> ManagedGraphiteTexture::refCountedCallback() const {
   *     return skgpu::RefCntedCallback::Make(FinishedProc, this->releaseContext());
   * }
   * ```
   */
  public override fun refCountedCallback(): SkSp<RefCntedCallback> {
    TODO("Implement refCountedCallback")
  }

  /**
   * C++ original:
   * ```cpp
   * const skgpu::graphite::BackendTexture& texture() { return fTexture; }
   * ```
   */
  public override fun texture(): BackendTexture {
    TODO("Implement texture")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<ManagedGraphiteTexture> ManagedGraphiteTexture::MakeUnInit(Recorder* recorder,
     *                                                                  const SkImageInfo& ii,
     *                                                                  Mipmapped mipmapped,
     *                                                                  Renderable renderable,
     *                                                                  Protected isProtected) {
     *     sk_sp<ManagedGraphiteTexture> mbet(new ManagedGraphiteTexture);
     *     mbet->fContext = recorder->priv().context();
     *     const skgpu::graphite::Caps* caps = recorder->priv().caps();
     *
     *     skgpu::graphite::TextureInfo info = caps->getDefaultSampledTextureInfo(ii.colorType(),
     *                                                                            mipmapped,
     *                                                                            isProtected,
     *                                                                            renderable);
     *
     *     mbet->fTexture = recorder->createBackendTexture(ii.dimensions(), info);
     *     if (!mbet->fTexture.isValid()) {
     *         return nullptr;
     *     }
     *
     *     recorder->addFinishInfo({mbet->releaseContext(), FinishedProc});
     *
     *     return mbet;
     * }
     * ```
     */
    public override fun makeUnInit(
      recorder: Recorder?,
      ii: SkImageInfo,
      mipmapped: Mipmapped,
      renderable: Renderable,
      isProtected: Protected = TODO(),
    ): SkSp<ManagedGraphiteTexture> {
      TODO("Implement makeUnInit")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<ManagedGraphiteTexture> ManagedGraphiteTexture::MakeFromPixmap(Recorder* recorder,
     *                                                                      const SkPixmap& src,
     *                                                                      Mipmapped mipmapped,
     *                                                                      Renderable renderable,
     *                                                                      Protected isProtected) {
     *     sk_sp<ManagedGraphiteTexture> mbet = MakeUnInit(recorder, src.info(), mipmapped, renderable,
     *                                                     isProtected);
     *     if (!mbet) {
     *         return nullptr;
     *     }
     *
     *     std::vector<SkPixmap> levels({src});
     *     std::unique_ptr<SkMipmap> mm;
     *
     *     if (mipmapped == Mipmapped::kYes) {
     *         mm.reset(SkMipmap::Build(src, nullptr));
     *         if (!mm) {
     *             return nullptr;
     *         }
     *         for (int i = 0; i < mm->countLevels(); ++i) {
     *             SkMipmap::Level level;
     *             SkAssertResult(mm->getLevel(i, &level));
     *             levels.push_back(level.fPixmap);
     *         }
     *     }
     *
     *     if (!recorder->updateBackendTexture(mbet->fTexture,
     *                                         levels.data(),
     *                                         static_cast<int>(levels.size()))) {
     *         return nullptr;
     *     }
     *
     *     return mbet;
     * }
     * ```
     */
    public override fun makeFromPixmap(
      recorder: Recorder?,
      src: SkPixmap,
      mipmapped: Mipmapped,
      renderable: Renderable,
      isProtected: Protected = TODO(),
    ): SkSp<ManagedGraphiteTexture> {
      TODO("Implement makeFromPixmap")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<ManagedGraphiteTexture> ManagedGraphiteTexture::MakeMipmappedFromPixmaps(
     *         Recorder* recorder,
     *         SkSpan<const SkPixmap> levels,
     *         skgpu::Renderable renderable,
     *         skgpu::Protected isProtected) {
     *     if (levels.empty()) {
     *         return nullptr;
     *     }
     *     sk_sp<ManagedGraphiteTexture> mbet = MakeUnInit(recorder,
     *                                                     levels[0].info(),
     *                                                     Mipmapped::kYes,
     *                                                     renderable,
     *                                                     isProtected);
     *     if (!recorder->updateBackendTexture(mbet->fTexture,
     *                                         levels.data(),
     *                                         static_cast<int>(levels.size()))) {
     *         return nullptr;
     *     }
     *
     *     return mbet;
     * }
     * ```
     */
    public override fun makeMipmappedFromPixmaps(
      recorder: Recorder?,
      levels: SkSpan<SkPixmap>,
      renderable: Renderable,
      isProtected: Protected = TODO(),
    ): SkSp<ManagedGraphiteTexture> {
      TODO("Implement makeMipmappedFromPixmaps")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<ManagedGraphiteTexture> ManagedGraphiteTexture::MakeFromCompressedData(
     *         Recorder* recorder,
     *         SkISize dimensions,
     *         SkTextureCompressionType compression,
     *         sk_sp<SkData> src,
     *         skgpu::Mipmapped mipmapped,
     *         skgpu::Protected isProtected) {
     *     sk_sp<ManagedGraphiteTexture> mbet(new ManagedGraphiteTexture);
     *     mbet->fContext = recorder->priv().context();
     *     const skgpu::graphite::Caps* caps = recorder->priv().caps();
     *
     *     skgpu::graphite::TextureInfo info = caps->getDefaultCompressedTextureInfo(compression,
     *                                                                               mipmapped,
     *                                                                               isProtected);
     *
     *     mbet->fTexture = recorder->createBackendTexture(dimensions, info);
     *     if (!mbet->fTexture.isValid()) {
     *         return nullptr;
     *     }
     *
     *     recorder->addFinishInfo({mbet->releaseContext(), FinishedProc});
     *
     *     if (!recorder->updateCompressedBackendTexture(mbet->fTexture,
     *                                                   src->data(),
     *                                                   src->size())) {
     *         return nullptr;
     *     }
     *
     *     return mbet;
     * }
     * ```
     */
    public override fun makeFromCompressedData(
      recorder: Recorder?,
      dimmensions: SkISize,
      compression: SkTextureCompressionType,
      src: SkSp<SkData>,
      mipmapped: Mipmapped,
      isProtected: Protected = TODO(),
    ): SkSp<ManagedGraphiteTexture> {
      TODO("Implement makeFromCompressedData")
    }

    /**
     * C++ original:
     * ```cpp
     * void ManagedGraphiteTexture::FinishedProc(void* ctx, skgpu::CallbackResult) {
     *     std::unique_ptr<MBETContext> context(static_cast<MBETContext*>(ctx));
     * }
     * ```
     */
    public override fun finishedProc(context: Unit?, param1: CallbackResult) {
      TODO("Implement finishedProc")
    }

    /**
     * C++ original:
     * ```cpp
     * void ManagedGraphiteTexture::ReleaseProc(void* ctx) {
     *     std::unique_ptr<MBETContext> context(static_cast<MBETContext*>(ctx));
     * }
     * ```
     */
    public override fun releaseProc(context: Unit?) {
      TODO("Implement releaseProc")
    }

    /**
     * C++ original:
     * ```cpp
     * void ManagedGraphiteTexture::ImageReleaseProc(void* ctx) {
     *     std::unique_ptr<MBETContext> context(static_cast<MBETContext*>(ctx));
     * }
     * ```
     */
    public override fun imageReleaseProc(context: Unit?) {
      TODO("Implement imageReleaseProc")
    }

    /**
     * C++ original:
     * ```cpp
     * void* ManagedGraphiteTexture::MakeYUVAReleaseContext(
     *         const sk_sp<ManagedGraphiteTexture> mbets[SkYUVAInfo::kMaxPlanes]) {
     *     return new MBETContext(mbets);
     * }
     * ```
     */
    public override fun makeYUVAReleaseContext(mbets: Array<SkSp<ManagedGraphiteTexture>>) {
      TODO("Implement makeYUVAReleaseContext")
    }
  }
}
