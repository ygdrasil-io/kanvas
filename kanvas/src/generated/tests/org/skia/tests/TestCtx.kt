package org.skia.tests

import kotlin.Array
import kotlin.Int
import org.skia.core.SkSurface
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.gpu.BackendTexture
import org.skia.gpu.GraphitePromiseImageContext
import org.skia.gpu.GraphitePromiseTextureFulfillContext

/**
 * C++ original:
 * ```cpp
 * struct TestCtx {
 *     TestCtx() {}
 *
 *     ~TestCtx() {
 *         for (int i = 0; i < 8; ++i) {
 *             if (fBackendTextures[i].isValid()) {
 *                 fContext->deleteBackendTexture(fBackendTextures[i]);
 *             }
 *         }
 *     }
 *
 *     Context* fContext;
 *     std::unique_ptr<Recorder> fRecorder;
 *     BackendTexture fBackendTextures[8];
 *     PromiseImageChecker fPromiseImageChecker;
 *     SkImages::GraphitePromiseImageContext fImageContext = &fPromiseImageChecker;
 *     PromiseTextureChecker fPromiseTextureCheckers[4];
 *     SkImages::GraphitePromiseTextureFulfillContext fTextureContexts[4] = {
 *         &fPromiseTextureCheckers[0],
 *         &fPromiseTextureCheckers[1],
 *         &fPromiseTextureCheckers[2],
 *         &fPromiseTextureCheckers[3],
 *     };
 *     sk_sp<SkImage> fImg;
 *     sk_sp<SkSurface> fSurface;
 * }
 * ```
 */
public data class TestCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * Context* fContext
   * ```
   */
  public var fContext: Context?,
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<Recorder> fRecorder
   * ```
   */
  public var fRecorder: Int,
  /**
   * C++ original:
   * ```cpp
   * BackendTexture fBackendTextures[8]
   * ```
   */
  public var fBackendTextures: Array<BackendTexture>,
  /**
   * C++ original:
   * ```cpp
   * PromiseImageChecker fPromiseImageChecker
   * ```
   */
  public var fPromiseImageChecker: PromiseImageChecker,
  /**
   * C++ original:
   * ```cpp
   * SkImages::GraphitePromiseImageContext fImageContext = &fPromiseImageChecker
   * ```
   */
  public var fImageContext: GraphitePromiseImageContext,
  /**
   * C++ original:
   * ```cpp
   * PromiseTextureChecker fPromiseTextureCheckers[4]
   * ```
   */
  public var fPromiseTextureCheckers: Array<PromiseTextureChecker>,
  /**
   * C++ original:
   * ```cpp
   * SkImages::GraphitePromiseTextureFulfillContext fTextureContexts[4]
   * ```
   */
  public var fTextureContexts: Array<GraphitePromiseTextureFulfillContext>,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fImg
   * ```
   */
  public var fImg: SkSp<SkImage>,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSurface> fSurface
   * ```
   */
  public var fSurface: SkSp<SkSurface>,
)
