package org.skia.core

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.foundation.SkSurfaceProps
import org.skia.foundation.SkTileMode
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.math.SkSize
import org.skia.memory.SkArenaAlloc
import org.skia.tests.ShaderType
import undefined.ContextRec

/**
 * C++ original:
 * ```cpp
 * class SkPictureShader : public SkShaderBase {
 * public:
 *     static sk_sp<SkShader> Make(sk_sp<SkPicture>, SkTileMode, SkTileMode, SkFilterMode,
 *                                 const SkMatrix*, const SkRect*);
 *
 *     SkPictureShader(sk_sp<SkPicture>, SkTileMode, SkTileMode, SkFilterMode, const SkRect*);
 *
 *     ShaderType type() const override { return ShaderType::kPicture; }
 *
 *     sk_sp<SkPicture> picture() const { return fPicture; }
 *     SkRect tile() const { return fTile; }
 *     SkTileMode tileModeX() const { return fTmx; }
 *     SkTileMode tileModeY() const { return fTmy; }
 *     SkFilterMode filter() const { return fFilter; }
 *
 *     struct CachedImageInfo {
 *         bool success;
 *         SkSize tileScale;        // Additional scale factors to apply when sampling image.
 *         SkMatrix matrixForDraw;  // Matrix used to produce an image from the picture
 *         SkImageInfo imageInfo;
 *         SkSurfaceProps props;
 *
 *         static CachedImageInfo Make(const SkRect& bounds,
 *                                     const SkMatrix& totalM,
 *                                     SkColorType dstColorType,
 *                                     SkColorSpace* dstColorSpace,
 *                                     const int maxTextureSize,
 *                                     const SkSurfaceProps& propsIn);
 *
 *         // Returns a temporary image created from the surface
 *         sk_sp<SkImage> makeImage(sk_sp<SkSurface> surf, const SkPicture* pict) const;
 *     };
 *
 * protected:
 *     SkPictureShader(SkReadBuffer&);
 *     void flatten(SkWriteBuffer&) const override;
 *     bool appendStages(const SkStageRec&, const SkShaders::MatrixRec&) const override;
 * #ifdef SK_ENABLE_LEGACY_SHADERCONTEXT
 *     Context* onMakeContext(const ContextRec&, SkArenaAlloc*) const override;
 * #endif
 *
 * private:
 *     SK_FLATTENABLE_HOOKS(SkPictureShader)
 *
 *     sk_sp<SkShader> rasterShader(const SkMatrix&,
 *                                  SkColorType dstColorType,
 *                                  SkColorSpace* dstColorSpace,
 *                                  const SkSurfaceProps& props) const;
 *
 *     sk_sp<SkPicture>    fPicture;
 *     SkRect              fTile;
 *     SkTileMode          fTmx, fTmy;
 *     SkFilterMode fFilter;
 * }
 * ```
 */
