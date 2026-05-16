package org.skia.foundation

import kotlin.Int
import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * struct SK_API SkSerialProcs {
 *     SkSerialPictureProc fPictureProc = nullptr;
 *     void*               fPictureCtx = nullptr;
 *
 *     SkSerialImageProc   fImageProc = nullptr;
 *     void*               fImageCtx = nullptr;
 *
 *     SkSerialTypefaceProc fTypefaceProc = nullptr;
 *     void*                fTypefaceCtx = nullptr;
 * }
 * ```
 */
public data class SkSerialProcs public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkSerialPictureProc fPictureProc
   * ```
   */
  public var fPictureProc: Int,
  /**
   * C++ original:
   * ```cpp
   * void*               fPictureCtx = nullptr
   * ```
   */
  public var fPictureCtx: Unit?,
  /**
   * C++ original:
   * ```cpp
   * SkSerialImageProc   fImageProc
   * ```
   */
  public var fImageProc: Int,
  /**
   * C++ original:
   * ```cpp
   * void*               fImageCtx = nullptr
   * ```
   */
  public var fImageCtx: Unit?,
  /**
   * C++ original:
   * ```cpp
   * SkSerialTypefaceProc fTypefaceProc
   * ```
   */
  public var fTypefaceProc: Int,
  /**
   * C++ original:
   * ```cpp
   * void*                fTypefaceCtx = nullptr
   * ```
   */
  public var fTypefaceCtx: Unit?,
)
