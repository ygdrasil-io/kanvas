package org.skia.core

import Validator
import kotlin.Array
import kotlin.Boolean
import kotlin.UInt
import org.skia.foundation.SkSp
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import undefined.RequiredProperties

/**
 * C++ original:
 * ```cpp
 * class SkImage_Picture : public SkImage_Lazy {
 * public:
 *     static sk_sp<SkImage> Make(sk_sp<SkPicture> picture, const SkISize& dimensions,
 *                                const SkMatrix* matrix, const SkPaint* paint,
 *                                SkImages::BitDepth bitDepth, sk_sp<SkColorSpace> colorSpace,
 *                                SkSurfaceProps props);
 *
 *     explicit SkImage_Picture(Validator* validator) : SkImage_Lazy(validator) {}
 *
 *     SkImage_Base::Type type() const override { return SkImage_Base::Type::kLazyPicture; }
 *
 *     // This is thread safe. It is a const field set in the constructor.
 *     const SkSurfaceProps* props() const;
 *
 *     // Call drawPicture on the provided canvas taking care of any required mutex locking.
 *     void replay(SkCanvas*) const;
 *
 *     sk_sp<SkImage> onMakeSubset(SkRecorder*, const SkIRect&, RequiredProperties) const override;
 *
 *     // If possible, extract key data based on the underlying drawPicture-call's parameters.
 *     // Takes care of any required mutex locking.
 *     bool getImageKeyValues(uint32_t keyValues[SkTiledImageUtils::kNumImageKeyValues]) const;
 * }
 * ```
 */
