package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkColorInfo
import org.skia.foundation.SkSp
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * class UploadList {
 * public:
 *     bool recordUpload(Recorder*,
 *                       sk_sp<TextureProxy> targetProxy,
 *                       const SkColorInfo& srcColorInfo,
 *                       const SkColorInfo& dstColorInfo,
 *                       const UploadSource& source,
 *                       const SkIRect& dstRect,
 *                       std::unique_ptr<ConditionalUploadContext>);
 *
 *     int size() { return fInstances.size(); }
 *
 * private:
 *     friend class UploadTask;
 *
 *     skia_private::STArray<1, UploadInstance> fInstances;
 * }
 * ```
 */
public data class UploadList public constructor(
  /**
   * C++ original:
   * ```cpp
   * skia_private::STArray<1, UploadInstance> fInstances
   * ```
   */
  private var fInstances: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool UploadList::recordUpload(Recorder* recorder,
   *                               sk_sp<TextureProxy> textureProxy,
   *                               const SkColorInfo& srcColorInfo,
   *                               const SkColorInfo& dstColorInfo,
   *                               const UploadSource& source,
   *                               const SkIRect& dstRect,
   *                               std::unique_ptr<ConditionalUploadContext> condContext) {
   *     // If possible, upload the data directly on host.
   *     if (source.canUploadOnHost()) {
   *         return textureProxy->texture()->uploadDataOnHost(source, dstRect);
   *     }
   *
   *     UploadInstance instance = UploadInstance::Make(recorder,
   *                                                    std::move(textureProxy),
   *                                                    srcColorInfo,
   *                                                    dstColorInfo,
   *                                                    source,
   *                                                    dstRect,
   *                                                    std::move(condContext));
   *     if (!instance.isValid()) {
   *         return false;
   *     }
   *
   *     fInstances.emplace_back(std::move(instance));
   *     return true;
   * }
   * ```
   */
  public fun recordUpload(
    recorder: Recorder?,
    targetProxy: SkSp<TextureProxy>,
    srcColorInfo: SkColorInfo,
    dstColorInfo: SkColorInfo,
    source: UploadSource,
    dstRect: SkIRect,
    condContext: ConditionalUploadContext?,
  ): Boolean {
    TODO("Implement recordUpload")
  }

  /**
   * C++ original:
   * ```cpp
   * int size() { return fInstances.size(); }
   * ```
   */
  public fun size(): Int {
    TODO("Implement size")
  }
}
