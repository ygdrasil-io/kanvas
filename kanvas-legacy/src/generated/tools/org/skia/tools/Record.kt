package org.skia.tools

import kotlin.Int
import org.skia.core.SkTextBlob
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSp
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * struct Record {
 *     uint32_t origUniqueID;
 *     SkPaint paint;
 *     SkPoint offset;
 *     sk_sp<SkTextBlob> blob;
 * }
 * ```
 */
public open class Record public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint32_t origUniqueID
   * ```
   */
  public var origUniqueID: Int,
  /**
   * C++ original:
   * ```cpp
   * SkPaint paint
   * ```
   */
  public var paint: SkPaint,
  /**
   * C++ original:
   * ```cpp
   * SkPoint offset
   * ```
   */
  public var offset: SkPoint,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkTextBlob> blob
   * ```
   */
  public var blob: SkSp<SkTextBlob>,
)
