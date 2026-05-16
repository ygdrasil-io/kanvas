package org.skia.foundation

import kotlin.Boolean
import kotlin.Int
import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * struct SK_API SkDeserialProcs {
 *     SkDeserialPictureProc        fPictureProc = nullptr;
 *     void*                        fPictureCtx = nullptr;
 *
 *     SkDeserialImageProc          fImageProc = nullptr;
 *     SkDeserialImageFromDataProc  fImageDataProc = nullptr;
 *     void*                        fImageCtx = nullptr;
 *
 *     SkSlugProc                   fSlugProc = nullptr;
 *     void*                        fSlugCtx = nullptr;
 *
 *     SkDeserialTypefaceProc       fTypefaceProc = nullptr;
 *     void*                        fTypefaceCtx = nullptr;
 *
 *     // This looks like a flag, but it could be considered a proc as well (one that takes no
 *     // parameters and returns a bool). Given that there are only two valid implementations of that
 *     // proc, we just insert the bool directly.
 *     bool                         fAllowSkSL = true;
 * }
 * ```
 */
public data class SkDeserialProcs public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkDeserialPictureProc        fPictureProc
   * ```
   */
  public var fPictureProc: Int,
  /**
   * C++ original:
   * ```cpp
   * void*                        fPictureCtx = nullptr
   * ```
   */
  public var fPictureCtx: Unit?,
  /**
   * C++ original:
   * ```cpp
   * SkDeserialImageProc          fImageProc
   * ```
   */
  public var fImageProc: SkDeserialProcs,
  /**
   * C++ original:
   * ```cpp
   * SkDeserialImageFromDataProc  fImageDataProc
   * ```
   */
  public var fImageDataProc: Int,
  /**
   * C++ original:
   * ```cpp
   * void*                        fImageCtx = nullptr
   * ```
   */
  public var fImageCtx: Unit?,
  /**
   * C++ original:
   * ```cpp
   * SkSlugProc                   fSlugProc
   * ```
   */
  public var fSlugProc: Int,
  /**
   * C++ original:
   * ```cpp
   * void*                        fSlugCtx = nullptr
   * ```
   */
  public var fSlugCtx: Unit?,
  /**
   * C++ original:
   * ```cpp
   * SkDeserialTypefaceProc       fTypefaceProc
   * ```
   */
  public var fTypefaceProc: Int,
  /**
   * C++ original:
   * ```cpp
   * void*                        fTypefaceCtx = nullptr
   * ```
   */
  public var fTypefaceCtx: Unit?,
  /**
   * C++ original:
   * ```cpp
   * bool                         fAllowSkSL = true
   * ```
   */
  public var fAllowSkSL: Boolean,
)
