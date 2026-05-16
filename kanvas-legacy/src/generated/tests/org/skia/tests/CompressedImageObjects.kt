package org.skia.tests

import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.tools.ManagedGraphiteTexture

/**
 * C++ original:
 * ```cpp
 * struct CompressedImageObjects {
 *     sk_sp<SkImage> fImage;
 * #if defined(SK_GRAPHITE)
 *     sk_sp<sk_gpu_test::ManagedGraphiteTexture> fGraphiteTexture;
 * #else
 *     void* fGraphiteTexture = nullptr;
 * #endif
 * }
 * ```
 */
public data class CompressedImageObjects public constructor(
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fImage
   * ```
   */
  public var fImage: SkSp<SkImage>,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<sk_gpu_test::ManagedGraphiteTexture> fGraphiteTexture
   * ```
   */
  public var fGraphiteTexture: SkSp<ManagedGraphiteTexture>,
)
