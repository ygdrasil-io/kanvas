package org.skia.foundation

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct DecoderProc {
 *         SkFourByteTag id;
 *         sk_sp<SkTypeface> (*makeFromStream)(std::unique_ptr<SkStreamAsset>, const SkFontArguments&);
 *     }
 * ```
 */
public data class DecoderProc public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkFourByteTag id
   * ```
   */
  public var id: Int,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTypeface> (*makeFromStream)(std::unique_ptr<SkStreamAsset>, const SkFontArguments&)
   * ```
   */
  public val makeFromStream: (Int, SkFontArguments) -> SkSp<SkTypeface>,
)
