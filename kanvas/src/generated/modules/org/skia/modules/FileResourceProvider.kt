package org.skia.modules

import kotlin.CharArray
import kotlin.Int
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * class FileResourceProvider final : public ResourceProvider {
 * public:
 *     // To decode images, clients must call SkCodecs::Register() before calling Make.
 *     static sk_sp<FileResourceProvider> Make(SkString base_dir,
 *                                             ImageDecodeStrategy = ImageDecodeStrategy::kLazyDecode);
 *
 *     sk_sp<SkData> load(const char resource_path[], const char resource_name[]) const override;
 *
 *     sk_sp<ImageAsset> loadImageAsset(const char[], const char[], const char[]) const override;
 *
 * private:
 *     FileResourceProvider(SkString, ImageDecodeStrategy);
 *
 *     const SkString fDir;
 *     const ImageDecodeStrategy fStrategy;
 *
 *     using INHERITED = ResourceProvider;
 * }
 * ```
 */
public class FileResourceProvider public constructor(
  baseDir: String,
  strat: ImageDecodeStrategy,
) : ResourceProvider() {
  /**
   * C++ original:
   * ```cpp
   * const SkString fDir
   * ```
   */
  private val fDir: Int = TODO("Initialize fDir")

  /**
   * C++ original:
   * ```cpp
   * const ImageDecodeStrategy fStrategy
   * ```
   */
  private val fStrategy: ImageDecodeStrategy = TODO("Initialize fStrategy")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> FileResourceProvider::load(const char resource_path[],
   *                                          const char resource_name[]) const {
   *     const auto full_dir  = SkOSPath::Join(fDir.c_str()    , resource_path),
   *                full_path = SkOSPath::Join(full_dir.c_str(), resource_name);
   *     return SkData::MakeFromFileName(full_path.c_str());
   * }
   * ```
   */
  public override fun load(resourcePath: CharArray, resourceName: CharArray): Int {
    TODO("Implement load")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<ImageAsset> FileResourceProvider::loadImageAsset(const char resource_path[],
   *                                                        const char resource_name[],
   *                                                        const char[]) const {
   *     auto data = this->load(resource_path, resource_name);
   *
   *     if (auto image = MultiFrameImageAsset::Make(data, fStrategy)) {
   *         return std::move(image);
   *     }
   *
   * #if defined(HAVE_VIDEO_DECODER)
   *     if (auto video = VideoAsset::Make(data)) {
   *         return std::move(video);
   *     }
   * #endif
   *
   *     return nullptr;
   * }
   * ```
   */
  public override fun loadImageAsset(
    resourcePath: CharArray,
    resourceName: CharArray,
    param2: CharArray,
  ): Int {
    TODO("Implement loadImageAsset")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<FileResourceProvider> FileResourceProvider::Make(SkString base_dir, ImageDecodeStrategy strat) {
     *     return sk_isdir(base_dir.c_str()) ? sk_sp<FileResourceProvider>(new FileResourceProvider(
     *                                                 std::move(base_dir), strat))
     *                                       : nullptr;
     * }
     * ```
     */
    public fun make(baseDir: String, strat: ImageDecodeStrategy = TODO()): Int {
      TODO("Implement make")
    }
  }
}