public open class SkPictureShader public constructor(
  picture: SkSp<SkPicture>,
  tmx: SkTileMode,
  tmy: SkTileMode,
  filter: SkFilterMode,
  tile: SkRect?,
) : SkShaderBase() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPicture>    fPicture
   * ```
   */
  private var fPicture: SkSp<SkPicture> = TODO("Initialize fPicture")

  /**
   * C++ original:
   * ```cpp
   * SkRect              fTile
   * ```
   */
  private var fTile: SkRect = TODO("Initialize fTile")

  /**
   * C++ original:
   * ```cpp
   * SkTileMode          fTmx
   * ```
   */
  private var fTmx: SkTileMode = TODO("Initialize fTmx")

  /**
   * C++ original:
   * ```cpp
   * SkTileMode          fTmx, fTmy
   * ```
   */
  private var fTmy: SkTileMode = TODO("Initialize fTmy")

  /**
   * C++ original:
   * ```cpp
   * SkFilterMode fFilter
   * ```
   */
  private var fFilter: SkFilterMode = TODO("Initialize fFilter")

  /**
   * C++ original:
   * ```cpp
   * SkPictureShader::SkPictureShader(sk_sp<SkPicture> picture,
   *                                  SkTileMode tmx,
   *                                  SkTileMode tmy,
   *                                  SkFilterMode filter,
   *                                  const SkRect* tile)
   *         : fPicture(std::move(picture))
   *         , fTile(tile ? *tile : fPicture->cullRect())
   *         , fTmx(tmx)
   *         , fTmy(tmy)
   *         , fFilter(filter) {}
   * ```
   */
  public constructor(param0: SkReadBuffer) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * ShaderType type() const override { return ShaderType::kPicture; }
   * ```
   */
  public override fun type(): ShaderType {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPicture> picture() const { return fPicture; }
   * ```
   */
  public fun picture(): SkSp<SkPicture> {
    TODO("Implement picture")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect tile() const { return fTile; }
   * ```
   */
  public fun tile(): SkRect {
    TODO("Implement tile")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTileMode tileModeX() const { return fTmx; }
   * ```
   */
  public fun tileModeX(): SkTileMode {
    TODO("Implement tileModeX")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTileMode tileModeY() const { return fTmy; }
   * ```
   */
  public fun tileModeY(): SkTileMode {
    TODO("Implement tileModeY")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFilterMode filter() const { return fFilter; }
   * ```
   */
  public fun filter(): SkFilterMode {
    TODO("Implement filter")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureShader::flatten(SkWriteBuffer& buffer) const {
   *     buffer.write32((unsigned)fTmx);
   *     buffer.write32((unsigned)fTmy);
   *     buffer.writeRect(fTile);
   *     buffer.write32((unsigned)fFilter);
   *     SkPicturePriv::Flatten(fPicture, buffer);
   * }
   * ```
   */
  protected override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPictureShader::appendStages(const SkStageRec& rec, const SkShaders::MatrixRec& mRec) const {
   *     // Keep bitmapShader alive by using alloc instead of stack memory
   *     auto& bitmapShader = *rec.fAlloc->make<sk_sp<SkShader>>();
   *     // We don't check whether the total local matrix is valid here because we have to assume *some*
   *     // mapping to make an image. It could be wildly wrong if there is a runtime shader transforming
   *     // the coordinates in a manner we don't know about here. However, that is a fundamental problem
   *     // with the technique of converting a picture to an image to implement this shader.
   *     bitmapShader = this->rasterShader(mRec.totalMatrix(),
   *                                       rec.fDstColorType,
   *                                       rec.fDstCS,
   *                                       rec.fSurfaceProps);
   *     if (!bitmapShader) {
   *         return false;
   *     }
   *     return as_SB(bitmapShader)->appendStages(rec, mRec);
   * }
   * ```
   */
  protected override fun appendStages(rec: SkStageRec, mRec: MatrixRec): Boolean {
    TODO("Implement appendStages")
  }

  /**
   * C++ original:
   * ```cpp
   * SkShaderBase::Context* SkPictureShader::onMakeContext(const ContextRec& rec,
   *                                                       SkArenaAlloc* alloc) const {
   *     sk_sp<SkShader> bitmapShader = this->rasterShader(
   *             rec.fMatrixRec.totalMatrix(), rec.fDstColorType, rec.fDstColorSpace, rec.fProps);
   *     if (!bitmapShader) {
   *         return nullptr;
   *     }
   *
   *     return as_SB(bitmapShader)->makeContext(rec, alloc);
   * }
   * ```
   */
  protected override fun onMakeContext(rec: ContextRec, alloc: SkArenaAlloc?): Context {
    TODO("Implement onMakeContext")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> SkPictureShader::rasterShader(const SkMatrix& totalM,
   *                                               SkColorType dstColorType,
   *                                               SkColorSpace* dstColorSpace,
   *                                               const SkSurfaceProps& propsIn) const {
   *     const int maxTextureSize_NotUsedForCPU = 0;
   *     CachedImageInfo info = CachedImageInfo::Make(fTile,
   *                                                  totalM,
   *                                                  dstColorType, dstColorSpace,
   *                                                  maxTextureSize_NotUsedForCPU,
   *                                                  propsIn);
   *     if (!info.success) {
   *         return nullptr;
   *     }
   *
   *     ImageFromPictureKey key(info.imageInfo.colorSpace(), info.imageInfo.colorType(),
   *                             fPicture->uniqueID(), fTile, info.tileScale, info.props);
   *
   *     sk_sp<SkImage> image;
   *     if (!SkResourceCache::Find(key, ImageFromPictureRec::Visitor, &image)) {
   *         image = info.makeImage(SkSurfaces::Raster(info.imageInfo, &info.props), fPicture.get());
   *         if (!image) {
   *             return nullptr;
   *         }
   *
   *         SkResourceCache::Add(new ImageFromPictureRec(key, image));
   *         SkPicturePriv::AddedToCache(fPicture.get());
   *     }
   *     // Scale the image to the original picture size.
   *     auto lm = SkMatrix::Scale(1.f/info.tileScale.width(), 1.f/info.tileScale.height());
   *     return image->makeShader(fTmx, fTmy, SkSamplingOptions(fFilter), &lm);
   * }
   * ```
   */
  private fun rasterShader(
    totalM: SkMatrix,
    dstColorType: SkColorType,
    dstColorSpace: SkColorSpace?,
    props: SkSurfaceProps,
  ): SkSp<SkShader> {
    TODO("Implement rasterShader")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkPictureShader::CreateProc(SkReadBuffer& buffer) {
   *     SkMatrix lm;
   *     if (buffer.isVersionLT(SkPicturePriv::Version::kNoShaderLocalMatrix)) {
   *         buffer.readMatrix(&lm);
   *     }
   *     auto tmx = buffer.read32LE(SkTileMode::kLastTileMode);
   *     auto tmy = buffer.read32LE(SkTileMode::kLastTileMode);
   *     SkRect tile = buffer.readRect();
   *
   *     sk_sp<SkPicture> picture;
   *
   *     SkFilterMode filter = SkFilterMode::kNearest;
   *     if (buffer.isVersionLT(SkPicturePriv::kNoFilterQualityShaders_Version)) {
   *         if (buffer.isVersionLT(SkPicturePriv::kPictureShaderFilterParam_Version)) {
   *             bool didSerialize = buffer.readBool();
   *             if (didSerialize) {
   *                 picture = SkPicturePriv::MakeFromBuffer(buffer);
   *             }
   *         } else {
   *             unsigned legacyFilter = buffer.read32();
   *             if (legacyFilter <= (unsigned)SkFilterMode::kLast) {
   *                 filter = (SkFilterMode)legacyFilter;
   *             }
   *             picture = SkPicturePriv::MakeFromBuffer(buffer);
   *         }
   *     } else {
   *         filter = buffer.read32LE(SkFilterMode::kLast);
   *         picture = SkPicturePriv::MakeFromBuffer(buffer);
   *     }
   *     return SkPictureShader::Make(picture, tmx, tmy, filter, &lm, &tile);
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }

  public data class CachedImageInfo public constructor(
    public var success: Boolean,
    public var tileScale: SkSize,
    public var matrixForDraw: SkMatrix,
    public var imageInfo: SkImageInfo,
    public var props: SkSurfaceProps,
  ) {
    public fun makeImage(surf: SkSp<SkSurface>, pict: SkPicture?): SkSp<SkImage> {
      TODO("Implement makeImage")
    }

    public companion object {
      public fun make(
        bounds: SkRect,
        totalM: SkMatrix,
        dstColorType: SkColorType,
        dstColorSpace: SkColorSpace?,
        maxTextureSize: Int,
        propsIn: SkSurfaceProps,
      ): CachedImageInfo {
        TODO("Implement make")
      }
    }
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkShader> SkPictureShader::Make(sk_sp<SkPicture> picture, SkTileMode tmx, SkTileMode tmy,
     *                                       SkFilterMode filter, const SkMatrix* lm, const SkRect* tile) {
     *     if (!picture || picture->cullRect().isEmpty() || (tile && tile->isEmpty())) {
     *         return SkShaders::Empty();
     *     }
     *     return SkLocalMatrixShader::MakeWrapped<SkPictureShader>(lm,
     *                                                              std::move(picture),
     *                                                              tmx, tmy,
     *                                                              filter,
     *                                                              tile);
     * }
     * ```
     */
    public fun make(
      picture: SkSp<SkPicture>,
      tmx: SkTileMode,
      tmy: SkTileMode,
      filter: SkFilterMode,
      lm: SkMatrix?,
      tile: SkRect?,
    ): SkSp<SkShader> {
      TODO("Implement make")
    }
  }
}
