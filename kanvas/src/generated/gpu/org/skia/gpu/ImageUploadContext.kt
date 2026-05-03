package org.skia.gpu

import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * class ImageUploadContext : public ConditionalUploadContext {
 * public:
 *     ~ImageUploadContext() override {}
 *
 *     // Always upload, since it will be discarded right afterwards
 *     bool needsUpload(Context*) override { return true; }
 *
 *     // Always return false so the upload instance is discarded after the first execution
 *     bool uploadSubmitted() override { return false; }
 * }
 * ```
 */
public open class ImageUploadContext : ConditionalUploadContext() {
  /**
   * C++ original:
   * ```cpp
   * bool needsUpload(Context*) override { return true; }
   * ```
   */
  public override fun needsUpload(param0: Context?): Boolean {
    TODO("Implement needsUpload")
  }

  /**
   * C++ original:
   * ```cpp
   * bool uploadSubmitted() override { return false; }
   * ```
   */
  public override fun uploadSubmitted(): Boolean {
    TODO("Implement uploadSubmitted")
  }
}
