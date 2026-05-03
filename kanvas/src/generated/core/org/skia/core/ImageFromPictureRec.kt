package org.skia.core

import kotlin.Boolean
import kotlin.Char
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkSp
import org.skia.pdf.Key
import undefined.SkDiscardableMemory

/**
 * C++ original:
 * ```cpp
 * struct ImageFromPictureRec : public SkResourceCache::Rec {
 *     ImageFromPictureRec(const ImageFromPictureKey& key, sk_sp<SkImage> image)
 *         : fKey(key)
 *         , fImage(std::move(image)) {}
 *
 *     ImageFromPictureKey fKey;
 *     sk_sp<SkImage>  fImage;
 *
 *     const Key& getKey() const override { return fKey; }
 *     size_t bytesUsed() const override {
 *         // Just the record overhead -- the actual pixels are accounted by SkImage_Lazy.
 *         return sizeof(fKey) + (size_t)fImage->width() * fImage->height() * 4;
 *     }
 *     const char* getCategory() const override { return "bitmap-shader"; }
 *     SkDiscardableMemory* diagnostic_only_getDiscardable() const override { return nullptr; }
 *
 *     static bool Visitor(const SkResourceCache::Rec& baseRec, void* contextShader) {
 *         const ImageFromPictureRec& rec = static_cast<const ImageFromPictureRec&>(baseRec);
 *         sk_sp<SkImage>* result = reinterpret_cast<sk_sp<SkImage>*>(contextShader);
 *
 *         *result = rec.fImage;
 *         return true;
 *     }
 * }
 * ```
 */
public open class ImageFromPictureRec public constructor(
  /**
   * C++ original:
   * ```cpp
   * ImageFromPictureKey fKey
   * ```
   */
  public var fKey: ImageFromPictureKey,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage>  fImage
   * ```
   */
  public var fImage: SkSp<SkImage>,
) : SkResourceCache.Rec(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * ImageFromPictureRec(const ImageFromPictureKey& key, sk_sp<SkImage> image)
   *         : fKey(key)
   *         , fImage(std::move(image)) {}
   * ```
   */
  public constructor(key: ImageFromPictureKey, image: SkSp<SkImage>) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * const Key& getKey() const override { return fKey; }
   * ```
   */
  public override fun getKey(): Key {
    TODO("Implement getKey")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t bytesUsed() const override {
   *         // Just the record overhead -- the actual pixels are accounted by SkImage_Lazy.
   *         return sizeof(fKey) + (size_t)fImage->width() * fImage->height() * 4;
   *     }
   * ```
   */
  public override fun bytesUsed(): ULong {
    TODO("Implement bytesUsed")
  }

  /**
   * C++ original:
   * ```cpp
   * const char* getCategory() const override { return "bitmap-shader"; }
   * ```
   */
  public override fun getCategory(): Char {
    TODO("Implement getCategory")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDiscardableMemory* diagnostic_only_getDiscardable() const override { return nullptr; }
   * ```
   */
  public override fun diagnosticOnlyGetDiscardable(): SkDiscardableMemory {
    TODO("Implement diagnosticOnlyGetDiscardable")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static bool Visitor(const SkResourceCache::Rec& baseRec, void* contextShader) {
     *         const ImageFromPictureRec& rec = static_cast<const ImageFromPictureRec&>(baseRec);
     *         sk_sp<SkImage>* result = reinterpret_cast<sk_sp<SkImage>*>(contextShader);
     *
     *         *result = rec.fImage;
     *         return true;
     *     }
     * ```
     */
    public fun visitor(baseRec: SkResourceCache.Rec, contextShader: Unit?): Boolean {
      TODO("Implement visitor")
    }
  }
}
