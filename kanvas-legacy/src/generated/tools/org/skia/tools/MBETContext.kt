package org.skia.tools

import kotlin.Array
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * struct MBETContext {
 *     MBETContext(const sk_sp<sk_gpu_test::ManagedGraphiteTexture>& tex)
 *             : fMBETs{tex, nullptr, nullptr, nullptr} {}
 *     MBETContext(const sk_sp<sk_gpu_test::ManagedGraphiteTexture> mbets[SkYUVAInfo::kMaxPlanes])
 *             : fMBETs{mbets[0], mbets[1], mbets[2], mbets[3]} {}
 *     sk_sp<sk_gpu_test::ManagedGraphiteTexture> fMBETs[SkYUVAInfo::kMaxPlanes];
 * }
 * ```
 */
public data class MBETContext public constructor(
  /**
   * C++ original:
   * ```cpp
   * sk_sp<sk_gpu_test::ManagedGraphiteTexture> fMBETs[SkYUVAInfo::kMaxPlanes]
   * ```
   */
  public var fMBETs: Array<SkSp<ManagedGraphiteTexture>>,
)
