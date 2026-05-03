package org.skia.tools

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkYUVAPixmaps
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkData
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.gpu.ganesh.GrDirectContext
import org.skia.gpu.ganesh.GrRecordingContext
import org.skia.gpu.ganesh.Mipmapped
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class LazyYUVImage {
 * public:
 *     // Returns null if the data could not be extracted into YUVA planes
 *     static std::unique_ptr<LazyYUVImage> Make(sk_sp<SkData> data,
 *                                               skgpu::Mipmapped = skgpu::Mipmapped::kNo,
 *                                               sk_sp<SkColorSpace> = nullptr);
 *     static std::unique_ptr<LazyYUVImage> Make(SkYUVAPixmaps,
 *                                               skgpu::Mipmapped = skgpu::Mipmapped::kNo,
 *                                               sk_sp<SkColorSpace> = nullptr);
 *
 *     enum class Type { kFromPixmaps, kFromGenerator, kFromTextures, kFromImages };
 *
 *     SkISize dimensions() const { return fPixmaps.yuvaInfo().dimensions(); }
 *
 *     sk_sp<SkImage> refImage(GrRecordingContext* rContext, Type);
 *
 *     sk_sp<SkImage> refImage(GrDirectContext* dContext, Type);
 *
 * #if defined(SK_GRAPHITE)
 *     sk_sp<SkImage> refImage(skgpu::graphite::Recorder* recorder, Type);
 * #endif
 *
 * private:
 *     // Decoded YUV data
 *     SkYUVAPixmaps fPixmaps;
 *
 *     skgpu::Mipmapped fMipmapped;
 *
 *     sk_sp<SkColorSpace> fColorSpace;
 *
 *     // Memoized SkImages formed with planes, one for each Type.
 *     sk_sp<SkImage> fYUVImage[4];
 *
 *     LazyYUVImage() = default;
 *
 *     bool reset(sk_sp<SkData> data, skgpu::Mipmapped, sk_sp<SkColorSpace>);
 *     bool reset(SkYUVAPixmaps pixmaps, skgpu::Mipmapped, sk_sp<SkColorSpace>);
 *
 *     bool ensureYUVImage(GrRecordingContext* rContext, Type type);
 *
 * #if defined(SK_GRAPHITE)
 *     bool ensureYUVImage(skgpu::graphite::Recorder* recorder, Type type);
 * #endif
 * }
 * ```
 */
public data class LazyYUVImage public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkYUVAPixmaps fPixmaps
   * ```
   */
  private var fPixmaps: SkYUVAPixmaps,
  /**
   * C++ original:
   * ```cpp
   * skgpu::Mipmapped fMipmapped
   * ```
   */
  private var fMipmapped: Mipmapped,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorSpace> fColorSpace
   * ```
   */
  private var fColorSpace: SkSp<SkColorSpace>,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fYUVImage[4]
   * ```
   */
  private var fYUVImage: Array<SkSp<SkImage>>,
) {
  /**
   * C++ original:
   * ```cpp
   * SkISize dimensions() const { return fPixmaps.yuvaInfo().dimensions(); }
   * ```
   */
  public fun dimensions(): SkISize {
    TODO("Implement dimensions")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> LazyYUVImage::refImage(GrRecordingContext* rContext, Type type) {
   *     if (this->ensureYUVImage(rContext, type)) {
   *         size_t idx = static_cast<size_t>(type);
   *         SkASSERT(idx < std::size(fYUVImage));
   *         return fYUVImage[idx];
   *     } else {
   *         return nullptr;
   *     }
   * }
   * ```
   */
  public fun refImage(rContext: GrRecordingContext?, type: Type): SkSp<SkImage> {
    TODO("Implement refImage")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> LazyYUVImage::refImage(GrDirectContext* dContext, Type type) {
   * #if defined(SK_GANESH)
   *     return this->refImage(static_cast<GrRecordingContext *>(dContext), type);
   * #else
   *     SkASSERT(!dContext);
   *     return this->refImage(static_cast<GrRecordingContext *>(nullptr), type);
   * #endif
   * }
   * ```
   */
  public fun refImage(dContext: GrDirectContext?, type: Type): SkSp<SkImage> {
    TODO("Implement refImage")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> LazyYUVImage::refImage(skgpu::graphite::Recorder* recorder, Type type) {
   *     if (this->ensureYUVImage(recorder, type)) {
   *         size_t idx = static_cast<size_t>(type);
   *         SkASSERT(idx < std::size(fYUVImage));
   *         return fYUVImage[idx];
   *     } else {
   *         return nullptr;
   *     }
   * }
   * ```
   */
  public fun refImage(recorder: org.skia.gpu.Recorder?, type: Type): SkSp<SkImage> {
    TODO("Implement refImage")
  }

  /**
   * C++ original:
   * ```cpp
   * bool LazyYUVImage::reset(sk_sp<SkData> data, skgpu::Mipmapped mipmapped, sk_sp<SkColorSpace> cs) {
   *     fMipmapped = mipmapped;
   *     auto codec = SkCodecImageGenerator::MakeFromEncodedCodec(data);
   *     if (!codec) {
   *         return false;
   *     }
   *
   *     SkYUVAPixmapInfo yuvaPixmapInfo;
   *     if (!codec->queryYUVAInfo(SkYUVAPixmapInfo::SupportedDataTypes::All(), &yuvaPixmapInfo)) {
   *         return false;
   *     }
   *     fPixmaps = SkYUVAPixmaps::Allocate(yuvaPixmapInfo);
   *     if (!fPixmaps.isValid()) {
   *         return false;
   *     }
   *
   *     if (!codec->getYUVAPlanes(fPixmaps)) {
   *         return false;
   *     }
   *
   *     fColorSpace = std::move(cs);
   *
   *     // The SkPixmap data is fully configured now for MakeFromYUVAPixmaps once we get a GrContext
   *     return true;
   * }
   * ```
   */
  private fun reset(
    `data`: SkSp<SkData>,
    mipmapped: Mipmapped,
    cs: SkSp<SkColorSpace>,
  ): Boolean {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * bool LazyYUVImage::reset(SkYUVAPixmaps pixmaps,
   *                          skgpu::Mipmapped mipmapped,
   *                          sk_sp<SkColorSpace> cs) {
   *     if (!pixmaps.isValid()) {
   *         return false;
   *     }
   *     fMipmapped = mipmapped;
   *     if (pixmaps.ownsStorage()) {
   *         fPixmaps = std::move(pixmaps);
   *     } else {
   *         fPixmaps = SkYUVAPixmaps::MakeCopy(std::move(pixmaps));
   *     }
   *     fColorSpace = std::move(cs);
   *     // The SkPixmap data is fully configured now for MakeFromYUVAPixmaps once we get a GrContext
   *     return true;
   * }
   * ```
   */
  private fun reset(
    pixmaps: SkYUVAPixmaps,
    mipmapped: Mipmapped,
    cs: SkSp<SkColorSpace>,
  ): Boolean {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * bool LazyYUVImage::ensureYUVImage(GrRecordingContext* rContext, Type type) {
   *     size_t idx = static_cast<size_t>(type);
   *     SkASSERT(idx < std::size(fYUVImage));
   * #if defined(SK_GANESH)
   *     if (fYUVImage[idx] && fYUVImage[idx]->isValid(rContext->asRecorder())) {
   *         return true;  // Have already made a YUV image valid for this context.
   *     }
   * #endif
   *     // Try to make a new YUV image for this context.
   *     switch (type) {
   *         case Type::kFromPixmaps:
   * #if defined(SK_GANESH)
   *             if (!rContext || rContext->abandoned()) {
   *                 return false;
   *             }
   *             fYUVImage[idx] = SkImages::TextureFromYUVAPixmaps(rContext,
   *                                                               fPixmaps,
   *                                                               fMipmapped,
   *                                                               /*limit to max tex size*/ false,
   *                                                               fColorSpace);
   * #else
   *             return false;
   * #endif
   *             break;
   *         case Type::kFromGenerator: {
   *             // Make sure the generator has ownership of its backing planes.
   *             auto generator = std::make_unique<Generator>(fPixmaps, fColorSpace);
   *             fYUVImage[idx] = SkImages::DeferredFromGenerator(std::move(generator));
   *             break;
   *         }
   *         case Type::kFromTextures:
   * #if defined(SK_GANESH)
   *             if (!rContext || rContext->abandoned()) {
   *                 return false;
   *             }
   *             if (auto direct = rContext->asDirectContext()) {
   *                 sk_sp<sk_gpu_test::ManagedBackendTexture> mbets[SkYUVAInfo::kMaxPlanes];
   *                 GrBackendTexture textures[SkYUVAInfo::kMaxPlanes];
   *                 for (int i = 0; i < fPixmaps.numPlanes(); ++i) {
   *                     mbets[i] = sk_gpu_test::ManagedBackendTexture::MakeFromPixmap(
   *                             direct,
   *                             fPixmaps.plane(i),
   *                             fMipmapped,
   *                             skgpu::Renderable::kNo,
   *                             skgpu::Protected::kNo);
   *                     if (mbets[i]) {
   *                         textures[i] = mbets[i]->texture();
   *                     } else {
   *                         return false;
   *                     }
   *                 }
   *                 GrYUVABackendTextures yuvaTextures(fPixmaps.yuvaInfo(),
   *                                                    textures,
   *                                                    kTopLeft_GrSurfaceOrigin);
   *                 if (!yuvaTextures.isValid()) {
   *                     return false;
   *                 }
   *                 void* planeRelContext =
   *                         sk_gpu_test::ManagedBackendTexture::MakeYUVAReleaseContext(mbets);
   *                 fYUVImage[idx] = SkImages::TextureFromYUVATextures(
   *                         direct,
   *                         yuvaTextures,
   *                         fColorSpace,
   *                         sk_gpu_test::ManagedBackendTexture::ReleaseProc,
   *                         planeRelContext);
   *             }
   * #else
   *             return false;
   * #endif
   *             break;
   *         case Type::kFromImages:
   *             // Not supported in Ganesh
   *             return false;
   *     }
   *     return fYUVImage[idx] != nullptr;
   * }
   * ```
   */
  private fun ensureYUVImage(rContext: GrRecordingContext?, type: Type): Boolean {
    TODO("Implement ensureYUVImage")
  }

  /**
   * C++ original:
   * ```cpp
   * bool ensureYUVImage(skgpu::graphite::Recorder* recorder, Type type)
   * ```
   */
  private fun ensureYUVImage(recorder: org.skia.gpu.Recorder?, type: Type): Boolean {
    TODO("Implement ensureYUVImage")
  }

  /**
   * C++ original:
   * ```cpp
   * bool LazyYUVImage::ensureYUVImage(Recorder* recorder, Type type) {
   *     size_t idx = static_cast<size_t>(type);
   *     SkASSERT(idx < std::size(fYUVImage));
   *     if (fYUVImage[idx] && as_IB(fYUVImage[idx])->isGraphiteBacked()) {
   *         return true;  // Have already made a YUV image suitable for Graphite.
   *     }
   *     // Try to make a new Graphite YUV image
   *     switch (type) {
   *         case Type::kFromPixmaps:
   *             if (!recorder) {
   *                 return false;
   *             }
   *             fYUVImage[idx] =
   *                     SkImages::TextureFromYUVAPixmaps(recorder,
   *                                                      fPixmaps,
   *                                                      {fMipmapped == skgpu::Mipmapped::kYes},
   *                                                      /*limitToMaxTextureSize=*/false,
   *                                                      fColorSpace);
   *             break;
   *         case Type::kFromGenerator: {
   *             // Make sure the generator has ownership of its backing planes.
   *             auto generator = std::make_unique<Generator>(fPixmaps, fColorSpace);
   *             fYUVImage[idx] = SkImages::DeferredFromGenerator(std::move(generator));
   *             break;
   *         }
   *         case Type::kFromTextures: {
   *             if (!recorder) {
   *                 return false;
   *             }
   *
   *             sk_sp<sk_gpu_test::ManagedGraphiteTexture> mbets[SkYUVAInfo::kMaxPlanes];
   *             BackendTexture textures[SkYUVAInfo::kMaxPlanes];
   *             for (int i = 0; i < fPixmaps.numPlanes(); ++i) {
   *                 // MakeFromPixmap will handle generating the upper mipmap levels if necessary.
   *                 mbets[i] = sk_gpu_test::ManagedGraphiteTexture::MakeFromPixmap(
   *                         recorder,
   *                         fPixmaps.plane(i),
   *                         fMipmapped,
   *                         skgpu::Renderable::kNo,
   *                         skgpu::Protected::kNo);
   *                 if (mbets[i]) {
   *                     textures[i] = mbets[i]->texture();
   *                 } else {
   *                     return false;
   *                 }
   *             }
   *             YUVABackendTextures yuvaTextures(fPixmaps.yuvaInfo(), textures);
   *             if (!yuvaTextures.isValid()) {
   *                 return false;
   *             }
   *             void* imageRelContext =
   *                     sk_gpu_test::ManagedGraphiteTexture::MakeYUVAReleaseContext(mbets);
   *             fYUVImage[idx] = SkImages::TextureFromYUVATextures(
   *                     recorder,
   *                     yuvaTextures,
   *                     fColorSpace,
   *                     sk_gpu_test::ManagedGraphiteTexture::ImageReleaseProc,
   *                     imageRelContext);
   *             break;
   *         }
   *         case Type::kFromImages: {
   *             if (!recorder) {
   *                 return false;
   *             }
   *
   *             sk_sp<SkImage> planeImgs[SkYUVAInfo::kMaxPlanes];
   *
   *             using SkImages::GenerateMipmapsFromBase;
   *             GenerateMipmapsFromBase genMipmaps = GenerateMipmapsFromBase::kNo;
   *             if (fMipmapped == skgpu::Mipmapped::kYes) {
   *                 genMipmaps = GenerateMipmapsFromBase::kYes;
   *             }
   *
   *             for (int i = 0; i < fPixmaps.numPlanes(); ++i) {
   *                 const auto& plane = fPixmaps.plane(i);
   *                 sk_sp<ManagedGraphiteTexture> mbet;
   *                 if (fMipmapped == skgpu::Mipmapped::kYes) {
   *                     mbet = ManagedGraphiteTexture::MakeUnInit(recorder,
   *                                                               plane.info(),
   *                                                               skgpu::Mipmapped::kYes,
   *                                                               skgpu::Renderable::kNo);
   *                     // We allocate a full mip set because updateBackendTexture requires it. However,
   *                     // the non-base levels are cleared to red. We rely on SkImages::WrapTexture
   *                     // to actually generate the contents from the base level for each plane on the
   *                     // GPU. This exercises the case where the client wants a mipmapped YUV image but
   *                     // only provides the base level contents.
   *                     int levelCnt = SkMipmap::ComputeLevelCount(plane.dimensions());
   *                     skia_private::TArray<SkAutoPixmapStorage> levelStorage(levelCnt);
   *                     skia_private::TArray<SkPixmap> levels(levelCnt + 1);
   *                     levels.push_back(plane);
   *                     for (int l = 0; l < levelCnt; ++l) {
   *                         SkISize dims = SkMipmap::ComputeLevelSize(plane.dimensions(), l);
   *                         SkAutoPixmapStorage level;
   *                         level.alloc(plane.info().makeDimensions(dims));
   *                         level.erase(SK_ColorRED);
   *                         levels.push_back(level);
   *                         levelStorage.push_back(std::move(level));
   *                     }
   *                     if (!mbet || !recorder->updateBackendTexture(mbet->texture(),
   *                                                                  levels.data(),
   *                                                                  levels.size())) {
   *                         return false;
   *                     }
   *                 } else {
   *                     mbet = ManagedGraphiteTexture::MakeFromPixmap(recorder,
   *                                                                   plane,
   *                                                                   skgpu::Mipmapped::kNo,
   *                                                                   skgpu::Renderable::kNo);
   *                     if (!mbet) {
   *                         return false;
   *                     }
   *                 }
   *                 planeImgs[i] = SkImages::WrapTexture(recorder,
   *                                                      mbet->texture(),
   *                                                      plane.colorType(),
   *                                                      plane.alphaType(),
   *                                                      fColorSpace,
   *                                                      skgpu::Origin::kTopLeft,
   *                                                      genMipmaps,
   *                                                      ManagedGraphiteTexture::ImageReleaseProc,
   *                                                      mbet->releaseContext());
   *             }
   *
   *             fYUVImage[idx] = SkImages::TextureFromYUVAImages(
   *                     recorder,
   *                     fPixmaps.yuvaInfo(),
   *                     planeImgs,
   *                     fColorSpace);
   *             break;
   *         }
   *     }
   *     return fYUVImage[idx] != nullptr;
   * }
   * ```
   */
  public fun ensureYUVImage(recorder: Recorder?, type: Type): Boolean {
    TODO("Implement ensureYUVImage")
  }

  public enum class Type {
    kFromPixmaps,
    kFromGenerator,
    kFromTextures,
    kFromImages,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<LazyYUVImage> LazyYUVImage::Make(sk_sp<SkData> data,
     *                                                  skgpu::Mipmapped mipmapped,
     *                                                  sk_sp<SkColorSpace> cs) {
     *     std::unique_ptr<LazyYUVImage> image(new LazyYUVImage());
     *     if (image->reset(std::move(data), mipmapped, std::move(cs))) {
     *         return image;
     *     } else {
     *         return nullptr;
     *     }
     * }
     * ```
     */
    public fun make(
      `data`: SkSp<SkData>,
      mipmapped: Mipmapped = TODO(),
      cs: SkSp<SkColorSpace> = TODO(),
    ): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * std::unique_ptr<LazyYUVImage> LazyYUVImage::Make(SkYUVAPixmaps pixmaps,
     *                                                  skgpu::Mipmapped mipmapped,
     *                                                  sk_sp<SkColorSpace> cs) {
     *     std::unique_ptr<LazyYUVImage> image(new LazyYUVImage());
     *     if (image->reset(std::move(pixmaps), mipmapped, std::move(cs))) {
     *         return image;
     *     } else {
     *         return nullptr;
     *     }
     * }
     * ```
     */
    public fun make(
      pixmaps: SkYUVAPixmaps,
      mipmapped: Mipmapped = TODO(),
      cs: SkSp<SkColorSpace> = TODO(),
    ): Int {
      TODO("Implement make")
    }
  }
}