public open class SkImagePicture public constructor(
  validator: Validator?,
) : SkImageLazy(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkImage_Base::Type type() const override { return SkImage_Base::Type::kLazyPicture; }
   * ```
   */
  public override fun type(): SkImage_Base.Type {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkSurfaceProps* SkImage_Picture::props() const {
   *     auto pictureIG = static_cast<SkPictureImageGenerator*>(this->generator()->fGenerator.get());
   *     return &pictureIG->fProps;
   * }
   * ```
   */
  public fun props(): SkSurfaceProps {
    TODO("Implement props")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkImage_Picture::replay(SkCanvas* canvas) const {
   *     auto sharedGenerator = this->generator();
   *     SkAutoMutexExclusive mutex(sharedGenerator->fMutex);
   *
   *     auto pictureIG = static_cast<SkPictureImageGenerator*>(sharedGenerator->fGenerator.get());
   *     canvas->clear(SkColors::kTransparent);
   *     canvas->drawPicture(pictureIG->fPicture,
   *                         &pictureIG->fMatrix,
   *                         SkOptAddressOrNull(pictureIG->fPaint));
   * }
   * ```
   */
  public fun replay(canvas: SkCanvas?) {
    TODO("Implement replay")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> SkImage_Picture::onMakeSubset(SkRecorder*,
   *                                              const SkIRect& subset,
   *                                              RequiredProperties) const {
   *     auto sharedGenerator = this->generator();
   *     auto pictureIG = static_cast<SkPictureImageGenerator*>(sharedGenerator->fGenerator.get());
   *
   *     SkMatrix matrix = pictureIG->fMatrix;
   *     matrix.postTranslate(-subset.left(), -subset.top());
   *     SkImages::BitDepth bitDepth =
   *             this->colorType() == kRGBA_F16_SkColorType ? SkImages::BitDepth::kF16
   *                                                        : SkImages::BitDepth::kU8;
   *
   *     return SkImage_Picture::Make(pictureIG->fPicture, subset.size(),
   *                                  &matrix, SkOptAddressOrNull(pictureIG->fPaint),
   *                                  bitDepth, this->refColorSpace(), pictureIG->fProps);
   * }
   * ```
   */
  public override fun onMakeSubset(
    param0: SkRecorder?,
    subset: SkIRect,
    param2: RequiredProperties,
  ): SkSp<SkImage> {
    TODO("Implement onMakeSubset")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkImage_Picture::getImageKeyValues(
   *         uint32_t keyValues[SkTiledImageUtils::kNumImageKeyValues]) const {
   *
   *     auto sharedGenerator = this->generator();
   *     SkAutoMutexExclusive mutex(sharedGenerator->fMutex);
   *
   *     auto pictureIG = static_cast<SkPictureImageGenerator*>(sharedGenerator->fGenerator.get());
   *     if (pictureIG->fPaint.has_value()) {
   *         // A full paint complicates the potential key too much.
   *         return false;
   *     }
   *
   *     const SkImageInfo& ii = sharedGenerator->getInfo();
   *     if (!ii.colorSpace()->isSRGB()) {
   *         // We only return key values if the colorSpace is sRGB.
   *         return false;
   *     }
   *
   *     const SkMatrix& m = pictureIG->fMatrix;
   *     if (!m.isIdentity() && !m.isTranslate()) {
   *         // To keep the key small we only cache simple (<= translation) matrices
   *         return false;
   *     }
   *
   *     bool isU8 = ii.colorType() != kRGBA_F16_SkColorType;
   *     uint32_t pixelGeometry = this->props()->pixelGeometry();
   *     uint32_t surfacePropFlags = this->props()->flags();
   *     int width = ii.width();
   *     int height = ii.height();
   *     float transX = m.getTranslateX();
   *     float transY = m.getTranslateY();
   *
   *     SkASSERT(pixelGeometry <= 4);
   *     SkASSERT(surfacePropFlags < 8);
   *     SkASSERT(SkTFitsIn<uint32_t>(width));
   *     SkASSERT(SkTFitsIn<uint32_t>(height));
   *     SkASSERT(sizeof(float) == sizeof(uint32_t));
   *
   *     // The 0th slot usually holds either the SkBitmap's ID or the image's. In those two cases
   *     // slot #1 is zero so we can reuse the 0th slot here.
   *     keyValues[0] = (isU8 ? 0x1 : 0x0) |     // 1 bit
   *                    (pixelGeometry << 1) |   // 3 bits
   *                    (surfacePropFlags << 4); // 3 bits
   *     keyValues[1] = pictureIG->fPicture->uniqueID();
   *     SkASSERT(keyValues[1] != 0);    // Double check we don't collide w/ bitmap or image keys
   *     keyValues[2] = width;
   *     keyValues[3] = height;
   *     memcpy(&keyValues[4], &transX, sizeof(uint32_t));
   *     memcpy(&keyValues[5], &transY, sizeof(uint32_t));
   *     return true;
   * }
   * ```
   */
  public fun getImageKeyValues(keyValues: Array<UInt>): Boolean {
    TODO("Implement getImageKeyValues")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkImage> SkImage_Picture::Make(sk_sp<SkPicture> picture, const SkISize& dimensions,
     *                                      const SkMatrix* matrix, const SkPaint* paint,
     *                                      SkImages::BitDepth bitDepth, sk_sp<SkColorSpace> colorSpace,
     *                                      SkSurfaceProps props) {
     *     auto gen = SkImageGenerators::MakeFromPicture(dimensions, std::move(picture), matrix, paint,
     *                                                   bitDepth, std::move(colorSpace), props);
     *
     *     SkImage_Lazy::Validator validator(
     *             SharedGenerator::Make(std::move(gen)), nullptr, nullptr);
     *
     *     return validator ? sk_make_sp<SkImage_Picture>(&validator) : nullptr;
     * }
     * ```
     */
    public fun make(
      picture: SkSp<SkPicture>,
      dimensions: SkISize,
      matrix: SkMatrix?,
      paint: SkPaint?,
      bitDepth: BitDepth,
      colorSpace: SkSp<SkColorSpace>,
      props: SkSurfaceProps,
    ): SkSp<SkImage> {
      TODO("Implement make")
    }
  }
}
