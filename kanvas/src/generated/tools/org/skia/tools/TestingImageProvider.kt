package org.skia.tools

import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import kotlin.ULong
import org.skia.core.SkLRUCache
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.gpu.ImageProvider
import org.skia.gpu.Recorder

/**
 * C++ original:
 * ```cpp
 * class TestingImageProvider : public skgpu::graphite::ImageProvider {
 * public:
 *     TestingImageProvider() : fCache(kDefaultNumCachedImages) {}
 *     ~TestingImageProvider() override {}
 *
 *     sk_sp<SkImage> findOrCreate(skgpu::graphite::Recorder* recorder,
 *                                 const SkImage* image,
 *                                 SkImage::RequiredProperties requiredProps) override {
 *         if (!requiredProps.fMipmapped) {
 *             // If no mipmaps are required, check to see if we have a mipmapped version anyway -
 *             // since it can be used in that case.
 *             // TODO: we could get fancy and, if ever a mipmapped key eclipsed a non-mipmapped
 *             // key, we could remove the hidden non-mipmapped key/image from the cache.
 *             ImageKey mipMappedKey(image, /* mipmapped= */ true);
 *             auto result = fCache.find(mipMappedKey);
 *             if (result) {
 *                 return *result;
 *             }
 *         }
 *
 *         ImageKey key(image, requiredProps.fMipmapped);
 *
 *         auto result = fCache.find(key);
 *         if (result) {
 *             return *result;
 *         }
 *
 *         sk_sp<SkImage> newImage = SkImages::TextureFromImage(recorder, image, requiredProps);
 *         if (!newImage) {
 *             return nullptr;
 *         }
 *
 *         result = fCache.insert(key, std::move(newImage));
 *         SkASSERT(result);
 *
 *         return *result;
 *     }
 *
 * private:
 *     static constexpr int kDefaultNumCachedImages = 256;
 *
 *     class ImageKey {
 *     public:
 *         ImageKey(const SkImage* image, bool mipmapped) {
 *             uint32_t flags = mipmapped ? 0x1 : 0x0;
 *             SkTiledImageUtils::GetImageKeyValues(image, &fValues[1]);
 *             fValues[kNumValues - 1] = flags;
 *             fValues[0] = SkChecksum::Hash32(&fValues[1], (kNumValues - 1) * sizeof(uint32_t));
 *         }
 *
 *         uint32_t hash() const { return fValues[0]; }
 *
 *         bool operator==(const ImageKey& other) const {
 *             for (int i = 0; i < kNumValues; ++i) {
 *                 if (fValues[i] != other.fValues[i]) {
 *                     return false;
 *                 }
 *             }
 *
 *             return true;
 *         }
 *         bool operator!=(const ImageKey& other) const { return !(*this == other); }
 *
 *     private:
 *         static const int kNumValues = SkTiledImageUtils::kNumImageKeyValues + 2;
 *
 *         uint32_t fValues[kNumValues];
 *     };
 *
 *     struct ImageHash {
 *         size_t operator()(const ImageKey& key) const { return key.hash(); }
 *     };
 *
 *     SkLRUCache<ImageKey, sk_sp<SkImage>, ImageHash> fCache;
 * }
 * ```
 */
public open class TestingImageProvider public constructor() : ImageProvider() {
  /**
   * C++ original:
   * ```cpp
   * static constexpr int kDefaultNumCachedImages = 256
   * ```
   */
  private var fCache: SkLRUCache<undefined.ImageKey, SkSp<SkImage>, ImageHash> =
      TODO("Initialize fCache")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> findOrCreate(skgpu::graphite::Recorder* recorder,
   *                                 const SkImage* image,
   *                                 SkImage::RequiredProperties requiredProps) override {
   *         if (!requiredProps.fMipmapped) {
   *             // If no mipmaps are required, check to see if we have a mipmapped version anyway -
   *             // since it can be used in that case.
   *             // TODO: we could get fancy and, if ever a mipmapped key eclipsed a non-mipmapped
   *             // key, we could remove the hidden non-mipmapped key/image from the cache.
   *             ImageKey mipMappedKey(image, /* mipmapped= */ true);
   *             auto result = fCache.find(mipMappedKey);
   *             if (result) {
   *                 return *result;
   *             }
   *         }
   *
   *         ImageKey key(image, requiredProps.fMipmapped);
   *
   *         auto result = fCache.find(key);
   *         if (result) {
   *             return *result;
   *         }
   *
   *         sk_sp<SkImage> newImage = SkImages::TextureFromImage(recorder, image, requiredProps);
   *         if (!newImage) {
   *             return nullptr;
   *         }
   *
   *         result = fCache.insert(key, std::move(newImage));
   *         SkASSERT(result);
   *
   *         return *result;
   *     }
   * ```
   */
  public override fun findOrCreate(
    recorder: Recorder?,
    image: SkImage?,
    requiredProps: SkImage.RequiredProperties,
  ): SkSp<SkImage> {
    TODO("Implement findOrCreate")
  }

  public data class ImageKey public constructor(
    private var fValues: Array<UInt>,
  ) {
    public fun hash(): UInt {
      TODO("Implement hash")
    }

    public override operator fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }

    public companion object {
      private val kNumValues: Int = TODO("Initialize kNumValues")
    }
  }

  public open class ImageHash {
    public operator fun invoke(key: undefined.ImageKey): ULong {
      TODO("Implement invoke")
    }
  }

  public companion object {
    private val kDefaultNumCachedImages: Int = TODO("Initialize kDefaultNumCachedImages")
  }
}
